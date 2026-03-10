package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kurosu.sleepin.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for schedule template rows.
 */
@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY id DESC")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :scheduleId")
    suspend fun deleteById(scheduleId: Long)
}

