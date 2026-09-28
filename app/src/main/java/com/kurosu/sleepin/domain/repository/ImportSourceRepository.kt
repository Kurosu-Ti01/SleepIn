package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult

/**
 * Imports a source and makes its files available to the preset repository.
 *
 * A source can come over the network or from a directory on the device; both go through the same
 * verification and assembly rules, so only where the bytes come from differs.
 *
 * Nothing is contacted or read until a user explicitly asks for a source: the app configures none.
 */
interface ImportSourceRepository {

    /** Imports the remote source [draft] points at. */
    suspend fun importSource(draft: ImportSourceDraft): ImportSourceImportResult

    /** Imports the source found in the directory [draft] refers to. */
    suspend fun importLocalSource(draft: ImportLocalSourceDraft): ImportSourceImportResult
}
