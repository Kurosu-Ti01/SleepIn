package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.repository.ImportPresetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the rules that decide whether a user's own preset may be stored.
 *
 * These rules are the app's only defence against a preset that would be listed but unusable: a stored
 * preset is a promise that picking it later will work, so a blank name, a missing start URL or a
 * script that is not a script has to be refused at the point it is entered.
 */
class AddCustomImportPresetUseCaseTest {

    private val repository = RecordingImportPresetRepository()
    private val useCase = AddCustomImportPresetUseCase(repository)

    @Test
    fun invoke_storesACompleteDraft() = runBlocking {
        val draft = draft()

        val result = useCase(draft)

        assertTrue(result is ImportPresetAddResult.Added)
        assertEquals(draft, repository.addedDraft)
    }

    @Test
    fun invoke_rejectsABlankSchoolName() = runBlocking {
        val result = useCase(draft(schoolName = "  "))

        assertEquals("请选择或填写学校名称。", rejectionMessage(result))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_rejectsABlankName() = runBlocking {
        val result = useCase(draft(name = "   "))

        assertEquals("请填写方案名称。", rejectionMessage(result))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_rejectsAnOverlongName() = runBlocking {
        val result = useCase(draft(name = "名".repeat(41)))

        assertTrue(rejectionMessage(result)!!.contains("最多 40"))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_rejectsAnInitialThatIsNotASingleUppercaseLetter() = runBlocking {
        assertTrue(useCase(draft(initial = "")) is ImportPresetAddResult.Rejected)
        assertTrue(useCase(draft(initial = "SS")) is ImportPresetAddResult.Rejected)
        assertTrue(useCase(draft(initial = "1")) is ImportPresetAddResult.Rejected)
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_acceptsALowercaseInitial_becauseStorageNormalisesIt() = runBlocking {
        val result = useCase(draft(initial = "s"))

        assertTrue(result is ImportPresetAddResult.Added)
    }

    @Test
    fun invoke_rejectsAMissingStartUrl() = runBlocking {
        val result = useCase(draft(startUrl = "  "))

        assertEquals("请填写起始网址。", rejectionMessage(result))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_rejectsAStartUrlWithoutAScheme() = runBlocking {
        // The scheme decides whether the portal is reached securely, so defaulting it silently would
        // hide a mistake in what the user pasted.
        val result = useCase(draft(startUrl = "portal.example.invalid"))

        assertTrue(rejectionMessage(result)!!.contains("http://"))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_rejectsABlankScript() = runBlocking {
        val result = useCase(draft(scriptText = "\n  \n"))

        assertTrue(rejectionMessage(result)!!.contains("脚本"))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_rejectsAScriptTooLargeToBeAnAdapter() = runBlocking {
        val result = useCase(draft(scriptText = "x".repeat(512 * 1024 + 1)))

        assertTrue(rejectionMessage(result)!!.contains("512 KB"))
        assertNull(repository.addedDraft)
    }

    @Test
    fun invoke_passesThroughAStorageFailure() = runBlocking {
        repository.addResult = ImportPresetAddResult.Failed("磁盘写入失败")

        val result = useCase(draft())

        // A storage failure is not a rejection: the screen has to be able to tell "fix your input"
        // apart from "try again".
        assertEquals(ImportPresetAddResult.Failed("磁盘写入失败"), result)
    }

    private fun rejectionMessage(result: ImportPresetAddResult): String? =
        (result as? ImportPresetAddResult.Rejected)?.message

    private fun draft(
        name: String = "示例大学",
        group: ImportPresetGroup = ImportPresetGroup.UNDERGRADUATE,
        initial: String = "S",
        startUrl: String = "https://portal.example.invalid",
        description: String = "先登录再打开课表页",
        maintainer: String = "someone",
        scriptText: String = "console.log('import')",
        schoolName: String = "示例大学"
    ) = CustomImportPresetDraft(
        name = name,
        group = group,
        initial = initial,
        startUrl = startUrl,
        description = description,
        maintainer = maintainer,
        scriptText = scriptText,
        schoolName = schoolName
    )
}

/**
 * Repository that records what the use case asked it to store.
 *
 * Shared with the remove use case's tests, which care only about the delegation.
 */
internal class RecordingImportPresetRepository : ImportPresetRepository {

    var addedDraft: CustomImportPresetDraft? = null
    var addResult: ImportPresetAddResult = ImportPresetAddResult.Added(samplePreset())
    val removedIds = mutableListOf<String>()
    var removeResult: Boolean = true

    override suspend fun getPresets(): List<ImportPreset> = emptyList()

    override suspend fun readScript(preset: ImportPreset): String? = null

    override suspend fun addCustomPreset(draft: CustomImportPresetDraft): ImportPresetAddResult {
        addedDraft = draft
        return addResult
    }

    override suspend fun removeCustomPreset(presetId: String): Boolean {
        removedIds.add(presetId)
        return removeResult
    }

    companion object {
        fun samplePreset(): ImportPreset = ImportPreset(
            id = "custom-abc",
            name = "示例大学",
            group = ImportPresetGroup.UNDERGRADUATE,
            initial = "S",
            startUrl = "https://portal.example.invalid",
            scriptPath = "presets/custom-abc/script.js",
            scriptSha256 = null,
            description = "",
            maintainer = "",
            source = ImportPresetSourceKind.CUSTOM
        )
    }
}
