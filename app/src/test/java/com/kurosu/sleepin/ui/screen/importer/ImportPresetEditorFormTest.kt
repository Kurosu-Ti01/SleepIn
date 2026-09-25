package com.kurosu.sleepin.ui.screen.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the initial field's behaviour.
 *
 * The rule that is easy to get wrong is the one about not fighting the user: an initial that follows the
 * name is helpful, and an initial that overwrites what the user typed is a bug they would only notice as
 * "it keeps changing my letter back".
 */
class ImportPresetEditorFormTest {

    @Test
    fun initialFromName_takesTheFirstLatinLetter() {
        assertEquals("P", initialFromName("Portal system"))
        assertEquals("J", initialFromName("jwc.example.invalid"))
    }

    @Test
    fun initialFromName_returnsNullForANameWithNoLatinLetter() {
        // Deriving the initial of a Chinese name would need a transliteration table this app does not
        // ship, so a Chinese-only name has nothing to derive from and the caller uses its fallback.
        assertNull(initialFromName("示例大学"))
        assertNull(initialFromName("另一所示例高校"))
        assertNull(initialFromName(""))
    }

    @Test
    fun initialAfterNameChange_followsTheNameWhileTheUserHasNotChosenOne() {
        val result = initialAfterNameChange(
            previousName = "",
            newName = "Portal system",
            currentInitial = FALLBACK_PRESET_INITIAL
        )

        assertEquals("P", result)
    }

    @Test
    fun initialAfterNameChange_keepsAHandTypedInitial() {
        val result = initialAfterNameChange(
            previousName = "Abc University",
            newName = "Portal system",
            currentInitial = "Q"
        )

        // "Q" is not what "Abc University" suggested, so the user chose it and it must survive the rename.
        assertEquals("Q", result)
    }

    @Test
    fun initialAfterNameChange_fallsBackWhenTheNameHasNoLatinLetter() {
        val result = initialAfterNameChange(
            previousName = "",
            newName = "示例大学",
            currentInitial = FALLBACK_PRESET_INITIAL
        )

        assertEquals(FALLBACK_PRESET_INITIAL, result)
    }

    @Test
    fun sanitizeInitialInput_keepsOnlyTheFirstLetterInUpperCase() {
        assertEquals("P", sanitizeInitialInput("p"))
        assertEquals("P", sanitizeInitialInput("Portal"))
        assertEquals("X", sanitizeInitialInput("1x2"))
        assertEquals("", sanitizeInitialInput("123"))
        assertEquals("", sanitizeInitialInput(""))
    }
}
