package com.kurosu.sleepin.domain.model

import com.kurosu.sleepin.domain.model.importer.DEFAULT_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS

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
    val lastUpdateCheckAtMillis: Long = 0L,
    /**
     * Whether the built-in preset source is refreshed in the background.
     *
     * On by default: the app ships no presets of its own, so without this a fresh install shows an
     * empty list until the user goes looking for a source. It stays a setting because the refresh is
     * the only thing in the app that talks to a server the user did not ask for.
     */
    val importSourceAutoRefreshEnabled: Boolean = true,
    /** How often every preset source is refreshed, in days. */
    val importSourceRefreshIntervalDays: Int = DEFAULT_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS,
    /**
     * When the built-in source was last refreshed successfully.
     *
     * A failed refresh leaves this alone on purpose, so the next attempt happens on the next launch
     * instead of after a whole interval.
     */
    val importSourceLastRefreshAtMillis: Long = 0L,
    /** Message of the last failed refresh; blank when the last attempt succeeded. */
    val importSourceLastRefreshError: String = ""
)

