package com.kurosu.sleepin.data.importer

import com.kurosu.sleepin.domain.usecase.importer.ImportCourseDraft
import com.kurosu.sleepin.domain.usecase.importer.ImportPayloadDecodeResult
import com.kurosu.sleepin.domain.usecase.importer.ImportPayloadDraft
import com.kurosu.sleepin.domain.usecase.importer.ImportPeriodDraft
import com.kurosu.sleepin.domain.usecase.importer.ImportSessionDraft
import com.kurosu.sleepin.domain.usecase.importer.ImportTimetableDraft
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Decodes the JSON payload produced by an academic-portal import script.
 *
 * This class is a codec, not a validator: it answers "is this well-formed JSON of the expected
 * shape" and maps it onto the nullable draft models. Business rules (which fields are required,
 * which ranges are legal, how weeks are normalized) belong to
 * `com.kurosu.sleepin.domain.usecase.importer.ParseImportSubmissionUseCase`, so they live in one
 * readable place and stay unit-testable without touching JSON.
 *
 * Unknown keys are accepted on purpose. That keeps a payload written for a newer app version
 * usable on an older one, and a misspelled key still surfaces as a precise "missing field" message
 * from the validator rather than as a silent default.
 */
class ImportPayloadParser {

    /**
     * Turns [rawJson] into an [ImportPayloadDraft], or reports why the text is unusable.
     *
     * The payload originates from JavaScript running inside a WebView page, so decoding is
     * defensive: oversized input is refused before parsing, and any decoder failure is converted
     * into a user-facing message instead of propagating a library exception.
     */
    fun parse(rawJson: String): ImportPayloadDecodeResult {
        if (rawJson.isBlank()) {
            return ImportPayloadDecodeResult.Malformed("导入脚本没有返回任何数据。")
        }
        if (rawJson.length > MAX_PAYLOAD_CHARS) {
            return ImportPayloadDecodeResult.Malformed(
                "导入数据过大（超过 $MAX_PAYLOAD_CHARS 个字符），请检查脚本是否返回了多余内容。"
            )
        }

        val dto = try {
            payloadJson.decodeFromString(ImportPayloadDto.serializer(), rawJson)
        } catch (error: SerializationException) {
            return ImportPayloadDecodeResult.Malformed(
                "导入数据不是合法的 JSON 结构：${error.message ?: "解析失败"}"
            )
        }

        return ImportPayloadDecodeResult.Decoded(
            ImportPayloadDraft(
                schemaVersion = dto.schema,
                timetable = dto.timetable?.toDraft(),
                courses = dto.courses?.map { it.toDraft() },
                periods = dto.periods?.map { it.toDraft() }
            )
        )
    }

    private fun ImportPeriodDto.toDraft(): ImportPeriodDraft = ImportPeriodDraft(
        periodNumber = period,
        startTime = start,
        endTime = end
    )

    private fun ImportTimetableDto.toDraft(): ImportTimetableDraft = ImportTimetableDraft(
        name = name,
        totalWeeks = totalWeeks,
        startDate = startDate
    )

    private fun ImportCourseDto.toDraft(): ImportCourseDraft = ImportCourseDraft(
        name = name,
        teacher = teacher,
        note = note,
        color = color,
        sessions = sessions?.map { it.toDraft() }
    )

    private fun ImportSessionDto.toDraft(): ImportSessionDraft = ImportSessionDraft(
        dayOfWeek = day,
        startPeriod = from,
        endPeriod = to,
        location = location,
        weeks = weeks
    )

    private companion object {
        /**
         * Upper bound on accepted payload size, in characters.
         *
         * A semester timetable is a few tens of kilobytes at most, so this leaves generous headroom
         * while refusing a script that accidentally serializes an entire page or an infinite loop.
         */
        const val MAX_PAYLOAD_CHARS = 2_000_000

        val payloadJson: Json = Json {
            ignoreUnknownKeys = true
        }
    }
}

/**
 * Wire shape of the payload root.
 */
@Serializable
internal data class ImportPayloadDto(
    val schema: Int? = null,
    val timetable: ImportTimetableDto? = null,
    val courses: List<ImportCourseDto>? = null,
    val periods: List<ImportPeriodDto>? = null
)

/**
 * Wire shape of the optional timetable metadata block.
 */
@Serializable
internal data class ImportTimetableDto(
    val name: String? = null,
    val totalWeeks: Int? = null,
    val startDate: String? = null
)

/**
 * Wire shape of one course. `day`/`from`/`to` are kept short in the contract because adapters are
 * hand-written per school; the draft models rename them to the app's own period vocabulary.
 */
@Serializable
internal data class ImportCourseDto(
    val name: String? = null,
    val teacher: String? = null,
    val note: String? = null,
    val color: Int? = null,
    val sessions: List<ImportSessionDto>? = null
)

/**
 * Wire shape of one weekly slot of a course.
 */
@Serializable
internal data class ImportSessionDto(
    val day: Int? = null,
    val from: Int? = null,
    val to: Int? = null,
    val location: String? = null,
    val weeks: List<Int>? = null
)

/**
 * Wire shape of one class-time entry reported by the script.
 *
 * Times are wall-clock text without a date (`"08:00"`), because an import describes a weekly
 * template rather than one particular day. The block is optional: a portal that only reports period
 * numbers cannot supply it, and the user then picks an existing schedule instead.
 */
@Serializable
internal data class ImportPeriodDto(
    val period: Int? = null,
    val start: String? = null,
    val end: String? = null
)
