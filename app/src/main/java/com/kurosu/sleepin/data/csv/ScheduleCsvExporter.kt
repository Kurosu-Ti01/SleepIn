package com.kurosu.sleepin.data.csv

import com.kurosu.sleepin.domain.model.SchedulePeriod

/**
 * CSV writer for exporting one schedule as one file.
 */
class ScheduleCsvExporter {

    fun export(scheduleName: String, periods: List<SchedulePeriod>): String {
        val rows = mutableListOf<String>()
        rows.add(listOf("作息表名称", "节次", "开始时间", "结束时间").joinToString(","))

        periods
            .sortedBy { it.periodNumber }
            .forEach { period ->
                val fields = listOf(
                    scheduleName,
                    period.periodNumber.toString(),
                    period.startTime.toString(),
                    period.endTime.toString()
                ).map(CsvCodec::encodeField)
                rows.add(fields.joinToString(","))
            }

        // Include UTF-8 BOM to keep spreadsheet import behavior consistent on Windows.
        return "\uFEFF" + rows.joinToString(separator = "\n")
    }
}

