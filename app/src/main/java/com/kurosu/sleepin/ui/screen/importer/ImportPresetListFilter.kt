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
    val presets: List<ImportPreset>
)

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
                val section = ImportPresetSchoolSection(
                    id = id,
                    name = first.schoolName ?: first.name,
                    code = first.schoolCode,
                    aliases = first.schoolAliases,
                    presets = schoolPresets
                )
                if (matchesSchool(section, query)) section else null
            }
            .sortedWith(compareBy({ section -> section.name }, { section -> section.id }))

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
