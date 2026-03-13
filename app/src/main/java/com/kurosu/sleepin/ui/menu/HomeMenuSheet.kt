package com.kurosu.sleepin.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kurosu.sleepin.ui.screen.home.HomeTimetableOption

/**
 * Bottom sheet used by Home screen. The content mirrors the planned two-level menu:
 * top section for quick context switching, bottom section for module navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(
    selectedTimetable: String,
    selectedSchedule: String,
    timetableOptions: List<HomeTimetableOption>,
    selectedWeek: Int,
    totalWeeks: Int,
    onTimetableSelected: (Long) -> Unit,
    onWeekChange: (Int) -> Unit,
    onTimetableListClick: () -> Unit,
    onScheduleListClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var timetableExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = timetableExpanded,
            onExpandedChange = { timetableExpanded = !timetableExpanded }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
                value = selectedTimetable,
                onValueChange = {},
                readOnly = true,
                label = { Text("课程表") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = timetableExpanded)
                }
            )

            DropdownMenu(
                expanded = timetableExpanded,
                onDismissRequest = { timetableExpanded = false }
            ) {
                timetableOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(if (option.isActive) "${option.name} (当前)" else option.name)
                        },
                        onClick = {
                            onTimetableSelected(option.id)
                            timetableExpanded = false
                        }
                    )
                }
            }
        }

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

