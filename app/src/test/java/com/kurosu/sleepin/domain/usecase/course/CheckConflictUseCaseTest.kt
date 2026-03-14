package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckConflictUseCaseTest {

    @Test
    fun invoke_returnsConflict_whenDayPeriodAndWeeksOverlap() = runBlocking {
        val repository = FakeCourseRepository(
            existingCourses = listOf(
                CourseWithSessions(
                    course = Course(
                        id = 100,
                        timetableId = 1,
                        name = "Math",
                        color = 0,
                        createdAt = 1
                    ),
                    sessions = listOf(
                        CourseSession(
                            courseId = 100,
                            dayOfWeek = 1,
                            startPeriod = 1,
                            endPeriod = 2,
                            weekType = WeekType.RANGE,
                            startWeek = 1,
                            endWeek = 10
                        )
                    )
                )
            )
        )
        val useCase = CheckConflictUseCase(repository)

        val conflicts = useCase(
            timetableId = 1,
            totalWeeks = 18,
            editingCourseId = null,
            candidateSessions = listOf(
                CourseSessionDraft(
                    dayOfWeek = 1,
                    startPeriod = 2,
                    endPeriod = 3,
                    location = "A101",
                    weekType = WeekType.RANGE,
                    startWeek = 5,
                    endWeek = 6,
                    customWeeks = emptyList()
                )
            )
        )

        assertEquals(1, conflicts.size)
        assertEquals("Math", conflicts.first().existingCourseName)
        assertEquals(listOf(5, 6), conflicts.first().overlapWeeks)
    }

    @Test
    fun invoke_returnsEmpty_whenOnlyPeriodOverlapsButWeeksDoNot() = runBlocking {
        val repository = FakeCourseRepository(
            existingCourses = listOf(
                CourseWithSessions(
                    course = Course(
                        id = 100,
                        timetableId = 1,
                        name = "Physics",
                        color = 0,
                        createdAt = 1
                    ),
                    sessions = listOf(
                        CourseSession(
                            courseId = 100,
                            dayOfWeek = 3,
                            startPeriod = 3,
                            endPeriod = 4,
                            weekType = WeekType.RANGE,
                            startWeek = 1,
                            endWeek = 8
                        )
                    )
                )
            )
        )
        val useCase = CheckConflictUseCase(repository)

        val conflicts = useCase(
            timetableId = 1,
            totalWeeks = 18,
            editingCourseId = null,
            candidateSessions = listOf(
                CourseSessionDraft(
                    dayOfWeek = 3,
                    startPeriod = 3,
                    endPeriod = 4,
                    location = null,
                    weekType = WeekType.RANGE,
                    startWeek = 10,
                    endWeek = 12,
                    customWeeks = emptyList()
                )
            )
        )

        assertTrue(conflicts.isEmpty())
    }

    private class FakeCourseRepository(
        private val existingCourses: List<CourseWithSessions>
    ) : CourseRepository {
        override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> = flowOf(existingCourses)

        override fun observeCourses(timetableId: Long): Flow<List<Course>> = flowOf(existingCourses.map { it.course })

        override fun observeCourseSessions(courseId: Long): Flow<List<CourseSession>> =
            flowOf(existingCourses.firstOrNull { it.course.id == courseId }?.sessions.orEmpty())

        override suspend fun getCourseWithSessions(courseId: Long): CourseWithSessions? =
            existingCourses.firstOrNull { it.course.id == courseId }

        override suspend fun getCoursesWithSessions(timetableId: Long): List<CourseWithSessions> = existingCourses

        override suspend fun saveCourseWithSessions(course: Course, sessions: List<CourseSession>): Long = 1

        override suspend fun upsertCourse(course: Course): Long = 1

        override suspend fun replaceCourseSessions(courseId: Long, sessions: List<CourseSession>) = Unit

        override suspend fun deleteCourse(courseId: Long) = Unit
    }
}

