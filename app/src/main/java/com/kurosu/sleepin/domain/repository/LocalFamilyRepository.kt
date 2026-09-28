package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyRequest
import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyResult

/**
 * Families this device cached from an imported source, and the ability to build a preset from one.
 *
 * A family only arrives here by importing a source, so an empty list means "nothing to reuse yet"
 * rather than a failure. Reusing a family is what makes the user's own preset as small as the values
 * that actually differ from it.
 */
interface LocalFamilyRepository {

    /**
     * Families that can be reused right now, sorted by id.
     *
     * A family counts as reusable only when its files **and** the shared runner are cached: assembling
     * without the runner cannot produce a script, so offering such a family would only fail later, after
     * the user had already committed to it.
     */
    suspend fun availableFamilyIds(): List<String>

    /** Builds a preset script from the cached family named in [request]. */
    suspend fun assembleScript(request: FamilyAssemblyRequest): FamilyAssemblyResult
}
