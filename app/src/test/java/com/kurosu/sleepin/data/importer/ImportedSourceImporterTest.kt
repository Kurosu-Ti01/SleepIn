package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for the rules every source import follows, whichever way the bytes arrive.
 *
 * The reader is a plain map of paths to text, so these cover the whole path — hash checks, family reuse,
 * assembly and registration — without a network, a device or DataStore. The reader also records what was
 * asked for, which is how the tests prove that a cached family was really reused instead of re-read.
 */
class ImportedSourceImporterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val registry = FakeRegistry()

    /**
     * Created in [setUp] rather than as a field initialiser: the temporary folder only exists once the
     * rule has run, which happens after the test class is constructed.
     */
    private lateinit var fileStore: ImportSourceFileStore

    @Before
    fun setUp() {
        fileStore = ImportSourceFileStore(File(temporaryFolder.root, "cache"))
    }

    private fun newImporter(
        registry: ImportedPresetRegistry = this.registry
    ): ImportedSourceImporter = ImportedSourceImporter(
        registry = registry,
        sourceFileStore = fileStore,
        indexParser = ImportSourceIndexParser(),
        assembler = ImportSourceAssembler()
    )

    @Test
    fun import_familySource_writesTheScriptAndRegistersThePreset() = runBlocking {
        val index = """
            {
              "schemaVersion": 2,
              "indexVersion": 7,
              "runner": ${fileEntry("lib/runner.js", RUNNER)},
              "families": [{
                "id": "fam",
                "metadata": ${fileEntry("families/fam/family.json", FAMILY_JSON)},
                "adapter": ${fileEntry("families/fam/adapter.js", ADAPTER)}
              }],
              "presets": [{
                "id": "preset-a", "name": "示例方案", "group": "GENERAL", "initial": "P",
                "assembly": "family", "family": "fam",
                "config": {"nested": {"value": "override"}},
                "description": "说明", "maintainer": "tester"
              }]
            }
        """.trimIndent()
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to index,
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to ADAPTER
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertEquals(ImportSourceImportResult.Imported(presetCount = 1, familyCount = 1), result)
        assertEquals(SOURCE_KEY, registry.sourceKey)
        assertEquals(7, registry.indexVersion)

        val stored = registry.presets.single()
        assertEquals("preset-a", stored.id)
        assertEquals("presets/$DIRECTORY_KEY/preset-a/script.js", stored.scriptPath)

        val script = fileStore.read(stored.scriptPath)
        assertTrue("组装结果应当带上族适配器", script!!.contains(ADAPTER))
        assertTrue("config 应当是默认值与方案取值的深合并", script.contains("\"nested\":{\"value\":\"override\",\"keep\":1}"))
        // Cached as well: the editor's "reuse a cached family" path needs the runner.
        assertEquals(RUNNER, fileStore.read(ImportSourceFileStore.runnerPath()))
    }

    @Test
    fun import_reusesTheCachedFamily_whenReuseIsAllowed() = runBlocking {
        // A cached family whose content differs from what the index declares, plus a reader that holds
        // *no* family files at all: succeeding therefore proves the cache was reused rather than read again.
        fileStore.write(ImportSourceFileStore.familyMetadataPath("fam"), CACHED_METADATA)
        fileStore.write(ImportSourceFileStore.familyAdapterPath("fam"), CACHED_ADAPTER)
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to familyIndex(),
            "lib/runner.js" to RUNNER
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = true)

        assertTrue(result is ImportSourceImportResult.Imported)
        val script = fileStore.read(registry.presets.single().scriptPath)!!
        assertTrue("应当用缓存里的适配器组装", script.contains(CACHED_ADAPTER))
        assertTrue("复用命中时不该去读源里的族文件", "families/fam/adapter.js" !in reader.requestedPaths)
    }

    @Test
    fun import_takesTheSourceFamily_whenReuseIsOff() = runBlocking {
        fileStore.write(ImportSourceFileStore.familyMetadataPath("fam"), CACHED_METADATA)
        fileStore.write(ImportSourceFileStore.familyAdapterPath("fam"), CACHED_ADAPTER)
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to familyIndex(),
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to ADAPTER
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertTrue(result is ImportSourceImportResult.Imported)
        val script = fileStore.read(registry.presets.single().scriptPath)!!
        assertTrue("应当用源里的适配器组装", script.contains(ADAPTER))

        // The cache is updated as well: otherwise the next import falls back to the version the user
        // just rejected.
        assertEquals(FAMILY_JSON, fileStore.read(ImportSourceFileStore.familyMetadataPath("fam")))
        assertEquals(ADAPTER, fileStore.read(ImportSourceFileStore.familyAdapterPath("fam")))
    }

    @Test
    fun import_rejectsAFileThatDoesNotMatchTheIndex() = runBlocking {
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to familyIndex(),
            // The index declares ADAPTER's hash, while the reader hands back different bytes.
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to "const familyAdapter = { tampered: true };\n"
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        val rejected = result as ImportSourceImportResult.Rejected
        assertTrue(rejected.message.contains("文件与索引不一致"))
        assertTrue("被拒绝时不该登记任何方案", registry.presets.isEmpty())
    }

    @Test
    fun import_rejectsASourceWithoutAnIndex() = runBlocking {
        val result = newImporter().import(FakeReader(), SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = true)

        assertEquals(
            ImportSourceImportResult.Rejected("源里找不到 $SOURCE_INDEX_FILE_NAME"),
            result
        )
    }

    @Test
    fun import_rewriteSource_usesTheScriptAsItIs() = runBlocking {
        val rewrite = "console.log('rewrite');\n"
        val index = """
            {
              "schemaVersion": 2,
              "indexVersion": 2,
              "runner": ${fileEntry("lib/runner.js", RUNNER)},
              "families": [],
              "presets": [{
                "id": "preset-r", "name": "重写方案", "group": "GENERAL", "initial": "R",
                "assembly": "rewrite",
                "rewrite": ${fileEntry("presets/preset-r/script.js", rewrite)},
                "description": "说明", "maintainer": "tester"
              }]
            }
        """.trimIndent()
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to index,
            "lib/runner.js" to RUNNER,
            "presets/preset-r/script.js" to rewrite
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = true)

        assertTrue(result is ImportSourceImportResult.Imported)
        assertEquals(rewrite, fileStore.read(registry.presets.single().scriptPath))
        // The runner is cached even though this import does not assemble with it: the editor needs one to
        // reuse any cached family, and the index always declares it.
        assertEquals(RUNNER, fileStore.read(ImportSourceFileStore.runnerPath()))
    }

    @Test
    fun import_familiesWithoutAnyPreset_leavesThemReusable() = runBlocking {
        // The state the family repository itself publishes in: families, no presets yet. Caching nothing
        // here is exactly what made every family unreachable in the editor.
        val index = """
            {
              "schemaVersion": 2,
              "indexVersion": 1,
              "runner": ${fileEntry("lib/runner.js", RUNNER)},
              "families": [{
                "id": "fam",
                "metadata": ${fileEntry("families/fam/family.json", FAMILY_JSON)},
                "adapter": ${fileEntry("families/fam/adapter.js", ADAPTER)}
              }],
              "presets": []
            }
        """.trimIndent()
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to index,
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to ADAPTER
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertEquals(ImportSourceImportResult.Imported(presetCount = 0, familyCount = 1), result)

        // What the editor asks for when it lists what can be reused.
        val reusable = CachedFamilyRepository(
            sourceFileStore = fileStore,
            assembler = ImportSourceAssembler()
        ).availableFamilyIds()
        assertEquals(listOf("fam"), reusable)
    }

    @Test
    fun import_cachesEveryDeclaredFamily_evenWhenNoPresetUsesIt() = runBlocking {
        val index = """
            {
              "schemaVersion": 2,
              "indexVersion": 3,
              "runner": ${fileEntry("lib/runner.js", RUNNER)},
              "families": [
                {"id": "used", "metadata": ${fileEntry("families/used/family.json", FAMILY_JSON)}, "adapter": ${fileEntry("families/used/adapter.js", ADAPTER)}},
                {"id": "unused", "metadata": ${fileEntry("families/unused/family.json", FAMILY_JSON)}, "adapter": ${fileEntry("families/unused/adapter.js", ADAPTER)}}
              ],
              "presets": [{
                "id": "preset-a", "name": "示例方案", "group": "GENERAL", "initial": "P",
                "assembly": "family", "family": "used",
                "description": "说明", "maintainer": "tester"
              }]
            }
        """.trimIndent()
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to index,
            "lib/runner.js" to RUNNER,
            "families/used/family.json" to FAMILY_JSON,
            "families/used/adapter.js" to ADAPTER,
            // Every declared family is cached, so a source that declares one has to ship it.
            "families/unused/family.json" to FAMILY_JSON,
            "families/unused/adapter.js" to ADAPTER
        )

        val result = newImporter().import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertEquals(ImportSourceImportResult.Imported(presetCount = 1, familyCount = 2), result)
        assertTrue(
            "声明的族都要缓存：编辑器就是靠这份缓存复用族的",
            "families/unused/adapter.js" in reader.requestedPaths
        )
        assertEquals(FAMILY_JSON, fileStore.read(ImportSourceFileStore.familyMetadataPath("unused")))
    }

    @Test
    fun import_skipsTheWork_whenTheIndexVersionHasNotMoved() = runBlocking {
        // The index itself has to be downloaded to know its version — there is no smaller way to ask a
        // source whether it changed — but everything after that is skipped: no family files, no
        // assembly, and no rewriting one script per preset.
        val registry = FakeRegistry(mapOf(SOURCE_KEY to 1))
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to familyIndex(),
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to ADAPTER
        )

        val result = newImporter(registry).import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertEquals(ImportSourceImportResult.Unchanged(1), result)
        assertEquals(listOf(SOURCE_INDEX_FILE_NAME), reader.requestedPaths)
        assertTrue("不该重新登记", registry.presets.isEmpty())
    }

    @Test
    fun import_importsAgain_whenTheIndexVersionMovesOn() = runBlocking {
        val registry = FakeRegistry(mapOf(SOURCE_KEY to 0))
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to familyIndex(),
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to ADAPTER
        )

        val result = newImporter(registry).import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertTrue(result is ImportSourceImportResult.Imported)
        assertEquals(1, registry.indexVersion)
    }

    @Test
    fun import_importsWhenAVersionMovedBackwards() = runBlocking {
        // A mirror serving an older index must still be applied: the version is only compared for
        // equality, never as "is it newer".
        val registry = FakeRegistry(mapOf(SOURCE_KEY to 99))
        val reader = FakeReader(
            SOURCE_INDEX_FILE_NAME to familyIndex(),
            "lib/runner.js" to RUNNER,
            "families/fam/family.json" to FAMILY_JSON,
            "families/fam/adapter.js" to ADAPTER
        )

        val result = newImporter(registry).import(reader, SOURCE_KEY, DIRECTORY_KEY, reuseLocalFamilies = false)

        assertTrue(result is ImportSourceImportResult.Imported)
        assertEquals(1, registry.indexVersion)
    }

    /** Index with one family and one preset that uses it, through the real family file paths. */
    private fun familyIndex(): String = """
        {
          "schemaVersion": 2,
          "indexVersion": 1,
          "runner": ${fileEntry("lib/runner.js", RUNNER)},
          "families": [{
            "id": "fam",
            "metadata": ${fileEntry("families/fam/family.json", FAMILY_JSON)},
            "adapter": ${fileEntry("families/fam/adapter.js", ADAPTER)}
          }],
          "presets": [{
            "id": "preset-a", "name": "示例方案", "group": "GENERAL", "initial": "P",
            "assembly": "family", "family": "fam",
            "description": "说明", "maintainer": "tester"
          }]
        }
    """.trimIndent()

    private fun fileEntry(path: String, text: String): String =
        """{"path":"$path","sha256":"${sha256(text)}"}"""

    private fun sha256(text: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private class FakeReader(vararg entries: Pair<String, String>) : ImportSourceReader {

        private val files = entries.toMap()

        /** Every path the importer asked for, in order; used to prove what was and was not read. */
        val requestedPaths = mutableListOf<String>()

        override suspend fun readIndex(): ByteArray? {
            requestedPaths += SOURCE_INDEX_FILE_NAME
            return files[SOURCE_INDEX_FILE_NAME]?.toByteArray(Charsets.UTF_8)
        }

        override suspend fun read(relativePath: String): ByteArray? {
            requestedPaths += relativePath
            return files[relativePath]?.toByteArray(Charsets.UTF_8)
        }
    }

    private class FakeRegistry(
        /** Version a previous import left behind, keyed by source. */
        private val storedVersions: Map<String, Int> = emptyMap()
    ) : ImportedPresetRegistry {

        var sourceKey: String? = null
        var indexVersion: Int = 0
        var presets: List<RemotePresetDto> = emptyList()
        var forgottenSourceKey: String? = null

        override suspend fun storedIndexVersion(sourceKey: String): Int? = storedVersions[sourceKey]

        override suspend fun replaceSource(
            sourceKey: String,
            indexVersion: Int,
            presets: List<RemotePresetDto>
        ): Boolean {
            this.sourceKey = sourceKey
            this.indexVersion = indexVersion
            this.presets = presets
            return true
        }

        override suspend fun forgetSource(sourceKey: String): Boolean {
            forgottenSourceKey = sourceKey
            return true
        }
    }

    private companion object {
        const val SOURCE_KEY = "local:content://source"
        const val DIRECTORY_KEY = "local-abc123"

        const val RUNNER = "function runImport() {}\n"
        const val FAMILY_JSON = """{"changePoints":{"nested":{"value":"default","keep":1}}}"""
        const val ADAPTER = "const familyAdapter = { source: true };\n"
        const val CACHED_METADATA = """{"changePoints":{"cached":true}}"""
        const val CACHED_ADAPTER = "const familyAdapter = { cached: true };\n"
    }
}
