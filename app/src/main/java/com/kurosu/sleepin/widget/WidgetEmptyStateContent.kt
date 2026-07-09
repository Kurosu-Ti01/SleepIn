package com.kurosu.sleepin.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Shared "no active timetable" empty state used by both the Today and Week widgets.
 *
 * Rendering contract:
 * - Fills the remaining widget body and centers the hint both vertically and horizontally,
 *   so the message stays readable across widget sizes and launcher grids.
 * - Text is user-facing Chinese per project language requirements.
 * - The whole widget root is already clickable (launches [com.kurosu.sleepin.MainActivity]),
 *   so the subtitle can safely tell users to tap the widget to open the app.
 *
 * @param palette resolved color tokens; callers pass the light/dark-aware fallback palette
 *        because this state has no [WidgetSnapshot.Content] to derive colors from
 */
@Composable
internal fun NoActiveTimetableContent(palette: WidgetPalette) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Emoji works as a lightweight icon in RemoteViews without drawable resources.
            Text(text = "📋", style = TextStyle(fontSize = 26.sp))
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "暂无启用的课程表",
                style = TextStyle(
                    color = ColorProvider(Color(palette.onSurface)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "点击打开 SleepIn 创建或启用",
                style = TextStyle(
                    color = ColorProvider(Color(palette.onSurfaceMuted)),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}
