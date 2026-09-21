package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.findUndefinedPeriods
import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Updates an existing course and replaces its full session list.
 *
 * Shares [validateCourseInput] with [AddCourseUseCase], and like it does not check period numbers
 * against the timetable's schedule: an imported course may use periods the schedule has not configured
 * yet, and it must stay editable. Those periods are reported through
 * [CourseSaveResult.Success.unconfiguredPeriods].
 */
class UpdateCourseUseCase(
    private val repository: CourseRepository,
    private val checkConflictUseCase: CheckConflictUseCase
) {
    /**
     * @param schedulePeriods period numbers the timetable's schedule defines. Used only to diagnose a
     *   mismatch; sessions outside this set are saved as they are.
     */
    suspend operator fun invoke(
        courseId: Long,
        timetableId: Long,
        totalWeeks: Int,
        schedulePeriods: Set<Int>,
        name: String,
        teacher: String?,
        color: Int,
        note: String?,
        sessions: List<CourseSessionDraft>,
        allowConflictSave: Boolean
    ): CourseSaveResult {
        val existing = repository.getCourseWithSessions(courseId)
            ?: return CourseSaveResult.ValidationError("课程不存在，可能已被删除")

        val validation = validateCourseInput(name = name, totalWeeks = totalWeeks, sessions = sessions)
        if (validation is CourseInputValidation.Invalid) {
            return CourseSaveResult.ValidationError(validation.message)
        }

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

        return CourseSaveResult.Success(
            courseId = savedId,
            unconfiguredPeriods = findUndefinedPeriods(
                usedPeriodRanges = sessions.map { draft -> draft.startPeriod..draft.endPeriod },
                definedPeriods = schedulePeriods
            )
        )
    }
}
