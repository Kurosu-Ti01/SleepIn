package com.kurosu.sleepin.domain.model.importer

/**
 * How a source preset obtains its adapter logic.
 *
 * The final WebView script is assembled on the device from the shared runner, a family adapter and
 * an optional school-specific piece. Sources therefore do not need to publish generated scripts.
 */
enum class ImportPresetAssemblyMode {
    /** Reuse the family adapter with only the preset's configuration values. */
    FAMILY,

    /** Reuse the family adapter and append a small school-specific override. */
    OVERRIDE,

    /** Use a school-specific adapter without a family. */
    REWRITE
}

/** A source the user wants to import, named by its index address. */
data class ImportSourceDraft(
    val indexUrl: String,
    val reuseLocalFamilies: Boolean
)

/**
 * A source the user wants to import from a directory on the device.
 *
 * The directory is identified by a URI string rather than a platform type so the domain layer stays
 * free of Android APIs. What is remembered is a **directory**, not a file: the runner and family files
 * an index refers to sit beside it, and only a directory grant can reach them.
 */
data class ImportLocalSourceDraft(
    /** URI text of the directory the user granted. */
    val rootUri: String,
    val reuseLocalFamilies: Boolean
)

/** What the preset editor collected to build a script out of a family cached on this device. */
data class FamilyAssemblyRequest(
    val familyId: String,
    /** Name of the preset being built; the assembled script reports it to the user while importing. */
    val presetName: String,
    /** The preset's own values as JSON. Blank means "use the family defaults unchanged". */
    val configJson: String,
    /** Optional school-specific override, appended after the family adapter. */
    val overrideText: String?
)

/** Outcome of building a script from a family cached on this device. */
sealed interface FamilyAssemblyResult {

    data class Assembled(val script: String) : FamilyAssemblyResult

    /**
     * Nothing usable was built; [message] is user-facing.
     *
     * Only one kind of failure is worth reporting separately: everything that can go wrong here — a
     * missing cached file, unusable JSON, an assembly error — ends in "fix this and press save again".
     */
    data class Failed(val message: String) : FamilyAssemblyResult
}

/** Outcome of importing one source. */
sealed interface ImportSourceImportResult {
    data class Imported(
        val presetCount: Int,
        val familyCount: Int
    ) : ImportSourceImportResult

    /**
     * The source has not changed since it was last imported, so nothing was downloaded or written.
     *
     * This is a **success**, not a skip: the presets on the device are already the current release.
     * [indexVersion] is what the version check compared.
     */
    data class Unchanged(val indexVersion: Int) : ImportSourceImportResult

    /** The source or the user's input cannot be used as given; [message] says what to fix. */
    data class Rejected(val message: String) : ImportSourceImportResult

    /** The source looked usable but the work did not finish; [message] is user-facing. */
    data class Failed(val message: String) : ImportSourceImportResult
}

/**
 * A source the device has already imported, as the settings screen needs to show it.
 *
 * @param sourceKey the link or `local:` identifier it was imported from
 * @param indexVersion the version currently registered, useful to tell two copies apart
 * @param presetCount how many presets it contributes
 * @param displayName name the user gave it, or null to fall back to something derived from the address
 */
data class RegisteredImportSource(
    val sourceKey: String,
    val indexVersion: Int,
    val presetCount: Int,
    val displayName: String? = null
) {
    /** Whether this source was a directory on the device rather than a link. */
    val isLocal: Boolean get() = sourceKey.startsWith(LOCAL_SOURCE_KEY_PREFIX)
}

/**
 * Prefix of the key a directory source is registered under.
 *
 * A directory identifier and a link can be the same text, so the prefix is what keeps the two apart.
 */
const val LOCAL_SOURCE_KEY_PREFIX: String = "local:"
