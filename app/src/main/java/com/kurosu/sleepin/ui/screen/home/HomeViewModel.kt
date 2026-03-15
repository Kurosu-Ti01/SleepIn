package com.kurosu.sleepin.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.usecase.course.GetCoursesForTimetableUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetActiveTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetablesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.SetActiveTimetableUseCase
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Keeps Home screen state synchronized with active timetable context.
 */
class HomeViewModel(
    private val getTimetablesUseCase: GetTimetablesUseCase,
    private val getSchedulesUseCase: GetSchedulesUseCase,
    private val getActiveTimetableUseCase: GetActiveTimetableUseCase,
    private val getCoursesForTimetableUseCase: GetCoursesForTimetableUseCase,
    private val getScheduleDetailUseCase: GetScheduleDetailUseCase,
    private val setActiveTimetableUseCase: SetActiveTimetableUseCase
) : ViewModel() {

    private val topBarDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * A nullable override. Null means "follow auto-calculated current week".
     */
    private val manualSelectedWeek = MutableStateFlow<Int?>(null)

    /**
     * Time ticker used by current-time indicator and today highlighting logic.
     */
    private val now = MutableStateFlow(LocalDateTime.now())

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            selectedWeek = 1,
            totalWeeks = 18
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        startClockTicker()
        resetWeekOverrideWhenTimetableChanges()
        observeHomeState()
    }

    fun onWeekChanged(week: Int) {
        val safeWeek = week.coerceIn(1, _uiState.value.totalWeeks)
        manualSelectedWeek.value = safeWeek
        _uiState.update { state -> state.copy(selectedWeek = safeWeek) }
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
     * Updates the local clock at minute-level precision for timeline overlays.
     */
    private fun startClockTicker() {
        viewModelScope.launch {
            while (true) {
                now.value = LocalDateTime.now()
                delay(60_000)
            }
        }
    }

    /**
     * Whenever user switches to another timetable, the week selector should snap back to auto mode.
     */
    private fun resetWeekOverrideWhenTimetableChanges() {
        viewModelScope.launch {
            getActiveTimetableUseCase()
                .map { it?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    manualSelectedWeek.value = null
                }
        }
    }

    /**
     * Builds a single source of truth for Home screen rendering.
     *
     * The state is composed from:
     * - all timetables/schedules for menu labels,
     * - active timetable's courses and schedule periods for week grid,
     * - real-time clock for today highlighting and current-time line.
     */
    private fun observeHomeState() {
        viewModelScope.launch {
            combine(
                getTimetablesUseCase(),
                getSchedulesUseCase(),
                activeTimetablePayloadFlow(),
                manualSelectedWeek,
                now
            ) { timetables, schedules, payload, manualWeek, currentDateTime ->
                val active = payload.activeTimetable
                val totalWeeks = active?.totalWeeks ?: 18
                val semesterInfo = active?.let {
                    calculateSemesterWeekInfo(
                        startDate = it.startDate,
                        totalWeeks = totalWeeks,
                        today = currentDateTime.toLocalDate()
                    )
                }
                val previousState = _uiState.value
                val autoWeek = semesterInfo?.currentWeek ?: 1
                val selectedWeek = when {
                    active == null -> 1
                    previousState.activeTimetableId != active.id -> autoWeek
                    manualWeek != null -> manualWeek
                    else -> previousState.selectedWeek
                }.coerceIn(1, totalWeeks)

                val weekDates = active?.let { buildWeekDates(it.startDate, selectedWeek) } ?: emptyList()
                val visibleCourses = if (active == null) {
                    emptyList()
                } else {
                    buildVisibleCourseCells(payload.courses, selectedWeek)
                }

                val isShowingCurrentWeek = semesterInfo?.progress == SemesterProgress.IN_PROGRESS &&
                    semesterInfo.currentWeek == selectedWeek
                val todayColumnIndex = if (isShowingCurrentWeek) {
                    currentDateTime.dayOfWeek.value - 1
                } else {
                    null
                }
                val currentTimeOffset = if (todayColumnIndex != null) {
                    calculateCurrentTimePeriodOffset(payload.periods, currentDateTime.toLocalTime())
                } else {
                    null
                }

                val scheduleNameMap = schedules.associate { it.id to it.name }
                HomeUiState(
                    isLoading = false,
                    hasActiveTimetable = active != null,
                    activeTimetableId = active?.id,
                    selectedWeek = selectedWeek,
                    currentWeek = semesterInfo?.currentWeek,
                    totalWeeks = totalWeeks,
                    selectedTimetable = active?.name ?: "未选择课程表",
                    selectedSchedule = active?.let { scheduleNameMap[it.scheduleId] ?: "未知作息表" } ?: "未选择作息表",
                    timetables = timetables.map { HomeTimetableOption(id = it.id, name = it.name, isActive = it.isActive) },
                    weekDates = weekDates,
                    periods = payload.periods.map { HomePeriodUi(it.periodNumber, it.startTime, it.endTime) },
                    courses = visibleCourses,
                    todayColumnIndex = todayColumnIndex,
                    currentTimePeriodOffset = currentTimeOffset,
                    semesterStatusMessage = when (semesterInfo?.progress) {
                        SemesterProgress.BEFORE_START -> "学期未开始"
                        SemesterProgress.AFTER_END -> "学期已结束"
                        else -> null
                    },
                    // The top bar always shows the real-world "today" date,
                    // regardless of which week the user is currently browsing.
                    todayDateText = currentDateTime.toLocalDate().format(topBarDateFormatter)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Loads the schedule periods and courses for the currently active timetable.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun activeTimetablePayloadFlow(): Flow<ActiveTimetablePayload> {
        return getActiveTimetableUseCase().flatMapLatest { activeTimetable ->
            if (activeTimetable == null) {
                return@flatMapLatest flowOf(ActiveTimetablePayload())
            }

            combine(
                flow {
                    val scheduleDetail = getScheduleDetailUseCase(activeTimetable.scheduleId)
                    emit(scheduleDetail?.periods.orEmpty().sortedBy { it.periodNumber })
                },
                getCoursesForTimetableUseCase(activeTimetable.id)
            ) { periods, courses ->
                ActiveTimetablePayload(
                    activeTimetable = activeTimetable,
                    periods = periods,
                    courses = courses
                )
            }
        }
    }

    /**
     * Applies week recurrence rules so only sessions visible in [week] are rendered.
     */
    private fun buildVisibleCourseCells(
        source: List<CourseWithSessions>,
        week: Int
    ): List<HomeCourseCellUi> {
        return source.flatMapIndexed { aggregateIndex, aggregate ->
            aggregate.sessions
                .filter { session -> isSessionVisibleInWeek(session, week) }
                .mapIndexed { sessionIndex, session ->
                    HomeCourseCellUi(
                        uniqueId = "${aggregate.course.id}_${session.id}_${aggregateIndex}_$sessionIndex",
                        courseId = aggregate.course.id,
                        sessionId = session.id,
                        name = aggregate.course.name,
                        teacher = aggregate.course.teacher,
                        note = aggregate.course.note,
                        color = aggregate.course.color,
                        dayOfWeek = session.dayOfWeek,
                        startPeriod = session.startPeriod,
                        endPeriod = session.endPeriod,
                        location = session.location,
                        weekDescription = buildWeekDescription(session)
                    )
                }
        }.sortedWith(
            compareBy<HomeCourseCellUi> { it.dayOfWeek }
                .thenBy { it.startPeriod }
                .thenBy { it.endPeriod }
                .thenBy { it.name }
        )
    }

    private fun isSessionVisibleInWeek(session: CourseSession, week: Int): Boolean {
        return when (session.weekType) {
            WeekType.ALL -> true
            WeekType.RANGE -> {
                val start = session.startWeek ?: 1
                val end = session.endWeek ?: Int.MAX_VALUE
                week in start..end
            }
            WeekType.CUSTOM -> week in session.customWeeks
        }
    }

    private fun buildWeekDescription(session: CourseSession): String {
        return when (session.weekType) {
            WeekType.ALL -> "All weeks"
            WeekType.RANGE -> {
                val start = session.startWeek ?: 1
                val end = session.endWeek ?: start
                "Week $start-$end"
            }
            WeekType.CUSTOM -> "Week ${session.customWeeks.joinToString(",")}"
        }
    }

    /**
     * Converts the current time to a floating row index where:
     * - 0.0 means at the top of period 1,
     * - 1.5 means middle of period 2,
     * - N means exactly at the boundary above period N+1.
     */
    private fun calculateCurrentTimePeriodOffset(periods: List<SchedulePeriod>, nowTime: LocalTime): Float? {
        if (periods.isEmpty()) return null

        val sorted = periods.sortedBy { it.periodNumber }
        val firstStart = sorted.first().startTime
        val lastEnd = sorted.last().endTime

        if (nowTime.isBefore(firstStart) || nowTime.isAfter(lastEnd)) {
            return null
        }

        sorted.forEachIndexed { index, period ->
            val isInsidePeriod = !nowTime.isBefore(period.startTime) && nowTime.isBefore(period.endTime)
            if (isInsidePeriod) {
                val totalSeconds = Duration.between(period.startTime, period.endTime).seconds.coerceAtLeast(1)
                val elapsedSeconds = Duration.between(period.startTime, nowTime).seconds.coerceAtLeast(0)
                return index + (elapsedSeconds.toFloat() / totalSeconds.toFloat())
            }

            val nextPeriod = sorted.getOrNull(index + 1)
            if (nextPeriod != null && !nowTime.isBefore(period.endTime) && nowTime.isBefore(nextPeriod.startTime)) {
                return (index + 1).toFloat()
            }
        }

        return if (nowTime == lastEnd) sorted.size.toFloat() else null
    }

    companion object {
        /** Manual factory keeps construction pattern consistent with other modules. */
        fun factory(
            getTimetablesUseCase: GetTimetablesUseCase,
            getSchedulesUseCase: GetSchedulesUseCase,
            getActiveTimetableUseCase: GetActiveTimetableUseCase,
            getCoursesForTimetableUseCase: GetCoursesForTimetableUseCase,
            getScheduleDetailUseCase: GetScheduleDetailUseCase,
            setActiveTimetableUseCase: SetActiveTimetableUseCase
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(
                        getTimetablesUseCase = getTimetablesUseCase,
                        getSchedulesUseCase = getSchedulesUseCase,
                        getActiveTimetableUseCase = getActiveTimetableUseCase,
                        getCoursesForTimetableUseCase = getCoursesForTimetableUseCase,
                        getScheduleDetailUseCase = getScheduleDetailUseCase,
                        setActiveTimetableUseCase = setActiveTimetableUseCase
                    ) as T
                }
            }
    }
}

private data class ActiveTimetablePayload(
    val activeTimetable: Timetable? = null,
    val periods: List<SchedulePeriod> = emptyList(),
    val courses: List<CourseWithSessions> = emptyList()
)

/**
 * Timetable option rendered inside the Home quick-switch panel.
 */
data class HomeTimetableOption(
    val id: Long,
    val name: String,
    val isActive: Boolean
)

/**
 * UI model for one period row in the week grid.
 */
data class HomePeriodUi(
    val periodNumber: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/**
 * UI model for one rendered course block in the week grid.
 */
data class HomeCourseCellUi(
    val uniqueId: String,
    val courseId: Long,
    val sessionId: Long,
    val name: String,
    val teacher: String?,
    val note: String?,
    val color: Int,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val location: String?,
    val weekDescription: String
)

/**
 * Home screen state consumed by top app bar and menu sheet.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val hasActiveTimetable: Boolean = false,
    val activeTimetableId: Long? = null,
    val selectedWeek: Int,
    val currentWeek: Int? = null,
    val totalWeeks: Int,
    val selectedTimetable: String = "未选择课程表",
    val selectedSchedule: String = "未选择作息表",
    val timetables: List<HomeTimetableOption> = emptyList(),
    val weekDates: List<LocalDate> = emptyList(),
    val periods: List<HomePeriodUi> = emptyList(),
    val courses: List<HomeCourseCellUi> = emptyList(),
    val todayColumnIndex: Int? = null,
    val currentTimePeriodOffset: Float? = null,
    val semesterStatusMessage: String? = null,
    val todayDateText: String = ""
)

