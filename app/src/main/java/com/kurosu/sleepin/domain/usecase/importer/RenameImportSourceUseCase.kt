package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.repository.ImportSourceRepository

/**
 * Gives a source a name of the user's own, or clears it again.
 *
 * Names live with the source rather than in settings because they are about that one source: deleting
 * the source should take its name with it, which only one storage can guarantee.
 *
 * Blank input is not an error — it means "go back to the derived label", so the caller does not have to
 * decide whether an empty field is a mistake or an intent.
 */
class RenameImportSourceUseCase(
    private val importSourceRepository: ImportSourceRepository
) {
    suspend operator fun invoke(sourceKey: String, displayName: String?): Boolean =
        importSourceRepository.renameSource(sourceKey, displayName)
}
