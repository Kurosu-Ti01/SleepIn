package com.kurosu.sleepin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Database table for schedule templates (daily periods definition).
 */
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long
)

