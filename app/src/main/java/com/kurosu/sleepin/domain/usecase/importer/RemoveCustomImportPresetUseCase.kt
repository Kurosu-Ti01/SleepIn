package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Deletes one of the user's own presets.
 *
 * Nothing else in the app may be removed this way: bundled presets live in the APK and a downloaded
 * index is replaced as a whole, so "remove" only ever means "this one is mine and I no longer want it".
 */
class RemoveCustomImportPresetUseCase(
    private val repository: ImportPresetRepository
) {

    /**
     * @return true when the preset is gone, including when it was already absent — which is the
     *   outcome the caller wanted either way.
     */
    suspend operator fun invoke(presetId: String): Boolean = repository.removeCustomPreset(presetId)
}
