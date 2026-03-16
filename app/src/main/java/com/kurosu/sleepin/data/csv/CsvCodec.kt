package com.kurosu.sleepin.data.csv

/**
 * Lightweight CSV codec tailored for this app's import/export needs.
 *
 * Rules implemented:
 * - Comma separator.
 * - Double quote escaping with RFC4180 style doubled quotes.
 * - Newline splitting is handled outside this helper.
 */
object CsvCodec {

    /**
     * Splits one CSV line into fields while respecting quoted segments.
     */
    fun parseLine(line: String): List<String> {
        if (line.isEmpty()) return emptyList()

        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    // Escaped quote inside a quoted field: "" -> ".
                    current.append('"')
                    index += 1
                }

                char == '"' -> {
                    inQuotes = !inQuotes
                }

                char == ',' && !inQuotes -> {
                    values.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
            index += 1
        }

        values.add(current.toString())
        return values
    }

    /**
     * Escapes one plain text value so it can be safely written as a CSV field.
     */
    fun encodeField(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

