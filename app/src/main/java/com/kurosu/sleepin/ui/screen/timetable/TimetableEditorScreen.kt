package com.kurosu.sleepin.ui.screen.timetable

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurosu.sleepin.data.csv.CsvImportTextDecoder

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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var scheduleExpanded by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    // CSV import picker is used only in create mode. File text is decoded with UTF-8 priority
    // and Chinese-encoding fallback before being delegated to the ViewModel parser.
    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val text = uri?.let { context.readTimetableCsvTextFromUri(it) }
        if (!text.isNullOrBlank()) {
            viewModel.createAndImportCsv(text)
        }
    }

    // CSV export requires a writeable document Uri chosen by user. We keep pending content in
    // memory between ViewModel event emission and document creation callback.
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val pending = pendingExportText
        if (uri != null && pending != null) {
            context.writeTextToUri(uri, pending)
        }
        pendingExportText = null
    }

    // Close editor only when ViewModel confirms save succeeded.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TimetableEditorEvent.Saved -> onBackClick()
                is TimetableEditorEvent.ExportCsvReady -> {
                    pendingExportText = event.content
                    exportCsvLauncher.launch(event.fileName)
                }
            }
        }
    }

    // Show and consume validation/error messages emitted by ViewModel.
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

    // Confirm before opening the file picker: a successful import saves the timetable
    // immediately, so the user must acknowledge that up front.
    if (uiState.showCsvImportConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCsvImportConfirm,
            title = { Text("导入 CSV 并保存") },
            text = { Text("导入成功后将直接创建并保存该课程表。是否继续选择 CSV 文件？") },
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
                title = { Text(if (uiState.isEditMode) "编辑课程表" else "新建课程表") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Edit mode exports the existing timetable; create mode imports one from CSV.
                    if (uiState.isEditMode) {
                        TextButton(
                            onClick = viewModel::exportCsvForEditingTimetable,
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
                    placeholder = { Text("16") },
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.startDateText,
                    onValueChange = viewModel::onStartDateChange,
                    label = { Text("开学日期") },
                    supportingText = { Text("使用 ISO 日期格式: yyyy-MM-dd") },
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
    getScheduleDetailUseCase: com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase,
    getTimetableDetailUseCase: com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase,
    createTimetableUseCase: com.kurosu.sleepin.domain.usecase.timetable.CreateTimetableUseCase,
    deleteTimetableUseCase: com.kurosu.sleepin.domain.usecase.timetable.DeleteTimetableUseCase,
    updateTimetableUseCase: com.kurosu.sleepin.domain.usecase.timetable.UpdateTimetableUseCase,
    importCsvUseCase: com.kurosu.sleepin.domain.usecase.csv.ImportCsvUseCase,
    exportCsvUseCase: com.kurosu.sleepin.domain.usecase.csv.ExportCsvUseCase
): TimetableEditorViewModel = viewModel(
    factory = TimetableEditorViewModel.factory(
        timetableId = timetableId,
        getSchedulesUseCase = getSchedulesUseCase,
        getScheduleDetailUseCase = getScheduleDetailUseCase,
        getTimetableDetailUseCase = getTimetableDetailUseCase,
        createTimetableUseCase = createTimetableUseCase,
        deleteTimetableUseCase = deleteTimetableUseCase,
        updateTimetableUseCase = updateTimetableUseCase,
        importCsvUseCase = importCsvUseCase,
        exportCsvUseCase = exportCsvUseCase
    )
)

/**
 * Reads timetable CSV text with UTF-8-first and Chinese-encoding fallback.
 */
private fun Context.readTimetableCsvTextFromUri(uri: Uri): String? =
    contentResolver.openInputStream(uri)?.use { stream ->
        CsvImportTextDecoder.decodeTimetableCsv(stream.readBytes())
    }

/**
 * Writes UTF-8 text to a Storage Access Framework Uri.
 */
private fun Context.writeTextToUri(uri: Uri, content: String) {
    contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
        writer.write(content)
    }
}


