package com.kurosu.sleepin.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Worker that refreshes every SleepIn widget instance.
 *
 * We keep the worker intentionally tiny: it delegates data loading to each widget's
 * `provideGlance` pipeline and only coordinates refresh timing.
 */
class WidgetRefreshWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    /**
     * Executes one refresh cycle for all widget variants.
     *
     * Side effects:
     * - Triggers Glance recomposition for Today and Week widgets.
     * - Causes each widget to re-run its snapshot loading pipeline.
     *
     * Retry policy:
     * - Returns [Result.retry] on any exception so WorkManager can back off and try again,
     *   which is safer than dropping refreshes silently.
     */
    override suspend fun doWork(): Result {
        return runCatching {
            TodayWidget.updateAll(applicationContext)
            WeekWidget.updateAll(applicationContext)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

