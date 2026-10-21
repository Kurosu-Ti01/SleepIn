package com.kurosu.sleepin.data.repository

import com.kurosu.sleepin.domain.model.importer.LOCAL_SOURCE_KEY_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the directory name a source's assembled scripts live under.
 *
 * Two places have to agree on it — the import that writes the scripts and the forget that deletes them —
 * and a disagreement would be silent: the import would succeed and the cleanup would leave megabytes
 * behind. The value also has to stay what earlier versions wrote, so a source imported before this rule
 * was extracted still gets its scripts replaced rather than duplicated.
 */
class ImportSourceDirectoryKeyTest {

    @Test
    fun key_separatesLinksFromDirectories() {
        val link = sourceDirectoryKey("https://example.invalid/source-index.json")
        val directory = sourceDirectoryKey("${LOCAL_SOURCE_KEY_PREFIX}content://tree/primary%3ASleepIn")

        assertTrue("链接源的目录前缀应当保持不变", link.startsWith("remote-"))
        assertTrue("目录源的目录前缀应当保持不变", directory.startsWith("local-"))
        assertNotEquals(link, directory)
    }

    @Test
    fun key_isStableForTheSameSource() {
        val url = "https://example.invalid/source-index.json"

        assertEquals(sourceDirectoryKey(url), sourceDirectoryKey(url))
        // Same 16 hex characters, whatever the source: the length is what keeps directory names readable.
        assertEquals("remote-".length + 16, sourceDirectoryKey(url).length)
    }

    @Test
    fun key_givesTwoSourcesDifferentDirectories() {
        assertNotEquals(
            sourceDirectoryKey("https://a.example.invalid/source-index.json"),
            sourceDirectoryKey("https://b.example.invalid/source-index.json")
        )
    }

    @Test
    fun key_hashesTheDirectoryUriWithoutItsPrefix() {
        // The prefix is a registry concern (`local:` keeps a URI from colliding with a link); the directory
        // name is derived from the URI alone, which is what earlier versions wrote.
        val uri = "content://tree/primary%3ASleepIn"
        val expected = "local-" + sourceDirectoryKey(uri).removePrefix("remote-")

        assertEquals(expected, sourceDirectoryKey(LOCAL_SOURCE_KEY_PREFIX + uri))
    }
}
