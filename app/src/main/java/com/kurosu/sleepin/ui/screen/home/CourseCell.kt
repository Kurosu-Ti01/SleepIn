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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Value of the user's course-text size setting that the grid was designed around, in sp.
 *
 * It equals Material's `labelMedium` size, so scaling from it keeps the default rendering
 * pixel-identical to before the setting existed.
 */
internal const val COURSE_CELL_DEFAULT_FONT_SIZE_SP = 12

/** Line-height ratio used when a theme style carries no explicit line height. */
private const val FALLBACK_LINE_HEIGHT_RATIO = 1.2f

/**
 * Returns this text style scaled by [scale], keeping its own font-size to line-height ratio.
 *
 * Scaling both keeps the course-name line and the teacher line from overlapping when the user picks
 * a larger size, and from looking sparse when they pick a smaller one.
 */
private fun TextStyle.scaledBy(scale: Float): TextStyle {
    val baseFontSizeSp = fontSize.value.takeIf { it.isFinite() && it > 0f }
        ?: COURSE_CELL_DEFAULT_FONT_SIZE_SP.toFloat()
    val baseLineHeightSp = lineHeight.value.takeIf { it.isFinite() && it > 0f }
        ?: (baseFontSizeSp * FALLBACK_LINE_HEIGHT_RATIO)
    return copy(
        fontSize = (baseFontSizeSp * scale).sp,
        lineHeight = (baseLineHeightSp * scale).sp
    )
}

/**
 * Visual block rendered inside one week-grid slot range.
 *
 * The component is intentionally compact because multiple cells can exist on the same screen,
 * and users still need to read at least course name + location quickly.
 *
 * @param fontSizeSp font size of the course text, driven by the "课程卡片字号" setting. Every text
 *   in the block scales from it together, so the "非本周" badge stays one step below the course name
 *   and the two text lines keep their line-height ratio at any size.
 */
@Composable
fun CourseCell(
    cell: HomeCourseCellUi,
    modifier: Modifier = Modifier,
    fontSizeSp: Int = COURSE_CELL_DEFAULT_FONT_SIZE_SP,
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
    val containerColor = if (cell.isCurrentWeek) {
        Color(cell.color)
    } else {
        Color(cell.color).copy(alpha = 0.45f)
    }
    // Read once per recomposition so both texts share the same base styles from the current theme.
    val textScale = fontSizeSp / COURSE_CELL_DEFAULT_FONT_SIZE_SP.toFloat()
    val titleStyle = MaterialTheme.typography.labelMedium.scaledBy(textScale)
    val badgeStyle = MaterialTheme.typography.labelSmall.scaledBy(textScale)

    Card(
        onClick = { onClick(cell) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (cell.isCurrentWeek) 1.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Keep inner padding minimal so narrow columns can still show several CJK characters.
                .padding(horizontal = 3.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!cell.isCurrentWeek) {
                Text(
                    text = "非本周",
                    style = badgeStyle,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                style = titleStyle,
                color = Color.White,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

