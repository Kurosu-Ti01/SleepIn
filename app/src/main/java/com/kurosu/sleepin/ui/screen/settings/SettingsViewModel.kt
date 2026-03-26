package com.kurosu.sleepin.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.BuildConfig
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.domain.model.AppUpdateCheckResult
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.domain.usecase.settings.ExportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ImportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ObserveSettingsUseCase
import com.kurosu.sleepin.domain.usecase.settings.PerformUpdateCheckUseCase
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
    val isCheckingUpdate: Boolean = false,
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
    private val importSettingsBackupUseCase: ImportSettingsBackupUseCase,
    private val performUpdateCheckUseCase: PerformUpdateCheckUseCase
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

    fun setShowNonCurrentWeekCourses(enabled: Boolean) =
        updateSettings { it.copy(showNonCurrentWeekCourses = enabled) }

    /**
     * Toggles background periodic update checks.
     */
    fun setAutoCheckUpdateEnabled(enabled: Boolean) =
        updateSettings { it.copy(autoCheckUpdateEnabled = enabled) }

    /**
     * Executes a user-initiated update check that ignores the auto-check toggle.
     */
    fun checkForUpdates() {
        if (_uiState.value.isCheckingUpdate) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true) }
            val result = performUpdateCheckUseCase(
                force = true,
                currentVersionName = BuildConfig.VERSION_NAME
            )
            _uiState.update {
                it.copy(
                    isCheckingUpdate = false,
                    message = result.toUserMessage()
                )
            }
        }
    }

    /**
     * Marks current update as acknowledged after download is queued.
     */
    fun onDownloadEnqueued() {
        val currentVersion = _uiState.value.settings.latestRemoteVersion
        updateSettings {
            it.copy(dismissedUpdateVersion = currentVersion)
        }
        _uiState.update { it.copy(message = "已加入下载队列，可在系统下载中查看进度") }
    }

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
                    isCheckingUpdate = _uiState.value.isCheckingUpdate,
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
            importSettingsBackupUseCase: ImportSettingsBackupUseCase,
            performUpdateCheckUseCase: PerformUpdateCheckUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsViewModel(
                        observeSettingsUseCase = observeSettingsUseCase,
                        updateSettingsUseCase = updateSettingsUseCase,
                        exportSettingsBackupUseCase = exportSettingsBackupUseCase,
                        importSettingsBackupUseCase = importSettingsBackupUseCase,
                        performUpdateCheckUseCase = performUpdateCheckUseCase
                    ) as T
                }
            }
    }
}

private fun AppUpdateCheckResult.toUserMessage(): String =
    when (this) {
        is AppUpdateCheckResult.UpdateAvailable -> "发现新版本 ${release.versionTag}，可立即下载"
        is AppUpdateCheckResult.UpToDate -> "当前已是最新版本（$latestVersionTag）"
        is AppUpdateCheckResult.Failed -> "检查更新失败：$reason"
        AppUpdateCheckResult.Skipped -> "已跳过自动检查"
    }



