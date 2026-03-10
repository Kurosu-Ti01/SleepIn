package com.kurosu.sleepin.domain.model

/**
 * Domain model for a course basic profile.
 */
data class Course(
    val id: Long = 0,
    val timetableId: Long,
    val name: String,
    val teacher: String? = null,
    val color: Int,
    val note: String? = null,
    val createdAt: Long
)

