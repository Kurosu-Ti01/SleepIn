package com.kurosu.sleepin.data.importer

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores downloaded source parts that may be reused by a later source import.
 *
 * The cache is intentionally separate from user preset scripts. A cached family is executable code,
 * so every file is still written atomically and every source download is hash-checked before it gets
 * here. The cache contains only source parts, never credentials or portal data.
 */
class ImportSourceFileStore(private val rootDirectory: File) {

    private val rootCanonicalPath: String by lazy { rootDirectory.canonicalFile.path }

    suspend fun read(relativePath: String): String? = withContext(Dispatchers.IO) {
        val file = resolve(relativePath) ?: return@withContext null
        try {
            if (file.isFile) file.readText(Charsets.UTF_8) else null
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    suspend fun write(relativePath: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val target = resolve(relativePath) ?: return@withContext false
        val parent = target.parentFile ?: return@withContext false
        try {
            parent.mkdirs()
            val temporary = File(parent, "${target.name}.tmp")
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
     * Names of the immediate subdirectories of [relativePath], sorted.
     *
     * Returns an empty list when the directory is missing or unreadable: "nothing cached yet" is a normal
     * state the caller shows as an empty choice rather than an error.
     */
    suspend fun listDirectories(relativePath: String): List<String> = withContext(Dispatchers.IO) {
        val directory = resolve(relativePath) ?: return@withContext emptyList()
        directory.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.map { file -> file.name }
            .orEmpty()
            .sorted()
    }

    /**
     * Removes a cached directory and everything under it.
     *
     * Used when a source is forgotten or re-pointed: the scripts it assembled are keyed by the source, so
     * they are unreachable afterwards and would otherwise accumulate a few megabytes per source.
     *
     * @return true when the directory is gone, including when it was never there.
     */
    suspend fun deleteDirectory(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val directory = resolve(relativePath) ?: return@withContext false
        try {
            if (!directory.exists()) return@withContext true
            directory.deleteRecursively()
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

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
        const val DIRECTORY_NAME = "import-source-cache"

        fun runnerPath(): String = "runner.js"

        fun familyMetadataPath(familyId: String): String = "families/$familyId/family.json"

        fun familyAdapterPath(familyId: String): String = "families/$familyId/adapter.js"
    }
}
