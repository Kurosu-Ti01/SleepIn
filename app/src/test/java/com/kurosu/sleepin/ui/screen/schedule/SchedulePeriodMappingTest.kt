package com.kurosu.sleepin.ui.screen.schedule

import com.kurosu.sleepin.domain.model.SchedulePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Unit tests for the schedule editor's row mapping.
 *
 * The rule under test is that a period number is always the value stored on the row and never its
 * position. A schedule that skips a number must survive both loading and saving, otherwise every
 * course referencing the skipped number silently falls outside the schedule's range.
 */
class SchedulePeriodMappingTest {

    @Test
    fun buildSchedulePeriodDrafts_keepsSparseNumbers_insteadOfRenumberingByPosition() {
        val result = buildSchedulePeriodDrafts(
            listOf(
                periodRow(id = 1L, periodNumber = "1", start = "08:00", end = "08:45"),
                periodRow(id = 2L, periodNumber = "2", start = "08:55", end = "09:40"),
                periodRow(id = 3L, periodNumber = "12", start = "19:00", end = "19:45")
            )
        )

        assertTrue("expected valid drafts but got $result", result is SchedulePeriodDraftResult.Ready)
        val drafts = (result as SchedulePeriodDraftResult.Ready).drafts
        assertEquals(listOf(1, 2, 12), drafts.map { it.periodNumber })
        // Existing row ids must survive as well, otherwise every save would rewrite the periods.
        assertEquals(listOf(1L, 2L, 3L), drafts.map { it.id })
    }

    @Test
    fun buildSchedulePeriodDrafts_trimsAndParsesTimes() {
        val result = buildSchedulePeriodDrafts(
            listOf(periodRow(id = 0L, periodNumber = " 3 ", start = " 08:00 ", end = "08:45"))
        )

        assertTrue("expected valid drafts but got $result", result is SchedulePeriodDraftResult.Ready)
        val draft = (result as SchedulePeriodDraftResult.Ready).drafts.single()
        assertEquals(3, draft.periodNumber)
        assertEquals(LocalTime.of(8, 0), draft.startTime)
        assertEquals(LocalTime.of(8, 45), draft.endTime)
    }

    @Test
    fun toEditableSchedulePeriods_keepsStoredNumbersAndIds_whenOrderedByNumber() {
        val rows = toEditableSchedulePeriods(
            listOf(
                storedPeriod(id = 7L, periodNumber = 12, start = "19:00", end = "19:45"),
                storedPeriod(id = 3L, periodNumber = 1, start = "08:00", end = "08:45")
            )
        )

        assertEquals(listOf("1", "12"), rows.map { it.periodNumber })
        assertEquals(listOf(3L, 7L), rows.map { it.id })
        assertEquals(listOf("08:00", "19:00"), rows.map { it.startTime })
    }

    @Test
    fun buildSchedulePeriodDrafts_namesTheRow_whenPeriodNumberIsNotAnInteger() {
        val result = buildSchedulePeriodDrafts(
            listOf(
                periodRow(id = 0L, periodNumber = "1", start = "08:00", end = "08:45"),
                periodRow(id = 0L, periodNumber = "", start = "08:55", end = "09:40")
            )
        )

        assertTrue("expected a rejection but got $result", result is SchedulePeriodDraftResult.Invalid)
        val message = (result as SchedulePeriodDraftResult.Invalid).message
        assertTrue(message.contains("第 2 行"))
        assertTrue(message.contains("整数"))
    }

    @Test
    fun buildSchedulePeriodDrafts_namesTheRow_whenPeriodNumberIsNotPositive() {
        val result = buildSchedulePeriodDrafts(
            listOf(
                periodRow(id = 0L, periodNumber = "1", start = "08:00", end = "08:45"),
                periodRow(id = 0L, periodNumber = "0", start = "08:55", end = "09:40")
            )
        )

        assertTrue("expected a rejection but got $result", result is SchedulePeriodDraftResult.Invalid)
        val message = (result as SchedulePeriodDraftResult.Invalid).message
        assertTrue(message.contains("第 2 行"))
        assertTrue(message.contains("大于 0"))
    }

    @Test
    fun buildSchedulePeriodDrafts_namesTheRow_whenATimeIsIncomplete() {
        val result = buildSchedulePeriodDrafts(
            listOf(
                periodRow(id = 0L, periodNumber = "1", start = "08:00", end = "08:45"),
                periodRow(id = 0L, periodNumber = "2", start = "08", end = "09:40")
            )
        )

        assertTrue("expected a rejection but got $result", result is SchedulePeriodDraftResult.Invalid)
        val message = (result as SchedulePeriodDraftResult.Invalid).message
        assertTrue(message.contains("第 2 行"))
        assertTrue(message.contains("开始时间"))
    }

    private fun periodRow(id: Long, periodNumber: String, start: String, end: String) =
        EditableSchedulePeriod(
            id = id,
            periodNumber = periodNumber,
            startTime = start,
            endTime = end
        )

    private fun storedPeriod(id: Long, periodNumber: Int, start: String, end: String) =
        SchedulePeriod(
            id = id,
            scheduleId = 1L,
            periodNumber = periodNumber,
            startTime = LocalTime.parse(start),
            endTime = LocalTime.parse(end)
        )
}
