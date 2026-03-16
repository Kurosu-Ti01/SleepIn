package com.kurosu.sleepin.domain.usecase.settings

import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.repository.SettingsRepository

/**
 * Persists a full settings snapshot.
 *
 * The caller is responsible for creating the new immutable settings object, typically by copying
 * the current state and changing one field.
 */
class UpdateSettingsUseCase(
    private val repository: SettingsRepository
) {
    suspend operator fun invoke(settings: AppSettings) {
        repository.updateSettings(settings)
    }
}

