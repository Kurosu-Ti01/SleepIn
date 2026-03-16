package com.kurosu.sleepin.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.domain.usecase.settings.ExportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ImportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ObserveSettingsUseCase
import com.kurosu.sleepin.domain.usecase.settings.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state consumed by the Settings screen.
 *
 * This state intentionally focuses on preference values and one-shot message rendering.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val message: String? = null
)

/**
 * One-off UI effects.
 *
 * These are not part of persistent screen state because they represent transient actions
 * such as opening a save dialog.
 */
sealed interface SettingsUiEffect {
    data class ExportBackupReady(val fileName: String, val content: String) : SettingsUiEffect
}

/**
 * ViewModel coordinating preference edits and data import/export operations.
 */
class SettingsViewModel(
    private val observeSettingsUseCase: ObserveSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val exportSettingsBackupUseCase: ExportSettingsBackupUseCase,
    private val importSettingsBackupUseCase: ImportSettingsBackupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsUiEffect>()
    val effects: SharedFlow<SettingsUiEffect> = _effects.asSharedFlow()

    init {
        observeState()
    }

    /**
     * Clears one-time snackbar message after UI consumption.
     */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Applies one settings update by copying from the current snapshot.
     */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val current = _uiState.value.settings
            updateSettingsUseCase(transform(current))
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) =
        updateSettings { it.copy(notificationsEnabled = enabled) }

    fun setReminderMinutes(minutes: Int) =
        updateSettings { it.copy(reminderMinutes = minutes.coerceIn(5, 30)) }

    fun setFluidCloudEnabled(enabled: Boolean) =
        updateSettings { it.copy(fluidCloudEnabled = enabled) }

    fun setThemeMode(mode: ThemeMode) =
        updateSettings { it.copy(themeMode = mode) }

    fun setDynamicColorEnabled(enabled: Boolean) =
        updateSettings { it.copy(dynamicColorEnabled = enabled) }

    fun setCourseCellHeightDp(heightDp: Int) =
        updateSettings { it.copy(courseCellHeightDp = heightDp.coerceIn(44, 120)) }

    /**
     * Serializes current settings and emits an effect for file save action.
     */
    fun exportBackup() {
        viewModelScope.launch {
            val backup = exportSettingsBackupUseCase()
            _effects.emit(
                SettingsUiEffect.ExportBackupReady(
                    fileName = "sleepin_settings_backup.json",
                    content = backup
                )
            )
        }
    }

    /**
     * Restores settings from JSON backup content selected by the user.
     */
    fun importBackup(rawJson: String) {
        viewModelScope.launch {
            runCatching {
                importSettingsBackupUseCase(rawJson)
            }.onSuccess {
                _uiState.update { it.copy(message = "Settings backup restored") }
            }.onFailure { error ->
                _uiState.update { it.copy(message = "Failed to restore backup: ${error.message}") }
            }
        }
    }

    private fun observeState() {
        viewModelScope.launch {
            observeSettingsUseCase().collect { settings ->
                val state = SettingsUiState(
                    isLoading = false,
                    settings = settings,
                    message = _uiState.value.message
                )
                _uiState.value = state
            }
        }
    }

    companion object {
        /**
         * Manual factory used by the navigation graph until Hilt is introduced.
         */
        fun factory(
            observeSettingsUseCase: ObserveSettingsUseCase,
            updateSettingsUseCase: UpdateSettingsUseCase,
            exportSettingsBackupUseCase: ExportSettingsBackupUseCase,
            importSettingsBackupUseCase: ImportSettingsBackupUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsViewModel(
                        observeSettingsUseCase = observeSettingsUseCase,
                        updateSettingsUseCase = updateSettingsUseCase,
                        exportSettingsBackupUseCase = exportSettingsBackupUseCase,
                        importSettingsBackupUseCase = importSettingsBackupUseCase
                    ) as T
                }
            }
    }
}



