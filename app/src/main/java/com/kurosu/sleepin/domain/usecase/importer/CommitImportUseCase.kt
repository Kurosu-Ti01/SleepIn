package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseColorPalette
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.MAX_SEMESTER_WEEK_COUNT
import com.kurosu.sleepin.domain.repository.TimetableImportPlan
import com.kurosu.sleepin.domain.repository.TimetableImportRepository
import com.kurosu.sleepin.domain.repository.TimetableImportResult
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import java.time.LocalDate

/**
 * Where an imported timetable should end up.
 */
sealed interface ImportTarget {

    /** Create a timetable for the imported courses. */
    data object NewTimetable : ImportTarget

    /** Replace the courses of a timetable the user already has. */
    data class ExistingTimetable(val timetableId: Long) : ImportTarget
}

/**
 * Turns a validated import payload into persisted courses.
 *
 * This is the domain layer's last chance to decide *what* gets written, so it owns the rules that
 * depend on the chosen target rather than on the payload itself:
 * - which timetable metadata to use (from the payload, or from the timetable being overwritten);
 * - how a course picks a color when the script did not choose one;
 * - how a list of teaching weeks becomes the app's week rule.
 *
 * Persistence itself is delegated to [TimetableImportRepository], whose implementation performs the
 * whole write inside one transaction.
 */
class CommitImportUseCase(
    private val timetableImportRepository: TimetableImportRepository,
    private val getTimetableDetailUseCase: GetTimetableDetailUseCase
) {

    /**
     * Writes [submission] according to [target].
     *
     * @param scheduleId schedule template the resulting timetable should use. Required when creating
     *   a timetable; when overwriting, a non-positive value means "keep the timetable's current
     *   schedule". The caller is expected to have the user choose one, because an academic portal
     *   usually reports only period numbers and the app refuses to invent class times.
     */
    suspend operator fun invoke(
        submission: ImportSubmission,
        target: ImportTarget,
        scheduleId: Long
    ): TimetableImportResult {
        // Guarded here rather than only in the parser because this use case is the last point before
        // the write: overwriting a timetable with nothing would silently delete the user's courses.
        if (submission.courses.isEmpty()) {
            return TimetableImportResult.Rejected("导入数据里没有任何课程，已取消导入。")
        }

        val plan = when (target) {
            ImportTarget.NewTimetable -> resolveCreatePlan(submission, scheduleId)
            is ImportTarget.ExistingTimetable -> resolveOverwritePlan(target, scheduleId)
        }
        return when (plan) {
            is PlanResolution.Rejected -> TimetableImportResult.Rejected(plan.message)
            PlanResolution.TargetNotFound -> TimetableImportResult.TimetableNotFound
            is PlanResolution.Resolved -> timetableImportRepository.commit(
                plan = plan.plan,
                courses = buildCourses(submission.courses, plan.totalWeeks)
            )
        }
    }

    private fun resolveCreatePlan(
        submission: ImportSubmission,
        scheduleId: Long
    ): PlanResolution {
        if (scheduleId <= 0L) {
            return PlanResolution.Rejected("请先选择作息表，再执行导入。")
        }

        val payloadTimetable = submission.timetable
        val totalWeeks = payloadTimetable?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
        // The parse step already rejects an out-of-range value from a script, but the resolved value
        // is what actually gets stored, so the same invariant is enforced where the write is built.
        if (totalWeeks !in 1..MAX_SEMESTER_WEEK_COUNT) {
            return PlanResolution.Rejected(
                "总周数应在 1 到 $MAX_SEMESTER_WEEK_COUNT 之间，当前为 $totalWeeks。"
            )
        }

        return PlanResolution.Resolved(
            plan = TimetableImportPlan.CreateNew(
                name = payloadTimetable?.name ?: DEFAULT_TIMETABLE_NAME,
                totalWeeks = totalWeeks,
                startDate = payloadTimetable?.startDate ?: LocalDate.now(),
                scheduleId = scheduleId,
                // An import is almost always the act of "setting up this semester", so the result
                // should be what the user lands on.
                setActive = true
            ),
            totalWeeks = totalWeeks
        )
    }

    private suspend fun resolveOverwritePlan(
        target: ImportTarget.ExistingTimetable,
        scheduleId: Long
    ): PlanResolution {
        val existing = getTimetableDetailUseCase(target.timetableId)
            ?: return PlanResolution.TargetNotFound

        return PlanResolution.Resolved(
            plan = TimetableImportPlan.Overwrite(
                timetableId = existing.id,
                // Falling back to the current schedule keeps a caller that only intends to refresh
                // courses from silently unlinking the timetable's class times.
                scheduleId = scheduleId.takeIf { it > 0L } ?: existing.scheduleId
            ),
            totalWeeks = existing.totalWeeks
        )
    }

    /**
     * Builds the persistence payload for [courses].
     *
     * Course and session ids stay at zero: the target timetable may not exist yet, and the
     * repository assigns real ids while writing.
     */
    private fun buildCourses(
        courses: List<ImportedCourse>,
        totalWeeks: Int
    ): List<CourseWithSessions> {
        val now = System.currentTimeMillis()
        return courses.mapIndexed { index, imported ->
            CourseWithSessions(
                course = Course(
                    timetableId = 0L,
                    name = imported.name,
                    teacher = imported.teacher,
                    // Falling back to the palette by position keeps colors stable and reproducible
                    // for a given payload, and spreads neighboring courses across distinct colors.
                    color = imported.color ?: CourseColorPalette[index % CourseColorPalette.size],
                    note = imported.note,
                    createdAt = now
                ),
                sessions = imported.sessions.map { session ->
                    val weekRule = resolveImportWeekRule(session.weeks, totalWeeks)
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = session.dayOfWeek,
                        startPeriod = session.startPeriod,
                        endPeriod = session.endPeriod,
                        location = session.location,
                        weekType = weekRule.weekType,
                        startWeek = weekRule.startWeek,
                        endWeek = weekRule.endWeek,
                        customWeeks = weekRule.customWeeks
                    )
                }
            )
        }
    }

    /**
     * Result of turning user intent into a concrete plan. Kept private so callers only ever deal
     * with [TimetableImportResult].
     */
    private sealed interface PlanResolution {
        data class Resolved(val plan: TimetableImportPlan, val totalWeeks: Int) : PlanResolution
        data object TargetNotFound : PlanResolution
        data class Rejected(val message: String) : PlanResolution
    }

    private companion object {
        /**
         * Name used when an import script does not report one. Deliberately generic: the user can
         * rename the timetable afterwards, and inventing a semester label from today's date would
         * be wrong for a payload that describes a different term.
         */
        const val DEFAULT_TIMETABLE_NAME = "导入的课程表"

        /**
         * Semester length used when the payload omits it. Matches the value the timetable editor
         * pre-fills, so an imported timetable starts from the same assumption as a manual one.
         */
        const val DEFAULT_TOTAL_WEEKS = 16
    }
}
