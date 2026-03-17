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
            maxPeriod = 12,
            rawCsv = csv
        )

        assertEquals(2, report.importedCourseCount)
        assertEquals(3, report.importedSessionCount)
        assertEquals(0, report.errors.size)
        assertEquals(2, repository.savedCourses.size)
        assertEquals(2, repository.savedCourses[0].second.size)
        assertEquals(1, repository.savedCourses[1].second.size)
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

