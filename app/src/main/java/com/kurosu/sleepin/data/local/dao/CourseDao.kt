package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kurosu.sleepin.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for course base rows.
 */
@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE timetableId = :timetableId ORDER BY id DESC")
    fun observeByTimetable(timetableId: Long): Flow<List<CourseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteById(courseId: Long)
}

