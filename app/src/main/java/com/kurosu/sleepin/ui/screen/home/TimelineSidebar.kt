package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${period.periodNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${period.startTime.format(sidebarTimeFormatter)}-${period.endTime.format(sidebarTimeFormatter)}",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

