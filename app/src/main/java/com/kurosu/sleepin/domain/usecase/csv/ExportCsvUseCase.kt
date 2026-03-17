package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.domain.repository.TimetableRepository

/**
 * Exports all courses and sessions of one timetable as CSV content.
 */
class ExportCsvUseCase(
    private val courseRepository: CourseRepository,
    private val timetableRepository: TimetableRepository,
    private val exporter: CsvExporter
) {
    suspend operator fun invoke(timetableId: Long): String {
        val courses = courseRepository.getCoursesWithSessions(timetableId)
        val timetable = timetableRepository.getTimetableById(timetableId)
        val totalWeeks = timetable?.totalWeeks ?: 20
        return exporter.export(courses, totalWeeks)
    }
}

