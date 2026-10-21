package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.model.importer.RegisteredImportSource

/**
 * Imports a source and makes its files available to the preset repository.
 *
 * A source can come over the network or from a directory on the device; both go through the same
 * verification and assembly rules, so only where the bytes come from differs.
 *
 * Apart from the one the app manages, nothing is contacted or read until a user asks for it.
 */
interface ImportSourceRepository {

    /** Imports the remote source [draft] points at. */
    suspend fun importSource(draft: ImportSourceDraft): ImportSourceImportResult

    /** Imports the source found in the directory [draft] refers to. */
    suspend fun importLocalSource(draft: ImportLocalSourceDraft): ImportSourceImportResult

    /**
     * Sources this device has already imported, so the settings screen can show, rename and remove them.
     *
     * Sources are described here rather than in a use case because only the storage knows which ones
     * exist; deciding what to do with them belongs to the use cases.
     */
    suspend fun registeredSources(): List<RegisteredImportSource>

    /**
     * Gives [sourceKey] a display name, or clears it when [displayName] is null or blank.
     *
     * Renaming is deliberately separate from importing: a name is the user's own organisation of their
     * sources, and making them re-download a source to change one would be absurd.
     *
     * @return true when the name was stored.
     */
    suspend fun renameSource(sourceKey: String, displayName: String?): Boolean

    /**
     * Forgets [sourceKey] and deletes the scripts it assembled.
     *
     * The presets it contributed disappear with it. [sourceKey] can no longer be refreshed afterwards,
     * which is why re-pointing a source imports the new address *before* forgetting the old one.
     *
     * @return true when nothing is registered under [sourceKey] afterwards.
     */
    suspend fun forgetSource(sourceKey: String): Boolean
}
