package com.kurosu.sleepin.ui.screen.home

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val WEEK_COLUMN_COUNT = 7
private const val SWIPE_CHANGE_THRESHOLD_PX = 80f
private val defaultRowHeight = 68.dp
private val timelineColumnWidth = 34.dp

/**
 * Full week-view container that composes header, sidebar, grid background, and course cells.
 */
@Composable
fun WeekGridView(
    dates: List<java.time.LocalDate>,
    periods: List<HomePeriodUi>,
    courses: List<HomeCourseCellUi>,
    todayColumnIndex: Int?,
    currentTimePeriodOffset: Float?,
    onCourseClick: (HomeCourseCellUi) -> Unit,
    onSwipeToPreviousWeek: () -> Unit,
    onSwipeToNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = defaultRowHeight
) {
    val verticalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            // Horizontal drag gesture offers a natural week-switching interaction.
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag > SWIPE_CHANGE_THRESHOLD_PX -> onSwipeToPreviousWeek()
                            totalDrag < -SWIPE_CHANGE_THRESHOLD_PX -> onSwipeToNextWeek()
                        }
                    }
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(timelineColumnWidth))
            WeekHeader(
                dates = dates,
                todayColumnIndex = todayColumnIndex,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
        ) {
            TimelineSidebar(
                periods = periods,
                rowHeight = rowHeight,
                modifier = Modifier.width(timelineColumnWidth)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                WeekGridContent(
                    periods = periods,
                    courses = courses,
                    todayColumnIndex = todayColumnIndex,
                    currentTimePeriodOffset = currentTimePeriodOffset,
                    rowHeight = rowHeight,
                    onCourseClick = onCourseClick
                )
            }
        }
    }
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
private fun WeekGridContent(
    periods: List<HomePeriodUi>,
    courses: List<HomeCourseCellUi>,
    todayColumnIndex: Int?,
    currentTimePeriodOffset: Float?,
    rowHeight: Dp,
    onCourseClick: (HomeCourseCellUi) -> Unit
) {
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val todayColumnColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val timeIndicatorColor = Color(0xFFE53935)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight * periods.size)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val density = LocalDensity.current
        val dayWidth = maxWidth / WEEK_COLUMN_COUNT

        // Grid background and dynamic overlays are painted in one canvas layer for efficiency.
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val dayWidthPx = size.width / WEEK_COLUMN_COUNT
            val rowHeightPx = with(density) { rowHeight.toPx() }

            // Highlight today's column when selected week is the actual current week.
            todayColumnIndex?.takeIf { it in 0 until WEEK_COLUMN_COUNT }?.let { todayIndex ->
                drawRect(
                    color = todayColumnColor,
                    topLeft = Offset(x = todayIndex * dayWidthPx, y = 0f),
                    size = androidx.compose.ui.geometry.Size(width = dayWidthPx, height = size.height)
                )
            }

            for (column in 1 until WEEK_COLUMN_COUNT) {
                val x = column * dayWidthPx
                drawLine(
                    color = gridLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height)
                )
            }

            for (row in 0..periods.size) {
                val y = row * rowHeightPx
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y)
                )
            }

            // Draw current-time indicator line only when the state can locate it in the visible grid.
            if (currentTimePeriodOffset != null) {
                val y = currentTimePeriodOffset * rowHeightPx
                drawLine(
                    color = timeIndicatorColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 4f
                )
            }
        }

        // Place each course block by converting day/period coordinates to pixel offsets.
        courses.forEach { course ->
            if (course.dayOfWeek !in 1..7) return@forEach
            if (course.startPeriod <= 0 || course.endPeriod < course.startPeriod) return@forEach

            val dayIndex = course.dayOfWeek - 1
            val startRowIndex = course.startPeriod - 1
            val spanRows = (course.endPeriod - course.startPeriod + 1)

            CourseCell(
                cell = course,
                onClick = onCourseClick,
                modifier = Modifier
                    .padding(2.dp)
                    .width(dayWidth - 4.dp)
                    .height((rowHeight * spanRows) - 4.dp)
                    .offset(
                        x = dayWidth * dayIndex,
                        y = rowHeight * startRowIndex
                    )
            )
        }
    }
}


