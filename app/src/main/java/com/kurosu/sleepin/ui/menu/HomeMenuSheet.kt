package com.kurosu.sleepin.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                shape = RoundedCornerShape(14.dp),
                label = { Text("当前课程表") },
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

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "关联作息表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedSchedule,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "快速跳转周次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "第 $selectedWeek / $totalWeeks 周",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Slider(
                    value = selectedWeek.toFloat(),
                    onValueChange = { onWeekChange(it.toInt()) },
                    valueRange = 1f..totalWeeks.toFloat(),
                    steps = (totalWeeks - 2).coerceAtLeast(0)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
 * Compact tonal action tile used in the home menu.
 */
@Composable
private fun HomeMenuActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 74.dp),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )
        }
    }
}

