package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams the currently active timetable.
 *
 * Home screen and quick-switch menus rely on this stream to stay synchronized
 * when active timetable changes from any entry point.
 */
class GetActiveTimetableUseCase(
    private val repository: TimetableRepository
) {
    operator fun invoke(): Flow<Timetable?> = repository.observeActiveTimetable()
}

