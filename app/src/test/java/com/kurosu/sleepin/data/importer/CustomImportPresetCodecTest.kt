package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.model.importer.customSchoolIdForName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure part of custom preset persistence.
 *
 * The stored document is user data the app cannot regenerate, so the interesting cases are the
 * damaged ones: a corrupt document, one written by a newer build, and single unusable entries inside
 * an otherwise fine list.
 */
class CustomImportPresetCodecTest {

    @Test
    fun encode_thenDecode_keepsEveryField() {
        val stored = listOf(
            CustomImportPresetDto(
                id = "custom-abc",
                name = "Sample campus",
                group = ImportPresetGroup.UNDERGRADUATE.name,
                initial = "S",
                startUrl = "https://portal.example.invalid",
                description = "Follow the portal instructions.",
                maintainer = "someone",
                schoolId = "school-sample",
                schoolName = "Sample campus",
                schoolCode = "SAMPLE-001"
            )
        )

        val decoded = CustomImportPresetCodec.decode(CustomImportPresetCodec.encode(stored))

        assertEquals(stored, (decoded as CustomImportPresetDecodeResult.Decoded).presets)
    }

    @Test
    fun decode_treatsMissingOrBlankStorageAsEmpty() {
        assertTrue(CustomImportPresetCodec.decode(null) is CustomImportPresetDecodeResult.Empty)
        assertTrue(CustomImportPresetCodec.decode("   ") is CustomImportPresetDecodeResult.Empty)
    }

    @Test
    fun decode_treatsCorruptStorageAsEmpty() {
        // Reported as empty rather than refused, so the next write can replace the damaged document.
        // Permanently refusing to save would be worse than losing the metadata.
        assertTrue(CustomImportPresetCodec.decode("{ not json") is CustomImportPresetDecodeResult.Empty)
    }

    @Test
    fun decode_refusesADocumentWrittenByANewerBuild() {
        val fromTheFuture = """{ "schemaVersion": 99, "presets": [] }"""

        // Reads report nothing, and the store refuses to write, so reinstalling an older build cannot
        // silently destroy presets the newer one created.
        assertTrue(CustomImportPresetCodec.decode(fromTheFuture) is CustomImportPresetDecodeResult.UnsupportedVersion)
    }

    @Test
    fun toImportPreset_mapsAStoredEntry() {
        val preset = CustomImportPresetDto(
            id = "custom-abc",
            name = "  Sample campus  ",
            group = ImportPresetGroup.POSTGRADUATE.name,
            initial = "s",
            startUrl = " https://portal.example.invalid ",
            description = " Description ",
            maintainer = " Someone "
        ).toImportPresetOrNull()

        assertEquals("custom-abc", preset?.id)
        assertEquals("Sample campus", preset?.name)
        assertEquals(ImportPresetGroup.POSTGRADUATE, preset?.group)
        assertEquals("S", preset?.initial)
        assertEquals("https://portal.example.invalid", preset?.startUrl)
        assertEquals("Description", preset?.description)
        assertEquals("Someone", preset?.maintainer)
        assertEquals(ImportPresetSourceKind.CUSTOM, preset?.source)
        assertEquals(customSchoolIdForName("Sample campus"), preset?.schoolId)
        assertEquals("Sample campus", preset?.schoolName)
        // The path is derived, never stored: the id is the only thing that decides where the script is.
        assertEquals("presets/custom-abc/script.js", preset?.scriptPath)
    }

    @Test
    fun toImportPreset_migratesLegacyPresetNameToSchoolName() {
        val preset = storedDto(name = "Sample campus undergraduate preset").toImportPresetOrNull()

        assertEquals("Sample campus undergraduate preset", preset?.schoolName)
        assertEquals(customSchoolIdForName("Sample campus undergraduate preset"), preset?.schoolId)
    }

    @Test
    fun toImportPreset_treatsABlankStartUrlAsAbsent() {
        val preset = storedDto(startUrl = "   ").toImportPresetOrNull()

        assertNull(preset?.startUrl)
    }

    @Test
    fun toImportPreset_skipsEntriesThatCannotBeUsed() {
        // An id becomes a folder name, so an unusable one would resolve to a location the store has to
        // refuse later; dropping it now keeps the list honest.
        assertNull(storedDto(id = "Custom-ABC").toImportPresetOrNull())
        assertNull(storedDto(id = "../escape").toImportPresetOrNull())
        assertNull(storedDto(id = "a").toImportPresetOrNull())
        assertNull(storedDto(name = "   ").toImportPresetOrNull())
        assertNull(storedDto(initial = "SS").toImportPresetOrNull())
        assertNull(storedDto(initial = "1").toImportPresetOrNull())
        assertNull(storedDto(group = "DOCTORATE").toImportPresetOrNull())
    }

    @Test
    fun generateId_producesASlugSafeId() {
        val id = generateCustomImportPresetId(taken = emptySet(), nowMillis = 1_700_000_000_000L)

        assertTrue(isValidPresetId(id))
        assertTrue(id.startsWith("custom-"))
    }

    @Test
    fun generateId_avoidsIdsThatAreAlreadyTaken() {
        val first = generateCustomImportPresetId(taken = emptySet(), nowMillis = 1_700_000_000_000L)

        // Two presets added in the same millisecond would otherwise share an id, and the id is also the
        // folder they are stored under.
        val second = generateCustomImportPresetId(taken = setOf(first), nowMillis = 1_700_000_000_000L)
        val third = generateCustomImportPresetId(taken = setOf(first, second), nowMillis = 1_700_000_000_000L)

        assertEquals(3, setOf(first, second, third).size)
        assertTrue(isValidPresetId(second))
        assertTrue(isValidPresetId(third))
    }

    @Test
    fun generateId_changesWithTheClock() {
        val earlier = generateCustomImportPresetId(taken = emptySet(), nowMillis = 1_000_000_000_000L)
        val later = generateCustomImportPresetId(taken = emptySet(), nowMillis = 1_000_000_001_000L)

        assertTrue(earlier != later)
    }

    private fun storedDto(
        id: String = "custom-abc",
        name: String = "Sample campus",
        group: String = ImportPresetGroup.UNDERGRADUATE.name,
        initial: String = "S",
        startUrl: String? = "https://portal.example.invalid"
    ) = CustomImportPresetDto(
        id = id,
        name = name,
        group = group,
        initial = initial,
        startUrl = startUrl
    )
}
