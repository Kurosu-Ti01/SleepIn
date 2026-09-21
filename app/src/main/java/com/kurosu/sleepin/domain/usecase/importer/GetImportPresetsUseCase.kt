package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Lists the import presets available on this device, already ordered for display.
 */
class GetImportPresetsUseCase(
    private val repository: ImportPresetRepository
) {
    suspend operator fun invoke(): List<ImportPreset> = repository.getPresets()
}
