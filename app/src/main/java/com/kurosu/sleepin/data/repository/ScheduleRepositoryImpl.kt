package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.data.local.dao.ScheduleDao
import com.kurosu.sleepin.data.local.dao.SchedulePeriodDao
import com.kurosu.sleepin.data.local.mapper.toDomain
import com.kurosu.sleepin.data.local.mapper.toEntity
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of schedule repository.
 */
class ScheduleRepositoryImpl(
    private val scheduleDao: ScheduleDao,
    private val schedulePeriodDao: SchedulePeriodDao
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<Schedule>> =
        scheduleDao.observeSchedules().map { entities -> entities.map { it.toDomain() } }

    override fun observeSchedulePeriods(scheduleId: Long): Flow<List<SchedulePeriod>> =
        schedulePeriodDao.observeBySchedule(scheduleId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertSchedule(schedule: Schedule): Long {
        val entity = schedule.toEntity()
        return if (entity.id == 0L) {
            scheduleDao.insert(entity)
        } else {
            scheduleDao.update(entity)
            entity.id
        }
    }

    override suspend fun replaceSchedulePeriods(scheduleId: Long, periods: List<SchedulePeriod>) {
        // Replace-all mirrors the schedule editor save behavior for phase 2.
        schedulePeriodDao.deleteByScheduleId(scheduleId)
        if (periods.isNotEmpty()) {
            schedulePeriodDao.insertAll(periods.map { period -> period.copy(scheduleId = scheduleId).toEntity() })
        }
    }

    override suspend fun deleteSchedule(scheduleId: Long) {
        scheduleDao.deleteById(scheduleId)
    }
}
