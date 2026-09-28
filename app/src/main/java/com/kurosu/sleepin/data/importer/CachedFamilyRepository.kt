package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyRequest
import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetAssemblyMode
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.repository.LocalFamilyRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The families a source import cached, reused to build a preset the user describes themselves.
 *
 * This runs the same assembly an import runs, with two differences: the runner and family files come
 * from the cache instead of a source, and the preset definition is what the user entered rather than
 * something an index declared. Sharing [ImportSourceAssembler] is deliberate — a preset built here and
 * one imported from a source must be assembled identically, or the same family would behave differently
 * depending on how the user got to it.
 */
internal class CachedFamilyRepository(
    private val sourceFileStore: ImportSourceFileStore,
    private val assembler: ImportSourceAssembler
) : LocalFamilyRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun availableFamilyIds(): List<String> {
        // Without the runner nothing can be assembled, so no family is offered.
        if (sourceFileStore.read(ImportSourceFileStore.runnerPath()) == null) return emptyList()

        return sourceFileStore.listDirectories(FAMILY_DIRECTORY).filter { familyId ->
            sourceFileStore.read(ImportSourceFileStore.familyMetadataPath(familyId)) != null &&
                sourceFileStore.read(ImportSourceFileStore.familyAdapterPath(familyId)) != null
        }
    }

    override suspend fun assembleScript(request: FamilyAssemblyRequest): FamilyAssemblyResult {
        val runner = sourceFileStore.read(ImportSourceFileStore.runnerPath())
            ?: return FamilyAssemblyResult.Failed("本地还没有缓存的运行流程，请先导入一个源。")
        val metadata = sourceFileStore.read(ImportSourceFileStore.familyMetadataPath(request.familyId))
            ?: return FamilyAssemblyResult.Failed("本地找不到族「${request.familyId}」的 family.json。")
        val adapter = sourceFileStore.read(ImportSourceFileStore.familyAdapterPath(request.familyId))
            ?: return FamilyAssemblyResult.Failed("本地找不到族「${request.familyId}」的 adapter.js。")

        val config = parseConfig(request.configJson)
            ?: return FamilyAssemblyResult.Failed("取值必须是合法的 JSON 对象，例如 {\"scheduleAction\":\"...\"}。")

        val overrideText = request.overrideText?.takeIf { it.isNotBlank() }
        val preset = ImportSourcePreset(
            id = CACHED_PRESET_ID,
            name = request.presetName,
            schoolId = null,
            schoolName = null,
            schoolCode = null,
            schoolAliases = emptyList(),
            // Only the assembly-relevant fields are real here; the rest exist because the assembler takes
            // the same preset shape a source import uses.
            group = ImportPresetGroup.GENERAL,
            initial = "A",
            startUrl = null,
            description = "",
            maintainer = "",
            assembly = if (overrideText == null) {
                ImportPresetAssemblyMode.FAMILY
            } else {
                ImportPresetAssemblyMode.OVERRIDE
            },
            familyId = request.familyId,
            config = config,
            override = null,
            rewrite = null
        )

        return assembler.assemble(
            runnerText = runner,
            familyMetadataText = metadata,
            adapterText = adapter,
            preset = preset,
            overrideText = overrideText,
            rewriteText = null
        ).fold(
            onSuccess = { script -> FamilyAssemblyResult.Assembled(script) },
            onFailure = { error -> FamilyAssemblyResult.Failed(error.message ?: "组装失败。") }
        )
    }

    /** Blank means "no overrides"; anything else has to be a JSON object. */
    private fun parseConfig(configJson: String): JsonObject? {
        val text = configJson.trim()
        if (text.isEmpty()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    private companion object {
        /** Placeholder id: the assembled script only reports it in a generated comment. */
        const val CACHED_PRESET_ID = "custom-preset"

        const val FAMILY_DIRECTORY = "families"
    }
}
