package com.kurosu.sleepin.data.repository

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * Unit tests for DataStore-backed settings persistence and backup restore behavior.
 */
class SettingsRepositoryImplTest {

    @Test
    fun observeSettings_withoutStoredValues_returnsDefaults() = runBlocking {
        val repository = createRepository()

        val settings = repository.observeSettings().first()

        assertTrue(settings.notificationsEnabled)
        assertEquals(10, settings.reminderMinutes)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(68, settings.courseCellHeightDp)
        assertEquals(false, settings.showNonCurrentWeekCourses)
        assertTrue(settings.autoCheckUpdateEnabled)
        assertEquals(false, settings.updateAvailable)
        assertEquals("", settings.latestRemoteVersion)
        assertEquals("", settings.latestReleaseNotes)
        assertEquals("", settings.dismissedUpdateVersion)
    }

    @Test
    fun updateAndBackupRestore_roundTripsSettingsValues() = runBlocking {
        val repository = createRepository()
        val updated = AppSettings(
            notificationsEnabled = false,
            reminderMinutes = 15,
            fluidCloudEnabled = false,
            themeMode = ThemeMode.DARK,
            dynamicColorEnabled = false,
            courseCellHeightDp = 84,
            showNonCurrentWeekCourses = true,
            autoCheckUpdateEnabled = false,
            updateAvailable = true,
            latestRemoteVersion = "v0.2.0",
            latestReleaseNotes = "## Changes\n- Added feature",
            latestApkDownloadUrl = "https://example.com/sleepin-universal.apk",
            latestReleasePageUrl = "https://github.com/Kurosu-Ti01/SleepIn/releases/tag/v0.2.0",
            dismissedUpdateVersion = "",
            lastUpdateCheckError = "",
            lastUpdateCheckAtMillis = 123456789L
        )

        repository.updateSettings(updated)
        val backup = repository.exportSettingsBackup()

        val secondRepository = createRepository()
        secondRepository.importSettingsBackup(backup)
        val restored = secondRepository.observeSettings().first()

        assertEquals(updated, restored)
    }

    private fun createRepository(): SettingsRepositoryImpl {
        val tempDir = Files.createTempDirectory("sleepin-settings-test")
        val dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = null,
            migrations = listOf(),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
            produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
        )

        runBlocking {
            dataStore.updateData { emptyPreferences() }
        }

        return SettingsRepositoryImpl(dataStore)
    }
}

