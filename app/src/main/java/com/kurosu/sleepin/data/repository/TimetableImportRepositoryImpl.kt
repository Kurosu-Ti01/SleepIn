package com.kurosu.sleepin.data.repository

import androidx.room.withTransaction
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.local.dao.CourseDao
import com.kurosu.sleepin.data.local.dao.CourseSessionDao
import com.kurosu.sleepin.data.local.dao.ScheduleDao
import com.kurosu.sleepin.data.local.dao.SchedulePeriodDao
import com.kurosu.sleepin.data.local.dao.TimetableDao
import com.kurosu.sleepin.data.local.entity.ScheduleEntity
import com.kurosu.sleepin.data.local.entity.TimetableEntity
import com.kurosu.sleepin.data.local.mapper.toEntity
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.domain.repository.ImportScheduleDraft
import com.kurosu.sleepin.domain.repository.TimetableImportPlan
import com.kurosu.sleepin.domain.repository.TimetableImportRepository
import com.kurosu.sleepin.domain.repository.TimetableImportResult

/**
 * Room-backed implementation of [TimetableImportRepository].
 *
 * The whole import runs inside one transaction. That matters because an import combines several
 * writes — create or retarget a timetable, optionally create a schedule template with its periods,
 * delete the old courses, insert the new ones and their sessions — and a failure part-way through
 * would otherwise leave the user with a timetable that is empty or half-filled (or with an orphan
 * schedule template) while the error message claims nothing was imported.
 *
 * The implementation works directly with DAOs instead of going through [CourseRepository] because a
 * bulk replacement must not open one transaction per course; the existing repository API is shaped
 * around editing a single course at a time.
 */
class TimetableImportRepositoryImpl(
    private val database: SleepInDatabase,
    private val timetableDao: TimetableDao,
    private val scheduleDao: ScheduleDao,
    private val schedulePeriodDao: SchedulePeriodDao,
    private val courseDao: CourseDao,
    private val courseSessionDao: CourseSessionDao
) : TimetableImportRepository {

    override suspend fun commit(
        plan: TimetableImportPlan,
        courses: List<CourseWithSessions>
    ): TimetableImportResult = database.withTransaction {
        when (plan) {
            is TimetableImportPlan.CreateNew -> commitCreateNew(plan, courses)
            is TimetableImportPlan.Overwrite -> commitOverwrite(plan, courses)
        }
    }

    private suspend fun commitCreateNew(
        plan: TimetableImportPlan.CreateNew,
        courses: List<CourseWithSessions>
    ): TimetableImportResult {
        // Checked up front so the user gets "作息表不存在" instead of a raw foreign-key failure.
        val scheduleId = resolveScheduleId(plan.scheduleId, plan.newSchedule)
            ?: return TimetableImportResult.ScheduleNotFound

        val now = System.currentTimeMillis()
        val timetableId = timetableDao.insert(
            TimetableEntity(
                name = plan.name,
                totalWeeks = plan.totalWeeks,
                startDate = plan.startDate,
                scheduleId = scheduleId,
                colorScheme = null,
                // Activation is applied below, because it must also clear the flag on every other row.
                isActive = false,
                createdAt = now,
                updatedAt = now
            )
        )

        if (plan.setActive) {
            timetableDao.setActiveTimetable(timetableId)
        }

        val sessionCount = replaceCourses(timetableId, courses)
        return TimetableImportResult.Imported(
            timetableId = timetableId,
            courseCount = courses.size,
            sessionCount = sessionCount,
            scheduleId = scheduleId
        )
    }

    private suspend fun commitOverwrite(
        plan: TimetableImportPlan.Overwrite,
        courses: List<CourseWithSessions>
    ): TimetableImportResult {
        val existing = timetableDao.getById(plan.timetableId)
            ?: return TimetableImportResult.TimetableNotFound
        val scheduleId = resolveScheduleId(plan.scheduleId, plan.newSchedule)
            ?: return TimetableImportResult.ScheduleNotFound

        timetableDao.update(
            existing.copy(
                scheduleId = scheduleId,
                updatedAt = System.currentTimeMillis()
            )
        )

        val sessionCount = replaceCourses(existing.id, courses)
        return TimetableImportResult.Imported(
            timetableId = existing.id,
            courseCount = courses.size,
            sessionCount = sessionCount,
            scheduleId = scheduleId
        )
    }

    /**
     * Returns the schedule the result should reference, creating [newSchedule] when the payload
     * carried class times.
     *
     * Null means the schedule the plan named is not there (any more); callers turn that into
     * [TimetableImportResult.ScheduleNotFound]. Creation happens before the timetable row is written
     * so the timetable never points at a schedule that failed to be stored, and both statements are
     * inside the same transaction as the rest of the import.
     */
    private suspend fun resolveScheduleId(
        scheduleId: Long,
        newSchedule: ImportScheduleDraft?
    ): Long? {
        if (newSchedule == null) {
            return scheduleId.takeIf { scheduleDao.getById(it) != null }
        }

        val createdId = scheduleDao.insert(
            ScheduleEntity(name = newSchedule.name, createdAt = System.currentTimeMillis())
        )
        schedulePeriodDao.insertAll(
            newSchedule.periods.map { period ->
                // Ids come from the caller's payload and the schedule id does not exist yet.
                period.copy(id = 0L, scheduleId = createdId).toEntity()
            }
        )
        return createdId
    }

    /**
     * Replaces every course of [timetableId] with [courses] and returns how many session rows were
     * written.
     *
     * Must only be called from inside the import transaction: the delete and the inserts have to be
     * observed as one change, otherwise the week grid would briefly render an empty timetable.
     */
    private suspend fun replaceCourses(
        timetableId: Long,
        courses: List<CourseWithSessions>
    ): Int {
        // Session rows disappear through the cascade on course_sessions.courseId.
        courseDao.deleteByTimetableId(timetableId)
        if (courses.isEmpty()) return 0

        val courseIds = courseDao.insertAll(
            courses.map { aggregate ->
                // The ids come from the caller's payload, so they are reset to let Room assign fresh
                // ones that belong to this timetable only.
                aggregate.course.copy(id = 0, timetableId = timetableId).toEntity()
            }
        )
        check(courseIds.size == courses.size) {
            "Room returned ${courseIds.size} course ids for ${courses.size} courses"
        }

        val sessionEntities = courses.flatMapIndexed { index, aggregate ->
            aggregate.sessions.map { session ->
                session.copy(id = 0, courseId = courseIds[index]).toEntity()
            }
        }
        courseSessionDao.insertAll(sessionEntities)

        return sessionEntities.size
    }
}
