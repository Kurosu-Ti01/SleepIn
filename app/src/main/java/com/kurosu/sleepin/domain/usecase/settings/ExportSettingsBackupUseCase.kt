package com.kurosu.sleepin.domain.usecase.settings

import com.kurosu.sleepin.domain.repository.SettingsRepository

/**
 * Produces a JSON backup string containing all persisted settings values.
 */
class ExportSettingsBackupUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(): String = repository.exportSettingsBackup()
}

