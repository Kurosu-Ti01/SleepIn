package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.findUndefinedPeriods
import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Creates a new course and its recurring sessions after validating editor input.
 *
 * Period numbers are not checked against the timetable's schedule. A course may reference a period the
 * schedule does not define yet — an imported timetable can use more periods than the user has
 * configured — and refusing the save would make such a course impossible to edit afterwards. Those
 * periods are reported through [CourseSaveResult.Success.unconfiguredPeriods] instead, so the UI can
 * point the user at the schedule editor where the gap is actually closed.
 */
class AddCourseUseCase(
    private val repository: CourseRepository,
    private val checkConflictUseCase: CheckConflictUseCase
) {
    /**
     * @param schedulePeriods period numbers the timetable's schedule defines. Used only to diagnose a
     *   mismatch; sessions outside this set are saved as they are.
     */
    suspend operator fun invoke(
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
        val validation = validateCourseInput(name = name, totalWeeks = totalWeeks, sessions = sessions)
        if (validation is CourseInputValidation.Invalid) {
            return CourseSaveResult.ValidationError(validation.message)
        }

        val conflicts = checkConflictUseCase(
            timetableId = timetableId,
            totalWeeks = totalWeeks,
            editingCourseId = null,
            candidateSessions = sessions
        )
        if (conflicts.isNotEmpty() && !allowConflictSave) {
            return CourseSaveResult.ConflictDetected(conflicts)
        }

        val now = System.currentTimeMillis()
        val savedId = repository.saveCourseWithSessions(
            course = Course(
                timetableId = timetableId,
                name = name.trim(),
                teacher = teacher?.trim().takeUnless { it.isNullOrEmpty() },
                color = color,
                note = note?.trim().takeUnless { it.isNullOrEmpty() },
                createdAt = now
            ),
            sessions = sessions.map { draft ->
                CourseSession(
                    id = draft.id,
                    courseId = 0,
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
