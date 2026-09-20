package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

private val sidebarTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Left-side fixed timeline showing period number and start/end time for each row.
 */
@Composable
fun TimelineSidebar(
    periods: List<HomePeriodUi>,
    rowHeight: Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        periods.forEach { period ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${period.periodNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )

                val timeTextStyle = MaterialTheme.typography.labelSmall.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                )
                val startTime = period.startTime
                val endTime = period.endTime
                if (startTime == null || endTime == null) {
                    // The schedule does not define this period, so the row has no class time to show.
                    // Saying so is better than leaving the two time lines blank, which would read as a
                    // rendering problem rather than as missing configuration.
                    Text(
                        text = "未配置",
                        style = timeTextStyle,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = startTime.format(sidebarTimeFormatter),
                        style = timeTextStyle,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = endTime.format(sidebarTimeFormatter),
                        style = timeTextStyle,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

