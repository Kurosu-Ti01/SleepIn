package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.repository.ImportSourceRepository

/**
 * Accepts a source the user picked and imports it.
 *
 * Only the shape of the user's input is checked here — does the link look like a link, did they actually
 * pick a directory. Whether the source itself is usable is decided by the repository, which reports both
 * cases as an [ImportSourceImportResult], so this layer never has to know a source's internals.
 */
class ImportSourceUseCase(
    private val repository: ImportSourceRepository
) {

    suspend operator fun invoke(draft: ImportSourceDraft): ImportSourceImportResult {
        val url = draft.indexUrl.trim()
        if (!url.startsWith("https://")) {
            return ImportSourceImportResult.Rejected("源链接必须以 https:// 开头。")
        }
        return repository.importSource(draft.copy(indexUrl = url))
    }

    /** Imports the source inside the directory the user selected. */
    suspend fun importFromDirectory(draft: ImportLocalSourceDraft): ImportSourceImportResult {
        val rootUri = draft.rootUri.trim()
        if (rootUri.isEmpty()) {
            return ImportSourceImportResult.Rejected("没有选择源目录。")
        }
        return repository.importLocalSource(draft.copy(rootUri = rootUri))
    }
}
