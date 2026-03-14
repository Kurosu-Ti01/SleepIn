package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams all courses (with session details) under one timetable.
 */
class GetCoursesForTimetableUseCase(
    private val repository: CourseRepository
) {
    operator fun invoke(timetableId: Long): Flow<List<CourseWithSessions>> =
        repository.observeCoursesWithSessions(timetableId)
}

