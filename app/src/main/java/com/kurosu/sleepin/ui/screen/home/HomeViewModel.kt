package com.kurosu.sleepin.ui.screen.home

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetablesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.SetActiveTimetableUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps Home screen state synchronized with active timetable context.
 */
class HomeViewModel(
    private val getTimetablesUseCase: GetTimetablesUseCase,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val setActiveTimetableUseCase: SetActiveTimetableUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedWeek = 1,
            totalWeeks = 18
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeMenuState()
    }

    fun onWeekChanged(week: Int) {
        _uiState.update { state -> state.copy(selectedWeek = week.coerceIn(1, state.totalWeeks)) }
    }

    /**
     * Activates the selected timetable and lets all dependent screens refresh automatically.
     */
    fun onTimetableSelected(timetableId: Long) {
        viewModelScope.launch {
            setActiveTimetableUseCase(timetableId)
        }
    }

    /**
     * Combines timetable and schedule streams so home menu can show readable labels.
     */
    private fun observeMenuState() {
        viewModelScope.launch {
            combine(getTimetablesUseCase(), getSchedulesUseCase()) { timetables, schedules ->
                val active = timetables.firstOrNull { it.isActive }
                val scheduleNameMap = schedules.associate { it.id to it.name }
                val totalWeeks = active?.totalWeeks ?: 18
                val selectedWeek = _uiState.value.selectedWeek.coerceIn(1, totalWeeks)

                HomeUiState(
                    selectedWeek = selectedWeek,
                    totalWeeks = totalWeeks,
                    selectedTimetable = active?.name ?: "未选择课程表",
                    selectedSchedule = active?.let { scheduleNameMap[it.scheduleId] ?: "未知作息表" } ?: "未选择作息表",
                    timetables = timetables.map { HomeTimetableOption(id = it.id, name = it.name, isActive = it.isActive) }
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    companion object {
        /** Manual factory keeps construction pattern consistent with other modules. */
        fun factory(
            getTimetablesUseCase: GetTimetablesUseCase,
            getSchedulesUseCase: GetSchedulesUseCase,
            setActiveTimetableUseCase: SetActiveTimetableUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        getTimetablesUseCase = getTimetablesUseCase,
                        getSchedulesUseCase = getSchedulesUseCase,
                        setActiveTimetableUseCase = setActiveTimetableUseCase
                    ) as T
                }
            }
    }
}

/**
 * Timetable option rendered inside the Home quick-switch panel.
 */
data class HomeTimetableOption(
    val id: Long,
    val name: String,
    val isActive: Boolean
)

/**
 * Home screen state consumed by top app bar and menu sheet.
 */
data class HomeUiState(
    val selectedWeek: Int,
    val totalWeeks: Int,
    val selectedTimetable: String = "未选择课程表",
    val selectedSchedule: String = "未选择作息表",
    val timetables: List<HomeTimetableOption> = emptyList()
)

