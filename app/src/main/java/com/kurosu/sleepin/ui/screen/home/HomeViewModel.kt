package com.kurosu.sleepin.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.Timetable
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.domain.model.findUndefinedPeriods
import com.kurosu.sleepin.domain.usecase.settings.ObserveSettingsUseCase
import com.kurosu.sleepin.domain.usecase.course.GetCoursesForTimetableUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetActiveTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetablesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.SetActiveTimetableUseCase
import java.time.LocalDate
import java.time.LocalDateTime
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
    private val setActiveTimetableUseCase: SetActiveTimetableUseCase,
    private val observeSettingsUseCase: ObserveSettingsUseCase
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
            val timetablesAndSchedulesFlow = combine(
                getTimetablesUseCase(),
                getSchedulesUseCase()
            ) { timetables: List<Timetable>, schedules: List<Schedule> ->
                timetables to schedules
            }
            val payloadAndManualWeekFlow = combine(
                activeTimetablePayloadFlow(),
                manualSelectedWeek
            ) { payload: ActiveTimetablePayload, manualWeek: Int? ->
                payload to manualWeek
            }

            combine(
                combine(timetablesAndSchedulesFlow, payloadAndManualWeekFlow) { timetableAndSchedule, payloadAndWeek ->
                    HomeStateInputs(
                        timetables = timetableAndSchedule.first,
                        schedules = timetableAndSchedule.second,
                        payload = payloadAndWeek.first,
                        manualWeek = payloadAndWeek.second
                    )
                },
                now
            ) { inputs, currentDateTime ->
                inputs to currentDateTime
            }.combine(observeSettingsUseCase()) { inputWithTime, settings ->
                val inputs = inputWithTime.first
                val currentDateTime = inputWithTime.second
                val timetables = inputs.timetables
                val schedules = inputs.schedules
                val payload = inputs.payload
                val manualWeek = inputs.manualWeek
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
                    buildVisibleCourseCells(
                        source = payload.courses,
                        week = selectedWeek,
                        showNonCurrentWeekCourses = settings.showNonCurrentWeekCourses
                    )
                }

                val periodRows = buildHomePeriodRows(
                    schedulePeriods = payload.periods,
                    courses = payload.courses
                )
                // The courses and the schedule can disagree: a portal may report a period the schedule
                // does not define yet. Reporting the gap here is what turns a silently empty row into
                // an instruction the user can act on.
                val unconfiguredPeriods = findUndefinedPeriods(
                    usedPeriodRanges = payload.courses
                        .flatMap { aggregate -> aggregate.sessions }
                        .map { session -> session.startPeriod..session.endPeriod },
                    definedPeriods = payload.periods
                        .filter { period -> period.periodNumber > 0 }
                        .map { period -> period.periodNumber }
                        .toSet()
                )

                val isShowingCurrentWeek = semesterInfo?.progress == SemesterProgress.IN_PROGRESS &&
                    semesterInfo.currentWeek == selectedWeek
                val todayColumnIndex = if (isShowingCurrentWeek) {
                    currentDateTime.dayOfWeek.value - 1
                } else {
                    null
                }
                val currentTimeOffset = if (todayColumnIndex != null) {
                    resolveCurrentTimeRowOffset(periodRows, currentDateTime.toLocalTime())
                } else {
                    null
                }

                val weekPages = if (active == null) {
                    emptyMap()
                } else {
                    (1..totalWeeks).associateWith { weekNumber ->
                        val isThisCurrentWeek = semesterInfo?.progress == SemesterProgress.IN_PROGRESS &&
                            semesterInfo.currentWeek == weekNumber
                        val pageTodayColumnIndex = if (isThisCurrentWeek) {
                            currentDateTime.dayOfWeek.value - 1
                        } else {
                            null
                        }
                        HomeWeekPageUi(
                            weekNumber = weekNumber,
                            weekDates = buildWeekDates(active.startDate, weekNumber),
                            courses = buildVisibleCourseCells(
                                source = payload.courses,
                                week = weekNumber,
                                showNonCurrentWeekCourses = settings.showNonCurrentWeekCourses
                            ),
                            todayColumnIndex = pageTodayColumnIndex,
                            currentTimePeriodOffset = if (pageTodayColumnIndex != null) {
                                resolveCurrentTimeRowOffset(periodRows, currentDateTime.toLocalTime())
                            } else {
                                null
                            }
                        )
                    }
                }

                val scheduleNameMap = schedules.associate { it.id to it.name }
                HomeUiState(
                    isLoading = false,
                    hasActiveTimetable = active != null,
                    activeTimetableId = active?.id,
                    activeScheduleId = active?.scheduleId,
                    selectedWeek = selectedWeek,
                    currentWeek = semesterInfo?.currentWeek,
                    totalWeeks = totalWeeks,
                    selectedTimetable = active?.name ?: "未选择课程表",
                    selectedSchedule = active?.let { scheduleNameMap[it.scheduleId] ?: "未知作息表" } ?: "未选择作息表",
                    timetables = timetables.map { HomeTimetableOption(id = it.id, name = it.name, isActive = it.isActive) },
                    weekDates = weekDates,
                    periods = periodRows,
                    unconfiguredPeriods = unconfiguredPeriods,
                    courses = visibleCourses,
                    todayColumnIndex = todayColumnIndex,
                    currentTimePeriodOffset = currentTimeOffset,
                    weekPages = weekPages,
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
        week: Int,
        showNonCurrentWeekCourses: Boolean
    ): List<HomeCourseCellUi> {
        val allCells = source.flatMapIndexed { aggregateIndex, aggregate ->
            aggregate.sessions
                .mapIndexed { sessionIndex, session ->
                    val isCurrentWeek = isSessionVisibleInWeek(session, week)
                    HomeCourseCellUi(
                        uniqueId = "${aggregate.course.id}_${session.id}_${aggregateIndex}_${sessionIndex}_${if (isCurrentWeek) "current" else "other"}",
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
                        weekDescription = buildWeekDescription(session),
                        isCurrentWeek = isCurrentWeek
                    )
                }
        }

        val currentWeekCells = allCells.filter { it.isCurrentWeek }
        if (!showNonCurrentWeekCourses) {
            return sortCourseCells(currentWeekCells)
        }

        val nonCurrentWeekCells = allCells
            .filterNot { it.isCurrentWeek }
            .filter { candidate ->
                currentWeekCells.none { current ->
                    current.dayOfWeek == candidate.dayOfWeek && periodsOverlap(current, candidate)
                }
            }

        return sortCourseCells(currentWeekCells + nonCurrentWeekCells)
    }

    private fun sortCourseCells(cells: List<HomeCourseCellUi>): List<HomeCourseCellUi> {
        return cells.sortedWith(
            compareBy<HomeCourseCellUi> { it.dayOfWeek }
                .thenBy { it.startPeriod }
                .thenBy { it.endPeriod }
                .thenByDescending { it.isCurrentWeek }
                .thenBy { it.name }
        )
    }

    private fun periodsOverlap(a: HomeCourseCellUi, b: HomeCourseCellUi): Boolean {
        return a.startPeriod <= b.endPeriod && b.startPeriod <= a.endPeriod
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

    companion object {
        /** Manual factory keeps construction pattern consistent with other modules. */
        fun factory(
            getTimetablesUseCase: GetTimetablesUseCase,
            getSchedulesUseCase: GetSchedulesUseCase,
            getActiveTimetableUseCase: GetActiveTimetableUseCase,
            getCoursesForTimetableUseCase: GetCoursesForTimetableUseCase,
            getScheduleDetailUseCase: GetScheduleDetailUseCase,
            setActiveTimetableUseCase: SetActiveTimetableUseCase,
            observeSettingsUseCase: ObserveSettingsUseCase
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
                        setActiveTimetableUseCase = setActiveTimetableUseCase,
                        observeSettingsUseCase = observeSettingsUseCase
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

private data class HomeStateInputs(
    val timetables: List<Timetable>,
    val schedules: List<Schedule>,
    val payload: ActiveTimetablePayload,
    val manualWeek: Int?
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
    val weekDescription: String,
    val isCurrentWeek: Boolean
)

/**
 * Precomputed snapshot for a single semester week inside the horizontal pager.
 */
data class HomeWeekPageUi(
    val weekNumber: Int,
    val weekDates: List<LocalDate>,
    val courses: List<HomeCourseCellUi>,
    val todayColumnIndex: Int?,
    val currentTimePeriodOffset: Float?
)

/**
 * Home screen state consumed by top app bar and menu sheet.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val hasActiveTimetable: Boolean = false,
    val activeTimetableId: Long? = null,
    /**
     * Schedule id of the active timetable, used to deep-link the user into the schedule editor when
     * the courses reference periods the schedule does not define.
     */
    val activeScheduleId: Long? = null,
    val selectedWeek: Int,
    val currentWeek: Int? = null,
    val totalWeeks: Int,
    val selectedTimetable: String = "未选择课程表",
    val selectedSchedule: String = "未选择作息表",
    val timetables: List<HomeTimetableOption> = emptyList(),
    val weekDates: List<LocalDate> = emptyList(),
    val periods: List<HomePeriodUi> = emptyList(),
    /**
     * Period numbers referenced by the timetable's courses that its schedule does not define yet.
     *
     * Non-empty means some sessions have no class time. The sessions are still stored and rendered, so
     * this is a prompt to complete the schedule rather than an error state.
     */
    val unconfiguredPeriods: List<Int> = emptyList(),
    val courses: List<HomeCourseCellUi> = emptyList(),
    val todayColumnIndex: Int? = null,
    val currentTimePeriodOffset: Float? = null,
    val weekPages: Map<Int, HomeWeekPageUi> = emptyMap(),
    val semesterStatusMessage: String? = null,
    val todayDateText: String = ""
)

