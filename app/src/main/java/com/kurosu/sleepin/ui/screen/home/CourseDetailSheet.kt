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

        if (!course.location.isNullOrBlank()) {
            Text(text = "Location: ${course.location}", style = MaterialTheme.typography.bodyLarge)
        }

        if (!course.teacher.isNullOrBlank()) {
            Text(text = "Teacher: ${course.teacher}", style = MaterialTheme.typography.bodyLarge)
        }

        Text(text = "Weeks: ${course.weekDescription}", style = MaterialTheme.typography.bodyMedium)

        if (!course.note.isNullOrBlank()) {
            Text(text = "Note: ${course.note}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

