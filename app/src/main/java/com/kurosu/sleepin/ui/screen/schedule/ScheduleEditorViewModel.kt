package com.kurosu.sleepin.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.usecase.schedule.ExportScheduleCsvUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.ImportScheduleCsvUseCase
import com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleResult
import com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * UI-editable representation of one period row.
 *
 * Strings are used for inputs so text fields can preserve partial/incomplete user input
 * until validation runs in the save action.
 */
data class EditableSchedulePeriod(
    val id: Long,
    val startTime: String,
    val endTime: String
)

/**
 * Complete state for the schedule editor screen.
 */
data class ScheduleEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isCsvBusy: Boolean = false,
    val name: String = "",
    val periods: List<EditableSchedulePeriod> = listOf(
        EditableSchedulePeriod(id = 0, startTime = "08:00", endTime = "08:45")
    ),
    // Fields used by the quick generation tool.
    val quickStartTime: String = "08:00",
    val quickClassDurationMinutes: String = "50",
    val quickBreakMinutes: String = "10",
    val quickPeriodCount: String = "10",
    // One-shot message consumed by the screen snackbar.
    val message: String? = null,
    val csvImportErrorDetail: String? = null,
    val isEditMode: Boolean = false
)

/**
 * One-off events that should not be kept forever inside state.
 */
sealed interface ScheduleEditorEvent {
    data object Saved : ScheduleEditorEvent
    data class ExportCsvReady(val fileName: String, val content: String) : ScheduleEditorEvent
}

/**
 * Coordinates schedule editor behavior:
 * - load existing detail when editing
 * - mutate text fields
 * - generate period rows from quick settings
 * - validate + persist
 */
class ScheduleEditorViewModel(
    private val scheduleId: Long?,
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase,
    private val saveScheduleUseCase: SaveScheduleUseCase,
    private val importScheduleCsvUseCase: ImportScheduleCsvUseCase,
    private val exportScheduleCsvUseCase: ExportScheduleCsvUseCase
) : ViewModel() {

    // Preserved on edit so save keeps historical creation time.
    private var createdAt: Long? = null

    private val _uiState = MutableStateFlow(ScheduleEditorUiState(isEditMode = scheduleId != null))
    val uiState: StateFlow<ScheduleEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScheduleEditorEvent>()
    val events: SharedFlow<ScheduleEditorEvent> = _events.asSharedFlow()

    init {
        if (scheduleId != null) {
            loadSchedule(scheduleId)
        }
    }

    /** Clears current snackbar message after UI consumes it. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeCsvImportErrorDetail() {
        _uiState.update { it.copy(csvImportErrorDetail = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onQuickStartTimeChange(value: String) {
        _uiState.update { it.copy(quickStartTime = value) }
    }

    fun onQuickClassDurationChange(value: String) {
        _uiState.update { it.copy(quickClassDurationMinutes = value) }
    }

    fun onQuickBreakChange(value: String) {
        _uiState.update { it.copy(quickBreakMinutes = value) }
    }

    fun onQuickPeriodCountChange(value: String) {
        _uiState.update { it.copy(quickPeriodCount = value) }
    }

    fun onPeriodStartTimeChange(index: Int, value: String) {
        updatePeriod(index) { it.copy(startTime = value) }
    }

    fun onPeriodEndTimeChange(index: Int, value: String) {
        updatePeriod(index) { it.copy(endTime = value) }
    }

    /** Appends a new row. Period numbering is derived from row order at save time. */
    fun addPeriod() {
        _uiState.update {
            it.copy(
                periods = it.periods + EditableSchedulePeriod(
                    id = 0,
                    startTime = "08:00",
                    endTime = "08:45"
                )
            )
        }
    }

    /** Removes one row by index (UI controls index validity). */
    fun removePeriod(index: Int) {
        _uiState.update { state ->
            state.copy(periods = state.periods.filterIndexed { i, _ -> i != index })
        }
    }

    /**
     * Generates a full period list from quick parameters.
     *
     * Example: start 08:00, class 45, break 10, count 3 ->
     * 1) 08:00-08:45
     * 2) 08:55-09:40
     * 3) 09:50-10:35
     */
    fun generatePeriods() {
        val state = _uiState.value
        val start = parseTime(state.quickStartTime)
            ?: return emitMessage("自动生成失败：开始时间格式应为 HH:mm")
        val classDuration = state.quickClassDurationMinutes.toIntOrNull()
            ?: return emitMessage("自动生成失败：课时长度应为整数")
        val breakDuration = state.quickBreakMinutes.toIntOrNull()
            ?: return emitMessage("自动生成失败：课间时长应为整数")
        val periodCount = state.quickPeriodCount.toIntOrNull()
            ?: return emitMessage("自动生成失败：总课节数应为整数")

        if (classDuration <= 0 || breakDuration < 0 || periodCount <= 0) {
            return emitMessage("自动生成失败：请检查课时、课间和总课节参数")
        }

        val generated = mutableListOf<EditableSchedulePeriod>()
        var cursor = start
        repeat(periodCount) {
            val end = cursor.plusMinutes(classDuration.toLong())
            generated += EditableSchedulePeriod(
                id = 0,
                startTime = cursor.toString(),
                endTime = end.toString()
            )
            cursor = end.plusMinutes(breakDuration.toLong())
        }

        _uiState.update { it.copy(periods = generated) }
    }

    /**
     * Converts text rows to strongly typed drafts and delegates persistence.
     */
    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val drafts = state.periods.mapIndexed { index, period ->
                val start = parseTime(period.startTime) ?: return@launch emitMessage("开始时间格式应为 HH:mm")
                val end = parseTime(period.endTime) ?: return@launch emitMessage("结束时间格式应为 HH:mm")
                SaveScheduleUseCase.PeriodDraft(
                    id = period.id,
                    periodNumber = index + 1,
                    startTime = start,
                    endTime = end
                )
            }

            _uiState.update { it.copy(isSaving = true) }
            when (val result = saveScheduleUseCase(scheduleId, state.name, createdAt, drafts)) {
                is SaveScheduleResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(ScheduleEditorEvent.Saved)
                }

                is SaveScheduleResult.ValidationError -> {
                    _uiState.update { it.copy(isSaving = false, message = result.message) }
                }
            }
        }
    }

    /**
     * Imports one CSV file and creates a brand-new schedule from it.
     */
    fun importCsvForCreate(rawCsv: String) {
        if (scheduleId != null) {
            emitMessage("仅支持在新建作息表时导入 CSV")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCsvBusy = true) }
            val report = importScheduleCsvUseCase(rawCsv)
            val hasImported = report.importedScheduleId != null && report.importedPeriodCount > 0

            _uiState.update {
                it.copy(
                    isCsvBusy = false,
                    message = if (hasImported) {
                        buildString {
                            append("导入完成：")
                            append(report.importedPeriodCount)
                            append(" 条课节")
                            if (report.errors.isNotEmpty()) {
                                append("，")
                                append(report.errors.size)
                                append(" 行失败")
                            }
                        }
                    } else {
                        "未导入任何课节"
                    },
                    csvImportErrorDetail = buildCsvImportErrorDetail(report)
                )
            }

            if (hasImported && report.errors.isEmpty()) {
                _events.emit(ScheduleEditorEvent.Saved)
            }
        }
    }

    /**
     * Exports the currently edited schedule as a CSV file.
     */
    fun exportCsvForEditingSchedule() {
        val id = scheduleId
        if (id == null) {
            emitMessage("请先保存作息表后再导出 CSV")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCsvBusy = true) }
            runCatching {
                exportScheduleCsvUseCase(id)
            }.onSuccess { csv ->
                _uiState.update { it.copy(isCsvBusy = false) }
                _events.emit(
                    ScheduleEditorEvent.ExportCsvReady(
                        fileName = "sleepin_schedule_${id}_${System.currentTimeMillis()}.csv",
                        content = csv
                    )
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isCsvBusy = false,
                        message = "导出失败：${error.message ?: "未知错误"}"
                    )
                }
            }
        }
    }

    /** Loads existing schedule and maps domain detail to editable text state. */
    private fun loadSchedule(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val detail = getScheduleDetailUseCase(id)
            if (detail == null) {
                _uiState.update { it.copy(isLoading = false, message = "未找到对应作息表") }
                return@launch
            }

            createdAt = detail.schedule.createdAt
            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = detail.schedule.name,
                    periods = detail.periods
                        .sortedBy { it.periodNumber }
                        .map { period ->
                        EditableSchedulePeriod(
                            id = period.id,
                            startTime = period.startTime.toString(),
                            endTime = period.endTime.toString()
                        )
                    }
                )
            }
        }
    }

    /** Applies a row-level transform while keeping immutable state updates. */
    private fun updatePeriod(index: Int, transform: (EditableSchedulePeriod) -> EditableSchedulePeriod) {
        _uiState.update { state ->
            val updated = state.periods.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(periods = updated)
        }
    }

    /** Parses HH:mm-like text into [LocalTime]. */
    private fun parseTime(value: String): LocalTime? =
        runCatching { LocalTime.parse(value.trim()) }.getOrNull()

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun buildCsvImportErrorDetail(report: com.kurosu.sleepin.domain.usecase.schedule.ScheduleCsvImportReport): String? {
        if (report.errors.isEmpty()) return null
        return buildString {
            append("导入遇到错误，请检查以下行：\n\n")
            report.errors.forEach { error ->
                append("第 ")
                append(error.rowNumber)
                append(" 行：")
                append(error.message)
                append('\n')
            }
        }.trimEnd()
    }

    companion object {
        /**
         * Manual factory used until DI framework wiring is introduced.
         */
        fun factory(
            scheduleId: Long?,
            getScheduleDetailUseCase: GetScheduleDetailUseCase,
            saveScheduleUseCase: SaveScheduleUseCase,
            importScheduleCsvUseCase: ImportScheduleCsvUseCase,
            exportScheduleCsvUseCase: ExportScheduleCsvUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ScheduleEditorViewModel(
                        scheduleId = scheduleId,
                        getScheduleDetailUseCase = getScheduleDetailUseCase,
                        saveScheduleUseCase = saveScheduleUseCase,
                        importScheduleCsvUseCase = importScheduleCsvUseCase,
                        exportScheduleCsvUseCase = exportScheduleCsvUseCase
                    ) as T
                }
            }
    }
}
