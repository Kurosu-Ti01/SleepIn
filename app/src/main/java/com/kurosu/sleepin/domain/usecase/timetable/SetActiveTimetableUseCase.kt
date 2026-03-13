package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.repository.TimetableRepository

/**
 * Marks one timetable as active.
 */
class SetActiveTimetableUseCase(
    private val repository: TimetableRepository
) {
    suspend operator fun invoke(timetableId: Long) {
        repository.setActiveTimetable(timetableId)
    }
}

