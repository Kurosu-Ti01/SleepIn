package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.MAX_SEMESTER_WEEK_COUNT
import com.kurosu.sleepin.domain.repository.TimetableImportPlan
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import java.time.LocalDate

/**
 * Result of turning a payload and a chosen target into a concrete persistence plan.
 *
 * Kept package-private so callers only deal with the result types of the use case they called:
 * [CommitImportUseCase] reports a [com.kurosu.sleepin.domain.repository.TimetableImportResult] and
 * [BuildImportPreviewUseCase] reports an [ImportPreviewResult].
 */
internal sealed interface ImportPlanResolution {

    /**
     * A plan that can be written or previewed.
     *
     * @param totalWeeks semester length the write will use. Resolved here because it comes from the
     *   payload for a new timetable and from the stored timetable when overwriting.
     * @param timetableName name the target timetable will have, resolved for the same reason.
     * @param scheduleId schedule the timetable will reference *after* the import, which is what both
     *   the write and the schedule-mismatch check have to agree on.
     */
    data class Resolved(
        val plan: TimetableImportPlan,
        val totalWeeks: Int,
        val timetableName: String,
        val scheduleId: Long
    ) : ImportPlanResolution

    /** The timetable to overwrite does not exist (any more). */
    data object TargetNotFound : ImportPlanResolution

    /** The request was refused before anything could be planned. [message] is user-facing. */
    data class Rejected(val message: String) : ImportPlanResolution
}

/**
 * Turns user intent into the plan that decides what an import writes.
 *
 * Shared by the confirmation step and the write itself: both must resolve the same target, name, week
 * count and schedule, and the only way to guarantee that is to have one implementation of the rules.
 * A confirmation that resolved them separately could describe a different write than the one that
 * follows it.
 */
internal class ImportPlanResolver(
    private val getTimetableDetailUseCase: GetTimetableDetailUseCase
) {

    /**
     * Resolves [submission] for [target].
     *
     * @param scheduleId schedule the user chose. Required when creating a timetable; when overwriting,
     *   a non-positive value means "keep the timetable's current schedule". The caller is expected to
     *   have the user choose one, because an academic portal usually reports only period numbers and
     *   the app refuses to invent class times.
     */
    suspend fun resolve(
        submission: ImportSubmission,
        target: ImportTarget,
        scheduleId: Long
    ): ImportPlanResolution = when (target) {
        ImportTarget.NewTimetable -> resolveCreatePlan(submission, scheduleId)
        is ImportTarget.ExistingTimetable -> resolveOverwritePlan(target, scheduleId)
    }

    private fun resolveCreatePlan(
        submission: ImportSubmission,
        scheduleId: Long
    ): ImportPlanResolution {
        if (scheduleId <= 0L) {
            return ImportPlanResolution.Rejected("请先选择作息表，再执行导入。")
        }

        val payloadTimetable = submission.timetable
        val totalWeeks = payloadTimetable?.totalWeeks ?: DEFAULT_TOTAL_WEEKS
        // The parse step already rejects an out-of-range value from a script, but the resolved value
        // is what actually gets stored, so the same invariant is enforced where the write is built.
        if (totalWeeks !in 1..MAX_SEMESTER_WEEK_COUNT) {
            return ImportPlanResolution.Rejected(
                "总周数应在 1 到 $MAX_SEMESTER_WEEK_COUNT 之间，当前为 $totalWeeks。"
            )
        }

        val name = payloadTimetable?.name ?: DEFAULT_TIMETABLE_NAME
        return ImportPlanResolution.Resolved(
            plan = TimetableImportPlan.CreateNew(
                name = name,
                totalWeeks = totalWeeks,
                startDate = payloadTimetable?.startDate ?: LocalDate.now(),
                scheduleId = scheduleId,
                // An import is almost always the act of "setting up this semester", so the result
                // should be what the user lands on.
                setActive = true
            ),
            totalWeeks = totalWeeks,
            timetableName = name,
            scheduleId = scheduleId
        )
    }

    private suspend fun resolveOverwritePlan(
        target: ImportTarget.ExistingTimetable,
        scheduleId: Long
    ): ImportPlanResolution {
        val existing = getTimetableDetailUseCase(target.timetableId)
            ?: return ImportPlanResolution.TargetNotFound

        // Falling back to the current schedule keeps a caller that only intends to refresh courses
        // from silently unlinking the timetable's class times.
        val resolvedScheduleId = scheduleId.takeIf { it > 0L } ?: existing.scheduleId
        return ImportPlanResolution.Resolved(
            plan = TimetableImportPlan.Overwrite(
                timetableId = existing.id,
                scheduleId = resolvedScheduleId
            ),
            totalWeeks = existing.totalWeeks,
            timetableName = existing.name,
            scheduleId = resolvedScheduleId
        )
    }

    internal companion object {
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
