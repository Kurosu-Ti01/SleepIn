package com.kurosu.sleepin.ui.screen.importer.web

import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
    var isAddressFocused by remember { mutableStateOf(false) }
    var scheduleExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

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

    // Keep the address field in step with the page, but never overwrite what the user is typing: a
    // focused field is one the user is editing, so the page's URL must not win.
    LaunchedEffect(uiState.pageUrl, isAddressFocused) {
        if (!isAddressFocused) {
            addressText = uiState.pageUrl.ifBlank { uiState.startUrl.orEmpty() }
        }
    }

    // Back is layered: the first press closes the keyboard if the address field has it (which is what a
    // focused field does everywhere else), then it walks the page's own history, and only when there is
    // nowhere left to go does it leave the screen.
    BackHandler {
        when {
            isAddressFocused -> focusManager.clearFocus()
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
            if (uiState.isStarted) {
                ImportRunningContent(
                    uiState = uiState,
                    addressText = addressText,
                    onAddressTextChange = { value -> addressText = value },
                    onAddressFocusChange = { focused -> isAddressFocused = focused },
                    onAddressSubmit = { raw ->
                        normalizeAddressInput(raw)?.let { target -> webViewState.value?.loadUrl(target) }
                    },
                    onRunScript = viewModel::onRunScriptClick,
                    webViewState = webViewState,
                    pageHandle = pageHandle,
                    bridgeHost = bridgeHost,
                    pageListener = pageListener,
                    viewModel = viewModel
                )
            } else {
                ImportPreparationContent(
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
    onBackClick: () -> Unit,
    onReloadClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = uiState.pageTitle.ifBlank {
                    uiState.presetName.ifBlank { "从教务系统导入" }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
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
 * The WebView and its address bar, progress bar and import button.
 *
 * Split out of the screen so the page and the preparation step are two sibling branches rather than
 * one branch that has to bail out of a shared container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportRunningContent(
    uiState: ImportWebUiState,
    addressText: String,
    onAddressTextChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onAddressSubmit: (String) -> Unit,
    onRunScript: () -> Unit,
    webViewState: MutableState<WebView?>,
    pageHandle: ImportWebViewHandle,
    bridgeHost: SleepInBridgeHost,
    pageListener: ImportPageListener,
    viewModel: ImportWebViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.startUrl == null) {
            AddressBar(
                value = addressText,
                onValueChange = onAddressTextChange,
                onSubmit = onAddressSubmit,
                onFocusChanged = onAddressFocusChange
            )
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
        }

        ImportActionBar(uiState = uiState, onRunScript = onRunScript)
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

/**
 * Address field for presets that do not name a start page.
 *
 * Always editable. An earlier version made it editable only after a tap, which cannot work: a
 * `readOnly` text field is not focusable, so clearing the flag at runtime does not give it focus and
 * the keyboard never appears. What the user is typing is protected from the page instead — the screen
 * stops syncing the field while it holds focus.
 */
@Composable
private fun AddressBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) { onFocusChanged(isFocused) }

    val submit = {
        focusManager.clearFocus()
        onSubmit(value)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("输入教务系统网址，例如 https://jwc.example.edu.cn") },
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { submit() }),
        trailingIcon = {
            IconButton(onClick = submit) {
                Icon(Icons.Filled.Search, contentDescription = "打开网址")
            }
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * The import button and the one instruction that makes the flow work.
 */
@Composable
private fun ImportActionBar(
    uiState: ImportWebUiState,
    onRunScript: () -> Unit
) {
    HorizontalDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (uiState.hasInjectedScript) {
                "如果导入没有成功，可以再次点击；重新执行会先刷新当前页面。"
            } else {
                "请先登录教务系统并打开课表页面，再点击下面的按钮。"
            },
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = onRunScript,
            enabled = uiState.canRunScript,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    uiState.isCommitting -> "正在写入…"
                    uiState.hasInjectedScript -> "重新执行导入"
                    else -> "执行导入"
                }
            )
        }
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
