package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository
import java.time.LocalDate

/**
 * Updates an existing timetable while preserving immutable historical fields.
 */
class UpdateTimetableUseCase(
    private val repository: TimetableRepository
) {
    suspend operator fun invoke(
        timetableId: Long,
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

        val current = repository.getTimetableById(timetableId) ?: return TimetableSaveResult.NotFound
        val updated = Timetable(
            id = current.id,
            name = name.trim(),
            totalWeeks = totalWeeks,
            startDate = startDate,
            scheduleId = scheduleId,
            colorScheme = colorScheme?.trim().takeUnless { it.isNullOrEmpty() },
            // Keep current active state unchanged during edit to avoid accidental switch.
            isActive = current.isActive,
            createdAt = current.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        repository.upsertTimetable(updated)
        return TimetableSaveResult.Success(updated.id)
    }
}

