package com.kurosu.sleepin.domain.usecase.importer

import com.kurosu.sleepin.domain.model.importer.RegisteredImportSource
import com.kurosu.sleepin.domain.repository.ImportSourceRepository

/**
 * Lists the sources this device has imported, for the settings screen to show and re-point.
 *
 * No filtering or sorting here on purpose: the screen knows which one the app manages (its address is in
 * settings) and can label it, while reordering in this layer would only hide the order they were added in.
 */
class GetRegisteredImportSourcesUseCase(
    private val importSourceRepository: ImportSourceRepository
) {
    suspend operator fun invoke(): List<RegisteredImportSource> =
        importSourceRepository.registeredSources()
}
