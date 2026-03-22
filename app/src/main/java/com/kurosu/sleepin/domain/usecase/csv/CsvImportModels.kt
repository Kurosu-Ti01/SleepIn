package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.domain.model.WeekType

/**
 * Parsed representation of one CSV row before it is mapped to persistence models.
 *
 * This data class models the raw data extracted from a CSV file during the import process.
 * It maps directly to one line in the CSV, containing details for a single course session.
 * These raw rows are later grouped by the course name to construct the domain models
 * (`Course` and `CourseSession`).
 * 
 * @property name The name of the course. Used to group multiple sessions into a single course.
 * @property teacher The name of the teacher, or null if not provided.
 * @property location The physical or virtual location of the session, or null if not provided.
 * @property dayOfWeek The day of the week this session occurs (e.g., 1 for Monday, 7 for Sunday).
 * @property startPeriod The starting period index for this session.
 * @property endPeriod The ending period index for this session.
 * @property weekType The type of week schedule (e.g., ALL, ODD, EVEN, CUSTOM).
 * @property startWeek The starting week number for this session, or null if using custom weeks.
 * @property endWeek The ending week number for this session, or null if using custom weeks.
 * @property customWeeks A list of specific weeks this session is active, used if weekType is CUSTOM.
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
 * 
 * Contains errors encountered during the reading/parsing phase of the IO flow.
 * These errors help the user identify which lines in their CSV were malformed.
 *
 * @property rowNumber The 1-based index (or 0-based, depending on the parser) of the original CSV row.
 * @property message The detailed error message explaining why the validation or parsing failed.
 */
data class CsvRowError(
    val rowNumber: Int,
    val message: String
)

/**
 * Import preview/report model used by UI to show successes and failures.
 *
 * This report is generated after the import process completes. It calculates how many
 * domain models (`Course` and `CourseSession`) were successfully constructed and saved
 * to the SQLite database via Room, as well as a list of any row-level parsing errors.
 *
 * @property importedCourseCount The number of distinct `Course` domain models saved.
 * @property importedSessionCount The number of `CourseSession` domain models saved.
 * @property errors A list of errors tied to specific CSV rows that were skipped.
 */
data class CsvImportReport(
    val importedCourseCount: Int,
    val importedSessionCount: Int,
    val errors: List<CsvRowError>
)

