package com.kurosu.sleepin.ui.screen.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.importer.CustomImportPresetDraft
import com.kurosu.sleepin.domain.model.importer.ImportPresetAddResult
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup
import com.kurosu.sleepin.domain.model.importer.ImportPresetSchool
import com.kurosu.sleepin.domain.usecase.importer.AddCustomImportPresetUseCase
import com.kurosu.sleepin.domain.usecase.importer.GetImportPresetsUseCase
import com.kurosu.sleepin.domain.usecase.importer.ReadImportScriptUseCase
import com.kurosu.sleepin.domain.usecase.importer.UpdateCustomImportPresetUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How much of a script the editor shows.
 *
 * A real adapter is tens of thousands of characters, and a text field lays out everything it is given,
 * so the preview is cut to something a phone can render while still showing enough to recognise the
 * script by.
 */
internal const val SCRIPT_PREVIEW_CHAR_LENGTH = 1200

/**
 * State of the custom preset editor.
 */
data class ImportPresetEditorUiState(
    val isLoading: Boolean = false,
    val isEditMode: Boolean = false,
    val name: String = "",
    val group: ImportPresetGroup = ImportPresetGroup.UNDERGRADUATE,
    val schoolId: String? = null,
    val schoolName: String = "",
    val schoolCode: String = "",
    val schoolSuggestions: List<ImportPresetSchool> = emptyList(),
    val initial: String = FALLBACK_PRESET_INITIAL,
    val startUrl: String = "",
    val description: String = "",
    val maintainer: String = "",
    val scriptText: String = "",
    /** Where the current script came from, for the user to confirm it is the file they meant. */
    val scriptSourceLabel: String = "",
    val isSaving: Boolean = false,
    val message: String? = null
) {
    val hasScript: Boolean get() = scriptText.isNotBlank()

    val scriptCharacterCount: Int get() = scriptText.length

    val scriptPreview: String get() = scriptText.take(SCRIPT_PREVIEW_CHAR_LENGTH)

    val isScriptPreviewTruncated: Boolean get() = scriptText.length > SCRIPT_PREVIEW_CHAR_LENGTH
}

/**
 * One-shot outcomes of the editor.
 */
sealed interface ImportPresetEditorEvent {
    /**
     * The preset was stored.
     *
     * @param presetId the stored preset, which is a new id when an existing preset was replaced.
     * @param runNow whether the user asked to try it straight away rather than go back to the list.
     */
    data class Saved(val presetId: String, val runNow: Boolean) : ImportPresetEditorEvent
}

/**
 * Backs the form that creates a preset of the user's own, and the one that changes an existing one.
 *
 * Both are the same form because they accept the same thing; only the storage step differs, and that
 * difference lives in the use cases rather than here.
 */
class ImportPresetEditorViewModel(
    private val presetId: String?,
    private val getImportPresetsUseCase: GetImportPresetsUseCase,
    private val readImportScriptUseCase: ReadImportScriptUseCase,
    private val addCustomImportPresetUseCase: AddCustomImportPresetUseCase,
    private val updateCustomImportPresetUseCase: UpdateCustomImportPresetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportPresetEditorUiState(isEditMode = presetId != null))
    val uiState: StateFlow<ImportPresetEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ImportPresetEditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ImportPresetEditorEvent> = _events.asSharedFlow()

    init {
        loadForm(presetId)
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { state ->
            state.copy(
                name = value,
                initial = initialAfterNameChange(
                    previousName = state.name,
                    newName = value,
                    currentInitial = state.initial
                )
            )
        }
    }

    fun onGroupChange(value: ImportPresetGroup) {
        _uiState.update { it.copy(group = value) }
    }

    fun onSchoolNameChange(value: String) {
        _uiState.update { state ->
            val match = state.schoolSuggestions.firstOrNull { school ->
                school.name.equals(value.trim(), ignoreCase = true) ||
                    school.aliases.any { alias -> alias.equals(value.trim(), ignoreCase = true) }
            }
            state.copy(
                schoolId = match?.id,
                schoolName = match?.name ?: value,
                schoolCode = match?.code.orEmpty()
            )
        }
    }

    fun onSchoolSelected(school: ImportPresetSchool) {
        _uiState.update {
            it.copy(schoolId = school.id, schoolName = school.name, schoolCode = school.code.orEmpty())
        }
    }

    fun onSchoolCodeChange(value: String) {
        _uiState.update { it.copy(schoolCode = value) }
    }

    fun onInitialChange(value: String) {
        _uiState.update { it.copy(initial = sanitizeInitialInput(value)) }
    }

    fun onStartUrlChange(value: String) {
        _uiState.update { it.copy(startUrl = value) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update { it.copy(description = value) }
    }

    fun onMaintainerChange(value: String) {
        _uiState.update { it.copy(maintainer = value) }
    }

    /** Records the text the user picked from a file or pasted, and where it came from. */
    fun onScriptLoaded(text: String, sourceLabel: String) {
        _uiState.update { it.copy(scriptText = text, scriptSourceLabel = sourceLabel) }
    }

    fun clearScript() {
        _uiState.update { it.copy(scriptText = "", scriptSourceLabel = "") }
    }

    /**
     * Reports that no script could be obtained, with the reason to show.
     *
     * The screen owns reading from a file and from the clipboard, so it owns the wording too; this
     * method exists so that failing to supply a script still arrives through the one message channel
     * the screen watches.
     */
    fun onScriptUnavailable(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    /**
     * Stores the form.
     *
     * @param runNow whether to try the preset immediately after saving. Offered because the loop that
     *   makes a preset work is "write, save, run, look at what went wrong" — making the user go back to
     *   the list between steps would be the slowest part of it.
     */
    fun save(runNow: Boolean) {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        val draft = CustomImportPresetDraft(
            name = state.name,
            group = state.group,
            initial = state.initial,
            startUrl = state.startUrl,
            description = state.description,
            maintainer = state.maintainer,
            scriptText = state.scriptText,
            schoolId = state.schoolId,
            schoolName = state.schoolName,
            schoolCode = state.schoolCode
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = if (presetId == null) {
                addCustomImportPresetUseCase(draft)
            } else {
                updateCustomImportPresetUseCase(previousPresetId = presetId, draft = draft)
            }

            when (result) {
                is ImportPresetAddResult.Added -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(ImportPresetEditorEvent.Saved(presetId = result.preset.id, runNow = runNow))
                }

                is ImportPresetAddResult.Rejected ->
                    _uiState.update { it.copy(isSaving = false, message = result.message) }

                is ImportPresetAddResult.Failed ->
                    _uiState.update { it.copy(isSaving = false, message = result.message) }
            }
        }
    }

    /**
     * Fills the form from a stored preset.
     *
     * A script that cannot be read is reported but not treated as fatal: the form still opens, and
     * saving without fixing it is refused with the same message the user would get for a blank script.
     */
    private fun loadForm(presetId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val presets = getImportPresetsUseCase()
            val schoolSuggestions = presets
                .filter { preset -> preset.schoolId != null && !preset.schoolName.isNullOrBlank() }
                .distinctBy { preset -> preset.schoolId }
                .map { preset ->
                    ImportPresetSchool(
                        id = preset.schoolId!!,
                        name = preset.schoolName!!,
                        code = preset.schoolCode,
                        aliases = preset.schoolAliases
                    )
                }
            if (presetId == null) {
                _uiState.update { it.copy(isLoading = false, schoolSuggestions = schoolSuggestions) }
                return@launch
            }

            val preset = presets.firstOrNull { candidate -> candidate.id == presetId }
            if (preset == null) {
                _uiState.update {
                    it.copy(isLoading = false, message = "找不到该方案，可能已被删除。")
                }
                return@launch
            }

            val script = readImportScriptUseCase(preset).orEmpty()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = preset.name,
                    group = preset.group,
                    schoolId = preset.schoolId,
                    schoolName = preset.schoolName.orEmpty(),
                    schoolCode = preset.schoolCode.orEmpty(),
                    schoolSuggestions = schoolSuggestions,
                    initial = preset.initial,
                    startUrl = preset.startUrl.orEmpty(),
                    description = preset.description,
                    maintainer = preset.maintainer,
                    scriptText = script,
                    scriptSourceLabel = if (script.isBlank()) {
                        "原脚本读取失败，请重新选择文件"
                    } else {
                        "已载入该方案原有的脚本"
                    }
                )
            }
        }
    }

    companion object {
        /** Manual factory mirrors the current non-Hilt architecture used across the app. */
        fun factory(
            presetId: String?,
            getImportPresetsUseCase: GetImportPresetsUseCase,
            readImportScriptUseCase: ReadImportScriptUseCase,
            addCustomImportPresetUseCase: AddCustomImportPresetUseCase,
            updateCustomImportPresetUseCase: UpdateCustomImportPresetUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ImportPresetEditorViewModel(
                        presetId = presetId,
                        getImportPresetsUseCase = getImportPresetsUseCase,
                        readImportScriptUseCase = readImportScriptUseCase,
                        addCustomImportPresetUseCase = addCustomImportPresetUseCase,
                        updateCustomImportPresetUseCase = updateCustomImportPresetUseCase
                    ) as T
                }
            }
    }
}
