package com.kurosu.sleepin.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore-backed implementation of app settings repository.
 *
 * Notes:
 * - Values are mapped to a single [AppSettings] snapshot for UI simplicity.
 * - Backup/restore is JSON-based and versioned for future schema evolution.
 */
class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun observeSettings(): Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            reminderMinutes = prefs[Keys.REMINDER_MINUTES] ?: 10,
            fluidCloudEnabled = prefs[Keys.FLUID_CLOUD_ENABLED] ?: true,
            themeMode = ThemeMode.entries
                .getOrNull(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.ordinal)
                ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: true,
            courseCellHeightDp = (prefs[Keys.COURSE_CELL_HEIGHT_DP] ?: 68).coerceIn(44, 120),
            showNonCurrentWeekCourses = prefs[Keys.SHOW_NON_CURRENT_WEEK_COURSES] ?: false
        )
    }

    override suspend fun updateSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = settings.notificationsEnabled
            prefs[Keys.REMINDER_MINUTES] = settings.reminderMinutes
            prefs[Keys.FLUID_CLOUD_ENABLED] = settings.fluidCloudEnabled
            prefs[Keys.THEME_MODE] = settings.themeMode.ordinal
            prefs[Keys.DYNAMIC_COLOR_ENABLED] = settings.dynamicColorEnabled
            prefs[Keys.COURSE_CELL_HEIGHT_DP] = settings.courseCellHeightDp.coerceIn(44, 120)
            prefs[Keys.SHOW_NON_CURRENT_WEEK_COURSES] = settings.showNonCurrentWeekCourses
        }
    }

    override suspend fun exportSettingsBackup(): String {
        val current = observeSettingsOneShot()
        val payload = SettingsBackupPayload(
            version = 1,
            notificationsEnabled = current.notificationsEnabled,
            reminderMinutes = current.reminderMinutes,
            fluidCloudEnabled = current.fluidCloudEnabled,
            themeMode = current.themeMode.name,
            dynamicColorEnabled = current.dynamicColorEnabled,
            courseCellHeightDp = current.courseCellHeightDp,
            showNonCurrentWeekCourses = current.showNonCurrentWeekCourses
        )
        return json.encodeToString(SettingsBackupPayload.serializer(), payload)
    }

    override suspend fun importSettingsBackup(rawJson: String) {
        val payload = json.decodeFromString(SettingsBackupPayload.serializer(), rawJson)
        val restored = AppSettings(
            notificationsEnabled = payload.notificationsEnabled,
            reminderMinutes = payload.reminderMinutes.coerceIn(5, 30),
            fluidCloudEnabled = payload.fluidCloudEnabled,
            themeMode = ThemeMode.entries.firstOrNull { it.name == payload.themeMode } ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = payload.dynamicColorEnabled,
            courseCellHeightDp = payload.courseCellHeightDp.coerceIn(44, 120),
            showNonCurrentWeekCourses = payload.showNonCurrentWeekCourses
        )
        updateSettings(restored)
    }

    private suspend fun observeSettingsOneShot(): AppSettings =
        observeSettings().first()

    private object Keys {
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val FLUID_CLOUD_ENABLED = booleanPreferencesKey("fluid_cloud_enabled")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val COURSE_CELL_HEIGHT_DP = intPreferencesKey("course_cell_height_dp")
        val SHOW_NON_CURRENT_WEEK_COURSES = booleanPreferencesKey("show_non_current_week_courses")
    }

    @Serializable
    private data class SettingsBackupPayload(
        val version: Int,
        val notificationsEnabled: Boolean,
        val reminderMinutes: Int,
        val fluidCloudEnabled: Boolean,
        val themeMode: String,
        val dynamicColorEnabled: Boolean,
        val courseCellHeightDp: Int,
        val showNonCurrentWeekCourses: Boolean = false
    )
}


