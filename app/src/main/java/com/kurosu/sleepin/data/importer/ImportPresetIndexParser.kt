package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetIndex
import com.kurosu.sleepin.domain.model.importer.ImportPresetIndexParseResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetSchool
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

        val schools = mutableMapOf<String, ImportPresetSchool>()
        dto.schools.orEmpty().forEachIndexed { index, school ->
            val path = "schools[$index]"
            val id = school.id?.trim().orEmpty()
            if (!isValidPresetId(id)) return invalid("$path.id must be a lowercase slug, got '$id'")
            if (id in schools) return invalid("$path.id '$id' is used by another school")
            val name = school.name?.trim().orEmpty()
            if (name.isEmpty()) return invalid("$path.name is required")
            schools[id] = ImportPresetSchool(
                id = id,
                name = name,
                code = school.code.trimmedOrNull(),
                aliases = school.aliases.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            )
        }

        val presets = mutableListOf<ImportPreset>()
        val seenIds = mutableSetOf<String>()
        dto.presets.orEmpty().forEachIndexed { index, preset ->
            val path = "presets[$index]"

            val id = preset.id?.trim().orEmpty()
            if (!isValidPresetId(id)) {
                return invalid("$path.id must be a lowercase slug, got '$id'")
            }
            if (!seenIds.add(id)) {
                return invalid("$path.id '$id' is used by another preset")
            }

            val name = preset.name?.trim().orEmpty()
            if (name.isEmpty()) return invalid("$path.name is required")

            val schoolId = preset.schoolId.trimmedOrNull()
            val school = schoolId?.let(schools::get)
            if (schoolId != null && school == null) {
                return invalid("$path.schoolId '$schoolId' does not name a school in the index")
            }

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
                source = source,
                schoolId = school?.id,
                schoolName = school?.name,
                schoolCode = school?.code,
                schoolAliases = school?.aliases.orEmpty()
            )
        }

        return ImportPresetIndexParseResult.Parsed(
            ImportPresetIndex(
                schemaVersion = schemaVersion,
                indexVersion = indexVersion,
                presets = presets,
                schools = schools.values.toList()
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
 * Wire shape of the index root.
 */
@Serializable
internal data class ImportPresetIndexDto(
    val schemaVersion: Int? = null,
    val indexVersion: Int? = null,
    val schools: List<ImportPresetSchoolDto>? = null,
    val presets: List<ImportPresetDto>? = null
)

/** Wire shape of one school directory entry. */
@Serializable
internal data class ImportPresetSchoolDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
    val aliases: List<String>? = null
)

/**
 * Wire shape of one preset.
 */
@Serializable
internal data class ImportPresetDto(
    val id: String? = null,
    val name: String? = null,
    val schoolId: String? = null,
    val group: String? = null,
    val initial: String? = null,
    val startUrl: String? = null,
    val script: String? = null,
    val scriptSha256: String? = null,
    val description: String? = null,
    val maintainer: String? = null
)
