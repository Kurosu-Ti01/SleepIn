package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPresetAssemblyMode
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ImportSourceAssemblerTest {

    private val assembler = ImportSourceAssembler()
    private val preset = ImportSourcePreset(
        id = "sample-preset",
        name = "示例方案",
        schoolId = null,
        schoolName = null,
        schoolCode = null,
        schoolAliases = emptyList(),
        group = ImportPresetGroup.GENERAL,
        initial = "S",
        startUrl = null,
        description = "说明",
        maintainer = "tester",
        assembly = ImportPresetAssemblyMode.FAMILY,
        familyId = "sample-family",
        config = buildJsonObject { put("nested", buildJsonObject { put("value", "override") }) },
        override = null,
        rewrite = null
    )

    @Test
    fun familyAssembly_mergesConfigAndAppendsRunnerAdapter() {
        val script = assembler.assemble(
            runnerText = "const RUNNER_API_VERSION = 1;",
            familyMetadataText = """{"changePoints":{"nested":{"value":"default","keep":true},"base":1}}""",
            adapterText = "const familyAdapter = {};",
            preset = preset,
            overrideText = null,
            rewriteText = null
        ).getOrThrow()

        assertEquals(true, script.contains("const config = {\"nested\":{\"value\":\"override\",\"keep\":true},\"base\":1};"))
        assertEquals(true, script.contains("const familyAdapter = {};"))
        assertEquals(true, script.contains("await runImport({"))
    }

    @Test
    fun overrideAssembly_appendsOverrideSource() {
        val script = assembler.assemble(
            runnerText = "runner",
            familyMetadataText = "{\"changePoints\":{}}",
            adapterText = "adapter",
            preset = preset.copy(assembly = ImportPresetAssemblyMode.OVERRIDE),
            overrideText = "const presetOverrides = {};",
            rewriteText = null
        ).getOrThrow()

        assertEquals(true, script.contains("const presetOverrides = {};"))
    }

    @Test
    fun rewriteAssembly_returnsRewriteTextWithoutFamily() {
        val rewrite = "console.log('rewrite');"
        val result = assembler.assemble(
            runnerText = "ignored",
            familyMetadataText = null,
            adapterText = null,
            preset = preset.copy(assembly = ImportPresetAssemblyMode.REWRITE, familyId = null),
            overrideText = null,
            rewriteText = rewrite
        ).getOrThrow()

        assertEquals(rewrite, result)
    }
}
