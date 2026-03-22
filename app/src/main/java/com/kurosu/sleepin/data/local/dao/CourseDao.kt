package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kurosu.sleepin.data.local.entity.CourseEntity
import com.kurosu.sleepin.data.local.entity.CourseWithSessionsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for [CourseEntity].
 *
 * Provides database operations for `courses` table. This interface covers standard mapping queries
 * and transaction handling for retrieving compound entity `CourseWithSessionsEntity`.
 * Flow returns are lifecycle-aware and emit whenever underlying tables change.
 */
@Dao
interface CourseDao {
    /**
     * Observes courses with their associated sessions for a given timetable.
     * Uses `@Transaction` because it queries two tables (courses and course_sessions) under the hood
     * to avoid data inconsistency.
     * Returns a `Flow` that emits whenever related rows update.
     */
    @Transaction
    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY id DESC")
    fun observeWithSessionsByTimetable(timetableId: Long): Flow<List<CourseWithSessionsEntity>>

    /**
     * Observes only the base courses (without sessions) for a given timetable.
     * Useful for lightweight lists where full session detail is not needed.
     */
    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY id DESC")
    fun observeByTimetable(timetableId: Long): Flow<List<CourseEntity>>

    /**
     * Performs a one-shot fetch for a timetable's courses and their sessions.
     * Suitable for one-time operations like CSV export or conflict checking logic, without observation.
     */
    @Transaction
    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY id DESC")
    suspend fun getWithSessionsByTimetable(timetableId: Long): List<CourseWithSessionsEntity>

    /**
     * Fetches a specific course and its sessions by ID.
     */
    @Transaction
    @Query("SELECT * FROM courses WHERE id = :courseId LIMIT 1")
    suspend fun getWithSessionsById(courseId: Long): CourseWithSessionsEntity?

    /**
     * Inserts a new course. Returns the newly generated primary key `id`.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    /**
     * Updates an existing course row matching the entity's primary key.
     */
    @Update
    suspend fun update(course: CourseEntity)

    /**
     * Deletes a specific course. Cascading handles session deletion if configured on FK.
     */
    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteById(courseId: Long)
}

