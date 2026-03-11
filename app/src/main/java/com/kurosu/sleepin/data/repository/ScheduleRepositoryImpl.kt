package com.kurosu.sleepin.data.repository

import androidx.room.withTransaction
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.local.dao.ScheduleDao
import com.kurosu.sleepin.data.local.dao.SchedulePeriodDao
import com.kurosu.sleepin.data.local.dao.TimetableDao
import com.kurosu.sleepin.data.local.mapper.toDomain
import com.kurosu.sleepin.data.local.mapper.toEntity
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of [ScheduleRepository].
 *
 * Design notes:
 * - Query methods are thin mapper wrappers around DAO calls.
 * - Save method performs replace-all of periods inside a DB transaction.
 * - Delete method is guarded: in-use schedules cannot be deleted.
 */
class ScheduleRepositoryImpl(
    private val database: SleepInDatabase,
    private val scheduleDao: ScheduleDao,
    private val schedulePeriodDao: SchedulePeriodDao,
    private val timetableDao: TimetableDao
) : ScheduleRepository {

    /**
     * Emits all schedules and maps Room entities to domain models.
     */
    override fun observeSchedules(): Flow<List<Schedule>> =
        scheduleDao.observeSchedules().map { entities -> entities.map { it.toDomain() } }

    /**
     * Emits periods for one schedule, ordered by period number at DAO level.
     */
    override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> =
        schedulePeriodDao.observeBySchedule(scheduleId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getScheduleById(scheduleId: Long): Schedule? =
        scheduleDao.getById(scheduleId)?.toDomain()

    override suspend fun getSchedulePeriods(scheduleId: Long): List<SchedulePeriod> =
        schedulePeriodDao.getBySchedule(scheduleId).map { it.toDomain() }

    /**
     * Returns how many timetable rows reference this schedule.
     * Used to block destructive deletes.
     */
    override suspend fun getScheduleUsageCount(scheduleId: Long): Int =
        timetableDao.countByScheduleId(scheduleId)

    override suspend fun countSchedules(): Int = scheduleDao.countSchedules()

    /**
     * Stores schedule + periods atomically.
     *
     * On update, periods are replaced entirely to match editor behavior: the UI sends
     * the full desired final list rather than patch operations.
     */
    override suspend fun saveScheduleWithPeriods(schedule: Schedule, periods: List<SchedulePeriod>): Long =
        database.withTransaction {
            val entity = schedule.toEntity()
            val scheduleId = if (entity.id == 0L) {
                scheduleDao.insert(entity)
            } else {
                scheduleDao.update(entity)
                entity.id
            }

            // Replace-all strategy keeps persistence semantics predictable and simple.
            schedulePeriodDao.deleteByScheduleId(scheduleId)
            if (periods.isNotEmpty()) {
                schedulePeriodDao.insertAll(
                    periods.map { period ->
                        // Always reset row id because old rows were deleted above.
                        period.copy(id = 0, scheduleId = scheduleId).toEntity()
                    }
                )
            }
            scheduleId
        }

    /**
     * Prevents deleting schedules that are referenced by existing timetables.
     */
    override suspend fun deleteSchedule(scheduleId: Long): DeleteScheduleResult {
        val usageCount = timetableDao.countByScheduleId(scheduleId)
        if (usageCount > 0) {
            return DeleteScheduleResult.InUse(usageCount)
        }
        scheduleDao.deleteById(scheduleId)
        return DeleteScheduleResult.Deleted
    }
}
