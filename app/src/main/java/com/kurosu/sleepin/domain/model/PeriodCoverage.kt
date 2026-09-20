package com.kurosu.sleepin.domain.model

/**
 * Lists the period numbers referenced by [usedPeriodRanges] that [definedPeriods] does not define.
 *
 * A session occupying periods 5 to 7 references all three, so ranges are expanded rather than only
 * compared by their endpoints: a schedule that defines 5 and 7 but not 6 still leaves the middle of
 * the block without a class time.
 *
 * Both the CSV import report and the week view answer the same question — "which periods does the
 * data use that the schedule has not configured?" — so they share this function instead of each
 * maintaining their own notion of a mismatch.
 */
fun findUndefinedPeriods(
    usedPeriodRanges: Iterable<IntRange>,
    definedPeriods: Set<Int>
): List<Int> = usedPeriodRanges
    .flatMap { range -> range.toList() }
    .distinct()
    .filterNot { period -> period in definedPeriods }
    .sorted()

/**
 * Renders period numbers compactly for display, collapsing runs: `1,2,3,7` becomes `1-3、7`.
 *
 * Shared so every place that reports a period mismatch describes it identically.
 */
fun formatPeriodNumbers(periods: List<Int>): String {
    val sorted = periods.distinct().sorted()
    val parts = mutableListOf<String>()
    var index = 0
    while (index < sorted.size) {
        val rangeStart = sorted[index]
        var rangeEnd = rangeStart
        while (index + 1 < sorted.size && sorted[index + 1] == rangeEnd + 1) {
            index += 1
            rangeEnd = sorted[index]
        }
        parts += if (rangeStart == rangeEnd) "$rangeStart" else "$rangeStart-$rangeEnd"
        index += 1
    }
    return parts.joinToString("、")
}
