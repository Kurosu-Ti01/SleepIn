package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.model.importer.RegisteredImportSource
import com.kurosu.sleepin.domain.repository.ImportSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests re-pointing a source the user added.
 *
 * The order matters more than the outcome: importing before forgetting is what makes a typo harmless.
 * The built-in source is not covered here because it cannot be re-pointed at all — see
 * [RefreshImportSourcesUseCase] for the fixed address it always uses.
 */
class ChangeImportSourceUrlUseCaseTest {

    @Test
    fun change_importsTheNewAddressBeforeForgettingTheOldOne() = runBlocking {
        val repository = FakeImportSourceRepository(ImportSourceImportResult.Imported(3, 1))

        useCase(repository).invoke(OLD_KEY, NEW_URL)

        // The old registration is only dropped once the new one exists.
        assertEquals(listOf("import:$NEW_URL", "forget:$OLD_KEY"), repository.events)
    }

    @Test
    fun change_keepsTheOldSource_whenTheNewAddressCannotBeImported() = runBlocking {
        val repository = FakeImportSourceRepository(ImportSourceImportResult.Failed("源服务器返回 HTTP 404"))

        val result = useCase(repository).invoke(OLD_KEY, NEW_URL)

        assertEquals(ImportSourceImportResult.Failed("源服务器返回 HTTP 404"), result)
        assertEquals(listOf("import:$NEW_URL"), repository.events)
    }

    @Test
    fun change_rejectsAnAddressThatIsNotHttps_withoutTouchingAnything() = runBlocking {
        val repository = FakeImportSourceRepository(ImportSourceImportResult.Imported(1, 1))

        val result = useCase(repository).invoke(OLD_KEY, "http://example.com/source-index.json")

        assertTrue(result is ImportSourceImportResult.Rejected)
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun change_trimsTheAddressBeforeImporting() = runBlocking {
        // The key the source is registered under has to be the one that was imported, or nothing would ever
        // find it again.
        val repository = FakeImportSourceRepository(ImportSourceImportResult.Imported(1, 1))

        useCase(repository).invoke(OLD_KEY, "  $NEW_URL  ")

        assertEquals(listOf("import:$NEW_URL", "forget:$OLD_KEY"), repository.events)
    }

    @Test
    fun change_doesNotForgetTheSource_whenTheAddressIsTheSame() = runBlocking {
        val repository = FakeImportSourceRepository(ImportSourceImportResult.Unchanged(indexVersion = 4))

        useCase(repository).invoke(NEW_URL, NEW_URL)

        // The import above already replaced the registration; forgetting it now would delete what was just
        // written.
        assertEquals(listOf("import:$NEW_URL"), repository.events)
    }

    private fun useCase(repository: FakeImportSourceRepository) = ChangeImportSourceUrlUseCase(repository)

    private class FakeImportSourceRepository(
        private val result: ImportSourceImportResult
    ) : ImportSourceRepository {

        /** What happened, in order, so the tests can assert sequencing rather than just the end state. */
        val events = mutableListOf<String>()

        override suspend fun importSource(draft: ImportSourceDraft): ImportSourceImportResult {
            events += "import:${draft.indexUrl}"
            return result
        }

        override suspend fun importLocalSource(draft: ImportLocalSourceDraft): ImportSourceImportResult =
            ImportSourceImportResult.Rejected("本测试只覆盖链接来源")

        override suspend fun registeredSources(): List<RegisteredImportSource> = emptyList()

        override suspend fun renameSource(sourceKey: String, displayName: String?): Boolean = true

        override suspend fun forgetSource(sourceKey: String): Boolean {
            events += "forget:$sourceKey"
            return true
        }
    }

    private companion object {
        const val OLD_KEY = "https://old.example.invalid/source-index.json"
        const val NEW_URL = "https://new.example.invalid/source-index.json"
    }
}
