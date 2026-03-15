package com.kurosu.sleepin.ui.screen.home

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for week/date calculations used by Home week view.
 */
class HomeWeekDateCalculatorTest {

    @Test
    fun `calculateSemesterWeekInfo returns BEFORE_START when today is before semester`() {
        val startDate = LocalDate.of(2026, 3, 2)

        val result = calculateSemesterWeekInfo(
            startDate = startDate,
            totalWeeks = 18,
            today = LocalDate.of(2026, 3, 1)
        )

        assertEquals(SemesterProgress.BEFORE_START, result.progress)
        assertNull(result.currentWeek)
        assertEquals(LocalDate.of(2026, 7, 5), result.semesterEndDate)
    }

    @Test
    fun `calculateSemesterWeekInfo returns IN_PROGRESS with expected week index`() {
        val startDate = LocalDate.of(2026, 3, 2)

        val result = calculateSemesterWeekInfo(
            startDate = startDate,
            totalWeeks = 18,
            today = LocalDate.of(2026, 3, 16)
        )

        assertEquals(SemesterProgress.IN_PROGRESS, result.progress)
        assertEquals(3, result.currentWeek)
    }

    @Test
    fun `calculateSemesterWeekInfo returns AFTER_END when semester already ended`() {
        val startDate = LocalDate.of(2026, 3, 2)

        val result = calculateSemesterWeekInfo(
            startDate = startDate,
            totalWeeks = 18,
            today = LocalDate.of(2026, 7, 6)
        )

        assertEquals(SemesterProgress.AFTER_END, result.progress)
        assertNull(result.currentWeek)
    }

    @Test
    fun `buildWeekDates returns seven consecutive dates for selected week`() {
        val startDate = LocalDate.of(2026, 3, 2)

        val weekDates = buildWeekDates(startDate = startDate, week = 2)

        assertEquals(7, weekDates.size)
        assertEquals(LocalDate.of(2026, 3, 9), weekDates.first())
        assertEquals(LocalDate.of(2026, 3, 15), weekDates.last())
    }
}

