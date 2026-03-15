package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Visual block rendered inside one week-grid slot range.
 *
 * The component is intentionally compact because multiple cells can exist on the same screen,
 * and users still need to read at least course name + location quickly.
 */
@Composable
fun CourseCell(
    cell: HomeCourseCellUi,
    modifier: Modifier = Modifier,
    onClick: (HomeCourseCellUi) -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick(cell) },
        colors = CardDefaults.cardColors(containerColor = Color(cell.color))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = cell.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!cell.location.isNullOrBlank()) {
                Text(
                    text = cell.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

