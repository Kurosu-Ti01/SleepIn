package com.kurosu.sleepin.ui.screen.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.repository.DeleteScheduleResult
import com.kurosu.sleepin.domain.usecase.schedule.DeleteScheduleUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleUsageCountUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * Aggregated row model rendered by the schedule list screen.
 */
data class ScheduleListItemUi(
    val id: Long,
    val name: String,
    val periodCount: Int,
    val timeRange: String,
    val timetableUsageCount: Int
)

/**
 * Entire state for schedule list screen.
 */
data class ScheduleListUiState(
    val isLoading: Boolean = true,
    val items: List<ScheduleListItemUi> = emptyList(),
    val message: String? = null
)

/**
 * Binds repository-backed schedule data to list UI.
 *
 * The list needs summary metadata (period count, range, usage count), so this ViewModel
 * performs small aggregation work across use cases.
 */
class ScheduleListViewModel(
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase,
    private val getScheduleUsageCountUseCase: GetScheduleUsageCountUseCase,
    private val deleteScheduleUseCase: DeleteScheduleUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleListUiState())
    val uiState: StateFlow<ScheduleListUiState> = _uiState.asStateFlow()

    init {
        observeSchedules()
    }

    /** Clears snackbar message after UI consumption. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Attempts deletion and exposes user-readable result in [ScheduleListUiState.message].
     */
    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            when (val result = deleteScheduleUseCase(scheduleId)) {
                DeleteScheduleResult.Deleted -> {
                    _uiState.update { it.copy(message = "作息表已删除") }
                }

                is DeleteScheduleResult.InUse -> {
                    _uiState.update {
                        it.copy(message = "该作息表仍被 ${result.timetableCount} 个课程表使用，无法删除")
                    }
                }
            }
        }
    }

    /**
     * Collects schedule stream and enriches each row with detail + usage data.
     */
    private fun observeSchedules() {
        viewModelScope.launch {
            getSchedulesUseCase().collect { schedules ->
                val formatter = DateTimeFormatter.ofPattern("HH:mm")
                val items = schedules.map { schedule ->
                    val detail = getScheduleDetailUseCase(schedule.id)
                    val periods = detail?.periods.orEmpty()
                    val firstStart = periods.minByOrNull { it.periodNumber }?.startTime
                    val lastEnd = periods.maxByOrNull { it.periodNumber }?.endTime
                    val timeRange = if (firstStart != null && lastEnd != null) {
                        "${firstStart.format(formatter)} - ${lastEnd.format(formatter)}"
                    } else {
                        "未设置课节"
                    }

                    ScheduleListItemUi(
                        id = schedule.id,
                        name = schedule.name,
                        periodCount = periods.size,
                        timeRange = timeRange,
                        timetableUsageCount = getScheduleUsageCountUseCase(schedule.id)
                    )
                }
                _uiState.value = ScheduleListUiState(isLoading = false, items = items)
            }
        }
    }

    companion object {
        /**
         * Manual ViewModel factory used by navigation host.
         */
        fun factory(
            getSchedulesUseCase: GetSchedulesUseCase,
            getScheduleDetailUseCase: GetScheduleDetailUseCase,
            getScheduleUsageCountUseCase: GetScheduleUsageCountUseCase,
            deleteScheduleUseCase: DeleteScheduleUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ScheduleListViewModel(
                        getSchedulesUseCase = getSchedulesUseCase,
                        getScheduleDetailUseCase = getScheduleDetailUseCase,
                        getScheduleUsageCountUseCase = getScheduleUsageCountUseCase,
                        deleteScheduleUseCase = deleteScheduleUseCase
                    ) as T
                }
            }
    }
}
