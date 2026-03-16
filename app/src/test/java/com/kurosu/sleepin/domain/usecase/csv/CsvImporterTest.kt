package com.kurosu.sleepin.domain.usecase.csv

import com.kurosu.sleepin.data.csv.CsvImporter
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
        // This fixture intentionally includes both RANGE and CUSTOM rows to validate week parsing.
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周数类型,起始周,结束周,自定义周
            高等数学,张三,A101,1,1,2,RANGE,1,16,
            线性代数,李四,B203,3,3,4,CUSTOM,,,1;3;5;7
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18, maxPeriod = 12)

        assertEquals(2, result.rows.size)
        assertTrue(result.errors.isEmpty())
        assertEquals(WeekType.RANGE, result.rows[0].weekType)
        assertEquals(WeekType.CUSTOM, result.rows[1].weekType)
        assertEquals(listOf(1, 3, 5, 7), result.rows[1].customWeeks)
    }

    @Test
    fun parse_invalidPeriodRange_collectsRowError() {
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周数类型,起始周,结束周,自定义周
            高等数学,张三,A101,1,15,20,RANGE,1,16,
        """.trimIndent()

        val result = importer.parse(rawCsv = csv, totalWeeks = 18, maxPeriod = 12)

        assertTrue(result.rows.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.first().message.contains("Period range"))
    }
}

