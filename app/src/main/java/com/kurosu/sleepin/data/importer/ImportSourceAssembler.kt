package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPresetAssemblyMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Builds the executable WebView script from a source index's parts.
 *
 * This is deliberately independent of Android and networking. It mirrors the preset repository's
 * PowerShell generator: the runner and family adapter are shared, family change points are defaults,
 * the school config replaces only the declared values, and an override is appended last.
 */
class ImportSourceAssembler {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return a self-contained script suitable for the existing import WebView.
     */
    fun assemble(
        runnerText: String,
        familyMetadataText: String?,
        adapterText: String?,
        preset: ImportSourcePreset,
        overrideText: String?,
        rewriteText: String?
    ): Result<String> = runCatching {
        if (preset.assembly == ImportPresetAssemblyMode.REWRITE) {
            require(!rewriteText.isNullOrBlank()) { "重写方案缺少完整脚本" }
            return@runCatching rewriteText
        }

        require(runnerText.isNotBlank()) { "族方案缺少 runner.js" }
        require(!familyMetadataText.isNullOrBlank()) { "族方案缺少 family.json" }
        require(!adapterText.isNullOrBlank()) { "族方案缺少 adapter.js" }

        val familyJson = json.parseToJsonElement(familyMetadataText).jsonObject
        val defaults = familyJson["changePoints"]?.jsonObject
            ?: error("family.json 缺少 changePoints")
        val mergedConfig = mergeObjects(defaults, preset.config)
        val configJson = json.encodeToString(JsonObject.serializer(), mergedConfig)

        buildString {
            appendLine("// 本文件由 SleepIn 在设备上组装生成，请勿手改。")
            appendLine("// 方案：${preset.id}")
            appendLine("// 族：${preset.familyId.orEmpty()}")
            appendLine("(async () => {")
            appendLine("  const config = $configJson;")
            appendLine(runnerText.trimEnd())
            appendLine()
            appendLine(adapterText.trimEnd())
            if (preset.assembly == ImportPresetAssemblyMode.OVERRIDE) {
                require(!overrideText.isNullOrBlank()) { "覆盖方案缺少 override.js" }
                appendLine()
                appendLine(overrideText.trimEnd())
            }
            appendLine()
            appendLine("  await runImport({")
            appendLine("    adapter: familyAdapter,")
            appendLine("    overrides: typeof presetOverrides === 'undefined' ? {} : presetOverrides,")
            appendLine("    config: config,")
            appendLine("    presetName: ${json.encodeToString(preset.name)}")
            appendLine("  });")
            appendLine("})();")
        }
    }

    private fun mergeObjects(defaults: JsonObject, overrides: JsonObject): JsonObject = buildJsonObject {
        defaults.forEach { (key, defaultValue) ->
            put(key, defaultValue)
        }
        overrides.forEach { (key, overrideValue) ->
            val defaultValue = defaults[key]
            if (defaultValue is JsonObject && overrideValue is JsonObject) {
                put(key, mergeObjects(defaultValue, overrideValue))
            } else {
                put(key, overrideValue)
            }
        }
    }
}
