package com.kurosu.sleepin.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database table for course base information.
 */
@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["timetableId"])]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timetableId: Long,
    val name: String,
    val teacher: String? = null,
    val color: Int,
    val note: String? = null,
    val createdAt: Long
)

