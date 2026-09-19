package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.CourseColorPalette
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.repository.TimetableImportPlan
import com.kurosu.sleepin.domain.repository.TimetableImportRepository
import com.kurosu.sleepin.domain.repository.TimetableImportResult
import com.kurosu.sleepin.domain.repository.TimetableRepository
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for turning a validated payload into a concrete import plan and persistence payload.
 */
class CommitImportUseCaseTest {

    @Test
    fun invoke_createsTimetableFromPayloadMetadata() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        useCase(
            submission = importSubmission(
                timetable = ImportedTimetable(
                    name = "2025 秋",
                    totalWeeks = 18,
                    startDate = LocalDate.of(2025, 9, 1)
                ),
                courses = listOf(importCourse(name = "高等数学", weeks = (1..18).toList()))
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = 7L
        )

        val plan = repository.lastPlan
        assertTrue("expected a create plan but got $plan", plan is TimetableImportPlan.CreateNew)
        val createPlan = plan as TimetableImportPlan.CreateNew
        assertEquals("2025 秋", createPlan.name)
        assertEquals(18, createPlan.totalWeeks)
        assertEquals(LocalDate.of(2025, 9, 1), createPlan.startDate)
        assertEquals(7L, createPlan.scheduleId)
        assertTrue(createPlan.setActive)

        val course = repository.lastCourses.single()
        assertEquals("高等数学", course.course.name)
        assertEquals(WeekType.ALL, course.sessions.single().weekType)
    }

    @Test
    fun invoke_usesDefaults_whenPayloadHasNoTimetableBlock() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        useCase(
            submission = importSubmission(courses = listOf(importCourse(name = "大学物理"))),
            target = ImportTarget.NewTimetable,
            scheduleId = 3L
        )

        val plan = repository.lastPlan as TimetableImportPlan.CreateNew
        assertEquals("导入的课程表", plan.name)
        assertEquals(16, plan.totalWeeks)
    }

    @Test
    fun invoke_rejectsImport_whenPayloadHasNoCourses_soExistingTimetableIsNotWiped() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository, existingTimetable(id = 5L, totalWeeks = 16, scheduleId = 3L))

        val result = useCase(
            submission = importSubmission(courses = emptyList()),
            target = ImportTarget.ExistingTimetable(timetableId = 5L),
            scheduleId = 3L
        )

        assertTrue("expected a rejection but got $result", result is TimetableImportResult.Rejected)
        assertNull(repository.lastPlan)
    }

    @Test
    fun invoke_assignsPaletteColorsInOrder_whenScriptLeavesColorUnset() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        useCase(
            submission = importSubmission(
                courses = listOf(
                    importCourse(name = "课程 A"),
                    importCourse(name = "课程 B"),
                    importCourse(name = "课程 C")
                )
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = 1L
        )

        assertEquals(
            listOf(CourseColorPalette[0], CourseColorPalette[1], CourseColorPalette[2]),
            repository.lastCourses.map { it.course.color }
        )
    }

    @Test
    fun invoke_keepsColorChosenByScript_whenProvided() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        useCase(
            submission = importSubmission(courses = listOf(importCourse(name = "课程 A", color = 0xFF123456.toInt()))),
            target = ImportTarget.NewTimetable,
            scheduleId = 1L
        )

        assertEquals(0xFF123456.toInt(), repository.lastCourses.single().course.color)
    }

    @Test
    fun invoke_mapsReportedWeeksToWeekRule() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        useCase(
            submission = importSubmission(
                timetable = ImportedTimetable(name = null, totalWeeks = 16, startDate = null),
                courses = listOf(
                    importCourse(name = "全周", weeks = (1..16).toList()),
                    importCourse(name = "前六周", weeks = listOf(1, 2, 3, 4, 5, 6)),
                    importCourse(name = "单周", weeks = listOf(1, 3, 5))
                )
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = 1L
        )

        val rules = repository.lastCourses.map { it.sessions.single() }
        assertEquals(WeekType.ALL, rules[0].weekType)
        assertEquals(WeekType.RANGE, rules[1].weekType)
        assertEquals(1, rules[1].startWeek)
        assertEquals(6, rules[1].endWeek)
        assertEquals(WeekType.CUSTOM, rules[2].weekType)
        assertEquals(listOf(1, 3, 5), rules[2].customWeeks)
    }

    @Test
    fun invoke_passesSessionDetailsThrough_andLeavesIdsToTheRepository() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        useCase(
            submission = importSubmission(
                courses = listOf(
                    ImportedCourse(
                        name = "体育",
                        teacher = "王老师",
                        note = "需穿运动鞋",
                        color = null,
                        sessions = listOf(
                            ImportedSession(
                                dayOfWeek = 3,
                                startPeriod = 5,
                                endPeriod = 6,
                                location = "体育馆",
                                weeks = listOf(2, 4)
                            )
                        )
                    )
                )
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = 1L
        )

        val aggregate = repository.lastCourses.single()
        assertEquals("王老师", aggregate.course.teacher)
        assertEquals("需穿运动鞋", aggregate.course.note)
        assertEquals(0L, aggregate.course.id)
        assertEquals(0L, aggregate.course.timetableId)

        val session = aggregate.sessions.single()
        assertEquals(3, session.dayOfWeek)
        assertEquals(5, session.startPeriod)
        assertEquals(6, session.endPeriod)
        assertEquals("体育馆", session.location)
        assertEquals(0L, session.courseId)
    }

    @Test
    fun invoke_overwritesExistingTimetable_andReusesItsSemesterLength() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository, existingTimetable(id = 5L, totalWeeks = 20, scheduleId = 3L))

        useCase(
            submission = importSubmission(
                timetable = ImportedTimetable(name = "忽略", totalWeeks = 18, startDate = null),
                courses = listOf(importCourse(name = "课程 A", weeks = (1..20).toList()))
            ),
            target = ImportTarget.ExistingTimetable(timetableId = 5L),
            scheduleId = 9L
        )

        val plan = repository.lastPlan
        assertTrue("expected an overwrite plan but got $plan", plan is TimetableImportPlan.Overwrite)
        val overwritePlan = plan as TimetableImportPlan.Overwrite
        assertEquals(5L, overwritePlan.timetableId)
        assertEquals(9L, overwritePlan.scheduleId)

        // The stored semester length wins, so the reported weeks still describe a full semester.
        assertEquals(WeekType.ALL, repository.lastCourses.single().sessions.single().weekType)
    }

    @Test
    fun invoke_keepsExistingSchedule_whenOverwriteDidNotChooseOne() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository, existingTimetable(id = 5L, totalWeeks = 16, scheduleId = 3L))

        useCase(
            submission = importSubmission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.ExistingTimetable(timetableId = 5L),
            scheduleId = 0L
        )

        val plan = repository.lastPlan as TimetableImportPlan.Overwrite
        assertEquals(3L, plan.scheduleId)
    }

    @Test
    fun invoke_returnsTimetableNotFound_whenOverwriteTargetIsGone() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        val result = useCase(
            submission = importSubmission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.ExistingTimetable(timetableId = 404L),
            scheduleId = 1L
        )

        assertEquals(TimetableImportResult.TimetableNotFound, result)
        assertNull(repository.lastPlan)
    }

    @Test
    fun invoke_rejectsImport_whenNoScheduleWasChosen() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        val result = useCase(
            submission = importSubmission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.NewTimetable,
            scheduleId = 0L
        )

        assertTrue("expected a rejection but got $result", result is TimetableImportResult.Rejected)
        assertNull(repository.lastPlan)
    }

    @Test
    fun invoke_rejectsImport_whenResolvedWeekCountIsOutsideSupportedRange() = runBlocking {
        val repository = RecordingTimetableImportRepository()
        val useCase = commitUseCase(repository)

        val result = useCase(
            submission = importSubmission(
                // Built directly, because the parser would already have refused this payload.
                timetable = ImportedTimetable(name = "异常学期", totalWeeks = 40, startDate = null),
                courses = listOf(importCourse(name = "课程 A"))
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = 1L
        )

        assertTrue("expected a rejection but got $result", result is TimetableImportResult.Rejected)
        assertNull(repository.lastPlan)
    }

    private fun commitUseCase(
        repository: TimetableImportRepository,
        vararg timetables: Timetable
    ): CommitImportUseCase = CommitImportUseCase(
        timetableImportRepository = repository,
        getTimetableDetailUseCase = GetTimetableDetailUseCase(FakeTimetableRepository(timetables.toList()))
    )

    private fun existingTimetable(id: Long, totalWeeks: Int, scheduleId: Long): Timetable = Timetable(
        id = id,
        name = "已有课程表",
        totalWeeks = totalWeeks,
        startDate = LocalDate.of(2025, 2, 24),
        scheduleId = scheduleId,
        isActive = false,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun importSubmission(
        timetable: ImportedTimetable? = null,
        courses: List<ImportedCourse>
    ): ImportSubmission = ImportSubmission(timetable = timetable, courses = courses)

    private fun importCourse(
        name: String,
        color: Int? = null,
        weeks: List<Int> = listOf(1)
    ): ImportedCourse = ImportedCourse(
        name = name,
        teacher = null,
        note = null,
        color = color,
        sessions = listOf(
            ImportedSession(
                dayOfWeek = 1,
                startPeriod = 1,
                endPeriod = 2,
                location = null,
                weeks = weeks
            )
        )
    )

    private class RecordingTimetableImportRepository : TimetableImportRepository {
        var lastPlan: TimetableImportPlan? = null
        var lastCourses: List<CourseWithSessions> = emptyList()

        override suspend fun commit(
            plan: TimetableImportPlan,
            courses: List<CourseWithSessions>
        ): TimetableImportResult {
            lastPlan = plan
            lastCourses = courses
            return TimetableImportResult.Imported(
                timetableId = 1L,
                courseCount = courses.size,
                sessionCount = courses.sumOf { it.sessions.size }
            )
        }
    }

    private class FakeTimetableRepository(
        private val timetables: List<Timetable>
    ) : TimetableRepository {
        override fun observeTimetables(): Flow<List<Timetable>> = flowOf(timetables)
        override fun observeActiveTimetable(): Flow<Timetable?> = flowOf(timetables.firstOrNull { it.isActive })
        override suspend fun getTimetableById(timetableId: Long): Timetable? =
            timetables.firstOrNull { it.id == timetableId }

        override suspend fun upsertTimetable(timetable: Timetable): Long =
            throw UnsupportedOperationException("not used by the import path")

        override suspend fun deleteTimetable(timetableId: Long) = Unit
        override suspend fun setActiveTimetable(timetableId: Long) = Unit
    }
}
