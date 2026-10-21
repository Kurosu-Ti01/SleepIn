package com.kurosu.sleepin.importer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kurosu.sleepin.data.preferences.importPresetDataStore
import com.kurosu.sleepin.data.preferences.settingsDataStore
import com.kurosu.sleepin.di.RepositoryModule
import com.kurosu.sleepin.domain.usecase.importer.ImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.RefreshImportSourcesUseCase

/**
 * Background refresh of the built-in preset source.
 *
 * Builds its own dependency graph from [RepositoryModule] because a worker is started by the system and
 * has no access to the application object's fields — the same arrangement the other workers use.
 *
 * A source that cannot be read is **not** retried here: the use case records the failure into settings
 * and leaves the timestamp alone, so the next launch tries again. Retrying from WorkManager on top of
 * that would only produce a burst of attempts against a source that is down.
 */
class ImportSourceRefreshWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext

        val sourceFileStore = RepositoryModule.provideImportSourceFileStore(appContext.filesDir)
        val importSourceRepository = RepositoryModule.provideImportSourceRepository(
            remotePresets = RepositoryModule.provideRemoteImportPresetStore(
                dataStore = appContext.importPresetDataStore,
                fileStore = sourceFileStore
            ),
            sourceFileStore = sourceFileStore,
            sourceIndexParser = RepositoryModule.provideImportSourceIndexParser(),
            contentResolver = appContext.contentResolver
        )

        val useCase = RefreshImportSourcesUseCase(
            settingsRepository = RepositoryModule.provideSettingsRepository(appContext.settingsDataStore),
            importSourceRepository = importSourceRepository
        )

        return runCatching { useCase() }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }
}
