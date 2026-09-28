package com.kurosu.sleepin.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.kurosu.sleepin.data.importer.HttpImportSourceReader
import com.kurosu.sleepin.data.importer.ImportedSourceImporter
import com.kurosu.sleepin.data.importer.TreeImportSourceReader
import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.repository.ImportSourceRepository
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One source import the user asked for.
 *
 * This layer answers a single question: **where do the bytes come from** — an HTTPS address the user
 * typed, or a directory they picked on the device. Verification, family handling, assembly and storage
 * all live in [ImportedSourceImporter] and are shared by both paths. The only per-path decisions are the
 * source key and the script directory prefix, which keep same-named presets from two sources apart.
 */
internal class ImportSourceRepositoryImpl(
    private val importer: ImportedSourceImporter,
    private val contentResolver: ContentResolver
) : ImportSourceRepository {

    override suspend fun importSource(draft: ImportSourceDraft): ImportSourceImportResult {
        val indexUrl = draft.indexUrl.trim()
        if (!indexUrl.startsWith("https://")) {
            return ImportSourceImportResult.Rejected("源链接必须以 https:// 开头。")
        }
        return withContext(Dispatchers.IO) {
            importer.import(
                reader = HttpImportSourceReader(indexUrl),
                sourceKey = indexUrl,
                directoryKey = "remote-${stableId(indexUrl)}",
                reuseLocalFamilies = draft.reuseLocalFamilies
            )
        }
    }

    override suspend fun importLocalSource(draft: ImportLocalSourceDraft): ImportSourceImportResult {
        val rootUri = draft.rootUri.trim()
        if (rootUri.isEmpty()) {
            return ImportSourceImportResult.Rejected("没有选择源目录。")
        }
        return withContext(Dispatchers.IO) {
            importer.import(
                reader = TreeImportSourceReader(Uri.parse(rootUri), contentResolver),
                // Prefixed so a directory identifier cannot collide with a link that happens to be equal.
                sourceKey = "local:$rootUri",
                directoryKey = "local-${stableId(rootUri)}",
                reuseLocalFamilies = draft.reuseLocalFamilies
            )
        }
    }

    /** A source identifier becomes a short directory name, keeping two sources' scripts apart. */
    private fun stableId(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(16)
}
