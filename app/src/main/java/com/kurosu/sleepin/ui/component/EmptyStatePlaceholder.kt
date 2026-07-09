package com.kurosu.sleepin.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Shared empty-state placeholder for list screens (timetables, schedules, courses).
 *
 * Renders a muted icon with a title and an action hint, centered both vertically and
 * horizontally inside the space given by [modifier]. Callers typically pass
 * `Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)` so the placeholder
 * occupies the whole scaffold body.
 *
 * @param icon large decorative icon shown above the text; not announced by TalkBack
 *        because the title already carries the meaning
 * @param title short state description, e.g. "暂无课程表"
 * @param subtitle action hint guiding users to the FAB, e.g. "点击右下角按钮创建"
 */
@Composable
fun EmptyStatePlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
