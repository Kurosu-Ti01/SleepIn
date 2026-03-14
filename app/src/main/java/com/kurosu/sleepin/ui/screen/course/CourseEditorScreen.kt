package com.kurosu.sleepin.ui.screen.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurosu.sleepin.domain.model.WeekType
import com.kurosu.sleepin.ui.component.ColorPicker
import com.kurosu.sleepin.ui.component.DayOfWeekSelector
import com.kurosu.sleepin.ui.component.PeriodRangePicker
import com.kurosu.sleepin.ui.component.WeekPicker

/**
 * Course editor supports both create and edit flows with multi-session configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorScreen(
    onBackClick: () -> Unit,
    viewModel: CourseEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CourseEditorEvent.Saved) {
                onBackClick()
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "编辑课程" else "新建课程") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !uiState.isSaving) {
                        Text(if (uiState.isSaving) "保存中..." else "保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 16.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("课程名称") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.teacher,
                    onValueChange = viewModel::onTeacherChange,
                    label = { Text("教师 (可选)") },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text("备注 (可选)") }
                )
            }
            item {
                Text("课程颜色", style = MaterialTheme.typography.titleMedium)
                ColorPicker(
                    selectedColor = uiState.color,
                    onColorSelected = viewModel::onColorChange
                )
            }

            item {
                Text("上课时间段", style = MaterialTheme.typography.titleMedium)
            }

            itemsIndexed(uiState.sessions) { index, session ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("时间段 ${index + 1}", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { viewModel.removeSession(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除时间段")
                        }
                    }

                    DayOfWeekSelector(
                        selectedDay = session.dayOfWeek,
                        onDaySelected = { viewModel.onDayChange(index, it) }
                    )

                    PeriodRangePicker(
                        startPeriod = session.startPeriod,
                        endPeriod = session.endPeriod,
                        maxPeriod = uiState.maxPeriod,
                        onRangeChange = { start, end -> viewModel.onPeriodRangeChange(index, start, end) }
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = session.location,
                        onValueChange = { viewModel.onLocationChange(index, it) },
                        label = { Text("地点 (可选)") },
                        singleLine = true
                    )

                    Text("周数配置", style = MaterialTheme.typography.titleSmall)
                    WeekType.entries.forEach { weekType ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(
                                selected = session.weekType == weekType,
                                onClick = { viewModel.onWeekTypeChange(index, weekType) }
                            )
                            Text(
                                text = when (weekType) {
                                    WeekType.ALL -> "所有周"
                                    WeekType.RANGE -> "连续范围"
                                    WeekType.CUSTOM -> "自定义"
                                }
                            )
                        }
                    }

                    when (session.weekType) {
                        WeekType.ALL -> {
                            Text("该时间段在整个学期每周生效", style = MaterialTheme.typography.bodySmall)
                        }
                        WeekType.RANGE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = session.startWeekText,
                                    onValueChange = { viewModel.onRangeWeekChange(index, startText = it) },
                                    label = { Text("起始周") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    modifier = Modifier.weight(1f),
                                    value = session.endWeekText,
                                    onValueChange = { viewModel.onRangeWeekChange(index, endText = it) },
                                    label = { Text("结束周") },
                                    singleLine = true
                                )
                            }
                        }
                        WeekType.CUSTOM -> {
                            WeekPicker(
                                totalWeeks = uiState.totalWeeks,
                                selectedWeeks = session.customWeeks,
                                onToggleWeek = { week -> viewModel.onToggleCustomWeek(index, week) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = viewModel::addSession) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加时间段", modifier = Modifier.padding(start = 4.dp))
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving
                ) {
                    Text(if (uiState.isSaving) "保存中..." else "保存课程")
                }
            }
        }
    }

    if (uiState.pendingConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConflictDialog,
            title = { Text("检测到课程冲突") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("以下冲突已检测到，是否仍要保存？")
                    uiState.pendingConflicts.take(4).forEach { conflict ->
                        Text(
                            text = "${conflict.existingCourseName}: Day ${conflict.dayOfWeek}, P${conflict.overlapStartPeriod}-${conflict.overlapEndPeriod}, W${conflict.overlapWeeks.joinToString(",")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (uiState.pendingConflicts.size > 4) {
                        Text("...and ${uiState.pendingConflicts.size - 4} more")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmForceSave) {
                    Text("强制保存")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConflictDialog) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * Helper for constructing [CourseEditorViewModel] with the app's manual DI setup.
 */
@Composable
fun rememberCourseEditorViewModel(
    timetableId: Long,
    courseId: Long?,
    getTimetableDetailUseCase: com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase,
    getScheduleDetailUseCase: com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase,
    getCourseDetailUseCase: com.kurosu.sleepin.domain.usecase.course.GetCourseDetailUseCase,
    addCourseUseCase: com.kurosu.sleepin.domain.usecase.course.AddCourseUseCase,
    updateCourseUseCase: com.kurosu.sleepin.domain.usecase.course.UpdateCourseUseCase
): CourseEditorViewModel = viewModel(
    factory = CourseEditorViewModel.factory(
        timetableId = timetableId,
        courseId = courseId,
        getTimetableDetailUseCase = getTimetableDetailUseCase,
        getScheduleDetailUseCase = getScheduleDetailUseCase,
        getCourseDetailUseCase = getCourseDetailUseCase,
        addCourseUseCase = addCourseUseCase,
        updateCourseUseCase = updateCourseUseCase
    )
)

