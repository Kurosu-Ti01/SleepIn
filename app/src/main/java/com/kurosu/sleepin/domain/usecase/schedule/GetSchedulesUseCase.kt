package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow

class GetSchedulesUseCase(
    private val repository: ScheduleRepository
) {
    operator fun invoke(): Flow<List<Schedule>> = repository.observeSchedules()
}

