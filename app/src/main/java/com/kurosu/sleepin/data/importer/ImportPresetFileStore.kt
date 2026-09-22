package com.kurosu.sleepin.data.importer

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the script files of the user's own presets under one directory.
 *
 * The layout matches the bundled ones exactly — `<root>/presets/<id>/script.js` — so a preset's
 * reference means the same thing wherever it is stored, and so the path rules in [ImportPresetPaths]
 * apply to both without a special case.
 *
 * Every method reports failure by returning null or false rather than throwing: "this preset's script
 * is missing" is a normal state the screens have to describe, not a programming error.
 */
class ImportPresetFileStore(private val rootDirectory: File) {

    /**
     * Resolved root path, used to confirm a candidate really sits inside the store.
     *
     * Computed once because it is compared on every read and write, and it does not change while the
     * process lives.
     */
    private val rootCanonicalPath: String by lazy { rootDirectory.canonicalFile.path }

    /**
     * Writes [text] as the script of [presetId], replacing any previous one.
     *
     * @return true when the file is in place.
     */
    suspend fun writeScript(presetId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPresetId(presetId)) return@withContext false
        val target = resolve(scriptPathFor(presetId)) ?: return@withContext false
        val parent = target.parentFile ?: return@withContext false

        try {
            parent.mkdirs()
            // Written beside the target and then moved onto it, so an interrupted write cannot leave a
            // half-written script that the WebView would later execute as if it were complete.
            val temporary = File(parent, "$PRESET_SCRIPT_FILE_NAME.tmp")
            temporary.writeText(text, Charsets.UTF_8)
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Reads the script at [relativePath], or null when it is missing or the path is not acceptable.
     */
    suspend fun readScript(relativePath: String): String? = withContext(Dispatchers.IO) {
        val file = resolve(relativePath) ?: return@withContext null
        try {
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Deletes everything stored for [presetId].
     *
     * @return true when nothing of it is left behind, including when there was nothing to delete.
     */
    suspend fun deletePreset(presetId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidPresetId(presetId)) return@withContext false
        val directory = resolve("presets/$presetId") ?: return@withContext false
        try {
            if (!directory.exists()) return@withContext true
            directory.deleteRecursively()
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Resolves [relativePath] against the store root, refusing anything that would land outside it.
     *
     * [isSafeRelativePath] already rejects every traversal form the app produces, and the canonical
     * comparison after it covers what a filesystem could add — a symlinked directory, for instance.
     */
    private fun resolve(relativePath: String): File? {
        if (!isSafeRelativePath(relativePath)) return null
        val candidate = File(rootDirectory, relativePath)
        return try {
            val canonical = candidate.canonicalFile
            if (canonical.path.startsWith(rootCanonicalPath + File.separator)) canonical else null
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    companion object {
        /**
         * Directory created under the app's files directory to hold custom preset scripts.
         *
         * Named here rather than at the wiring site so the layout of the store lives with the code
         * that implements it.
         */
        const val DIRECTORY_NAME = "import-presets"
    }
}
