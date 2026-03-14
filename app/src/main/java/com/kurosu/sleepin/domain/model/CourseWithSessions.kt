package com.kurosu.sleepin.domain.model

/**
 * Aggregate domain model that bundles one course with all of its recurring session slots.
 *
 * This shape is intentionally used by list/editor/conflict use cases so callers do not need
 * to manually combine multiple streams or issue N+1 repository requests.
 */
data class CourseWithSessions(
    val course: Course,
    val sessions: List<CourseSession>
)

