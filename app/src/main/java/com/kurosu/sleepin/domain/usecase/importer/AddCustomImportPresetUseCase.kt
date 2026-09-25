package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Validates and stores a preset the user added themselves.
 *
 * This is the only way a new custom preset enters the app, so every rule about what one may contain
 * passes through here. The storage layer stores what it is given; the form asks for it; neither decides
 * what is acceptable.
 */
class AddCustomImportPresetUseCase(
    private val repository: ImportPresetRepository
) {

    /**
     * @return [ImportPresetAddResult.Added] with the stored preset, or a rejection/failure carrying a
     *   message the screen can show as-is.
     */
    suspend operator fun invoke(draft: CustomImportPresetDraft): ImportPresetAddResult {
        val rejection = CustomImportPresetValidation.rejectionMessage(draft)
        if (rejection != null) return ImportPresetAddResult.Rejected(rejection)
        return repository.addCustomPreset(draft)
    }
}
