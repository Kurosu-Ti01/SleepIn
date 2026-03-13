package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository

/**
 * Loads one timetable by id for editor prefill.
 */
class GetTimetableDetailUseCase(
    private val repository: TimetableRepository
) {
    suspend operator fun invoke(timetableId: Long): Timetable? =
        repository.getTimetableById(timetableId)
}

