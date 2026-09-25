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
            indexOf(presetJson(scriptSha256 = VALID_HASH, group = "GENERAL", initial = "Y")),
            ImportPresetSourceKind.BUNDLED
        )

        val index = (result as ImportPresetIndexParseResult.Parsed).index
        assertEquals(1, index.schemaVersion)
        assertEquals(3, index.indexVersion)

        val preset = index.presets.single()
        assertEquals("sample-preset", preset.id)
        assertEquals("Sample preset", preset.name)
        assertEquals(ImportPresetGroup.GENERAL, preset.group)
        assertEquals("Y", preset.initial)
        assertEquals("presets/sample-preset/script.js", preset.scriptPath)
        // Hashes are normalized so a comparison against a downloaded file cannot fail on casing.
        assertEquals(VALID_HASH.lowercase(), preset.scriptSha256)
        assertEquals(ImportPresetSourceKind.BUNDLED, preset.source)
        assertNull(preset.schoolId)
    }

    @Test
    fun parse_attachesSchoolMetadataToEachScript() {
        val json = """
            {
              "schemaVersion": 1,
              "indexVersion": 3,
              "schools": [{"id":"school-sample","name":"测试高校","code":"TEST-001","aliases":["测试简称"]}],
              "presets": [{"id":"school-sample-undergrad","name":"本科方案","schoolId":"school-sample","group":"UNDERGRADUATE",
                "initial":"S","startUrl":"https://portal.example.invalid","script":"presets/school-sample-undergrad/script.js",
                "description":"请先登录","maintainer":"SleepIn"}]
            }
        """.trimIndent()

        val index = (parser.parse(json, ImportPresetSourceKind.BUNDLED) as ImportPresetIndexParseResult.Parsed).index

        assertEquals("测试高校", index.schools.single().name)
        assertEquals("school-sample", index.presets.single().schoolId)
        assertEquals("TEST-001", index.presets.single().schoolCode)
        assertEquals(listOf("测试简称"), index.presets.single().schoolAliases)
    }

    @Test
    fun parse_rejectsPresetPointingToUnknownSchool() {
        val json = indexOf(
            presetJson(name = "Sample preset", schoolId = "missing")
        )

        val result = parser.parse(json, ImportPresetSourceKind.BUNDLED)

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("schoolId"))
    }

    @Test
    fun parse_rejectsDuplicateSchoolIds() {
        val json = """
            {"schemaVersion":1,"indexVersion":1,
             "schools":[{"id":"school-a","name":"甲校"},{"id":"school-a","name":"乙校"}],
             "presets":[]}
        """.trimIndent()

        val result = parser.parse(json, ImportPresetSourceKind.BUNDLED)

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("another school"))
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

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("presets/sample-preset/"))
    }

    @Test
    fun parse_rejectsScriptPathWithTraversal() {
        val result = parser.parse(
            indexOf(presetJson(script = "presets/sample-preset/../../../shared_prefs/app.xml")),
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
                { "id": "sample-preset", "name": "Sample preset", "group": "GENERAL", "initial": "S",
                  "startUrl": "", "script": "presets\\sample-preset\\script.js",
                  "description": "Sample description", "maintainer": "SleepIn" }
              ]
            }
        """.trimIndent()

        val result = parser.parse(json, ImportPresetSourceKind.BUNDLED)

        assertTrue((result as ImportPresetIndexParseResult.Invalid).message.contains("presets/sample-preset/"))
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
    fun parse_acceptsTheEmptyIndexShippedInTheApp() {
        // School-specific presets are added only after they are reviewed and verified.
        val result = parser.parse(shippedIndexJson(), ImportPresetSourceKind.BUNDLED)

        val index = (result as ImportPresetIndexParseResult.Parsed).index
        assertTrue("the bundled index is intentionally empty until a preset is verified", index.presets.isEmpty())
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
        id: String = "sample-preset",
        name: String = "Sample preset",
        group: String = "GENERAL",
        initial: String = "Y",
        startUrl: String = "",
        script: String = "presets/sample-preset/script.js",
        scriptSha256: String? = null,
        description: String = "Sample import preset.",
        schoolId: String? = null,
        maintainer: String = "SleepIn"
    ): String = buildString {
        append("""{ "id": "$id", "name": "$name", "group": "$group", "initial": "$initial",""")
        if (schoolId != null) append(""" "schoolId": "$schoolId",""")
        append(""" "startUrl": "$startUrl", "script": "$script",""")
        if (scriptSha256 != null) append(""" "scriptSha256": "$scriptSha256",""")
        append(""" "description": "$description", "maintainer": "$maintainer" }""")
    }
}
