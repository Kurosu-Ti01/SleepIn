package com.kurosu.sleepin.domain.model

/**
 * User-selectable app theme policy.
 *
 * - [SYSTEM] follows the device-wide dark mode setting.
 * - [LIGHT] always uses light colors.
 * - [DARK] always uses dark colors.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Centralized settings model exposed to UI and other modules.
 *
 * Keeping all settings in one immutable object makes state synchronization predictable:
 * - Data layer emits a full snapshot via Flow.
 * - ViewModel updates produce a copied instance.
 * - Compose simply renders this snapshot as source of truth.
 */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val reminderMinutes: Int = 10,
    val fluidCloudEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val courseCellHeightDp: Int = 68,
    val showNonCurrentWeekCourses: Boolean = false,
    val autoCheckUpdateEnabled: Boolean = true,
    val updateAvailable: Boolean = false,
    val latestRemoteVersion: String = "",
    val latestReleaseNotes: String = "",
    val latestApkDownloadUrl: String = "",
    val latestReleasePageUrl: String = "",
    val dismissedUpdateVersion: String = "",
    val lastUpdateCheckError: String = "",
    val lastUpdateCheckAtMillis: Long = 0L
)

