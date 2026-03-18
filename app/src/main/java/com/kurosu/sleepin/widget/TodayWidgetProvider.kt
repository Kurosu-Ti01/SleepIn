package com.kurosu.sleepin.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
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
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kurosu.sleepin.MainActivity
import com.kurosu.sleepin.SleepInApplication
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * AndroidManifest receiver entry for the Today widget.
 *
 * The receiver is intentionally minimal and delegates all logic to [TodayWidget].
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget
}

/**
 * Widget that shows today's classes for the active timetable.
 */
object TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
        val app = context.applicationContext as SleepInApplication
        val snapshot = WidgetCourseSnapshotProvider(app).load()

        provideContent {
            TodayWidgetContent(snapshot)
        }
    }
}

/**
 * Stateless renderer for the Today widget.
 */
@Composable
private fun TodayWidgetContent(snapshot: WidgetSnapshot) {
    val launchAppAction = actionStartActivity<MainActivity>()
    val widgetSize = LocalSize.current
    val compact = widgetSize.height.value < 170f
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
                    text = "Today",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = singleColor(palette.onSurface)
                    )
                )
                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(text = "No active timetable", style = TextStyle(color = singleColor(palette.onSurfaceMuted)))
                Text(text = "Open SleepIn to choose one", style = TextStyle(color = singleColor(palette.onSurfaceMuted)))
            }

            is WidgetSnapshot.Content -> {
                val remainingCourses = filterRemainingCourses(snapshot.todayCourses)

                HeaderBlock(snapshot = snapshot, remainingCount = remainingCourses.size)

                snapshot.semesterStatusLabel?.let { status ->
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = status,
                        style = TextStyle(
                            fontStyle = FontStyle.Italic,
                            color = singleColor(snapshot.palette.onSurfaceMuted)
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                if (remainingCourses.isEmpty()) {
                    Text(
                        text = "今天没有课哦 (/≧▽≦)/",
                        style = TextStyle(color = singleColor(snapshot.palette.onSurfaceMuted))
                    )
                } else {
                    val maxRows = if (compact) 2 else 4
                    remainingCourses.take(maxRows).forEach { item ->
                        TodayCourseRow(item = item, palette = snapshot.palette)
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }

                    if (remainingCourses.size > maxRows) {
                        Text(
                            text = "+${remainingCourses.size - maxRows} more",
                            style = TextStyle(color = singleColor(snapshot.palette.onSurfaceMuted))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBlock(snapshot: WidgetSnapshot.Content, remainingCount: Int) {
    // Use one line for date/week/count to stay compatible with older Glance versions.
    Text(
        text = "${snapshot.dateLabel} ${snapshot.weekdayLabel} · 第${snapshot.currentWeek}周    剩余${remainingCount}节课",
        style = TextStyle(
            color = singleColor(snapshot.palette.onSurface),
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1
    )
    Text(
        text = snapshot.timetableName,
        style = TextStyle(color = singleColor(snapshot.palette.onSurfaceMuted)),
        maxLines = 1
    )
}

/**
 * Compact one-line course row designed for small widget space.
 */
@Composable
private fun TodayCourseRow(item: WidgetSessionItem, palette: WidgetPalette) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(singleColor(palette.surfaceSubtle))
            .cornerRadius(12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The colored stripe carries course identity while the card body stays visually stable.
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(34.dp)
                .background(singleColor(item.courseColor))
                .cornerRadius(2.dp)
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = item.courseName,
                style = TextStyle(
                    color = singleColor(palette.onSurface),
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = formatCourseSubline(item),
                style = TextStyle(color = singleColor(palette.onSurfaceMuted)),
                maxLines = 1
            )
        }
    }
}

/**
 * Build a compact secondary line that always keeps time and location in a single visual unit.
 */
private fun formatCourseSubline(item: WidgetSessionItem): String {
    return when {
        item.startTimeLabel != null && item.endTimeLabel != null -> {
            val location = item.location?.takeIf { it.isNotBlank() } ?: "--"
            "${item.startTimeLabel} - ${item.endTimeLabel}  $location"
        }

        else -> "P${item.startPeriod}-${item.endPeriod}"
    }
}

/**
 * Hide sessions that have already ended; sessions with unknown time metadata stay visible.
 */
private fun filterRemainingCourses(
    courses: List<WidgetSessionItem>,
    now: LocalTime = LocalTime.now()
): List<WidgetSessionItem> {
    return courses.filter { item ->
        val end = item.endTimeLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalTime.parse(it, TIME_FORMATTER) }.getOrNull() }

        // Keep course visible when no end-time can be resolved from schedule settings.
        end == null || !end.isBefore(now)
    }
}


private fun singleColor(argb: Int): ColorProvider =
    ColorProvider(color = Color(argb))

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")



