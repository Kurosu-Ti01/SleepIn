package com.kurosu.sleepin.data.importer

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes a script file the user picked from their device.
 *
 * A `.js` file saved by a Chinese Windows editor is often GBK rather than UTF-8, and decoding those
 * bytes as UTF-8 produces replacement characters instead of an error. That failure is expensive: the
 * script looks present, the preset saves, and the import simply does not work, with nothing pointing
 * at the encoding.
 *
 * The policy differs from the CSV decoder, which scores candidate charsets by matching known column
 * headers. A script has no headers to match, so the rule here is narrower: a byte order mark decides
 * when present, otherwise the first charset that decodes the whole file without error wins, starting
 * with UTF-8.
 */
object ImportScriptTextDecoder {

    /**
     * Candidate charsets, in the order they are tried.
     *
     * GB18030 comes second because it is a superset of GBK and GB2312, and because UTF-8 has to be
     * tried first: it rejects most Chinese legacy byte sequences, whereas GB18030 accepts almost
     * anything, so trying it first would turn every UTF-8 script into mojibake.
     */
    private val charsetCandidates: List<Charset> = listOfNotNull(
        Charsets.UTF_8,
        runCatching { Charset.forName("GB18030") }.getOrNull()
    )

    /**
     * Turns [bytes] into text, never throwing.
     *
     * When no candidate decodes cleanly the bytes are returned decoded as UTF-8 anyway, replacement
     * characters included: a script the user can read and fix by hand is more useful than an empty one.
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        reorderByBom(bytes).forEach { charset ->
            decodeStrict(bytes, charset)?.let { decoded ->
                // A byte order mark survives decoding as a zero-width character at the start. It is legal
                // JavaScript whitespace, but it is not part of the script and would show up in the
                // preview, so it is dropped here.
                return decoded.removePrefix("\uFEFF")
            }
        }

        return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun reorderByBom(bytes: ByteArray): List<Charset> {
        val bomCharset = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> Charsets.UTF_8

            else -> null
        } ?: return charsetCandidates

        return listOf(bomCharset) + charsetCandidates.filterNot {
            it.name().equals(bomCharset.name(), ignoreCase = true)
        }
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }
}
