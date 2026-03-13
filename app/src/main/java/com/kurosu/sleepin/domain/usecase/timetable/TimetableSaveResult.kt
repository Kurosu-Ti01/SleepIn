package com.kurosu.sleepin.domain.usecase.timetable

/**
 * Unified output contract for timetable create/update operations.
 *
 * UI can show validation feedback directly and only navigate away on success.
 */
sealed interface TimetableSaveResult {
    data class Success(val timetableId: Long) : TimetableSaveResult
    data class ValidationError(val message: String) : TimetableSaveResult
    data object NotFound : TimetableSaveResult
}

