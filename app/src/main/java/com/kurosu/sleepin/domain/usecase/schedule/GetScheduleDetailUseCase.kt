package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.ScheduleRepository

data class ScheduleDetail(
    val schedule: Schedule,
    val periods: List<SchedulePeriod>
)

class GetScheduleDetailUseCase(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke(scheduleId: Long): ScheduleDetail? {
        val schedule = repository.getScheduleById(scheduleId) ?: return null
        val periods = repository.getSchedulePeriods(scheduleId)
        return ScheduleDetail(schedule = schedule, periods = periods)
    }
}

