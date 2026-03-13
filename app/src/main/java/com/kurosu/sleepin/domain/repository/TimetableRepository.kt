package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.Timetable
import kotlinx.coroutines.flow.Flow

/**
 * Contract for timetable persistence and active timetable switching.
 */
interface TimetableRepository {
    fun observeTimetables(): Flow<List<Timetable>>
    fun observeActiveTimetable(): Flow<Timetable?>
    suspend fun getTimetableById(timetableId: Long): Timetable?
    suspend fun upsertTimetable(timetable: Timetable): Long
    suspend fun deleteTimetable(timetableId: Long)
    suspend fun setActiveTimetable(timetableId: Long)
}

