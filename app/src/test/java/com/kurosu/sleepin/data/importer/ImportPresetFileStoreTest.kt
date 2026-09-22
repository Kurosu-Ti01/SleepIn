package com.kurosu.sleepin.data.importer

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the custom preset script store.
 *
 * The store is the component that turns an identifier into a filesystem location, so the cases that
 * matter are the ones where that location could escape the store, and the ones where a failure has to
 * be reported instead of thrown.
 *
 * Uses a real temporary directory: the class is about actual file behaviour (atomic replacement,
 * recursive deletion), which a fake filesystem would not exercise.
 */
class ImportPresetFileStoreTest {

    private val root: File = Files.createTempDirectory("import-presets-test").toFile()
    private val store = ImportPresetFileStore(root)

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun writeScript_placesTheScriptWhereThePathRulesSayItGoes() = runBlocking {
        assertTrue(store.writeScript("school-a", "console.log('a')"))

        val expected = File(root, "presets/school-a/script.js")
        assertTrue(expected.isFile)
        assertEquals("console.log('a')", expected.readText())
    }

    @Test
    fun writeScript_replacesAPreviousScript() = runBlocking {
        store.writeScript("school-a", "first")

        assertTrue(store.writeScript("school-a", "second"))

        assertEquals("second", store.readScript("presets/school-a/script.js"))
        // The temporary file used to make the replacement atomic must not be left behind.
        assertFalse(File(root, "presets/school-a/script.js.tmp").exists())
    }

    @Test
    fun writeScript_refusesIdentifiersThatCouldNotBeAFolderName() = runBlocking {
        assertFalse(store.writeScript("School-A", "x"))
        assertFalse(store.writeScript("..", "x"))
        assertFalse(store.writeScript("a/../b", "x"))
        assertFalse(store.writeScript("", "x"))
        assertFalse(store.writeScript("a".repeat(65), "x"))
    }

    @Test
    fun readScript_returnsNullForPathsOutsideTheStore() = runBlocking {
        store.writeScript("school-a", "text")

        assertNull(store.readScript("presets/school-a/../../shared_prefs/app.xml"))
        assertNull(store.readScript("/etc/hosts"))
        assertNull(store.readScript("presets\\school-a\\script.js"))
        assertNull(store.readScript("presets/school-a/%2e%2e/x"))
        assertNull(store.readScript(""))
    }

    @Test
    fun readScript_returnsNullForAMissingFile() = runBlocking {
        assertNull(store.readScript("presets/nothing-here/script.js"))
    }

    @Test
    fun readScript_returnsTheTextThatWasWritten() = runBlocking {
        val script = "// 中文注释\nvar a = 1;\n"
        store.writeScript("school-a", script)

        assertEquals(script, store.readScript("presets/school-a/script.js"))
    }

    @Test
    fun deletePreset_removesEverythingUnderThatPreset() = runBlocking {
        store.writeScript("school-a", "a")
        val folder = File(root, "presets/school-a")
        assertTrue(folder.isDirectory)

        assertTrue(store.deletePreset("school-a"))

        assertFalse(folder.exists())
    }

    @Test
    fun deletePreset_succeedsWhenThereIsNothingToDelete() = runBlocking {
        assertTrue(store.deletePreset("never-existed"))
    }

    @Test
    fun deletePreset_refusesAnUnusableIdentifier() = runBlocking {
        store.writeScript("school-a", "a")

        assertFalse(store.deletePreset("../school-a"))

        assertTrue(File(root, "presets/school-a/script.js").isFile)
    }

    @Test
    fun writeScript_createsTheRootOnFirstUse() = runBlocking {
        val freshRoot = File(root, "nested/root")
        val freshStore = ImportPresetFileStore(freshRoot)

        assertTrue(freshStore.writeScript("school-a", "a"))

        assertTrue(File(freshRoot, "presets/school-a/script.js").isFile)
    }
}
