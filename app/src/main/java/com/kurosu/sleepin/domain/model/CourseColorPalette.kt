package com.kurosu.sleepin.domain.model

/**
 * Default palette used whenever a course needs a display color and the caller does not supply one.
 *
 * Values are ARGB integers rather than a Compose color, because they are persisted verbatim in
 * `courses.color` and therefore belong to the storage contract, not to the presentation layer.
 * Keeping the list here means the color picker, CSV import and academic-portal import all draw
 * from one source of truth, so a course imported through any entry point gets a stable,
 * reproducible color.
 */
val CourseColorPalette: List<Int> = listOf(
    0xFF4A90D9.toInt(),
    0xFF67C23A.toInt(),
    0xFFE6A23C.toInt(),
    0xFFF56C6C.toInt(),
    0xFF909399.toInt(),
    0xFF9B59B6.toInt(),
    0xFF1ABC9C.toInt(),
    0xFFE91E63.toInt(),
    0xFF3F51B5.toInt(),
    0xFF00BCD4.toInt(),
    0xFF8BC34A.toInt(),
    0xFFFF9800.toInt()
)
