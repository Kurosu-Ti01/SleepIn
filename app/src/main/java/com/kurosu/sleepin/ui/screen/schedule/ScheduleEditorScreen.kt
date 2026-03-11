package com.kurosu.sleepin.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Full-screen schedule editor UI.
 *
 * Responsibilities of this composable:
 * - render all editable controls for one schedule
 * - subscribe to state + one-off events from [ScheduleEditorViewModel]
 * - forward user actions back to the ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    onBackClick: () -> Unit,
    viewModel: ScheduleEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Listen for one-time success events and leave the screen when save succeeds.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is ScheduleEditorEvent.Saved) {
                onBackClick()
            }
        }
    }

    // Display user-facing validation or error messages.
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑作息表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // Keep loading state explicit so edited content never flashes stale values.
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

        // A lazy list keeps layout scalable even when period count becomes large.
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
                    label = { Text("作息表名称") },
                    singleLine = true
                )
            }

            item {
                Text("自动生成", style = MaterialTheme.typography.titleMedium)
            }

            // Quick generation inputs: first class start + durations + count.
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = uiState.quickStartTime,
                        onValueChange = viewModel::onQuickStartTimeChange,
                        label = { Text("第一节开始") },
                        placeholder = { Text("08:00") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = uiState.quickClassDurationMinutes,
                        onValueChange = viewModel::onQuickClassDurationChange,
                        label = { Text("课时(分钟)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = uiState.quickBreakMinutes,
                        onValueChange = viewModel::onQuickBreakChange,
                        label = { Text("课间(分钟)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = uiState.quickPeriodCount,
                        onValueChange = viewModel::onQuickPeriodCountChange,
                        label = { Text("总课节") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            item {
                OutlinedButton(onClick = viewModel::generatePeriods) {
                    Text("自动生成课节")
                }
            }

            item {
                Text("课节列表", style = MaterialTheme.typography.titleMedium)
            }

            // Period row editor section.
            itemsIndexed(uiState.periods) { index, period ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("第 ${index + 1} 行")
                        IconButton(onClick = { viewModel.removePeriod(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除课节")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = period.periodNumber,
                            onValueChange = { viewModel.onPeriodNumberChange(index, it) },
                            label = { Text("节次") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = period.startTime,
                            onValueChange = { viewModel.onPeriodStartTimeChange(index, it) },
                            label = { Text("开始") },
                            placeholder = { Text("08:00") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = period.endTime,
                            onValueChange = { viewModel.onPeriodEndTimeChange(index, it) },
                            label = { Text("结束") },
                            placeholder = { Text("08:45") },
                            singleLine = true
                        )
                    }
                }
            }

            item {
                OutlinedButton(onClick = viewModel::addPeriod) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加课节", modifier = Modifier.padding(start = 4.dp))
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving
                ) {
                    Text(if (uiState.isSaving) "保存中..." else "保存")
                }
            }
        }
    }
}

/**
 * Helper for constructing [ScheduleEditorViewModel] with manual dependencies.
 */
@Composable
fun rememberScheduleEditorViewModel(
    scheduleId: Long?,
    getScheduleDetailUseCase: com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase,
    saveScheduleUseCase: com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase
): ScheduleEditorViewModel = viewModel(
    factory = ScheduleEditorViewModel.factory(
        scheduleId = scheduleId,
        getScheduleDetailUseCase = getScheduleDetailUseCase,
        saveScheduleUseCase = saveScheduleUseCase
    )
)
