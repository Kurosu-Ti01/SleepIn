package com.kurosu.sleepin.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.kurosu.sleepin.domain.model.CourseColorPalette

/**
 * Wrapping color picker for selecting a course display color.
 *
 * The palette is owned by the domain layer ([CourseColorPalette]) because the chosen value is
 * persisted as-is; the picker only renders it.
 *
 * We intentionally use [WrapFlowLayout] instead of a single-line row so the palette can reflow to
 * multiple lines on narrow screens and under larger font/display scaling without clipping.
 */
@Composable
fun ColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Int> = CourseColorPalette
) {
    Column(modifier = modifier.fillMaxWidth()) {
        WrapFlowLayout(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 8.dp,
            verticalSpacing = 8.dp
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
}


