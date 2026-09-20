package com.kurosu.sleepin.ui.screen.home

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Unit tests for the week grid's row model.
 *
 * The rule under test is that a course is never lost to the layout: rows are filled densely up to
 * the highest period either the schedule or the courses use, so a period the schedule has not
 * configured yet still gets a row instead of pushing the class off the bottom of the grid.
 */
class HomePeriodRowsTest {

    @Test
    fun buildHomePeriodRows_usesScheduleTimes_forConfiguredPeriods() {
        val rows = buildHomePeriodRows(
            schedulePeriods = listOf(
                schedulePeriod(periodNumber = 1, start = "08:00", end = "08:45"),
                schedulePeriod(periodNumber = 2, start = "08:55", end = "09:40")
            ),
            courses = emptyList()
        )

        assertEquals(listOf(1, 2), rows.map { it.periodNumber })
        assertEquals(LocalTime.of(8, 0), rows.first().startTime)
        assertEquals(LocalTime.of(9, 40), rows.last().endTime)
    }

    @Test
    fun buildHomePeriodRows_addsUnconfiguredRows_forPeriodsOnlyCoursesUse() {
        val rows = buildHomePeriodRows(
            schedulePeriods = listOf(
                schedulePeriod(periodNumber = 1, start = "08:00", end = "08:45"),
                schedulePeriod(periodNumber = 2, start = "08:55", end = "09:40")
            ),
            courses = listOf(courseWithSessions(startPeriod = 1, endPeriod = 4))
        )

        assertEquals(listOf(1, 2, 3, 4), rows.map { it.periodNumber })
        assertNull(rows[2].startTime)
        assertNull(rows[3].endTime)
    }

    @Test
    fun buildHomePeriodRows_fillsGapsInTheMiddle_whenScheduleSkipsANumber() {
        val rows = buildHomePeriodRows(
            schedulePeriods = listOf(
                schedulePeriod(periodNumber = 1, start = "08:00", end = "08:45"),
                schedulePeriod(periodNumber = 3, start = "10:00", end = "10:45")
            ),
            courses = emptyList()
        )

        // Row index must stay equal to periodNumber - 1 for every row, including the gap, because the
        // grid places course blocks by index.
        assertEquals(listOf(1, 2, 3), rows.map { it.periodNumber })
        assertNull(rows[1].startTime)
        assertEquals(LocalTime.of(10, 0), rows[2].startTime)
    }

    @Test
    fun buildHomePeriodRows_returnsNoRows_whenNeitherSideUsesAnyPeriod() {
        val rows = buildHomePeriodRows(schedulePeriods = emptyList(), courses = emptyList())

        assertTrue(rows.isEmpty())
    }

    @Test
    fun resolveCurrentTimeRowOffset_returnsNull_whenNoRowIsConfigured() {
        val rows = listOf(
            HomePeriodUi(periodNumber = 1, startTime = null, endTime = null),
            HomePeriodUi(periodNumber = 2, startTime = null, endTime = null)
        )

        assertNull(resolveCurrentTimeRowOffset(rows, LocalTime.of(9, 0)))
    }

    @Test
    fun resolveCurrentTimeRowOffset_returnsNull_outsideClassHours() {
        val rows = listOf(HomePeriodUi(periodNumber = 1, LocalTime.of(8, 0), LocalTime.of(9, 0)))

        assertNull(resolveCurrentTimeRowOffset(rows, LocalTime.of(7, 0)))
        assertNull(resolveCurrentTimeRowOffset(rows, LocalTime.of(9, 1)))
    }

    @Test
    fun resolveCurrentTimeRowOffset_countsUnconfiguredRows_aboveTheMatchingPeriod() {
        val rows = listOf(
            HomePeriodUi(periodNumber = 1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            HomePeriodUi(periodNumber = 2, startTime = null, endTime = null),
            HomePeriodUi(periodNumber = 3, LocalTime.of(10, 0), LocalTime.of(10, 45))
        )

        // Period 3 sits on row index 2, so the indicator must be there and not on index 1.
        assertEquals(2.0f, resolveCurrentTimeRowOffset(rows, LocalTime.of(10, 0)))
    }

    @Test
    fun resolveCurrentTimeRowOffset_interpolatesWithinAPeriod() {
        val rows = listOf(HomePeriodUi(periodNumber = 1, LocalTime.of(8, 0), LocalTime.of(9, 0)))

        assertEquals(0.5f, resolveCurrentTimeRowOffset(rows, LocalTime.of(8, 30)))
    }

    @Test
    fun resolveCurrentTimeRowOffset_returnsTheRowBoundary_duringABreak() {
        val rows = listOf(
            HomePeriodUi(periodNumber = 1, LocalTime.of(8, 0), LocalTime.of(9, 0)),
            HomePeriodUi(periodNumber = 2, LocalTime.of(10, 0), LocalTime.of(11, 0))
        )

        assertEquals(1.0f, resolveCurrentTimeRowOffset(rows, LocalTime.of(9, 30)))
    }

    @Test
    fun buildHomePeriodRows_buildsRowsFromCourses_whenTheScheduleHasNoPeriods() {
        val rows = buildHomePeriodRows(
            schedulePeriods = emptyList(),
            courses = listOf(courseWithSessions(startPeriod = 1, endPeriod = 3))
        )

        // The schedule being empty must not hide the courses: every referenced period needs a row.
        assertEquals(listOf(1, 2, 3), rows.map { it.periodNumber })
        assertNull(rows.first().startTime)
    }

    @Test
    fun resolveCurrentTimeRowOffset_returnsTheBottomBoundary_atTheLastEnd() {
        val rows = listOf(HomePeriodUi(periodNumber = 1, LocalTime.of(8, 0), LocalTime.of(9, 0)))

        assertEquals(1.0f, resolveCurrentTimeRowOffset(rows, LocalTime.of(9, 0)))
    }

    @Test
    fun resolveCurrentTimeRowOffset_skipsUnconfiguredRows_whenLookingForTheNextPeriod() {
        val rows = listOf(
            HomePeriodUi(periodNumber = 1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            HomePeriodUi(periodNumber = 2, startTime = null, endTime = null),
            HomePeriodUi(periodNumber = 3, LocalTime.of(10, 0), LocalTime.of(10, 45))
        )

        // The break must be measured against the next *configured* period, not the physically next row:
        // reading the unconfigured row as "the next period" would drop the indicator entirely here.
        assertEquals(1.0f, resolveCurrentTimeRowOffset(rows, LocalTime.of(9, 0)))
    }

    private fun schedulePeriod(periodNumber: Int, start: String, end: String) = SchedulePeriod(
        id = periodNumber.toLong(),
        scheduleId = 1L,
        periodNumber = periodNumber,
        startTime = LocalTime.parse(start),
        endTime = LocalTime.parse(end)
    )

    private fun courseWithSessions(startPeriod: Int, endPeriod: Int) = CourseWithSessions(
        course = Course(
            id = 1L,
            timetableId = 1L,
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            createdAt = 0L
        ),
        sessions = listOf(
            CourseSession(
                id = 1L,
                courseId = 1L,
                dayOfWeek = 1,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                location = null,
                weekType = WeekType.ALL,
                startWeek = null,
                endWeek = null,
                customWeeks = emptyList()
            )
        )
    )
}
