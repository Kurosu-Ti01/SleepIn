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

    private data class WeekParseResult(
        val weekType: WeekType,
        val startWeek: Int?,
        val endWeek: Int?,
        val customWeeks: List<Int>
    )

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

        val hasWeeksColumn = headerMap.containsKey("周次") || headerMap.containsKey("Weeks")
        val weekParseResult = if (hasWeeksColumn) {
            parseWeeksSpec(
                spec = value("周次", "Weeks"),
                totalWeeks = totalWeeks,
                rowNumber = rowNumber
            )
        } else {
            // Keep backward compatibility with older exported files during migration.
            parseLegacyWeekColumns(
                weekTypeRaw = value("周数类型", "WeekType"),
                startWeekRaw = value("起始周", "StartWeek"),
                endWeekRaw = value("结束周", "EndWeek"),
                customWeeksRaw = value("自定义周", "CustomWeeks"),
                totalWeeks = totalWeeks
            )
        }

        return CsvCourseRow(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weekType = weekParseResult.weekType,
            startWeek = weekParseResult.startWeek,
            endWeek = weekParseResult.endWeek,
            customWeeks = weekParseResult.customWeeks
        )
    }

    /**
     * Parses the new single-column week syntax.
     *
     * Supported forms:
     * - 1-16
     * - 1;3;5;7;8
     * - 1-16(odd) / 1-16(even)
     * - 1-4;7-9;13;14-18(odd)
     */
    private fun parseWeeksSpec(spec: String, totalWeeks: Int, rowNumber: Int): WeekParseResult {
        val normalized = spec.trim()
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Weeks cannot be empty")
        }

        // Preserve a direct ALL fallback so manually-authored CSV can still express this intent.
        if (normalized.equals("ALL", ignoreCase = true)) {
            return WeekParseResult(weekType = WeekType.ALL, startWeek = null, endWeek = null, customWeeks = emptyList())
        }

        val rangePattern = Regex("^\\s*(\\d+)\\s*-\\s*(\\d+)\\s*$")
        val parityRangePattern = Regex("^\\s*(\\d+)\\s*-\\s*(\\d+)\\s*\\((odd|even)\\)\\s*$", RegexOption.IGNORE_CASE)
        val singleWeekPattern = Regex("^\\s*(\\d+)\\s*$")

        if (!normalized.contains(';')) {
            val rangeMatch = rangePattern.matchEntire(normalized)
            if (rangeMatch != null) {
                val startWeek = rangeMatch.groupValues[1].toInt()
                val endWeek = rangeMatch.groupValues[2].toInt()
                validateRange(startWeek = startWeek, endWeek = endWeek, totalWeeks = totalWeeks)
                return WeekParseResult(
                    weekType = WeekType.RANGE,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    customWeeks = emptyList()
                )
            }
        }

        val expandedWeeks = mutableSetOf<Int>()
        normalized
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val parityRangeMatch = parityRangePattern.matchEntire(token)
                if (parityRangeMatch != null) {
                    val startWeek = parityRangeMatch.groupValues[1].toInt()
                    val endWeek = parityRangeMatch.groupValues[2].toInt()
                    val parity = parityRangeMatch.groupValues[3].lowercase()
                    validateRange(startWeek = startWeek, endWeek = endWeek, totalWeeks = totalWeeks)

                    (startWeek..endWeek)
                        .filter { week -> (parity == "odd" && week % 2 == 1) || (parity == "even" && week % 2 == 0) }
                        .forEach(expandedWeeks::add)
                    return@forEach
                }

                val plainRangeMatch = rangePattern.matchEntire(token)
                if (plainRangeMatch != null) {
                    val startWeek = plainRangeMatch.groupValues[1].toInt()
                    val endWeek = plainRangeMatch.groupValues[2].toInt()
                    validateRange(startWeek = startWeek, endWeek = endWeek, totalWeeks = totalWeeks)
                    (startWeek..endWeek).forEach(expandedWeeks::add)
                    return@forEach
                }

                val singleWeekMatch = singleWeekPattern.matchEntire(token)
                if (singleWeekMatch != null) {
                    val week = singleWeekMatch.groupValues[1].toInt()
                    if (week !in 1..totalWeeks) {
                        throw IllegalArgumentException("Week $week is out of range 1-$totalWeeks")
                    }
                    expandedWeeks.add(week)
                    return@forEach
                }

                throw IllegalArgumentException("Invalid weeks token '$token' at row $rowNumber")
            }

        if (expandedWeeks.isEmpty()) {
            throw IllegalArgumentException("Weeks cannot be empty")
        }

        return WeekParseResult(
            weekType = WeekType.CUSTOM,
            startWeek = null,
            endWeek = null,
            customWeeks = expandedWeeks.sorted()
        )
    }

    /**
     * Parses the legacy four-column week representation used by older CSV exports.
     */
    private fun parseLegacyWeekColumns(
        weekTypeRaw: String,
        startWeekRaw: String,
        endWeekRaw: String,
        customWeeksRaw: String,
        totalWeeks: Int
    ): WeekParseResult {
        val weekType = try {
            WeekType.valueOf(weekTypeRaw.uppercase())
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Week type must be ALL, RANGE, or CUSTOM")
        }

        val startWeekValue = startWeekRaw.toIntOrNull()
        val endWeekValue = endWeekRaw.toIntOrNull()
        val customWeeks = customWeeksRaw
            .split(';')
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
            .distinct()
            .sorted()

        when (weekType) {
            WeekType.ALL -> Unit
            WeekType.RANGE -> {
                val startWeek = startWeekValue ?: throw IllegalArgumentException("Start week is required for RANGE")
                val endWeek = endWeekValue ?: throw IllegalArgumentException("End week is required for RANGE")
                validateRange(startWeek = startWeek, endWeek = endWeek, totalWeeks = totalWeeks)
            }
            WeekType.CUSTOM -> {
                if (customWeeks.isEmpty()) throw IllegalArgumentException("Custom weeks cannot be empty for CUSTOM")
                if (customWeeks.any { it !in 1..totalWeeks }) {
                    throw IllegalArgumentException("Custom weeks must be between 1 and $totalWeeks")
                }
            }
        }

        return WeekParseResult(
            weekType = weekType,
            startWeek = startWeekValue,
            endWeek = endWeekValue,
            customWeeks = customWeeks
        )
    }

    private fun validateRange(startWeek: Int, endWeek: Int, totalWeeks: Int) {
        if (startWeek !in 1..totalWeeks || endWeek !in 1..totalWeeks || startWeek > endWeek) {
            throw IllegalArgumentException("Week range must be between 1 and $totalWeeks")
        }
    }

}

