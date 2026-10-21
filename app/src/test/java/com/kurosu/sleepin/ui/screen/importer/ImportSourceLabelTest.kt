package com.kurosu.sleepin.ui.screen.importer

import com.kurosu.sleepin.domain.model.importer.BUILT_IN_IMPORT_SOURCE_URL
import com.kurosu.sleepin.domain.model.importer.LOCAL_SOURCE_KEY_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the label a source row shows.
 *
 * The fixed words this replaces ("自建源" on every user-added row) told the user nothing when they had two
 * of them, so the fallback is derived from the address and must stay recognisable: a host for a link, the
 * folder name for a directory.
 */
class ImportSourceLabelTest {

    @Test
    fun namedSource_showsItsName() {
        assertEquals(
            "学校镜像",
            importSourceLabel("https://mirror.example.invalid/source-index.json", "学校镜像")
        )
    }

    @Test
    fun blankName_fallsBackToTheDerivedLabel() {
        // Clearing the field is the documented way to go back to the derived label, not an error.
        assertEquals(
            "mirror.example.invalid",
            importSourceLabel("https://mirror.example.invalid/source-index.json", "   ")
        )
        assertEquals(
            "mirror.example.invalid",
            importSourceLabel("https://mirror.example.invalid/source-index.json", null)
        )
    }

    @Test
    fun unnamedLink_isNamedByItsHost() {
        assertEquals(
            "raw.githubusercontent.com",
            defaultImportSourceLabel("https://raw.githubusercontent.com/owner/repo/main/source-index.json")
        )
    }

    @Test
    fun unnamedDirectory_isNamedByItsLastSegment() {
        assertEquals(
            "SleepIn-ImportPresets",
            defaultImportSourceLabel("${LOCAL_SOURCE_KEY_PREFIX}content://tree/primary%3ASleepIn-ImportPresets")
        )
        // A URI whose path uses real separators still resolves to the folder name.
        assertEquals(
            "presets",
            defaultImportSourceLabel("${LOCAL_SOURCE_KEY_PREFIX}content://tree/primary/presets")
        )
    }

    @Test
    fun unnamedDirectory_withoutASegment_stillHasALabel() {
        // Never blank: a row with an empty title looks like a rendering bug.
        assertEquals("本地目录", defaultImportSourceLabel(LOCAL_SOURCE_KEY_PREFIX))
    }

    @Test
    fun builtInSource_isAlwaysCalledTheBuiltInSource() {
        assertEquals("内置源", importSourceLabel(BUILT_IN_IMPORT_SOURCE_URL, null))
        // Even a name could not stick: it is not renameable, and the title must not disagree with that.
        assertEquals("内置源", importSourceLabel(BUILT_IN_IMPORT_SOURCE_URL, "我的源"))
    }
}
