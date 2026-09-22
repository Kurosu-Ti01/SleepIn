package com.kurosu.sleepin.domain.usecase.importer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for removing a user's own preset.
 *
 * The use case only delegates, so what these tests pin down is the delegation itself: that the id
 * reaches the repository unchanged and that the repository's answer is what the caller sees. Bundled
 * presets and a downloaded index are never removable through this path.
 */
class RemoveCustomImportPresetUseCaseTest {

    private val repository = RecordingImportPresetRepository()
    private val useCase = RemoveCustomImportPresetUseCase(repository)

    @Test
    fun invoke_removesThePresetWithTheGivenId() = runBlocking {
        val result = useCase("custom-abc")

        assertTrue(result)
        assertEquals(listOf("custom-abc"), repository.removedIds)
    }

    @Test
    fun invoke_reportsWhenTheRepositoryCouldNotRemove() = runBlocking {
        repository.removeResult = false

        assertFalse(useCase("custom-abc"))
    }
}
