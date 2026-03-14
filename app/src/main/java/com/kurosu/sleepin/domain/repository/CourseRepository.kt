package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import kotlinx.coroutines.flow.Flow

/**
 * Contract for course and session persistence under a timetable.
 */
interface CourseRepository {
    fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>>
    fun observeCourses(timetableId: Long): Flow<List<Course>>
    fun observeCourseSessions(courseId: Long): Flow<List<CourseSession>>
    suspend fun getCourseWithSessions(courseId: Long): CourseWithSessions?
    suspend fun getCoursesWithSessions(timetableId: Long): List<CourseWithSessions>
    suspend fun saveCourseWithSessions(course: Course, sessions: List<CourseSession>): Long
    suspend fun upsertCourse(course: Course): Long
    suspend fun replaceCourseSessions(courseId: Long, sessions: List<CourseSession>)
    suspend fun deleteCourse(courseId: Long)
}

