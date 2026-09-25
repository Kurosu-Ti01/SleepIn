package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import com.kurosu.sleepin.domain.repository.TimetableRepository
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for describing an import before it is written.
 *
 * These cover the mapping from a user's target choice to the summary they are shown, and the numbers
 * inside it. The property that matters most is that the preview resolves the target through the same
 * rules the write uses — a summary that disagreed with the write would be worse than no summary.
 */
class BuildImportPreviewUseCaseTest {

    @Test
    fun preview_labelsANewTimetableAndCountsWhatWillBeWritten() = runBlocking {
        val result = previewUseCase().invoke(
            submission = submission(
                timetable = ImportedTimetable(
                    name = "2025 秋",
                    totalWeeks = 18,
                    startDate = LocalDate.of(2025, 9, 1)
                ),
                courses = listOf(
                    importCourse(name = "高等数学", sessions = listOf(session(day = 1, from = 1, to = 2))),
                    importCourse(name = "大学物理", sessions = listOf(session(day = 3, from = 5, to = 6)))
                )
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = SCHEDULE_ID
        )

        val preview = (result as ImportPreviewResult.Ready).preview
        assertEquals(ImportTarget.NewTimetable, preview.target)
        assertEquals("新建课程表", preview.targetLabel)
        assertEquals("2025 秋", preview.timetableName)
        assertEquals("默认作息表", preview.scheduleLabel)
        assertEquals(18, preview.totalWeeks)
        assertEquals(2, preview.diagnostics.courseCount)
        assertEquals(2, preview.diagnostics.sessionCount)
    }

    @Test
    fun preview_usesTheDefaultName_whenThePayloadHasNoTimetableBlock() = runBlocking {
        val result = previewUseCase().invoke(
            submission = submission(courses = listOf(importCourse(name = "大学物理"))),
            target = ImportTarget.NewTimetable,
            scheduleId = SCHEDULE_ID
        )

        val preview = (result as ImportPreviewResult.Ready).preview
        assertEquals("导入的课程表", preview.timetableName)
        assertEquals(16, preview.totalWeeks)
    }

    @Test
    fun preview_namesTheTimetableAnOverwriteWillReplace() = runBlocking {
        val result = previewUseCase(existingTimetable(id = 5L, name = "期末课程表"))
            .invoke(
                submission = submission(courses = listOf(importCourse(name = "课程 A"))),
                target = ImportTarget.ExistingTimetable(timetableId = 5L),
                scheduleId = SCHEDULE_ID
            )

        val preview = (result as ImportPreviewResult.Ready).preview
        assertEquals(ImportTarget.ExistingTimetable(timetableId = 5L), preview.target)
        assertEquals("覆盖「期末课程表」", preview.targetLabel)
        assertEquals("期末课程表", preview.timetableName)
    }

    @Test
    fun preview_usesTheStoredSemesterLength_whenOverwriting() = runBlocking {
        val result = previewUseCase(existingTimetable(id = 5L, totalWeeks = 20))
            .invoke(
                submission = submission(
                    // Ignored on purpose: replacing courses must not silently shorten the semester the
                    // user already configured.
                    timetable = ImportedTimetable(name = "忽略", totalWeeks = 12, startDate = null),
                    courses = listOf(importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 1, to = 2, weeks = listOf(1, 20)))))
                ),
                target = ImportTarget.ExistingTimetable(timetableId = 5L),
                scheduleId = SCHEDULE_ID
            )

        val preview = (result as ImportPreviewResult.Ready).preview
        assertEquals(20, preview.totalWeeks)
        assertTrue("no week should be reported as outside a 20-week semester", preview.diagnostics.weeksOutsideSemester.isEmpty())
    }

    @Test
    fun preview_usesTheExistingSchedule_whenOverwriteDidNotChooseOne() = runBlocking {
        val result = previewUseCase(
            existingTimetable(id = 5L, name = "期末课程表", scheduleId = 9L),
            schedules = listOf(schedule(id = 9L, name = "备用作息表", periods = (1..10).toList()))
        ).invoke(
            submission = submission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.ExistingTimetable(timetableId = 5L),
            scheduleId = 0L
        )

        val preview = (result as ImportPreviewResult.Ready).preview
        assertEquals(9L, preview.scheduleId)
        assertEquals("备用作息表", preview.scheduleLabel)
    }

    @Test
    fun preview_reportsPeriodsTheResolvedScheduleDoesNotDefine() = runBlocking {
        val result = previewUseCase(
            schedules = listOf(schedule(periods = listOf(1, 2, 3, 4)))
        ).invoke(
            submission = submission(
                courses = listOf(importCourse(name = "课程 A", sessions = listOf(session(day = 1, from = 4, to = 6))))
            ),
            target = ImportTarget.NewTimetable,
            scheduleId = SCHEDULE_ID
        )

        val preview = (result as ImportPreviewResult.Ready).preview
        assertEquals(listOf(5, 6), preview.diagnostics.periodsMissingFromSchedule)
        assertEquals(1..1, preview.diagnostics.weekRange)
    }

    @Test
    fun preview_rejects_whenNoScheduleWasChosen() = runBlocking {
        val result = previewUseCase().invoke(
            submission = submission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.NewTimetable,
            scheduleId = 0L
        )

        assertTrue(result is ImportPreviewResult.Rejected)
    }

    @Test
    fun preview_reportsTimetableNotFound_whenTheOverwriteTargetIsGone() = runBlocking {
        val result = previewUseCase().invoke(
            submission = submission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.ExistingTimetable(timetableId = 404L),
            scheduleId = SCHEDULE_ID
        )

        assertEquals(ImportPreviewResult.TimetableNotFound, result)
    }

    @Test
    fun preview_reportsScheduleNotFound_whenTheScheduleIsGone() = runBlocking {
        val result = previewUseCase(schedules = emptyList()).invoke(
            submission = submission(courses = listOf(importCourse(name = "课程 A"))),
            target = ImportTarget.NewTimetable,
            scheduleId = SCHEDULE_ID
        )

        assertEquals(ImportPreviewResult.ScheduleNotFound, result)
    }

    private fun previewUseCase(
        vararg timetables: Timetable,
        schedules: List<ScheduleWithPeriods> = listOf(schedule())
    ): BuildImportPreviewUseCase {
        val timetableRepository = FakeTimetableRepository(timetables.toList())
        val scheduleRepository = FakeScheduleRepository(schedules)
        return BuildImportPreviewUseCase(
            getTimetableDetailUseCase = GetTimetableDetailUseCase(timetableRepository),
            getScheduleDetailUseCase = GetScheduleDetailUseCase(scheduleRepository)
        )
    }

    private fun existingTimetable(
        id: Long,
        name: String = "已有课程表",
        totalWeeks: Int = 16,
        scheduleId: Long = SCHEDULE_ID
    ): Timetable = Timetable(
        id = id,
        name = name,
        totalWeeks = totalWeeks,
        startDate = LocalDate.of(2025, 2, 24),
        scheduleId = scheduleId,
        isActive = false,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun schedule(
        id: Long = SCHEDULE_ID,
        name: String = "默认作息表",
        periods: List<Int> = (1..12).toList()
    ): ScheduleWithPeriods = ScheduleWithPeriods(
        schedule = Schedule(id = id, name = name, createdAt = 0L),
        periods = periods
    )

    private fun submission(
        timetable: ImportedTimetable? = null,
        courses: List<ImportedCourse>
    ): ImportSubmission = ImportSubmission(timetable = timetable, courses = courses)

    private fun importCourse(
        name: String,
        sessions: List<ImportedSession> = listOf(session(day = 1, from = 1, to = 2))
    ): ImportedCourse = ImportedCourse(
        name = name,
        teacher = null,
        note = null,
        color = null,
        sessions = sessions
    )

    private fun session(
        day: Int,
        from: Int,
        to: Int,
        weeks: List<Int> = listOf(1)
    ): ImportedSession = ImportedSession(
        dayOfWeek = day,
        startPeriod = from,
        endPeriod = to,
        location = null,
        weeks = weeks
    )

    private data class ScheduleWithPeriods(
        val schedule: Schedule,
        val periods: List<Int>
    )

    private class FakeTimetableRepository(
        private val timetables: List<Timetable>
    ) : TimetableRepository {
        override fun observeTimetables(): Flow<List<Timetable>> = flowOf(timetables)
        override fun observeActiveTimetable(): Flow<Timetable?> = flowOf(timetables.firstOrNull { it.isActive })
        override suspend fun getTimetableById(timetableId: Long): Timetable? =
            timetables.firstOrNull { it.id == timetableId }

        override suspend fun upsertTimetable(timetable: Timetable): Long =
            throw UnsupportedOperationException("not used by the preview path")

        override suspend fun deleteTimetable(timetableId: Long) = Unit
        override suspend fun setActiveTimetable(timetableId: Long) = Unit
    }

    private class FakeScheduleRepository(
        private val schedules: List<ScheduleWithPeriods>
    ) : ScheduleRepository {
        override fun observeSchedules(): Flow<List<Schedule>> = flowOf(schedules.map { it.schedule })
        override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> =
            flowOf(periodsOf(scheduleId))

        override suspend fun getScheduleById(scheduleId: Long): Schedule? =
            schedules.firstOrNull { it.schedule.id == scheduleId }?.schedule

        override suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod> = periodsOf(scheduleId)

        override suspend fun getScheduleUsageCount(scheduleId: Long): Int =
            throw UnsupportedOperationException("not used by the preview path")

        override suspend fun countSchedules(): Int = schedules.size

        override suspend fun saveScheduleWithPeriods(
            schedule: Schedule,
            periods: List<SchedulePeriod>
        ): Long = throw UnsupportedOperationException("not used by the preview path")

        override suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult =
            throw UnsupportedOperationException("not used by the preview path")

        private fun periodsOf(scheduleId: Long): List<SchedulePeriod> = schedules
            .firstOrNull { it.schedule.id == scheduleId }
            ?.periods
            .orEmpty()
            .map { periodNumber ->
                SchedulePeriod(
                    id = periodNumber.toLong(),
                    scheduleId = scheduleId,
                    periodNumber = periodNumber,
                    startTime = LocalTime.of(8, 0),
                    endTime = LocalTime.of(8, 45)
                )
            }
    }

    private companion object {
        const val SCHEDULE_ID = 3L
    }
}
