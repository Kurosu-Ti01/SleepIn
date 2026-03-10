package com.kurosu.sleepin.domain.model

import java.time.LocalDate

/**
 * Domain model for one semester timetable.
 */
data class Timetable(
    val id: Long = 0,
    val name: String,
    val totalWeeks: Int,
    val startDate: LocalDate,
    val scheduleId: Long,
    val colorScheme: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

