package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.WeekType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating generated CSV structure and UTF-8 BOM output behavior.
 */
class CsvExporterTest {

    private val exporter = CsvExporter()

    @Test
    fun export_includesHeaderBomAndSessionRows() {
        val aggregate = CourseWithSessions(
            course = Course(
                id = 1,
                timetableId = 1,
                name = "高等数学",
                teacher = "张三",
                color = 0xFF4A90D9.toInt(),
                note = null,
                createdAt = 1L
            ),
            sessions = listOf(
                CourseSession(
                    id = 1,
                    courseId = 1,
                    dayOfWeek = 1,
                    startPeriod = 1,
                    endPeriod = 2,
                    location = "A101",
                    weekType = WeekType.RANGE,
                    startWeek = 1,
                    endWeek = 16,
                    customWeeks = emptyList()
                )
            )
        )

        val csv = exporter.export(courses = listOf(aggregate), totalWeeks = 18)

        assertTrue(csv.startsWith("\uFEFF课程名称,教师,地点"))
        assertTrue(csv.contains("高等数学,张三,A101,1,1,2,1-16"))
    }
}

