package com.kurosu.sleepin.domain.model.importer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the one piece of a registered source the UI reads directly.
 *
 * The source list picks its icon from [RegisteredImportSource.isLocal], so getting this wrong is a row
 * that claims a directory came from the network.
 */
class RegisteredImportSourceTest {

    @Test
    fun isLocal_distinguishesADirectoryFromALink() {
        assertFalse(RegisteredImportSource("https://example.invalid/source-index.json", 3, 5).isLocal)
        assertTrue(RegisteredImportSource("${LOCAL_SOURCE_KEY_PREFIX}content://tree/primary%3ASleepIn", 1, 2).isLocal)
    }

    @Test
    fun isLocal_onlyCountsThePrefixAtTheStart() {
        // A link is free to mention "local:" anywhere in its path; only a prefixed key is a directory.
        assertFalse(
            RegisteredImportSource("https://example.invalid/local:mirror/source-index.json", 1, 1).isLocal
        )
    }
}
