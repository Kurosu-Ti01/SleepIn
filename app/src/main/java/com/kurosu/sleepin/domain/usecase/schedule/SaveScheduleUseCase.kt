package com.kurosu.sleepin.domain.usecase.schedule

import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import java.time.LocalTime

/**
 * Validates schedule editor input and persists schedule + periods as one logical action.
 *
 * This use case is intentionally strict because editor input is text-based and prone to user format errors.
 * Every validation failure returns a user-facing message instead of throwing, so the
 * ViewModel can surface it through UI state.
 */
class SaveScheduleUseCase(
    private val repository: ScheduleRepository
) {

    /**
     * Raw period payload coming from the editor layer.
     *
     * - [id] is optional and mainly meaningful when editing an existing schedule.
     * - [periodNumber] is business-visible numbering (1, 2, 3...).
     * - [startTime]/[endTime] are normalized `LocalTime` values already parsed by the ViewModel.
     */
    data class PeriodDraft(
        val id: Long = 0,
        val periodNumber: Int,
        val startTime: LocalTime,
        val endTime: LocalTime
    )

    /**
     * Persists a schedule and all period rows.
     * 
     * Validates individual schedule parameters and detailed period drafts. If successful, maps the 
     * payload to [Schedule] and [SchedulePeriod] entities, and executes the save across database boundaries.
     * 
     * Side effects:
     * - Inserts or updates entities using Room transactions via [repository] (IO-bound).
     *
     * @param scheduleId `null` when creating; non-null when editing.
     * @param name schedule display name. Must be non-blank.
     * @param createdAt original creation timestamp when editing; null for create.
     * @param periods all rows that should exist after save (replace-all semantics). Must not be empty.
     * @return Result of the schedule creation, returning [SaveScheduleResult.Success] containing the schedule primary key, or [SaveScheduleResult.ValidationError].
     */
    suspend operator fun invoke(
        scheduleId: Long?,
        name: String,
        createdAt: Long?,
        periods: List<PeriodDraft>
    ): SaveScheduleResult {
        // Name cannot be blank because list screen and menu rely on human-readable labels.
        if (name.isBlank()) {
            return SaveScheduleResult.ValidationError("请填写作息表名称")
        }
        // A schedule without periods is not meaningful for timetable placement.
        if (periods.isEmpty()) {
            return SaveScheduleResult.ValidationError("至少需要一节课")
        }
        // Period numbering starts at 1 by convention.
        if (periods.any { it.periodNumber <= 0 }) {
            return SaveScheduleResult.ValidationError("课节编号必须大于 0")
        }
        // Duplicate numbers would make downstream mapping ambiguous.
        if (periods.map { it.periodNumber }.distinct().size != periods.size) {
            return SaveScheduleResult.ValidationError("课节编号不能重复")
        }
        // Each row must represent a valid forward time interval.
        if (periods.any { !it.endTime.isAfter(it.startTime) }) {
            return SaveScheduleResult.ValidationError("结束时间必须晚于开始时间")
        }

        // Detect overlap by scanning start-time ordered periods.
        val sortedByStart = periods.sortedBy { it.startTime }
        for (index in 1 until sortedByStart.size) {
            if (sortedByStart[index].startTime.isBefore(sortedByStart[index - 1].endTime)) {
                return SaveScheduleResult.ValidationError("课节时间不能重叠")
            }
        }

        // Preserve createdAt on edit; generate a timestamp for newly created schedules.
        val schedule = Schedule(
            id = scheduleId ?: 0,
            name = name.trim(),
            createdAt = createdAt ?: System.currentTimeMillis()
        )

        // Repository owns transaction boundaries and replace-all behavior.
        val savedId = repository.saveScheduleWithPeriods(
            schedule = schedule,
            periods = periods.map {
                SchedulePeriod(
                    id = it.id,
                    scheduleId = scheduleId ?: 0,
                    periodNumber = it.periodNumber,
                    startTime = it.startTime,
                    endTime = it.endTime
                )
            }
        )
        return SaveScheduleResult.Success(savedId)
    }
}

/**
 * Output model for save attempts.
 */
sealed interface SaveScheduleResult {
    data class Success(val scheduleId: Long) : SaveScheduleResult
    data class ValidationError(val message: String) : SaveScheduleResult
}
