package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.domain.model.MAX_SUPPORTED_PERIOD_NUMBER
import com.kurosu.sleepin.domain.model.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests covering CSV parser validation and row mapping behavior.
 */
class CsvImporterTest {

    private val importer = CsvImporter()

    @Test
    fun parse_validCsv_returnsRowsWithoutErrors() {
        // This fixture intentionally covers range, odd/even range, and mixed week specifications.
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,1,2,1-16
            线性代数,李四,B203,3,3,4,1-16(odd)
            大学英语,王老师,C305,5,7,8,1-4;7-9;13;14-18(odd)
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18)

        assertEquals(3, result.rows.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(WeekType.RANGE, result.rows[0].weekType)
        assertEquals(WeekType.CUSTOM, result.rows[1].weekType)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), result.rows[1].customWeeks)
        assertEquals(listOf(1, 2, 3, 4, 7, 8, 9, 13, 15, 17), result.rows[2].customWeeks)
    }

    @Test
    fun parse_largeButStructurallyValidPeriods_areKeptAsRows() {
        // The parser cannot know which schedule the row will be imported into, so it must not reject a
        // structurally legal period just because it is larger than a typical schedule.
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,15,20,1-16
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18)

        assertEquals(1, result.rows.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(15, result.rows.first().startPeriod)
        assertEquals(20, result.rows.first().endPeriod)
    }

    @Test
    fun parse_nonPositivePeriod_collectsRowError() {
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,0,2,1-16
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18)

        assertTrue(result.rows.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().message.contains("greater than 0"))
    }

    @Test
    fun parse_periodBeyondSupportedMaximum_collectsRowError() {
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,1,9999,1-16
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18)

        assertTrue(result.rows.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(
            result.errors.first().message.contains("$MAX_SUPPORTED_PERIOD_NUMBER")
        )
    }

    @Test
    fun parse_startPeriodAfterEndPeriod_collectsRowError() {
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,5,3,1-16
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18)

        assertTrue(result.rows.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().message.contains("Start period"))
    }

}

