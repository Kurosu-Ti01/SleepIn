package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Updates an existing course and replaces its full session list.
 */
class UpdateCourseUseCase(
    private val repository: CourseRepository,
    private val checkConflictUseCase: CheckConflictUseCase
) {
    suspend operator fun invoke(
        courseId: Long,
        timetableId: Long,
        totalWeeks: Int,
        maxPeriod: Int,
        name: String,
        teacher: String?,
        color: Int,
        note: String?,
        sessions: List<CourseSessionDraft>,
        allowConflictSave: Boolean
    ): CourseSaveResult {
        val existing = repository.getCourseWithSessions(courseId)
            ?: return CourseSaveResult.ValidationError("Course does not exist")

        val validationError = validate(name, totalWeeks, maxPeriod, sessions)
        if (validationError != null) return CourseSaveResult.ValidationError(validationError)

        val conflicts = checkConflictUseCase(
            timetableId = timetableId,
            totalWeeks = totalWeeks,
            editingCourseId = courseId,
            candidateSessions = sessions
        )
        if (conflicts.isNotEmpty() && !allowConflictSave) {
            return CourseSaveResult.ConflictDetected(conflicts)
        }

        val savedId = repository.saveCourseWithSessions(
            course = Course(
                id = courseId,
                timetableId = timetableId,
                name = name.trim(),
                teacher = teacher?.trim().takeUnless { it.isNullOrEmpty() },
                color = color,
                note = note?.trim().takeUnless { it.isNullOrEmpty() },
                createdAt = existing.course.createdAt
            ),
            sessions = sessions.map { draft ->
                CourseSession(
                    id = draft.id,
                    courseId = courseId,
                    dayOfWeek = draft.dayOfWeek,
                    startPeriod = draft.startPeriod,
                    endPeriod = draft.endPeriod,
                    location = draft.location?.trim().takeUnless { it.isNullOrEmpty() },
                    weekType = draft.weekType,
                    startWeek = draft.startWeek,
                    endWeek = draft.endWeek,
                    customWeeks = draft.customWeeks.distinct().sorted()
                )
            }
        )

        return CourseSaveResult.Success(savedId)
    }

    private fun validate(
        name: String,
        totalWeeks: Int,
        maxPeriod: Int,
        sessions: List<CourseSessionDraft>
    ): String? {
        if (name.isBlank()) return "Please enter a course name"
        if (totalWeeks !in 1..30) return "Invalid timetable total weeks"
        if (maxPeriod <= 0) return "Invalid schedule period configuration"
        if (sessions.isEmpty()) return "Please add at least one session"

        sessions.forEachIndexed { index, session ->
            if (session.dayOfWeek !in 1..7) return "Session ${index + 1}: day must be between 1 and 7"
            if (session.startPeriod !in 1..maxPeriod || session.endPeriod !in 1..maxPeriod) {
                return "Session ${index + 1}: period range exceeds schedule limits"
            }
            if (session.startPeriod > session.endPeriod) return "Session ${index + 1}: start period must be <= end period"
            when (session.weekType) {
                WeekType.ALL -> Unit
                WeekType.RANGE -> {
                    val start = session.startWeek ?: return "Session ${index + 1}: start week is required"
                    val end = session.endWeek ?: return "Session ${index + 1}: end week is required"
                    if (start !in 1..totalWeeks || end !in 1..totalWeeks || start > end) {
                        return "Session ${index + 1}: invalid week range"
                    }
                }
                WeekType.CUSTOM -> {
                    if (session.customWeeks.isEmpty()) return "Session ${index + 1}: choose at least one custom week"
                    if (session.customWeeks.any { it !in 1..totalWeeks }) return "Session ${index + 1}: custom week out of range"
                }
            }
        }

        return null
    }
}

