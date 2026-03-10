package com.kurosu.sleepin.domain.model

/**
 * Domain model for one recurring session slot of a course.
 */
data class CourseSession(
    val id: Long = 0,
    val courseId: Long,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val location: String? = null,
    val weekType: WeekType,
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val customWeeks: List<Int> = emptyList()
)

