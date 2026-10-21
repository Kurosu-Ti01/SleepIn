package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.repository.ImportSourceRepository

/**
 * Re-points a source the user added at another address.
 *
 * Only sources the user added can be re-pointed. The one the app manages is a fixed address, so it has
 * neither an editor nor a way to break it — which also means there is no "restore default" to offer.
 *
 * The new address is imported **first**. A typo then leaves the source the user already had exactly as it
 * was, instead of trading a working source for nothing; only a successful import makes the old
 * registration obsolete.
 */
class ChangeImportSourceUrlUseCase(
    private val importSourceRepository: ImportSourceRepository
) {

    suspend operator fun invoke(
        previousSourceKey: String,
        newIndexUrl: String
    ): ImportSourceImportResult {
        val trimmed = newIndexUrl.trim()
        if (!trimmed.startsWith("https://")) {
            return ImportSourceImportResult.Rejected("源链接必须以 https:// 开头。")
        }

        val result = importSourceRepository.importSource(
            // The address being added is the authoritative copy of its own families. Reusing the cache
            // here would keep an adapter this source has just replaced, which is the opposite of what
            // someone changing an address is trying to achieve.
            ImportSourceDraft(indexUrl = trimmed, reuseLocalFamilies = false)
        )
        val registered = result is ImportSourceImportResult.Imported ||
            result is ImportSourceImportResult.Unchanged
        if (!registered) return result

        // Same address: the import above already replaced it, and forgetting it now would delete what was
        // just written.
        if (previousSourceKey != trimmed) {
            importSourceRepository.forgetSource(previousSourceKey)
        }
        return result
    }
}
