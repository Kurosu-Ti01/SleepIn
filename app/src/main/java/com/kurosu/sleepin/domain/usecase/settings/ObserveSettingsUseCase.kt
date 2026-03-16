package com.kurosu.sleepin.domain.usecase.settings

import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Streams app settings as a single immutable snapshot.
 */
class ObserveSettingsUseCase(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> = repository.observeSettings()
}

