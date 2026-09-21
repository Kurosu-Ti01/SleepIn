package com.kurosu.sleepin.domain.usecase.course

import com.kurosu.sleepin.domain.model.MAX_SUPPORTED_PERIOD_NUMBER
import com.kurosu.sleepin.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the input validation shared by the add and update course use cases.
 *
 * The rule worth pinning down is that period numbers are validated on their own merits and never
 * against a schedule: a course referencing a period the schedule has not configured yet must stay
 * editable, and the mismatch is reported after saving instead.
 */
class CourseInputValidationTest {

    @Test
    fun validateCourseInput_acceptsSessions_whenPeriodsAreNotConfiguredByAnySchedule() {
        val validation = validateCourseInput(
            name = "高等数学",
            totalWeeks = 18,
            sessions = listOf(draft(startPeriod = 11, endPeriod = 13))
        )

        assertEquals(CourseInputValidation.Valid, validation)
    }

    @Test
    fun validateCourseInput_acceptsInput_whenEveryRuleHolds() {
        val validation = validateCourseInput(
            name = "高等数学",
            totalWeeks = 18,
            sessions = listOf(
                draft(startPeriod = 1, endPeriod = 2, weekType = WeekType.RANGE, startWeek = 1, endWeek = 16),
                draft(startPeriod = 3, endPeriod = 4, weekType = WeekType.CUSTOM, customWeeks = listOf(1, 3, 5))
            )
        )

        assertEquals(CourseInputValidation.Valid, validation)
    }

    @Test
    fun validateCourseInput_rejectsBlankName() {
        val validation = validateCourseInput("  ", 18, listOf(draft()))

        assertEquals(CourseInputValidation.Invalid("请填写课程名称"), validation)
    }

    @Test
    fun validateCourseInput_rejectsTotalWeeksOutsideSupportedRange() {
        val validation = validateCourseInput("高等数学", 40, listOf(draft()))

        assertTrue(validation is CourseInputValidation.Invalid)
    }

    @Test
    fun validateCourseInput_rejectsEmptySessionList() {
        val validation = validateCourseInput("高等数学", 18, emptyList())

        assertTrue(validation is CourseInputValidation.Invalid)
    }

    @Test
    fun validateCourseInput_rejectsDayOutsideOneToSeven() {
        val validation = validateCourseInput("高等数学", 18, listOf(draft(dayOfWeek = 8)))

        assertTrue((validation as CourseInputValidation.Invalid).message.contains("星期"))
    }

    @Test
    fun validateCourseInput_rejectsNonPositivePeriod() {
        val validation = validateCourseInput("高等数学", 18, listOf(draft(startPeriod = 0, endPeriod = 2)))

        assertTrue((validation as CourseInputValidation.Invalid).message.contains("大于 0"))
    }

    @Test
    fun validateCourseInput_rejectsInvertedPeriodRange() {
        val validation = validateCourseInput("高等数学", 18, listOf(draft(startPeriod = 5, endPeriod = 3)))

        assertTrue((validation as CourseInputValidation.Invalid).message.contains("结束节次"))
    }

    @Test
    fun validateCourseInput_rejectsPeriodAboveTheStructuralCeiling() {
        val validation = validateCourseInput(
            name = "高等数学",
            totalWeeks = 18,
            sessions = listOf(draft(startPeriod = 1, endPeriod = MAX_SUPPORTED_PERIOD_NUMBER + 1))
        )

        val message = (validation as CourseInputValidation.Invalid).message
        assertTrue(message.contains("$MAX_SUPPORTED_PERIOD_NUMBER"))
    }

    @Test
    fun validateCourseInput_rejectsRangeWeeks_whenBoundsAreMissing() {
        val missingStart = validateCourseInput(
            "高等数学",
            18,
            listOf(draft(weekType = WeekType.RANGE, startWeek = null, endWeek = 16))
        )
        val missingEnd = validateCourseInput(
            "高等数学",
            18,
            listOf(draft(weekType = WeekType.RANGE, startWeek = 1, endWeek = null))
        )

        assertTrue(missingStart is CourseInputValidation.Invalid)
        assertTrue(missingEnd is CourseInputValidation.Invalid)
    }

    @Test
    fun validateCourseInput_rejectsCustomWeeks_whenEmptyOrOutOfRange() {
        val empty = validateCourseInput(
            "高等数学",
            18,
            listOf(draft(weekType = WeekType.CUSTOM, customWeeks = emptyList()))
        )
        val outOfRange = validateCourseInput(
            "高等数学",
            18,
            listOf(draft(weekType = WeekType.CUSTOM, customWeeks = listOf(1, 19)))
        )

        assertTrue(empty is CourseInputValidation.Invalid)
        assertTrue(outOfRange is CourseInputValidation.Invalid)
    }

    @Test
    fun validateCourseInput_namesTheOffendingSession() {
        val validation = validateCourseInput(
            name = "高等数学",
            totalWeeks = 18,
            sessions = listOf(draft(), draft(dayOfWeek = 9))
        )

        assertTrue((validation as CourseInputValidation.Invalid).message.contains("第 2 个时间段"))
    }

    private fun draft(
        startPeriod: Int = 1,
        endPeriod: Int = 2,
        dayOfWeek: Int = 1,
        weekType: WeekType = WeekType.ALL,
        startWeek: Int? = null,
        endWeek: Int? = null,
        customWeeks: List<Int> = emptyList()
    ) = CourseSessionDraft(
        id = 0L,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        location = null,
        weekType = weekType,
        startWeek = startWeek,
        endWeek = endWeek,
        customWeeks = customWeeks
    )
}
