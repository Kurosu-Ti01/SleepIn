package com.kurosu.sleepin.data.csv

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Decodes imported CSV byte streams with UTF-8 first and Chinese-encoding fallback.
 *
 * Charset candidates are scored by header matching so UTF-8 remains default while
 * files saved by common Chinese spreadsheet encodings can still be imported.
 */
object CsvImportTextDecoder {

    private val timetableHeaders = setOf(
        "课程名称", "教师", "地点", "星期", "开始节次", "结束节次", "周次",
        "CourseName", "Teacher", "Location", "DayOfWeek", "StartPeriod", "EndPeriod", "Weeks"
    )

    private val scheduleHeaders = setOf(
        "作息表名称", "节次", "开始时间", "结束时间",
        "ScheduleName", "PeriodNumber", "StartTime", "EndTime"
    )

    private val charsetCandidates: List<Charset> = buildList {
        add(Charsets.UTF_8)
        addIfAvailable("GB18030")
        addIfAvailable("GBK")
        addIfAvailable("GB2312")
        addIfAvailable("Big5")
        add(Charsets.UTF_16LE)
        add(Charsets.UTF_16BE)
    }.distinctBy { it.name().uppercase(Locale.ROOT) }

    fun decodeTimetableCsv(bytes: ByteArray): String =
        decodeWithHeaderHints(bytes = bytes, expectedHeaders = timetableHeaders)

    fun decodeScheduleCsv(bytes: ByteArray): String =
        decodeWithHeaderHints(bytes = bytes, expectedHeaders = scheduleHeaders)

    private fun decodeWithHeaderHints(bytes: ByteArray, expectedHeaders: Set<String>): String {
        if (bytes.isEmpty()) return ""

        val candidates = reorderByBom(bytes)
        var bestText: String? = null
        var bestScore = Int.MIN_VALUE

        candidates.forEach { charset ->
            val decoded = decodeStrict(bytes, charset) ?: return@forEach
            val score = scoreHeader(decoded, expectedHeaders)
            if (score > bestScore) {
                bestScore = score
                bestText = decoded
            }
        }

        return bestText ?: String(bytes, Charsets.UTF_8)
    }

    private fun reorderByBom(bytes: ByteArray): List<Charset> {
        val bomCharset = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> Charsets.UTF_8

            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE

            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE

            else -> null
        } ?: return charsetCandidates

        return listOf(bomCharset) + charsetCandidates.filterNot {
            it.name().equals(bomCharset.name(), ignoreCase = true)
        }
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return runCatching {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
    }

    private fun scoreHeader(decoded: String, expectedHeaders: Set<String>): Int {
        val headerLine = decoded
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.removePrefix("\uFEFF")
            ?.trim()
            ?: return 0

        val columns = CsvCodec.parseLine(headerLine)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (columns.isEmpty()) return 0

        val matched = columns.count { expectedHeaders.contains(it) }
        val replacementChars = columns.count { it.contains('\uFFFD') }
        return matched * 10 - replacementChars * 5
    }

    private fun MutableList<Charset>.addIfAvailable(name: String) {
        runCatching { Charset.forName(name) }
            .getOrNull()
            ?.let { add(it) }
    }
}

