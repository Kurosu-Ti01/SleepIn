package com.kurosu.sleepin.data.importer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the path rules a preset index must satisfy.
 *
 * These are tested directly rather than only through the parser so each rejection reason is pinned by
 * itself: a case that fails during JSON decoding, for instance, would pass at the parser level even if
 * the path rule it was meant to cover was removed.
 */
class ImportPresetPathSafetyTest {

    @Test
    fun isSafeRelativePath_acceptsAPlainRelativePath() {
        assertTrue(isSafeRelativePath("presets/sample-preset/script.js"))
        assertTrue(isSafeRelativePath("presets/sample-preset/sub/script.js"))
    }

    @Test
    fun isSafeRelativePath_acceptsDotsInsideAFileName() {
        // ".." is only traversal as a whole segment; a name may legitimately contain it.
        assertTrue(isSafeRelativePath("presets/sample-preset/a..b.js"))
    }

    @Test
    fun isSafeRelativePath_rejectsEmptyPath() {
        assertFalse(isSafeRelativePath(""))
    }

    @Test
    fun isSafeRelativePath_rejectsAbsolutePaths() {
        assertFalse(isSafeRelativePath("/data/data/com.kurosu.sleepin/files/x.js"))
        assertFalse(isSafeRelativePath("\\x.js"))
    }

    @Test
    fun isSafeRelativePath_rejectsTraversalSegments() {
        assertFalse(isSafeRelativePath("presets/sample-preset/../../../shared_prefs/app.xml"))
        assertFalse(isSafeRelativePath("presets/../x.js"))
        assertFalse(isSafeRelativePath("presets/sample-preset/sub/../script.js"))
    }

    @Test
    fun isSafeRelativePath_rejectsCurrentDirectorySegments() {
        assertFalse(isSafeRelativePath("presets/sample-preset/./script.js"))
    }

    @Test
    fun isSafeRelativePath_rejectsEmptySegments() {
        assertFalse(isSafeRelativePath("presets//script.js"))
        assertFalse(isSafeRelativePath("presets/sample-preset/"))
    }

    @Test
    fun isSafeRelativePath_rejectsWindowsSeparatorsAndDrivePrefixes() {
        assertFalse(isSafeRelativePath("presets\\sample-preset\\script.js"))
        assertFalse(isSafeRelativePath("C:/x.js"))
    }

    @Test
    fun isSafeRelativePath_rejectsPercentEncoding() {
        // Not decodable into an escape by anything today, but a future source that normalizes paths
        // must not be able to turn an accepted index value into one.
        assertFalse(isSafeRelativePath("presets/sample-preset/%2e%2e/x.js"))
    }

    @Test
    fun isSafeScriptPath_requiresThePathToSitInsideThePresetsOwnFolder() {
        assertTrue(isSafeScriptPath("presets/sample-preset/script.js", "sample-preset"))

        assertFalse(isSafeScriptPath("presets/other/script.js", "sample-preset"))
        assertFalse(isSafeScriptPath("presets/sample-preset/script.js", "other"))
    }

    @Test
    fun isSafeScriptPath_doesNotMatchAFolderThatMerelySharesThePrefix() {
        // The trailing separator is what makes this safe: without it, preset "generic" could reach the
        // files of "sample-preset".
        assertFalse(isSafeScriptPath("presets/sample-preset/script.js", "generic"))
        assertFalse(isSafeScriptPath("presets/sample-preset-extra/script.js", "sample-preset"))
    }

    @Test
    fun isSafeScriptPath_isCaseSensitive() {
        assertFalse(isSafeScriptPath("presets/Sample-Preset/script.js", "sample-preset"))
    }
}
