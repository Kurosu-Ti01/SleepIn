package com.kurosu.sleepin.importer

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kurosu.sleepin.domain.model.importer.MAX_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS
import com.kurosu.sleepin.domain.model.importer.MIN_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS
import java.util.concurrent.TimeUnit

/**
 * WorkManager scheduling for the built-in preset source.
 *
 * Two kinds of work, for two different reasons:
 *
 * - **One-time, on launch** — a fresh install has no presets at all, and the user should not have to
 *   wait for the periodic schedule to come around before the list is useful. This request is repeated on
 *   every launch; whether it actually fetches anything is decided by
 *   [com.kurosu.sleepin.domain.usecase.importer.RefreshImportSourcesUseCase], which knows when the
 *   stored copy was last replaced.
 * - **Periodic** — keeps the copy current while the app is not being opened. Its interval is the user's
 *   setting, so changing the setting replaces the existing work rather than adding a second schedule.
 */
object ImportSourceRefreshScheduler {

    private const val PERIODIC_WORK_NAME = "sleepin_import_source_periodic_refresh"
    private const val ONCE_WORK_NAME = "sleepin_import_source_launch_refresh"

    /** Both jobs only make sense online, and the system should not wake the radio for them otherwise. */
    private val networkConstraints: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Aligns the periodic refresh with the user's preference: on with [intervalDays], or off entirely.
     */
    fun syncPeriodic(context: Context, enabled: Boolean, intervalDays: Int) {
        if (enabled) {
            schedulePeriodic(context, intervalDays)
        } else {
            cancelPeriodic(context)
        }
    }

    /** Enqueues or updates the periodic refresh. Safe to call on every settings change. */
    fun schedulePeriodic(context: Context, intervalDays: Int) {
        val days = intervalDays
            .coerceIn(MIN_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS, MAX_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS)
            .toLong()

        val request = PeriodicWorkRequestBuilder<ImportSourceRefreshWorker>(days, TimeUnit.DAYS)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Asks for a refresh on this launch.
     *
     * The worker itself decides whether anything is due, so this is deliberately not a "download now"
     * call: it is queued as soon as the app starts, and an app that is opened ten times a day still
     * fetches once per interval.
     *
     * [ExistingWorkPolicy.KEEP] rather than `REPLACE`: replacing would restart a fetch that is already
     * running, which on a slow connection is exactly when it hurts.
     */
    fun requestLaunchRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<ImportSourceRefreshWorker>()
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}
