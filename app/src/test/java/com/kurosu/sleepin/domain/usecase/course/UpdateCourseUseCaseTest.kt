package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for updating a course.
 *
 * Validation is shared with [AddCourseUseCase] and covered by [CourseInputValidationTest]; these cases
 * cover what is specific to updating — the existing-course lookup and keeping the edited course out of
 * its own conflict check.
 */
class UpdateCourseUseCaseTest {

    @Test
    fun invoke_reportsThatTheCourseIsGone_whenItNoLongerExists() = runBlocking {
        val repository = FakeCourseRepository(existing = null)
        val useCase = UpdateCourseUseCase(repository, CheckConflictUseCase(repository))

        val result = useCase(
            courseId = COURSE_ID,
            timetableId = TIMETABLE_ID,
            totalWeeks = 18,
            schedulePeriods = (1..12).toSet(),
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            sessions = listOf(draft()),
            allowConflictSave = false
        )

        assertTrue("expected a rejection but got $result", result is CourseSaveResult.ValidationError)
        assertTrue((result as CourseSaveResult.ValidationError).message.contains("不存在"))
        assertTrue(repository.savedCourses.isEmpty())
    }

    @Test
    fun invoke_savesAndReportsPeriodsTheScheduleDoesNotDefine() = runBlocking {
        val repository = FakeCourseRepository(existing = existingCourse())
        val useCase = UpdateCourseUseCase(repository, CheckConflictUseCase(repository))

        val result = useCase(
            courseId = COURSE_ID,
            timetableId = TIMETABLE_ID,
            totalWeeks = 18,
            schedulePeriods = (1..12).toSet(),
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            sessions = listOf(draft(startPeriod = 11, endPeriod = 13)),
            allowConflictSave = false
        )

        assertTrue("expected a save but got $result", result is CourseSaveResult.Success)
        assertEquals(listOf(13), (result as CourseSaveResult.Success).unconfiguredPeriods)
        assertEquals(1, repository.savedCourses.size)
    }

    @Test
    fun invoke_doesNotReportAConflict_againstTheVeryCourseBeingEdited() = runBlocking {
        val repository = FakeCourseRepository(existing = existingCourse())
        val useCase = UpdateCourseUseCase(repository, CheckConflictUseCase(repository))

        // The candidate session is identical to the stored one. Counting that as a conflict would make
        // every edit of an existing course collide with itself.
        val result = useCase(
            courseId = COURSE_ID,
            timetableId = TIMETABLE_ID,
            totalWeeks = 18,
            schedulePeriods = (1..12).toSet(),
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            sessions = listOf(draft(startPeriod = 1, endPeriod = 2)),
            allowConflictSave = false
        )

        assertTrue("expected a save but got $result", result is CourseSaveResult.Success)
    }

    private fun draft(startPeriod: Int = 1, endPeriod: Int = 2) = CourseSessionDraft(
        id = 0L,
        dayOfWeek = 1,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        location = null,
        weekType = WeekType.ALL,
        startWeek = null,
        endWeek = null,
        customWeeks = emptyList()
    )

    private fun existingCourse() = CourseWithSessions(
        course = Course(
            id = COURSE_ID,
            timetableId = TIMETABLE_ID,
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            createdAt = 0L
        ),
        sessions = listOf(
            CourseSession(
                id = 1L,
                courseId = COURSE_ID,
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                location = null,
                weekType = WeekType.ALL,
                startWeek = null,
                endWeek = null,
                customWeeks = emptyList()
            )
        )
    )

    private class FakeCourseRepository(
        private val existing: CourseWithSessions?
    ) : CourseRepository {
        val savedCourses = mutableListOf<Pair<Course, List<CourseSession>>>()

        override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> = emptyFlow()

        override fun observeCourses(timetableId: Long): Flow<List<Course>> = emptyFlow()

        override fun observeCourseSessions(courseId: Long): Flow<List<CourseSession>> = emptyFlow()

        override suspend fun getCourseWithSessions(courseId: Long): CourseWithSessions? =
            existing?.takeIf { aggregate -> aggregate.course.id == courseId }

        override suspend fun getCoursesWithSessions(timetableId: Long): List<CourseWithSessions> =
            listOfNotNull(existing)

        override suspend fun saveCourseWithSessions(course: Course, sessions: List<CourseSession>): Long {
            savedCourses.add(course to sessions)
            return course.id.takeIf { it > 0L } ?: 1L
        }

        override suspend fun upsertCourse(course: Course): Long = throw UnsupportedOperationException()

        override suspend fun replaceCourseSessions(courseId: Long, sessions: List<CourseSession>) = Unit

        override suspend fun deleteCourse(courseId: Long) = Unit
    }

    private companion object {
        const val COURSE_ID = 7L
        const val TIMETABLE_ID = 1L
    }
}
