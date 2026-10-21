package com.kurosu.sleepin.domain.model.importer

/**
 * The preset source the app keeps up to date on its own.
 *
 * This points at the `source-index.json` of the community preset repository. It must be the **index
 * file**, not the directory containing it: the reader downloads exactly this address once and resolves
 * every other file in the index relative to it, which is also what keeps the whole import on one host.
 *
 * `raw.githubusercontent.com` is used rather than the repository page because the page address
 * redirects, and a redirect would move the base that every relative path is resolved against.
 */
const val BUILT_IN_IMPORT_SOURCE_URL: String =
    "https://raw.githubusercontent.com/Kurosu-Ti01/SleepIn-ImportPresets/main/source-index.json"

/** Refresh cadence used until the user picks another one. */
const val DEFAULT_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS: Int = 7

/**
 * Shortest cadence the app accepts.
 *
 * A day is the point below which the source cannot realistically have changed, and every refresh
 * re-downloads the shared runner and every family the index declares.
 */
const val MIN_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS: Int = 1

/** Longest cadence the app accepts. */
const val MAX_IMPORT_SOURCE_REFRESH_INTERVAL_DAYS: Int = 30
