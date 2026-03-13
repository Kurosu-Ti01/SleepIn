package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.first

/**
 * Deletes one timetable.
 *
 * If the deleted timetable is currently active, this use case promotes a fallback
 * timetable (if any exists) so Home can continue to show a consistent active context.
 */
class DeleteTimetableUseCase(
    private val repository: TimetableRepository
) {
    suspend operator fun invoke(timetableId: Long) {
        val wasActive = repository.observeActiveTimetable().first()?.id == timetableId
        val fallbackId = repository.observeTimetables().first()
            .firstOrNull { it.id != timetableId }
            ?.id

        repository.deleteTimetable(timetableId)

        if (wasActive && fallbackId != null) {
            repository.setActiveTimetable(fallbackId)
        }
    }
}

