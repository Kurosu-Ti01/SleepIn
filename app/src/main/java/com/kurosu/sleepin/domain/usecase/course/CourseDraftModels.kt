package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.WeekType

/**
 * Editable session payload sent from UI layer to course use cases.
 *
 * Keeping this as a dedicated draft model decouples validation from persistence entities and
 * allows the UI to evolve without leaking storage details into Composables.
 */
data class CourseSessionDraft(
    val id: Long = 0,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val location: String?,
    val weekType: WeekType,
    val startWeek: Int?,
    val endWeek: Int?,
    val customWeeks: List<Int>
)

