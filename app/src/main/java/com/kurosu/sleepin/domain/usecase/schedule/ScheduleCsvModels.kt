package com.kurosu.sleepin.domain.usecase.schedule

/**
 * One parse/validation issue tied to a specific row number in a schedule CSV file.
 */
data class ScheduleCsvRowError(
    val rowNumber: Int,
    val message: String
)

/**
 * Import report for one schedule CSV file.
 */
data class ScheduleCsvImportReport(
    val importedScheduleId: Long?,
    val importedPeriodCount: Int,
    val errors: List<ScheduleCsvRowError>
)

