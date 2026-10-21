package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.model.importer.customSchoolIdForName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The pure part of persisting a user's own import presets: how the metadata list is encoded and
 * decoded, how a stored entry becomes a domain model, and how a new preset gets its id.
 *
 * Deliberately free of storage types so these rules can be tested on the JVM.
 * [CustomImportPresetStore] is the thin layer that moves the strings and files around.
 *
 * The script text itself is not part of this format: each preset's script lives in its own file
 * (see [scriptPathFor]), so a large script never has to be re-encoded every time the metadata for a
 * different preset changes.
 */

/**
 * Persisted metadata of one custom preset.
 *
 * Everything here is what the user typed, already validated by the use case that accepted it, so
 * reading it back is a matter of mapping rather than re-checking.
 */
@Serializable
internal data class CustomImportPresetDto(
    val id: String,
    val name: String,
    val group: String,
    val initial: String,
    val startUrl: String? = null,
    val description: String = "",
    val maintainer: String = "",
    val schoolId: String? = null,
    val schoolName: String? = null,
    val schoolCode: String? = null
)

/**
 * Root of the stored metadata document.
 *
 * The whole list is one value rather than one key per preset: the list is small, it is only read when
 * the preset list is built, and a single value makes an update atomic.
 */
@Serializable
internal data class CustomImportPresetListDto(
    val schemaVersion: Int = CustomImportPresetCodec.SUPPORTED_SCHEMA_VERSION,
    val presets: List<CustomImportPresetDto> = emptyList()
)

/**
 * Outcome of reading the stored metadata.
 */
internal sealed interface CustomImportPresetDecodeResult {

    data class Decoded(val presets: List<CustomImportPresetDto>) : CustomImportPresetDecodeResult

    /** Nothing stored yet, or nothing readable. Either way there is nothing to preserve. */
    data object Empty : CustomImportPresetDecodeResult

    /**
     * The stored value was written by a build that knows a newer format.
     *
     * Reads report no presets, and writes are refused: overwriting would silently destroy presets a
     * newer build created, and the user would only find out after reinstalling an older version.
     */
    data object UnsupportedVersion : CustomImportPresetDecodeResult
}

/**
 * Encodes and decodes the custom preset metadata.
 */
internal object CustomImportPresetCodec {

    /** Format revision this build writes. Raised only for breaking changes. */
    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(presets: List<CustomImportPresetDto>): String = json.encodeToString(
        CustomImportPresetListDto.serializer(),
        CustomImportPresetListDto(presets = presets)
    )

    fun decode(raw: String?): CustomImportPresetDecodeResult {
        if (raw.isNullOrBlank()) return CustomImportPresetDecodeResult.Empty

        val dto = try {
            json.decodeFromString(CustomImportPresetListDto.serializer(), raw)
        } catch (_: SerializationException) {
            // Corrupt storage is reported as empty so the next write can recover it. Losing the
            // metadata is bad; being permanently unable to save anything is worse.
            return CustomImportPresetDecodeResult.Empty
        }

        return if (dto.schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            CustomImportPresetDecodeResult.UnsupportedVersion
        } else {
            CustomImportPresetDecodeResult.Decoded(dto.presets)
        }
    }
}

/**
 * Turns a stored entry into a usable preset, or null when the entry cannot be used at all.
 *
 * A single damaged entry is skipped rather than failing the whole list: one bad record must not make
 * every other preset the user added disappear.
 */
internal fun CustomImportPresetDto.toImportPresetOrNull(): ImportPreset? {
    val resolvedId = id.trim()
    if (!isValidPresetId(resolvedId)) return null

    val resolvedName = name.trim()
    if (resolvedName.isEmpty()) return null

    val resolvedInitial = initial.trim().uppercase()
    if (resolvedInitial.length != 1 || resolvedInitial[0] !in 'A'..'Z') return null

    val resolvedGroup = ImportPresetGroup.entries
        .firstOrNull { candidate -> candidate.name == group.trim().uppercase() }
        ?: return null

    return ImportPreset(
        id = resolvedId,
        name = resolvedName,
        group = resolvedGroup,
        initial = resolvedInitial,
        startUrl = startUrl?.trim()?.takeUnless { it.isEmpty() },
        scriptPath = scriptPathFor(resolvedId),
        description = description.trim(),
        maintainer = maintainer.trim(),
        source = ImportPresetSourceKind.CUSTOM,
        // Older stored entries used the school name as the preset name. Treat that as the school when
        // reading them so users keep their saved scripts and they appear in the new hierarchy.
        schoolId = schoolId?.trim()?.takeIf { it.isNotEmpty() }
            ?: customSchoolIdForName(schoolName?.trim()?.takeUnless { it.isEmpty() } ?: resolvedName),
        schoolName = schoolName?.trim()?.takeUnless { it.isEmpty() } ?: resolvedName,
        schoolCode = schoolCode?.trim()?.takeUnless { it.isEmpty() }
    )
}

/**
 * Allocates an id for a new custom preset.
 *
 * @param taken ids already in use; an id is also the folder name the script is stored under, so it
 *   has to be unique and slug-safe.
 * @param nowMillis injectable so the collision behaviour can be tested without waiting for a clock.
 *
 * The id is opaque on purpose. The user's identity for a preset is its display name, which may be
 * Chinese and therefore cannot become a path segment without a transliteration step this app does
 * not have.
 */
internal fun generateCustomImportPresetId(
    taken: Set<String>,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val base = "custom-" + nowMillis.toString(36)
    if (base !in taken) return base

    var suffix = 2
    while ("$base-$suffix" in taken) suffix += 1
    return "$base-$suffix"
}
