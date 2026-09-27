package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase

/**
 * Everything the confirmation step needs to describe an import before it happens.
 *
 * Also handed back after the write so the success notice can reuse the same numbers and the same
 * schedule reference, instead of recomputing them from a payload that is no longer at hand.
 */
data class ImportPreview(
    /** Target the write will use, resolved from the user's choice. */
    val target: ImportTarget,
    /** User-facing description of [target], resolved here so both dialogs name it identically. */
    val targetLabel: String,
    /** Name the target timetable will have after the import. */
    val timetableName: String,
    /** Where the class times come from; carried so the write uses what the user confirmed. */
    val scheduleSource: ImportScheduleSource,
    /**
     * Schedule the result will reference, or 0 when the import creates one.
     *
     * Zero means "not decided yet": the template is written together with the courses, and the real
     * id comes back in the write result.
     */
    val scheduleId: Long,
    /** User-facing description of where the class times come from. */
    val scheduleLabel: String,
    /**
     * How many class times the payload reported.
     *
     * Zero means this portal only reports period numbers, and the confirmation step then offers no
     * choice at all rather than an option that would have to be refused.
     */
    val importedPeriodCount: Int,
    val totalWeeks: Int,
    val diagnostics: ImportDiagnostics
)

/**
 * Outcome of describing an import before writing it.
 *
 * Mirrors the failures of the write itself so an impossible import is refused while the user is still
 * looking at a summary, rather than after they confirm one.
 */
sealed interface ImportPreviewResult {

    data class Ready(val preview: ImportPreview) : ImportPreviewResult

    /** The request was refused before anything could be described. [message] is user-facing. */
    data class Rejected(val message: String) : ImportPreviewResult

    /** The referenced schedule template does not exist. */
    data object ScheduleNotFound : ImportPreviewResult

    /** The timetable to overwrite does not exist (any more). */
    data object TimetableNotFound : ImportPreviewResult
}

/**
 * Describes what an import would store, without storing anything.
 *
 * This is what makes the confirmation dialog trustworthy: the numbers, the target's name and the
 * schedule the result will reference are the ones the subsequent [CommitImportUseCase] call resolves
 * again through the same [ImportPlanResolver], so the summary cannot drift from the write.
 *
 * The diagnostics it produces are deliberately not a gate. A payload that references periods the
 * user's schedule does not define, weeks beyond the semester, or two courses sharing a slot is still
 * imported — dropping those sessions would lose classes the portal really reports. They are reported
 * so the user can extend the schedule or fix their portal selection instead.
 */
class BuildImportPreviewUseCase(
    getTimetableDetailUseCase: GetTimetableDetailUseCase,
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase
) {

    private val planResolver = ImportPlanResolver(getTimetableDetailUseCase)

    suspend operator fun invoke(
        submission: ImportSubmission,
        target: ImportTarget,
        scheduleSource: ImportScheduleSource
    ): ImportPreviewResult {
        val resolved = when (val resolution = planResolver.resolve(submission, target, scheduleSource)) {
            is ImportPlanResolution.Rejected -> return ImportPreviewResult.Rejected(resolution.message)
            ImportPlanResolution.TargetNotFound -> return ImportPreviewResult.TimetableNotFound
            is ImportPlanResolution.Resolved -> resolution
        }

        // Which periods count as "defined" depends on the source the user chose: the template that
        // is about to be created, or the one already on the device. Getting this from the same
        // resolved plan the write uses is what keeps the two dialogs from disagreeing.
        val scheduleLabel: String
        val definedPeriods: Set<Int>
        val newSchedule = resolved.newSchedule
        if (newSchedule != null) {
            scheduleLabel = "新建作息表（$CREATED_SCHEDULE_LABEL_SUFFIX，${newSchedule.periods.size} 节）"
            definedPeriods = newSchedule.periods.mapTo(mutableSetOf()) { period -> period.periodNumber }
        } else {
            val schedule = getScheduleDetailUseCase(resolved.scheduleId)
                ?: return ImportPreviewResult.ScheduleNotFound
            scheduleLabel = schedule.schedule.name
            definedPeriods = schedule.periods.mapTo(mutableSetOf()) { period -> period.periodNumber }
        }

        return ImportPreviewResult.Ready(
            ImportPreview(
                target = target,
                targetLabel = targetLabel(target, resolved.timetableName),
                timetableName = resolved.timetableName,
                scheduleSource = scheduleSource,
                scheduleId = resolved.scheduleId,
                scheduleLabel = scheduleLabel,
                importedPeriodCount = submission.periods.size,
                totalWeeks = resolved.totalWeeks,
                diagnostics = buildImportDiagnostics(
                    submission = submission,
                    definedPeriods = definedPeriods,
                    totalWeeks = resolved.totalWeeks
                )
            )
        )
    }

    private fun targetLabel(target: ImportTarget, timetableName: String): String = when (target) {
        ImportTarget.NewTimetable -> "新建课程表"
        is ImportTarget.ExistingTimetable -> "覆盖「$timetableName」"
    }

    private companion object {
        /** Wording for a template that will be created from the portal's own times. */
        const val CREATED_SCHEDULE_LABEL_SUFFIX = "来自方案"
    }
}
