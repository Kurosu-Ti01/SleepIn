package com.kurosu.sleepin.data.csv

import com.kurosu.sleepin.domain.usecase.schedule.ScheduleCsvRowError
import java.time.LocalTime

/**
 * CSV parser + validator for importing exactly one schedule from one file.
 */
class ScheduleCsvImporter {

    data class ScheduleCsvRow(
        val scheduleName: String,
        val periodNumber: Int,
        val startTime: LocalTime,
        val endTime: LocalTime
    )

    data class ParseResult(
        val rows: List<ScheduleCsvRow>,
        val errors: List<ScheduleCsvRowError>
    )

    fun parse(rawCsv: String): ParseResult {
        val normalized = rawCsv.replace("\uFEFF", "")
        val lines = normalized
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) {
            return ParseResult(
                rows = emptyList(),
                errors = listOf(ScheduleCsvRowError(rowNumber = 1, message = "CSV file is empty"))
            )
        }

        val header = CsvCodec.parseLine(lines.first()).map { it.trim() }
        val headerMap = header.mapIndexed { index, name -> name to index }.toMap()

        val rows = mutableListOf<ScheduleCsvRow>()
        val errors = mutableListOf<ScheduleCsvRowError>()

        lines.drop(1).forEachIndexed { index, rawLine ->
            val rowNumber = index + 2
            val fields = CsvCodec.parseLine(rawLine)
            try {
                rows.add(parseRow(fields = fields, headerMap = headerMap))
            } catch (error: IllegalArgumentException) {
                errors.add(ScheduleCsvRowError(rowNumber = rowNumber, message = error.message ?: "Unknown error"))
            }
        }

        val scheduleNameSet = rows.map { it.scheduleName.trim() }.filter { it.isNotEmpty() }.distinct()
        if (scheduleNameSet.size > 1) {
            errors.add(
                ScheduleCsvRowError(
                    rowNumber = 1,
                    message = "CSV must contain exactly one schedule name"
                )
            )
        }

        return ParseResult(rows = rows, errors = errors)
    }

    private fun parseRow(
        fields: List<String>,
        headerMap: Map<String, Int>
    ): ScheduleCsvRow {
        fun value(vararg candidateHeaders: String): String {
            val index = candidateHeaders
                .asSequence()
                .mapNotNull { headerMap[it] }
                .firstOrNull()
                ?: throw IllegalArgumentException("Missing header for ${candidateHeaders.joinToString("/")}")
            return fields.getOrNull(index)?.trim().orEmpty()
        }

        val scheduleName = value("作息表名称", "ScheduleName")
        if (scheduleName.isBlank()) throw IllegalArgumentException("Schedule name cannot be empty")

        val periodNumber = value("节次", "PeriodNumber").toIntOrNull()
            ?: throw IllegalArgumentException("Period number must be an integer")
        if (periodNumber <= 0) throw IllegalArgumentException("Period number must be greater than 0")

        val startTime = runCatching { LocalTime.parse(value("开始时间", "StartTime")) }.getOrNull()
            ?: throw IllegalArgumentException("Start time format must be HH:mm")

        val endTime = runCatching { LocalTime.parse(value("结束时间", "EndTime")) }.getOrNull()
            ?: throw IllegalArgumentException("End time format must be HH:mm")

        if (!endTime.isAfter(startTime)) {
            throw IllegalArgumentException("End time must be after start time")
        }

        return ScheduleCsvRow(
            scheduleName = scheduleName,
            periodNumber = periodNumber,
            startTime = startTime,
            endTime = endTime
        )
    }
}

