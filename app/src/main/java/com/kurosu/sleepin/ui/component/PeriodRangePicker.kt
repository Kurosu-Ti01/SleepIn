package com.kurosu.sleepin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Two dropdown selectors that edit a class-period range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodRangePicker(
    startPeriod: Int,
    endPeriod: Int,
    maxPeriod: Int,
    onRangeChange: (startPeriod: Int, endPeriod: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = (1..maxPeriod).toList()
    var startExpanded by remember { mutableStateOf(false) }
    var endExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = startExpanded,
            onExpandedChange = { startExpanded = !startExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = startPeriod.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Start") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startExpanded) },
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            DropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                options.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.toString()) },
                        onClick = {
                            onRangeChange(period, maxOf(period, endPeriod))
                            startExpanded = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = endExpanded,
            onExpandedChange = { endExpanded = !endExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = endPeriod.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("End") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endExpanded) },
                modifier = Modifier
                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            DropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                options.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.toString()) },
                        onClick = {
                            onRangeChange(minOf(startPeriod, period), period)
                            endExpanded = false
                        }
                    )
                }
            }
        }
    }
}

