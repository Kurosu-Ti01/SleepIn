package com.kurosu.sleepin.domain.model.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the stable identity used when a user adds a school not present in the directory. */
class ImportPresetSchoolTest {

    @Test
    fun customSchoolIdForName_normalizesCaseAndWhitespace() {
        assertEquals(customSchoolIdForName(" Example   University "), customSchoolIdForName("example university"))
    }

    @Test
    fun customSchoolIdForName_isSafeAndDistinctForDifferentNames() {
        val schoolId = customSchoolIdForName("示例高校甲")

        assertTrue(schoolId.matches(Regex("^school-[0-9a-f]{20}$")))
        assertNotEquals(schoolId, customSchoolIdForName("示例高校乙"))
    }
}
