package com.kurosu.sleepin.ui.screen.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions

/**
 * The preset sources this device has imported.
 *
 * A page of its own rather than a block in settings: every row carries its own actions, one of them
 * destructive, and the addresses are long enough to need the width.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSourceListScreen(
    onBackClick: () -> Unit,
    viewModel: ImportSourceListViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddMenu by remember { mutableStateOf(false) }

    // A directory rather than a single file: the runner and family files an index refers to sit beside it.
    // The grant is made persistable so later refreshes can still read it.
    val pickSourceDirectory = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onLocalDirectoryPicked(uri.toString())
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("方案源") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "intro") {
                    Text(
                        text = "SleepIn 不自带导入方案，所有学校导入方案自下列源读取。" +
                            "内置源固定不可改；自己添加的源可以改地址、重命名或删除（删除会让它带来的方案一起消失）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(uiState.sources, key = { row -> "source-${row.sourceKey}" }) { row ->
                    SourceCard(
                        row = row,
                        onEditClick = { viewModel.onEditRequested(row) },
                        onRenameClick = { viewModel.onRenameRequested(row) },
                        onDeleteClick = { viewModel.onDeleteRequested(row) }
                    )
                }
            }

            // Adding a source belongs on the page that lists them, not only on the preset list: that is
            // where a user who is here to manage sources would look for it.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加方案源")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("从链接添加") },
                        onClick = {
                            showAddMenu = false
                            viewModel.onAddRemoteRequested()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("从本地目录添加") },
                        onClick = {
                            showAddMenu = false
                            pickSourceDirectory.launch(null)
                        }
                    )
                }
            }
        }
    }

    uiState.editor?.let { editor ->
        SourceAddressDialog(
            editor = editor,
            onUrlChange = viewModel::onUrlChange,
            onDismiss = viewModel::dismissEditor,
            onConfirm = viewModel::confirmEditor
        )
    }

    uiState.rename?.let { rename ->
        RenameSourceDialog(
            rename = rename,
            onNameChange = viewModel::onRenameChange,
            onDismiss = viewModel::dismissRename,
            onConfirm = viewModel::confirmRename
        )
    }

    uiState.addRemote?.let { add ->
        AddRemoteSourceDialog(
            add = add,
            onUrlChange = viewModel::onAddUrlChange,
            onReuseChange = viewModel::onAddReuseLocalFamiliesChange,
            onDismiss = viewModel::dismissAddRemote,
            onConfirm = viewModel::confirmAddRemote
        )
    }

    uiState.addLocal?.let { add ->
        AddLocalSourceDialog(
            add = add,
            onReuseChange = viewModel::onAddReuseLocalFamiliesChange,
            onDismiss = viewModel::dismissAddLocal,
            onConfirm = viewModel::confirmAddLocal
        )
    }

    uiState.pendingDelete?.let { row ->
        DeleteSourceDialog(
            row = row,
            onDismiss = viewModel::dismissDelete,
            onConfirm = viewModel::confirmDelete
        )
    }
}

@Composable
private fun SourceCard(
    row: ImportSourceRowUi,
    onEditClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The icon answers "where does this come from": a directory on the device or a link.
                Icon(
                    imageVector = if (row.isLocal) Icons.Outlined.Folder else Icons.Outlined.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = row.sourceKey,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = if (row.presetCount > 0) {
                    "${row.presetCount} 个方案 · 索引版本 ${row.indexVersion}"
                } else {
                    "尚未拉取到内容"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.isManaged) {
                    // Read-only on purpose: a mistyped built-in address would leave the app with no source it
                    // can rely on, and there is no default to restore to. Adding a source of one's own is the
                    // supported way to point the app somewhere else.
                    Text(
                        text = "默认内置方案源",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TextButton(onClick = onEditClick) { Text("修改地址") }
                    TextButton(onClick = onRenameClick) { Text("重命名") }
                    TextButton(onClick = onDeleteClick) {
                        Text(text = "删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * Edits one source address.
 *
 * The dialog stays open when the new address fails, because a rejected address is almost always a typo and
 * closing the editor would make the user start over.
 */
@Composable
private fun SourceAddressDialog(
    editor: ImportSourceEditorUi,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val error = editor.error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改方案源地址") },
        text = {
            Column {
                Text(
                    text = "填入源索引（source-index.json）的 HTTPS 地址。保存后会立即导入新地址；" +
                        "导入失败则保留原来的源。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = editor.urlText,
                    onValueChange = onUrlChange,
                    label = { Text("源索引地址") },
                    placeholder = { Text("https://…/source-index.json") },
                    singleLine = true,
                    isError = error != null,
                    enabled = !editor.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !editor.isSaving) {
                Text(if (editor.isSaving) "导入中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun RenameSourceDialog(
    rename: ImportSourceRenameUi,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名方案源") },
        text = {
            Column {
                Text(
                    text = "名字只用于本机显示。留空则恢复成根据地址生成的名称。",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = rename.nameText,
                    onValueChange = onNameChange,
                    label = { Text("名称") },
                    singleLine = true,
                    enabled = !rename.isSaving,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !rename.isSaving) {
                Text(if (rename.isSaving) "保存中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun DeleteSourceDialog(
    row: ImportSourceRowUi,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除方案源") },
        text = {
            Text(
                text = if (row.presetCount > 0) {
                    "删除后它带来的 ${row.presetCount} 个方案会从列表里消失，缓存的脚本也会一起删掉。\n\n" +
                        row.sourceKey
                } else {
                    "这个源还没有拉取到任何方案，删除只影响登记记录。\n\n${row.sourceKey}"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * Adds a source by its index address.
 *
 * The same two switches as the import flow uses, because a source added here and one added while picking
 * a preset end up doing exactly the same thing.
 */
@Composable
private fun AddRemoteSourceDialog(
    add: ImportSourceAddRemoteUi,
    onUrlChange: (String) -> Unit,
    onReuseChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val error = add.error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从链接添加方案源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = add.urlText,
                    onValueChange = onUrlChange,
                    label = { Text("源索引链接") },
                    placeholder = { Text("https://…/source-index.json") },
                    singleLine = true,
                    isError = error != null,
                    enabled = !add.isSaving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = add.reuseLocalFamilies,
                        onCheckedChange = onReuseChange,
                        enabled = !add.isSaving
                    )
                    Text("优先复用本地已缓存的族")
                }
                Text(
                    text = "关闭后会使用源内族文件，并覆盖同名本地族缓存。最终脚本仍由本机生成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !add.isSaving && add.urlText.isNotBlank()) {
                Text(if (add.isSaving) "导入中…" else "导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** Confirms a directory the user picked, before anything is read from it. */
@Composable
private fun AddLocalSourceDialog(
    add: ImportSourceAddLocalUi,
    onReuseChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val error = add.error
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从本地目录添加方案源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "所选目录里需要有 source-index.json，以及它引用的 runner 与族文件。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = add.rootUri,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = add.reuseLocalFamilies,
                        onCheckedChange = onReuseChange,
                        enabled = !add.isSaving
                    )
                    Text("优先复用本地已缓存的族")
                }
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !add.isSaving) {
                Text(if (add.isSaving) "导入中…" else "导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
