package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import java.time.LocalTime

class SeedDefaultScheduleUseCase(
    private val repository: ScheduleRepository
) {
    suspend operator fun invoke() {
        if (repository.countSchedules() > 0) return

        val now = System.currentTimeMillis()
        val schedule = Schedule(name = "默认作息", createdAt = now)
        val periods = buildDefaultPeriods()
        repository.saveScheduleWithPeriods(schedule, periods)
    }

    private fun buildDefaultPeriods(): List<SchedulePeriod> {
        val result = mutableListOf<SchedulePeriod>()
        var start = LocalTime.of(8, 0)

        for (periodNumber in 1..12) {
            val end = start.plusMinutes(45)
            result += SchedulePeriod(
                scheduleId = 0,
                periodNumber = periodNumber,
                startTime = start,
                endTime = end
            )
            if (periodNumber < 12) {
                val breakMinutes = when (periodNumber) {
                    4 -> 90L
                    8 -> 60L
                    else -> 10L
                }
                start = end.plusMinutes(breakMinutes)
            }
        }
        return result
    }
}

