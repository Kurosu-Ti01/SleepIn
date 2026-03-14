package com.kurosu.sleepin.domain.usecase.course

/**
 * Save result for both add and update actions in the course editor.
 *
 * The result is explicit so UI can show accurate feedback without parsing exception text.
 */
sealed interface CourseSaveResult {
    data class Success(val courseId: Long) : CourseSaveResult
    data class ValidationError(val message: String) : CourseSaveResult
    data class ConflictDetected(val conflicts: List<CourseConflict>) : CourseSaveResult
}

/**
 * One conflict item between the candidate session and an existing session.
 */
data class CourseConflict(
    val existingCourseId: Long,
    val existingCourseName: String,
    val dayOfWeek: Int,
    val overlapStartPeriod: Int,
    val overlapEndPeriod: Int,
    val overlapWeeks: List<Int>
)

