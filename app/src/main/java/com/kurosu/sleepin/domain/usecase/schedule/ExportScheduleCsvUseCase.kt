package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.data.csv.ScheduleCsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exports one schedule and all of its periods to CSV.
 */
class ExportScheduleCsvUseCase(
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase,
    private val exporter: ScheduleCsvExporter
) {

    suspend operator fun invoke(scheduleId: Long): String = withContext(Dispatchers.IO) {
        val detail = getScheduleDetailUseCase(scheduleId)
            ?: throw IllegalArgumentException("Schedule not found")

        return@withContext exporter.export(
            scheduleName = detail.schedule.name,
            periods = detail.periods
        )
    }
}

