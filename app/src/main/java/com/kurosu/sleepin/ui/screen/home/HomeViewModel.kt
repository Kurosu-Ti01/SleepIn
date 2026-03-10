package com.kurosu.sleepin.ui.screen.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Keeps phase 1 home UI state in a single flow.
 * Later phases can replace hard-coded demo data with repository-backed data.
 */
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            selectedWeek = 1,
            totalWeeks = 18,
            selectedTimetable = "示例课程表",
            selectedSchedule = "默认作息"
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onWeekChanged(week: Int) {
        _uiState.update { state -> state.copy(selectedWeek = week.coerceIn(1, state.totalWeeks)) }
    }

    fun onTimetableSelected(name: String) {
        _uiState.update { state -> state.copy(selectedTimetable = name) }
    }

    fun onScheduleSelected(name: String) {
        _uiState.update { state -> state.copy(selectedSchedule = name) }
    }
}

/**
 * Home screen UI state placeholder for phase 1.
 */
data class HomeUiState(
    val selectedWeek: Int,
    val totalWeeks: Int,
    val selectedTimetable: String,
    val selectedSchedule: String
)

