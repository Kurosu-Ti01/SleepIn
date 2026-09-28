package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyRequest
import com.kurosu.sleepin.domain.model.importer.FamilyAssemblyResult
import com.kurosu.sleepin.domain.repository.LocalFamilyRepository

/**
 * Reusing a family this device already has, for a preset the user writes themselves.
 *
 * Both members belong together: the editor asks which families exist, then asks for one of them to be
 * assembled, and the two answers have to describe the same cache state.
 */
class ReuseLocalFamilyUseCase(
    private val repository: LocalFamilyRepository
) {

    /** Families that can be reused right now; empty means "import a source first". */
    suspend fun availableFamilyIds(): List<String> = repository.availableFamilyIds()

    /** Builds the final script for [request]. */
    suspend fun assemble(request: FamilyAssemblyRequest): FamilyAssemblyResult =
        repository.assembleScript(request)
}
