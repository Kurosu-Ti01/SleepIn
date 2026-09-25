package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.findUndefinedPeriods

/**
 * One slot that two imported courses occupy at the same time.
 *
 * This is a property of the payload itself, not of the timetable being written: an import replaces
 * every course of its target, so there is no stored course left for it to clash with. What can still
 * disagree is the portal's own data, which happens when two courses are exported for the same slot
 * (a shared room, a mis-tagged week range, a course the user took twice).
 *
 * [weeks] lists only the weeks where the two really meet, so a course that shares a slot in odd weeks
 * and another that uses even weeks is not reported.
 */
data class ImportSlotOverlap(
    val firstCourseName: String,
    val secondCourseName: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>
)

/**
 * What an import is about to store, in the terms the user is asked to confirm.
 *
 * Exists so the confirmation step and the success notice describe the same facts without either
 * recomputing them differently. Every field is derived from the parsed payload and the resolved
 * target, so the numbers shown are the numbers the write will use.
 */
data class ImportDiagnostics(
    val courseCount: Int,
    val sessionCount: Int,
    val totalWeeks: Int,
    /**
     * Weeks the payload actually teaches in, or null when it has no sessions. Reported instead of the
     * semester length because the two disagree often enough that showing only the configured length
     * would hide a portal that exports a different term.
     */
    val weekRange: IntRange?,
    /**
     * Periods the imported sessions use that the target schedule does not define. Not an error: the
     * courses are stored and rendered, they simply have no class time until the schedule covers them.
     */
    val periodsMissingFromSchedule: List<Int>,
    /**
     * Weeks outside `1..totalWeeks`. Kept out of the stored week rule rather than clipped, so the
     * mismatch surfaces here instead of disappearing into a course that silently meets in fewer weeks.
     */
    val weeksOutsideSemester: List<Int>,
    val slotOverlaps: List<ImportSlotOverlap>
) {
    /** Whether the user should be sent to the schedule editor afterwards. */
    val hasScheduleMismatch: Boolean
        get() = periodsMissingFromSchedule.isNotEmpty()
}

/**
 * Computes the diagnostics for one payload against the schedule it will be linked to.
 *
 * Pure: every input is already resolved, which is what lets the numbers be asserted directly in unit
 * tests and keeps the same computation usable both before the write (confirmation) and after it
 * (result notice).
 */
fun buildImportDiagnostics(
    submission: ImportSubmission,
    definedPeriods: Set<Int>,
    totalWeeks: Int
): ImportDiagnostics {
    val sessions = submission.courses.flatMap { course -> course.sessions }
    val weeks = sessions.flatMap { session -> session.weeks }

    return ImportDiagnostics(
        courseCount = submission.courses.size,
        sessionCount = sessions.size,
        totalWeeks = totalWeeks,
        weekRange = weeks.minOrNull()?.let { first -> first..weeks.max() },
        periodsMissingFromSchedule = findUndefinedPeriods(
            usedPeriodRanges = sessions.map { session -> session.startPeriod..session.endPeriod },
            definedPeriods = definedPeriods
        ),
        weeksOutsideSemester = weeks.filter { week -> week < 1 || week > totalWeeks }.distinct().sorted(),
        slotOverlaps = findSlotOverlaps(submission.courses)
    )
}

/**
 * Finds slots that two different imported courses occupy at overlapping periods and weeks.
 *
 * Sessions are grouped by day first because only same-day sessions can overlap; comparing the whole
 * payload pairwise would be quadratic for a result that cannot happen. Sessions of the same course are
 * skipped: a course legitimately lists the same slot several times to express alternating weeks, and
 * reporting that as a clash would bury the cases that matter.
 */
private fun findSlotOverlaps(courses: List<ImportedCourse>): List<ImportSlotOverlap> {
    val sessionsByDay = mutableMapOf<Int, MutableList<Pair<String, ImportedSession>>>()
    courses.forEach { course ->
        course.sessions.forEach { session ->
            sessionsByDay.getOrPut(session.dayOfWeek) { mutableListOf() } += course.name to session
        }
    }

    // Keyed so that the same clash reported by several session pairs — a course meeting twice in the
    // same block — is listed once.
    val overlaps = LinkedHashMap<String, ImportSlotOverlap>()
    sessionsByDay.values.forEach { sessions ->
        for (firstIndex in 0 until sessions.size) {
            for (secondIndex in firstIndex + 1 until sessions.size) {
                val (firstName, first) = sessions[firstIndex]
                val (secondName, second) = sessions[secondIndex]
                if (firstName == secondName) continue

                val startPeriod = maxOf(first.startPeriod, second.startPeriod)
                val endPeriod = minOf(first.endPeriod, second.endPeriod)
                if (startPeriod > endPeriod) continue

                val sharedWeeks = first.weeks.intersect(second.weeks.toSet()).sorted()
                if (sharedWeeks.isEmpty()) continue

                val names = listOf(firstName, secondName).sorted()
                val key = "${names[0]}|${names[1]}|${first.dayOfWeek}|$startPeriod|$endPeriod|" +
                    sharedWeeks.joinToString(",")
                overlaps.putIfAbsent(
                    key,
                    ImportSlotOverlap(
                        firstCourseName = names[0],
                        secondCourseName = names[1],
                        dayOfWeek = first.dayOfWeek,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        weeks = sharedWeeks
                    )
                )
            }
        }
    }
    return overlaps.values.toList()
}
