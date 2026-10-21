package com.kurosu.sleepin.data.importer

/**
 * Identifier and path rules shared by everything that turns a preset reference into a file.
 *
 * These are the app's security boundary for import presets, so they live in one place rather than
 * being restated by each storage layer: an imported source and a user's own presets both name files
 * the same way, and both have to be checked the same way.
 *
 * The layout is deliberately identical across sources — `<root>/presets/<id>/<script>` — so the same
 * preset can be moved between them, and so a rule that is enforced for one source is meaningful for
 * the others.
 */

/** Name of the script file inside every preset folder. One script per preset, by construction. */
internal const val PRESET_SCRIPT_FILE_NAME: String = "script.js"

private val PRESET_ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]{1,63}$")

/**
 * Whether [id] is an identifier the app is willing to resolve to a storage location.
 *
 * Lowercase ASCII and dashes only: the id becomes part of a path, and restricting it this way means
 * there is no encoding, case-folding or separator to worry about.
 */
internal fun isValidPresetId(id: String): Boolean = PRESET_ID_PATTERN.matches(id)

/** Path of [presetId]'s script, relative to a source's own root. */
internal fun scriptPathFor(presetId: String): String = "presets/$presetId/$PRESET_SCRIPT_FILE_NAME"

/**
 * Whether [path] is a relative path that cannot escape the directory it is resolved against.
 *
 * `\`, `:` and `%` are rejected as well as traversal segments. None of them can appear in a path this
 * app resolves today, and an encoded form (`%2e%2e`) would only become dangerous once some consumer
 * decodes it — which is exactly the kind of later change that should not be able to turn a validated
 * path into an escape.
 */
internal fun isSafeRelativePath(path: String): Boolean {
    if (path.isEmpty()) return false
    if (path.startsWith("/") || path.startsWith("\\")) return false
    if (path.contains('\\') || path.contains(':') || path.contains('%')) return false
    return path.split('/').none { segment ->
        segment.isEmpty() || segment == "." || segment == ".."
    }
}
