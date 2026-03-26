package com.kurosu.sleepin.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduler for periodic and on-demand update checks.
 */
object UpdateCheckScheduler {

    private const val PERIODIC_WORK_NAME = "sleepin_update_periodic_check"
    private const val IMMEDIATE_WORK_NAME = "sleepin_update_immediate_check"

    /**
     * Enables or disables periodic update checks according to user preference.
     */
    fun syncPeriodic(context: Context, enabled: Boolean) {
        if (enabled) {
            schedulePeriodic(context)
        } else {
            cancelPeriodic(context)
        }
    }

    /**
     * Enqueues or updates a periodic worker that checks GitHub releases in background.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Requests a one-time update check, usually triggered by app startup or user action.
     */
    fun requestImmediateCheck(context: Context, force: Boolean) {
        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(UpdateCheckWorker.inputData(force = force))
            .build()

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


