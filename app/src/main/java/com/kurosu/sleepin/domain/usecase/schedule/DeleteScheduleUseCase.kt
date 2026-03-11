package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.repository.ScheduleRepository

class DeleteScheduleUseCase(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(scheduleId: Long): DeleteScheduleResult =
        repository.deleteSchedule(scheduleId)
}

