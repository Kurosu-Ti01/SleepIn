package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.data.csv.ScheduleCsvImporter
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for schedule CSV import orchestration.
 */
class ImportScheduleCsvUseCaseTest {

    @Test
    fun invoke_validCsv_importsOneNewSchedule() = runBlocking {
        val repository = RecordingScheduleRepository()
        val saveScheduleUseCase = SaveScheduleUseCase(repository)
        val useCase = ImportScheduleCsvUseCase(
            importer = ScheduleCsvImporter(),
            saveScheduleUseCase = saveScheduleUseCase
        )

        val csv = """
            作息表名称,节次,开始时间,结束时间
            春季作息,1,08:00,08:45
            春季作息,2,08:55,09:40
        """.trimIndent()

        val report = useCase(csv)

        assertEquals(1L, report.importedScheduleId)
        assertEquals(2, report.importedPeriodCount)
        assertTrue(report.errors.isEmpty())
        assertEquals("春季作息", repository.lastSavedSchedule?.name)
        assertEquals(2, repository.lastSavedPeriods.size)
    }

    @Test
    fun invoke_multiScheduleName_returnsErrorAndDoesNotSave() = runBlocking {
        val repository = RecordingScheduleRepository()
        val saveScheduleUseCase = SaveScheduleUseCase(repository)
        val useCase = ImportScheduleCsvUseCase(
            importer = ScheduleCsvImporter(),
            saveScheduleUseCase = saveScheduleUseCase
        )

        val csv = """
            作息表名称,节次,开始时间,结束时间
            春季作息,1,08:00,08:45
            夏季作息,2,08:55,09:40
        """.trimIndent()

        val report = useCase(csv)

        assertNull(report.importedScheduleId)
        assertEquals(0, report.importedPeriodCount)
        assertEquals(1, report.errors.size)
        assertTrue(report.errors.first().message.contains("exactly one schedule"))
        assertEquals(null, repository.lastSavedSchedule)
    }

    private class RecordingScheduleRepository : ScheduleRepository {
        private var nextId = 1L
        var lastSavedSchedule: Schedule? = null
        var lastSavedPeriods: List<SchedulePeriod> = emptyList()

        override fun observeSchedules(): Flow<List<Schedule>> = flowOf(emptyList())

        override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> = flowOf(emptyList())

        override suspend fun getScheduleById(scheduleId: Long): Schedule? = null

        override suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod> = emptyList()

        override suspend fun getScheduleUsageCount(scheduleId: Long): Int = 0

        override suspend fun countSchedules(): Int = 0

        override suspend fun saveScheduleWithPeriods(schedule: Schedule, periods: List<SchedulePeriod>): Long {
            val id = if (schedule.id == 0L) nextId++ else schedule.id
            lastSavedSchedule = schedule.copy(id = id)
            lastSavedPeriods = periods.map { it.copy(scheduleId = id) }
            return id
        }

        override suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult = DeleteScheduleResult.Deleted
    }
}


