package com.kurosu.sleepin.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Central scheduler for widget refresh work.
 *
 * Why a dedicated object:
 * - keeps work names in one place,
 * - avoids duplicated enqueue logic,
 * - makes future tuning (interval, constraints) straightforward.
 */
object WidgetRefreshScheduler {

    private const val PERIODIC_WORK_NAME = "sleepin_widget_periodic_refresh"
    private const val IMMEDIATE_WORK_NAME = "sleepin_widget_immediate_refresh"

    /**
     * Ensures the periodic refresh pipeline exists.
     *
     * `UPDATE` lets us change interval/constraints in future releases without forcing users
     * to clear app data.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Triggers a near-real-time refresh after data mutations.
     *
     * `REPLACE` ensures frequent edits collapse into one latest request.
     */
    fun requestImmediateUpdate(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

