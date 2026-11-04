package com.kurosu.sleepin.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurosu.sleepin.domain.model.formatPeriodNumbers
import com.kurosu.sleepin.ui.menu.HomeMenuSheet
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Home screen in Phase 5 now hosts the full week-view framework:
 * - top app bar with week switch actions,
 * - week grid + timeline,
 * - course detail bottom sheet,
 * - existing menu bottom sheet for quick switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTimetableListClick: () -> Unit,
    onScheduleListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditScheduleClick: (Long) -> Unit,
    courseRowHeightDp: Int,
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenuSheet by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<HomeCourseCellUi?>(null) }
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sharedVerticalScrollState = rememberScrollState()

    val pageCount = uiState.totalWeeks.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = (uiState.selectedWeek - 1).coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )

    // Sync ViewModel week state when the user finishes swiping the HorizontalPager.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                val targetWeek = settledPage + 1
                if (targetWeek != uiState.selectedWeek) {
                    viewModel.onWeekChanged(targetWeek)
                }
            }
    }

    // Smoothly animate the HorizontalPager when selectedWeek is updated via top bar arrows or menu sheet.
    LaunchedEffect(uiState.selectedWeek, uiState.activeTimetableId, pageCount) {
        val targetPage = (uiState.selectedWeek - 1).coerceIn(0, pageCount - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Top line is always today's real date in bold, independent from selected week.
                        Text(
                            text = uiState.todayDateText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // Bottom line shows selected week with smooth vertical slide transition, plus a quick-return badge when browsing another week.
                        // Fixed 20.dp height and Box-based badge avoid M3 Surface's 48.dp minimum touch target, preventing the date above from jumping.
                        Row(
                            modifier = Modifier.height(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnimatedContent(
                                targetState = uiState.selectedWeek,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith
                                            (slideOutVertically { height -> -height / 2 } + fadeOut())
                                    } else {
                                        (slideInVertically { height -> -height / 2 } + fadeIn()) togetherWith
                                            (slideOutVertically { height -> height / 2 } + fadeOut())
                                    }
                                },
                                label = "weekSubtitleTransition"
                            ) { weekNumber ->
                                Text(
                                    text = "第 $weekNumber 周",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val currentSemesterWeek = uiState.currentWeek
                            if (currentSemesterWeek != null && currentSemesterWeek != uiState.selectedWeek) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable { viewModel.onWeekChanged(currentSemesterWeek) }
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "回到第 $currentSemesterWeek 周",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onWeekChanged(uiState.selectedWeek - 1) },
                        enabled = uiState.selectedWeek > 1
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一周"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onWeekChanged(uiState.selectedWeek + 1) },
                        enabled = uiState.selectedWeek < uiState.totalWeeks
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一周"
                        )
                    }
                    IconButton(onClick = { showMenuSheet = true }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "打开菜单")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopStart
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "加载中...", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                !uiState.hasActiveTimetable -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "尚未选择课程表", style = MaterialTheme.typography.titleMedium)
                            Text(text = "请在右上角菜单中选择或创建课程表", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        uiState.semesterStatusMessage?.let { message ->
                            Text(
                                text = message,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Imported classes may reference periods the schedule does not define yet. They are
                        // stored and drawn, but without a class time — so name the missing periods and let
                        // the user jump straight to the schedule editor that can fix it.
                        val activeScheduleId = uiState.activeScheduleId
                        if (uiState.unconfiguredPeriods.isNotEmpty() && activeScheduleId != null) {
                            // onClick rather than a clickable modifier so the banner gets Material's minimum
                            // touch target and button semantics.
                            Surface(
                                onClick = { onEditScheduleClick(activeScheduleId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "第 ${formatPeriodNumbers(uiState.unconfiguredPeriods)} 节未在作息表中定义，" +
                                        "这些节次没有上课时间。点此补全作息表，补全后自动消失",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        if (uiState.periods.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "关联作息表没有课节配置，请先编辑作息表",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                beyondViewportPageCount = 1,
                                key = { page -> page + 1 }
                            ) { pageIndex ->
                                val weekNumber = pageIndex + 1
                                val pageUi = uiState.weekPages[weekNumber]
                                WeekGridView(
                                    dates = pageUi?.weekDates ?: uiState.weekDates,
                                    periods = uiState.periods,
                                    courses = pageUi?.courses ?: uiState.courses,
                                    todayColumnIndex = pageUi?.todayColumnIndex ?: uiState.todayColumnIndex,
                                    currentTimePeriodOffset = pageUi?.currentTimePeriodOffset
                                        ?: uiState.currentTimePeriodOffset,
                                    onCourseClick = { clicked -> selectedCourse = clicked },
                                    verticalScrollState = sharedVerticalScrollState,
                                    rowHeight = courseRowHeightDp.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            sheetState = menuSheetState,
            onDismissRequest = { showMenuSheet = false }
        ) {
            HomeMenuSheet(
                selectedTimetable = uiState.selectedTimetable,
                selectedSchedule = uiState.selectedSchedule,
                timetableOptions = uiState.timetables,
                selectedWeek = uiState.selectedWeek,
                totalWeeks = uiState.totalWeeks,
                onTimetableSelected = viewModel::onTimetableSelected,
                onWeekChange = viewModel::onWeekChanged,
                onTimetableListClick = {
                    showMenuSheet = false
                    onTimetableListClick()
                },
                onScheduleListClick = {
                    showMenuSheet = false
                    onScheduleListClick()
                },
                onSettingsClick = {
                    showMenuSheet = false
                    onSettingsClick()
                }
            )
        }
    }

    if (selectedCourse != null) {
        ModalBottomSheet(
            sheetState = detailSheetState,
            onDismissRequest = { selectedCourse = null }
        ) {
            CourseDetailSheet(course = selectedCourse!!)
        }
    }
}

