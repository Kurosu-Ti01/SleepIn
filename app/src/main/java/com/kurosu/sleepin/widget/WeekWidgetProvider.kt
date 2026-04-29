package com.kurosu.sleepin.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kurosu.sleepin.MainActivity
import com.kurosu.sleepin.SleepInApplication
import java.time.LocalDate

/**
 * AndroidManifest receiver entry for the Week overview widget.
 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget
}

/**
 * Widget that shows a compact summary for each weekday in the selected semester week.
 */
object WeekWidget : GlanceAppWidget() {
    /**
     * Glance entrypoint for week-summary rendering.
     *
     * The host process may call this at any time (placement, resize, periodic refresh),
     * so this function always rebuilds snapshot data from use cases instead of caching UI state.
     */
    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
        val app = context.applicationContext as SleepInApplication
        val snapshot = WidgetCourseSnapshotProvider(app).load()

        provideContent {
            WeekWidgetContent(snapshot)
        }
    }
}

/**
 * Stateless renderer for week summary rows.
 */
@Composable
private fun WeekWidgetContent(snapshot: WidgetSnapshot) {
    val launchAppAction = actionStartActivity<MainActivity>()
    val todayDayOfWeek = LocalDate.now().dayOfWeek.value
    val palette = (snapshot as? WidgetSnapshot.Content)?.palette
        ?: WidgetPalette(
            surface = 0xFFFFFFFF.toInt(),
            surfaceSubtle = 0xFFF6F8FC.toInt(),
            onSurface = 0xFF202124.toInt(),
            onSurfaceMuted = 0xFF6B7280.toInt(),
            primary = 0xFF4A90D9.toInt(),
            outline = 0xFFE5E7EB.toInt(),
            onAccent = 0xFFFFFFFF.toInt()
        )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(singleColor(palette.surface))
            .padding(12.dp)
            .clickable(launchAppAction),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start
    ) {
        when (snapshot) {
            WidgetSnapshot.NoActiveTimetable -> {
                Text(
                    text = "Week",
                    style = TextStyle(fontWeight = FontWeight.Bold, color = singleColor(palette.onSurface))
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(text = "No active timetable", style = TextStyle(color = singleColor(palette.onSurfaceMuted)))
            }

            is WidgetSnapshot.Content -> {
                Text(
                    text = snapshot.timetableName,
                    style = TextStyle(fontWeight = FontWeight.Bold, color = singleColor(snapshot.palette.onSurface))
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = "${snapshot.dateLabel} ${snapshot.weekdayLabel} · 第${snapshot.currentWeek}周",
                    style = TextStyle(color = singleColor(snapshot.palette.primary), fontWeight = FontWeight.Medium)
                )
                snapshot.semesterStatusLabel?.let { status ->
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(text = status, style = TextStyle(color = singleColor(snapshot.palette.onSurfaceMuted)))
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                // A stable day-by-day layout is easier to scan than dense text on launchers.
                snapshot.weekOverview.forEach { day ->
                    DayLine(
                        day = day,
                        palette = snapshot.palette,
                        isToday = day.dayOfWeek == todayDayOfWeek
                    )
                }
            }
        }
    }
}

@Composable
private fun DayLine(day: WidgetDayOverview, palette: WidgetPalette, isToday: Boolean) {
    val accent = day.accentColor ?: palette.outline
    val background = if (isToday) withAlpha(palette.primary, 0.14f) else palette.surfaceSubtle

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(singleColor(background))
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .background(singleColor(accent))
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = day.weekdayLabel,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = singleColor(if (isToday) palette.primary else palette.onSurface)
            )
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = if (day.count == 0) "无课" else "${day.summary} (${day.count}节)",
            style = TextStyle(color = singleColor(palette.onSurfaceMuted)),
            maxLines = 1
        )
        if (day.count > 0) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .background(singleColor(accent))
                    .cornerRadius(8.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${day.count}",
                    style = TextStyle(
                        color = singleColor(palette.onAccent),
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Apply an alpha channel while preserving original RGB, used for subtle selection backgrounds.
 */
private fun withAlpha(argb: Int, alpha: Float): Int {
    val normalizedAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
    return (argb and 0x00FFFFFF) or (normalizedAlpha shl 24)
}

private fun singleColor(argb: Int): ColorProvider {
    val color = Color(argb)
    return ColorProvider(color)
}
