package com.kurosu.sleepin.ui.screen.importer

/**
 * How the import directory is laid out, letter by letter.
 *
 * Part of the picker rather than the filter because it is about presentation, but kept out of the
 * Compose file because it is pure list work: which row a letter starts at is exactly the kind of thing
 * that is worth testing without rendering a screen.
 */
internal sealed interface ImportPresetDirectoryRow {

    /** A titled block, used for "schools" and for "general tools". */
    data class Header(val title: String, val subtitle: String?) : ImportPresetDirectoryRow

    /** The A–Z (or #) divider that a school starts under. */
    data class Letter(val initial: Char) : ImportPresetDirectoryRow

    data class School(val item: ImportPresetSchoolUi) : ImportPresetDirectoryRow

    data class Preset(val item: ImportPresetItemUi) : ImportPresetDirectoryRow
}

/**
 * Flattens the directory into rows, inserting a letter divider whenever the initial changes.
 *
 * The dividers are omitted when every school shares one letter: a single "B" above the whole list takes
 * space and says nothing. That also keeps the jump bar away, because it is built from these dividers.
 */
internal fun directoryRows(
    schools: List<ImportPresetSchoolUi>,
    general: List<ImportPresetItemUi>
): List<ImportPresetDirectoryRow> = buildList {
    if (schools.isNotEmpty()) {
        add(ImportPresetDirectoryRow.Header(title = "学校", subtitle = "选择学校后查看其导入方案"))

        val withDividers = schools.map { school -> school.initial }.distinct().size > 1
        var currentInitial: Char? = null
        schools.forEach { school ->
            if (withDividers && school.initial != currentInitial) {
                currentInitial = school.initial
                add(ImportPresetDirectoryRow.Letter(school.initial))
            }
            add(ImportPresetDirectoryRow.School(school))
        }
    }

    if (general.isNotEmpty()) {
        add(ImportPresetDirectoryRow.Header(title = "通用工具", subtitle = null))
        general.forEach { item -> add(ImportPresetDirectoryRow.Preset(item)) }
    }
}

/**
 * The row each letter starts at, in the order the letters appear.
 *
 * This is what the jump bar needs: a letter maps to a list position, and positions are only valid for the
 * rows they were computed from.
 */
internal fun letterAnchorsOf(rows: List<ImportPresetDirectoryRow>): List<Pair<Char, Int>> =
    rows.mapIndexedNotNull { index, row ->
        (row as? ImportPresetDirectoryRow.Letter)?.let { letter -> letter.initial to index }
    }
