package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.MAX_SUPPORTED_PERIOD_NUMBER
import com.kurosu.sleepin.domain.model.WeekType

/**
 * Outcome of validating the course editor's input.
 */
sealed interface CourseInputValidation {
    data object Valid : CourseInputValidation
    data class Invalid(val message: String) : CourseInputValidation
}

/**
 * Validates the input shared by the add and update course use cases.
 *
 * Only rules the input can be judged on by itself are enforced here. Whether a referenced period
 * exists in the timetable's schedule is deliberately *not* one of them: a course may legitimately use
 * a period the schedule has not configured yet, and refusing the save would make such a course
 * impossible to edit after being imported. Callers report that as a non-blocking notice instead, and
 * the schedule editor is where the user resolves it.
 *
 * Extracted rather than repeated in both use cases so the two cannot drift apart on rules the editor
 * depends on.
 */
fun validateCourseInput(
    name: String,
    totalWeeks: Int,
    sessions: List<CourseSessionDraft>
): CourseInputValidation {
    if (name.isBlank()) return CourseInputValidation.Invalid("请填写课程名称")
    if (totalWeeks !in 1..30) return CourseInputValidation.Invalid("总周数应在 1 到 30 之间")
    if (sessions.isEmpty()) return CourseInputValidation.Invalid("请至少添加一个上课时间段")

    sessions.forEachIndexed { index, session ->
        val position = index + 1
        if (session.dayOfWeek !in 1..7) {
            return CourseInputValidation.Invalid("第 $position 个时间段：星期应在 1-7 之间")
        }
        if (session.startPeriod < 1 || session.endPeriod < 1) {
            return CourseInputValidation.Invalid("第 $position 个时间段：节次必须大于 0")
        }
        if (session.startPeriod > session.endPeriod) {
            return CourseInputValidation.Invalid("第 $position 个时间段：结束节次不能小于开始节次")
        }
        // Structural ceiling only. The week grid renders one row per period number, so an unbounded
        // value would ask the home screen for an enormous grid; see MAX_SUPPORTED_PERIOD_NUMBER.
        if (session.endPeriod > MAX_SUPPORTED_PERIOD_NUMBER) {
            return CourseInputValidation.Invalid(
                "第 $position 个时间段：节次不能超过 $MAX_SUPPORTED_PERIOD_NUMBER"
            )
        }
        when (session.weekType) {
            WeekType.ALL -> Unit
            WeekType.RANGE -> {
                val start = session.startWeek
                    ?: return CourseInputValidation.Invalid("第 $position 个时间段：请填写起始周")
                val end = session.endWeek
                    ?: return CourseInputValidation.Invalid("第 $position 个时间段：请填写结束周")
                if (start !in 1..totalWeeks || end !in 1..totalWeeks || start > end) {
                    return CourseInputValidation.Invalid(
                        "第 $position 个时间段：周次应在 1-$totalWeeks 之间，且起始周不大于结束周"
                    )
                }
            }
            WeekType.CUSTOM -> {
                if (session.customWeeks.isEmpty()) {
                    return CourseInputValidation.Invalid("第 $position 个时间段：请至少选择一个周次")
                }
                if (session.customWeeks.any { it !in 1..totalWeeks }) {
                    return CourseInputValidation.Invalid("第 $position 个时间段：周次应在 1-$totalWeeks 之间")
                }
            }
        }
    }

    return CourseInputValidation.Valid
}
