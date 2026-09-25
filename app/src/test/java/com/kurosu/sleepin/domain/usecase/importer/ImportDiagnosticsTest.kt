package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for the diagnostics shown before and after an import is written.
 *
 * These numbers are what the user agrees to when they confirm, so the cases that matter are the ones
 * where a naive computation would report something reassuring but wrong: a period block whose middle
 * is undefined, alternating weeks that do not actually clash, and weeks the semester does not cover.
 */
class ImportDiagnosticsTest {

    @Test
    fun build_countsCoursesAndSessions() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 2))),
                importCourse(
                    name = "课程 B",
                    sessions = listOf(
                        session(day = 2, from = 3, to = 4),
                        session(day = 4, from = 5, to = 6)
                    )
                )
            )
        )

        assertEquals(2, diagnostics.courseCount)
        assertEquals(3, diagnostics.sessionCount)
    }

    @Test
    fun build_reportsWeekRangeAcrossEverySession() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(2, 3)))),
                importCourse(name = "课程 B", sessions = listOf(session(day = 2, from = 1, to = 2, weeks = listOf(9))))
            ),
            totalWeeks = 16
        )

        assertEquals(2..9, diagnostics.weekRange)
    }

    @Test
    fun build_hasNoWeekRange_whenNothingIsImported() {
        assertNull(diagnostics(submission = submission()).weekRange)
    }

    @Test
    fun build_reportsEveryPeriodOfABlockTheScheduleDoesNotFullyDefine() {
        // Periods 5 and 7 exist but 6 does not: comparing only the endpoints of the block would call
        // this a match and leave the middle of the lesson without a time.
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 5, to = 7)))
            ),
            definedPeriods = setOf(1, 2, 3, 4, 5, 7)
        )

        assertEquals(listOf(6), diagnostics.periodsMissingFromSchedule)
        assertTrue(diagnostics.hasScheduleMismatch)
    }

    @Test
    fun build_reportsNothingMissing_whenTheScheduleCoversThePeriods() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 4)))
            ),
            definedPeriods = setOf(1, 2, 3, 4)
        )

        assertTrue(diagnostics.periodsMissingFromSchedule.isEmpty())
        assertFalse(diagnostics.hasScheduleMismatch)
    }

    @Test
    fun build_reportsWeeksOutsideTheSemester() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(1, 8, 18, 20))))
            ),
            totalWeeks = 16
        )

        // Weeks are not clipped when stored, so the mismatch has to be reported instead of hidden.
        assertEquals(listOf(18, 20), diagnostics.weeksOutsideSemester)
    }

    @Test
    fun build_reportsOverlap_whenTwoCoursesShareASlotInTheSameWeek() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(1, 2)))),
                importCourse(name = "课程 B", sessions = listOf(session(day = 1, from = 2, to = 3, weeks = listOf(2, 3))))
            )
        )

        val overlap = diagnostics.slotOverlaps.single()
        assertEquals("课程 A", overlap.firstCourseName)
        assertEquals("课程 B", overlap.secondCourseName)
        assertEquals(1, overlap.dayOfWeek)
        // Only the periods and weeks the two really share.
        assertEquals(2, overlap.startPeriod)
        assertEquals(2, overlap.endPeriod)
        assertEquals(listOf(2), overlap.weeks)
    }

    @Test
    fun build_ignoresAlternatingWeeksThatNeverMeet() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "单周课", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(1, 3, 5)))),
                importCourse(name = "双周课", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(2, 4, 6))))
            )
        )

        assertTrue(diagnostics.slotOverlaps.isEmpty())
    }

    @Test
    fun build_ignoresOverlapOnDifferentDays() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 2))),
                importCourse(name = "课程 B", sessions = listOf(session(day = 3, from = 1, to = 2)))
            )
        )

        assertTrue(diagnostics.slotOverlaps.isEmpty())
    }

    @Test
    fun build_ignoresASessionOverlappingItsOwnCourse() {
        // The same course listing one slot twice is how a payload expresses alternating weeks; treating
        // it as a clash would bury the overlaps that do matter.
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(
                    name = "课程 A",
                    sessions = listOf(
                        session(day = 1, from = 1, to = 2, weeks = listOf(1, 3)),
                        session(day = 1, from = 1, to = 2, weeks = listOf(1, 3))
                    )
                )
            )
        )

        assertTrue(diagnostics.slotOverlaps.isEmpty())
    }

    @Test
    fun build_reportsTheSameClashOnce_whenSeveralSessionPairsRepeatIt() {
        val diagnostics = diagnostics(
            submission = submission(
                importCourse(
                    name = "课程 A",
                    sessions = listOf(
                        session(day = 1, from = 1, to = 2, weeks = listOf(1)),
                        session(day = 1, from = 1, to = 2, weeks = listOf(1))
                    )
                ),
                importCourse(name = "课程 B", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(1))))
            )
        )

        assertEquals(1, diagnostics.slotOverlaps.size)
    }

    private fun diagnostics(
        submission: ImportSubmission,
        definedPeriods: Set<Int> = (1..12).toSet(),
        totalWeeks: Int = 16
    ): ImportDiagnostics = buildImportDiagnostics(
        submission = submission,
        definedPeriods = definedPeriods,
        totalWeeks = totalWeeks
    )

    private fun submission(vararg courses: ImportedCourse): ImportSubmission =
        ImportSubmission(timetable = null, courses = courses.toList())

    private fun importCourse(name: String, sessions: List<ImportedSession>): ImportedCourse = ImportedCourse(
        name = name,
        teacher = null,
        note = null,
        color = null,
        sessions = sessions
    )

    private fun session(
        day: Int,
        from: Int,
        to: Int,
        weeks: List<Int> = listOf(1)
    ): ImportedSession = ImportedSession(
        dayOfWeek = day,
        startPeriod = from,
        endPeriod = to,
        location = null,
        weeks = weeks
    )
}

/**
 * Guards that the resolved week rule and the diagnostics agree about a semester that is shorter than
 * the payload, which is the pair of facts the confirmation dialog shows together.
 */
class ImportWeekRuleVersusDiagnosticsTest {

    @Test
    fun weeksOutsideTheSemester_areNotPartOfTheStoredRule_butAreReported() {
        val weeks = listOf(1, 2, 18)
        val rule = resolveImportWeekRule(weeks, totalWeeks = 16)
        val diagnostics = buildImportDiagnostics(
            submission = ImportSubmission(
                timetable = ImportedTimetable(
                    name = null,
                    totalWeeks = 16,
                    startDate = LocalDate.of(2025, 9, 1)
                ),
                courses = listOf(
                    ImportedCourse(
                        name = "课程 A",
                        teacher = null,
                        note = null,
                        color = null,
                        sessions = listOf(
                            ImportedSession(
                                dayOfWeek = 1,
                                startPeriod = 1,
                                endPeriod = 2,
                                location = null,
                                weeks = weeks
                            )
                        )
                    )
                )
            ),
            definedPeriods = (1..12).toSet(),
            totalWeeks = 16
        )

        // Stored as-is rather than clipped to 1..16, so nothing silently changes the course's weeks…
        assertEquals(WeekType.CUSTOM, rule.weekType)
        assertEquals(weeks, rule.customWeeks)
        // …while the user is still told which weeks will not appear.
        assertEquals(listOf(18), diagnostics.weeksOutsideSemester)
    }
}
