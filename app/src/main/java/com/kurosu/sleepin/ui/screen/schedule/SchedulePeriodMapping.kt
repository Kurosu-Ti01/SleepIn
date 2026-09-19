package com.kurosu.sleepin.ui.screen.schedule

import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase
import java.time.LocalTime

/**
 * Outcome of converting the schedule editor's text rows into persistence drafts.
 */
sealed interface SchedulePeriodDraftResult {
    /** Every row was well formed. */
    data class Ready(val drafts: List<SaveScheduleUseCase.PeriodDraft>) : SchedulePeriodDraftResult

    /** One row was not usable. [message] is user-facing and names the offending row. */
    data class Invalid(val message: String) : SchedulePeriodDraftResult
}

/**
 * Maps stored periods to editor rows, keeping each period's own number and row id.
 *
 * Together with [buildSchedulePeriodDrafts] this forms the round trip an edit performs. Both halves
 * preserve the number rather than deriving it from a position, so opening and saving a schedule can
 * never renumber it. Loading is sorted by number because the editor shows rows in that order.
 */
fun toEditableSchedulePeriods(periods: List<SchedulePeriod>): List<EditableSchedulePeriod> =
    periods
        .sortedBy { period -> period.periodNumber }
        .map { period ->
            EditableSchedulePeriod(
                id = period.id,
                periodNumber = period.periodNumber.toString(),
                startTime = period.startTime.toString(),
                endTime = period.endTime.toString()
            )
        }

/**
 * Converts editor rows into persistence drafts, preserving each row's own period number.
 *
 * The period number comes from the row rather than from its position on purpose. A schedule may
 * legitimately skip a number — a CSV export with a gap, a portal with no class in one slot, or a
 * period list the user extended by hand — and renumbering from the row position would silently move
 * every course that referenced the skipped number out of the schedule's range.
 *
 * This lives outside the ViewModel so the rule is unit-testable on the JVM: a ViewModel test would
 * need `Dispatchers.Main`, which this project's test setup does not provide.
 */
fun buildSchedulePeriodDrafts(periods: List<EditableSchedulePeriod>): SchedulePeriodDraftResult {
    val drafts = mutableListOf<SaveScheduleUseCase.PeriodDraft>()
    periods.forEachIndexed { index, period ->
        val position = index + 1
        val periodNumber = period.periodNumber.trim().toIntOrNull()
            ?: return SchedulePeriodDraftResult.Invalid("第 $position 行的节次编号应为整数")
        if (periodNumber <= 0) {
            return SchedulePeriodDraftResult.Invalid("第 $position 行的节次编号应大于 0")
        }
        val startTime = parseScheduleTime(period.startTime)
            ?: return SchedulePeriodDraftResult.Invalid("第 $position 行的开始时间格式应为 HH:mm")
        val endTime = parseScheduleTime(period.endTime)
            ?: return SchedulePeriodDraftResult.Invalid("第 $position 行的结束时间格式应为 HH:mm")

        drafts += SaveScheduleUseCase.PeriodDraft(
            id = period.id,
            periodNumber = periodNumber,
            startTime = startTime,
            endTime = endTime
        )
    }
    return SchedulePeriodDraftResult.Ready(drafts)
}

/**
 * Parses `HH:mm`-like text into [LocalTime], or null when the text is not a complete time.
 *
 * Shared with the quick-generation panel so both entry points accept exactly the same input.
 */
fun parseScheduleTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value.trim()) }.getOrNull()
