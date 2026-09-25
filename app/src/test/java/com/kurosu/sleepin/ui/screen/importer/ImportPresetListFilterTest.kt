package com.kurosu.sleepin.ui.screen.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.model.importer.customSchoolIdForName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the preset picker's filtering and grouping.
 */
class ImportPresetListFilterTest {

    @Test
    fun sections_groupsEverySourceUnderTheSameSchool() {
        val schoolId = customSchoolIdForName("示例大学")
        val sections = ImportPresetListFilter.sections(
            presets = listOf(
                preset(id = "bundled-a", name = "本科系统", source = ImportPresetSourceKind.BUNDLED,
                    schoolId = schoolId, schoolName = "示例大学"),
                preset(id = "custom-a", name = "研究生系统", source = ImportPresetSourceKind.CUSTOM,
                    schoolId = schoolId, schoolName = "示例大学"),
                preset(id = "remote-a", name = "新门户", source = ImportPresetSourceKind.REMOTE,
                    schoolId = schoolId, schoolName = "示例大学")
            ),
            query = ""
        )

        assertEquals(1, sections.schools.size)
        assertEquals("示例大学", sections.schools.single().name)
        assertEquals(listOf("bundled-a", "custom-a", "remote-a"), sections.schools.single().presets.map { it.id })
    }

    @Test
    fun sections_placesUnassociatedToolsInGeneralSection() {
        val sections = ImportPresetListFilter.sections(
            presets = listOf(preset(id = "generic", name = "通用工具")),
            query = ""
        )

        assertEquals(listOf("generic"), sections.general.map { it.id })
        assertTrue(sections.schools.isEmpty())
    }

    @Test
    fun sections_keepsTheOrderTheRepositoryReturned() {
        val sections = ImportPresetListFilter.sections(
            presets = listOf(
                preset(id = "b", name = "乙", group = ImportPresetGroup.UNDERGRADUATE,
                    schoolId = "test-school", schoolName = "测试学校"),
                preset(id = "a", name = "甲", group = ImportPresetGroup.UNDERGRADUATE,
                    schoolId = "test-school", schoolName = "测试学校"),
                preset(id = "c", name = "丙", group = ImportPresetGroup.UNDERGRADUATE,
                    schoolId = "test-school", schoolName = "测试学校")
            ),
            query = ""
        )

        // Display order is the repository's contract; the split must not re-sort it.
        assertEquals(listOf("b", "a", "c"), sections.schools.single().presets.map { it.id })
    }

    @Test
    fun matches_findsByWholeName() {
        assertTrue(ImportPresetListFilter.matches(preset(id = "a", name = "示例高校"), "示例"))
    }

    @Test
    fun matches_findsByInitialRegardlessOfCase() {
        val target = preset(id = "a", name = "示例高校", initial = "S")

        // A Chinese name contains no Latin letter, so a hit here can only come from the initial.
        assertTrue(ImportPresetListFilter.matches(target, "S"))
        assertTrue(ImportPresetListFilter.matches(target, "s"))
    }

    @Test
    fun matches_doesNotMatchAPresetWhoseInitialIsDifferent() {
        assertFalse(ImportPresetListFilter.matches(preset(id = "b", name = "大学", initial = "D"), "X"))
    }

    @Test
    fun matches_findsSchoolByOfficialCodeAndAlias() {
        val target = preset(
            id = "school-script",
            name = "本科系统",
            schoolId = "school-sample",
            schoolName = "示例高校",
            schoolCode = "SAMPLE-001",
            schoolAliases = listOf("示例简称")
        )
        assertTrue(ImportPresetListFilter.sections(listOf(target), "SAMPLE-001").schools.singleOrNull() != null)
        assertTrue(ImportPresetListFilter.sections(listOf(target), "示例简称").schools.singleOrNull() != null)
    }

    @Test
    fun matches_findsByDescription() {
        assertTrue(
            ImportPresetListFilter.matches(
                preset(id = "a", name = "某校", description = "使用正方系统"),
                "正方"
            )
        )
    }

    @Test
    fun matches_treatsABlankQueryAsEverything() {
        assertTrue(ImportPresetListFilter.matches(preset(id = "a", name = "某校"), "   "))
    }

    @Test
    fun matches_trimsTheQuery() {
        assertTrue(ImportPresetListFilter.matches(preset(id = "a", name = "示例大学"), "  示例  "))
    }

    @Test
    fun sections_reportsEmptyWhenNothingMatches() {
        val sections = ImportPresetListFilter.sections(
            presets = listOf(preset(id = "a", name = "某校")),
            query = "不存在的学校"
        )

        assertTrue(sections.isEmpty)
        assertTrue(sections.schools.isEmpty())
        assertTrue(sections.general.isEmpty())
    }

    private fun preset(
        id: String,
        name: String = id,
        group: ImportPresetGroup = ImportPresetGroup.GENERAL,
        initial: String = "A",
        description: String = "",
        source: ImportPresetSourceKind = ImportPresetSourceKind.BUNDLED,
        schoolId: String? = null,
        schoolName: String? = null,
        schoolCode: String? = null,
        schoolAliases: List<String> = emptyList()
    ) = ImportPreset(
        id = id,
        name = name,
        group = group,
        initial = initial,
        startUrl = "https://portal.example.invalid",
        scriptPath = "presets/$id/script.js",
        scriptSha256 = null,
        description = description,
        maintainer = "SleepIn",
        source = source,
        schoolId = schoolId,
        schoolName = schoolName,
        schoolCode = schoolCode,
        schoolAliases = schoolAliases
    )
}
