package com.kurosu.sleepin.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wrapping chip picker for custom week selection.
 *
 * We avoid fixed-column grids here because chip labels and screen widths vary.
 * The custom wrapping layout keeps behavior stable across Compose runtime versions.
 */
@Composable
fun WeekPicker(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    onToggleWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val weeks = (1..totalWeeks).toList()
    val spacing = 8.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Keep all chips the same width so each row aligns to the same column grid.
        val columns = if (maxWidth < 300.dp) 3 else 4
        val chipWidth = (maxWidth - spacing * (columns - 1)) / columns

        WrapFlowLayout(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = spacing,
            verticalSpacing = spacing
        ) {
            weeks.forEach { week ->
                Box(modifier = Modifier.width(chipWidth)) {
                    FilterChip(
                        modifier = Modifier.fillMaxWidth(),
                        selected = selectedWeeks.contains(week),
                        onClick = { onToggleWeek(week) },
                        label = { Text("W$week") }
                    )
                }
            }
        }
    }
}

