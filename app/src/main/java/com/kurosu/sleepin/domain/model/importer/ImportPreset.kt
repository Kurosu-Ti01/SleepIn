package com.kurosu.sleepin.domain.model.importer

/**
 * Where an import preset came from.
 *
 * This is an identity, not a priority: merge order is decided by the order the sources are handed to
 * the repository, so a caller can put a downloaded source after a bundled one and have it win.
 */
enum class ImportPresetSourceKind {
    /** Packaged inside the APK. */
    BUNDLED,

    /** Added by the user from a script file on the device. */
    CUSTOM,

    /** Downloaded from the configured preset source. */
    REMOTE
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
 * This is the runtime shape shared by every source. The wire format (`index.json`) carries the same
 * fields; [source] is added while parsing so callers can tell a bundled preset from a user's own.
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
    /**
     * Lowercase hex SHA-256 of the script file.
     *
     * Optional for bundled presets, which ship inside the APK and cannot change underneath the user.
     * Required for remotely downloaded ones, where it is the only thing that makes the index and the
     * script it points at a single trustworthy unit.
     */
    val scriptSha256: String?,
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

/**
 * Parsed contents of an `index.json`.
 */
data class ImportPresetIndex(
    val schemaVersion: Int,
    val indexVersion: Int,
    val presets: List<ImportPreset>,
    val schools: List<ImportPresetSchool> = emptyList()
)

/**
 * Outcome of decoding an index file.
 */
sealed interface ImportPresetIndexParseResult {
    data class Parsed(val index: ImportPresetIndex) : ImportPresetIndexParseResult

    /**
     * The index is unusable.
     *
     * [message] is diagnostic text rather than something to show the user: a broken index means a
     * packaging or publication mistake, and the app degrades to the sources it can read instead of
     * interrupting the user with an error they cannot act on.
     */
    data class Invalid(val message: String) : ImportPresetIndexParseResult
}
