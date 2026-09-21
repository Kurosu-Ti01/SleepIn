package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.data.importer.ImportPresetSource
import com.kurosu.sleepin.domain.model.importer.ImportPreset
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
 * The merge order is the mechanism that lets a user's own preset replace the bundled one of the same
 * id, and later a downloaded one replace both, so it is worth pinning down before more sources exist.
 */
class ImportPresetRepositoryImplTest {

    @Test
    fun getPresets_returnsThePresetsOfASingleSource() = runBlocking {
        val repository = ImportPresetRepositoryImpl(
            listOf(FakeImportPresetSource(ImportPresetSourceKind.BUNDLED, listOf(preset(id = "a"))))
        )

        assertEquals(listOf("a"), repository.getPresets().map { it.id })
    }

    @Test
    fun getPresets_letsALaterSourceOverrideAnEarlierPresetWithTheSameId() = runBlocking {
        val repository = ImportPresetRepositoryImpl(
            listOf(
                FakeImportPresetSource(
                    ImportPresetSourceKind.BUNDLED,
                    listOf(preset(id = "generic-blank", name = "内置名称"))
                ),
                FakeImportPresetSource(
                    ImportPresetSourceKind.CUSTOM,
                    listOf(preset(id = "generic-blank", name = "我的名称", source = ImportPresetSourceKind.CUSTOM))
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
        val repository = ImportPresetRepositoryImpl(
            listOf(
                FakeImportPresetSource(
                    ImportPresetSourceKind.BUNDLED,
                    listOf(
                        preset(id = "general", group = ImportPresetGroup.GENERAL, initial = "A", name = "a"),
                        preset(id = "postgraduate", group = ImportPresetGroup.POSTGRADUATE, initial = "Z", name = "z"),
                        preset(id = "undergrad-b", group = ImportPresetGroup.UNDERGRADUATE, initial = "B", name = "b"),
                        preset(id = "undergrad-a-late", group = ImportPresetGroup.UNDERGRADUATE, initial = "A", name = "c"),
                        preset(id = "undergrad-a-early", group = ImportPresetGroup.UNDERGRADUATE, initial = "A", name = "a")
                    )
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
        assertTrue(ImportPresetRepositoryImpl(emptyList()).getPresets().isEmpty())
    }

    @Test
    fun readScript_readsFromTheSourceThatSuppliedThePreset() = runBlocking {
        val scriptPath = "presets/shared/script.js"
        val repository = ImportPresetRepositoryImpl(
            listOf(
                FakeImportPresetSource(
                    ImportPresetSourceKind.BUNDLED,
                    presets = emptyList(),
                    scripts = mapOf(scriptPath to "bundled script")
                ),
                FakeImportPresetSource(
                    ImportPresetSourceKind.CUSTOM,
                    presets = emptyList(),
                    scripts = mapOf(scriptPath to "custom script")
                )
            )
        )

        assertEquals(
            "bundled script",
            repository.readScript(preset(id = "shared", scriptPath = scriptPath, source = ImportPresetSourceKind.BUNDLED))
        )
        assertEquals(
            "custom script",
            repository.readScript(preset(id = "shared", scriptPath = scriptPath, source = ImportPresetSourceKind.CUSTOM))
        )
    }

    @Test
    fun readScript_returnsNull_whenNoSourceMatchesThePreset() = runBlocking {
        val repository = ImportPresetRepositoryImpl(
            listOf(FakeImportPresetSource(ImportPresetSourceKind.BUNDLED, presets = emptyList()))
        )

        assertNull(repository.readScript(preset(id = "a", source = ImportPresetSourceKind.REMOTE)))
    }

    private fun preset(
        id: String,
        name: String = id,
        group: ImportPresetGroup = ImportPresetGroup.GENERAL,
        initial: String = "A",
        scriptPath: String = "presets/$id/script.js",
        source: ImportPresetSourceKind = ImportPresetSourceKind.BUNDLED
    ) = ImportPreset(
        id = id,
        name = name,
        group = group,
        initial = initial,
        startUrl = null,
        scriptPath = scriptPath,
        scriptSha256 = null,
        description = "说明",
        maintainer = "SleepIn",
        source = source
    )

    private class FakeImportPresetSource(
        override val kind: ImportPresetSourceKind,
        private val presets: List<ImportPreset>,
        private val scripts: Map<String, String> = emptyMap()
    ) : ImportPresetSource {
        override suspend fun presets(): List<ImportPreset> = presets

        override suspend fun readScript(scriptPath: String): String? = scripts[scriptPath]
    }
}
