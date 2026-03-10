package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import kotlinx.coroutines.flow.Flow

/**
 * Contract for course and session persistence under a timetable.
 */
interface CourseRepository {
    fun observeCourses(timetableId: Long): Flow<List<Course>>
    fun observeCourseSessions(courseId: Long): Flow<List<CourseSession>>
    suspend fun upsertCourse(course: Course): Long
    suspend fun replaceCourseSessions(courseId: Long, sessions: List<CourseSession>)
    suspend fun deleteCourse(courseId: Long)
}

