package com.kurosu.sleepin.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules class reminder checks in background.
 *
 * We keep a periodic check plus an on-demand check. The periodic work guarantees reminders
 * even when app UI is never opened, while the one-time work reacts quickly to data/setting edits.
 */
object CourseReminderScheduler {

    private const val PERIODIC_WORK_NAME = "sleepin_course_reminder_periodic"
    private const val IMMEDIATE_WORK_NAME = "sleepin_course_reminder_immediate"

    /**
     * Enables or disables periodic reminder checks according to user setting.
     */
    fun syncPeriodic(context: Context, enabled: Boolean) {
        if (enabled) {
            schedulePeriodic(context)
        } else {
            cancelPeriodic(context)
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
    fun requestImmediateCheck(context: Context) {
        val request = OneTimeWorkRequestBuilder<CourseReminderWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
