package com.kurosu.sleepin.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the shared period-coverage helpers used by both the CSV import report and the week
 * view, so the two surfaces cannot describe a mismatch differently.
 */
class PeriodCoverageTest {

    @Test
    fun findUndefinedPeriods_expandsRanges_soAGapInsideABlockIsFound() {
        val undefined = findUndefinedPeriods(
            usedPeriodRanges = listOf(2..4),
            definedPeriods = setOf(1, 2, 4)
        )

        assertEquals(listOf(3), undefined)
    }

    @Test
    fun findUndefinedPeriods_returnsSortedDistinctNumbers() {
        val undefined = findUndefinedPeriods(
            usedPeriodRanges = listOf(7..9, 3..3, 7..7),
            definedPeriods = setOf(1, 2)
        )

        assertEquals(listOf(3, 7, 8, 9), undefined)
    }

    @Test
    fun findUndefinedPeriods_returnsEmpty_whenEverythingIsDefined() {
        val undefined = findUndefinedPeriods(
            usedPeriodRanges = listOf(1..3, 2..2),
            definedPeriods = setOf(1, 2, 3)
        )

        assertEquals(emptyList<Int>(), undefined)
    }

    @Test
    fun formatPeriodNumbers_collapsesRuns() {
        assertEquals("1-3、7", formatPeriodNumbers(listOf(1, 2, 3, 7)))
    }

    @Test
    fun formatPeriodNumbers_sortsAndDeduplicatesInput() {
        assertEquals("1-2、5", formatPeriodNumbers(listOf(5, 1, 1, 2)))
    }

    @Test
    fun formatPeriodNumbers_returnsEmptyText_forNoInput() {
        assertEquals("", formatPeriodNumbers(emptyList()))
    }
}
