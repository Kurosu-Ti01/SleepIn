package com.kurosu.sleepin.domain.model

/**
 * Domain model for a daily period timetable template.
 */
data class Schedule(
    val id: Long = 0,
    val name: String,
    val createdAt: Long
)

