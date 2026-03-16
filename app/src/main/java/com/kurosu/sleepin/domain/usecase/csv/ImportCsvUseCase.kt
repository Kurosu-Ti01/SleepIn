package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.ui.component.DefaultCourseColors

/**
 * Parses CSV text and writes courses/sessions into a target timetable.
 *
 * Import strategy:
 * - Invalid rows are skipped and reported with row-level errors.
 * - Valid rows are grouped by course name so one course can own multiple sessions.
 * - Each grouped course is persisted as one course row with many session rows.
 */
class ImportCsvUseCase(
    private val importer: CsvImporter,
    private val courseRepository: CourseRepository
) {
    suspend operator fun invoke(
        timetableId: Long,
        totalWeeks: Int,
        maxPeriod: Int,
        rawCsv: String
    ): CsvImportReport {
        val parsed = importer.parse(rawCsv = rawCsv, totalWeeks = totalWeeks, maxPeriod = maxPeriod)
        if (parsed.rows.isEmpty()) {
            return CsvImportReport(
                importedCourseCount = 0,
                importedSessionCount = 0,
                errors = parsed.errors
            )
        }

        val now = System.currentTimeMillis()
        var importedCourseCount = 0
        var importedSessionCount = 0

        parsed.rows
            .groupBy { it.name.trim().lowercase() }
            .values
            .forEachIndexed { index, rowsForCourse ->
                val first = rowsForCourse.first()
                val courseId = courseRepository.saveCourseWithSessions(
                    course = Course(
                        timetableId = timetableId,
                        name = first.name.trim(),
                        teacher = first.teacher?.trim().takeUnless { it.isNullOrEmpty() },
                        color = DefaultCourseColors[index % DefaultCourseColors.size],
                        note = null,
                        createdAt = now
                    ),
                    sessions = rowsForCourse.map { row ->
                        CourseSession(
                            courseId = 0L,
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
                )
                if (courseId > 0L) {
                    importedCourseCount += 1
                    importedSessionCount += rowsForCourse.size
                }
            }

        return CsvImportReport(
            importedCourseCount = importedCourseCount,
            importedSessionCount = importedSessionCount,
            errors = parsed.errors
        )
    }
}

