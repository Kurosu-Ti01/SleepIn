package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for CSV import orchestration and same-name course merge behavior.
 */
class ImportCsvUseCaseTest {

    @Test
    fun importCsv_mergesRowsByCourseNameAndPersistsGroupedSessions() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = ImportCsvUseCase(
            importer = CsvImporter(),
            courseRepository = repository
        )

        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,1,2,1-16
            高等数学,张三,A101,3,1,2,1-16
            线性代数,李四,B203,2,3,4,1-18
        """.trimIndent()

        val report = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            schedulePeriods = (1..12).toSet(),
            rawCsv = csv
        )

        assertEquals(2, report.importedCourseCount)
        assertEquals(3, report.importedSessionCount)
        assertEquals(0, report.errors.size)
        assertEquals(emptyList<Int>(), report.periodsMissingFromSchedule)
        assertEquals(2, repository.savedCourses.size)
        assertEquals(2, repository.savedCourses[0].second.size)
        assertEquals(1, repository.savedCourses[1].second.size)
    }

    @Test
    fun importCsv_keepsSessionsOutsideTheSchedule_andReportsTheMissingPeriods() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = ImportCsvUseCase(
            importer = CsvImporter(),
            courseRepository = repository
        )

        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,11,13,1-16
        """.trimIndent()

        val report = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            // The schedule stops at period 12, so period 13 has no class time configured yet.
            schedulePeriods = (1..12).toSet(),
            rawCsv = csv
        )

        assertEquals(1, report.importedCourseCount)
        assertEquals(1, report.importedSessionCount)
        assertEquals(0, report.errors.size)
        assertEquals(listOf(13), report.periodsMissingFromSchedule)

        val savedSession = repository.savedCourses.single().second.single()
        assertEquals(11, savedSession.startPeriod)
        assertEquals(13, savedSession.endPeriod)
    }

    @Test
    fun importCsv_reportsGapPeriods_whenScheduleSkipsANumberInsideTheRange() = runBlocking {
        val repository = RecordingCourseRepository()
        val useCase = ImportCsvUseCase(
            importer = CsvImporter(),
            courseRepository = repository
        )

        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,2,4,1-16
        """.trimIndent()

        val report = useCase(
            timetableId = 1L,
            totalWeeks = 18,
            // Periods 1 and 2 and 4 exist, but 3 is missing: both endpoints are defined, so only
            // expanding the range can find the gap.
            schedulePeriods = setOf(1, 2, 4),
            rawCsv = csv
        )

        assertEquals(listOf(3), report.periodsMissingFromSchedule)
        assertEquals(1, report.importedSessionCount)
    }

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

