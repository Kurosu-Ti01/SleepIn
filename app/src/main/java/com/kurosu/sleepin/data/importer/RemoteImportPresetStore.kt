package com.kurosu.sleepin.data.importer

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kurosu.sleepin.data.preferences.ImportPresetPreferenceStore
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.model.importer.RegisteredImportSource
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Persisted metadata for one remotely imported preset. */
@Serializable
internal data class RemotePresetDto(
    val id: String,
    val name: String,
    val group: String,
    val initial: String,
    val startUrl: String? = null,
    val description: String = "",
    val maintainer: String = "",
    val schoolId: String? = null,
    val schoolName: String? = null,
    val schoolCode: String? = null,
    val schoolAliases: List<String> = emptyList(),
    val scriptPath: String
)

/** Persisted metadata for one imported source URL. */
@Serializable
internal data class RemoteSourceDto(
    val sourceUrl: String,
    val indexVersion: Int,
    val presets: List<RemotePresetDto>,
    /**
     * Name the user gave this source, or null to show something derived from the address.
     *
     * Nullable and defaulted so sources registered before naming existed keep loading.
     */
    val displayName: String? = null
)

@Serializable
internal data class RemoteSourceListDto(
    val schemaVersion: Int = 1,
    val sources: List<RemoteSourceDto> = emptyList()
)

/**
 * Read-only preset source for presets assembled from an imported source.
 *
 * The source may be a remote address or a directory on the device (see [ImportedSourceImporter]). Both
 * look the same here: presets that can be read but not edited, which is why they are all reported as
 * [ImportPresetSourceKind.REMOTE]. Changing one means importing its source again.
 */
internal class RemoteImportPresetStore(
    private val dataStore: ImportPresetPreferenceStore,
    private val fileStore: ImportSourceFileStore
) : ImportPresetSource, ImportedPresetRegistry {

    override val kind: ImportPresetSourceKind = ImportPresetSourceKind.REMOTE

    override suspend fun presets(): List<ImportPreset> = readSources()
        .flatMap { source -> source.presets.mapNotNull { preset -> preset.toImportPresetOrNull() } }

    override suspend fun readScript(scriptPath: String): String? = fileStore.read(scriptPath)

    override suspend fun storedIndexVersion(sourceKey: String): Int? =
        readSources().firstOrNull { source -> source.sourceUrl == sourceKey }?.indexVersion

    override suspend fun replaceSource(
        sourceKey: String,
        indexVersion: Int,
        presets: List<RemotePresetDto>
    ): Boolean {
        val existing = readSources().filterNot { it.sourceUrl == sourceKey }
        return writeSources(
            existing + RemoteSourceDto(
                sourceUrl = sourceKey,
                indexVersion = indexVersion,
                presets = presets
            )
        )
    }

    override suspend fun forgetSource(sourceKey: String): Boolean {
        val remaining = readSources().filterNot { it.sourceUrl == sourceKey }
        return writeSources(remaining)
    }

    /** Gives a source a display name, or clears it when [displayName] is blank. */
    suspend fun renameSource(sourceKey: String, displayName: String?): Boolean {
        val cleaned = displayName?.trim()?.takeUnless { it.isEmpty() }
        val sources = readSources()
        if (sources.none { it.sourceUrl == sourceKey }) return false
        return writeSources(
            sources.map { source ->
                if (source.sourceUrl == sourceKey) source.copy(displayName = cleaned) else source
            }
        )
    }

    /** Every registered source, in the order they were first imported. */
    suspend fun registeredSources(): List<RegisteredImportSource> = readSources().map { source ->
        RegisteredImportSource(
            sourceKey = source.sourceUrl,
            indexVersion = source.indexVersion,
            presetCount = source.presets.size,
            displayName = source.displayName
        )
    }

    private suspend fun writeSources(sources: List<RemoteSourceDto>): Boolean = try {
        dataStore.edit { preferences ->
            preferences[KEY] = json.encodeToString(
                RemoteSourceListDto.serializer(),
                RemoteSourceListDto(sources = sources)
            )
        }
        true
    } catch (_: IOException) {
        false
    }

    private suspend fun readSources(): List<RemoteSourceDto> {
        val raw = try {
            dataStore.data.first()[KEY]
        } catch (_: IOException) {
            return emptyList()
        }
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(RemoteSourceListDto.serializer(), raw).sources
        } catch (_: SerializationException) {
            emptyList()
        }
    }

    private fun RemotePresetDto.toImportPresetOrNull(): ImportPreset? {
        if (!isValidPresetId(id)) return null
        val resolvedGroup = ImportPresetGroup.entries.firstOrNull { it.name == group } ?: return null
        if (!Regex("^[A-Z]$").matches(initial)) return null
        if (!isSafeRelativePath(scriptPath)) return null
        return ImportPreset(
            id = id,
            name = name,
            group = resolvedGroup,
            initial = initial,
            startUrl = startUrl,
            scriptPath = scriptPath,
            description = description,
            maintainer = maintainer,
            source = ImportPresetSourceKind.REMOTE,
            schoolId = schoolId,
            schoolName = schoolName,
            schoolCode = schoolCode,
            schoolAliases = schoolAliases
        )
    }

    private companion object {
        val KEY = stringPreferencesKey("remote_import_sources")
        val json = Json { ignoreUnknownKeys = true }
    }
}
