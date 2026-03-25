package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.data.csv.ScheduleCsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports one schedule from CSV and always creates a new schedule record.
 */
class ImportScheduleCsvUseCase(
    private val importer: ScheduleCsvImporter,
    private val saveScheduleUseCase: SaveScheduleUseCase
) {

    suspend operator fun invoke(rawCsv: String): ScheduleCsvImportReport = withContext(Dispatchers.IO) {
        val parsed = importer.parse(rawCsv)
        if (parsed.errors.isNotEmpty() || parsed.rows.isEmpty()) {
            return@withContext ScheduleCsvImportReport(
                importedScheduleId = null,
                importedPeriodCount = 0,
                errors = parsed.errors
            )
        }

        val scheduleName = parsed.rows.first().scheduleName
        val periodDrafts = parsed.rows
            .sortedBy { it.periodNumber }
            .map { row ->
                SaveScheduleUseCase.PeriodDraft(
                    periodNumber = row.periodNumber,
                    startTime = row.startTime,
                    endTime = row.endTime
                )
            }

        when (val saveResult = saveScheduleUseCase(null, scheduleName, null, periodDrafts)) {
            is SaveScheduleResult.Success -> {
                ScheduleCsvImportReport(
                    importedScheduleId = saveResult.scheduleId,
                    importedPeriodCount = periodDrafts.size,
                    errors = emptyList()
                )
            }

            is SaveScheduleResult.ValidationError -> {
                ScheduleCsvImportReport(
                    importedScheduleId = null,
                    importedPeriodCount = 0,
                    errors = listOf(
                        ScheduleCsvRowError(
                            rowNumber = 1,
                            message = saveResult.message
                        )
                    )
                )
            }
        }
    }
}

