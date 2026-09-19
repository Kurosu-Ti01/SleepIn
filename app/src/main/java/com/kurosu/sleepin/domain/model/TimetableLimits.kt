package com.kurosu.sleepin.domain.model

/**
 * Upper bound on the number of weeks a semester timetable may span.
 *
 * Mirrors the range the timetable editor already enforces, restated here so import paths can check
 * a resolved value without reaching into UI defaults. A semester longer than this is always bad
 * input rather than a long term. The lower bound needs no constant: week numbering is 1-based
 * everywhere in the app, so the first legal value is always 1.
 */
const val MAX_SEMESTER_WEEK_COUNT: Int = 30

/**
 * Highest period (节次) number any data source is allowed to reference.
 *
 * A course session stores only a period number; the wall-clock time comes from the timetable's
 * schedule template. That means a session may legitimately point at a period the current schedule
 * does not define yet — the user is expected to extend the schedule afterwards — so import paths
 * must not reject a value merely because it exceeds the schedule's own period list.
 *
 * This constant is the separate, structural guard that keeps such values sane: the week grid renders
 * one row per period number, so an unbounded value (a typo like `9999`, or a buggy import script)
 * would allocate an enormous grid and freeze the UI. Values above this bound are treated as
 * malformed input rather than as a schedule mismatch.
 */
const val MAX_SUPPORTED_PERIOD_NUMBER: Int = 60
