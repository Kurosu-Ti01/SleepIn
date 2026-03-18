package com.kurosu.sleepin.widget

import com.kurosu.sleepin.SleepInApplication
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.ui.screen.home.SemesterProgress
import com.kurosu.sleepin.ui.screen.home.calculateSemesterWeekInfo
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import android.content.res.Configuration
import kotlinx.coroutines.flow.first

/**
 * Builds a compact, widget-ready snapshot from existing domain use cases.
 *
 * The widget layer intentionally reads from the same use cases as the UI layer so it follows
 * exactly the same business rules (active timetable selection, recurrence filtering, and
 * schedule period mapping). This avoids duplicated SQL and keeps behavior consistent.
 */
class WidgetCourseSnapshotProvider(
    private val app: SleepInApplication
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("M-d")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Loads all data needed by both the Today and Week widgets in one request.
     *
     * [today] is injectable for deterministic tests and for future preview tooling.
     */
    suspend fun load(today: LocalDate = LocalDate.now()): WidgetSnapshot {
        val settings = app.observeSettingsUseCase().first()
        val palette = resolvePalette(settings.themeMode, settings.dynamicColorEnabled)

        val activeTimetable = app.getActiveTimetableUseCase().first() ?: return WidgetSnapshot.NoActiveTimetable

        val semesterWeekInfo = calculateSemesterWeekInfo(
            startDate = activeTimetable.startDate,
            totalWeeks = activeTimetable.totalWeeks,
            today = today
        )

        // When semester is outside range we still render timetable metadata, but no sessions.
        val selectedWeek = when (semesterWeekInfo.progress) {
            SemesterProgress.IN_PROGRESS -> semesterWeekInfo.currentWeek ?: 1
            SemesterProgress.BEFORE_START,
            SemesterProgress.AFTER_END -> 1
        }.coerceIn(1, activeTimetable.totalWeeks.coerceAtLeast(1))

        val schedulePeriods = app.getScheduleDetailUseCase(activeTimetable.scheduleId)
            ?.periods
            .orEmpty()
            .sortedBy { it.periodNumber }

        val allCourses = app.getCoursesForTimetableUseCase(activeTimetable.id).first()

        val visibleSessions = buildVisibleSessions(
            courses = allCourses,
            schedulePeriods = schedulePeriods,
            week = selectedWeek
        )

        val todaySessions = visibleSessions
            .filter { it.dayOfWeek == today.dayOfWeek.value }
            .sortedWith(
                compareBy<WidgetSessionItem> { it.startPeriod }
                    .thenBy { it.endPeriod }
                    .thenBy { it.courseName }
            )

        return WidgetSnapshot.Content(
            palette = palette,
            timetableName = activeTimetable.name,
            dateLabel = today.format(dateFormatter),
            weekdayLabel = weekdayLabelCn(today.dayOfWeek.value),
            weekLabel = "Week $selectedWeek / ${activeTimetable.totalWeeks}",
            currentWeek = selectedWeek,
            semesterStatusLabel = semesterStatusLabel(semesterWeekInfo.progress),
            todayCourses = todaySessions,
            weekOverview = buildWeekOverview(visibleSessions)
        )
    }

    /**
     * Converts domain aggregates into display rows while applying recurrence rules.
     */
    private fun buildVisibleSessions(
        courses: List<CourseWithSessions>,
        schedulePeriods: List<SchedulePeriod>,
        week: Int
    ): List<WidgetSessionItem> {
        return courses.flatMap { aggregate ->
            aggregate.sessions
                .filter { session -> isSessionVisibleInWeek(session, week) }
                .map { session ->
                    session.toWidgetItem(
                        courseName = aggregate.course.name,
                        courseColor = aggregate.course.color,
                        periods = schedulePeriods
                    )
                }
        }
    }

    /**
     * Mirrors Home screen recurrence logic so widgets and in-app grid never disagree.
     */
    private fun isSessionVisibleInWeek(session: CourseSession, week: Int): Boolean {
        return when (session.weekType) {
            WeekType.ALL -> true
            WeekType.RANGE -> {
                val start = session.startWeek ?: 1
                val end = session.endWeek ?: Int.MAX_VALUE
                week in start..end
            }
            WeekType.CUSTOM -> week in session.customWeeks
        }
    }

    /**
     * Aggregates one-line summary text for each weekday (Mon..Sun) used by the week widget.
     */
    private fun buildWeekOverview(sessions: List<WidgetSessionItem>): List<WidgetDayOverview> {
        return (1..7).map { dayIndex ->
            val daySessions = sessions
                .filter { it.dayOfWeek == dayIndex }
                .sortedWith(compareBy<WidgetSessionItem> { it.startPeriod }.thenBy { it.endPeriod })

            val weekdayLabel = weekdayShortLabel(dayIndex)
            val summary = when {
                daySessions.isEmpty() -> "No classes"
                else -> {
                    val first = daySessions.first()
                    val extraCount = daySessions.size - 1
                    if (extraCount > 0) {
                        "${first.courseName} +$extraCount"
                    } else {
                        first.courseName
                    }
                }
            }

            WidgetDayOverview(
                dayOfWeek = dayIndex,
                weekdayLabel = weekdayLabel,
                summary = summary,
                count = daySessions.size,
                accentColor = daySessions.firstOrNull()?.courseColor
            )
        }
    }

    /**
     * Uses locale-aware short weekday names while keeping Monday as index 1.
     */
    private fun weekdayShortLabel(dayOfWeek: Int): String {
        val normalized = dayOfWeek.coerceIn(1, 7)
        val referenceDate = LocalDate.of(2026, 1, 5).plusDays((normalized - 1).toLong())
        return referenceDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    private fun semesterStatusLabel(progress: SemesterProgress): String? {
        return when (progress) {
            SemesterProgress.BEFORE_START -> "Semester has not started yet"
            SemesterProgress.AFTER_END -> "Semester has already ended"
            SemesterProgress.IN_PROGRESS -> null
        }
    }

    private fun weekdayLabelCn(dayOfWeek: Int): String {
        return when (dayOfWeek.coerceIn(1, 7)) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }

    /**
     * Resolves a stable palette for widgets.
     *
     * Widgets are rendered by the launcher process, so we do not rely on Compose Material theme.
     * Instead, we derive colors from app settings and pick robust fallbacks that look good on
     * both light/dark modes.
     */
    private fun resolvePalette(themeMode: ThemeMode, dynamicColorEnabled: Boolean): WidgetPalette {
        val dark = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val nightMask = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }

        val accent = if (dynamicColorEnabled) 0xFF4A90D9.toInt() else 0xFF3F51B5.toInt()

        return if (dark) {
            WidgetPalette(
                surface = 0xFF1F2023.toInt(),
                surfaceSubtle = 0xFF2A2D33.toInt(),
                onSurface = 0xFFF2F3F5.toInt(),
                onSurfaceMuted = 0xFFB9BEC8.toInt(),
                primary = accent,
                outline = 0xFF34373D.toInt(),
                onAccent = 0xFFFFFFFF.toInt()
            )
        } else {
            WidgetPalette(
                // Light mode uses a white card to satisfy non-transparent requirement.
                surface = 0xFFFFFFFF.toInt(),
                surfaceSubtle = 0xFFF6F8FC.toInt(),
                onSurface = 0xFF202124.toInt(),
                onSurfaceMuted = 0xFF6B7280.toInt(),
                primary = accent,
                outline = 0xFFE5E7EB.toInt(),
                onAccent = 0xFFFFFFFF.toInt()
            )
        }
    }

    /**
     * Maps one course session + schedule period metadata into a widget row.
     */
    private fun CourseSession.toWidgetItem(
        courseName: String,
        courseColor: Int,
        periods: List<SchedulePeriod>
    ): WidgetSessionItem {
        val start = periods.firstOrNull { it.periodNumber == startPeriod }
        val end = periods.firstOrNull { it.periodNumber == endPeriod }

        return WidgetSessionItem(
            courseName = courseName,
            courseColor = courseColor,
            dayOfWeek = dayOfWeek,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            location = location,
            startTimeLabel = formatTime(start?.startTime),
            endTimeLabel = formatTime(end?.endTime)
        )
    }

    private fun formatTime(time: LocalTime?): String? = time?.format(timeFormatter)
}

/**
 * Root model consumed by widget providers.
 */
sealed interface WidgetSnapshot {
    data object NoActiveTimetable : WidgetSnapshot

    data class Content(
        val palette: WidgetPalette,
        val timetableName: String,
        val dateLabel: String,
        val weekdayLabel: String,
        val weekLabel: String,
        val currentWeek: Int,
        val semesterStatusLabel: String?,
        val todayCourses: List<WidgetSessionItem>,
        val weekOverview: List<WidgetDayOverview>
    ) : WidgetSnapshot
}

/**
 * Compact row used by today widget.
 */
data class WidgetSessionItem(
    val courseName: String,
    val courseColor: Int,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val location: String?,
    val startTimeLabel: String?,
    val endTimeLabel: String?
)

/**
 * One-day summary for week widget.
 */
data class WidgetDayOverview(
    val dayOfWeek: Int,
    val weekdayLabel: String,
    val summary: String,
    val count: Int,
    val accentColor: Int?
)

/**
 * Centralized widget color tokens resolved from settings.
 */
data class WidgetPalette(
    val surface: Int,
    val surfaceSubtle: Int,
    val onSurface: Int,
    val onSurfaceMuted: Int,
    val primary: Int,
    val outline: Int,
    val onAccent: Int
)


