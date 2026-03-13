package com.kurosu.sleepin.ui.screen.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.DeleteTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetablesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.SetActiveTimetableUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Render-ready row model for timetable list cards.
 */
data class TimetableListItemUi(
    val id: Long,
    val name: String,
    val totalWeeks: Int,
    val dateRangeText: String,
    val scheduleName: String,
    val isActive: Boolean
)

/**
 * Aggregated screen state for timetable list.
 */
data class TimetableListUiState(
    val isLoading: Boolean = true,
    val items: List<TimetableListItemUi> = emptyList(),
    val message: String? = null
)

/**
 * ViewModel for the timetable list screen.
 *
 * This ViewModel combines timetable stream with schedule stream because each timetable
 * row needs to show the human-readable schedule name instead of raw schedule id.
 */
class TimetableListViewModel(
    private val getTimetablesUseCase: GetTimetablesUseCase,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val setActiveTimetableUseCase: SetActiveTimetableUseCase,
    private val deleteTimetableUseCase: DeleteTimetableUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimetableListUiState())
    val uiState: StateFlow<TimetableListUiState> = _uiState.asStateFlow()

    init {
        observeRows()
    }

    /** Clears one-shot snackbar text after it has been consumed by UI. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Marks a timetable as active and reports operation result to UI. */
    fun setActive(timetableId: Long) {
        viewModelScope.launch {
            setActiveTimetableUseCase(timetableId)
            _uiState.update { it.copy(message = "已切换当前课程表") }
        }
    }

    /** Deletes the selected timetable and exposes feedback text for snackbar. */
    fun delete(timetableId: Long) {
        viewModelScope.launch {
            deleteTimetableUseCase(timetableId)
            _uiState.update { it.copy(message = "课程表已删除") }
        }
    }

    /**
     * Collects timetable + schedule streams and maps domain models into concise UI rows.
     */
    private fun observeRows() {
        viewModelScope.launch {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE
            combine(getTimetablesUseCase(), getSchedulesUseCase()) { timetables, schedules ->
                val scheduleNameMap = schedules.associate { it.id to it.name }
                timetables.map { timetable ->
                    val endDate = timetable.startDate.plusWeeks((timetable.totalWeeks - 1).toLong()).plusDays(6)
                    TimetableListItemUi(
                        id = timetable.id,
                        name = timetable.name,
                        totalWeeks = timetable.totalWeeks,
                        dateRangeText = "${timetable.startDate.format(formatter)} ~ ${endDate.format(formatter)}",
                        scheduleName = scheduleNameMap[timetable.scheduleId] ?: "未知作息表",
                        isActive = timetable.isActive
                    )
                }
            }.collect { rows ->
                _uiState.value = TimetableListUiState(
                    isLoading = false,
                    items = rows
                )
            }
        }
    }

    companion object {
        /** Manual factory used by navigation host while project is still in manual DI phase. */
        fun factory(
            getTimetablesUseCase: GetTimetablesUseCase,
            getSchedulesUseCase: GetSchedulesUseCase,
            setActiveTimetableUseCase: SetActiveTimetableUseCase,
            deleteTimetableUseCase: DeleteTimetableUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return TimetableListViewModel(
                        getTimetablesUseCase = getTimetablesUseCase,
                        getSchedulesUseCase = getSchedulesUseCase,
                        setActiveTimetableUseCase = setActiveTimetableUseCase,
                        deleteTimetableUseCase = deleteTimetableUseCase
                    ) as T
                }
            }
    }
}

