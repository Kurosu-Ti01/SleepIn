package com.kurosu.sleepin.domain.model.importer

/**
 * Where an import preset came from.
 *
 * This is an identity, not a priority: merge order is decided by the order the sources are handed to
 * the repository, so a caller can put an imported source after the user's own and have it win. The app
 * packages no presets itself, so every preset the user sees was either imported or written by them.
 */
enum class ImportPresetSourceKind {
    /** Added by the user from a script file on the device, or assembled in the editor. */
    CUSTOM,

    /** Imported from a source the user pointed at: a link, or a directory on the device. */
    REMOTE,

    /**
     * Synthesized by the app itself, for testing the import flow without a real portal.
     *
     * A preset of this kind can only appear when something supplies one: the debug build registers
     * a source that hands out a fixed sample, and release builds register no such source. Keeping this
     * a separate kind is what lets a preset be read back from the source that owns it, and keeps the
     * sample from ever colliding with a real preset.
     */
    DEBUG
}

/**
 * Display grouping of a preset.
 *
 * Kept as variant metadata inside a school's script list; school identity and top-level grouping are
 * represented separately by [ImportPreset.schoolId].
 */
enum class ImportPresetGroup {
    /** Bachelor and associate degree programmes. */
    UNDERGRADUATE,

    /** Postgraduate programmes. */
    POSTGRADUATE,

    /** Tools that belong to no single school. */
    GENERAL
}

/**
 * One import preset: the metadata describing an import script and where to start reading.
 *
 * This is the runtime shape shared by every source. The stored form carries the same fields;
 * [source] is filled in by the source that produced the preset, so callers can tell them apart.
 */
data class ImportPreset(
    /** Stable identifier, also the name of the folder holding the script. */
    val id: String,
    val name: String,
    val group: ImportPresetGroup,
    /** Single uppercase letter used to group and search the list. */
    val initial: String,
    /**
     * Page to open before running the script. Null means the WebView starts blank and shows an address
     * bar instead, which is how a preset without a fixed login page works.
     */
    val startUrl: String?,
    /** Path of the script relative to the source root, always inside `presets/<id>/`. */
    val scriptPath: String,
    /** Shown in the preset list and on the import screen; carries any per-school usage hint. */
    val description: String,
    val maintainer: String,
    val source: ImportPresetSourceKind,
    /** Stable school identity shared by all of its import scripts; null means a general tool. */
    val schoolId: String? = null,
    /** School display name, separate from [name], which names this particular script variant. */
    val schoolName: String? = null,
    /** Optional official school code; useful for search, but not used as the internal identity. */
    val schoolCode: String? = null,
    /** Other searchable names for the school. */
    val schoolAliases: List<String> = emptyList()
)
