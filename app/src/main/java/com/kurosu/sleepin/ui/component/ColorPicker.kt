package com.kurosu.sleepin.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Default course color palette used by the editor.
 *
 * The integer values are ARGB colors to keep parity with the persisted course color field.
 */
val DefaultCourseColors: List<Int> = listOf(
    0xFF4A90D9.toInt(),
    0xFF67C23A.toInt(),
    0xFFE6A23C.toInt(),
    0xFFF56C6C.toInt(),
    0xFF909399.toInt(),
    0xFF9B59B6.toInt(),
    0xFF1ABC9C.toInt(),
    0xFFE91E63.toInt(),
    0xFF3F51B5.toInt(),
    0xFF00BCD4.toInt(),
    0xFF8BC34A.toInt(),
    0xFFFF9800.toInt()
)

/**
 * Simple horizontal color picker for selecting a course display color.
 */
@Composable
fun ColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Int> = DefaultCourseColors
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { colorInt ->
            val color = Color(colorInt)
            val isSelected = colorInt == selectedColor
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorInt) }
            )
        }
    }
    Text(
        text = "Selected color: #${selectedColor.toUInt().toString(16).uppercase()}",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp)
    )
}


