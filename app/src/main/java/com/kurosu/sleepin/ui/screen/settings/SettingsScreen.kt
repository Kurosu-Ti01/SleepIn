package com.kurosu.sleepin.ui.screen.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.update.ApkDownloadManager

/**
 * Full settings screen for Phase 6.
 *
 * Responsibilities in this screen:
 * - Render app preferences and write changes through ViewModel.
 * - Trigger settings backup file flows via system file picker contracts.
 * - Display operation feedback as snackbars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // The save contract needs a second step (create Uri first, write bytes second),
    // so we cache pending content between the effect emission and document creation callback.
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val text = uri?.let { context.readTextFromUri(it) }
        if (text != null) {
            viewModel.importBackup(text)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val pending = pendingExportText
        if (uri != null && pending != null) {
            context.writeTextToUri(uri, pending)
        }
        pendingExportText = null
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsUiEffect.ExportBackupReady -> {
                    pendingExportText = effect.content
                    exportLauncher.launch(effect.fileName)
                }
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsSectionCard(title = "通知") {
                SettingsSwitchRow(
                    title = "开启上课提醒",
                    checked = uiState.settings.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "距离上课还有：${uiState.settings.reminderMinutes} 分钟",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { minutes ->
                        FilterChip(
                            selected = uiState.settings.reminderMinutes == minutes,
                            onClick = { viewModel.setReminderMinutes(minutes) },
                            label = { Text("${minutes}m") }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = "启用灵动岛通知",
                    checked = uiState.settings.fluidCloudEnabled,
                    onCheckedChange = viewModel::setFluidCloudEnabled
                )
            }

            SettingsSectionCard(title = "外观") {
                Text("主题模式", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = "启用动态取色",
                    checked = uiState.settings.dynamicColorEnabled,
                    onCheckedChange = viewModel::setDynamicColorEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "课程卡片高度：${uiState.settings.courseCellHeightDp} dp",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.settings.courseCellHeightDp.toFloat(),
                    onValueChange = { viewModel.setCourseCellHeightDp(it.toInt()) },
                    valueRange = 44f..120f
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    title = "显示非本周课程",
                    checked = uiState.settings.showNonCurrentWeekCourses,
                    onCheckedChange = viewModel::setShowNonCurrentWeekCourses
                )
            }

            SettingsSectionCard(title = "数据") {
                Text(
                    text = "设置备份仅包含应用偏好配置。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Text("导入备份")
                    }
                    Button(onClick = viewModel::exportBackup) {
                        Text("导出备份")
                    }
                }
            }

            SettingsSectionCard(title = "更新") {
                SettingsSwitchRow(
                    title = "自动检查更新",
                    checked = uiState.settings.autoCheckUpdateEnabled,
                    onCheckedChange = viewModel::setAutoCheckUpdateEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = when {
                        uiState.settings.lastUpdateCheckAtMillis <= 0L -> "尚未检查更新"
                        uiState.settings.updateAvailable -> "发现新版本：${uiState.settings.latestRemoteVersion}"
                        uiState.settings.lastUpdateCheckError.isNotBlank() -> "上次检查失败：${uiState.settings.lastUpdateCheckError}"
                        else -> "当前已是最新版本"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = viewModel::checkForUpdates,
                        enabled = !uiState.isCheckingUpdate
                    ) {
                        Text(if (uiState.isCheckingUpdate) "检查中..." else "立即检查")
                    }
                    Button(
                        onClick = {
                            val url = uiState.settings.latestApkDownloadUrl.ifBlank {
                                uiState.settings.latestReleasePageUrl
                            }
                            if (url.isNotBlank()) {
                                ApkDownloadManager.enqueueApkDownload(
                                    context = context,
                                    downloadUrl = url,
                                    versionTag = uiState.settings.latestRemoteVersion
                                )
                                viewModel.onDownloadEnqueued()
                            }
                        },
                        enabled = uiState.settings.updateAvailable &&
                            uiState.settings.latestApkDownloadUrl.isNotBlank()
                    ) {
                        Text("下载更新")
                    }
                }
            }
        }
    }
}

/**
 * Section card wrapper used to keep the settings page visually grouped and scan-friendly.
 */
@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/**
 * Reusable settings row with text at start and switch at end.
 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Reads UTF-8 text from a document Uri selected through Storage Access Framework.
 */
private fun Context.readTextFromUri(uri: Uri): String? =
    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

/**
 * Writes UTF-8 text to a document Uri selected through Storage Access Framework.
 */
private fun Context.writeTextToUri(uri: Uri, content: String) {
    contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
        writer.write(content)
    }
}


