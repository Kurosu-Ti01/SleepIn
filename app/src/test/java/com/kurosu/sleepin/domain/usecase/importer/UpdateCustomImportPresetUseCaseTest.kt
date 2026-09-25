package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for replacing one of the user's own presets.
 *
 * The interesting property is the order of the two writes: getting it backwards means a failed save
 * destroys the preset the user was editing.
 */
class UpdateCustomImportPresetUseCaseTest {

    private val repository = RecordingImportPresetRepository()
    private val useCase = UpdateCustomImportPresetUseCase(repository)

    @Test
    fun invoke_storesTheNewVersionAndThenDropsTheOldOne() = runBlocking {
        val submitted = draft()

        val result = useCase(previousPresetId = "custom-old", draft = submitted)

        assertTrue(result is ImportPresetAddResult.Added)
        assertEquals(submitted, repository.addedDraft)
        assertEquals(listOf("custom-old"), repository.removedIds)
    }

    @Test
    fun invoke_keepsTheOldPresetWhenTheNewOneCannotBeStored() = runBlocking {
        repository.addResult = ImportPresetAddResult.Failed("磁盘写入失败")

        val result = useCase(previousPresetId = "custom-old", draft = draft())

        assertEquals(ImportPresetAddResult.Failed("磁盘写入失败"), result)
        // The old preset must survive a failed replacement: removing it first would have left the user
        // with nothing at all.
        assertTrue(repository.removedIds.isEmpty())
    }

    @Test
    fun invoke_keepsTheOldPresetWhenTheDraftIsRefused() = runBlocking {
        repository.addResult = ImportPresetAddResult.Rejected("请选择或填写学校名称。")

        val result = useCase(previousPresetId = "custom-old", draft = draft())

        assertEquals(ImportPresetAddResult.Rejected("请选择或填写学校名称。"), result)
        assertTrue(repository.removedIds.isEmpty())
    }

    @Test
    fun invoke_refusesAnInvalidDraftWithoutTouchingStorage() = runBlocking {
        val result = useCase(previousPresetId = "custom-old", draft = draft().copy(name = "  "))

        assertEquals("请填写方案名称。", (result as ImportPresetAddResult.Rejected).message)
        // Neither half of the replacement may run: a preset has to survive an unusable draft.
        assertNull(repository.addedDraft)
        assertTrue(repository.removedIds.isEmpty())
    }

    private fun draft() = CustomImportPresetDraft(
        name = "本科教务系统",
        group = ImportPresetGroup.UNDERGRADUATE,
        initial = "S",
        startUrl = "https://portal.example.invalid",
        description = "",
        maintainer = "",
        scriptText = "console.log('x')",
        schoolName = "示例大学"
    )
}
