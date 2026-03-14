package com.kurosu.sleepin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Grid picker for custom week selection.
 */
@Composable
fun WeekPicker(
    totalWeeks: Int,
    selectedWeeks: Set<Int>,
    onToggleWeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val weeks = (1..totalWeeks).toList()

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(weeks) { week ->
            FilterChip(
                selected = selectedWeeks.contains(week),
                onClick = { onToggleWeek(week) },
                label = { Text("W$week") }
            )
        }
    }
}

