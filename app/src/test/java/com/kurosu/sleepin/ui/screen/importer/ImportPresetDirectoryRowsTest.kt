package com.kurosu.sleepin.ui.screen.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the directory's row layout and the letter anchors built from it.
 *
 * The anchors are list **indices**, so they are only meaningful for the exact rows they came from. That
 * is why they are derived in one pass together with the rows rather than computed by the screen: a jump
 * bar that scrolls to the wrong row is a bug that only shows up once a letter header is added or removed.
 */
class ImportPresetDirectoryRowsTest {

    @Test
    fun rows_insertOneLetterHeaderPerRun() {
        val rows = directoryRows(
            schools = listOf(
                school("ahu", "安徽大学", 'A'),
                school("pku", "北京大学", 'B'),
                school("pku-2", "北京大学（研究生）", 'B'),
                school("cqu", "重庆大学", 'C')
            ),
            general = emptyList()
        )

        assertEquals(
            listOf(
                ImportPresetDirectoryRow.Header(title = "学校", subtitle = "选择学校后查看其导入方案"),
                ImportPresetDirectoryRow.Letter('A'),
                ImportPresetDirectoryRow.School(school("ahu", "安徽大学", 'A')),
                ImportPresetDirectoryRow.Letter('B'),
                ImportPresetDirectoryRow.School(school("pku", "北京大学", 'B')),
                ImportPresetDirectoryRow.School(school("pku-2", "北京大学（研究生）", 'B')),
                ImportPresetDirectoryRow.Letter('C'),
                ImportPresetDirectoryRow.School(school("cqu", "重庆大学", 'C'))
            ),
            rows
        )
    }

    @Test
    fun rows_skipLetterHeadersWhenEverythingSharesOneLetter() {
        // A lone "B" above the whole list costs a row and tells the user nothing, and it would also
        // switch on a jump bar with a single entry.
        val rows = directoryRows(
            schools = listOf(school("pku", "北京大学", 'B'), school("bit", "北京理工大学", 'B')),
            general = emptyList()
        )

        assertTrue(rows.none { it is ImportPresetDirectoryRow.Letter })
        assertTrue(letterAnchorsOf(rows).isEmpty())
    }

    @Test
    fun rows_placeGeneralToolsAfterTheSchools() {
        val rows = directoryRows(
            schools = listOf(school("ahu", "安徽大学", 'A')),
            general = listOf(item("generic-blank"))
        )

        assertEquals(
            ImportPresetDirectoryRow.Header(title = "通用工具", subtitle = null),
            rows[rows.size - 2]
        )
        assertEquals(ImportPresetDirectoryRow.Preset(item("generic-blank")), rows.last())
    }

    @Test
    fun rows_handleAnEmptyDirectory() {
        assertTrue(directoryRows(schools = emptyList(), general = emptyList()).isEmpty())
    }

    @Test
    fun letterAnchors_pointAtTheRowEachLetterStartsAt() {
        val rows = directoryRows(
            schools = listOf(
                school("ahu", "安徽大学", 'A'),
                school("pku", "北京大学", 'B'),
                school("cqu", "重庆大学", 'C')
            ),
            general = emptyList()
        )

        // Header, then A at 1, school at 2, letter B at 3, school at 4, letter C at 5, …
        assertEquals(listOf('A' to 1, 'B' to 3, 'C' to 5), letterAnchorsOf(rows))
        assertEquals(ImportPresetDirectoryRow.Letter('C'), rows[5])
    }

    private fun school(id: String, name: String, initial: Char) = ImportPresetSchoolUi(
        id = id,
        name = name,
        code = null,
        initial = initial,
        scriptCount = 1
    )

    private fun item(id: String) = ImportPresetItemUi(
        id = id,
        name = id,
        description = "",
        maintainer = "",
        isCustom = false
    )
}
