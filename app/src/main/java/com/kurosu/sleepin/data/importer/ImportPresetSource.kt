package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind

/**
 * One place import presets can come from.
 *
 * The repository merges sources instead of knowing about each kind, so adding a source — a user's own
 * script folder, a source imported from a link or a directory — is a matter of constructing it and
 * adding it to the list, with no change to the repository or the screens.
 *
 * Implementations must not throw for "unavailable" conditions: a source that cannot be read simply
 * reports nothing, so one broken source cannot take the whole list down with it.
 */
interface ImportPresetSource {

    val kind: ImportPresetSourceKind

    /** Presets this source currently provides; empty when it has nothing to offer. */
    suspend fun presets(): List<ImportPreset>

    /**
     * Text of the script at [scriptPath], or null when it is missing or the path is not acceptable.
     *
     * [scriptPath] comes from a parsed index or a stored preset, so it has already been validated; it
     * is re-checked here because this is the step that actually resolves it to a file.
     */
    suspend fun readScript(scriptPath: String): String?
}

/**
 * A source the user can add presets to and remove them from.
 *
 * Only the user's own presets are writable: an imported source is replaced wholesale when it is
 * imported again, and has no per-preset edit to offer. Keeping the writable half a separate interface
 * means a read-only source cannot be handed to the repository by mistake.
 */
interface MutableImportPresetSource : ImportPresetSource {

    /**
     * Stores [draft] as a new preset.
     *
     * The implementation allocates the id, because only it can see which ids are already taken.
     */
    suspend fun add(draft: CustomImportPresetDraft): ImportPresetAddResult

    /**
     * Removes the preset identified by [presetId].
     *
     * @return true when the preset is gone, including when it was not there to begin with.
     */
    suspend fun remove(presetId: String): Boolean
}
