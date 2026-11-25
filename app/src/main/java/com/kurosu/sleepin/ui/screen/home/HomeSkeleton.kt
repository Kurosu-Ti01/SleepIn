package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Day/period footprints of the placeholder course blocks.
 *
 * The values are fixed rather than random so the skeleton looks identical on every recomposition; a
 * random layout would make the placeholder flicker while it is on screen. Each entry is
 * `dayIndex (0 = Monday) to periodRowRange (0-based)`.
 */
private val skeletonCourseBlocks = listOf(
    1 to 0..1,
    3 to 2..2,
    5 to 0..1,
    0 to 4..5,
    2 to 6..7,
    4 to 3..4,
    6 to 5..6,
    3 to 6..7
)

/**
 * Neutral rounded bar used by loading placeholders in the home screen.
 *
 * @param width Bar width; pass the width of the real content it stands in for.
 * @param height Bar height. The corner radius is derived from it so the bar always reads as a pill.
 */
@Composable
fun SkeletonBar(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2f))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    )
}

/**
 * Placeholder timetable shown while the first real `HomeUiState` is still loading.
 *
 * A cold launch cannot draw courses on the first frame: home data comes from Room and DataStore
 * flows, so the state starts as an `isLoading` placeholder. This skeleton reuses the real week view's
 * geometry (same period gutter width, same weekday header, same row height), so switching to the
 * real timetable changes the contents without moving the layout. The caller cross-fades between the
 * two states; nothing here animates on its own.
 *
 * No data is read from the ViewModel, which means the placeholder cannot show stale week or course
 * information; every placeholder element is derived from theme colors only.
 *
 * @param rowHeight Height of one period row. Pass the user's configured course cell height so the
 *   placeholder rows land on the same y positions as the real grid.
 */
@Composable
fun HomeSkeleton(
    rowHeight: Dp,
    modifier: Modifier = Modifier
) {
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val blockColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)

    Column(modifier = modifier.fillMaxSize()) {
        // Weekday labels are static, so the placeholder can show the real header; only the dates are
        // missing, which WeekHeader renders as a "--" placeholder.
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(timelineColumnWidth))
            WeekHeader(
                dates = emptyList(),
                todayColumnIndex = null,
                modifier = Modifier.weight(1f)
            )
        }

        // The row count comes from the space actually available, so the placeholder always fills the
        // viewport: no blank strip at the bottom, and no scrolling while there is nothing to scroll.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val rowCount = (maxHeight.value / rowHeight.value).toInt().coerceAtLeast(1)

            Row(modifier = Modifier.fillMaxSize()) {
                // Period-number gutter: one short bar per row stands in for the missing period label.
                Column(modifier = Modifier.width(timelineColumnWidth)) {
                    repeat(rowCount) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight),
                            contentAlignment = Alignment.Center
                        ) {
                            SkeletonBar(width = 12.dp, height = 6.dp)
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(rowHeight * rowCount)
                ) {
                    val rowHeightPx = rowHeight.toPx()
                    val dayWidthPx = size.width / WEEK_COLUMN_COUNT
                    val blockInsetPx = 2.dp.toPx()

                    // Same grid line layout as WeekGridContent: vertical separators between days and one
                    // horizontal line per row boundary, so lines stay put when real data replaces them.
                    for (column in 1 until WEEK_COLUMN_COUNT) {
                        val x = column * dayWidthPx
                        drawLine(
                            color = gridLineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height)
                        )
                    }
                    for (row in 0..rowCount) {
                        val y = row * rowHeightPx
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y)
                        )
                    }

                    skeletonCourseBlocks.forEach { (dayIndex, rows) ->
                        val firstRow = rows.first
                        // Blocks that start below the visible viewport are skipped entirely; partially
                        // visible ones are clipped to the last drawn row.
                        if (firstRow >= rowCount) return@forEach
                        val lastRow = rows.last.coerceAtMost(rowCount - 1)
                        drawRoundRect(
                            color = blockColor,
                            topLeft = Offset(
                                x = dayIndex * dayWidthPx + blockInsetPx,
                                y = firstRow * rowHeightPx + blockInsetPx
                            ),
                            size = Size(
                                width = dayWidthPx - blockInsetPx * 2,
                                height = (lastRow - firstRow + 1) * rowHeightPx - blockInsetPx * 2
                            ),
                            cornerRadius = CornerRadius(8.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
