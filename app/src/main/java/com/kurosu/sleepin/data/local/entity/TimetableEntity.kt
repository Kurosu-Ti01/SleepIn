package com.kurosu.sleepin.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Database table for semester-level timetable metadata.
 */
@Entity(
    tableName = "timetables",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["scheduleId"])]
)
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val totalWeeks: Int,
    val startDate: LocalDate,
    val scheduleId: Long,
    val colorScheme: String? = null,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

