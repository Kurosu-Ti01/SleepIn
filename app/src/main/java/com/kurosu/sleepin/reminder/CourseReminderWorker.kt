package com.kurosu.sleepin.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker fallback that executes reminder evaluation and notification posting.
 */
class CourseReminderWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    /**
     * Executes one reminder scan and always refreshes the next exact alarm before exit.
     */
    override suspend fun doWork(): Result {
        return try {
            CourseReminderNotificationExecutor.execute(
                context = applicationContext,
                toleranceMinutes = CourseReminderNotificationExecutor.PERIODIC_TOLERANCE_MINUTES
            )
            Result.success()
        } finally {
            // Keep exact alarm aligned even when this run exits early.
            runCatching {
                CourseReminderScheduler.scheduleNextExactAlarm(applicationContext)
            }
        }
    }
}
