package com.kurosu.sleepin.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
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
            HomeMenuActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.ViewWeek,
                label = "课程表",
                onClick = onTimetableListClick
            )
            HomeMenuActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Schedule,
                label = "作息表",
                onClick = onScheduleListClick
            )
            HomeMenuActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Settings,
                label = "设置",
                onClick = onSettingsClick
            )
        }
    }
}

/**
 * Renders a compact home menu action button with icon on top and label below.
 */
/**
 * Compact action tile used in the home menu.
 *
 * Layout: icon on top and label below. Visual style is an outlined square tile
 * with a slightly rounded corner to better frame vertical content.
 *
 * @param modifier Modifier applied to the button (e.g. weight for even columns).
 * @param icon Icon to show above the label.
 * @param label Short text label displayed below the icon.
 * @param onClick Click handler for the tile.
 */
@Composable
private fun HomeMenuActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        modifier = modifier.heightIn(min = 64.dp),
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        // Use a thicker primary-colored border to increase emphasis for the tile
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

