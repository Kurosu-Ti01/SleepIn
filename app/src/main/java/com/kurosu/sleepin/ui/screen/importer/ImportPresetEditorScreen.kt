package com.kurosu.sleepin.ui.screen.importer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurosu.sleepin.data.importer.ImportScriptTextDecoder
import com.kurosu.sleepin.domain.model.importer.ImportPresetGroup

/**
 * Creates a preset of the user's own, or changes one they added earlier.
 *
 * This screen is where the long tail is actually solved: a portal the app has never heard of becomes
 * usable because someone who has an account typed its address and supplied a script. Everything here is
 * arranged to make that as short a job as possible — the address first, the script second, and a way to
 * try the result without going back to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPresetEditorScreen(
    onBackClick: () -> Unit,
    onSaved: (presetId: String, runNow: Boolean) -> Unit,
    viewModel: ImportPresetEditorViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var groupExpanded by remember { mutableStateOf(false) }
    var schoolExpanded by remember { mutableStateOf(false) }

    // Any MIME type is accepted: Android's mapping for .js files depends on which document provider is
    // installed, and several report it as an unknown binary, which a narrower filter would hide.
    val pickScriptLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = context.readScriptTextFromUri(uri)
            if (text.isNullOrBlank()) {
                viewModel.onScriptUnavailable("无法读取该文件，请换一个文件，或改用粘贴。")
            } else {
                viewModel.onScriptLoaded(text, sourceLabel = "已选择文件")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportPresetEditorEvent.Saved -> onSaved(event.presetId, event.runNow)
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
                title = { Text(if (uiState.isEditMode) "修改导入方案" else "导入自己的脚本") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "每个学校可以保存多种导入方案，例如本科和研究生教务系统；相同学校请使用相同名称。",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = uiState.schoolName,
                    onValueChange = viewModel::onSchoolNameChange,
                    label = { Text("学校名称") },
                    placeholder = { Text("填写学校名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    OutlinedButton(onClick = { schoolExpanded = true }) {
                        Text("从已有学校中选择")
                    }
                    DropdownMenu(
                        expanded = schoolExpanded,
                        onDismissRequest = { schoolExpanded = false }
                    ) {
                        uiState.schoolSuggestions.forEach { school ->
                            DropdownMenuItem(
                                text = {
                                    Text(buildString {
                                        append(school.name)
                                        school.code?.let { append(" · $it") }
                                    })
                                },
                                onClick = {
                                    viewModel.onSchoolSelected(school)
                                    schoolExpanded = false
                                }
                            )
                        }
                        if (uiState.schoolSuggestions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无已有学校，可直接填写新学校名称") },
                                onClick = { schoolExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.schoolCode,
                    onValueChange = viewModel::onSchoolCodeChange,
                    label = { Text("学校代码（可选）") },
                    placeholder = { Text("可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("导入方案名称") },
                    placeholder = { Text("填写方案名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                GroupPicker(
                    group = uiState.group,
                    expanded = groupExpanded,
                    onExpandedChange = { expanded -> groupExpanded = expanded },
                    onGroupChange = viewModel::onGroupChange
                )

                OutlinedTextField(
                    value = uiState.initial,
                    onValueChange = viewModel::onInitialChange,
                    label = { Text("首字母") },
                    supportingText = { Text("用于搜索，按名称自动填写，可以改") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.startUrl,
                    onValueChange = viewModel::onStartUrlChange,
                    label = { Text("起始网址") },
                    placeholder = { Text("输入教务系统网址") },
                    supportingText = { Text("打开导入页面时先访问这个地址，必须带 http:// 或 https://") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("方案说明（可选）") },
                    supportingText = { Text("写清导入前要做什么，例如“先登录并打开课表页”") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uiState.maintainer,
                    onValueChange = viewModel::onMaintainerChange,
                    label = { Text("维护者（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ScriptSourcePicker(
                    selected = uiState.scriptSource,
                    canReuseFamily = uiState.availableFamilyIds.isNotEmpty(),
                    onSelect = viewModel::onScriptSourceChange
                )

                when (uiState.scriptSource) {
                    ImportPresetScriptSource.WRITTEN -> ScriptSection(
                        uiState = uiState,
                        onPickFile = { pickScriptLauncher.launch(arrayOf("*/*")) },
                        onPaste = {
                            val pasted = clipboardManager.getText()?.text.orEmpty()
                            if (pasted.isBlank()) {
                                viewModel.onScriptUnavailable("剪贴板里没有文本。")
                            } else {
                                viewModel.onScriptLoaded(pasted, sourceLabel = "已从剪贴板粘贴")
                            }
                        },
                        onClear = viewModel::clearScript
                    )

                    ImportPresetScriptSource.FAMILY -> FamilyAssemblySection(
                        uiState = uiState,
                        onFamilySelected = viewModel::onFamilySelected,
                        onConfigChange = viewModel::onConfigChange,
                        onOverrideChange = viewModel::onOverrideChange
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.save(runNow = true) },
                        enabled = !uiState.isSaving && uiState.canSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存并试运行")
                    }
                    Button(
                        onClick = { viewModel.save(runNow = false) },
                        enabled = !uiState.isSaving && uiState.canSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (uiState.isSaving) "保存中…" else "保存")
                    }
                }

                Text(
                    text = "「保存并试运行」会直接进入导入页面，方便边改边试。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPicker(
    group: ImportPresetGroup,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onGroupChange: (ImportPresetGroup) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
                .clickable { onExpandedChange(true) },
            value = group.displayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text("方案类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            ImportPresetGroup.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName()) },
                    onClick = {
                        onGroupChange(option)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

/**
 * Picks where the script comes from.
 *
 * Reusing a cached family is only offered once one exists: a family only ever arrives by importing a
 * source, so on a fresh device the option would be a dead end. It stays visible but disabled, which
 * tells the user the way exists rather than hiding it.
 */
@Composable
private fun ScriptSourcePicker(
    selected: ImportPresetScriptSource,
    canReuseFamily: Boolean,
    onSelect: (ImportPresetScriptSource) -> Unit
) {
    Text(text = "脚本来源", style = MaterialTheme.typography.titleSmall)

    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected == ImportPresetScriptSource.WRITTEN,
            onClick = { onSelect(ImportPresetScriptSource.WRITTEN) }
        )
        Text("自己写完整脚本")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected == ImportPresetScriptSource.FAMILY,
            onClick = { onSelect(ImportPresetScriptSource.FAMILY) },
            enabled = canReuseFamily
        )
        Text(
            text = "用本地缓存的族组装",
            color = if (canReuseFamily) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }

    if (!canReuseFamily) {
        Text(
            text = "本地还没有可复用的族。先在方案列表里「从链接导入源」或「从本地目录导入源」，导入过之后就可以只写与族不同的取值。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The inputs for assembling a script from a cached family.
 *
 * The same pieces a source publishes per school: a family, the values that differ, and an optional
 * override. Only the values that differ have to be written down here, which is the whole point of
 * reusing a family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilyAssemblySection(
    uiState: ImportPresetEditorUiState,
    onFamilySelected: (String) -> Unit,
    onConfigChange: (String) -> Unit,
    onOverrideChange: (String) -> Unit
) {
    var familyExpanded by remember { mutableStateOf(false) }

    Text(text = "脚本", style = MaterialTheme.typography.titleSmall)

    ExposedDropdownMenuBox(
        expanded = familyExpanded,
        onExpandedChange = { familyExpanded = it }
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
                .clickable { familyExpanded = true },
            value = uiState.selectedFamilyId.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("要复用的族") },
            placeholder = { Text("选择一个族") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = familyExpanded) }
        )

        DropdownMenu(
            expanded = familyExpanded,
            onDismissRequest = { familyExpanded = false }
        ) {
            uiState.availableFamilyIds.forEach { familyId ->
                DropdownMenuItem(
                    text = { Text(familyId) },
                    onClick = {
                        onFamilySelected(familyId)
                        familyExpanded = false
                    }
                )
            }
        }
    }

    OutlinedTextField(
        value = uiState.configJson,
        onValueChange = onConfigChange,
        label = { Text("取值（JSON，可留空）") },
        placeholder = { Text("{\"scheduleAction\":\"...\"}") },
        supportingText = { Text("只写与族默认值不同的项；留空表示完全用族默认值") },
        minLines = 3,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = uiState.overrideText,
        onValueChange = onOverrideChange,
        label = { Text("覆盖脚本（可选）") },
        supportingText = { Text("只有某一处逻辑与族不同时才需要；会接在族适配器之后") },
        minLines = 3,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The script, with the two ways of supplying one.
 *
 * Only the beginning is shown. A real adapter is tens of thousands of characters, and a text field lays
 * out everything it is given; the preview exists so the user can recognise the file, not read it.
 */
@Composable
private fun ScriptSection(
    uiState: ImportPresetEditorUiState,
    onPickFile: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit
) {
    Text(text = "脚本", style = MaterialTheme.typography.titleSmall)

    if (uiState.hasScript) {
        if (uiState.scriptSourceLabel.isNotBlank()) {
            Text(
                text = "${uiState.scriptSourceLabel}，共 ${uiState.scriptCharacterCount} 字符",
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedTextField(
            value = uiState.scriptPreview,
            onValueChange = {},
            readOnly = true,
            minLines = 5,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text("内容预览") },
            modifier = Modifier.fillMaxWidth()
        )
        if (uiState.isScriptPreviewTruncated) {
            Text(
                text = "只显示了开头部分，保存的是完整脚本。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Text(
            text = "还没有脚本内容。可以在电脑上写好之后传进手机再选文件，也可以直接粘贴。",
            style = MaterialTheme.typography.bodySmall
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onPickFile, modifier = Modifier.weight(1f)) {
            Text(if (uiState.hasScript) "换一个文件" else "选择 .js 文件")
        }
        OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
            Text(if (uiState.hasScript) "重新粘贴" else "从剪贴板粘贴")
        }
    }

    if (uiState.hasScript) {
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Text("清除脚本")
        }
    }
}

private fun Context.readScriptTextFromUri(uri: Uri): String? = runCatching {
    contentResolver.openInputStream(uri)?.use { stream ->
        ImportScriptTextDecoder.decode(stream.readBytes())
    }
}.getOrNull()
