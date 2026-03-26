package com.kurosu.sleepin.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for semantic-like version comparison used by update checks.
 */
class VersionNameComparatorTest {

    @Test
    fun compare_higherRemoteVersion_returnsNegativeForCurrentVsRemote() {
        val result = VersionNameComparator.compare("1.0.0", "1.1.0")

        assertTrue(result < 0)
    }

    @Test
    fun compare_equalWithVPrefix_returnsZero() {
        val result = VersionNameComparator.compare("v1.2.3", "1.2.3")

        assertEquals(0, result)
    }

    @Test
    fun compare_prereleaseIsOlderThanStable() {
        val result = VersionNameComparator.compare("1.2.3-beta", "1.2.3")

        assertTrue(result < 0)
    }
}

