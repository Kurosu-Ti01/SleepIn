package com.kurosu.sleepin.data.csv

import java.nio.charset.Charset
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CSV import text decoding with Chinese-encoding fallback.
 */
class CsvImportTextDecoderTest {

    @Test
    fun decodeTimetableCsv_utf8_keepsChineseText() {
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            高等数学,张三,A101,1,1,2,1-16
        """.trimIndent()

        val decoded = CsvImportTextDecoder.decodeTimetableCsv(csv.toByteArray(Charsets.UTF_8))

        assertTrue(decoded.contains("课程名称"))
        assertTrue(decoded.contains("高等数学"))
    }

    @Test
    fun decodeTimetableCsv_gbk_restoresChineseText() {
        val csv = """
            课程名称,教师,地点,星期,开始节次,结束节次,周次
            线性代数,李四,B203,3,3,4,1-16
        """.trimIndent()
        val gbk = Charset.forName("GBK")

        val decoded = CsvImportTextDecoder.decodeTimetableCsv(csv.toByteArray(gbk))

        assertTrue(decoded.contains("课程名称"))
        assertTrue(decoded.contains("线性代数"))
    }

    @Test
    fun decodeScheduleCsv_utf16leWithBom_restoresChineseText() {
        val csv = """
            作息表名称,节次,开始时间,结束时间
            春季作息,1,08:00,08:45
        """.trimIndent()
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + csv.toByteArray(Charsets.UTF_16LE)

        val decoded = CsvImportTextDecoder.decodeScheduleCsv(bytes)

        assertTrue(decoded.contains("作息表名称"))
        assertTrue(decoded.contains("春季作息"))
    }
}

