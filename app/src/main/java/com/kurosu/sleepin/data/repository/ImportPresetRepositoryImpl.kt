package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.data.importer.ImportPresetSource
import com.kurosu.sleepin.data.importer.MutableImportPresetSource
import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.repository.ImportPresetRepository

/**
 * Merges every preset source into the single list the screens work with.
 *
 * Sources are consulted in the order they are given, and a later source replaces an earlier preset
 * with the same id. That ordering is what lets a user's own preset override the bundled one, and it
 * will let a downloaded preset override both, without the app having to rewrite any index.
 *
 * Because a preset records only the kind of source it came from, at most one source per kind may be
 * registered: with two, a preset would be read through whichever one happened to come first.
 *
 * @param customPresets the one writable source. Separate from [sources] because adding and removing
 *   only ever applies to the user's own presets, and passing it explicitly makes that visible here
 *   instead of leaving it to be discovered by searching the list for a writable entry.
 */
class ImportPresetRepositoryImpl(
    private val sources: List<ImportPresetSource>,
    private val customPresets: MutableImportPresetSource
) : ImportPresetRepository {

    override suspend fun getPresets(): List<ImportPreset> {
        // LinkedHashMap keeps the first position a preset was seen at, so an override replaces the
        // definition without reshuffling the list.
        val presetsById = LinkedHashMap<String, ImportPreset>()
        sources.forEach { source ->
            source.presets().forEach { preset -> presetsById[preset.id] = preset }
        }

        return presetsById.values.sortedWith(
            compareBy({ preset -> preset.group.ordinal }, { preset -> preset.initial }, { preset -> preset.name })
        )
    }

    override suspend fun readScript(preset: ImportPreset): String? =
        sources.firstOrNull { source -> source.kind == preset.source }?.readScript(preset.scriptPath)

    override suspend fun addCustomPreset(draft: CustomImportPresetDraft): ImportPresetAddResult =
        customPresets.add(draft)

    override suspend fun removeCustomPreset(presetId: String): Boolean =
        customPresets.remove(presetId)
}
