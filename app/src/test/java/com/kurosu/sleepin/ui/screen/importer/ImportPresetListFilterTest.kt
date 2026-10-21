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
                preset(id = "imported-a", name = "本科系统", source = ImportPresetSourceKind.REMOTE,
                    schoolId = schoolId, schoolName = "示例大学"),
                preset(id = "custom-a", name = "研究生系统", source = ImportPresetSourceKind.CUSTOM,
                    schoolId = schoolId, schoolName = "示例大学"),
                preset(id = "imported-b", name = "新门户", source = ImportPresetSourceKind.REMOTE,
                    schoolId = schoolId, schoolName = "示例大学")
            ),
            query = ""
        )

        assertEquals(1, sections.schools.size)
        assertEquals("示例大学", sections.schools.single().name)
        assertEquals(listOf("imported-a", "custom-a", "imported-b"), sections.schools.single().presets.map { it.id })
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

    @Test
    fun sections_filesSchoolsUnderTheLetterTheirPresetsDeclare() {
        // The community registry files 北京大学 under "B" — its pinyin initial, which the app cannot
        // derive from the name itself without shipping a pinyin table.
        val sections = ImportPresetListFilter.sections(
            presets = listOf(
                preset(
                    id = "pku-undergraduate",
                    name = "北京大学 · 本科教务",
                    initial = "B",
                    schoolId = "pku",
                    schoolName = "北京大学"
                )
            ),
            query = ""
        )

        assertEquals('B', sections.schools.single().initial)
    }

    @Test
    fun sections_ordersSchoolsByLetter_andPutsUnsortableOnesLast() {
        val sections = ImportPresetListFilter.sections(
            presets = listOf(
                preset(id = "cqu", name = "重庆大学", initial = "C", schoolId = "cqu", schoolName = "重庆大学"),
                preset(id = "mine", name = "我的学校", initial = "1", schoolId = "mine", schoolName = "我的学校"),
                preset(id = "pku", name = "北京大学", initial = "B", schoolId = "pku", schoolName = "北京大学"),
                preset(id = "ahu", name = "安徽大学", initial = "A", schoolId = "ahu", schoolName = "安徽大学")
            ),
            query = ""
        )

        assertEquals(
            listOf('A', 'B', 'C', UNSORTED_SCHOOL_INITIAL),
            sections.schools.map { section -> section.initial }
        )
    }

    @Test
    fun schoolInitialOf_prefersTheLetterTheSourceFiledTheSchoolUnder() {
        // The name starts with a character the app cannot sort, but the source knows the answer.
        assertEquals('B', schoolInitialOf("北京大学", listOf(preset(id = "x", initial = "B"))))
    }

    @Test
    fun schoolInitialOf_fallsBackToTheNameOfALatinSchool() {
        assertEquals('E', schoolInitialOf("Example University", emptyList()))
        assertEquals('E', schoolInitialOf("example university", emptyList()))
    }

    @Test
    fun schoolInitialOf_givesUpOnANameItCannotSort() {
        // A school the user typed in Chinese has no letter available anywhere; filing it under an
        // arbitrary letter would be worse than saying it is unsorted.
        assertEquals(UNSORTED_SCHOOL_INITIAL, schoolInitialOf("我的学校", emptyList()))
        assertEquals(UNSORTED_SCHOOL_INITIAL, schoolInitialOf("   ", emptyList()))
        assertEquals(
            UNSORTED_SCHOOL_INITIAL,
            schoolInitialOf("我的学校", listOf(preset(id = "x", initial = "1")))
        )
    }

    private fun preset(
        id: String,
        name: String = id,
        group: ImportPresetGroup = ImportPresetGroup.GENERAL,
        initial: String = "A",
        description: String = "",
        source: ImportPresetSourceKind = ImportPresetSourceKind.REMOTE,
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
        description = description,
        maintainer = "SleepIn",
        source = source,
        schoolId = schoolId,
        schoolName = schoolName,
        schoolCode = schoolCode,
        schoolAliases = schoolAliases
    )
}
