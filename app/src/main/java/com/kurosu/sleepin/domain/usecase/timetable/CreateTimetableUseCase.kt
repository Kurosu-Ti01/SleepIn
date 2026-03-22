package com.kurosu.sleepin.domain.usecase.timetable

import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.repository.TimetableRepository
import java.time.LocalDate

/**
 * Creates a new timetable after validating editor input.
 *
 * This use case handles the business logic of constructing a new timetable entity
 * and storing it in the database.
 * 
 * @property repository The [TimetableRepository] used to persist the data to the Room database.
 */
class CreateTimetableUseCase(
    private val repository: TimetableRepository
) {
    /**
     * Executes the timetable creation process.
     * 
     * Validates user inputs such as name, weeks, and linked schedule ID. If any check fails,
     * it returns a [TimetableSaveResult.ValidationError] for the ViewModel to surface in the UI state.
     * On success, writes the payload into the Room database.
     * 
     * Side effects:
     * - Performs IO tasks (database insertion) when the validation passes.
     * 
     * @param name The display name of the timetable. Must not be blank.
     * @param totalWeeks Total duration of the semester (valid range: 1 to 30).
     * @param startDate The start date of the newly created timetable.
     * @param scheduleId Identifier for the linked schedule that defines time blocks.
     * @param colorScheme Optional theme color configuration string.
     * @return A subclass of [TimetableSaveResult] representing the outcome of the operation.
     */
    suspend operator fun invoke(
        name: String,
        totalWeeks: Int,
        startDate: LocalDate,
        scheduleId: Long,
        colorScheme: String?
    ): TimetableSaveResult {
        // Enforce a valid name to prevent UI glitches with empty labels.
        if (name.isBlank()) {
            return TimetableSaveResult.ValidationError("请填写课程表名称")
        }
        // Limit total weeks to typical semester boundaries.
        if (totalWeeks !in 1..30) {
            return TimetableSaveResult.ValidationError("总周数应在 1 到 30 之间")
        }
        // Must reference a valid structural schedule ID as assigned by Room.
        if (scheduleId <= 0L) {
            return TimetableSaveResult.ValidationError("请选择作息表")
        }

        // Establish creation time boundaries for auditing.
        val now = System.currentTimeMillis()
        
        // Assemble the domain entity.
        val timetable = Timetable(
            name = name.trim(),
            totalWeeks = totalWeeks,
            startDate = startDate,
            scheduleId = scheduleId,
            colorScheme = colorScheme?.trim().takeUnless { it.isNullOrEmpty() },
            isActive = false, // Newly created timetables shouldn't be active immediately
            createdAt = now,
            updatedAt = now
        )
        
        // Push to local DB via repository running in the suspended coroutine scope
        val id = repository.upsertTimetable(timetable)
        return TimetableSaveResult.Success(id)
    }
}

