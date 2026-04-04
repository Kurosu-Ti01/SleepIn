package com.kurosu.sleepin.ui.screen.settings

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurosu.sleepin.BuildConfig
import com.kurosu.sleepin.domain.model.ThemeMode
import com.kurosu.sleepin.update.ApkDownloadManager
import kotlinx.coroutines.launch

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
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val alarmManager = remember(context) {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    // The save contract needs a second step (create Uri first, write bytes second),
    // so we cache pending content between the effect emission and document creation callback.
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var showExactAlarmRationale by remember { mutableStateOf(false) }
    var showExactAlarmFallbackMessage by remember { mutableStateOf(false) }
    var exactAlarmGranted by remember {
        mutableStateOf(isExactAlarmGranted(alarmManager))
    }
    var notificationPermissionGranted by remember {
        mutableStateOf(isNotificationPermissionGranted(context))
    }
    var appNotificationsEnabled by remember {
        mutableStateOf(areAppNotificationsEnabled(context))
    }
    var batteryOptimizationIgnored by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }
    var backgroundRestricted by remember {
        mutableStateOf(isBackgroundRestricted(context))
    }

    fun refreshReminderCapabilityStates() {
        exactAlarmGranted = isExactAlarmGranted(alarmManager)
        notificationPermissionGranted = isNotificationPermissionGranted(context)
        appNotificationsEnabled = areAppNotificationsEnabled(context)
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
        backgroundRestricted = isBackgroundRestricted(context)
    }

    val settingsPageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshReminderCapabilityStates()
    }

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

    val exactAlarmPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refreshReminderCapabilityStates()
        if (!exactAlarmGranted) {
            showExactAlarmFallbackMessage = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        // Refresh status values whenever users come back from a system settings page.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshReminderCapabilityStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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

    LaunchedEffect(showExactAlarmFallbackMessage) {
        if (!showExactAlarmFallbackMessage) return@LaunchedEffect
        snackbarHostState.showSnackbar("未授予“闹钟和提醒”权限，将回退为非精确提醒")
        showExactAlarmFallbackMessage = false
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
                    leadingIcon = Icons.Outlined.NotificationsActive,
                    title = "开启上课提醒",
                    checked = uiState.settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && shouldRequestExactAlarmPermission(alarmManager)) {
                            showExactAlarmRationale = true
                        } else {
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    }
                )

                val notificationReady = notificationPermissionGranted && appNotificationsEnabled
                SettingsClickableRow(
                    leadingIcon = Icons.Outlined.Alarm,
                    title = "精确闹钟授权",
                    valueText = if (exactAlarmGranted) "已授权" else "未授权",
                    valueColor = if (exactAlarmGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    onClick = { context.openExactAlarmSettings(exactAlarmPermissionLauncher::launch) }
                )
                SettingsClickableRow(
                    leadingIcon = Icons.Outlined.Notifications,
                    title = "通知权限",
                    valueText = if (notificationReady) "已开启" else "未开启",
                    valueColor = if (notificationReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    onClick = { context.openNotificationSettings(settingsPageLauncher::launch) }
                )
                SettingsClickableRow(
                    leadingIcon = Icons.Outlined.BatterySaver,
                    title = "电池优化白名单",
                    valueText = if (batteryOptimizationIgnored) "已加入" else "未加入",
                    valueColor = if (batteryOptimizationIgnored) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    onClick = { context.openBatteryOptimizationSettings(settingsPageLauncher::launch) }
                )
                SettingsClickableRow(
                    leadingIcon = Icons.Outlined.Settings,
                    title = "后台运行限制",
                    valueText = if (backgroundRestricted) "受限" else "正常",
                    valueColor = if (backgroundRestricted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { context.openAutoStartSettings(settingsPageLauncher::launch) }
                )
                SettingsInfoTextRow(
                    leadingIcon = Icons.Outlined.Info,
                    text = "提示：不同厂商的后台和自启动入口位置不同，若未跳到目标页可在系统设置中手动搜索。",
                    textStyle = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsValueRow(
                    leadingIcon = Icons.Outlined.Schedule,
                    title = "距离上课还有",
                    valueText = "${uiState.settings.reminderMinutes} 分钟"
                )
                // Keep quick-select chips in one horizontal line across screen sizes.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 20, 30).forEach { minutes ->
                        FilterChip(
                            selected = uiState.settings.reminderMinutes == minutes,
                            onClick = { viewModel.setReminderMinutes(minutes) },
                            label = { Text("${minutes}m") }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    leadingIcon = Icons.Outlined.AutoAwesome,
                    title = "启用灵动岛通知（未实现）",
                    checked = uiState.settings.fluidCloudEnabled,
                    enabled = false,
                    onCheckedChange = viewModel::setFluidCloudEnabled
                )
            }

            SettingsSectionCard(title = "外观") {
                SettingsInfoTextRow(
                    leadingIcon = Icons.Outlined.Palette,
                    text = "主题模式",
                    textStyle = MaterialTheme.typography.bodyMedium
                )
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
                    leadingIcon = Icons.Outlined.ColorLens,
                    title = "启用动态取色",
                    checked = uiState.settings.dynamicColorEnabled,
                    onCheckedChange = viewModel::setDynamicColorEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsValueRow(
                    leadingIcon = Icons.Outlined.Height,
                    title = "课程卡片高度",
                    valueText = "${uiState.settings.courseCellHeightDp} dp"
                )
                Slider(
                    value = uiState.settings.courseCellHeightDp.toFloat(),
                    onValueChange = { viewModel.setCourseCellHeightDp(it.toInt()) },
                    valueRange = 44f..120f
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSwitchRow(
                    leadingIcon = Icons.Outlined.ViewWeek,
                    title = "显示非本周课程",
                    checked = uiState.settings.showNonCurrentWeekCourses,
                    onCheckedChange = viewModel::setShowNonCurrentWeekCourses
                )
            }

            SettingsSectionCard(title = "数据") {
                SettingsInfoTextRow(
                    leadingIcon = Icons.Outlined.Backup,
                    text = "设置备份仅包含应用偏好配置。",
                    textStyle = MaterialTheme.typography.bodyMedium
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
                val updateError = uiState.settings.lastUpdateCheckError
                SettingsSwitchRow(
                    leadingIcon = Icons.Outlined.Update,
                    title = "自动检查更新",
                    checked = uiState.settings.autoCheckUpdateEnabled,
                    onCheckedChange = viewModel::setAutoCheckUpdateEnabled
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsValueRow(
                    leadingIcon = Icons.Outlined.SystemUpdate,
                    title = "更新状态",
                    valueText = when {
                        uiState.settings.lastUpdateCheckAtMillis <= 0L -> "尚未检查更新"
                        uiState.settings.updateAvailable -> "发现新版本：${uiState.settings.latestRemoteVersion}"
                        updateError.isNotBlank() -> "上次检查失败"
                        else -> "当前已是最新版本"
                    }
                )
                if (updateError.isNotBlank()) {
                    SettingsCopyableMessageRow(
                        leadingIcon = Icons.Outlined.Info,
                        title = "错误详情",
                        valueText = updateError,
                        onCopyClick = {
                            clipboardManager.setText(AnnotatedString(updateError))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("已复制更新报错信息")
                            }
                        }
                    )
                }
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

            SettingsSectionCard(title = "关于") {
                SettingsClickableRow(
                    leadingIcon = Icons.Outlined.Language,
                    title = "项目网站",
                    valueText = "sleepin.kurosu.qzz.io",
                    valueColor = MaterialTheme.colorScheme.primary,
                    onClick = { context.openUrl("https://sleepin.kurosu.qzz.io/") }
                )
                SettingsClickableRow(
                    leadingIcon = Icons.Outlined.Code,
                    title = "GitHub",
                    valueText = "Kurosu-Ti01/SleepIn",
                    valueColor = MaterialTheme.colorScheme.primary,
                    onClick = { context.openUrl("https://github.com/Kurosu-Ti01/SleepIn") }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsValueRow(
                    leadingIcon = Icons.Outlined.Info,
                    title = "当前版本",
                    valueText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
            }
        }
    }

    if (showExactAlarmRationale) {
        AlertDialog(
            onDismissRequest = { showExactAlarmRationale = false },
            title = { Text("需要“闹钟和提醒”权限") },
            text = {
                Text("为了尽量严格在上课前 N 分钟触发提醒，需要允许“闹钟和提醒”。未授权时仍会使用后台任务提醒，但时间可能延后。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmRationale = false
                        viewModel.setNotificationsEnabled(true)
                        context.openExactAlarmSettings(exactAlarmPermissionLauncher::launch)
                    }
                ) {
                    Text("去授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmRationale = false }) {
                    Text("暂不")
                }
            }
        )
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
    leadingIcon: ImageVector,
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Reusable settings row that is fully clickable.
 */
@Composable
private fun SettingsClickableRow(
    leadingIcon: ImageVector,
    title: String,
    valueText: String,
    valueColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp), // Increase padding for better touch target
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = valueText, style = MaterialTheme.typography.bodySmall, color = valueColor)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "跳转",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Reusable settings row that displays read-only information.
 */
@Composable
private fun SettingsValueRow(
    leadingIcon: ImageVector,
    title: String,
    valueText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Reusable text row with leading icon for non-interactive setting descriptions.
 */
@Composable
private fun SettingsInfoTextRow(
    leadingIcon: ImageVector,
    text: String,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Displays a selectable error message with an explicit copy action.
 */
@Composable
private fun SettingsCopyableMessageRow(
    leadingIcon: ImageVector,
    title: String,
    valueText: String,
    onCopyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onCopyClick) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制错误详情",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SelectionContainer {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
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

/**
 * Opens a URL in browser apps and silently ignores unsupported targets.
 */
private fun Context.openUrl(url: String): Boolean {
    return tryDirectStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

/**
 * Returns whether exact alarm authorization flow should be shown before enabling reminders.
 */
private fun shouldRequestExactAlarmPermission(alarmManager: AlarmManager): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()
}

/**
 * Returns whether the app currently has exact alarm capability.
 */
private fun isExactAlarmGranted(alarmManager: AlarmManager): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}

/**
 * Returns whether the app can post notifications according to Android runtime permission model.
 */
private fun isNotificationPermissionGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * Returns whether app-level notification switch is enabled in system settings.
 */
private fun areAppNotificationsEnabled(context: Context): Boolean {
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/**
 * Returns whether the app is currently exempted from battery optimization restrictions.
 */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Returns whether Android marks this app as background restricted.
 */
private fun isBackgroundRestricted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return activityManager.isBackgroundRestricted
}

/**
 * Opens app notification settings and falls back to app details page if needed.
 */
private fun Context.openNotificationSettings(launch: (Intent) -> Unit) {
    val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    launchSettingsIntentWithFallback(
        launch = launch,
        candidates = listOf(notificationIntent)
    )
}

/**
 * Opens battery optimization screens for whitelist configuration with safe fallbacks.
 */
private fun Context.openBatteryOptimizationSettings(launch: (Intent) -> Unit) {
    // Use the generic battery optimization page first.
    // Some OEM ROMs reject package-specific requests and log "Error getting package info".
    val ignoreListIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    launchSettingsIntentWithFallback(
        launch = launch,
        candidates = listOf(ignoreListIntent, appDetailsIntent)
    )
}

/**
 * Opens vendor-specific auto-start/background pages and falls back to app details.
 */
private fun Context.openAutoStartSettings(launch: (Intent) -> Unit) {
    val autoStartCandidates = buildAutoStartIntents() +
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    launchSettingsIntentWithFallback(
        launch = launch,
        candidates = autoStartCandidates
    )
}

/**
 * Launches settings intents in order. If one target cannot be opened, it keeps trying
 * the next candidate and finally falls back to app details settings.
 */
private fun Context.launchSettingsIntentWithFallback(
    launch: (Intent) -> Unit,
    candidates: List<Intent>
) {
    for (candidate in candidates) {
        if (tryLaunchIntent(launch, candidate)) return
    }
    openAppDetailsSettings(launch)
}

/**
 * Opens app details settings for manual permission and battery management fallback.
 */
private fun Context.openAppDetailsSettings(launch: (Intent) -> Unit) {
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
    }
    if (tryLaunchIntent(launch, appDetailsIntent)) return

    // Some ROMs block direct app-details routing; use system settings as a final fallback.
    tryLaunchIntent(launch, Intent(Settings.ACTION_SETTINGS))
}

/**
 * Tries launching through ActivityResult launcher first, then falls back to direct startActivity.
 */
private fun Context.tryLaunchIntent(
    launch: (Intent) -> Unit,
    intent: Intent
): Boolean {
    try {
        launch(intent)
        return true
    } catch (_: ActivityNotFoundException) {
        // Fallback to direct startActivity path.
    } catch (_: SecurityException) {
        // Fallback to direct startActivity path.
    }

    return tryDirectStartActivity(intent)
}

/**
 * Directly starts an activity and returns whether the operation succeeds.
 */
private fun Context.tryDirectStartActivity(intent: Intent): Boolean {
    val safeIntent = Intent(intent).apply {
        if (this@tryDirectStartActivity !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return try {
        startActivity(safeIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }
}

/**
 * Builds known vendor-specific intents that commonly host auto-start/background controls.
 */
private fun buildAutoStartIntents(): List<Intent> {
    return listOf(
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        Intent().setClassName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"
        ),
        Intent().setClassName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"
        ),
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
        ),
        Intent().setClassName(
            "com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
        )
    )
}

/**
 * Opens Android system screens where users can grant exact alarm scheduling capability.
 */
private fun Context.openExactAlarmSettings(launch: (Intent) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val exactAlarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:$packageName")
    }
    launchSettingsIntentWithFallback(
        launch = launch,
        candidates = listOf(exactAlarmIntent)
    )
}
