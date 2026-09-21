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
 * Unit tests for saving a course whose periods are not all defined by the timetable's schedule.
 *
 * Such a course must still be saved — otherwise an imported one could never be edited — and the
 * mismatch must come back as a reportable notice instead of a validation failure.
 */
class AddCourseUseCaseTest {

    @Test
    fun invoke_savesTheCourse_andReportsPeriodsTheScheduleDoesNotDefine() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = AddCourseUseCase(repository, CheckConflictUseCase(repository))

        val result = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            // The schedule stops at period 12, so period 13 has no class time yet.
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
        assertEquals(11, repository.savedCourses.single().second.single().startPeriod)
        assertEquals(13, repository.savedCourses.single().second.single().endPeriod)
    }

    @Test
    fun invoke_reportsGapPeriods_whenTheScheduleSkipsANumber() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = AddCourseUseCase(repository, CheckConflictUseCase(repository))

        val result = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            schedulePeriods = setOf(1, 2, 4),
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            sessions = listOf(draft(startPeriod = 2, endPeriod = 4)),
            allowConflictSave = false
        )

        assertEquals(listOf(3), (result as CourseSaveResult.Success).unconfiguredPeriods)
    }

    @Test
    fun invoke_reportsNoMismatch_whenEveryReferencedPeriodIsConfigured() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = AddCourseUseCase(repository, CheckConflictUseCase(repository))

        val result = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            schedulePeriods = (1..12).toSet(),
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            sessions = listOf(draft(startPeriod = 1, endPeriod = 2)),
            allowConflictSave = false
        )

        assertEquals(emptyList<Int>(), (result as CourseSaveResult.Success).unconfiguredPeriods)
    }

    @Test
    fun invoke_refusesToSave_whenAPeriodExceedsTheStructuralCeiling() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = AddCourseUseCase(repository, CheckConflictUseCase(repository))

        val result = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            schedulePeriods = (1..12).toSet(),
            name = "高等数学",
            teacher = null,
            color = 0,
            note = null,
            sessions = listOf(draft(startPeriod = 1, endPeriod = 9999)),
            allowConflictSave = false
        )

        assertTrue("expected a rejection but got $result", result is CourseSaveResult.ValidationError)
        assertTrue(repository.savedCourses.isEmpty())
    }

    private fun draft(startPeriod: Int, endPeriod: Int) = CourseSessionDraft(
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

    private class RecordingCourseRepository : CourseRepository {
        val savedCourses = mutableListOf<Pair<Course, List<CourseSession>>>()

        override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> = emptyFlow()

        override fun observeCourses(timetableId: Long): Flow<List<Course>> = emptyFlow()

        override fun observeCourseSessions(courseId: Long): Flow<List<CourseSession>> = emptyFlow()

        override suspend fun getCourseWithSessions(courseId: Long): CourseWithSessions? = null

        override suspend fun getCoursesWithSessions(timetableId: Long): List<CourseWithSessions> = emptyList()

        override suspend fun saveCourseWithSessions(course: Course, sessions: List<CourseSession>): Long {
            savedCourses.add(course to sessions)
            return savedCourses.size.toLong()
        }

        override suspend fun upsertCourse(course: Course): Long = throw UnsupportedOperationException()

        override suspend fun replaceCourseSessions(courseId: Long, sessions: List<CourseSession>) = Unit

        override suspend fun deleteCourse(courseId: Long) = Unit
    }
}
