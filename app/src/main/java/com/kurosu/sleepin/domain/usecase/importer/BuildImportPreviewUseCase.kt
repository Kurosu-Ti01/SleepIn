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
    /** Schedule the resulting timetable will reference. */
    val scheduleId: Long,
    val scheduleLabel: String,
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
        scheduleId: Long
    ): ImportPreviewResult {
        val resolved = when (val resolution = planResolver.resolve(submission, target, scheduleId)) {
            is ImportPlanResolution.Rejected -> return ImportPreviewResult.Rejected(resolution.message)
            ImportPlanResolution.TargetNotFound -> return ImportPreviewResult.TimetableNotFound
            is ImportPlanResolution.Resolved -> resolution
        }

        val schedule = getScheduleDetailUseCase(resolved.scheduleId)
            ?: return ImportPreviewResult.ScheduleNotFound

        return ImportPreviewResult.Ready(
            ImportPreview(
                target = target,
                targetLabel = targetLabel(target, resolved.timetableName),
                timetableName = resolved.timetableName,
                scheduleId = resolved.scheduleId,
                scheduleLabel = schedule.schedule.name,
                totalWeeks = resolved.totalWeeks,
                diagnostics = buildImportDiagnostics(
                    submission = submission,
                    definedPeriods = schedule.periods.map { period -> period.periodNumber }.toSet(),
                    totalWeeks = resolved.totalWeeks
                )
            )
        )
    }

    private fun targetLabel(target: ImportTarget, timetableName: String): String = when (target) {
        ImportTarget.NewTimetable -> "新建课程表"
        is ImportTarget.ExistingTimetable -> "覆盖「$timetableName」"
    }
}
