package com.kurosu.sleepin.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kurosu.sleepin.SleepInApplication
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.ui.screen.home.SemesterProgress
import com.kurosu.sleepin.ui.screen.home.calculateSemesterWeekInfo
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Schedules class reminder checks in background.
 *
 * The scheduler now uses two lanes:
 * - WorkManager periodic checks as a reliability fallback.
 * - AlarmManager single exact trigger for the nearest upcoming reminder.
 */
object CourseReminderScheduler {

    private const val PERIODIC_WORK_NAME = "sleepin_course_reminder_periodic"
    private const val IMMEDIATE_WORK_NAME = "sleepin_course_reminder_immediate"
    private const val ALARM_REQUEST_CODE = 31001

    /**
     * Enables or disables reminder background scheduling according to user setting.
     *
     * Side effects:
     * - Enqueues or cancels periodic WorkManager jobs.
     * - Cancels any pending exact alarm when disabled.
     */
    fun syncPeriodic(context: Context, enabled: Boolean) {
        if (enabled) {
            schedulePeriodic(context)
        } else {
            cancelPeriodic(context)
            cancelExactAlarm(context)
        }
    }

    /**
     * Creates or updates periodic checks.
     *
     * 15 minutes is the minimum interval allowed by WorkManager for periodic jobs.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CourseReminderWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Enqueues one immediate check and replaces stale requests.
     */
    fun requestImmediateCheck(context: Context, expedited: Boolean = false) {
        val builder = OneTimeWorkRequestBuilder<CourseReminderWorker>()
        if (expedited) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = builder.build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Recomputes and schedules the nearest reminder trigger using AlarmManager.
     *
     * This method is safe to call repeatedly after settings/data changes:
     * - It replaces previous exact alarm with the newly calculated nearest one.
     * - It cancels the alarm when reminders are disabled or no future session exists.
     */
    suspend fun scheduleNextExactAlarm(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val app = context.applicationContext as? SleepInApplication ?: return
        val settings = app.observeSettingsUseCase().first()
        if (!settings.notificationsEnabled) {
            cancelExactAlarm(context)
            return
        }

        val timetable = app.getActiveTimetableUseCase().first()
        if (timetable == null) {
            cancelExactAlarm(context)
            return
        }

        val periodsByNumber = app.getScheduleDetailUseCase(timetable.scheduleId)
            ?.periods
            .orEmpty()
            .associateBy { it.periodNumber }
        if (periodsByNumber.isEmpty()) {
            cancelExactAlarm(context)
            return
        }

        val courses = app.getCoursesForTimetableUseCase(timetable.id).first()
        val weekInfo = calculateSemesterWeekInfo(
            startDate = timetable.startDate,
            totalWeeks = timetable.totalWeeks,
            today = now.toLocalDate()
        )
        val candidate = findNextReminderTime(
            now = now,
            semesterStart = timetable.startDate,
            semesterEnd = weekInfo.semesterEndDate,
            totalWeeks = timetable.totalWeeks,
            reminderMinutes = settings.reminderMinutes,
            courses = courses,
            periodsByNumber = periodsByNumber
        )

        if (candidate == null) {
            cancelExactAlarm(context)
            return
        }

        scheduleAlarmAt(context, candidate.toEpochMillis())
    }

    private fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun scheduleAlarmAt(context: Context, triggerAtEpochMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildAlarmPendingIntent(context)

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMillis,
                    pendingIntent
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMillis,
                    pendingIntent
                )
            }
            else -> {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMillis,
                    pendingIntent
                )
            }
        }
    }

    private fun cancelExactAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildAlarmPendingIntent(context))
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CourseReminderAlarmReceiver::class.java).apply {
            action = CourseReminderAlarmReceiver.ACTION_TRIGGER_COURSE_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findNextReminderTime(
        now: LocalDateTime,
        semesterStart: LocalDate,
        semesterEnd: LocalDate,
        totalWeeks: Int,
        reminderMinutes: Int,
        courses: List<CourseWithSessions>,
        periodsByNumber: Map<Int, SchedulePeriod>
    ): LocalDateTime? {
        var date = now.toLocalDate()
        var nearest: LocalDateTime? = null

        while (!date.isAfter(semesterEnd)) {
            val weekInfo = calculateSemesterWeekInfo(
                startDate = semesterStart,
                totalWeeks = totalWeeks,
                today = date
            )
            if (weekInfo.progress == SemesterProgress.IN_PROGRESS) {
                val currentWeek = weekInfo.currentWeek ?: 1
                courses.forEach { aggregate ->
                    aggregate.sessions.forEach { session ->
                        if (session.dayOfWeek != date.dayOfWeek.value) return@forEach
                        if (!isSessionVisibleInWeek(session, currentWeek)) return@forEach

                        val startPeriod = periodsByNumber[session.startPeriod] ?: return@forEach
                        val reminderTime = LocalDateTime.of(date, startPeriod.startTime)
                            .minusMinutes(reminderMinutes.toLong())
                        if (!reminderTime.isAfter(now)) return@forEach

                        if (nearest == null || reminderTime.isBefore(nearest)) {
                            nearest = reminderTime
                        }
                    }
                }
            }
            if (nearest != null) return nearest
            date = date.plusDays(1)
        }

        return null
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

    private fun LocalDateTime.toEpochMillis(): Long {
        return atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
