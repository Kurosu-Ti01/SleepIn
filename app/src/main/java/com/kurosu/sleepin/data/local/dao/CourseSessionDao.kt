package com.kurosu.sleepin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kurosu.sleepin.data.local.entity.CourseSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for recurring course session rows.
 */
@Dao
interface CourseSessionDao {
    @Query("SELECT * FROM course_sessions WHERE courseId = :courseId ORDER BY dayOfWeek, startPeriod")
    fun observeByCourse(courseId: Long): Flow<List<CourseSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<CourseSessionEntity>)

    @Query("DELETE FROM course_sessions WHERE courseId = :courseId")
    suspend fun deleteByCourseId(courseId: Long)
}

