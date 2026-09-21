package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetIndex
import com.kurosu.sleepin.domain.model.importer.ImportPresetIndexParseResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Decodes and validates an `index.json` preset index.
 *
 * This is the gate every preset passes through, so it enforces the whole contract rather than only the
 * parts the app happens to read today: an index that reaches the repository is guaranteed to name
 * presets the rest of the app can use without further checking.
 *
 * Unknown keys are tolerated so an index published for a newer build stays usable, and missing ones
 * are reported with their JSON path because the same message is what a preset author sees when the
 * index they published is rejected.
 */
class ImportPresetIndexParser {

    /**
     * @param source recorded on every preset so later merge steps can tell where it came from.
     */
    fun parse(rawJson: String, source: ImportPresetSourceKind): ImportPresetIndexParseResult {
        if (rawJson.isBlank()) return invalid("index is empty")

        val dto = try {
            indexJson.decodeFromString(ImportPresetIndexDto.serializer(), rawJson)
        } catch (error: SerializationException) {
            return invalid("index is not a valid preset index: ${error.message ?: "decode failed"}")
        }

        val schemaVersion = dto.schemaVersion
            ?: return invalid("index has no schemaVersion")
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return invalid(
                "unsupported index schemaVersion $schemaVersion; this build reads $SUPPORTED_SCHEMA_VERSION"
            )
        }

        val indexVersion = dto.indexVersion
            ?: return invalid("index has no indexVersion")
        if (indexVersion < MIN_INDEX_VERSION) {
            return invalid("indexVersion must be at least $MIN_INDEX_VERSION")
        }

        val presets = mutableListOf<ImportPreset>()
        val seenIds = mutableSetOf<String>()
        dto.presets.orEmpty().forEachIndexed { index, preset ->
            val path = "presets[$index]"

            val id = preset.id?.trim().orEmpty()
            if (!PRESET_ID_PATTERN.matches(id)) {
                return invalid("$path.id must match ${PRESET_ID_PATTERN.pattern}, got '$id'")
            }
            if (!seenIds.add(id)) {
                return invalid("$path.id '$id' is used by another preset")
            }

            val name = preset.name?.trim().orEmpty()
            if (name.isEmpty()) return invalid("$path.name is required")

            val groupName = preset.group?.trim()?.uppercase().orEmpty()
            val group = PRESET_GROUP_BY_NAME[groupName]
                ?: return invalid(
                    "$path.group must be one of ${PRESET_GROUP_BY_NAME.keys.joinToString(", ")}, got '$groupName'"
                )

            val initial = preset.initial?.trim().orEmpty()
            if (!INITIAL_PATTERN.matches(initial)) {
                return invalid("$path.initial must be a single uppercase letter, got '$initial'")
            }

            val scriptPath = preset.script?.trim().orEmpty()
            if (!isSafeScriptPath(scriptPath, id)) {
                return invalid("$path.script must be a relative path inside presets/$id/, got '$scriptPath'")
            }

            val description = preset.description?.trim().orEmpty()
            if (description.isEmpty()) return invalid("$path.description is required")

            val scriptSha256 = preset.scriptSha256.trimmedOrNull()?.lowercase()
            if (scriptSha256 != null && !SHA256_PATTERN.matches(scriptSha256)) {
                return invalid("$path.scriptSha256 must be a 64-character hex digest")
            }
            // A downloaded script is the one case where the index and the file must be bound together:
            // without the digest, a tampered or half-updated source could hand the app any script.
            if (source == ImportPresetSourceKind.REMOTE && scriptSha256 == null) {
                return invalid("$path.scriptSha256 is required for downloaded presets")
            }

            val maintainer = preset.maintainer?.trim().orEmpty()
            if (maintainer.isEmpty()) return invalid("$path.maintainer is required")

            presets += ImportPreset(
                id = id,
                name = name,
                group = group,
                initial = initial,
                startUrl = preset.startUrl.trimmedOrNull(),
                scriptPath = scriptPath,
                scriptSha256 = scriptSha256,
                description = description,
                maintainer = maintainer,
                source = source
            )
        }

        return ImportPresetIndexParseResult.Parsed(
            ImportPresetIndex(
                schemaVersion = schemaVersion,
                indexVersion = indexVersion,
                presets = presets
            )
        )
    }

    private fun invalid(message: String): ImportPresetIndexParseResult.Invalid =
        ImportPresetIndexParseResult.Invalid(message)

    private fun String?.trimmedOrNull(): String? = this?.trim()?.takeUnless { it.isEmpty() }

    private companion object {
        /**
         * Index format revision this build understands. Raised only for breaking changes; new optional
         * fields do not need a bump because unknown keys are tolerated.
         */
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val MIN_INDEX_VERSION = 1

        val PRESET_ID_PATTERN = Regex("^[a-z0-9][a-z0-9-]{1,63}$")
        val INITIAL_PATTERN = Regex("^[A-Z]$")
        val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

        val PRESET_GROUP_BY_NAME: Map<String, ImportPresetGroup> =
            ImportPresetGroup.entries.associateBy { group -> group.name }

        val indexJson: Json = Json {
            ignoreUnknownKeys = true
        }
    }
}

/**
 * Whether [path] may be used as a script reference for the preset identified by [presetId].
 *
 * An index is data, and a remote one is data the app did not author, while the file it names is read
 * and executed inside the WebView. Requiring a relative path inside the preset's own folder is what
 * stops a tampered index from pointing the app at some other file in its private storage.
 */
internal fun isSafeScriptPath(path: String, presetId: String): Boolean =
    isSafeRelativePath(path) && path.startsWith("presets/$presetId/")

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

/**
 * Wire shape of the index root.
 */
@Serializable
internal data class ImportPresetIndexDto(
    val schemaVersion: Int? = null,
    val indexVersion: Int? = null,
    val presets: List<ImportPresetDto>? = null
)

/**
 * Wire shape of one preset.
 */
@Serializable
internal data class ImportPresetDto(
    val id: String? = null,
    val name: String? = null,
    val group: String? = null,
    val initial: String? = null,
    val startUrl: String? = null,
    val script: String? = null,
    val scriptSha256: String? = null,
    val description: String? = null,
    val maintainer: String? = null
)
