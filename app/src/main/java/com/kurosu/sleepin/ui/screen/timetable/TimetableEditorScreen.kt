package com.kurosu.sleepin.ui.screen.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Timetable editor screen used for both create and edit flows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableEditorScreen(
    onBackClick: () -> Unit,
    viewModel: TimetableEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var scheduleExpanded by remember { mutableStateOf(false) }

    // Close editor only when ViewModel confirms save succeeded.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is TimetableEditorEvent.Saved) {
                onBackClick()
            }
        }
    }

    // Show and consume validation/error messages emitted by ViewModel.
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "编辑课程表" else "新建课程表") },
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
                    label = { Text("课程表名称") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.totalWeeks,
                    onValueChange = viewModel::onTotalWeeksChange,
                    label = { Text("总周数 (1-30)") },
                    placeholder = { Text("18") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.startDateText,
                    onValueChange = viewModel::onStartDateChange,
                    label = { Text("开学日期") },
                    supportingText = { Text("Use ISO date format: yyyy-MM-dd") },
                    singleLine = true
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = scheduleExpanded,
                    onExpandedChange = { scheduleExpanded = !scheduleExpanded }
                ) {
                    val selectedName = uiState.scheduleOptions
                        .firstOrNull { it.id == uiState.selectedScheduleId }
                        ?.name
                        .orEmpty()

                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            .fillMaxWidth()
                            .clickable { scheduleExpanded = true },
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("关联作息表") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = scheduleExpanded)
                        }
                    )

                    DropdownMenu(
                        expanded = scheduleExpanded,
                        onDismissRequest = { scheduleExpanded = false }
                    ) {
                        uiState.scheduleOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    viewModel.onScheduleChange(option.id)
                                    scheduleExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.colorScheme,
                    onValueChange = viewModel::onColorSchemeChange,
                    label = { Text("配色方案标识 (可选)") },
                    placeholder = { Text("spring_light") },
                    singleLine = true
                )
            }

            item {
                Text(
                    text = "保存后可在课程表列表中设为当前激活课程表。",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving
                ) {
                    Text(if (uiState.isSaving) "保存中..." else "保存课程表")
                }
            }
        }
    }
}

/**
 * Helper for creating [TimetableEditorViewModel] with manually provided dependencies.
 */
@Composable
fun rememberTimetableEditorViewModel(
    timetableId: Long?,
    getSchedulesUseCase: com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase,
    getTimetableDetailUseCase: com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase,
    createTimetableUseCase: com.kurosu.sleepin.domain.usecase.timetable.CreateTimetableUseCase,
    updateTimetableUseCase: com.kurosu.sleepin.domain.usecase.timetable.UpdateTimetableUseCase
): TimetableEditorViewModel = viewModel(
    factory = TimetableEditorViewModel.factory(
        timetableId = timetableId,
        getSchedulesUseCase = getSchedulesUseCase,
        getTimetableDetailUseCase = getTimetableDetailUseCase,
        createTimetableUseCase = createTimetableUseCase,
        updateTimetableUseCase = updateTimetableUseCase
    )
)


