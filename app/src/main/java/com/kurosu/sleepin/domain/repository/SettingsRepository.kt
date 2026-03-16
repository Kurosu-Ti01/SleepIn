package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Contract for reading and writing app-level preferences.
 *
 * This repository intentionally exposes a full settings snapshot instead of many small streams,
 * which keeps UI composition and backup/restore flows straightforward.
 */
interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun updateSettings(settings: AppSettings)
    suspend fun exportSettingsBackup(): String
    suspend fun importSettingsBackup(rawJson: String)
}

