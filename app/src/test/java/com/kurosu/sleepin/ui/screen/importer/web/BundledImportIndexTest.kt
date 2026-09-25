package com.kurosu.sleepin.ui.screen.importer.web

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards that school-specific adapters enter the package only after being reviewed. */
class BundledImportIndexTest {

    @Test
    fun bundledIndexStartsEmptyUntilASchoolAdapterIsVerified() {
        val index = bundledIndex()

        assertTrue(index.contains("\"schools\":[]"))
        assertTrue(index.contains("\"presets\":[]"))
    }

    private fun bundledIndex(): String {
        val relativePath = "src/main/assets/import-scripts/index.json"
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("could not locate $relativePath starting from ${File("").absolutePath}")
    }
}
