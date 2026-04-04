package com.kurosu.sleepin.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver that rebuilds reminder scheduling after reboot, app upgrade, or clock changes.
 *
 * Side effects:
 * - Recomputes next exact reminder alarm.
 * - Enqueues one immediate reminder check as a safety catch-up.
 */
class CourseReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                CourseReminderScheduler.scheduleNextExactAlarm(context)
            }
            pendingResult.finish()
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}
