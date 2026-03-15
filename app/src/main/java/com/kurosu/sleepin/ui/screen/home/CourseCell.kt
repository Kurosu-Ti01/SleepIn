package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
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
    val primaryPart = buildString {
        append(cell.name)
        if (!cell.location.isNullOrBlank()) {
            append("@")
            append(cell.location)
        }
    }
    val teacherPart = cell.teacher?.takeIf { it.isNotBlank() }

    Card(
        modifier = modifier.clickable { onClick(cell) },
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(cell.color))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Keep inner padding minimal so narrow columns can still show several CJK characters.
                .padding(horizontal = 2.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(primaryPart)
                    }
                    if (!teacherPart.isNullOrBlank()) {
                        append("\n") // Force teacher name to a new line to avoid truncation of course name/location.
                        append(teacherPart)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

