package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.data.importer.ImportPresetSource
import com.kurosu.sleepin.data.importer.MutableImportPresetSource
import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for merging preset sources.
 *
 * The merge order is the mechanism that lets a user's own preset replace an imported one of the same
 * id, so it is worth pinning down as sources are added.
 */
class ImportPresetRepositoryImplTest {

    @Test
    fun getPresets_returnsThePresetsOfASingleSource() = runBlocking {
        val repository = repositoryOf(
            FakeImportPresetSource(ImportPresetSourceKind.REMOTE, listOf(preset(id = "a")))
        )

        assertEquals(listOf("a"), repository.getPresets().map { it.id })
    }

    @Test
    fun getPresets_letsAMutableSourceReplaceAnImportedPresetWithTheSameId() = runBlocking {
        val repository = repositoryOf(
            FakeImportPresetSource(
                ImportPresetSourceKind.REMOTE,
                listOf(preset(id = "sample-preset", name = "源里的名称"))
            ),
            FakeMutableImportPresetSource(
                presets = listOf(
                    preset(
                        id = "sample-preset",
                        name = "我的名称",
                        source = ImportPresetSourceKind.CUSTOM
                    )
                )
            )
        )

        val presets = repository.getPresets()

        assertEquals(1, presets.size)
        assertEquals("我的名称", presets.single().name)
        assertEquals(ImportPresetSourceKind.CUSTOM, presets.single().source)
    }

    @Test
    fun getPresets_ordersByGroupThenInitialThenName() = runBlocking {
        val repository = repositoryOf(
            FakeImportPresetSource(
                ImportPresetSourceKind.REMOTE,
                listOf(
                    preset(id = "general", group = ImportPresetGroup.GENERAL, initial = "A", name = "a"),
                    preset(id = "postgraduate", group = ImportPresetGroup.POSTGRADUATE, initial = "Z", name = "z"),
                    preset(id = "undergrad-b", group = ImportPresetGroup.UNDERGRADUATE, initial = "B", name = "b"),
                    preset(id = "undergrad-a-late", group = ImportPresetGroup.UNDERGRADUATE, initial = "A", name = "c"),
                    preset(id = "undergrad-a-early", group = ImportPresetGroup.UNDERGRADUATE, initial = "A", name = "a")
                )
            )
        )

        assertEquals(
            listOf("undergrad-a-early", "undergrad-a-late", "undergrad-b", "postgraduate", "general"),
            repository.getPresets().map { it.id }
        )
    }

    @Test
    fun getPresets_returnsNothing_whenNoSourceIsRegistered() = runBlocking {
        val repository = ImportPresetRepositoryImpl(
            sources = emptyList(),
            customPresets = FakeMutableImportPresetSource()
        )

        assertTrue(repository.getPresets().isEmpty())
    }

    @Test
    fun readScript_readsFromTheSourceThatSuppliedThePreset() = runBlocking {
        val scriptPath = "presets/shared/script.js"
        val repository = repositoryOf(
            FakeImportPresetSource(
                ImportPresetSourceKind.REMOTE,
                presets = emptyList(),
                scripts = mapOf(scriptPath to "imported script")
            ),
            FakeMutableImportPresetSource(
                scripts = mapOf(scriptPath to "custom script")
            )
        )

        assertEquals(
            "imported script",
            repository.readScript(preset(id = "shared", scriptPath = scriptPath, source = ImportPresetSourceKind.REMOTE))
        )
        assertEquals(
            "custom script",
            repository.readScript(preset(id = "shared", scriptPath = scriptPath, source = ImportPresetSourceKind.CUSTOM))
        )
    }

    @Test
    fun readScript_returnsNull_whenNoSourceMatchesThePreset() = runBlocking {
        val repository = repositoryOf(
            FakeImportPresetSource(ImportPresetSourceKind.REMOTE, presets = emptyList())
        )

        // The user's own presets are not in `sources`, so asking for one finds no source to read
        // through, even though a writable source is registered separately.
        assertNull(repository.readScript(preset(id = "a", source = ImportPresetSourceKind.CUSTOM)))
    }

    @Test
    fun addCustomPreset_delegatesToTheWritableSource() = runBlocking {
        val customSource = FakeMutableImportPresetSource()
        val repository = repositoryOf(customSource = customSource)
        val draft = CustomImportPresetDraft(
            name = "示例大学",
            group = ImportPresetGroup.UNDERGRADUATE,
            initial = "S",
            startUrl = "https://portal.example.invalid",
            description = "",
            maintainer = "",
            scriptText = "console.log('x')"
        )

        repository.addCustomPreset(draft)

        // The repository decides nothing about where a user's preset belongs; it must hand the draft to
        // the one source that can store it.
        assertEquals(listOf(draft), customSource.addedDrafts)
    }

    @Test
    fun removeCustomPreset_delegatesToTheWritableSource() = runBlocking {
        val customSource = FakeMutableImportPresetSource()
        val repository = repositoryOf(customSource = customSource)

        repository.removeCustomPreset("custom-abc")

        assertEquals(listOf("custom-abc"), customSource.removedIds)
    }

    private fun repositoryOf(
        vararg sources: ImportPresetSource,
        customSource: FakeMutableImportPresetSource = sources
            .filterIsInstance<FakeMutableImportPresetSource>()
            .firstOrNull()
            ?: FakeMutableImportPresetSource()
    ): ImportPresetRepositoryImpl = ImportPresetRepositoryImpl(
        sources = sources.toList(),
        customPresets = customSource
    )

    private class FakeImportPresetSource(
        override val kind: ImportPresetSourceKind,
        private val presets: List<ImportPreset>,
        private val scripts: Map<String, String> = emptyMap()
    ) : ImportPresetSource {
        override suspend fun presets(): List<ImportPreset> = presets

        override suspend fun readScript(scriptPath: String): String? = scripts[scriptPath]
    }

    private class FakeMutableImportPresetSource(
        private val presets: List<ImportPreset> = emptyList(),
        private val scripts: Map<String, String> = emptyMap()
    ) : MutableImportPresetSource {

        override val kind: ImportPresetSourceKind = ImportPresetSourceKind.CUSTOM

        val addedDrafts = mutableListOf<CustomImportPresetDraft>()
        val removedIds = mutableListOf<String>()

        override suspend fun presets(): List<ImportPreset> = presets

        override suspend fun readScript(scriptPath: String): String? = scripts[scriptPath]

        override suspend fun add(draft: CustomImportPresetDraft): ImportPresetAddResult {
            addedDrafts.add(draft)
            return ImportPresetAddResult.Added(preset(id = "custom-abc", source = ImportPresetSourceKind.CUSTOM))
        }

        override suspend fun remove(presetId: String): Boolean {
            removedIds.add(presetId)
            return true
        }
    }
}

/**
 * Builds a preset for the merge tests.
 *
 * File-level rather than a member of the test class so the fake sources can use it too: a nested class
 * cannot reach its enclosing instance's members.
 */
private fun preset(
    id: String,
    name: String = id,
    group: ImportPresetGroup = ImportPresetGroup.GENERAL,
    initial: String = "A",
    scriptPath: String = "presets/$id/script.js",
    source: ImportPresetSourceKind = ImportPresetSourceKind.REMOTE
) = ImportPreset(
    id = id,
    name = name,
    group = group,
    initial = initial,
    startUrl = null,
    scriptPath = scriptPath,
    description = "说明",
    maintainer = "SleepIn",
    source = source
)
