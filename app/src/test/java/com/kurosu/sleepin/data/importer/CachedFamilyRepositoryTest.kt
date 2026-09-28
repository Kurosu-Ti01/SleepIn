package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyRequest
import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for reusing a family the device cached, which is how the user's own preset can be as small as
 * the values that differ from the family.
 *
 * The cache is a real directory here, so these also pin down what counts as "reusable": a family whose
 * files are present *and* a runner to assemble it with. Offering a family that cannot be assembled would
 * only fail after the user had committed to it.
 */
class CachedFamilyRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var fileStore: ImportSourceFileStore

    @Before
    fun setUp() {
        fileStore = ImportSourceFileStore(File(temporaryFolder.root, "cache"))
    }

    private fun repository() = CachedFamilyRepository(
        sourceFileStore = fileStore,
        assembler = ImportSourceAssembler()
    )

    @Test
    fun availableFamilyIds_isEmpty_whenNoRunnerIsCached() = runBlocking {
        cacheFamily(FAMILY_ID)

        assertTrue(repository().availableFamilyIds().isEmpty())
    }

    @Test
    fun availableFamilyIds_listsOnlyFamiliesThatAreComplete() = runBlocking {
        fileStore.write(ImportSourceFileStore.runnerPath(), RUNNER)
        cacheFamily("fam-complete")
        // No adapter.js: such a family cannot be assembled, so it must not be offered.
        fileStore.write(ImportSourceFileStore.familyMetadataPath("fam-incomplete"), FAMILY_JSON)

        assertEquals(listOf("fam-complete"), repository().availableFamilyIds())
    }

    @Test
    fun assembleScript_buildsAScriptFromTheCachedFamily() = runBlocking {
        fileStore.write(ImportSourceFileStore.runnerPath(), RUNNER)
        cacheFamily(FAMILY_ID)

        val result = repository().assembleScript(
            request(configJson = """{"nested":{"value":"override"}}""")
        )

        val script = (result as FamilyAssemblyResult.Assembled).script
        assertTrue("应当带上缓存的族适配器", script.contains(ADAPTER))
        assertTrue("应当带上缓存的运行流程", script.contains(RUNNER))
        assertTrue("取值应当覆盖族默认值并保留其余项", script.contains("""{"nested":{"value":"override","keep":1}}"""))
    }

    @Test
    fun assembleScript_appendsTheOverride_whenOneIsGiven() = runBlocking {
        fileStore.write(ImportSourceFileStore.runnerPath(), RUNNER)
        cacheFamily(FAMILY_ID)

        val result = repository().assembleScript(
            request(configJson = "", overrideText = OVERRIDE)
        )

        val script = (result as FamilyAssemblyResult.Assembled).script
        assertTrue("覆盖脚本应当出现在最终脚本里", script.contains(OVERRIDE))
        assertTrue(script.contains("presetOverrides"))
    }

    @Test
    fun assembleScript_rejectsValuesThatAreNotAJsonObject() = runBlocking {
        fileStore.write(ImportSourceFileStore.runnerPath(), RUNNER)
        cacheFamily(FAMILY_ID)

        val result = repository().assembleScript(request(configJson = "[1, 2]"))

        val failed = result as FamilyAssemblyResult.Failed
        assertTrue(failed.message.contains("JSON"))
    }

    @Test
    fun assembleScript_reportsAFamilyThatIsNotCached() = runBlocking {
        fileStore.write(ImportSourceFileStore.runnerPath(), RUNNER)

        val result = repository().assembleScript(request(configJson = ""))

        assertTrue(result is FamilyAssemblyResult.Failed)
    }

    @Test
    fun assembleScript_reportsAMissingRunner() = runBlocking {
        cacheFamily(FAMILY_ID)

        val result = repository().assembleScript(request(configJson = ""))

        val failed = result as FamilyAssemblyResult.Failed
        assertTrue(failed.message.contains("运行流程"))
    }

    private suspend fun cacheFamily(familyId: String) {
        fileStore.write(ImportSourceFileStore.familyMetadataPath(familyId), FAMILY_JSON)
        fileStore.write(ImportSourceFileStore.familyAdapterPath(familyId), ADAPTER)
    }

    private fun request(configJson: String, overrideText: String? = null) = FamilyAssemblyRequest(
        familyId = FAMILY_ID,
        presetName = "我的方案",
        configJson = configJson,
        overrideText = overrideText
    )

    private companion object {
        const val FAMILY_ID = "fam"
        const val RUNNER = "function runImport() {}\n"
        const val FAMILY_JSON = """{"changePoints":{"nested":{"value":"default","keep":1}}}"""
        const val ADAPTER = "const familyAdapter = { source: true };\n"
        const val OVERRIDE = "const presetOverrides = { normalizeRow() {} };\n"
    }
}
