package com.kurosu.sleepin.ui.screen.importer.web

import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The WebView an import script runs in, plus the controls around it.
 *
 * The page is left to the user: they log in, navigate, and only then press the import button. That
 * order matters because a portal's timetable sits behind a session, so the app cannot fetch it on its
 * own — which is why the import runs as a script inside the browser the user is already using.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWebScreen(
    onBackClick: () -> Unit,
    onFinished: () -> Unit,
    onOpenScheduleList: () -> Unit,
    viewModel: ImportWebViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var addressText by remember { mutableStateOf("") }
    var isAddressEditing by remember { mutableStateOf(false) }
    val addressFocusRequester = remember { FocusRequester() }
    val addressInteractionSource = remember { MutableInteractionSource() }
    val isAddressFocused by addressInteractionSource.collectIsFocusedAsState()
    var scheduleExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showImportHelp by remember { mutableStateOf(false) }
    var userAgentMode by remember { mutableStateOf(ImportUserAgentMode.MOBILE) }
    val mobileUserAgent = remember(context) { WebSettings.getDefaultUserAgent(context) }

    // The ViewModel drives the bridge but must not hold a WebView, so the screen lends it one.
    val pageHandle = remember(viewModel) {
        object : ImportWebViewHandle {
            override fun evaluate(script: String, onResult: ((String?) -> Unit)?) {
                val view = webViewState.value ?: return
                val callback = onResult?.let { notify ->
                    ValueCallback<String> { value -> notify(value) }
                }
                view.evaluateJavascript(script, callback)
            }

            override fun setUserAgent(userAgent: String) {
                webViewState.value?.settings?.userAgentString = userAgent
            }

            override fun reload() {
                webViewState.value?.reload()
            }
        }
    }

    val pageListener = remember(viewModel) {
        object : ImportPageListener {
            override fun onPageStarted(url: String) = viewModel.onPageStarted(url)
            override fun onPageFinished(url: String) = viewModel.onPageFinished(url)
            override fun onTitleChanged(title: String) = viewModel.onTitleChanged(title)
            override fun onProgressChanged(progress: Int) = viewModel.onProgressChanged(progress)
            override fun onPageLoadFailed(message: String) = viewModel.onPageLoadFailed(message)
        }
    }

    val bridgeHost = remember(viewModel) {
        SleepInBridgeHost(Handler(Looper.getMainLooper())) { message ->
            viewModel.onBridgeMessage(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportWebEvent.Finished -> {
                    // A toast rather than a snackbar: this screen is about to leave the back stack, and
                    // the confirmation has to outlive it.
                    event.message?.let { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    onFinished()
                }
            }
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    // Keep the address in step with the page while it is in title mode, but do not overwrite the URL
    // during editing or while the text field still owns focus.
    LaunchedEffect(uiState.pageUrl, isAddressEditing, isAddressFocused) {
        if (!isAddressEditing && !isAddressFocused) {
            addressText = uiState.pageUrl.ifBlank { uiState.startUrl.orEmpty() }
        }
    }

    LaunchedEffect(isAddressEditing) {
        if (isAddressEditing && uiState.isStarted) addressFocusRequester.requestFocus()
    }

    // Back is layered: the first press closes the keyboard if the address field has it (which is what a
    // focused field does everywhere else), then it walks the page's own history, and only when there is
    // nowhere left to go does it leave the screen.
    BackHandler {
        when {
            isAddressEditing || isAddressFocused -> {
                focusManager.clearFocus()
                isAddressEditing = false
                addressText = uiState.pageUrl.ifBlank { uiState.startUrl.orEmpty() }
            }
            webViewState.value?.canGoBack() == true -> webViewState.value?.goBack()
            else -> onBackClick()
        }
    }

    uiState.pendingCommit?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCommit,
            title = { Text("确认导入") },
            text = { Text(buildCommitPreview(pending)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCommit) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCommit) { Text("取消") }
            }
        )
    }

    uiState.successNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::consumeSuccessNotice,
            title = { Text("导入完成") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeSuccessNotice) { Text("完成") }
            }
        )
    }

    ImportDialogHost(
        request = uiState.dialog,
        onAnswer = viewModel::onDialogAnswered,
        onValidate = viewModel::validatePromptInput
    )

    Scaffold(
        topBar = {
            ImportTopBar(
                uiState = uiState,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { expanded -> menuExpanded = expanded },
                userAgentMode = userAgentMode,
                addressEditing = isAddressEditing,
                addressText = addressText,
                addressInteractionSource = addressInteractionSource,
                addressFocusRequester = addressFocusRequester,
                onAddressTextChange = { value -> addressText = value },
                onAddressEditClick = { isAddressEditing = true },
                onAddressSubmit = { raw ->
                    normalizeAddressInput(raw)?.let { target ->
                        focusManager.clearFocus()
                        isAddressEditing = false
                        webViewState.value?.loadUrl(target)
                    }
                },
                onAddressCancel = {
                    focusManager.clearFocus()
                    isAddressEditing = false
                    addressText = uiState.pageUrl.ifBlank { uiState.startUrl.orEmpty() }
                },
                onUserAgentModeChange = { mode ->
                    if (mode != userAgentMode) {
                        userAgentMode = mode
                        pageHandle.setUserAgent(userAgentForMode(mobileUserAgent, mode))
                        viewModel.onReloadClick()
                    }
                },
                onBackClick = onBackClick,
                onReloadClick = viewModel::onReloadClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (uiState.stage()) {
                ImportWebStage.Running -> ImportRunningContent(
                    uiState = uiState,
                    mobileUserAgent = mobileUserAgent,
                    userAgentMode = userAgentMode,
                    onRunScript = viewModel::onRunScriptClick,
                    webViewState = webViewState,
                    pageHandle = pageHandle,
                    bridgeHost = bridgeHost,
                    pageListener = pageListener,
                    viewModel = viewModel,
                    showImportHelp = showImportHelp,
                    onImportHelpChange = { showImportHelp = it }
                )

                // Preparation also covers "still loading" and "the script could not be read"; it shows a
                // spinner in the first case and the reason in the second.
                ImportWebStage.Loading,
                ImportWebStage.Preparation -> ImportPreparationContent(
                    uiState = uiState,
                    scheduleExpanded = scheduleExpanded,
                    onScheduleExpandedChange = { expanded -> scheduleExpanded = expanded },
                    onScheduleChange = viewModel::onScheduleChange,
                    onOpenScheduleList = onOpenScheduleList,
                    onStart = viewModel::onStartImport
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportTopBar(
    uiState: ImportWebUiState,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    userAgentMode: ImportUserAgentMode,
    addressEditing: Boolean,
    addressText: String,
    addressInteractionSource: MutableInteractionSource,
    addressFocusRequester: FocusRequester,
    onAddressTextChange: (String) -> Unit,
    onAddressEditClick: () -> Unit,
    onAddressSubmit: (String) -> Unit,
    onAddressCancel: () -> Unit,
    onUserAgentModeChange: (ImportUserAgentMode) -> Unit,
    onBackClick: () -> Unit,
    onReloadClick: () -> Unit
) {
    TopAppBar(
        title = {
            if (uiState.isStarted && addressEditing) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = onAddressTextChange,
                    singleLine = true,
                    placeholder = { Text("输入网址") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onAddressSubmit(addressText) }),
                    trailingIcon = {
                        IconButton(onClick = { onAddressSubmit(addressText) }) {
                            Icon(Icons.Filled.Search, contentDescription = "打开网址")
                        }
                    },
                    interactionSource = addressInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(addressFocusRequester)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = uiState.isStarted, onClick = onAddressEditClick),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = uiState.pageTitle.ifBlank {
                            uiState.presetName.ifBlank { "从教务系统导入" }
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.isStarted) {
                        Icon(Icons.Filled.Search, contentDescription = "编辑网址")
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = if (addressEditing) onAddressCancel else onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            if (uiState.isStarted) {
                Box {
                    IconButton(onClick = { onMenuExpandedChange(true) }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text("手机版 UA${if (userAgentMode == ImportUserAgentMode.MOBILE) "（当前）" else ""}") },
                            onClick = {
                                onMenuExpandedChange(false)
                                onUserAgentModeChange(ImportUserAgentMode.MOBILE)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("桌面版 UA${if (userAgentMode == ImportUserAgentMode.DESKTOP) "（当前）" else ""}") },
                            onClick = {
                                onMenuExpandedChange(false)
                                onUserAgentModeChange(ImportUserAgentMode.DESKTOP)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("刷新页面") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                onMenuExpandedChange(false)
                                onReloadClick()
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * The WebView, compact address bar and floating controls.
 *
 * Split out of the screen so the page and the preparation step are two sibling branches rather than
 * one branch that has to bail out of a shared container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportRunningContent(
    uiState: ImportWebUiState,
    mobileUserAgent: String,
    userAgentMode: ImportUserAgentMode,
    onRunScript: () -> Unit,
    webViewState: MutableState<WebView?>,
    pageHandle: ImportWebViewHandle,
    bridgeHost: SleepInBridgeHost,
    pageListener: ImportPageListener,
    viewModel: ImportWebViewModel,
    showImportHelp: Boolean,
    onImportHelpChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.pageUrl.startsWith("http://", ignoreCase = true)) {
            CleartextConnectionWarning()
        }

        uiState.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        uiState.loadError?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    val created = ImportWebViewFactory.create(
                        context = viewContext,
                        initialUrl = uiState.startUrl.orEmpty().ifBlank { BLANK_PAGE },
                        userAgent = userAgentForMode(mobileUserAgent, userAgentMode),
                        host = bridgeHost,
                        listener = pageListener
                    )
                    // Assigned before the page can report anything: loadUrl only posts the load, so both
                    // the handle and the ViewModel see a WebView that already exists.
                    webViewState.value = created
                    viewModel.attachPage(pageHandle)
                    created
                },
                onRelease = { released ->
                    viewModel.detachPage()
                    webViewState.value = null
                    ImportWebViewFactory.destroy(released)
                }
            )

            // Keep the browser controls over the page, not in a fixed-height bottom bar: portals often
            // need every vertical pixel to show their timetable grid on a phone.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(onClick = { onImportHelpChange(true) }) {
                    Icon(Icons.Outlined.Info, contentDescription = "导入帮助")
                }
                ExtendedFloatingActionButton(
                    text = {
                        Text(
                            when {
                                uiState.isCommitting -> "正在写入…"
                                uiState.hasInjectedScript -> "重新导入"
                                else -> "执行导入"
                            }
                        )
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = { if (uiState.canRunScript) onRunScript() },
                    modifier = Modifier.semantics {
                        if (!uiState.canRunScript) disabled()
                    },
                    containerColor = if (uiState.canRunScript) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (uiState.canRunScript) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    if (showImportHelp) {
        AlertDialog(
            onDismissRequest = { onImportHelpChange(false) },
            title = { Text("导入提示") },
            text = {
                Text(
                    "请先登录教务系统并打开课表页面，再点“执行导入”。页面显示或功能异常时，可在右上角“更多”中切换手机版 / 桌面版 UA。重新执行会先刷新当前页面。"
                )
            },
            confirmButton = {
                TextButton(onClick = { onImportHelpChange(false) }) { Text("知道了") }
            }
        )
    }
}

/**
 * The step before the WebView: confirm which schedule the imported timetable will use.
 *
 * Kept inside this screen rather than on a page of its own because it is the last thing the user
 * decides before the page appears, and it is the same schedule the write will reference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreparationContent(
    uiState: ImportWebUiState,
    scheduleExpanded: Boolean,
    onScheduleExpandedChange: (Boolean) -> Unit,
    onScheduleChange: (Long) -> Unit,
    onOpenScheduleList: () -> Unit,
    onStart: () -> Unit
) {
    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (uiState.presetName.isNotBlank()) {
                Text(text = uiState.presetName, style = MaterialTheme.typography.titleMedium)
            }
            if (uiState.presetDescription.isNotBlank()) {
                Text(
                    text = uiState.presetDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.scheduleOptions.isEmpty()) {
                Text(
                    text = "还没有作息表。导入不会修改作息表，而课程表需要关联一个作息表才能显示上课时间。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onOpenScheduleList) { Text("去创建作息表") }
            } else {
                ScheduleDropdown(
                    uiState = uiState,
                    expanded = scheduleExpanded,
                    onExpandedChange = onScheduleExpandedChange,
                    onScheduleChange = onScheduleChange
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onStart,
                    enabled = !uiState.scriptUnavailable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("开始导入")
                }

                if (uiState.scriptUnavailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "该方案的脚本无法读取，请返回重新选择方案。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Picker for the schedule the resulting timetable will reference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDropdown(
    uiState: ImportWebUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onScheduleChange: (Long) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        val selectedName = uiState.scheduleOptions
            .firstOrNull { option -> option.id == uiState.selectedScheduleId }
            ?.name
            .orEmpty()

        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
                .clickable { onExpandedChange(true) },
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("关联作息表") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            uiState.scheduleOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onScheduleChange(option.id)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

/** Warns before the user browses a page over HTTP. */
@Composable
private fun CleartextConnectionWarning() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "不安全连接：当前页面使用 HTTP 明文传输，URL 中的登录认证 code 和请求内容可能被窃听或篡改。请确认这是学校官方域名；其他非 .edu.cn 的 HTTP 地址仍会被系统拦截。",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
        )
    }
}

/**
 * Summary shown before writing.
 *
 * Deliberately describes what will be replaced as well as what will be added: the user is about to
 * change stored data on the word of a script they did not write, and this is their only chance to see
 * it first.
 */
private fun buildCommitPreview(pending: ImportPendingCommit): String = buildString {
    append("目标：")
    append(pending.targetLabel)
    append('\n')
    val name = pending.submission.timetable?.name
    if (!name.isNullOrBlank()) {
        append("名称：")
        append(name)
        append('\n')
    }
    if (pending.scheduleLabel.isNotBlank()) {
        append("作息表：")
        append(pending.scheduleLabel)
        append('\n')
    }
    append("课程：")
    append(pending.submission.courses.size)
    append(" 门\n")
    append("课时：")
    append(pending.submission.courses.sumOf { course -> course.sessions.size })
    append(" 条")
}

/**
 * Turns what the user typed into a URL, defaulting to https because portal addresses almost always use
 * it. A portal that only serves plain HTTP needs the scheme typed out.
 */
private fun normalizeAddressInput(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.startsWith("about:")) return trimmed
    return "https://$trimmed"
}

/** Blank page a preset without a start URL begins at. */
private const val BLANK_PAGE = "about:blank"
