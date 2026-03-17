package com.kurosu.sleepin.data.csv

import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.WeekType

/**
 * CSV writer for timetable course export.
 *
 * Output uses the project-defined Chinese headers and includes one row per course session.
 */
class CsvExporter {

    /**
     * Exports course sessions using a single `周次` column.
     *
     * For `ALL`, exporter materializes to `1-totalWeeks` so a re-import can preserve equivalent
     * scheduling behavior without requiring a dedicated enum column.
     */
    fun export(courses: List<CourseWithSessions>, totalWeeks: Int): String {
        val rows = mutableListOf<String>()
        rows.add(
            listOf(
                "课程名称",
                "教师",
                "地点",
                "星期",
                "开始节次",
                "结束节次",
                "周次"
            ).joinToString(",")
        )

        courses
            .sortedBy { it.course.name.lowercase() }
            .forEach { aggregate ->
                aggregate.sessions
                    .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }))
                    .forEach { session ->
                        val weeksSpec = buildWeeksSpec(session = session, totalWeeks = totalWeeks)

                        val fields = listOf(
                            aggregate.course.name,
                            aggregate.course.teacher.orEmpty(),
                            session.location.orEmpty(),
                            session.dayOfWeek.toString(),
                            session.startPeriod.toString(),
                            session.endPeriod.toString(),
                            weeksSpec
                        ).map(CsvCodec::encodeField)

                        rows.add(fields.joinToString(","))
                    }
            }

        // Include UTF-8 BOM to improve compatibility with spreadsheet tools on Windows.
        return "\uFEFF" + rows.joinToString(separator = "\n")
    }

    /**
     * Converts in-memory week data into the parser-supported textual grammar.
     */
    private fun buildWeeksSpec(session: CourseSession, totalWeeks: Int): String {
        return when (session.weekType) {
            WeekType.ALL -> "1-$totalWeeks"
            WeekType.RANGE -> {
                val startWeek = session.startWeek ?: 1
                val endWeek = session.endWeek ?: totalWeeks
                "$startWeek-$endWeek"
            }
            WeekType.CUSTOM -> session.customWeeks.sorted().joinToString(";")
        }
    }
}

