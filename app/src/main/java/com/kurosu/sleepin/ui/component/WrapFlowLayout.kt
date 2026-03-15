package com.kurosu.sleepin.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max

/**
 * Lightweight wrapping layout used as a runtime-safe replacement for FlowRow.
 *
 * It places children left-to-right and moves them to a new line when the current line
 * does not have enough remaining width.
 */
@Composable
fun WrapFlowLayout(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val horizontalSpacingPx = with(density) { horizontalSpacing.roundToPx() }
    val verticalSpacingPx = with(density) { verticalSpacing.roundToPx() }

    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        val maxWidth = if (constraints.maxWidth == Constraints.Infinity) {
            placeables.maxOfOrNull { it.width } ?: 0
        } else {
            constraints.maxWidth
        }

        val xPositions = IntArray(placeables.size)
        val yPositions = IntArray(placeables.size)

        var currentX = 0
        var currentY = 0
        var rowHeight = 0
        var contentWidth = 0

        placeables.forEachIndexed { index, placeable ->
            if (currentX > 0 && currentX + placeable.width > maxWidth) {
                currentX = 0
                currentY += rowHeight + verticalSpacingPx
                rowHeight = 0
            }

            xPositions[index] = currentX
            yPositions[index] = currentY

            currentX += placeable.width + horizontalSpacingPx
            rowHeight = max(rowHeight, placeable.height)
            contentWidth = max(contentWidth, currentX - horizontalSpacingPx)
        }

        val contentHeight = if (placeables.isEmpty()) 0 else currentY + rowHeight
        val layoutWidth = if (constraints.maxWidth == Constraints.Infinity) contentWidth else constraints.maxWidth

        layout(
            width = layoutWidth.coerceAtLeast(constraints.minWidth),
            height = contentHeight.coerceAtLeast(constraints.minHeight)
        ) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = xPositions[index], y = yPositions[index])
            }
        }
    }
}

