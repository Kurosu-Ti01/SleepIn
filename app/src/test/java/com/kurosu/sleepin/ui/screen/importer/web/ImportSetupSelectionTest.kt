package com.kurosu.sleepin.ui.screen.importer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for which timetable an import defaults to when the user switches the target to overwrite.
 *
 * The case worth pinning is the schedule that comes with the choice: an earlier version defaulted to
 * the first schedule in the list, which relinked the timetable being overwritten and changed every
 * class time it displayed — a change the user never asked for and could only notice after the import.
 */
class ImportSetupSelectionTest {

    @Test
    fun chooseOverwriteTarget_keepsTheChosenTimetableAndItsOwnSchedule() {
        val options = listOf(
            option(id = 1L, name = "课程表 A", scheduleId = 10L),
            option(id = 2L, name = "课程表 B", scheduleId = 20L)
        )

        val choice = chooseOverwriteTarget(options, currentTimetableId = 2L)

        assertEquals(2L, choice?.id)
        assertEquals(20L, choice?.scheduleId)
    }

    @Test
    fun chooseOverwriteTarget_fallsBackToTheFirstTimetable_whenNothingIsChosen() {
        val options = listOf(
            option(id = 1L, name = "课程表 A", scheduleId = 10L),
            option(id = 2L, name = "课程表 B", scheduleId = 20L)
        )

        assertEquals(1L, chooseOverwriteTarget(options, currentTimetableId = null)?.id)
    }

    @Test
    fun chooseOverwriteTarget_fallsBackToTheFirstTimetable_whenTheChosenOneIsGone() {
        val options = listOf(option(id = 2L, name = "课程表 B", scheduleId = 20L))

        assertEquals(2L, chooseOverwriteTarget(options, currentTimetableId = 1L)?.id)
    }

    @Test
    fun chooseOverwriteTarget_returnsNothing_whenThereIsNothingToOverwrite() {
        // The screen disables the option in this case, so the caller must not have to invent a target.
        assertNull(chooseOverwriteTarget(options = emptyList(), currentTimetableId = null))
    }

    private fun option(id: Long, name: String, scheduleId: Long): ImportTimetableOptionUi =
        ImportTimetableOptionUi(id = id, name = name, scheduleId = scheduleId)
}
