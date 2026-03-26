package com.kurosu.sleepin.domain.model

/**
 * Normalized release metadata used by UI and update workflows.
 */
data class AppReleaseInfo(
    val versionTag: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String
)

/**
 * Result model for one GitHub update check request.
 */
sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val release: AppReleaseInfo) : AppUpdateCheckResult
    data class UpToDate(val latestVersionTag: String) : AppUpdateCheckResult
    data class Failed(val reason: String) : AppUpdateCheckResult
    data object Skipped : AppUpdateCheckResult
}

