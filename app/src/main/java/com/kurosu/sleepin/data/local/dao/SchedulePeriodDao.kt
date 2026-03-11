package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kurosu.sleepin.data.local.entity.SchedulePeriodEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for period definitions inside one schedule template.
 */
@Dao
interface SchedulePeriodDao {
    @Query("SELECT * FROM schedule_periods WHERE scheduleId = :scheduleId ORDER BY periodNumber")
    fun observeBySchedule(scheduleId: Long): Flow<List<SchedulePeriodEntity>>

    @Query("SELECT * FROM schedule_periods WHERE scheduleId = :scheduleId ORDER BY periodNumber")
    suspend fun getBySchedule(scheduleId: Long): List<SchedulePeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(periods: List<SchedulePeriodEntity>)

    @Query("DELETE FROM schedule_periods WHERE scheduleId = :scheduleId")
    suspend fun deleteByScheduleId(scheduleId: Long)
}
