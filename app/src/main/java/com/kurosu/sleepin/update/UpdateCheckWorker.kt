package com.kurosu.sleepin.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.kurosu.sleepin.BuildConfig
import com.kurosu.sleepin.data.preferences.settingsDataStore
import com.kurosu.sleepin.di.RepositoryModule
import com.kurosu.sleepin.domain.usecase.settings.PerformUpdateCheckUseCase

/**
 * Background worker that checks GitHub release updates and writes status into settings.
 */
class UpdateCheckWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    /**
     * Performs one update check and retries on unexpected failures.
     */
    override suspend fun doWork(): Result {
        val settingsRepository = RepositoryModule.provideSettingsRepository(applicationContext.settingsDataStore)
        val updateRepository = RepositoryModule.provideUpdateRepository()
        val useCase = PerformUpdateCheckUseCase(settingsRepository, updateRepository)
        val force = inputData.getBoolean(KEY_FORCE, false)

        return runCatching {
            useCase(force = force, currentVersionName = BuildConfig.VERSION_NAME)
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_FORCE = "force"

        fun inputData(force: Boolean): Data = Data.Builder()
            .putBoolean(KEY_FORCE, force)
            .build()
    }
}


