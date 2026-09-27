package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseColorPalette
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.repository.TimetableImportRepository
import com.kurosu.sleepin.domain.repository.TimetableImportResult
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase

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
 * Where the class times of the imported timetable should come from.
 *
 * Two sources are possible and the user picks between them at confirmation time, which is the first
 * moment the app knows whether the script actually read a time table: an academic portal may report
 * only period numbers, and the app then refuses to invent the times.
 */
sealed interface ImportScheduleSource {

    /**
     * Use a schedule template the user already has.
     *
     * A non-positive id means "keep the target timetable's current template", which only makes sense
     * when overwriting.
     */
    data class Existing(val scheduleId: Long) : ImportScheduleSource

    /**
     * Create a schedule template from the times the payload reported.
     *
     * Refused when the payload carries none, rather than silently falling back to an existing
     * template: the user asked for the portal's times and would otherwise not be told they were
     * missing.
     */
    data object FromPayload : ImportScheduleSource
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
 * Those target rules live in [ImportPlanResolver], which is shared with [BuildImportPreviewUseCase] so
 * the summary shown before the write cannot describe a different outcome than this call produces.
 * Persistence itself is delegated to [TimetableImportRepository], whose implementation performs the
 * whole write inside one transaction.
 */
class CommitImportUseCase(
    private val timetableImportRepository: TimetableImportRepository,
    getTimetableDetailUseCase: GetTimetableDetailUseCase
) {

    private val planResolver = ImportPlanResolver(getTimetableDetailUseCase)

    /**
     * Writes [submission] according to [target] and [scheduleSource].
     *
     * @param scheduleSource where the result's class times come from. [ImportScheduleSource.Existing]
     *   with a non-positive id means "keep the timetable's current schedule";
     *   [ImportScheduleSource.FromPayload] builds a new template from the payload's own times and is
     *   refused when it carried none.
     */
    suspend operator fun invoke(
        submission: ImportSubmission,
        target: ImportTarget,
        scheduleSource: ImportScheduleSource
    ): TimetableImportResult {
        // Guarded here rather than only in the parser because this use case is the last point before
        // the write: overwriting a timetable with nothing would silently delete the user's courses.
        if (submission.courses.isEmpty()) {
            return TimetableImportResult.Rejected("导入数据里没有任何课程，已取消导入。")
        }

        val plan = planResolver.resolve(submission, target, scheduleSource)
        return when (plan) {
            is ImportPlanResolution.Rejected -> TimetableImportResult.Rejected(plan.message)
            ImportPlanResolution.TargetNotFound -> TimetableImportResult.TimetableNotFound
            is ImportPlanResolution.Resolved -> timetableImportRepository.commit(
                plan = plan.plan,
                courses = buildCourses(submission.courses, plan.totalWeeks)
            )
        }
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
}
