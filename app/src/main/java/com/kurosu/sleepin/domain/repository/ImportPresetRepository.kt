package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.importer.ImportPreset

/**
 * Contract for reading the import presets available on this device.
 *
 * Presets come from several places (shipped in the APK, added by the user, downloaded), and callers
 * must not have to know which. Implementations merge the sources and return one list in display order.
 */
interface ImportPresetRepository {

    /**
     * All usable presets, ordered by group, then initial, then name.
     *
     * Ordering is part of the contract rather than left to each screen so the list cannot be presented
     * two different ways.
     */
    suspend fun getPresets(): List<ImportPreset>

    /**
     * Text of [preset]'s script, or null when it cannot be read.
     *
     * Returning null rather than throwing keeps "this preset is broken" — a missing file, a stale index
     * — distinguishable from a programming error at the call site.
     */
    suspend fun readScript(preset: ImportPreset): String?
}
