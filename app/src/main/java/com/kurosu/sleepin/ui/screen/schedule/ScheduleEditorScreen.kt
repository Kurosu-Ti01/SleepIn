package com.kurosu.sleepin.ui.screen.schedule

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurosu.sleepin.data.csv.CsvImportTextDecoder

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
    val clipboardManager = LocalClipboardManager.current
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val text = uri?.let { context.readScheduleCsvTextFromUri(it) }
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

    // Import failure detail: selectable text plus an explicit copy action so users can
    // share or inspect parser errors outside the app.
    uiState.csvImportErrorDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = viewModel::consumeCsvImportErrorDetail,
            title = { Text("CSV 导入错误详情") },
            text = { SelectionContainer { Text(detail) } },
            confirmButton = {
                TextButton(onClick = viewModel::consumeCsvImportErrorDetail) {
                    Text("我知道了")
                }
            },
            dismissButton = {
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(detail)) }) {
                    Text("复制")
                }
            }
        )
    }

    // Confirm before opening the file picker: a successful import saves the schedule
    // immediately, so the user must acknowledge that up front.
    if (uiState.showCsvImportConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCsvImportConfirm,
            title = { Text("导入 CSV 并保存") },
            text = { Text("导入成功后将直接创建并保存该作息表。是否继续选择 CSV 文件？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissCsvImportConfirm()
                        importCsvLauncher.launch(arrayOf("text/csv", "text/*"))
                    }
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCsvImportConfirm) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditMode) "编辑作息表" else "新建作息表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Edit mode exports the existing schedule; create mode imports one from CSV.
                    if (uiState.isEditMode) {
                        TextButton(
                            onClick = viewModel::exportCsvForEditingSchedule,
                            enabled = !uiState.isSaving && !uiState.isCsvBusy
                        ) {
                            Text(if (uiState.isCsvBusy) "导出中..." else "导出CSV")
                        }
                    } else {
                        TextButton(
                            onClick = viewModel::onImportCsvClick,
                            enabled = !uiState.isSaving && !uiState.isCsvBusy
                        ) {
                            Text(if (uiState.isCsvBusy) "导入中..." else "导入CSV")
                        }
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !uiState.isSaving && !uiState.isCsvBusy
                    ) {
                        Text(if (uiState.isSaving) "保存中..." else "保存")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "基本信息",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.name,
                            onValueChange = viewModel::onNameChange,
                            label = { Text("作息表名称") },
                            singleLine = true
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "自动生成课节",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Quick generation inputs: first class start + durations + count.
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

                        OutlinedButton(
                            onClick = viewModel::generatePeriods,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("按上述参数生成课节")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "课节列表",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "共 ${uiState.periods.size} 节",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Each period is its own bordered card, matching the course editor's session cards.
            itemsIndexed(uiState.periods) { index, period ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "课节 ${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.removePeriod(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除课节",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )

                        // Editable so a gap in the numbering can be filled: the app only asks the user
                        // to complete a schedule, it must not force them to delete and re-add rows to
                        // reach a number in the middle.
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = period.periodNumber,
                            onValueChange = { viewModel.onPeriodNumberChange(index, it) },
                            label = { Text("节次") },
                            // No placeholder: the row number would be wrong for any schedule that skips a
                            // number, and a grey value that the field does not actually contain is easy to
                            // mistake for the real one.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

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
            }

            item {
                OutlinedButton(
                    onClick = viewModel::addPeriod,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加课节", modifier = Modifier.padding(start = 6.dp))
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

/** Reads schedule CSV text with UTF-8-first and Chinese-encoding fallback. */
private fun Context.readScheduleCsvTextFromUri(uri: Uri): String? =
    contentResolver.openInputStream(uri)?.use { stream ->
        CsvImportTextDecoder.decodeScheduleCsv(stream.readBytes())
    }

/** Writes UTF-8 text to a Storage Access Framework Uri. */
private fun Context.writeTextToUri(uri: Uri, content: String) {
    contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
        writer.write(content)
    }
}

