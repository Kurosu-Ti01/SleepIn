package com.kurosu.sleepin.domain.model.importer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * A school in the directory used to group its import scripts.
 *
 * [id] is an app-stable slug and is not inferred from the official code: codes can be absent or change,
 * while the same school may have several independent scripts. [aliases] and [code] are optional metadata.
 */
data class ImportPresetSchool(
    val id: String,
    val name: String,
    val code: String? = null,
    val aliases: List<String> = emptyList()
)

/**
 * Creates a deterministic, path-safe internal identity for a user-entered school name.
 *
 * The name is normalized so capitalization and repeated whitespace do not create separate schools.
 * Changing capitalization or whitespace in the same name does not change its key. Renaming the school
 * changes its key and requires its presets to be reassigned.
 */
fun customSchoolIdForName(name: String): String {
    val normalized = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(StandardCharsets.UTF_8))
        .take(10)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "school-$digest"
}
