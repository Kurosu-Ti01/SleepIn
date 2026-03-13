package com.kurosu.sleepin.ui.screen.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.CreateTimetableUseCase
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
    val name: String = "",
    val totalWeeks: String = "18",
    val startDateText: String = LocalDate.now().toString(),
    val colorScheme: String = "",
    val scheduleOptions: List<ScheduleOptionUi> = emptyList(),
    val selectedScheduleId: Long? = null,
    val message: String? = null,
    val isEditMode: Boolean = false
)

/**
 * One-shot events that should not be persisted inside long-lived state.
 */
sealed interface TimetableEditorEvent {
    data object Saved : TimetableEditorEvent
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
    private val getTimetableDetailUseCase: GetTimetableDetailUseCase,
    private val createTimetableUseCase: CreateTimetableUseCase,
    private val updateTimetableUseCase: UpdateTimetableUseCase
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

    companion object {
        /** Manual factory mirrors current non-Hilt architecture used across the app. */
        fun factory(
            timetableId: Long?,
            getSchedulesUseCase: GetSchedulesUseCase,
            getTimetableDetailUseCase: GetTimetableDetailUseCase,
            createTimetableUseCase: CreateTimetableUseCase,
            updateTimetableUseCase: UpdateTimetableUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TimetableEditorViewModel(
                        timetableId = timetableId,
                        getSchedulesUseCase = getSchedulesUseCase,
                        getTimetableDetailUseCase = getTimetableDetailUseCase,
                        createTimetableUseCase = createTimetableUseCase,
                        updateTimetableUseCase = updateTimetableUseCase
                    ) as T
                }
            }
    }
}

