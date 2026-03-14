package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Deletes one course; child session rows are removed by foreign key cascade.
 */
class DeleteCourseUseCase(
    private val repository: CourseRepository
) {
    suspend operator fun invoke(courseId: Long) {
        repository.deleteCourse(courseId)
    }
}

