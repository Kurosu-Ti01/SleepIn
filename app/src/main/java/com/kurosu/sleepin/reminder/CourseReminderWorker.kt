package com.kurosu.sleepin.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kurosu.sleepin.MainActivity
import com.kurosu.sleepin.R
import com.kurosu.sleepin.SleepInApplication
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.ui.screen.home.SemesterProgress
import com.kurosu.sleepin.ui.screen.home.calculateSemesterWeekInfo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * Worker that finds the next class and posts a "before class" reminder notification.
 *
 * Side effects:
 * - Reads timetable/course/settings snapshots from repositories through application use cases.
 * - Writes one dedupe key to SharedPreferences so the same reminder is not posted repeatedly.
 * - Posts a system notification if a reminder candidate is found and permission is granted.
 */
class CourseReminderWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SleepInApplication ?: return Result.success()
        val settings = app.observeSettingsUseCase().first()
        if (!settings.notificationsEnabled) return Result.success()

        if (!canPostNotification(applicationContext)) {
            return Result.success()
        }

        val activeTimetable = app.getActiveTimetableUseCase().first() ?: return Result.success()
        val now = LocalDateTime.now()
        val weekInfo = calculateSemesterWeekInfo(
            startDate = activeTimetable.startDate,
            totalWeeks = activeTimetable.totalWeeks,
            today = now.toLocalDate()
        )
        if (weekInfo.progress != SemesterProgress.IN_PROGRESS) return Result.success()

        val currentWeek = weekInfo.currentWeek ?: return Result.success()
        val schedulePeriods = app.getScheduleDetailUseCase(activeTimetable.scheduleId)
            ?.periods
            .orEmpty()
            .associateBy { it.periodNumber }
        val courses = app.getCoursesForTimetableUseCase(activeTimetable.id).first()

        val reminders = findDueReminders(
            now = now,
            today = now.toLocalDate(),
            currentWeek = currentWeek,
            reminderMinutes = settings.reminderMinutes,
            courses = courses,
            periodsByNumber = schedulePeriods
        )
        if (reminders.isEmpty()) return Result.success()

        val dedupeSet = loadDedupeSet(now.toEpochMillis())
        val pending = reminders.filterNot { reminder -> reminder.dedupeKey in dedupeSet }
        if (pending.isEmpty()) return Result.success()

        ensureChannel(applicationContext)
        pending.forEach { reminder ->
            postReminder(applicationContext, reminder)
            dedupeSet += reminder.dedupeKey
        }
        saveDedupeSet(dedupeSet)
        return Result.success()
    }

    /**
     * Returns all classes whose reminder window currently includes [now].
     */
    private fun findDueReminders(
        now: LocalDateTime,
        today: LocalDate,
        currentWeek: Int,
        reminderMinutes: Int,
        courses: List<CourseWithSessions>,
        periodsByNumber: Map<Int, SchedulePeriod>
    ): List<ReminderPayload> {
        return courses
            .flatMap { aggregate ->
                aggregate.sessions.mapNotNull { session ->
                    if (session.dayOfWeek != today.dayOfWeek.value) return@mapNotNull null
                    if (!isSessionVisibleInWeek(session, currentWeek)) return@mapNotNull null

                    val startPeriod = periodsByNumber[session.startPeriod] ?: return@mapNotNull null
                    val startAt = LocalDateTime.of(today, startPeriod.startTime)
                    val reminderAt = startAt.minusMinutes(reminderMinutes.toLong())

                    if (now.isBefore(reminderAt) || !now.isBefore(startAt)) {
                        return@mapNotNull null
                    }

                    ReminderPayload(
                        courseName = aggregate.course.name,
                        location = session.location,
                        startAt = startAt,
                        dedupeKey = buildDedupeKey(
                            timetableId = aggregate.course.timetableId,
                            courseId = aggregate.course.id,
                            sessionId = session.id,
                            startAtEpochMillis = startAt.toEpochMillis()
                        )
                    )
                }
            }
            .sortedBy { it.startAt }
    }

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

    private fun postReminder(context: Context, payload: ReminderPayload) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "即将上课：${payload.courseName}"
        val content = buildString {
            append("将在 ")
            append(payload.startAt.format(TIME_FORMATTER))
            append(" 开始")
            if (!payload.location.isNullOrBlank()) {
                append(" · ")
                append(payload.location)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(payload.dedupeKey.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "上课提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "在上课前按设置分钟数发送提醒"
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotification(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadDedupeSet(nowEpochMillis: Long): MutableSet<String> {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getStringSet(KEY_NOTIFIED_KEYS, emptySet()).orEmpty()
        val cutoff = nowEpochMillis - DEDUPE_RETENTION_MILLIS
        return cached.filterTo(mutableSetOf()) { key ->
            key.substringAfterLast('_').toLongOrNull()?.let { it >= cutoff } == true
        }
    }

    private fun saveDedupeSet(set: Set<String>) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_NOTIFIED_KEYS, set).apply()
    }

    private fun buildDedupeKey(
        timetableId: Long,
        courseId: Long,
        sessionId: Long,
        startAtEpochMillis: Long
    ): String {
        return "${timetableId}_${courseId}_${sessionId}_$startAtEpochMillis"
    }

    private fun LocalDateTime.toEpochMillis(): Long {
        return atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private data class ReminderPayload(
        val courseName: String,
        val location: String?,
        val startAt: LocalDateTime,
        val dedupeKey: String
    )

    companion object {
        private const val CHANNEL_ID = "course_reminders"
        private const val PREFS_NAME = "course_reminder_prefs"
        private const val KEY_NOTIFIED_KEYS = "notified_keys"
        private const val DEDUPE_RETENTION_MILLIS = 2 * 24 * 60 * 60 * 1000L
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    }
}
