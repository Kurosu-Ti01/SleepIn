package com.kurosu.sleepin.ui.screen.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.usecase.importer.GetImportPresetsUseCase
import com.kurosu.sleepin.domain.usecase.importer.RemoveCustomImportPresetUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the preset picker.
 *
 * The domain model is not used directly because the row needs nothing else from it: a script path and
 * a digest are implementation details of how a preset is stored.
 */
data class ImportPresetItemUi(
    val id: String,
    val name: String,
    val description: String,
    val maintainer: String,
    /** Whether this is the user's own preset, which is the only kind they can change. */
    val isCustom: Boolean
)

/** List item for a school directory row. */
data class ImportPresetSchoolUi(
    val id: String,
    val name: String,
    val code: String?,
    val scriptCount: Int
)

/**
 * State of the preset picker.
 */
data class ImportPresetListUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val schools: List<ImportPresetSchoolUi> = emptyList(),
    val presetsBySchool: Map<String, List<ImportPresetItemUi>> = emptyMap(),
    val general: List<ImportPresetItemUi> = emptyList(),
    /**
     * Whether the device has any preset at all.
     *
     * Kept apart from "nothing is visible" because the two need different words: one offers to import
     * a script, the other suggests a different search.
     */
    val hasAnyPreset: Boolean = false,
    val message: String? = null
) {
    val hasVisiblePreset: Boolean get() = schools.isNotEmpty() || general.isNotEmpty()
}

/**
 * Backs the preset picker and the list the user manages their own presets from.
 *
 * One screen serves both: the presets are the same list, and the only difference between picking one
 * and managing one is which control the user touches.
 */
class ImportPresetListViewModel(
    private val getImportPresetsUseCase: GetImportPresetsUseCase,
    private val removeCustomImportPresetUseCase: RemoveCustomImportPresetUseCase
) : ViewModel() {

    /**
     * The last list read from storage, kept so a search does not have to re-read it.
     *
     * Reading touches the APK's assets and the user's files, which is wasteful for a keystroke.
     */
    private var loadedPresets: List<ImportPreset> = emptyList()

    private val _uiState = MutableStateFlow(ImportPresetListUiState())
    val uiState: StateFlow<ImportPresetListUiState> = _uiState.asStateFlow()

    /**
     * Re-reads the presets.
     *
     * Driven by the screen rather than by construction: the screen is composed again every time the user
     * returns to it, and a preset added or changed in the editor has to be visible when they do.
     */
    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadedPresets = getImportPresetsUseCase()
            publishSections()
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        publishSections()
    }

    /** Deletes one of the user's own presets. */
    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            if (!removeCustomImportPresetUseCase(presetId)) {
                _uiState.update { it.copy(message = "删除失败，请重试。") }
                return@launch
            }
            loadedPresets = getImportPresetsUseCase()
            publishSections()
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
    private fun publishSections() {
        val sections = ImportPresetListFilter.sections(loadedPresets, _uiState.value.query)
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                schools = sections.schools.map { section ->
                    ImportPresetSchoolUi(
                        id = section.id,
                        name = section.name,
                        code = section.code,
                        scriptCount = section.presets.size
                    )
                },
                presetsBySchool = sections.schools.associate { section ->
                    section.id to section.presets.map { preset -> preset.toItemUi() }
                },
                general = sections.general.map { preset -> preset.toItemUi() },
                hasAnyPreset = loadedPresets.isNotEmpty()
            )
        }
    }

    companion object {
        /** Manual factory mirrors the current non-Hilt architecture used across the app. */
        fun factory(
            getImportPresetsUseCase: GetImportPresetsUseCase,
            removeCustomImportPresetUseCase: RemoveCustomImportPresetUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ImportPresetListViewModel(
                        getImportPresetsUseCase = getImportPresetsUseCase,
                        removeCustomImportPresetUseCase = removeCustomImportPresetUseCase
                    ) as T
                }
            }
    }
}

private fun ImportPreset.toItemUi() = ImportPresetItemUi(
    id = id,
    name = name,
    description = description,
    maintainer = maintainer,
    isCustom = source == ImportPresetSourceKind.CUSTOM
)
