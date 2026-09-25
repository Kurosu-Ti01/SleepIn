package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Changes a preset the user added earlier.
 *
 * The stored preset is not edited in place. A preset's id is also the folder its script lives in, and the
 * storage layer only ever creates a preset with an id it allocates itself, so the shape of the change is
 * "store the new version, then drop the old one".
 *
 * The order matters and is not interchangeable: the new preset is written **first**, so a failure leaves
 * the user with the preset they already had rather than with nothing. The reverse order would delete
 * their preset and then fail to save the replacement.
 *
 * A consequence worth knowing: if the deletion fails, the user ends up with both versions listed. That is
 * recoverable by deleting one of them, which is much better than the alternative.
 */
class UpdateCustomImportPresetUseCase(
    private val repository: ImportPresetRepository
) {

    /**
     * @param previousPresetId the preset being replaced, which the user is no longer editing.
     */
    suspend operator fun invoke(
        previousPresetId: String,
        draft: CustomImportPresetDraft
    ): ImportPresetAddResult {
        // Validated here as well as on the way in: replacing accepts exactly what adding does, and a
        // rule that only holds on one of two paths is not a rule.
        val rejection = CustomImportPresetValidation.rejectionMessage(draft)
        if (rejection != null) return ImportPresetAddResult.Rejected(rejection)

        val added = repository.addCustomPreset(draft)
        if (added !is ImportPresetAddResult.Added) return added

        repository.removeCustomPreset(previousPresetId)
        return added
    }
}
