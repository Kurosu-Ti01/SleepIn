package com.kurosu.sleepin.data.csv

import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.usecase.csv.CsvCourseRow
import com.kurosu.sleepin.domain.usecase.csv.CsvRowError

/**
 * CSV parser + validator for timetable course imports.
 *
 * The parser accepts both Chinese and English header variants so localized files can be imported
 * without extra user conversion steps.
 */
class CsvImporter {

    data class ParseResult(
        val rows: List<CsvCourseRow>,
        val errors: List<CsvRowError>
    )

    fun parse(rawCsv: String, totalWeeks: Int, maxPeriod: Int): ParseResult {
        val normalized = rawCsv.replace("\uFEFF", "")
        val lines = normalized
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) {
            return ParseResult(
                rows = emptyList(),
                errors = listOf(CsvRowError(rowNumber = 1, message = "CSV file is empty"))
            )
        }

        val header = CsvCodec.parseLine(lines.first()).map { it.trim() }
        val headerMap = header.mapIndexed { index, name -> name to index }.toMap()

        val rows = mutableListOf<CsvCourseRow>()
        val errors = mutableListOf<CsvRowError>()

        lines.drop(1).forEachIndexed { index, rawLine ->
            val rowNumber = index + 2
            val fields = CsvCodec.parseLine(rawLine)
            try {
                val row = parseRow(
                    rowNumber = rowNumber,
                    fields = fields,
                    headerMap = headerMap,
                    totalWeeks = totalWeeks,
                    maxPeriod = maxPeriod
                )
                rows.add(row)
            } catch (error: IllegalArgumentException) {
                errors.add(CsvRowError(rowNumber = rowNumber, message = error.message ?: "Unknown error"))
            }
        }

        return ParseResult(rows = rows, errors = errors)
    }

    private fun parseRow(
        rowNumber: Int,
        fields: List<String>,
        headerMap: Map<String, Int>,
        totalWeeks: Int,
        maxPeriod: Int
    ): CsvCourseRow {
        fun value(vararg candidateHeaders: String): String {
            val index = candidateHeaders
                .asSequence()
                .mapNotNull { headerMap[it] }
                .firstOrNull()
                ?: throw IllegalArgumentException("Missing header for ${candidateHeaders.joinToString("/")}")
            return fields.getOrNull(index)?.trim().orEmpty()
        }

        val name = value("课程名称", "CourseName")
        if (name.isBlank()) throw IllegalArgumentException("Course name cannot be empty")

        val teacher = value("教师", "Teacher").ifBlank { null }
        val location = value("地点", "Location").ifBlank { null }

        val dayOfWeek = value("星期", "DayOfWeek").toIntOrNull()
            ?: throw IllegalArgumentException("Day of week must be an integer")
        if (dayOfWeek !in 1..7) throw IllegalArgumentException("Day of week must be between 1 and 7")

        val startPeriod = value("开始节次", "StartPeriod").toIntOrNull()
            ?: throw IllegalArgumentException("Start period must be an integer")
        val endPeriod = value("结束节次", "EndPeriod").toIntOrNull()
            ?: throw IllegalArgumentException("End period must be an integer")
        if (startPeriod !in 1..maxPeriod || endPeriod !in 1..maxPeriod || startPeriod > endPeriod) {
            throw IllegalArgumentException("Period range must be within 1-$maxPeriod and start <= end")
        }

        val weekTypeRaw = value("周数类型", "WeekType").uppercase()
        val weekType = try {
            WeekType.valueOf(weekTypeRaw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Week type must be ALL, RANGE, or CUSTOM")
        }

        val startWeekValue = value("起始周", "StartWeek").toIntOrNull()
        val endWeekValue = value("结束周", "EndWeek").toIntOrNull()
        val customWeeks = value("自定义周", "CustomWeeks")
            .split(';')
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
            .distinct()
            .sorted()

        when (weekType) {
            WeekType.ALL -> Unit
            WeekType.RANGE -> {
                val startWeek = startWeekValue ?: throw IllegalArgumentException("Start week is required for RANGE")
                val endWeek = endWeekValue ?: throw IllegalArgumentException("End week is required for RANGE")
                if (startWeek !in 1..totalWeeks || endWeek !in 1..totalWeeks || startWeek > endWeek) {
                    throw IllegalArgumentException("Week range must be between 1 and $totalWeeks")
                }
            }
            WeekType.CUSTOM -> {
                if (customWeeks.isEmpty()) throw IllegalArgumentException("Custom weeks cannot be empty for CUSTOM")
                if (customWeeks.any { it !in 1..totalWeeks }) {
                    throw IllegalArgumentException("Custom weeks must be between 1 and $totalWeeks")
                }
            }
        }

        return CsvCourseRow(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekType = weekType,
            startWeek = startWeekValue,
            endWeek = endWeekValue,
            customWeeks = customWeeks
        )
    }
}

