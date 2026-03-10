package com.kurosu.sleepin.domain.model

import java.time.LocalTime

/**
 * Domain model for one class period time range.
 */
data class SchedulePeriod(
    val id: Long = 0,
    val scheduleId: Long,
    val periodNumber: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)

