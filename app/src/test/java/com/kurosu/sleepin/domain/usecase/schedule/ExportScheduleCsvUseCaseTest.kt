package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.data.csv.ScheduleCsvExporter
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Unit tests for schedule CSV export orchestration.
 */
class ExportScheduleCsvUseCaseTest {

    @Test
    fun invoke_existingSchedule_returnsCsvWithHeaderAndRows() = runBlocking {
        val repository = FixedScheduleRepository()
        val getScheduleDetailUseCase = GetScheduleDetailUseCase(repository)
        val useCase = ExportScheduleCsvUseCase(
            getScheduleDetailUseCase = getScheduleDetailUseCase,
            exporter = ScheduleCsvExporter()
        )

        val csv = useCase(1L)

        assertTrue(csv.startsWith("\uFEFF作息表名称,节次,开始时间,结束时间"))
        assertTrue(csv.contains("春季作息,1,08:00,08:45"))
        assertTrue(csv.contains("春季作息,2,08:55,09:40"))
    }

    private class FixedScheduleRepository : ScheduleRepository {
        override fun observeSchedules(): Flow<List<Schedule>> = flowOf(emptyList())

        override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> = flowOf(emptyList())

        override suspend fun getScheduleById(scheduleId: Long): Schedule? {
            if (scheduleId != 1L) return null
            return Schedule(id = 1L, name = "春季作息", createdAt = 1L)
        }

        override suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod> {
            if (scheduleId != 1L) return emptyList()
            return listOf(
                SchedulePeriod(
                    id = 11L,
                    scheduleId = 1L,
                    periodNumber = 2,
                    startTime = LocalTime.of(8, 55),
                    endTime = LocalTime.of(9, 40)
                ),
                SchedulePeriod(
                    id = 10L,
                    scheduleId = 1L,
                    periodNumber = 1,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(8, 45)
                )
            )
        }

        override suspend fun getScheduleUsageCount(scheduleId: Long): Int = 0

        override suspend fun countSchedules(): Int = 1

        override suspend fun saveScheduleWithPeriods(schedule: Schedule, periods: List<SchedulePeriod>): Long =
            throw UnsupportedOperationException()

        override suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult = DeleteScheduleResult.Deleted
    }
}

