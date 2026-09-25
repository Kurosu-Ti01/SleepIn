package com.kurosu.sleepin.data.importer

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for decoding a user-picked script file.
 *
 * The case worth protecting is the quiet one: a GBK script decoded as UTF-8 does not fail, it produces
 * a script full of replacement characters that simply never works, and nothing in the app would point
 * at the encoding.
 */
class ImportScriptTextDecoderTest {

    @Test
    fun decode_readsUtf8Text() {
        val text = "// 中文注释\nvar a = 1;\n"

        assertEquals(text, ImportScriptTextDecoder.decode(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decode_readsTextSavedAsGb18030() {
        val text = "// 中文注释：课程表\nvar a = 1;\n"
        val gb18030 = Charset.forName("GB18030")
        val bytes = text.toByteArray(gb18030)

        // The same bytes are not valid UTF-8, which is why the fallback exists.
        assertEquals(text, ImportScriptTextDecoder.decode(bytes))
    }

    @Test
    fun decode_usesTheByteOrderMarkWhenThereIsOne() {
        val text = "var a = 1;"
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)

        // The mark itself must not survive into the script.
        assertEquals(text, ImportScriptTextDecoder.decode(withBom))
    }

    @Test
    fun decode_returnsEmptyForNoBytes() {
        assertEquals("", ImportScriptTextDecoder.decode(ByteArray(0)))
    }

    @Test
    fun decode_readsPlainAsciiTheSameWayEitherCharsetWould() {
        val text = "alert('hi');"

        assertEquals(text, ImportScriptTextDecoder.decode(text.toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun decode_keepsGoingWhenNothingDecodesCleanly() {
        // 0xFF is not valid in UTF-8 or in GB18030's two-byte forms, so no candidate accepts it and the
        // bytes are decoded anyway. Losing the file entirely would leave the user with nothing to fix.
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41)

        val decoded = ImportScriptTextDecoder.decode(bytes)

        assertTrue(decoded.isNotEmpty())
    }
}
