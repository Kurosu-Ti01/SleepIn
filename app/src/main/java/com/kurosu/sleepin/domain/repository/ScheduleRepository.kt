package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import kotlinx.coroutines.flow.Flow

/**
 * Contract for schedule template and period persistence.
 */
interface ScheduleRepository {
    fun observeSchedules(): Flow<List<Schedule>>
    fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>>
    suspend fun upsertSchedule(schedule: Schedule): Long
    suspend fun replaceSchedulePeriods(scheduleId: Long, periods: List<SchedulePeriod>)
    suspend fun deleteSchedule(scheduleId: Long)
}

