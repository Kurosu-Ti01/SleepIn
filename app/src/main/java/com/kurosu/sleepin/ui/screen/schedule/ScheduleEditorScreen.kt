package com.kurosu.sleepin.ui.screen.schedule

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val text = uri?.let { context.readTextFromUri(it) }
        if (!text.isNullOrBlank()) {
            viewModel.importCsvForCreate(text)
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val pending = pendingExportText
        if (uri != null && pending != null) {
            context.writeTextToUri(uri, pending)
        }
        pendingExportText = null
    }

    // Listen for one-time success events and leave the screen when save succeeds.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ScheduleEditorEvent.Saved -> onBackClick()
                is ScheduleEditorEvent.ExportCsvReady -> {
                    pendingExportText = event.content
                    exportCsvLauncher.launch(event.fileName)
                }
            }
        }
    }

    // Display user-facing validation or error messages.
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    uiState.csvImportErrorDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = viewModel::consumeCsvImportErrorDetail,
            title = { Text("CSV 导入错误详情") },
            text = { Text(detail) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeCsvImportErrorDetail) {
                    Text("我知道了")
                }
            }
        )
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
                        Text("第 ${index + 1} 节")
                        IconButton(onClick = { viewModel.removePeriod(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除课节")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    enabled = !uiState.isSaving && !uiState.isCsvBusy
                ) {
                    Text(if (uiState.isSaving) "保存中..." else "保存")
                }
            }

            item {
                if (uiState.isEditMode) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::exportCsvForEditingSchedule,
                        enabled = !uiState.isSaving && !uiState.isCsvBusy
                    ) {
                        Text(if (uiState.isCsvBusy) "导出中..." else "导出当前作息表CSV")
                    }
                } else {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { importCsvLauncher.launch(arrayOf("text/csv", "text/*")) },
                        enabled = !uiState.isSaving && !uiState.isCsvBusy
                    ) {
                        Text(if (uiState.isCsvBusy) "导入中..." else "导入CSV并创建作息表")
                    }
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
    saveScheduleUseCase: com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase,
    importScheduleCsvUseCase: com.kurosu.sleepin.domain.usecase.schedule.ImportScheduleCsvUseCase,
    exportScheduleCsvUseCase: com.kurosu.sleepin.domain.usecase.schedule.ExportScheduleCsvUseCase
): ScheduleEditorViewModel = viewModel(
    factory = ScheduleEditorViewModel.factory(
        scheduleId = scheduleId,
        getScheduleDetailUseCase = getScheduleDetailUseCase,
        saveScheduleUseCase = saveScheduleUseCase,
        importScheduleCsvUseCase = importScheduleCsvUseCase,
        exportScheduleCsvUseCase = exportScheduleCsvUseCase
    )
)

/** Reads UTF-8 text from a Storage Access Framework Uri. */
private fun Context.readTextFromUri(uri: Uri): String? =
    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

/** Writes UTF-8 text to a Storage Access Framework Uri. */
private fun Context.writeTextToUri(uri: Uri, content: String) {
    contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
        writer.write(content)
    }
}

