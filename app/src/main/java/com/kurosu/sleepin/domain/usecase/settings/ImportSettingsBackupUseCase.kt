package com.kurosu.sleepin.domain.usecase.settings

import com.kurosu.sleepin.domain.repository.SettingsRepository

/**
 * Restores settings from a previously exported JSON backup string.
 */
class ImportSettingsBackupUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(rawJson: String) {
        repository.importSettingsBackup(rawJson)
    }
}

