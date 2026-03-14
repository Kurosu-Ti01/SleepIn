package com.kurosu.sleepin.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.usecase.course.DeleteCourseUseCase
import com.kurosu.sleepin.domain.usecase.course.GetCoursesForTimetableUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Render-friendly row model for each course card in the list screen.
 */
data class CourseListItemUi(
    val id: Long,
    val name: String,
    val teacher: String,
    val color: Int,
    val sessionSummary: String
)

/**
 * Full screen state for course list.
 */
data class CourseListUiState(
    val isLoading: Boolean = true,
    val items: List<CourseListItemUi> = emptyList(),
    val message: String? = null
)

/**
 * Binds course domain flow to list UI actions (view, edit, delete).
 */
class CourseListViewModel(
    private val timetableId: Long,
    private val getCoursesForTimetableUseCase: GetCoursesForTimetableUseCase,
    private val deleteCourseUseCase: DeleteCourseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseListUiState())
    val uiState: StateFlow<CourseListUiState> = _uiState.asStateFlow()

    init {
        observeCourses()
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun delete(courseId: Long) {
        viewModelScope.launch {
            deleteCourseUseCase(courseId)
            _uiState.update { it.copy(message = "课程已删除") }
        }
    }

    private fun observeCourses() {
        viewModelScope.launch {
            getCoursesForTimetableUseCase(timetableId).collect { aggregates ->
                val rows = aggregates.map { aggregate ->
                    CourseListItemUi(
                        id = aggregate.course.id,
                        name = aggregate.course.name,
                        teacher = aggregate.course.teacher ?: "",
                        color = aggregate.course.color,
                        sessionSummary = aggregate.sessions
                            .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
                            .joinToString(separator = "\n") { session ->
                                buildSessionSummary(session)
                            }
                    )
                }
                _uiState.value = CourseListUiState(
                    isLoading = false,
                    items = rows
                )
            }
        }
    }

    private fun buildSessionSummary(session: CourseSession): String {
        val day = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[session.dayOfWeek - 1]
        val weekText = when (session.weekType) {
            WeekType.ALL -> "All weeks"
            WeekType.RANGE -> "W${session.startWeek}-${session.endWeek}"
            WeekType.CUSTOM -> "W${session.customWeeks.joinToString(",")}"
        }
        val locationText = session.location?.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: ""
        return "$day P${session.startPeriod}-${session.endPeriod} $weekText$locationText"
    }

    companion object {
        fun factory(
            timetableId: Long,
            getCoursesForTimetableUseCase: GetCoursesForTimetableUseCase,
            deleteCourseUseCase: DeleteCourseUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return CourseListViewModel(
                        timetableId = timetableId,
                        getCoursesForTimetableUseCase = getCoursesForTimetableUseCase,
                        deleteCourseUseCase = deleteCourseUseCase
                    ) as T
                }
            }
    }
}

