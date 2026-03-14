package com.kurosu.sleepin.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.usecase.course.AddCourseUseCase
import com.kurosu.sleepin.domain.usecase.course.CourseConflict
import com.kurosu.sleepin.domain.usecase.course.CourseSaveResult
import com.kurosu.sleepin.domain.usecase.course.CourseSessionDraft
import com.kurosu.sleepin.domain.usecase.course.GetCourseDetailUseCase
import com.kurosu.sleepin.domain.usecase.course.UpdateCourseUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI model for one editable session card in the course editor.
 */
data class EditableCourseSession(
    val id: Long = 0,
    val dayOfWeek: Int = 1,
    val startPeriod: Int = 1,
    val endPeriod: Int = 1,
    val location: String = "",
    val weekType: WeekType = WeekType.ALL,
    val startWeekText: String = "1",
    val endWeekText: String = "1",
    val customWeeks: Set<Int> = emptySet()
)

/**
 * Main screen state for adding/updating one course.
 */
data class CourseEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isEditMode: Boolean = false,
    val name: String = "",
    val teacher: String = "",
    val note: String = "",
    val color: Int = 0xFF4A90D9.toInt(),
    val totalWeeks: Int = 18,
    val maxPeriod: Int = 12,
    val sessions: List<EditableCourseSession> = listOf(EditableCourseSession()),
    val pendingConflicts: List<CourseConflict> = emptyList(),
    val message: String? = null
)

sealed interface CourseEditorEvent {
    data object Saved : CourseEditorEvent
}

/**
 * Coordinates loading prerequisites, editing form state, validation, and save actions.
 */
class CourseEditorViewModel(
    private val timetableId: Long,
    private val courseId: Long?,
    private val getTimetableDetailUseCase: GetTimetableDetailUseCase,
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase,
    private val getCourseDetailUseCase: GetCourseDetailUseCase,
    private val addCourseUseCase: AddCourseUseCase,
    private val updateCourseUseCase: UpdateCourseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseEditorUiState(isEditMode = courseId != null))
    val uiState: StateFlow<CourseEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CourseEditorEvent>()
    val events: SharedFlow<CourseEditorEvent> = _events.asSharedFlow()

    init {
        loadInitialData()
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissConflictDialog() {
        _uiState.update { it.copy(pendingConflicts = emptyList()) }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onTeacherChange(value: String) = _uiState.update { it.copy(teacher = value) }
    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }
    fun onColorChange(value: Int) = _uiState.update { it.copy(color = value) }

    fun addSession() {
        val state = _uiState.value
        val next = EditableCourseSession(
            dayOfWeek = 1,
            startPeriod = 1,
            endPeriod = minOf(2, state.maxPeriod),
            startWeekText = "1",
            endWeekText = state.totalWeeks.toString()
        )
        _uiState.update { it.copy(sessions = it.sessions + next) }
    }

    fun removeSession(index: Int) {
        _uiState.update { state ->
            val updated = state.sessions.filterIndexed { i, _ -> i != index }
            state.copy(sessions = if (updated.isEmpty()) listOf(EditableCourseSession()) else updated)
        }
    }

    fun onDayChange(index: Int, day: Int) = updateSession(index) { it.copy(dayOfWeek = day) }

    fun onPeriodRangeChange(index: Int, start: Int, end: Int) = updateSession(index) {
        it.copy(startPeriod = start, endPeriod = end)
    }

    fun onLocationChange(index: Int, value: String) = updateSession(index) { it.copy(location = value) }

    fun onWeekTypeChange(index: Int, weekType: WeekType) = updateSession(index) {
        when (weekType) {
            WeekType.ALL -> it.copy(weekType = WeekType.ALL, customWeeks = emptySet())
            WeekType.RANGE -> it.copy(weekType = WeekType.RANGE)
            WeekType.CUSTOM -> it.copy(weekType = WeekType.CUSTOM, customWeeks = it.customWeeks.ifEmpty { setOf(1) })
        }
    }

    fun onRangeWeekChange(index: Int, startText: String? = null, endText: String? = null) = updateSession(index) {
        it.copy(
            startWeekText = startText ?: it.startWeekText,
            endWeekText = endText ?: it.endWeekText
        )
    }

    fun onToggleCustomWeek(index: Int, week: Int) = updateSession(index) { session ->
        val next = session.customWeeks.toMutableSet()
        if (next.contains(week)) {
            next.remove(week)
        } else {
            next.add(week)
        }
        session.copy(customWeeks = next)
    }

    fun save() {
        saveInternal(allowConflictSave = false)
    }

    fun confirmForceSave() {
        saveInternal(allowConflictSave = true)
    }

    private fun saveInternal(allowConflictSave: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            val drafts = state.sessions.map { session ->
                CourseSessionDraft(
                    id = session.id,
                    dayOfWeek = session.dayOfWeek,
                    startPeriod = session.startPeriod,
                    endPeriod = session.endPeriod,
                    location = session.location,
                    weekType = session.weekType,
                    startWeek = session.startWeekText.toIntOrNull(),
                    endWeek = session.endWeekText.toIntOrNull(),
                    customWeeks = session.customWeeks.sorted()
                )
            }

            _uiState.update { it.copy(isSaving = true, pendingConflicts = emptyList()) }

            val result = if (courseId == null) {
                addCourseUseCase(
                    timetableId = timetableId,
                    totalWeeks = state.totalWeeks,
                    maxPeriod = state.maxPeriod,
                    name = state.name,
                    teacher = state.teacher,
                    color = state.color,
                    note = state.note,
                    sessions = drafts,
                    allowConflictSave = allowConflictSave
                )
            } else {
                updateCourseUseCase(
                    courseId = courseId,
                    timetableId = timetableId,
                    totalWeeks = state.totalWeeks,
                    maxPeriod = state.maxPeriod,
                    name = state.name,
                    teacher = state.teacher,
                    color = state.color,
                    note = state.note,
                    sessions = drafts,
                    allowConflictSave = allowConflictSave
                )
            }

            when (result) {
                is CourseSaveResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(CourseEditorEvent.Saved)
                }
                is CourseSaveResult.ValidationError -> {
                    _uiState.update { it.copy(isSaving = false, message = result.message) }
                }
                is CourseSaveResult.ConflictDetected -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            pendingConflicts = result.conflicts,
                            message = "检测到时间冲突，请确认是否仍要保存"
                        )
                    }
                }
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // We need timetable + schedule metadata first to constrain week and period pickers.
            val timetable = getTimetableDetailUseCase(timetableId)
            if (timetable == null) {
                _uiState.update { it.copy(isLoading = false, message = "未找到课程表") }
                return@launch
            }

            val scheduleDetail = getScheduleDetailUseCase(timetable.scheduleId)
            val maxPeriod = scheduleDetail?.periods?.maxOfOrNull { it.periodNumber } ?: 12

            _uiState.update {
                it.copy(
                    isLoading = false,
                    totalWeeks = timetable.totalWeeks,
                    maxPeriod = maxPeriod,
                    sessions = listOf(
                        EditableCourseSession(
                            dayOfWeek = 1,
                            startPeriod = 1,
                            endPeriod = minOf(2, maxPeriod),
                            startWeekText = "1",
                            endWeekText = timetable.totalWeeks.toString()
                        )
                    )
                )
            }

            if (courseId != null) {
                val detail = getCourseDetailUseCase(courseId)
                if (detail == null) {
                    _uiState.update { it.copy(message = "未找到课程") }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        name = detail.course.name,
                        teacher = detail.course.teacher.orEmpty(),
                        note = detail.course.note.orEmpty(),
                        color = detail.course.color,
                        sessions = detail.sessions.map { session ->
                            EditableCourseSession(
                                id = session.id,
                                dayOfWeek = session.dayOfWeek,
                                startPeriod = session.startPeriod,
                                endPeriod = session.endPeriod,
                                location = session.location.orEmpty(),
                                weekType = session.weekType,
                                startWeekText = (session.startWeek ?: 1).toString(),
                                endWeekText = (session.endWeek ?: timetable.totalWeeks).toString(),
                                customWeeks = session.customWeeks.toSet()
                            )
                        }.ifEmpty {
                            listOf(
                                EditableCourseSession(
                                    dayOfWeek = 1,
                                    startPeriod = 1,
                                    endPeriod = minOf(2, maxPeriod),
                                    startWeekText = "1",
                                    endWeekText = timetable.totalWeeks.toString()
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    private fun updateSession(
        index: Int,
        transform: (EditableCourseSession) -> EditableCourseSession
    ) {
        _uiState.update { state ->
            val updated = state.sessions.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(sessions = updated)
        }
    }

    companion object {
        fun factory(
            timetableId: Long,
            courseId: Long?,
            getTimetableDetailUseCase: GetTimetableDetailUseCase,
            getScheduleDetailUseCase: GetScheduleDetailUseCase,
            getCourseDetailUseCase: GetCourseDetailUseCase,
            addCourseUseCase: AddCourseUseCase,
            updateCourseUseCase: UpdateCourseUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return CourseEditorViewModel(
                        timetableId = timetableId,
                        courseId = courseId,
                        getTimetableDetailUseCase = getTimetableDetailUseCase,
                        getScheduleDetailUseCase = getScheduleDetailUseCase,
                        getCourseDetailUseCase = getCourseDetailUseCase,
                        addCourseUseCase = addCourseUseCase,
                        updateCourseUseCase = updateCourseUseCase
                    ) as T
                }
            }
    }
}

