package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.data.local.dao.TimetableDao
import com.kurosu.sleepin.data.local.mapper.toDomain
import com.kurosu.sleepin.data.local.mapper.toEntity
import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of timetable repository.
 */
class TimetableRepositoryImpl(
    private val timetableDao: TimetableDao
) : TimetableRepository {

    override fun observeTimetables(): Flow<List<Timetable>> =
        timetableDao.observeTimetables().map { entities -> entities.map { it.toDomain() } }

    override fun observeActiveTimetable(): Flow<Timetable?> =
        timetableDao.observeActiveTimetable().map { entity -> entity?.toDomain() }

    override suspend fun upsertTimetable(timetable: Timetable): Long {
        val entity = timetable.toEntity()
        return if (entity.id == 0L) {
            timetableDao.insert(entity)
        } else {
            timetableDao.update(entity)
            entity.id
        }
    }

    override suspend fun deleteTimetable(timetableId: Long) {
        timetableDao.deleteById(timetableId)
    }

    override suspend fun setActiveTimetable(timetableId: Long) {
        timetableDao.setActiveTimetable(timetableId)
    }
}
