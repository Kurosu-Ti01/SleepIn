package com.kurosu.sleepin.ui.screen.schedule

import com.kurosu.sleepin.data.csv.ScheduleCsvExporter
import com.kurosu.sleepin.data.csv.ScheduleCsvImporter
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import com.kurosu.sleepin.domain.usecase.schedule.ExportScheduleCsvUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.ImportScheduleCsvUseCase
import com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the numbering rules the schedule editor applies to its rows.
 *
 * Only the synchronous state transitions are exercised. The save and import paths need
 * `Dispatchers.Main`, which this project's JVM test setup does not provide; the mapping they delegate
 * to is covered separately by [SchedulePeriodMappingTest].
 */
class ScheduleEditorViewModelTest {

    @Test
    fun addPeriod_numbersTheNewRowAfterTheHighestExistingNumber() {
        val viewModel = editorViewModel()

        // A sparse schedule. The next row must continue from 12 rather than from the row count, because
        // continuing from the count would either duplicate an existing number or leave a gap the user
        // could not fill by appending.
        viewModel.onPeriodNumberChange(0, "12")
        viewModel.addPeriod()

        val periods = viewModel.uiState.value.periods
        assertEquals(2, periods.size)
        assertEquals("13", periods.last().periodNumber)
    }

    @Test
    fun addPeriod_usesTheMaximumOverAllRows_notTheLastRow() {
        val viewModel = editorViewModel()

        // Row order must not decide the next number: the highest number can sit anywhere in the list.
        viewModel.onPeriodNumberChange(0, "1")
        viewModel.addPeriod()
        viewModel.onPeriodNumberChange(1, "12")
        viewModel.addPeriod()
        viewModel.onPeriodNumberChange(2, "5")
        viewModel.addPeriod()

        assertEquals("13", viewModel.uiState.value.periods.last().periodNumber)
    }

    @Test
    fun addPeriod_startsFromOne_whenNoRowCarriesAUsableNumber() {
        val viewModel = editorViewModel()

        viewModel.onPeriodNumberChange(0, "")

        viewModel.addPeriod()

        assertEquals("1", viewModel.uiState.value.periods.last().periodNumber)
    }

    @Test
    fun addPeriod_ignoresNegativeNumbers_whenChoosingTheNextNumber() {
        val viewModel = editorViewModel()

        viewModel.onPeriodNumberChange(0, "-5")

        viewModel.addPeriod()

        assertEquals("1", viewModel.uiState.value.periods.last().periodNumber)
    }

    /**
     * Builds a create-mode editor. Create mode matters: with no schedule id the constructor performs no
     * repository read, so no coroutine is started and no main dispatcher is required.
     */
    private fun editorViewModel(): ScheduleEditorViewModel {
        val repository = FakeScheduleRepository()
        val getScheduleDetailUseCase = GetScheduleDetailUseCase(repository)
        val saveScheduleUseCase = SaveScheduleUseCase(repository)
        return ScheduleEditorViewModel(
            scheduleId = null,
            getScheduleDetailUseCase = getScheduleDetailUseCase,
            saveScheduleUseCase = saveScheduleUseCase,
            importScheduleCsvUseCase = ImportScheduleCsvUseCase(
                importer = ScheduleCsvImporter(),
                saveScheduleUseCase = saveScheduleUseCase
            ),
            exportScheduleCsvUseCase = ExportScheduleCsvUseCase(
                getScheduleDetailUseCase = getScheduleDetailUseCase,
                exporter = ScheduleCsvExporter()
            )
        )
    }

    private class FakeScheduleRepository : ScheduleRepository {
        override fun observeSchedules(): Flow<List<Schedule>> = flowOf(emptyList())

        override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> = flowOf(emptyList())

        override suspend fun getScheduleById(scheduleId: Long): Schedule? = null

        override suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod> = emptyList()

        override suspend fun getScheduleUsageCount(scheduleId: Long): Int = 0

        override suspend fun countSchedules(): Int = 0

        override suspend fun saveScheduleWithPeriods(schedule: Schedule, periods: List<SchedulePeriod>): Long = 1L

        override suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult = DeleteScheduleResult.Deleted
    }
}
