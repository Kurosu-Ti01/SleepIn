package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository
import java.time.LocalDate

/**
 * Creates a new timetable after validating editor input.
 */
class CreateTimetableUseCase(
    private val repository: TimetableRepository
) {
    suspend operator fun invoke(
        name: String,
        totalWeeks: Int,
        startDate: LocalDate,
        scheduleId: Long,
        colorScheme: String?
    ): TimetableSaveResult {
        if (name.isBlank()) {
            return TimetableSaveResult.ValidationError("请填写课程表名称")
        }
        if (totalWeeks !in 1..30) {
            return TimetableSaveResult.ValidationError("总周数应在 1 到 30 之间")
        }
        if (scheduleId <= 0L) {
            return TimetableSaveResult.ValidationError("请选择作息表")
        }

        val now = System.currentTimeMillis()
        val timetable = Timetable(
            name = name.trim(),
            totalWeeks = totalWeeks,
            startDate = startDate,
            scheduleId = scheduleId,
            colorScheme = colorScheme?.trim().takeUnless { it.isNullOrEmpty() },
            isActive = false,
            createdAt = now,
            updatedAt = now
        )
        val id = repository.upsertTimetable(timetable)
        return TimetableSaveResult.Success(id)
    }
}

