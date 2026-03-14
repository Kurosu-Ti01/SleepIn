package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Loads one course aggregate for editor pre-fill in edit mode.
 */
class GetCourseDetailUseCase(
    private val repository: CourseRepository
) {
    suspend operator fun invoke(courseId: Long): CourseWithSessions? =
        repository.getCourseWithSessions(courseId)
}

