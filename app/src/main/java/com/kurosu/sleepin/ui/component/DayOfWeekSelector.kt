package com.kurosu.sleepin.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val DayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/**
 * Weekday selector for choosing weekday in range [1..7].
 *
 * We use wrapping layout so chips keep natural width and do not get squeezed on small screens.
 */
@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
fun DayOfWeekSelector(
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = 6.dp
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Keep weekday chips on an aligned 4-column grid (Mon-Thu, Fri-Sun) with equal widths.
        val columns = 4
        val spacingPx = with(density) { spacing.roundToPx() }
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val chipWidthPx = ((maxWidthPx - spacingPx * (columns - 1)) / columns).coerceAtLeast(1)
        val chipWidth = with(density) { chipWidthPx.toDp() }

        WrapFlowLayout(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = spacing,
            verticalSpacing = spacing
        ) {
            DayLabels.forEachIndexed { index, label ->
                val day = index + 1
                Box(modifier = Modifier.width(chipWidth)) {
                    FilterChip(
                        modifier = Modifier.fillMaxWidth(),
                        selected = selectedDay == day,
                        onClick = { onDaySelected(day) },
                        label = {
                            Text(
                                text = label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }
            }
        }
    }
}

