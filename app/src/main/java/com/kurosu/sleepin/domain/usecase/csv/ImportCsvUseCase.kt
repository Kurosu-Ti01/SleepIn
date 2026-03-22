package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.ui.component.DefaultCourseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses CSV text and writes courses/sessions into a target timetable.
 *
 * Parsing Logic and Domain Mapping:
 * - A raw CSV string is given to `CsvImporter`, which parses lines into `CsvCourseRow` instances.
 * - Valid CSV rows (`CsvCourseRow`) are mapped grouping by course name. Thus, if a CSV has 3 lines with
 *   the same course name "Math", it maps to exactly ONE `Course` domain entity and THREE `CourseSession` models.
 *
 * Import Strategy:
 * - Invalid rows are skipped completely but kept track of, resulting in row-level errors (`CsvRowError`).
 * - For successfully grouped rows, a new parent `Course` entity is created, assigning it a default UI color.
 * - The session details are linked via a `saveCourseWithSessions` repository call which uses Room's 
 *   @Transaction to commit the parent-child entities atomically to the SQLite database.
 * 
 * IO Flow and Coroutine Dispatching:
 * - Both the parsing of the string and the database writes (via CourseRepository) are I/O bound 
 *   or heavy computations. To ensure that the Compose UI thread remains unblocked, the entire operation
 *   is enclosed within `withContext(Dispatchers.IO)`.
 *
 * @property importer The CSV parsing utility reading text into intermediate model groups.
 * @property courseRepository The repository bridging Domain models to Data layer (Room DAOs).
 */
class ImportCsvUseCase(
    private val importer: CsvImporter,
    private val courseRepository: CourseRepository
) {
    /**
     * Executes the import operation for a given raw CSV string into a specific timetable.
     *
     * @param timetableId the target timetable to receive these models.
     * @param totalWeeks limit bounds for parsing weeks data constraint.
     * @param maxPeriod limit bounds for parsing daily periods.
     * @param rawCsv the unparsed raw string content read from the file stream.
     * @return A report combining imported counts and validation errors.
     */
    suspend operator fun invoke(
        timetableId: Long,
        totalWeeks: Int,
        maxPeriod: Int,
        rawCsv: String
    ): CsvImportReport = withContext(Dispatchers.IO) {
        // Step 1: Parse the plain text CSV into structured flat intermediate representations.
        val parsed = importer.parse(rawCsv = rawCsv, totalWeeks = totalWeeks, maxPeriod = maxPeriod)
        
        // If there are no valid rows, return structural errors early
        if (parsed.rows.isEmpty()) {
            return@withContext CsvImportReport(
                importedCourseCount = 0,
                importedSessionCount = 0,
                errors = parsed.errors
            )
        }

        val now = System.currentTimeMillis()
        var importedCourseCount = 0
        var importedSessionCount = 0

        // Step 2: Domain Mapping. Group the flat CSV rows by course name.
        // Doing this ensures the one-to-many relationship (one Course -> many CourseSessions).
        parsed.rows
            .groupBy { it.name.trim().lowercase() }
            .values
            .forEachIndexed { index, rowsForCourse ->
                val first = rowsForCourse.first()
                
                // Construct the parent Domain Model
                val courseToSave = Course(
                    timetableId = timetableId,
                    name = first.name.trim(),
                    teacher = first.teacher?.trim().takeUnless { it.isNullOrEmpty() },
                    color = DefaultCourseColors[index % DefaultCourseColors.size], // Assign sequential color
                    note = null,
                    createdAt = now
                )
                
                // Construct the child Domain Models
                val sessionsToSave = rowsForCourse.map { row ->
                    CourseSession(
                        courseId = 0L, // Handled automatically by the generic repository @Insert or transaction
                        dayOfWeek = row.dayOfWeek,
                        startPeriod = row.startPeriod,
                        endPeriod = row.endPeriod,
                        location = row.location?.trim().takeUnless { it.isNullOrEmpty() },
                        weekType = row.weekType,
                        startWeek = row.startWeek,
                        endWeek = row.endWeek,
                        customWeeks = row.customWeeks
                    )
                }
                
                // Step 3: Persist through Data Layer (Room)
                val courseId = courseRepository.saveCourseWithSessions(
                    course = courseToSave,
                    sessions = sessionsToSave
                )
                
                // Step 4: Keep metrics for the report
                if (courseId > 0L) {
                    importedCourseCount += 1
                    importedSessionCount += rowsForCourse.size
                }
            }

        // Return final preview/report to UI
        return@withContext CsvImportReport(
            importedCourseCount = importedCourseCount,
            importedSessionCount = importedSessionCount,
            errors = parsed.errors
        )
    }
}

