package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurosu.sleepin.ui.menu.HomeMenuSheet

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
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenuSheet by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<HomeCourseCellUi?>(null) }
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("第 ${uiState.selectedWeek} 周", style = MaterialTheme.typography.titleMedium)
                        Text(uiState.topBarDateText, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onWeekChanged(uiState.selectedWeek - 1) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一周"
                        )
                    }
                    IconButton(onClick = { viewModel.onWeekChanged(uiState.selectedWeek + 1) }) {
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

                        if (uiState.periods.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "关联作息表没有课节配置，请先编辑作息表",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            WeekGridView(
                                dates = uiState.weekDates,
                                periods = uiState.periods,
                                courses = uiState.courses,
                                todayColumnIndex = uiState.todayColumnIndex,
                                currentTimePeriodOffset = uiState.currentTimePeriodOffset,
                                onCourseClick = { clicked -> selectedCourse = clicked },
                                onSwipeToPreviousWeek = {
                                    viewModel.onWeekChanged(uiState.selectedWeek - 1)
                                },
                                onSwipeToNextWeek = {
                                    viewModel.onWeekChanged(uiState.selectedWeek + 1)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
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

