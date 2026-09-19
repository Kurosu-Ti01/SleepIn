package com.kurosu.sleepin.domain.usecase.importer

import java.time.LocalDate

/**
 * Payload shape produced by an academic-portal import script, as decoded from JSON.
 *
 * Every field is nullable on purpose. JSON decoding and domain validation are deliberately split:
 * this model only reflects "what the script sent", while [ParseImportSubmissionUseCase] decides
 * whether the content is acceptable. Keeping nullability here means a missing or mistyped field is
 * reported as a precise validation message with a JSON path instead of a decoder stack trace.
 */
data class ImportPayloadDraft(
    val schemaVersion: Int?,
    val timetable: ImportTimetableDraft?,
    val courses: List<ImportCourseDraft>?
)

/**
 * Optional timetable metadata inside an import payload.
 *
 * All three fields are optional: a script may only care about courses, leaving the user to fill in
 * the semester name, week count and start date in the import confirmation step.
 */
data class ImportTimetableDraft(
    val name: String?,
    val totalWeeks: Int?,
    val startDate: String?
)

/**
 * One course as sent by the script, together with every weekly slot it occupies.
 *
 * Sessions are nested under the course (rather than being flat rows) because that mirrors the app's
 * own course/session ownership: it removes the "group flat rows back by course name" step and makes
 * it impossible to silently merge two different courses that happen to share a name.
 */
data class ImportCourseDraft(
    val name: String?,
    val teacher: String?,
    val note: String?,
    val color: Int?,
    val sessions: List<ImportSessionDraft>?
)

/**
 * One recurring slot of an imported course, as decoded from JSON.
 */
data class ImportSessionDraft(
    val dayOfWeek: Int?,
    val startPeriod: Int?,
    val endPeriod: Int?,
    val location: String?,
    val weeks: List<Int>?
)

/**
 * Outcome of turning a raw JSON string into an [ImportPayloadDraft].
 *
 * Declared in the domain layer so the data-layer decoder can report failures without the use case
 * having to inspect decoder exception types.
 */
sealed interface ImportPayloadDecodeResult {
    /** The text was syntactically valid JSON matching the payload shape. */
    data class Decoded(val draft: ImportPayloadDraft) : ImportPayloadDecodeResult

    /** The text could not be decoded at all. [message] is user-facing and already localized. */
    data class Malformed(val message: String) : ImportPayloadDecodeResult
}

/**
 * A validated import payload ready to be turned into persistence models.
 *
 * Reaching this type means every structural rule held: courses exist, names are non-blank, days are
 * within 1..7, period ranges are ordered, and week lists are non-empty and normalized. It does
 * *not* mean the referenced periods exist in the target schedule — an import may legitimately
 * reference periods the user has not configured yet.
 */
data class ImportSubmission(
    val timetable: ImportedTimetable?,
    val courses: List<ImportedCourse>
)

/**
 * Validated timetable metadata. Fields stay nullable because supplying them is optional.
 */
data class ImportedTimetable(
    val name: String?,
    val totalWeeks: Int?,
    val startDate: LocalDate?
)

/**
 * Validated course together with its weekly slots.
 */
data class ImportedCourse(
    val name: String,
    val teacher: String?,
    val note: String?,
    val color: Int?,
    val sessions: List<ImportedSession>
)

/**
 * Validated weekly slot of an imported course.
 *
 * [weeks] is sorted and duplicate-free but is *not* clipped to the semester length: keeping the
 * original week numbers preserves what the portal reported, which is what lets the app tell the
 * user that the payload and the timetable disagree instead of quietly dropping weeks.
 */
data class ImportedSession(
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val location: String?,
    val weeks: List<Int>
)

/**
 * Outcome of decoding *and* validating an import payload.
 */
sealed interface ImportParseResult {
    /** The payload is usable; [submission] contains only validated values. */
    data class Accepted(val submission: ImportSubmission) : ImportParseResult

    /**
     * The payload cannot be used. [message] is user-facing Chinese text, and for field-level
     * problems it includes the offending JSON path (for example `courses[2].sessions[0].day`) so
     * import-script authors can locate the mistake without a debugger.
     */
    data class Rejected(val message: String) : ImportParseResult
}
