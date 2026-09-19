package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for turning a reported list of teaching weeks into the app's week rule.
 *
 * The rule only decides how the weeks are *described*; it must never change which weeks a course
 * actually meets in.
 */
class ImportWeekRuleResolverTest {

    @Test
    fun resolveImportWeekRule_returnsAllWeeks_whenReportedWeeksCoverTheWholeSemester() {
        val rule = resolveImportWeekRule(weeks = listOf(1, 2, 3, 4), totalWeeks = 4)

        assertEquals(WeekType.ALL, rule.weekType)
        assertNull(rule.startWeek)
        assertEquals(emptyList<Int>(), rule.customWeeks)
    }

    @Test
    fun resolveImportWeekRule_returnsAllWeeks_whenWeeksAreReportedOutOfOrder() {
        val rule = resolveImportWeekRule(weeks = listOf(3, 1, 4, 2), totalWeeks = 4)

        assertEquals(WeekType.ALL, rule.weekType)
    }

    @Test
    fun resolveImportWeekRule_returnsRange_whenWeeksAreContiguous() {
        val rule = resolveImportWeekRule(weeks = listOf(1, 2, 3, 4, 5, 6), totalWeeks = 16)

        assertEquals(WeekType.RANGE, rule.weekType)
        assertEquals(1, rule.startWeek)
        assertEquals(6, rule.endWeek)
    }

    @Test
    fun resolveImportWeekRule_returnsCustom_whenWeeksHaveGaps() {
        val rule = resolveImportWeekRule(weeks = listOf(1, 3, 5), totalWeeks = 16)

        assertEquals(WeekType.CUSTOM, rule.weekType)
        assertEquals(listOf(1, 3, 5), rule.customWeeks)
        assertNull(rule.startWeek)
    }

    @Test
    fun resolveImportWeekRule_returnsCustom_whenWeekCountMatchesButWeeksSkipAValue() {
        // Same number of weeks as the semester, but not the same weeks: it must not collapse to ALL.
        val rule = resolveImportWeekRule(weeks = listOf(1, 2, 3, 5), totalWeeks = 4)

        assertEquals(WeekType.CUSTOM, rule.weekType)
        assertEquals(listOf(1, 2, 3, 5), rule.customWeeks)
    }

    @Test
    fun resolveImportWeekRule_deduplicatesAndSortsReportedWeeks() {
        val rule = resolveImportWeekRule(weeks = listOf(5, 1, 5, 3), totalWeeks = 16)

        assertEquals(WeekType.CUSTOM, rule.weekType)
        assertEquals(listOf(1, 3, 5), rule.customWeeks)
    }

    @Test
    fun resolveImportWeekRule_preservesWeeksBeyondSemester_whenRangeStartsAfterIt() {
        val rule = resolveImportWeekRule(weeks = listOf(17, 18), totalWeeks = 16)

        assertEquals(WeekType.RANGE, rule.weekType)
        assertEquals(17, rule.startWeek)
        assertEquals(18, rule.endWeek)
    }

    @Test
    fun resolveImportWeekRule_doesNotCollapse_whenSemesterLengthIsUnknown() {
        val rule = resolveImportWeekRule(weeks = listOf(1, 2, 3), totalWeeks = 0)

        assertEquals(WeekType.RANGE, rule.weekType)
        assertEquals(1, rule.startWeek)
        assertEquals(3, rule.endWeek)
    }

    @Test
    fun resolveImportWeekRule_returnsEmptyCustom_whenNoWeekIsUsable() {
        val rule = resolveImportWeekRule(weeks = listOf(0, -1), totalWeeks = 16)

        assertEquals(WeekType.CUSTOM, rule.weekType)
        assertEquals(emptyList<Int>(), rule.customWeeks)
    }
}
