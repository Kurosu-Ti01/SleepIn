package com.kurosu.sleepin.domain.usecase.course

/**
 * Save result for both add and update actions in the course editor.
 *
 * The result is explicit so UI can show accurate feedback without parsing exception text.
 */
sealed interface CourseSaveResult {
    /**
     * The course was written.
     *
     * @param unconfiguredPeriods period numbers the saved sessions reference that the timetable's
     *   schedule does not define. This is not a failure: the course is stored and rendered, it just has
     *   no class time until the user completes the schedule. The UI reports it as a notice.
     */
    data class Success(
        val courseId: Long,
        val unconfiguredPeriods: List<Int> = emptyList()
    ) : CourseSaveResult

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

