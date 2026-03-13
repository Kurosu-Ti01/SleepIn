package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kurosu.sleepin.data.local.entity.TimetableEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for semester timetable records and active timetable switching.
 */
@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetables ORDER BY updatedAt DESC")
    fun observeTimetables(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetables WHERE isActive = 1 LIMIT 1")
    fun observeActiveTimetable(): Flow<TimetableEntity?>

    @Query("SELECT * FROM timetables WHERE id = :timetableId LIMIT 1")
    suspend fun getById(timetableId: Long): TimetableEntity?

    @Query("SELECT COUNT(*) FROM timetables WHERE scheduleId = :scheduleId")
    suspend fun countByScheduleId(scheduleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timetable: TimetableEntity): Long

    @Update
    suspend fun update(timetable: TimetableEntity)

    @Query("DELETE FROM timetables WHERE id = :timetableId")
    suspend fun deleteById(timetableId: Long)

    @Query("UPDATE timetables SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveTimetable(activeId: Long)
}
