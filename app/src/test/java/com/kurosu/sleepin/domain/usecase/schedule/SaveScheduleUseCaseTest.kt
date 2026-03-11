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
import java.time.LocalTime

class SaveScheduleUseCaseTest {

    @Test
    fun invoke_returnsValidationError_whenNameIsBlank() = runBlocking {
        val useCase = SaveScheduleUseCase(FakeScheduleRepository())

        val result = useCase(
            scheduleId = null,
            name = "  ",
            createdAt = null,
            periods = listOf(
                SaveScheduleUseCase.PeriodDraft(
                    periodNumber = 1,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(8, 45)
                )
            )
        )

        assertTrue(result is SaveScheduleResult.ValidationError)
    }

    @Test
    fun invoke_savesScheduleAndPeriods_whenInputIsValid() = runBlocking {
        val repository = FakeScheduleRepository()
        val useCase = SaveScheduleUseCase(repository)

        val result = useCase(
            scheduleId = null,
            name = "春季作息",
            createdAt = 100L,
            periods = listOf(
                SaveScheduleUseCase.PeriodDraft(
                    periodNumber = 1,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(8, 45)
                ),
                SaveScheduleUseCase.PeriodDraft(
                    periodNumber = 2,
                    startTime = LocalTime.of(8, 55),
                    endTime = LocalTime.of(9, 40)
                )
            )
        )

        assertTrue(result is SaveScheduleResult.Success)
        assertEquals(1L, repository.lastSavedSchedule?.id)
        assertEquals("春季作息", repository.lastSavedSchedule?.name)
        assertEquals(2, repository.lastSavedPeriods.size)
    }

    private class FakeScheduleRepository : ScheduleRepository {
        var nextId = 1L
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

