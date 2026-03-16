package com.kurosu.sleepin.data.csv

import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.WeekType

/**
 * CSV writer for timetable course export.
 *
 * Output uses the project-defined Chinese headers and includes one row per course session.
 */
class CsvExporter {

    fun export(courses: List<CourseWithSessions>): String {
        val rows = mutableListOf<String>()
        rows.add(
            listOf(
                "课程名称",
                "教师",
                "地点",
                "星期",
                "开始节次",
                "结束节次",
                "周数类型",
                "起始周",
                "结束周",
                "自定义周"
            ).joinToString(",")
        )

        courses
            .sortedBy { it.course.name.lowercase() }
            .forEach { aggregate ->
                aggregate.sessions
                    .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.endPeriod }))
                    .forEach { session ->
                        val startWeek = if (session.weekType == WeekType.RANGE) session.startWeek?.toString().orEmpty() else ""
                        val endWeek = if (session.weekType == WeekType.RANGE) session.endWeek?.toString().orEmpty() else ""
                        val customWeeks = if (session.weekType == WeekType.CUSTOM) {
                            session.customWeeks.joinToString(";")
                        } else {
                            ""
                        }

                        val fields = listOf(
                            aggregate.course.name,
                            aggregate.course.teacher.orEmpty(),
                            session.location.orEmpty(),
                            session.dayOfWeek.toString(),
                            session.startPeriod.toString(),
                            session.endPeriod.toString(),
                            session.weekType.name,
                            startWeek,
                            endWeek,
                            customWeeks
                        ).map(CsvCodec::encodeField)

                        rows.add(fields.joinToString(","))
                    }
            }

        // Include UTF-8 BOM to improve compatibility with spreadsheet tools on Windows.
        return "\uFEFF" + rows.joinToString(separator = "\n")
    }
}

