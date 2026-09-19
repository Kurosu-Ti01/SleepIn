package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.WeekType

/**
 * How one imported slot repeats across the semester, expressed in the app's own week vocabulary.
 *
 * Mirrors the `weekType`/`startWeek`/`endWeek`/`customWeeks` group stored on a course session, so
 * callers can apply it without knowing which representation was chosen.
 */
data class ImportWeekRule(
    val weekType: WeekType,
    val startWeek: Int?,
    val endWeek: Int?,
    val customWeeks: List<Int>
)

/**
 * Chooses the most specific week representation that still describes [weeks] exactly.
 *
 * Import scripts report the concrete list of teaching weeks, because that is what a portal exposes.
 * The app stores weeks as a rule, so collapsing a contiguous run into [WeekType.RANGE] — and a full
 * semester into [WeekType.ALL] — keeps imported courses indistinguishable from hand-entered ones:
 * a course detail sheet then reads "第 1-16 周" instead of listing sixteen separate numbers.
 *
 * Weeks outside the semester are preserved rather than clipped. Dropping them would hide a genuine
 * disagreement between the portal and the configured semester length, and a clipped list is
 * indistinguishable from a course that really does meet in fewer weeks. Such a session simply never
 * matches a visible week, and the confirmation step is responsible for reporting the mismatch.
 *
 * @param weeks teaching weeks reported by the import script; order and duplicates are irrelevant.
 * @param totalWeeks semester length the timetable will use. A value below 1 disables collapsing, so
 *   the result can only describe the weeks that were actually reported.
 */
fun resolveImportWeekRule(weeks: List<Int>, totalWeeks: Int): ImportWeekRule {
    val normalized = weeks.filter { it > 0 }.distinct().sorted()
    if (normalized.isEmpty()) {
        return ImportWeekRule(
            weekType = WeekType.CUSTOM,
            startWeek = null,
            endWeek = null,
            customWeeks = emptyList()
        )
    }

    val isContiguous = normalized.last() - normalized.first() == normalized.size - 1
    if (!isContiguous) {
        return ImportWeekRule(
            weekType = WeekType.CUSTOM,
            startWeek = null,
            endWeek = null,
            customWeeks = normalized
        )
    }

    val coversWholeSemester = totalWeeks > 0 &&
        normalized.first() == 1 &&
        normalized.last() == totalWeeks
    if (coversWholeSemester) {
        return ImportWeekRule(
            weekType = WeekType.ALL,
            startWeek = null,
            endWeek = null,
            customWeeks = emptyList()
        )
    }

    return ImportWeekRule(
        weekType = WeekType.RANGE,
        startWeek = normalized.first(),
        endWeek = normalized.last(),
        customWeeks = emptyList()
    )
}
