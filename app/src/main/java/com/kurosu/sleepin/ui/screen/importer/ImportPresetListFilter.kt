package com.kurosu.sleepin.ui.screen.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup

/**
 * The directory results shown by the picker.
 *
 * School scripts are grouped regardless of whether they are bundled, remote, or user-created. The
 * general section is reserved for tools that do not belong to a particular school.
 */
data class ImportPresetSections(
    val schools: List<ImportPresetSchoolSection>,
    val general: List<ImportPreset>
) {
    /** Whether the query matched nothing — which the screen has to distinguish from "nothing exists". */
    val isEmpty: Boolean get() = schools.isEmpty() && general.isEmpty()
}

/** All scripts belonging to one school, in repository order. */
data class ImportPresetSchoolSection(
    val id: String,
    val name: String,
    val code: String?,
    val aliases: List<String>,
    /** Letter this school is filed under; see [schoolInitialOf]. */
    val initial: Char,
    val presets: List<ImportPreset>
)

/**
 * Letter used for schools that cannot be filed under a letter at all.
 *
 * A user-created school with a name written in a script the app cannot derive an initial from lands
 * here. Sorting them last is deliberate: they are the exception, and they should not push the readable
 * letters down the list.
 */
internal const val UNSORTED_SCHOOL_INITIAL: Char = '#'

/**
 * The letter [sectionName] is filed under.
 *
 * The presets are asked first, because a source files each of them under one letter: in the community
 * registry that letter is the pinyin initial of the school name, which is precisely what an alphabetical
 * index needs and precisely what the app cannot compute from the name on its own — deriving it would
 * mean shipping a pinyin table.
 *
 * A school the user created has no such letter, so its own name is used when it is written in a script
 * the app can sort, and [UNSORTED_SCHOOL_INITIAL] otherwise.
 */
internal fun schoolInitialOf(sectionName: String, presets: List<ImportPreset>): Char {
    presets.forEach { preset ->
        val letter = preset.initial.trim().firstOrNull()
        if (letter != null && letter.isAsciiLetter()) return letter.uppercaseChar()
    }

    val first = sectionName.trim().firstOrNull()
    if (first != null && first.isAsciiLetter()) return first.uppercaseChar()
    return UNSORTED_SCHOOL_INITIAL
}

/** Sort key that keeps A–Z in order and pushes [UNSORTED_SCHOOL_INITIAL] to the end. */
private fun initialSortKey(initial: Char): Int =
    if (initial == UNSORTED_SCHOOL_INITIAL) Int.MAX_VALUE else initial.code

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * Filtering and school grouping for the preset picker.
 *
 * Kept apart from the ViewModel so school identity, query matching, and preservation of repository
 * ordering can be tested without rendering the screen.
 */
internal object ImportPresetListFilter {

    /**
     * Splits [presets] into the sections to display for [query].
     *
     * Groups with no matching preset are left out entirely, so a search never produces an empty heading.
     */
    fun sections(presets: List<ImportPreset>, query: String): ImportPresetSections {
        val schoolGroups = presets
            .filter { preset -> preset.schoolId != null }
            .groupBy { preset -> preset.schoolId!! }
            .mapNotNull { (id, schoolPresets) ->
                val first = schoolPresets.first()
                val name = first.schoolName ?: first.name
                val section = ImportPresetSchoolSection(
                    id = id,
                    name = name,
                    code = first.schoolCode,
                    aliases = first.schoolAliases,
                    initial = schoolInitialOf(name, schoolPresets),
                    presets = schoolPresets
                )
                if (matchesSchool(section, query)) section else null
            }
            .sortedWith(
                compareBy(
                    { section -> initialSortKey(section.initial) },
                    { section -> section.name },
                    { section -> section.id }
                )
            )

        return ImportPresetSections(
            schools = schoolGroups,
            general = presets.filter { preset -> preset.schoolId == null && matches(preset, query) }
        )
    }

    private fun matchesSchool(section: ImportPresetSchoolSection, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val lowered = trimmed.lowercase()
        return section.name.lowercase().contains(lowered) ||
            section.code.orEmpty().lowercase().contains(lowered) ||
            section.aliases.any { alias -> alias.lowercase().contains(lowered) } ||
            section.presets.any { preset -> matches(preset, trimmed) }
    }

    /**
     * Whether [preset] should be listed for [query].
     *
     * A blank query matches everything. The initial is compared as a whole equality rather than a
     * prefix, because it is a single letter the user picked to stand for the name.
     */
    fun matches(preset: ImportPreset, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true

        val lowered = trimmed.lowercase()
        return preset.name.lowercase().contains(lowered) ||
            preset.description.lowercase().contains(lowered) ||
            preset.initial.equals(trimmed, ignoreCase = true) ||
            preset.schoolName.orEmpty().lowercase().contains(lowered) ||
            preset.schoolCode.orEmpty().lowercase().contains(lowered) ||
            preset.schoolAliases.any { alias -> alias.lowercase().contains(lowered) }
    }
}

/** Chinese label of a script's education-level variant. */
internal fun ImportPresetGroup.displayName(): String = when (this) {
    ImportPresetGroup.UNDERGRADUATE -> "本科 / 专科"
    ImportPresetGroup.POSTGRADUATE -> "研究生"
    ImportPresetGroup.GENERAL -> "通用"
}
