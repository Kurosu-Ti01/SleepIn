package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.domain.repository.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports all courses and sessions of one timetable as CSV content.
 *
 * Generation Logic:
 * This UseCase retrieves the full schedule for a given `timetableId` from the database.
 * The domain models (`CourseWithSessions`) are handed over to the `CsvExporter`, which maps
 * these hierarchical object models into a flat list of CSV rows (comma-separated string).
 * 
 * IO Flow and Coroutine Dispatching:
 * The data read involves Room database transactions, and the CSV string generation involves
 * heavy string concatenations. To prevent UI blocking, this function must be safe to call
 * from any thread. It utilizes `withContext(Dispatchers.IO)` to explicitly move the workload
 * to the IO thread pool.
 *
 * @property courseRepository The repository for fetching courses and their sessions.
 * @property timetableRepository The repository for fetching timetable metadata (e.g., total weeks).
 * @property exporter The utility responsible for generating the raw CSV string.
 */
class ExportCsvUseCase(
    private val courseRepository: CourseRepository,
    private val timetableRepository: TimetableRepository,
    private val exporter: CsvExporter
) {
    /**
     * Executes the export flow.
     *
     * @param timetableId The ID of the timetable to be exported.
     * @return A formatted CSV string containing all session records for the timetable.
     */
    suspend operator fun invoke(timetableId: Long): String = withContext(Dispatchers.IO) {
        // Step 1: Query domain models from Room database via the repository.
        val courses = courseRepository.getCoursesWithSessions(timetableId)
        val timetable = timetableRepository.getTimetableById(timetableId)
        
        // Step 2: Determine total weeks constraint or fallback to the standard 20 weeks.
        val totalWeeks = timetable?.totalWeeks ?: 20
        
        // Step 3: Delegate the task of mapping domain models -> CSV string to the CsvExporter.
        return@withContext exporter.export(courses, totalWeeks)
    }
}

