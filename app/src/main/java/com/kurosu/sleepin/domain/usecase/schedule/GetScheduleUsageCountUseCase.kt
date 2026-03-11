package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.repository.ScheduleRepository

class GetScheduleUsageCountUseCase(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(scheduleId: Long): Int =
        repository.getScheduleUsageCount(scheduleId)
}

