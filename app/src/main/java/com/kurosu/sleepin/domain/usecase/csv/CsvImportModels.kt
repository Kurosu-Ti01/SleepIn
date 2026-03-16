package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.domain.model.WeekType

/**
 * Parsed representation of one CSV row before it is mapped to persistence models.
 */
data class CsvCourseRow(
    val name: String,
    val teacher: String?,
    val location: String?,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekType: WeekType,
    val startWeek: Int?,
    val endWeek: Int?,
    val customWeeks: List<Int>
)

/**
 * One parse/validation issue tied to a specific row number in the source CSV.
 */
data class CsvRowError(
    val rowNumber: Int,
    val message: String
)

/**
 * Import preview/report model used by UI to show successes and failures.
 */
data class CsvImportReport(
    val importedCourseCount: Int,
    val importedSessionCount: Int,
    val errors: List<CsvRowError>
)

