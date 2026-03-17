package com.kurosu.sleepin.ui.screen.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.usecase.csv.ExportCsvUseCase
import com.kurosu.sleepin.domain.usecase.csv.ImportCsvUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.CreateTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.DeleteTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import com.kurosu.sleepin.domain.usecase.timetable.TimetableSaveResult
import com.kurosu.sleepin.domain.usecase.timetable.UpdateTimetableUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Lightweight option model for schedule dropdown selection.
 */
data class ScheduleOptionUi(
    val id: Long,
    val name: String
)

/**
 * Complete editable state of timetable editor screen.
 */
data class TimetableEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isCsvBusy: Boolean = false,
    val name: String = "",
    val totalWeeks: String = "16",
    val startDateText: String = LocalDate.now().toString(),
    val colorScheme: String = "",
    val scheduleOptions: List<ScheduleOptionUi> = emptyList(),
    val selectedScheduleId: Long? = null,
    val message: String? = null,
    val csvImportErrorDetail: String? = null,
    val isEditMode: Boolean = false
)

/**
 * One-shot events that should not be persisted inside long-lived state.
 */
sealed interface TimetableEditorEvent {
    data object Saved : TimetableEditorEvent
    data class ExportCsvReady(val fileName: String, val content: String) : TimetableEditorEvent
}

/**
 * Coordinates timetable creation and editing.
 *
 * Core responsibilities:
 * - load existing timetable when `timetableId` is provided
 * - collect schedule options for linking timetable to one schedule template
 * - validate and delegate create/update operations to domain use cases
 */
class TimetableEditorViewModel(
    private val timetableId: Long?,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase,
    private val getTimetableDetailUseCase: GetTimetableDetailUseCase,
    private val createTimetableUseCase: CreateTimetableUseCase,
    private val deleteTimetableUseCase: DeleteTimetableUseCase,
    private val updateTimetableUseCase: UpdateTimetableUseCase,
    private val importCsvUseCase: ImportCsvUseCase,
    private val exportCsvUseCase: ExportCsvUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableEditorUiState(isEditMode = timetableId != null))
    val uiState: StateFlow<TimetableEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TimetableEditorEvent>()
    val events: SharedFlow<TimetableEditorEvent> = _events.asSharedFlow()

    init {
        observeSchedules()
        if (timetableId != null) {
            loadTimetable(timetableId)
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumeCsvImportErrorDetail() {
        _uiState.update { it.copy(csvImportErrorDetail = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun onTotalWeeksChange(value: String) {
        _uiState.update { it.copy(totalWeeks = value) }
    }

    fun onStartDateChange(value: String) {
        _uiState.update { it.copy(startDateText = value) }
    }

    fun onColorSchemeChange(value: String) {
        _uiState.update { it.copy(colorScheme = value) }
    }

    fun onScheduleChange(scheduleId: Long) {
        _uiState.update { it.copy(selectedScheduleId = scheduleId) }
    }

    /**
     * Creates a brand-new timetable and immediately imports courses from a CSV payload.
     *
     * Why this flow exists:
     * - CSV import requires a concrete `timetableId` as persistence target.
     * - In create mode, that id does not exist until the timetable is saved successfully.
     *
     * This method therefore executes a two-step transaction-like sequence:
     * 1) validate/create timetable,
     * 2) resolve schedule max period and import CSV rows into the newly created timetable.
     */
    fun createAndImportCsv(rawCsv: String) {
        if (timetableId != null) {
            emitMessage("CSV import for existing timetable is not supported in create flow")
            return
        }

        viewModelScope.launch {
            val state = _uiState.value
            val totalWeeks = state.totalWeeks.toIntOrNull()
                ?: return@launch emitMessage("总周数必须是整数")
            val startDate = runCatching { LocalDate.parse(state.startDateText.trim()) }.getOrNull()
                ?: return@launch emitMessage("开学日期格式应为 yyyy-MM-dd")
            val scheduleId = state.selectedScheduleId
                ?: return@launch emitMessage("请选择作息表")

            _uiState.update { it.copy(isSaving = true, isCsvBusy = true) }

            when (
                val createResult = createTimetableUseCase(
                    name = state.name,
                    totalWeeks = totalWeeks,
                    startDate = startDate,
                    scheduleId = scheduleId,
                    colorScheme = state.colorScheme
                )
            ) {
                is TimetableSaveResult.Success -> {
                    val scheduleDetail = getScheduleDetailUseCase(scheduleId)
                    val maxPeriod = scheduleDetail?.periods?.maxOfOrNull { it.periodNumber }
                    if (maxPeriod == null || maxPeriod <= 0) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                isCsvBusy = false,
                                message = "所选作息表没有可用课节，无法导入 CSV"
                            )
                        }
                        return@launch
                    }

                    val report = importCsvUseCase(
                        timetableId = createResult.timetableId,
                        totalWeeks = totalWeeks,
                        maxPeriod = maxPeriod,
                        rawCsv = rawCsv
                    )

                    // Avoid leaving a meaningless empty timetable when import produced no sessions.
                    if (report.importedSessionCount == 0) {
                        deleteTimetableUseCase(createResult.timetableId)
                    }

                    val isEmptyImport = report.importedSessionCount == 0

                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isCsvBusy = false,
                            message = if (isEmptyImport) {
                                "未导入任何课时，已取消创建课程表"
                            } else {
                                buildString {
                                    append("导入完成：")
                                    append(report.importedCourseCount)
                                    append(" 门课程，")
                                    append(report.importedSessionCount)
                                    append(" 条课时")
                                    if (report.errors.isNotEmpty()) {
                                        append("，")
                                        append(report.errors.size)
                                        append(" 行失败")
                                    }
                                }
                            },
                            csvImportErrorDetail = buildCsvImportErrorDetail(report)
                        )
                    }
                    // Keep user on this page when any row fails so the detail dialog can be read.
                    if (!isEmptyImport && report.errors.isEmpty()) {
                        _events.emit(TimetableEditorEvent.Saved)
                    }
                }

                is TimetableSaveResult.ValidationError -> {
                    _uiState.update {
                        it.copy(isSaving = false, isCsvBusy = false, message = createResult.message)
                    }
                }

                TimetableSaveResult.NotFound -> {
                    _uiState.update {
                        it.copy(isSaving = false, isCsvBusy = false, message = "课程表不存在，可能已被删除")
                    }
                }
            }
        }
    }

    /**
     * Exports all courses under the editing timetable to CSV and asks UI to save it.
     */
    fun exportCsvForEditingTimetable() {
        if (timetableId == null) {
            emitMessage("请先创建课程表后再导出 CSV")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCsvBusy = true) }
            val csv = exportCsvUseCase(timetableId)
            _uiState.update { it.copy(isCsvBusy = false) }
            _events.emit(
                TimetableEditorEvent.ExportCsvReady(
                    fileName = "sleepin_courses_${timetableId}_${System.currentTimeMillis()}.csv",
                    content = csv
                )
            )
        }
    }

    /**
     * Validates text input fields and submits either create or update command.
     */
    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val totalWeeks = state.totalWeeks.toIntOrNull()
                ?: return@launch emitMessage("总周数必须是整数")
            val startDate = runCatching { LocalDate.parse(state.startDateText.trim()) }.getOrNull()
                ?: return@launch emitMessage("开学日期格式应为 yyyy-MM-dd")
            val scheduleId = state.selectedScheduleId
                ?: return@launch emitMessage("请选择作息表")

            _uiState.update { it.copy(isSaving = true) }
            val result = if (timetableId == null) {
                createTimetableUseCase(
                    name = state.name,
                    totalWeeks = totalWeeks,
                    startDate = startDate,
                    scheduleId = scheduleId,
                    colorScheme = state.colorScheme
                )
            } else {
                updateTimetableUseCase(
                    timetableId = timetableId,
                    name = state.name,
                    totalWeeks = totalWeeks,
                    startDate = startDate,
                    scheduleId = scheduleId,
                    colorScheme = state.colorScheme
                )
            }

            when (result) {
                is TimetableSaveResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(TimetableEditorEvent.Saved)
                }

                is TimetableSaveResult.ValidationError -> {
                    _uiState.update { it.copy(isSaving = false, message = result.message) }
                }

                TimetableSaveResult.NotFound -> {
                    _uiState.update { it.copy(isSaving = false, message = "课程表不存在，可能已被删除") }
                }
            }
        }
    }

    /**
     * Streams schedule options so editor dropdown always reflects latest templates.
     */
    private fun observeSchedules() {
        viewModelScope.launch {
            getSchedulesUseCase().collect { schedules ->
                _uiState.update { state ->
                    val options = schedules.map { ScheduleOptionUi(id = it.id, name = it.name) }
                    state.copy(
                        scheduleOptions = options,
                        // Auto-select first option only when no option has been selected yet.
                        selectedScheduleId = state.selectedScheduleId ?: options.firstOrNull()?.id
                    )
                }
            }
        }
    }

    /** Loads timetable data for edit mode and maps it into editable text state. */
    private fun loadTimetable(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val timetable = getTimetableDetailUseCase(id)
            if (timetable == null) {
                _uiState.update { it.copy(isLoading = false, message = "未找到对应课程表") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    name = timetable.name,
                    totalWeeks = timetable.totalWeeks.toString(),
                    startDateText = timetable.startDate.toString(),
                    colorScheme = timetable.colorScheme.orEmpty(),
                    selectedScheduleId = timetable.scheduleId
                )
            }
        }
    }

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    /**
     * Builds a multi-line error report so users can quickly locate malformed CSV rows.
     */
    private fun buildCsvImportErrorDetail(report: com.kurosu.sleepin.domain.usecase.csv.CsvImportReport): String? {
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
        /** Manual factory mirrors current non-Hilt architecture used across the app. */
        fun factory(
            timetableId: Long?,
            getSchedulesUseCase: GetSchedulesUseCase,
            getScheduleDetailUseCase: GetScheduleDetailUseCase,
            getTimetableDetailUseCase: GetTimetableDetailUseCase,
            createTimetableUseCase: CreateTimetableUseCase,
            deleteTimetableUseCase: DeleteTimetableUseCase,
            updateTimetableUseCase: UpdateTimetableUseCase,
            importCsvUseCase: ImportCsvUseCase,
            exportCsvUseCase: ExportCsvUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TimetableEditorViewModel(
                        timetableId = timetableId,
                        getSchedulesUseCase = getSchedulesUseCase,
                        getScheduleDetailUseCase = getScheduleDetailUseCase,
                        getTimetableDetailUseCase = getTimetableDetailUseCase,
                        createTimetableUseCase = createTimetableUseCase,
                        deleteTimetableUseCase = deleteTimetableUseCase,
                        updateTimetableUseCase = updateTimetableUseCase,
                        importCsvUseCase = importCsvUseCase,
                        exportCsvUseCase = exportCsvUseCase
                    ) as T
                }
            }
    }
}

