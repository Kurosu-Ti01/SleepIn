package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kurosu.sleepin.data.local.entity.TimetableEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for semester timetable records and active timetable switching.
 * Handles the main configurations of distinct "semester" boundaries.
 */
@Dao
interface TimetableDao {
    /**
     * Observes all timetables sorted by most recently updated.
     * Returns a `Flow` that auto-emits when a timetable is added/updated/deleted.
     */
    @Query("SELECT * FROM timetables ORDER BY updatedAt DESC")
    fun observeTimetables(): Flow<List<TimetableEntity>>

    /**
     * Observes the single active timetable. Useful since the app usually focuses on one current semester.
     * Yields null if no timetable has `isActive = 1`.
     */
    @Query("SELECT * FROM timetables WHERE isActive = 1 LIMIT 1")
    fun observeActiveTimetable(): Flow<TimetableEntity?>

    /**
     * One-shot fetch for a specific timetable based on ID.
     */
    @Query("SELECT * FROM timetables WHERE id = :timetableId LIMIT 1")
    suspend fun getById(timetableId: Long): TimetableEntity?

    /**
     * Checks how many timetables reference a specific schedule ID.
     * Used for constraints: if > 0, the schedule cannot be safely deleted without breaking these timetables.
     */
    @Query("SELECT COUNT(*) FROM timetables WHERE scheduleId = :scheduleId")
    suspend fun countByScheduleId(scheduleId: Long): Int

    /**
     * Inserts a new timetable and returns the new auto-generated ID.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(timetable: TimetableEntity): Long

    /**
     * Updates an existing timetable.
     */
    @Update
    suspend fun update(timetable: TimetableEntity)

    /**
     * Deletes a timetable by ID. Will cascade delete corresponding courses if set up.
     */
    @Query("DELETE FROM timetables WHERE id = :timetableId")
    suspend fun deleteById(timetableId: Long)

    /**
     * Performs a transaction-like update to enforce a single active timetable rule.
     * Sets `isActive` to 1 for the passed [activeId], and 0 for all other rows.
     */
    @Query("UPDATE timetables SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveTimetable(activeId: Long)
}
