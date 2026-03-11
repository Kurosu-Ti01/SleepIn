package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import kotlinx.coroutines.flow.Flow

/**
 * Contract for schedule template and period persistence.
 *
 * A "schedule" is a reusable daily time template (period 1 starts at X, period 2 at Y, ...).
 * Timetables reference a schedule by `scheduleId`.
 */
interface ScheduleRepository {

    /** Emits all schedule templates. */
    fun observeSchedules(): Flow<List<Schedule>>

    /** Emits period rows for one schedule template. */
    fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>>

    /** Returns one schedule template, or null if it does not exist. */
    suspend fun getScheduleById(scheduleId: Long): Schedule?

    /** Returns all period rows for one schedule template. */
    suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod>

    /** Returns how many timetable records currently reference this schedule template. */
    suspend fun getScheduleUsageCount(scheduleId: Long): Int

    /** Returns total number of schedule templates. Used by startup seed logic. */
    suspend fun countSchedules(): Int

    /**
     * Saves schedule + periods as one logical operation.
     *
     * Implementations are expected to keep this atomic so partial writes do not occur.
     */
    suspend fun saveScheduleWithPeriods(schedule: Schedule, periods: List<SchedulePeriod>): Long

    /** Attempts to delete a schedule template and returns the exact result. */
    suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult
}

/**
 * Deletion outcome for schedule templates.
 */
sealed interface DeleteScheduleResult {
    data object Deleted : DeleteScheduleResult
    data class InUse(val timetableCount: Int) : DeleteScheduleResult
}
