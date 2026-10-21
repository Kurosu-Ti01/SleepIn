package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.repository.ImportSourceRepository

/**
 * Forgets a source the user no longer wants.
 *
 * The presets it contributed disappear with it, and so do the scripts it assembled — which is why this
 * goes through the repository rather than deleting an entry from a list somewhere.
 *
 * Only sources the user added can be removed. The one the app manages has its own slot: it exists before
 * any import has happened, its address is a setting, and removing it would just bring it back on the next
 * refresh. Turning off automatic updates is the way to stop that one.
 */
class ForgetImportSourceUseCase(
    private val importSourceRepository: ImportSourceRepository
) {
    /** @return true when the source is gone afterwards. */
    suspend operator fun invoke(sourceKey: String): Boolean =
        importSourceRepository.forgetSource(sourceKey)
}
