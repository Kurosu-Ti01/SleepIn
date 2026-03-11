package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDefaultScheduleUseCaseTest {

    @Test
    fun invoke_insertsDefaultSchedule_whenDatabaseIsEmpty() = runBlocking {
        val repository = FakeScheduleRepository(countSchedules = 0)
        val useCase = SeedDefaultScheduleUseCase(repository)

        useCase()

        assertEquals(1, repository.saveCalls)
        assertEquals("默认作息", repository.savedSchedule?.name)
        assertEquals(12, repository.savedPeriods.size)
    }

    @Test
    fun invoke_skipsInsert_whenScheduleAlreadyExists() = runBlocking {
        val repository = FakeScheduleRepository(countSchedules = 1)
        val useCase = SeedDefaultScheduleUseCase(repository)

        useCase()

        assertEquals(0, repository.saveCalls)
        assertTrue(repository.savedPeriods.isEmpty())
    }

    private class FakeScheduleRepository(
        private val countSchedules: Int
    ) : ScheduleRepository {
        var saveCalls: Int = 0
        var savedSchedule: Schedule? = null
        var savedPeriods: List<SchedulePeriod> = emptyList()

        override fun observeSchedules(): Flow<List<Schedule>> = flowOf(emptyList())

        override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> = flowOf(emptyList())

        override suspend fun getScheduleById(scheduleId: Long): Schedule? = null

        override suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod> = emptyList()

        override suspend fun getScheduleUsageCount(scheduleId: Long): Int = 0

        override suspend fun countSchedules(): Int = countSchedules

        override suspend fun saveScheduleWithPeriods(schedule: Schedule, periods: List<SchedulePeriod>): Long {
            saveCalls += 1
            savedSchedule = schedule.copy(id = 1)
            savedPeriods = periods
            return 1
        }

        override suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult = DeleteScheduleResult.Deleted
    }
}

