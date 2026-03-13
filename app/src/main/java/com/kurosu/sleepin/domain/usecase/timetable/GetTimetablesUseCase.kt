package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams all timetables for list-style UI rendering.
 *
 * Keeping this access behind a dedicated use case allows future business rules
 * (filtering, sorting, permission checks) to be added without touching UI code.
 */
class GetTimetablesUseCase(
    private val repository: TimetableRepository
) {
    operator fun invoke(): Flow<List<Timetable>> = repository.observeTimetables()
}

