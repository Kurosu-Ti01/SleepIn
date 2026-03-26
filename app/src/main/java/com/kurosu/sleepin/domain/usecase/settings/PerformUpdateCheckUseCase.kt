package com.kurosu.sleepin.domain.usecase.settings

import com.kurosu.sleepin.domain.model.AppUpdateCheckResult
import com.kurosu.sleepin.domain.repository.SettingsRepository
import com.kurosu.sleepin.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.first

/**
 * Executes one update check and persists the latest check status into settings.
 */
class PerformUpdateCheckUseCase(
    private val settingsRepository: SettingsRepository,
    private val updateRepository: UpdateRepository
) {
    /**
     * Runs one update check using [currentVersionName] and persists status fields into settings.
     *
     * When [force] is false, checks are skipped if the user has disabled auto-check.
     */
    suspend operator fun invoke(force: Boolean, currentVersionName: String): AppUpdateCheckResult {
        val current = settingsRepository.observeSettings().first()
        if (!force && !current.autoCheckUpdateEnabled) {
            return AppUpdateCheckResult.Skipped
        }

        val result = updateRepository.checkForUpdate(currentVersionName = currentVersionName)
        val updated = when (result) {
            is AppUpdateCheckResult.UpdateAvailable -> {
                current.copy(
                    lastUpdateCheckAtMillis = System.currentTimeMillis(),
                    latestRemoteVersion = result.release.versionTag,
                    latestReleaseNotes = result.release.releaseNotes,
                    latestApkDownloadUrl = result.release.apkDownloadUrl,
                    latestReleasePageUrl = result.release.releasePageUrl,
                    updateAvailable = true,
                    dismissedUpdateVersion = if (current.dismissedUpdateVersion == result.release.versionTag) {
                        current.dismissedUpdateVersion
                    } else {
                        ""
                    },
                    lastUpdateCheckError = ""
                )
            }

            is AppUpdateCheckResult.UpToDate -> {
                current.copy(
                    lastUpdateCheckAtMillis = System.currentTimeMillis(),
                    latestRemoteVersion = result.latestVersionTag,
                    latestReleaseNotes = "",
                    latestApkDownloadUrl = "",
                    latestReleasePageUrl = "",
                    updateAvailable = false,
                    dismissedUpdateVersion = result.latestVersionTag,
                    lastUpdateCheckError = ""
                )
            }

            is AppUpdateCheckResult.Failed -> {
                current.copy(
                    lastUpdateCheckAtMillis = System.currentTimeMillis(),
                    lastUpdateCheckError = result.reason
                )
            }

            AppUpdateCheckResult.Skipped -> current
        }

        settingsRepository.updateSettings(updated)
        return result
    }
}



