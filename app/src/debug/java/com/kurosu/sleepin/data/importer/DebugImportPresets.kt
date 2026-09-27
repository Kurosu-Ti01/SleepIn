package com.kurosu.sleepin.data.importer

/**
 * Preset sources that exist only in debug builds.
 *
 * Declared per build type so the sample and its script are compiled into debug builds and left out of
 * release builds entirely, rather than being unreachable code that ships anyway. The release variant of
 * this file returns an empty list.
 */
internal fun debugImportPresetSources(): List<ImportPresetSource> = listOf(ImportSamplePresetSource())
