package com.kurosu.sleepin.ui.screen.home

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Describes where "today" is located relative to one semester window.
 */
enum class SemesterProgress {
    BEFORE_START,
    IN_PROGRESS,
    AFTER_END
}

/**
 * Pure date-calculation result used by Home screen to decide week selection and status messages.
 *
 * [currentWeek] is only non-null when [progress] is [SemesterProgress.IN_PROGRESS].
 */
data class SemesterWeekInfo(
    val progress: SemesterProgress,
    val currentWeek: Int?,
    val semesterStartDate: LocalDate,
    val semesterEndDate: LocalDate
)

/**
 * Calculates semester progress and current week index using the timetable's configured start date.
 *
 * Assumption: week 1 day 1 starts at [startDate], and each week has exactly 7 days.
 */
fun calculateSemesterWeekInfo(
    startDate: LocalDate,
    totalWeeks: Int,
    today: LocalDate
): SemesterWeekInfo {
    val safeTotalWeeks = totalWeeks.coerceAtLeast(1)
    val semesterEndDate = startDate.plusDays((safeTotalWeeks * 7L) - 1L)

    if (today.isBefore(startDate)) {
        return SemesterWeekInfo(
            progress = SemesterProgress.BEFORE_START,
            currentWeek = null,
            semesterStartDate = startDate,
            semesterEndDate = semesterEndDate
        )
    }

    if (today.isAfter(semesterEndDate)) {
        return SemesterWeekInfo(
            progress = SemesterProgress.AFTER_END,
            currentWeek = null,
            semesterStartDate = startDate,
            semesterEndDate = semesterEndDate
        )
    }

    val daysFromStart = ChronoUnit.DAYS.between(startDate, today)
    val currentWeek = (daysFromStart / 7L).toInt() + 1

    return SemesterWeekInfo(
        progress = SemesterProgress.IN_PROGRESS,
        currentWeek = currentWeek,
        semesterStartDate = startDate,
        semesterEndDate = semesterEndDate
    )
}

/**
 * Returns exactly 7 dates for one semester week, Monday-slot to Sunday-slot.
 */
fun buildWeekDates(startDate: LocalDate, week: Int): List<LocalDate> {
    val safeWeek = week.coerceAtLeast(1)
    val firstDate = startDate.plusDays((safeWeek - 1L) * 7L)
    return List(7) { index -> firstDate.plusDays(index.toLong()) }
}

