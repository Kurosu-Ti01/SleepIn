package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Loads the script text of one preset so it can be run inside the import WebView.
 *
 * A null result means the preset cannot be used right now (its script is missing or unreadable), which
 * the caller reports instead of running nothing and leaving the user waiting.
 */
class ReadImportScriptUseCase(
    private val repository: ImportPresetRepository
) {
    suspend operator fun invoke(preset: ImportPreset): String? = repository.readScript(preset)
}
