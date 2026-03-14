package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.data.local.dao.CourseDao
import com.kurosu.sleepin.data.local.dao.CourseSessionDao
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.local.mapper.toDomain
import com.kurosu.sleepin.data.local.mapper.toEntity
import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.repository.CourseRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of course repository.
 */
class CourseRepositoryImpl(
    private val database: SleepInDatabase,
    private val courseDao: CourseDao,
    private val courseSessionDao: CourseSessionDao
) : CourseRepository {

    override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> =
        courseDao.observeWithSessionsByTimetable(timetableId)
            .map { aggregates -> aggregates.map { it.toDomain() } }

    override fun observeCourses(timetableId: Long): Flow<List<Course>> =
        courseDao.observeByTimetable(timetableId).map { entities -> entities.map { it.toDomain() } }

    override fun observeCourseSessions(courseId: Long): Flow<List<CourseSession>> =
        courseSessionDao.observeByCourse(courseId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertCourse(course: Course): Long {
        val entity = course.toEntity()
        return if (entity.id == 0L) {
            courseDao.insert(entity)
        } else {
            courseDao.update(entity)
            entity.id
        }
    }

    override suspend fun getCourseWithSessions(courseId: Long): CourseWithSessions? =
        courseDao.getWithSessionsById(courseId)?.toDomain()

    override suspend fun getCoursesWithSessions(timetableId: Long): List<CourseWithSessions> =
        courseDao.getWithSessionsByTimetable(timetableId).map { it.toDomain() }

    override suspend fun saveCourseWithSessions(course: Course, sessions: List<CourseSession>): Long =
        database.withTransaction {
            // Persist the course first because session rows reference generated/known course id.
            val courseId = upsertCourse(course)
            // Replace-all keeps editing model simple and avoids partial stale session rows.
            replaceCourseSessions(courseId, sessions)
            courseId
        }

    override suspend fun replaceCourseSessions(courseId: Long, sessions: List<CourseSession>) {
        // Replace-all keeps session list in sync with editor state in one call site.
        courseSessionDao.deleteByCourseId(courseId)
        if (sessions.isNotEmpty()) {
            courseSessionDao.insertAll(sessions.map { session -> session.copy(courseId = courseId).toEntity() })
        }
    }

    override suspend fun deleteCourse(courseId: Long) {
        courseDao.deleteById(courseId)
    }
}
