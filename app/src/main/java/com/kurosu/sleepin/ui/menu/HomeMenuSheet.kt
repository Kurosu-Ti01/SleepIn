package com.kurosu.sleepin.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Bottom sheet used by Home screen. The content mirrors the planned two-level menu:
 * top section for quick context switching, bottom section for module navigation.
 */
@Composable
fun HomeMenuSheet(
    selectedTimetable: String,
    selectedSchedule: String,
    selectedWeek: Int,
    totalWeeks: Int,
    onWeekChange: (Int) -> Unit,
    onTimetableListClick: () -> Unit,
    onScheduleListClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "课程表: $selectedTimetable", style = MaterialTheme.typography.titleMedium)
        Text(text = "作息表: $selectedSchedule", style = MaterialTheme.typography.titleMedium)

        Text(text = "第 $selectedWeek 周 / 共 $totalWeeks 周")
        Slider(
            value = selectedWeek.toFloat(),
            onValueChange = { onWeekChange(it.toInt()) },
            valueRange = 1f..totalWeeks.toFloat(),
            steps = (totalWeeks - 2).coerceAtLeast(0)
        )

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(modifier = Modifier.weight(1f), onClick = onTimetableListClick) {
                Text("课程表")
            }
            Button(modifier = Modifier.weight(1f), onClick = onScheduleListClick) {
                Text("作息表")
            }
            Button(modifier = Modifier.weight(1f), onClick = onSettingsClick) {
                Text("设置")
            }
        }
    }
}

