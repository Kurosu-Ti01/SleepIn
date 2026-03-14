package com.kurosu.sleepin.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room projection that loads a course row together with all child session rows.
 *
 * Using an explicit relation model keeps DAO APIs expressive and avoids hand-written joins
 * in higher layers whenever we need full course detail.
 */
data class CourseWithSessionsEntity(
    @Embedded
    val course: CourseEntity,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val sessions: List<CourseSessionEntity>
)

