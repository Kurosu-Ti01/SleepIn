package com.kurosu.sleepin.ui.screen.home

import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.MAX_SUPPORTED_PERIOD_NUMBER
import com.kurosu.sleepin.domain.model.SchedulePeriod
import java.time.Duration
import java.time.LocalTime

/**
 * One row of the week grid.
 *
 * Rows are dense from period 1, so a row index always equals `periodNumber - 1`. The grid relies on
 * that to place course blocks, which is why this is a row model rather than a copy of the schedule's
 * own period list.
 */
data class HomePeriodUi(
    val periodNumber: Int,
    /**
     * Class start time, or null when the schedule does not configure this period yet.
     *
     * Null is the single marker for "not configured": a stored schedule period always has both times,
     * so a separate flag would only be able to disagree with these fields.
     */
    val startTime: LocalTime?,
    /** Class end time, or null when the schedule does not configure this period yet. */
    val endTime: LocalTime?
)

/**
 * Builds the rows the week grid renders.
 *
 * The schedule supplies class times, but courses may reference period numbers the schedule does not
 * define yet — an academic portal can report period 13 for a schedule that stops at 12. Those rows
 * are still built, without times, so the affected sessions remain visible and the user can see which
 * periods are missing instead of the classes silently disappearing off the bottom of the grid.
 *
 * Rows are filled in densely up to the highest number either side uses, so a gap in the middle is
 * rendered as an unconfigured row rather than shifting everything below it up. Period 0 and below
 * are ignored because they cannot be addressed by a row index.
 *
 * The highest number is one for one with the row count, so it is capped at
 * [MAX_SUPPORTED_PERIOD_NUMBER] — the same bound every other consumer applies to a period number.
 * Only data written before the schedule editor enforced that bound can exceed it, and without the cap
 * such a value would ask for an enormous grid.
 *
 * @param schedulePeriods periods defined by the active timetable's schedule, in any order.
 * @param courses courses of the active timetable, used only to find the highest referenced period.
 */
fun buildHomePeriodRows(
    schedulePeriods: List<SchedulePeriod>,
    courses: List<CourseWithSessions>
): List<HomePeriodUi> {
    val timesByPeriodNumber = schedulePeriods
        .filter { period -> period.periodNumber > 0 }
        .associate { period -> period.periodNumber to period }

    val highestUsedPeriodNumber = courses
        .flatMap { aggregate -> aggregate.sessions }
        .maxOfOrNull { session -> maxOf(session.startPeriod, session.endPeriod) }
        ?: 0
    val highestPeriodNumber = minOf(
        maxOf(
            timesByPeriodNumber.keys.maxOrNull() ?: 0,
            highestUsedPeriodNumber
        ),
        MAX_SUPPORTED_PERIOD_NUMBER
    )
    if (highestPeriodNumber < 1) return emptyList()

    return (1..highestPeriodNumber).map { periodNumber ->
        val period = timesByPeriodNumber[periodNumber]
        HomePeriodUi(
            periodNumber = periodNumber,
            startTime = period?.startTime,
            endTime = period?.endTime
        )
    }
}

/**
 * Converts the current time into a floating row offset for the current-time indicator, where `0.0`
 * is the top of row 1 and `N` is the boundary below row N.
 *
 * Rows without times are skipped when interpolating but still counted, so the indicator stays on the
 * row that actually holds that time even when the schedule has unconfigured periods above it.
 *
 * @return null when [nowTime] is outside the configured class hours, so the indicator is not drawn.
 */
fun resolveCurrentTimeRowOffset(rows: List<HomePeriodUi>, nowTime: LocalTime): Float? {
    val configuredRows = rows.withIndex().filter { (_, row) ->
        row.startTime != null && row.endTime != null
    }
    if (configuredRows.isEmpty()) return null

    val firstStart = configuredRows.first().value.startTime ?: return null
    val lastEnd = configuredRows.last().value.endTime ?: return null
    if (nowTime.isBefore(firstStart) || nowTime.isAfter(lastEnd)) return null

    configuredRows.forEachIndexed { position, (rowIndex, row) ->
        val startTime = row.startTime ?: return@forEachIndexed
        val endTime = row.endTime ?: return@forEachIndexed

        if (!nowTime.isBefore(startTime) && nowTime.isBefore(endTime)) {
            // Distinguish "inside a period" from "in the break before the next one" by the row's own
            // duration rather than by the gap, so breaks do not stretch the indicator's travel.
            val periodSeconds = Duration.between(startTime, endTime).seconds.coerceAtLeast(1)
            val elapsedSeconds = Duration.between(startTime, nowTime).seconds.coerceAtLeast(0)
            return rowIndex + (elapsedSeconds.toFloat() / periodSeconds.toFloat())
        }

        val nextRow = configuredRows.getOrNull(position + 1)?.value
        val nextStart = nextRow?.startTime
        if (nextStart != null && !nowTime.isBefore(endTime) && nowTime.isBefore(nextStart)) {
            return (rowIndex + 1).toFloat()
        }
    }

    return if (nowTime == lastEnd) {
        (configuredRows.last().index + 1).toFloat()
    } else {
        null
    }
}
