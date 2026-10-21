package com.kurosu.sleepin.data.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Name of the source index inside a source root; both remote and local sources use it. */
internal const val SOURCE_INDEX_FILE_NAME: String = "source-index.json"

/**
 * Reads the raw bytes of one source.
 *
 * Splitting "where the bytes come from" from "what happens to them" is what lets a local directory and
 * a remote URL share one set of rules: hash verification, safe relative paths and the three assembly
 * modes are implemented once, in [ImportedSourceImporter].
 *
 * Failures come in two kinds: a **missing file** is reported as null so the caller can phrase something
 * the user can act on, while a **transport failure** is thrown.
 *
 * There is deliberately no conditional-request support here. The index is downloaded in full and the
 * importer decides whether anything has to change, by comparing the version it carries: a validator such
 * as an ETag would save those bytes, but a wrong one would let the app skip an update it never saw.
 * `raw.githubusercontent.com` currently answers `If-None-Match` with 304 even after the file changed, so
 * its validator cannot be trusted to mean "unchanged".
 */
internal interface ImportSourceReader {

    /** Bytes of the source index itself, or null when it is not there. */
    suspend fun readIndex(): ByteArray?

    /** Bytes of [relativePath] under the source root, or null when it is missing or unusable. */
    suspend fun read(relativePath: String): ByteArray?
}

/**
 * Reads a remote source over HTTPS.
 *
 * The index address is supplied by the user, and every other file is resolved against it. Resolution
 * must stay on the **same host**: an index is data someone else published, and it must not be able to
 * point the app at another domain.
 */
internal class HttpImportSourceReader(private val indexUrl: String) : ImportSourceReader {

    override suspend fun readIndex(): ByteArray = download(indexUrl)

    override suspend fun read(relativePath: String): ByteArray {
        val base = URI(indexUrl)
        val resolved = base.resolve(relativePath)
        require(resolved.scheme == "https") { "源文件必须通过 HTTPS 下载：$relativePath" }
        require(resolved.host == base.host) { "源文件必须与索引同源：$relativePath" }
        return download(resolved.toString())
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("User-Agent", "SleepIn-Android")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            require(code in 200..299) { "源服务器返回 HTTP $code：$url" }
            bytes
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Reads a source from a directory on the device.
 *
 * The user grants a **directory**, not a single file: the runner and family files an index refers to sit
 * next to it, and a single-file grant cannot reach siblings. The index must be named
 * [SOURCE_INDEX_FILE_NAME]; everything else is looked up by the relative paths the index declares.
 *
 * Paths pass the same safety rules as every other storage layer first. The tree grant would refuse an
 * escape on its own, but keeping both means neither has to be trusted alone.
 */
internal class TreeImportSourceReader(
    private val rootUri: Uri,
    private val contentResolver: ContentResolver
) : ImportSourceReader {

    override suspend fun readIndex(): ByteArray? = read(SOURCE_INDEX_FILE_NAME)

    override suspend fun read(relativePath: String): ByteArray? {
        if (!isSafeRelativePath(relativePath)) return null

        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(rootUri) }
            .getOrNull()
            ?: return null
        val documentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(rootUri, "$treeDocumentId/$relativePath")
        }.getOrNull()
            ?: return null

        return try {
            contentResolver.openInputStream(documentUri)?.use { stream -> stream.readBytes() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}
