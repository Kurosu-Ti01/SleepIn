package com.kurosu.sleepin.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarm receiver that wakes the reminder pipeline at an exact trigger time.
 *
 * Side effects:
 * - Runs reminder evaluation immediately for stricter timing.
 * - Falls back to an expedited [CourseReminderWorker] run if direct execution fails.
 */
class CourseReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TRIGGER_COURSE_REMINDER) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                CourseReminderNotificationExecutor.execute(
                    context = context,
                    toleranceMinutes = ALARM_TOLERANCE_MINUTES
                )
            }.onFailure {
                // Keep a fallback path if direct execution fails unexpectedly.
                CourseReminderScheduler.requestImmediateCheck(context, expedited = true)
            }

            runCatching {
                CourseReminderScheduler.scheduleNextExactAlarm(context)
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_TRIGGER_COURSE_REMINDER =
            "com.kurosu.sleepin.action.TRIGGER_COURSE_REMINDER"
        private const val ALARM_TOLERANCE_MINUTES = 2L
    }
}

