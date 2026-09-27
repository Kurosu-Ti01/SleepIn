package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.MAX_SEMESTER_WEEK_COUNT
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.ImportScheduleDraft
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
        val scheduleId: Long,
        /**
         * Non-null when the import creates a schedule template from the payload's own class times.
         *
         * [scheduleId] is then 0: the template does not exist yet, and both the confirmation step
         * and the write have to describe the same thing, so the draft travels with the plan instead
         * of being rebuilt from the payload a second time.
         */
        val newSchedule: ImportScheduleDraft? = null
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
     * Resolves [submission] for [target] and [scheduleSource].
     *
     * @param scheduleSource where the class times come from. An existing schedule must be named (or,
     *   when overwriting, may be left at 0 to keep the timetable's current one);
     *   [ImportScheduleSource.FromPayload] requires the payload to have reported times, because the
     *   app does not invent them.
     */
    suspend fun resolve(
        submission: ImportSubmission,
        target: ImportTarget,
        scheduleSource: ImportScheduleSource
    ): ImportPlanResolution {
        val schedule = when (val resolved = resolveSchedule(submission, scheduleSource)) {
            is ScheduleResolution.Rejected -> return ImportPlanResolution.Rejected(resolved.message)
            is ScheduleResolution.Resolved -> resolved
        }
        return when (target) {
            ImportTarget.NewTimetable -> resolveCreatePlan(submission, schedule)
            is ImportTarget.ExistingTimetable -> resolveOverwritePlan(target, schedule)
        }
    }

    /**
     * Decides which schedule the result will reference: an existing one, or one built from the
     * payload's times.
     */
    private fun resolveSchedule(
        submission: ImportSubmission,
        scheduleSource: ImportScheduleSource
    ): ScheduleResolution = when (scheduleSource) {
        is ImportScheduleSource.Existing ->
            ScheduleResolution.Resolved(scheduleId = scheduleSource.scheduleId, newSchedule = null)

        ImportScheduleSource.FromPayload -> {
            val periods = submission.periods
            if (periods.isEmpty()) {
                ScheduleResolution.Rejected("该方案没有提供作息时间，请选择一个已有作息表。")
            } else {
                ScheduleResolution.Resolved(
                    scheduleId = 0L,
                    newSchedule = ImportScheduleDraft(
                        name = DEFAULT_SCHEDULE_NAME,
                        // scheduleId is assigned by the repository while writing; the draft carries
                        // times only.
                        periods = periods.map { period ->
                            SchedulePeriod(
                                scheduleId = 0L,
                                periodNumber = period.periodNumber,
                                startTime = period.startTime,
                                endTime = period.endTime
                            )
                        }
                    )
                )
            }
        }
    }

    private fun resolveCreatePlan(
        submission: ImportSubmission,
        schedule: ScheduleResolution.Resolved
    ): ImportPlanResolution {
        if (schedule.newSchedule == null && schedule.scheduleId <= 0L) {
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
                scheduleId = schedule.scheduleId,
                // An import is almost always the act of "setting up this semester", so the result
                // should be what the user lands on.
                setActive = true,
                newSchedule = schedule.newSchedule
            ),
            totalWeeks = totalWeeks,
            timetableName = name,
            scheduleId = schedule.scheduleId,
            newSchedule = schedule.newSchedule
        )
    }

    private suspend fun resolveOverwritePlan(
        target: ImportTarget.ExistingTimetable,
        schedule: ScheduleResolution.Resolved
    ): ImportPlanResolution {
        val existing = getTimetableDetailUseCase(target.timetableId)
            ?: return ImportPlanResolution.TargetNotFound

        // Falling back to the current schedule keeps a caller that only intends to refresh courses
        // from silently unlinking the timetable's class times.
        val resolvedScheduleId = when {
            schedule.newSchedule != null -> 0L
            schedule.scheduleId > 0L -> schedule.scheduleId
            else -> existing.scheduleId
        }
        return ImportPlanResolution.Resolved(
            plan = TimetableImportPlan.Overwrite(
                timetableId = existing.id,
                scheduleId = resolvedScheduleId,
                newSchedule = schedule.newSchedule
            ),
            totalWeeks = existing.totalWeeks,
            timetableName = existing.name,
            scheduleId = resolvedScheduleId,
            newSchedule = schedule.newSchedule
        )
    }

    /**
     * Outcome of deciding where the class times come from. Private because callers only ever see the
     * result of the whole resolution.
     */
    private sealed interface ScheduleResolution {

        /** @param scheduleId existing template id, or 0 when [newSchedule] will be created. */
        data class Resolved(
            val scheduleId: Long,
            val newSchedule: ImportScheduleDraft?
        ) : ScheduleResolution

        data class Rejected(val message: String) : ScheduleResolution
    }

    internal companion object {
        /**
         * Name used when an import script does not report one. Deliberately generic: the user can
         * rename the timetable afterwards, and inventing a semester label from today's date would
         * be wrong for a payload that describes a different term.
         */
        const val DEFAULT_TIMETABLE_NAME = "导入的课程表"

        /**
         * Name for a schedule template built from the times a portal reported.
         *
         * Says where it came from so the user can tell it apart from their own templates when they
         * later edit or delete it.
         */
        const val DEFAULT_SCHEDULE_NAME = "导入的作息表"

        /**
         * Semester length used when the payload omits it. Matches the value the timetable editor
         * pre-fills, so an imported timetable starts from the same assumption as a manual one.
         */
        const val DEFAULT_TOTAL_WEEKS = 16
    }
}
