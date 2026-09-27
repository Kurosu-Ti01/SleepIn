package com.kurosu.sleepin.data.importer

/**
 * Preset sources that exist only in debug builds.
 *
 * Release builds register none: the simulated-import preset and the script it hands out live in the
 * debug source set, so nothing about them is packaged here. The debug variant of this file returns the
 * sample source.
 */
internal fun debugImportPresetSources(): List<ImportPresetSource> = emptyList()
