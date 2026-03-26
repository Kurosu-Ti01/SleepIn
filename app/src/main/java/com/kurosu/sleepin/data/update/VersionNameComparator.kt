package com.kurosu.sleepin.data.update

/**
 * Compares semantic-like version names such as `v1.2.3` and `1.2.3-beta`.
 */
object VersionNameComparator {

    /**
     * Returns positive when [left] is newer, negative when [right] is newer, zero when equal.
     */
    fun compare(left: String, right: String): Int {
        val leftNormalized = normalize(left)
        val rightNormalized = normalize(right)
        val leftParts = leftNormalized.split('.', '-', '_')
        val rightParts = rightNormalized.split('.', '-', '_')
        val max = maxOf(leftParts.size, rightParts.size)

        for (index in 0 until max) {
            val leftValue = leftParts.getOrNull(index).toNumberPart()
            val rightValue = rightParts.getOrNull(index).toNumberPart()
            if (leftValue != rightValue) {
                return leftValue.compareTo(rightValue)
            }
        }

        val leftHasPreRelease = leftNormalized.contains('-')
        val rightHasPreRelease = rightNormalized.contains('-')
        return when {
            leftHasPreRelease && !rightHasPreRelease -> -1
            !leftHasPreRelease && rightHasPreRelease -> 1
            else -> 0
        }
    }

    private fun normalize(value: String): String =
        value.trim().removePrefix("v").removePrefix("V")

    private fun String?.toNumberPart(): Int {
        if (this.isNullOrBlank()) return 0
        val digits = takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}


