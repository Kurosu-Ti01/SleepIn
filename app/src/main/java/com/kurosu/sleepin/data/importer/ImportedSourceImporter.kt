package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import java.security.MessageDigest

/**
 * Where imported presets get recorded.
 *
 * Extracted purely so the import flow can run end to end in a JVM test without standing up DataStore.
 */
internal interface ImportedPresetRegistry {

    /**
     * Version of the index that was last imported for [sourceKey], or null when it has never been.
     *
     * The importer compares it with the version it just read: an unchanged version means the copy on this
     * device is current, so nothing has to be downloaded, assembled or written.
     */
    suspend fun storedIndexVersion(sourceKey: String): Int?

    /** Replaces whatever [sourceKey] had registered before with [presets]. */
    suspend fun replaceSource(
        sourceKey: String,
        indexVersion: Int,
        presets: List<RemotePresetDto>
    ): Boolean

    /** Forgets [sourceKey] entirely. Returns true when nothing is registered under it afterwards. */
    suspend fun forgetSource(sourceKey: String): Boolean
}

/**
 * Turns one source (remote or local) into presets the app can run.
 *
 * This is the whole rule set for an import, and it does not care where the bytes came from:
 * read the index, verify every file's hash, take the families (reusing the cache when allowed), assemble,
 * write each script, register the result.
 *
 * Because the rules exist once, a local import cannot end up skipping a check that a remote import does.
 */
internal class ImportedSourceImporter(
    private val registry: ImportedPresetRegistry,
    private val sourceFileStore: ImportSourceFileStore,
    private val indexParser: ImportSourceIndexParser,
    private val assembler: ImportSourceAssembler
) {

    /**
     * @param sourceKey identifies the source; importing the same source again replaces its presets.
     * @param directoryKey directory prefix for the assembled scripts, keeping sources apart.
     */
    suspend fun import(
        reader: ImportSourceReader,
        sourceKey: String,
        directoryKey: String,
        reuseLocalFamilies: Boolean
    ): ImportSourceImportResult {
        return try {
            val indexBytes = reader.readIndex() ?: reject("源里找不到 $SOURCE_INDEX_FILE_NAME")
            val index = indexParser.parse(indexBytes.toString(Charsets.UTF_8))
                .getOrElse { reject(it.message ?: "源索引无效") }

            // The publisher's generator bumps indexVersion whenever the published bytes change, and CI
            // fails when a generated index is stale. An equal version therefore means this device already
            // holds the current release, and the rest of this method would download ~120 KB, assemble one
            // script per preset and write them all back unchanged.
            val storedVersion = registry.storedIndexVersion(sourceKey)
            if (storedVersion != null && storedVersion == index.indexVersion) {
                return ImportSourceImportResult.Unchanged(index.indexVersion)
            }

            // The runner and *every* declared family are cached, which is more than this import strictly
            // needs: the cache is what the editor's "reuse a cached family" option reads, so a source that
            // publishes families before it has any preset still has to leave them usable. Caching the
            // runner is part of the same promise — without it no cached family could be assembled.
            val runnerText = readVerified(reader, index.runner.path, index.runner.sha256).also { text ->
                // A failed cache write is deliberately not fatal: this import already succeeded, and only
                // that optional path becomes unavailable (and [availableFamilyIds] reports it by then).
                sourceFileStore.write(ImportSourceFileStore.runnerPath(), text)
            }
            val families = loadFamilies(
                reader = reader,
                families = index.families,
                reuseLocalFamilies = reuseLocalFamilies
            )

            val presets = index.presets.map { preset ->
                storePreset(reader, preset, runnerText, families, directoryKey)
            }

            require(registry.replaceSource(sourceKey, index.indexVersion, presets)) {
                "无法保存导入的方案索引"
            }
            ImportSourceImportResult.Imported(
                presetCount = presets.size,
                familyCount = index.families.size
            )
        } catch (error: IllegalArgumentException) {
            ImportSourceImportResult.Rejected(error.message ?: "源内容不符合要求。")
        } catch (error: Exception) {
            ImportSourceImportResult.Failed(error.message ?: "导入源失败。")
        }
    }

    /**
     * Loads the family files.
     *
     * With reuse allowed, a cached family wins. Otherwise the source wins and the cache is overwritten:
     * switching reuse off means "take what this source has", so leaving the old copy in place would make
     * the next import silently fall back to the version the user just rejected.
     */
    private suspend fun loadFamilies(
        reader: ImportSourceReader,
        families: List<ImportSourceFamily>,
        reuseLocalFamilies: Boolean
    ): Map<String, Pair<String, String>> {
        val loaded = mutableMapOf<String, Pair<String, String>>()
        for (family in families) {
            val cached = if (reuseLocalFamilies) cachedFamily(family.id) else null
            loaded[family.id] = cached ?: run {
                val metadata = readVerified(reader, family.metadata.path, family.metadata.sha256)
                val adapter = readVerified(reader, family.adapter.path, family.adapter.sha256)
                require(sourceFileStore.write(ImportSourceFileStore.familyMetadataPath(family.id), metadata)) {
                    "无法缓存族 ${family.id} 的 family.json"
                }
                require(sourceFileStore.write(ImportSourceFileStore.familyAdapterPath(family.id), adapter)) {
                    "无法缓存族 ${family.id} 的 adapter.js"
                }
                metadata to adapter
            }
        }
        return loaded
    }

    private suspend fun cachedFamily(familyId: String): Pair<String, String>? {
        val metadata = sourceFileStore.read(ImportSourceFileStore.familyMetadataPath(familyId)) ?: return null
        val adapter = sourceFileStore.read(ImportSourceFileStore.familyAdapterPath(familyId)) ?: return null
        return metadata to adapter
    }

    /** Assembles one preset and writes it as that preset's own script. */
    private suspend fun storePreset(
        reader: ImportSourceReader,
        preset: ImportSourcePreset,
        runnerText: String,
        families: Map<String, Pair<String, String>>,
        directoryKey: String
    ): RemotePresetDto {
        val familyTexts = preset.familyId?.let(families::get)
        val overrideText = preset.override?.let { file -> readVerified(reader, file.path, file.sha256) }
        val rewriteText = preset.rewrite?.let { file -> readVerified(reader, file.path, file.sha256) }

        val script = assembler.assemble(
            runnerText = runnerText,
            familyMetadataText = familyTexts?.first,
            adapterText = familyTexts?.second,
            preset = preset,
            overrideText = overrideText,
            rewriteText = rewriteText
        ).getOrElse { reject(it.message ?: "方案组装失败") }

        val scriptPath = "presets/$directoryKey/${preset.id}/script.js"
        require(sourceFileStore.write(scriptPath, script)) {
            "无法保存方案 ${preset.id} 的最终脚本"
        }

        return RemotePresetDto(
            id = preset.id,
            name = preset.name,
            group = preset.group.name,
            initial = preset.initial,
            startUrl = preset.startUrl,
            description = preset.description,
            maintainer = preset.maintainer,
            schoolId = preset.schoolId,
            schoolName = preset.schoolName,
            schoolCode = preset.schoolCode,
            schoolAliases = preset.schoolAliases,
            scriptPath = scriptPath
        )
    }

    /**
     * Reads one source file and checks it against the hash the index declares.
     *
     * A local import is verified too. Without it, editing a script and forgetting to regenerate the index
     * would silently assemble something other than what the index describes, while the user believes they
     * just installed their latest edit.
     */
    private suspend fun readVerified(
        reader: ImportSourceReader,
        path: String,
        expectedSha256: String
    ): String {
        val bytes = reader.read(path) ?: reject("源文件缺失：$path")
        require(sha256(bytes) == expectedSha256.lowercase()) {
            "文件与索引不一致：$path（改过文件后需要重新生成源索引）"
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /** Problems the user can fix are reported as Rejected rather than folded into an unknown failure. */
    private fun reject(message: String): Nothing = throw IllegalArgumentException(message)

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
