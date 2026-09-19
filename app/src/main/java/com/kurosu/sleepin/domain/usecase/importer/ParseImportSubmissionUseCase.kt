package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.data.importer.ImportPayloadParser
import com.kurosu.sleepin.domain.model.MAX_SEMESTER_WEEK_COUNT
import com.kurosu.sleepin.domain.model.MAX_SUPPORTED_PERIOD_NUMBER
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Validates the raw JSON produced by an academic-portal import script.
 *
 * This is the single place where the payload contract is enforced, so adapters get consistent,
 * actionable errors and the rest of the app can rely on [ImportSubmission] holding only legal
 * values. The work is intentionally split from JSON decoding
 * ([com.kurosu.sleepin.data.importer.ImportPayloadParser]) because the two concerns fail for
 * different reasons and are easier to reason about separately.
 *
 * Deliberately *not* validated here: whether a referenced period exists in the target schedule, and
 * whether a week fits the semester length. Both depend on the target the user picks at commit time,
 * and neither is an error — they are mismatches the user is expected to resolve.
 */
class ParseImportSubmissionUseCase(
    private val parser: ImportPayloadParser
) {

    /**
     * Decodes and validates [rawJson].
     *
     * Field-level failures include the JSON path of the offending value so an adapter author can
     * locate the mistake without attaching a debugger.
     */
    operator fun invoke(rawJson: String): ImportParseResult {
        val draft = when (val decoded = parser.parse(rawJson)) {
            is ImportPayloadDecodeResult.Decoded -> decoded.draft
            is ImportPayloadDecodeResult.Malformed -> return reject(decoded.message)
        }

        val schemaVersion = draft.schemaVersion
            ?: return reject("导入数据缺少 schema 版本号，请更新该导入方案。")
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return reject(
                "不支持的导入数据版本 $schemaVersion，本应用支持的版本为 $SUPPORTED_SCHEMA_VERSION。"
            )
        }

        val timetable = when (val outcome = validateTimetable(draft.timetable)) {
            is TimetableValidation.Invalid -> return reject(outcome.message)
            is TimetableValidation.Valid -> outcome.value
        }

        val courseDrafts = draft.courses
            ?: return reject("导入数据缺少 courses 数组。")
        if (courseDrafts.isEmpty()) {
            return reject("导入数据里没有任何课程。")
        }
        if (courseDrafts.size > MAX_IMPORTED_COURSE_COUNT) {
            return reject(
                "导入数据包含 ${courseDrafts.size} 门课程，超过上限 $MAX_IMPORTED_COURSE_COUNT，请检查脚本是否多带了数据。"
            )
        }

        val courses = mutableListOf<ImportedCourse>()
        courseDrafts.forEachIndexed { courseIndex, courseDraft ->
            val coursePath = "courses[$courseIndex]"

            val name = courseDraft.name.trimmedOrNull()
                ?: return reject("$coursePath 缺少课程名称 name。")

            val sessionDrafts = courseDraft.sessions
                ?: return reject("$coursePath 缺少 sessions 数组。")
            if (sessionDrafts.isEmpty()) {
                return reject("$coursePath 的 sessions 为空，每门课程至少要有一个上课时段。")
            }

            val sessions = mutableListOf<ImportedSession>()
            sessionDrafts.forEachIndexed { sessionIndex, sessionDraft ->
                val sessionPath = "$coursePath.sessions[$sessionIndex]"

                val dayOfWeek = sessionDraft.dayOfWeek
                    ?: return reject("$sessionPath 缺少 day（星期几）。")
                if (dayOfWeek !in 1..7) {
                    return reject("$sessionPath 的 day 应在 1-7 之间（1 表示周一），当前为 $dayOfWeek。")
                }

                val startPeriod = sessionDraft.startPeriod
                    ?: return reject("$sessionPath 缺少 from（开始节次）。")
                val endPeriod = sessionDraft.endPeriod
                    ?: return reject("$sessionPath 缺少 to（结束节次）。")
                if (startPeriod < 1 || endPeriod < 1) {
                    return reject("$sessionPath 的节次必须大于 0，当前为 $startPeriod-$endPeriod。")
                }
                if (endPeriod < startPeriod) {
                    return reject("$sessionPath 的结束节次 to 不能小于开始节次 from，当前为 $startPeriod-$endPeriod。")
                }
                if (endPeriod > MAX_SUPPORTED_PERIOD_NUMBER) {
                    return reject(
                        "$sessionPath 的节次 $endPeriod 超过上限 $MAX_SUPPORTED_PERIOD_NUMBER，请检查脚本是否正确提取了节次。"
                    )
                }

                val weeks = sessionDraft.weeks
                    ?: return reject("$sessionPath 缺少 weeks（上课周次数组）。")
                if (weeks.isEmpty()) {
                    return reject("$sessionPath 的 weeks 为空，每节课至少要有一个上课周次。")
                }
                val invalidWeek = weeks.firstOrNull { it < 1 }
                if (invalidWeek != null) {
                    return reject("$sessionPath 的 weeks 含有非法周次 $invalidWeek，周次必须大于 0。")
                }

                sessions += ImportedSession(
                    dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    location = sessionDraft.location.trimmedOrNull(),
                    weeks = weeks.distinct().sorted()
                )
            }

            courses += ImportedCourse(
                name = name,
                teacher = courseDraft.teacher.trimmedOrNull(),
                note = courseDraft.note.trimmedOrNull(),
                color = courseDraft.color,
                sessions = sessions
            )
        }

        return ImportParseResult.Accepted(
            ImportSubmission(timetable = timetable, courses = courses)
        )
    }

    private fun validateTimetable(draft: ImportTimetableDraft?): TimetableValidation {
        if (draft == null) return TimetableValidation.Valid(null)

        val totalWeeks = draft.totalWeeks
        if (totalWeeks != null && totalWeeks !in 1..MAX_SEMESTER_WEEK_COUNT) {
            return TimetableValidation.Invalid(
                "timetable.totalWeeks 应在 1 到 $MAX_SEMESTER_WEEK_COUNT 之间，当前为 $totalWeeks。"
            )
        }

        val startDateText = draft.startDate.trimmedOrNull()
        val startDate = if (startDateText == null) {
            null
        } else {
            try {
                LocalDate.parse(startDateText)
            } catch (_: DateTimeParseException) {
                return TimetableValidation.Invalid(
                    "timetable.startDate 格式应为 yyyy-MM-dd，当前为 \"$startDateText\"。"
                )
            }
        }

        return TimetableValidation.Valid(
            ImportedTimetable(
                name = draft.name.trimmedOrNull(),
                totalWeeks = totalWeeks,
                startDate = startDate
            )
        )
    }

    private fun reject(message: String): ImportParseResult.Rejected = ImportParseResult.Rejected(message)

    /**
     * Internal outcome of the optional timetable block, kept private because callers only ever see
     * a validated [ImportedTimetable] or a rejection message.
     */
    private sealed interface TimetableValidation {
        data class Valid(val value: ImportedTimetable?) : TimetableValidation
        data class Invalid(val message: String) : TimetableValidation
    }

    private companion object {
        /**
         * Payload contract revision this build understands. Bumped only for breaking changes; new
         * optional fields do not require a bump because unknown values are tolerated by the parser.
         */
        const val SUPPORTED_SCHEMA_VERSION = 1

        /**
         * Upper bound on the number of courses accepted from one payload. A real semester has a few
         * dozen courses, so this only trips on a script that serialized far more than intended.
         */
        const val MAX_IMPORTED_COURSE_COUNT = 1000
    }
}

/**
 * Trims a nullable string and treats a blank result as absent.
 *
 * Import scripts frequently emit empty strings for optional fields such as a teacher or room; the
 * app models those as null everywhere else, so normalizing here keeps a single convention.
 */
private fun String?.trimmedOrNull(): String? = this?.trim()?.takeUnless { it.isEmpty() }
