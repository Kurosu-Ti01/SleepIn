package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.repository.CourseRepository

/**
 * Detects timetable conflicts between candidate course sessions and existing persisted courses.
 *
 * Conflict definition:
 * - Same day of week
 * - Period ranges overlap
 * - Week sets overlap after resolving ALL/RANGE/CUSTOM into explicit week numbers
 */
class CheckConflictUseCase(
    private val repository: CourseRepository
) {
    suspend operator fun invoke(
        timetableId: Long,
        totalWeeks: Int,
        editingCourseId: Long?,
        candidateSessions: List<CourseSessionDraft>
    ): List<CourseConflict> {
        val existingCourses = repository.getCoursesWithSessions(timetableId)
            .filter { aggregate -> editingCourseId == null || aggregate.course.id != editingCourseId }

        val conflicts = mutableListOf<CourseConflict>()

        candidateSessions.forEach { candidate ->
            val candidateWeeks = resolveWeeks(
                weekType = candidate.weekType,
                startWeek = candidate.startWeek,
                endWeek = candidate.endWeek,
                customWeeks = candidate.customWeeks,
                totalWeeks = totalWeeks
            )
            if (candidateWeeks.isEmpty()) return@forEach

            existingCourses.forEach { existing ->
                existing.sessions.forEach { existingSession ->
                    if (candidate.dayOfWeek != existingSession.dayOfWeek) return@forEach
                    if (!isPeriodOverlap(candidate.startPeriod, candidate.endPeriod, existingSession.startPeriod, existingSession.endPeriod)) {
                        return@forEach
                    }

                    val existingWeeks = resolveWeeks(
                        weekType = existingSession.weekType,
                        startWeek = existingSession.startWeek,
                        endWeek = existingSession.endWeek,
                        customWeeks = existingSession.customWeeks,
                        totalWeeks = totalWeeks
                    )

                    val overlapWeeks = candidateWeeks.intersect(existingWeeks).sorted()
                    if (overlapWeeks.isNotEmpty()) {
                        conflicts += CourseConflict(
                            existingCourseId = existing.course.id,
                            existingCourseName = existing.course.name,
                            dayOfWeek = candidate.dayOfWeek,
                            overlapStartPeriod = maxOf(candidate.startPeriod, existingSession.startPeriod),
                            overlapEndPeriod = minOf(candidate.endPeriod, existingSession.endPeriod),
                            overlapWeeks = overlapWeeks
                        )
                    }
                }
            }
        }

        // Deduplicate conflicts to avoid repetitive warnings when multiple sessions map to same range.
        return conflicts.distinctBy {
            listOf(
                it.existingCourseId,
                it.dayOfWeek,
                it.overlapStartPeriod,
                it.overlapEndPeriod,
                it.overlapWeeks.joinToString(",")
            ).joinToString("#")
        }
    }

    private fun isPeriodOverlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean =
        aStart <= bEnd && bStart <= aEnd

    private fun resolveWeeks(
        weekType: WeekType,
        startWeek: Int?,
        endWeek: Int?,
        customWeeks: List<Int>,
        totalWeeks: Int
    ): Set<Int> {
        return when (weekType) {
            WeekType.ALL -> (1..totalWeeks).toSet()
            WeekType.RANGE -> {
                val start = startWeek ?: return emptySet()
                val end = endWeek ?: return emptySet()
                if (start > end) return emptySet()
                (start.coerceAtLeast(1)..end.coerceAtMost(totalWeeks)).toSet()
            }
            WeekType.CUSTOM -> customWeeks.filter { it in 1..totalWeeks }.toSet()
        }
    }
}



