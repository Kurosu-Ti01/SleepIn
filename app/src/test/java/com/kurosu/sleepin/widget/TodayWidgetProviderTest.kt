package com.kurosu.sleepin.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for Today widget layout decisions and empty-state resolution.
 */
class TodayWidgetProviderTest {

    @Test
    fun calculateLayoutSpec_shortHeight_switchesToTwoColumnsWhenSingleColumnCannotFitAll() {
        val spec = calculateTodayWidgetLayoutSpec(
            heightDp = 130f,
            hasSemesterStatus = false,
            remainingCourseCount = 5
        )

        assertEquals(2, spec.columns)
        assertEquals(1, spec.rowsPerColumn)
        assertEquals(2, spec.maxVisibleItems)
    }

    @Test
    fun calculateLayoutSpec_tallHeight_keepsSingleColumnWhenAllCoursesFit() {
        val spec = calculateTodayWidgetLayoutSpec(
            heightDp = 240f,
            hasSemesterStatus = false,
            remainingCourseCount = 3
        )

        assertEquals(1, spec.columns)
        assertEquals(3, spec.rowsPerColumn)
        assertEquals(3, spec.maxVisibleItems)
    }

    @Test
    fun calculateLayoutSpec_tallHeight_canShowMoreThanTwoCells() {
        val spec = calculateTodayWidgetLayoutSpec(
            heightDp = 240f,
            hasSemesterStatus = false,
            remainingCourseCount = 8
        )

        assertEquals(2, spec.columns)
        assertEquals(3, spec.rowsPerColumn)
        assertEquals(6, spec.maxVisibleItems)
    }

    @Test
    fun calculateLayoutSpec_tinyHeight_rendersNoPartialRow() {
        val spec = calculateTodayWidgetLayoutSpec(
            heightDp = 90f,
            hasSemesterStatus = false,
            remainingCourseCount = 5
        )

        assertEquals(1, spec.columns)
        assertEquals(0, spec.rowsPerColumn)
        assertEquals(0, spec.maxVisibleItems)
    }

    @Test
    fun calculateLayoutSpec_boundaryHeight_prefersSingleColumnWhenAllCanFit() {
        val spec = calculateTodayWidgetLayoutSpec(
            heightDp = 361.6f,
            hasSemesterStatus = false,
            remainingCourseCount = 5
        )

        assertEquals(1, spec.columns)
        assertEquals(5, spec.rowsPerColumn)
        assertEquals(5, spec.maxVisibleItems)
    }

    @Test
    fun resolveRenderPlan_singleColumnButNotEnoughRows_fallsBackToTwoColumns() {
        val renderPlan = resolveTodayCourseRenderPlan(
            courseCount = 7,
            layoutSpec = sampleLayoutSpec(columns = 1, rowsPerColumn = 4)
        )

        assertEquals(2, renderPlan.columns)
        assertEquals(7, renderPlan.maxVisibleItems)
    }

    @Test
    fun resolveRenderPlan_singleColumnEnoughRows_keepsSingleColumn() {
        val renderPlan = resolveTodayCourseRenderPlan(
            courseCount = 7,
            layoutSpec = sampleLayoutSpec(columns = 1, rowsPerColumn = 7)
        )

        assertEquals(1, renderPlan.columns)
        assertEquals(7, renderPlan.maxVisibleItems)
    }

    @Test
    fun resolveTodayEmptyState_noCoursesToday_returnsNoCourse() {
        val state = resolveTodayEmptyState(
            todayCourses = emptyList(),
            remainingCourses = emptyList()
        )

        assertEquals(TodayEmptyState.NO_COURSE, state)
    }

    @Test
    fun resolveTodayEmptyState_hasCoursesButNoneRemaining_returnsFinished() {
        val state = resolveTodayEmptyState(
            todayCourses = listOf(sampleSession()),
            remainingCourses = emptyList()
        )

        assertEquals(TodayEmptyState.FINISHED, state)
    }

    private fun sampleSession(): WidgetSessionItem = WidgetSessionItem(
        courseName = "线性代数",
        courseColor = 0xFF4A90D9.toInt(),
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        location = "A101",
        startTimeLabel = "08:00",
        endTimeLabel = "08:45"
    )

    private fun sampleLayoutSpec(columns: Int, rowsPerColumn: Int): TodayWidgetLayoutSpec {
        return TodayWidgetLayoutSpec(
            rowMetrics = TodayRowMetrics(
                fixedRowHeightDp = 54f,
                horizontalPaddingDp = 12f,
                verticalPaddingDp = 6f,
                stripeHeightDp = 32f,
                cornerRadiusDp = 12f,
                rowSpacingDp = 6f
            ),
            listTopSpacingDp = 10f,
            columns = columns,
            rowsPerColumn = rowsPerColumn
        )
    }
}

