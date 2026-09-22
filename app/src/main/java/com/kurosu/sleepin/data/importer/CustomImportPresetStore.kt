package com.kurosu.sleepin.data.importer

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kurosu.sleepin.data.preferences.ImportPresetPreferenceStore
import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import java.io.IOException
import kotlinx.coroutines.flow.first

/**
 * The presets a user added themselves.
 *
 * This is the writable source: metadata lives in a DataStore document, and each preset's script lives
 * in its own file under [ImportPresetFileStore]. Splitting them that way means adding a preset rewrites
 * one small metadata document rather than re-encoding every script the user has ever added.
 *
 * Ordering of writes is deliberate in both directions:
 * - adding writes the script first, so a stored entry never points at a missing file;
 * - removing drops the metadata first, so a failed file deletion leaves something invisible rather
 *   than a listed preset that cannot run.
 */
class CustomImportPresetStore(
    private val dataStore: ImportPresetPreferenceStore,
    private val fileStore: ImportPresetFileStore
) : MutableImportPresetSource {

    override val kind: ImportPresetSourceKind = ImportPresetSourceKind.CUSTOM

    override suspend fun presets(): List<ImportPreset> {
        val decoded = readMetadata()
        if (decoded !is CustomImportPresetDecodeResult.Decoded) return emptyList()
        // Entries that cannot be used are skipped one by one rather than failing the list: a single
        // damaged record must not hide every other preset the user added.
        return decoded.presets.mapNotNull { dto -> dto.toImportPresetOrNull() }
    }

    override suspend fun readScript(scriptPath: String): String? = fileStore.readScript(scriptPath)

    override suspend fun add(draft: CustomImportPresetDraft): ImportPresetAddResult {
        val existing = when (val decoded = readMetadata()) {
            is CustomImportPresetDecodeResult.Decoded -> decoded.presets
            CustomImportPresetDecodeResult.Empty -> emptyList()
            CustomImportPresetDecodeResult.UnsupportedVersion -> return ImportPresetAddResult.Failed(
                "本机保存的导入方案来自更新版本的应用，当前版本无法修改。请更新应用后重试。"
            )
        }

        val taken = existing.mapTo(mutableSetOf()) { dto -> dto.id }
        val id = generateCustomImportPresetId(taken)

        if (!fileStore.writeScript(id, draft.scriptText)) {
            return ImportPresetAddResult.Failed("无法保存脚本文件，请检查存储空间后重试。")
        }

        val dto = CustomImportPresetDto(
            id = id,
            name = draft.name.trim(),
            group = draft.group.name,
            initial = draft.initial.trim().uppercase(),
            startUrl = draft.startUrl.trim(),
            description = draft.description.trim(),
            maintainer = draft.maintainer.trim()
        )
        if (!writeMetadata(existing + dto)) {
            // The script is already on disk; leaving it would waste space and could confuse a later
            // read that finds a file with no entry pointing at it.
            fileStore.deletePreset(id)
            return ImportPresetAddResult.Failed("无法保存导入方案信息，请重试。")
        }

        val preset = dto.toImportPresetOrNull()
            ?: return ImportPresetAddResult.Failed("保存的导入方案无法读取。")

        return ImportPresetAddResult.Added(preset)
    }

    override suspend fun remove(presetId: String): Boolean {
        val existing = when (val decoded = readMetadata()) {
            is CustomImportPresetDecodeResult.Decoded -> decoded.presets
            CustomImportPresetDecodeResult.Empty -> return true
            CustomImportPresetDecodeResult.UnsupportedVersion -> return false
        }

        val remaining = existing.filterNot { dto -> dto.id == presetId }
        if (remaining.size == existing.size) return true
        if (!writeMetadata(remaining)) return false

        fileStore.deletePreset(presetId)
        return true
    }

    private suspend fun readMetadata(): CustomImportPresetDecodeResult {
        val raw = try {
            dataStore.data.first()[KEY]
        } catch (_: IOException) {
            // Storage that cannot be read is reported as "nothing stored": the list stays usable, and a
            // later write gets the chance to replace the damaged document.
            return CustomImportPresetDecodeResult.Empty
        }
        return CustomImportPresetCodec.decode(raw)
    }

    private suspend fun writeMetadata(presets: List<CustomImportPresetDto>): Boolean = try {
        dataStore.edit { preferences -> preferences[KEY] = CustomImportPresetCodec.encode(presets) }
        true
    } catch (_: IOException) {
        false
    }

    private companion object {
        /**
         * Whole-list key. One value rather than one key per preset because the list is small, it is
         * only read when the preset list is shown, and a single value makes an update atomic.
         */
        val KEY = stringPreferencesKey("custom_presets")
    }
}
