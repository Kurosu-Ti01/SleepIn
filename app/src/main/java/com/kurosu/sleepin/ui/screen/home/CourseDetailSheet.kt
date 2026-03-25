package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val detailDayLabels = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/**
 * Read-only detail panel displayed when user taps one course cell in the week view.
 */
@Composable
fun CourseDetailSheet(
    course: HomeCourseCellUi,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = course.name, style = MaterialTheme.typography.headlineSmall)

        Text(
            text = "${detailDayLabels.getOrElse(course.dayOfWeek - 1) { "Unknown" }} P${course.startPeriod}-P${course.endPeriod}",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = if (course.isCurrentWeek) "本周课程" else "非本周课程",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!course.location.isNullOrBlank()) {
            Text(text = "地点: ${course.location}", style = MaterialTheme.typography.bodyLarge)
        }

        if (!course.teacher.isNullOrBlank()) {
            Text(text = "教室: ${course.teacher}", style = MaterialTheme.typography.bodyLarge)
        }


        Text(text = "上课周数: ${course.weekDescription}", style = MaterialTheme.typography.bodyMedium)

        if (!course.note.isNullOrBlank()) {
            Text(text = "备注: ${course.note}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

