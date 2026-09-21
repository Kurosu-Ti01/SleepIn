package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetIndexParseResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for the preset index contract.
 *
 * The index is data the app did not author — a published one is downloaded — and the path it names is
 * read and executed inside the WebView, so the path and identifier rules are part of the security
 * boundary rather than cosmetic validation.
 */
class ImportPresetIndexParserTest {

    private val parser = ImportPresetIndexParser()

    @Test
    fun parse_acceptsACompleteIndex() {
        val result = parser.parse(
            indexOf(presetJson(scriptSha256 = VALID_HASH)),
            ImportPresetSourceKind.BUNDLED
        )

        val index = (result as ImportPresetIndexParseResult.Parsed).index
        assertEquals(1, index.schemaVersion)
        assertEquals(3, index.indexVersion)

        val preset = index.presets.single()
        assertEquals("generic-blank", preset.id)
        assertEquals("演示导入", preset.name)
        assertEquals(ImportPresetGroup.GENERAL, preset.group)
        assertEquals("Y", preset.initial)
        assertEquals("presets/generic-blank/blank.js", preset.scriptPath)
        // Hashes are normalized so a comparison against a downloaded file cannot fail on casing.
        assertEquals(VALID_HASH.lowercase(), preset.scriptSha256)
        assertEquals(ImportPresetSourceKind.BUNDLED, preset.source)
    }

    @Test
    fun parse_treatsABlankStartUrlAsAbsent() {
        val result = parser.parse(indexOf(presetJson(startUrl = "")), ImportPresetSourceKind.BUNDLED)

        assertNull((result as ImportPresetIndexParseResult.Parsed).index.presets.single().startUrl)
    }

    @Test
    fun parse_acceptsAPresetWithoutAHash_soBundledIndicesStayUsable() {
        val result = parser.parse(indexOf(presetJson(scriptSha256 = null)), ImportPresetSourceKind.BUNDLED)

        assertTrue(result is ImportPresetIndexParseResult.Parsed)
    }

    @Test
    fun parse_rejectsBlankInput() {
        assertTrue(parser.parse("   ", ImportPresetSourceKind.BUNDLED) is ImportPresetIndexParseResult.Invalid)
    }

    @Test
    fun parse_rejectsMalformedJson() {
        assertTrue(
            parser.parse("{ not json", ImportPresetSourceKind.BUNDLED) is ImportPresetIndexParseResult.Invalid
        )
    }

    @Test
    fun parse_rejectsUnsupportedSchemaVersion() {
        val result = parser.parse(
            """{ "schemaVersion": 99, "indexVersion": 1, "presets": [] }""",
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("schemaVersion"))
    }

    @Test
    fun parse_rejectsMissingIndexVersion() {
        val result = parser.parse(
            """{ "schemaVersion": 1, "presets": [] }""",
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("indexVersion"))
    }

    @Test
    fun parse_rejectsDuplicatedPresetIds() {
        val result = parser.parse(
            indexOf(presetJson(), presetJson(name = "另一个")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("another preset"))
    }

    @Test
    fun parse_rejectsPresetIdThatIsNotASlug() {
        val result = parser.parse(
            indexOf(presetJson(id = "Generic_Blank")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("presets[0].id"))
    }

    @Test
    fun parse_rejectsInitialThatIsNotASingleUppercaseLetter() {
        val lowercase = parser.parse(indexOf(presetJson(initial = "y")), ImportPresetSourceKind.BUNDLED)
        val longer = parser.parse(indexOf(presetJson(initial = "Yan")), ImportPresetSourceKind.BUNDLED)

        assertTrue((lowercase as ImportPresetIndexParseResult.Invalid).message.contains("initial"))
        assertTrue((longer as ImportPresetIndexParseResult.Invalid).message.contains("initial"))
    }

    @Test
    fun parse_rejectsUnknownGroup() {
        val result = parser.parse(
            indexOf(presetJson(group = "DOCTORATE")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("group"))
    }

    @Test
    fun parse_rejectsBlankRequiredText() {
        val result = parser.parse(
            indexOf(presetJson(description = "  ")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("description"))
    }

    @Test
    fun parse_rejectsScriptThatEscapesIntoAnotherFolder() {
        val result = parser.parse(
            indexOf(presetJson(script = "presets/other/script.js")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("presets/generic-blank/"))
    }

    @Test
    fun parse_rejectsScriptPathWithTraversal() {
        val result = parser.parse(
            indexOf(presetJson(script = "presets/generic-blank/../../../shared_prefs/app.xml")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue(result is ImportPresetIndexParseResult.Invalid)
    }

    @Test
    fun parse_rejectsAbsoluteScriptPath() {
        val result = parser.parse(
            indexOf(presetJson(script = "/data/data/com.kurosu.sleepin/files/x.js")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue(result is ImportPresetIndexParseResult.Invalid)
    }

    @Test
    fun parse_rejectsMalformedScriptHash() {
        val result = parser.parse(
            indexOf(presetJson(scriptSha256 = "deadbeef")),
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("scriptSha256"))
    }

    @Test
    fun parse_requiresAScriptHashForDownloadedPresets() {
        val result = parser.parse(
            indexOf(presetJson(scriptSha256 = null)),
            ImportPresetSourceKind.REMOTE
        )

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("scriptSha256"))
    }

    @Test
    fun parse_rejectsScriptPathWithBackslashes() {
        // Written as raw JSON so the backslash survives decoding. Passing the path through the helper
        // would produce a single backslash in the JSON text, which is an invalid escape and would make
        // this pass because decoding failed rather than because the path was rejected.
        val json = """
            {
              "schemaVersion": 1,
              "indexVersion": 1,
              "presets": [
                { "id": "generic-blank", "name": "演示导入", "group": "GENERAL", "initial": "Y",
                  "startUrl": "", "script": "presets\\generic-blank\\blank.js",
                  "description": "说明", "maintainer": "SleepIn" }
              ]
            }
        """.trimIndent()

        val result = parser.parse(json, ImportPresetSourceKind.BUNDLED)

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("presets/generic-blank/"))
    }

    @Test
    fun parse_acceptsAnIndexWithoutAnyPresets() {
        val result = parser.parse(
            """{ "schemaVersion": 1, "indexVersion": 1 }""",
            ImportPresetSourceKind.BUNDLED
        )

        assertTrue((result as ImportPresetIndexParseResult.Parsed).index.presets.isEmpty())
    }

    @Test
    fun parse_acceptsTheIndexShippedInTheApp() {
        // Guards a packaging mistake nothing else would catch: a bundled index that fails to parse
        // leaves the preset list silently empty at runtime.
        val result = parser.parse(shippedIndexJson(), ImportPresetSourceKind.BUNDLED)

        val index = (result as ImportPresetIndexParseResult.Parsed).index
        assertTrue("the bundled index should ship at least one preset", index.presets.isNotEmpty())
        index.presets.forEach { preset ->
            assertEquals(ImportPresetSourceKind.BUNDLED, preset.source)
            assertTrue(
                "shipped preset ${preset.id} points at ${preset.scriptPath}",
                preset.scriptPath.startsWith("presets/${preset.id}/")
            )
        }
    }

    private fun shippedIndexJson(): String {
        val relativePath = "src/main/assets/import-scripts/index.json"
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("could not locate $relativePath starting from ${File("").absolutePath}")
    }

    private fun indexOf(vararg presets: String): String = """
        {
          "schemaVersion": 1,
          "indexVersion": 3,
          "presets": [ ${presets.joinToString(",")} ]
        }
    """.trimIndent()

    private companion object {
        val VALID_HASH: String = "0123456789ABCDEF".repeat(4)
    }

    private fun presetJson(
        id: String = "generic-blank",
        name: String = "演示导入",
        group: String = "GENERAL",
        initial: String = "Y",
        startUrl: String = "",
        script: String = "presets/generic-blank/blank.js",
        scriptSha256: String? = null,
        description: String = "用固定示例数据走完整个导入流程。",
        maintainer: String = "SleepIn"
    ): String = buildString {
        append("""{ "id": "$id", "name": "$name", "group": "$group", "initial": "$initial",""")
        append(""" "startUrl": "$startUrl", "script": "$script",""")
        if (scriptSha256 != null) append(""" "scriptSha256": "$scriptSha256",""")
        append(""" "description": "$description", "maintainer": "$maintainer" }""")
    }
}
