package com.kurosu.sleepin.ui.screen.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.importer.ImportLocalSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportPreset
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSourceKind
import com.kurosu.sleepin.domain.model.importer.ImportSourceDraft
import com.kurosu.sleepin.domain.model.importer.ImportSourceImportResult
import com.kurosu.sleepin.domain.usecase.importer.GetImportPresetsUseCase
import com.kurosu.sleepin.domain.usecase.importer.ImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.RemoveCustomImportPresetUseCase
import com.kurosu.sleepin.domain.usecase.importer.ReuseLocalFamilyUseCase
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
    val isImportingSource: Boolean = false,
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
    /**
     * Families cached on this device by an imported source.
     *
     * Only used to explain an otherwise empty list: a source may publish families before it has any
     * preset, and telling the user there is nothing would then be plainly wrong.
     */
    val cachedFamilyCount: Int = 0,
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
    private val importSourceUseCase: ImportSourceUseCase,
    private val removeCustomImportPresetUseCase: RemoveCustomImportPresetUseCase,
    private val reuseLocalFamilyUseCase: ReuseLocalFamilyUseCase
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
            _uiState.update { it.copy(cachedFamilyCount = reuseLocalFamilyUseCase.availableFamilyIds().size) }
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

    /** Imports a source from a user-provided HTTPS index URL. */
    fun importSource(indexUrl: String, reuseLocalFamilies: Boolean) {
        if (_uiState.value.isImportingSource) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingSource = true) }
            publishImportResult(importSourceUseCase(ImportSourceDraft(indexUrl, reuseLocalFamilies)))
        }
    }

    /**
     * Imports a source from a directory the user picked on the device.
     *
     * @param rootUri URI text of the granted directory. The screen converts the picker result, so this
     *   layer keeps no platform types.
     */
    fun importLocalSource(rootUri: String, reuseLocalFamilies: Boolean) {
        if (_uiState.value.isImportingSource) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingSource = true) }
            publishImportResult(
                importSourceUseCase.importFromDirectory(ImportLocalSourceDraft(rootUri, reuseLocalFamilies))
            )
        }
    }

    /**
     * Reports how an import ended and, on success, re-reads the list so the new presets are visible.
     *
     * Both ways of importing finish here, so what the user reads does not depend on which one they used.
     */
    private suspend fun publishImportResult(result: ImportSourceImportResult) {
        when (result) {
            is ImportSourceImportResult.Imported -> {
                loadedPresets = getImportPresetsUseCase()
                publishSections()
                _uiState.update { it.copy(isImportingSource = false, message = importSummary(result)) }
            }

            is ImportSourceImportResult.Rejected ->
                _uiState.update { it.copy(isImportingSource = false, message = result.message) }

            is ImportSourceImportResult.Failed ->
                _uiState.update { it.copy(isImportingSource = false, message = result.message) }
        }
    }

    /**
     * What to say about a finished import.
     *
     * A source can publish families before it has any preset — the family repository itself is in that
     * state — and then the list stays empty even though something useful was stored. Saying so, and where
     * to use it, is the difference between "it worked" and "it silently did nothing".
     */
    private fun importSummary(result: ImportSourceImportResult.Imported): String = when {
        result.presetCount > 0 ->
            "已导入 ${result.presetCount} 个方案，缓存/复用 ${result.familyCount} 个族。"

        result.familyCount > 0 ->
            "该源只有族，还没有方案：已缓存 ${result.familyCount} 个族，" +
                "在「导入自己的脚本」里选「用本地缓存的族组装」即可复用。"

        else -> "该源里没有可导入的内容。"
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
            importSourceUseCase: ImportSourceUseCase,
            removeCustomImportPresetUseCase: RemoveCustomImportPresetUseCase,
            reuseLocalFamilyUseCase: ReuseLocalFamilyUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ImportPresetListViewModel(
                        getImportPresetsUseCase = getImportPresetsUseCase,
                        importSourceUseCase = importSourceUseCase,
                        removeCustomImportPresetUseCase = removeCustomImportPresetUseCase,
                        reuseLocalFamilyUseCase = reuseLocalFamilyUseCase
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
