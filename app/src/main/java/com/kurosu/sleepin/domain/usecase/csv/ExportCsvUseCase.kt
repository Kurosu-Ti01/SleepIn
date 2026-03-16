package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Exports all courses and sessions of one timetable as CSV content.
 */
class ExportCsvUseCase(
    private val courseRepository: CourseRepository,
    private val exporter: CsvExporter
) {
    suspend operator fun invoke(timetableId: Long): String {
        val courses = courseRepository.getCoursesWithSessions(timetableId)
        return exporter.export(courses)
    }
}

