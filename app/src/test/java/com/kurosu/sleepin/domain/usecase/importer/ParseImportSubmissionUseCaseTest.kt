package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.data.importer.ImportPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Unit tests for the import payload contract: which payloads are accepted, and how rejections point
 * an adapter author at the offending field.
 */
class ParseImportSubmissionUseCaseTest {

    private val useCase = ParseImportSubmissionUseCase(ImportPayloadParser())

    @Test
    fun invoke_acceptsOptionalPeriods_andSortsThemByPeriodNumber() {
        val result = useCase(
            """
            {
              "schema": 1,
              "periods": [
                { "period": 2, "start": "08:55", "end": "09:40" },
                { "period": 1, "start": "8:00", "end": "08:45" }
              ],
              "courses": [
                { "name": "高等数学", "sessions": [ { "day": 1, "from": 1, "to": 2, "weeks": [1] } ] }
              ]
            }
            """.trimIndent()
        )

        val periods = accepted(result).periods
        assertEquals(listOf(1, 2), periods.map { it.periodNumber })
        // "8:00" is accepted as well as "08:00": a missing leading zero is a habit, not an ambiguity.
        assertEquals(LocalTime.of(8, 0), periods.first().startTime)
        assertEquals(LocalTime.of(9, 40), periods.last().endTime)
    }

    @Test
    fun invoke_acceptsPayload_withoutPeriodsBlock() {
        val result = useCase(
            """
            {
              "schema": 1,
              "courses": [
                { "name": "线性代数", "sessions": [ { "day": 2, "from": 3, "to": 4, "weeks": [1] } ] }
              ]
            }
            """.trimIndent()
        )

        // An empty list rather than null: "this portal reports no class times" is a normal state.
        assertTrue(accepted(result).periods.isEmpty())
    }

    @Test
    fun invoke_rejectsPeriods_whenAnEntryIsIncompleteOrImpossible() {
        assertTrue(rejectionMessage(useCase(coursePayloadWithPeriods("{ \"period\": 1, \"end\": \"08:45\" }")))
            .contains("periods[0] 缺少 start"))
        assertTrue(rejectionMessage(useCase(coursePayloadWithPeriods("{ \"period\": 1, \"start\": \"早上八点\", \"end\": \"08:45\" }")))
            .contains("start 格式应为 HH:mm"))
        assertTrue(rejectionMessage(useCase(coursePayloadWithPeriods("{ \"period\": 1, \"start\": \"09:00\", \"end\": \"08:45\" }")))
            .contains("必须晚于开始时间"))
        assertTrue(rejectionMessage(useCase(coursePayloadWithPeriods("{ \"period\": 0, \"start\": \"08:00\", \"end\": \"08:45\" }")))
            .contains("period 应在 1 到"))
    }

    @Test
    fun invoke_rejectsPeriods_whenOnePeriodNumberIsDefinedTwice() {
        val message = rejectionMessage(
            useCase(
                coursePayloadWithPeriods(
                    "{ \"period\": 1, \"start\": \"08:00\", \"end\": \"08:45\" }",
                    "{ \"period\": 1, \"start\": \"09:00\", \"end\": \"09:45\" }"
                )
            )
        )

        // Last-one-wins would be a guess about which time is real, so the payload is refused.
        assertTrue(message, message.contains("periods[1] 的 period 1 与前面的节次重复"))
    }

    /**
     * Builds a payload that is valid apart from its `periods` entries.
     */
    private fun coursePayloadWithPeriods(vararg periods: String): String = """
        {
          "schema": 1,
          "periods": [ ${periods.joinToString(", ")} ],
          "courses": [
            { "name": "高等数学", "sessions": [ { "day": 1, "from": 1, "to": 2, "weeks": [1] } ] }
          ]
        }
    """.trimIndent()

    @Test
    fun invoke_acceptsPayload_andNormalizesOptionalFieldsAndWeeks() {
        val result = useCase(
            """
            {
              "schema": 1,
              "timetable": { "name": " 2025 秋 ", "totalWeeks": 18, "startDate": "2025-09-01" },
              "courses": [
                {
                  "name": " 高等数学 ",
                  "teacher": "   ",
                  "note": "",
                  "sessions": [
                    { "day": 1, "from": 1, "to": 2, "location": "  ", "weeks": [3, 1, 2, 2] }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val submission = accepted(result)
        assertEquals("2025 秋", submission.timetable?.name)
        assertEquals(18, submission.timetable?.totalWeeks)
        assertEquals(LocalDate.of(2025, 9, 1), submission.timetable?.startDate)

        val course = submission.courses.single()
        assertEquals("高等数学", course.name)
        assertNull(course.teacher)
        assertNull(course.note)

        val session = course.sessions.single()
        assertNull(session.location)
        assertEquals(listOf(1, 2, 3), session.weeks)
    }

    @Test
    fun invoke_acceptsPayload_withoutTimetableBlock() {
        val result = useCase(
            """
            {
              "schema": 1,
              "courses": [
                { "name": "线性代数", "sessions": [ { "day": 2, "from": 3, "to": 4, "weeks": [1] } ] }
              ]
            }
            """.trimIndent()
        )

        val submission = accepted(result)
        assertNull(submission.timetable)
        assertEquals(1, submission.courses.size)
    }

    @Test
    fun invoke_keepsWeeksOutsideSemester_soMismatchStaysVisible() {
        val result = useCase(
            """
            {
              "schema": 1,
              "timetable": { "totalWeeks": 16 },
              "courses": [
                { "name": "体育", "sessions": [ { "day": 3, "from": 5, "to": 6, "weeks": [20] } ] }
              ]
            }
            """.trimIndent()
        )

        val session = accepted(result).courses.single().sessions.single()
        assertEquals(listOf(20), session.weeks)
    }

    @Test
    fun invoke_rejectsPayload_whenSchemaIsMissing() {
        val message = rejectionMessage(useCase("""{ "courses": [] }"""))

        assertTrue(message.contains("schema"))
    }

    @Test
    fun invoke_rejectsPayload_whenSchemaVersionIsUnsupported() {
        val message = rejectionMessage(
            useCase("""{ "schema": 99, "courses": [ { "name": "a", "sessions": [] } ] }""")
        )

        assertTrue(message.contains("99"))
    }

    @Test
    fun invoke_rejectsPayload_whenJsonIsMalformed() {
        val message = rejectionMessage(useCase("{ not json"))

        assertTrue(message.contains("JSON"))
    }

    @Test
    fun invoke_rejectsPayload_whenNoCoursesArePresent() {
        val message = rejectionMessage(useCase("""{ "schema": 1, "courses": [] }"""))

        assertTrue(message.contains("没有任何课程"))
    }

    @Test
    fun invoke_rejectsPayload_whenCourseNameIsBlank() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "courses": [
                    { "name": "  ", "sessions": [ { "day": 1, "from": 1, "to": 1, "weeks": [1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("courses[0]"))
        assertTrue(message.contains("name"))
    }

    @Test
    fun invoke_rejectsPayload_whenSessionDayIsOutOfRange() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 9, "from": 1, "to": 1, "weeks": [1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("courses[0].sessions[0]"))
        assertTrue(message.contains("day"))
    }

    @Test
    fun invoke_rejectsPayload_whenPeriodRangeIsInverted() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 1, "from": 4, "to": 2, "weeks": [1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("from"))
    }

    @Test
    fun invoke_rejectsPayload_whenPeriodExceedsSupportedMaximum() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 1, "from": 1, "to": 9999, "weeks": [1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("9999"))
        assertTrue(message.contains("上限"))
    }

    @Test
    fun invoke_rejectsPayload_whenWeeksAreMissing() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 1, "from": 1, "to": 1 } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("weeks"))
    }

    @Test
    fun invoke_rejectsPayload_whenWeekNumberIsNotPositive() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 1, "from": 1, "to": 1, "weeks": [0, 1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("weeks"))
    }

    @Test
    fun invoke_rejectsPayload_whenSessionListIsEmpty() {
        val message = rejectionMessage(
            useCase("""{ "schema": 1, "courses": [ { "name": "英语", "sessions": [] } ] }""")
        )

        assertTrue(message.contains("sessions"))
    }

    @Test
    fun invoke_rejectsPayload_whenTimetableTotalWeeksIsOutOfRange() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "timetable": { "totalWeeks": 40 },
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 1, "from": 1, "to": 1, "weeks": [1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("totalWeeks"))
    }

    @Test
    fun invoke_rejectsPayload_whenStartDateIsNotIsoFormatted() {
        val message = rejectionMessage(
            useCase(
                """
                {
                  "schema": 1,
                  "timetable": { "startDate": "2025/09/01" },
                  "courses": [
                    { "name": "英语", "sessions": [ { "day": 1, "from": 1, "to": 1, "weeks": [1] } ] }
                  ]
                }
                """.trimIndent()
            )
        )

        assertTrue(message.contains("startDate"))
    }

    private fun accepted(result: ImportParseResult): ImportSubmission {
        assertTrue("expected an accepted payload but got $result", result is ImportParseResult.Accepted)
        return (result as ImportParseResult.Accepted).submission
    }

    private fun rejectionMessage(result: ImportParseResult): String {
        assertTrue("expected a rejected payload but got $result", result is ImportParseResult.Rejected)
        return (result as ImportParseResult.Rejected).message
    }
}
