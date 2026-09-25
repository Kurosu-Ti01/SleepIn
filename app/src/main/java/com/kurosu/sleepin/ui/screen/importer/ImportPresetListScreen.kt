package com.kurosu.sleepin.ui.screen.importer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kurosu.sleepin.ui.component.EmptyStatePlaceholder

/**
 * Lets the user pick an import preset, and manage the ones they added themselves.
 *
 * One screen for both because it is one list: picking a preset and editing one differ only in which
 * control is touched, and separating them would mean the user has to know in advance which of two
 * nearly identical screens they want.
 *
 * When nothing matches, the screen does not stop at "no results". The long tail of portals will never
 * be covered by what ships, so an unmatched search is exactly the moment to offer the way out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPresetListScreen(
    onBackClick: () -> Unit,
    onPresetClick: (presetId: String) -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (presetId: String) -> Unit,
    viewModel: ImportPresetListViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<ImportPresetItemUi?>(null) }
    var selectedSchoolId by remember { mutableStateOf<String?>(null) }

    // Runs again whenever the user comes back to this screen, so a preset added or edited in the editor
    // is in the list by the time they see it.
    LaunchedEffect(Unit) { viewModel.reload() }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(selectedSchoolId, uiState.schools) {
        if (selectedSchoolId != null && uiState.schools.none { school -> school.id == selectedSchoolId }) {
            selectedSchoolId = null
        }
    }

    BackHandler(enabled = selectedSchoolId != null) { selectedSchoolId = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedSchoolId == null) "选择学校"
                        else uiState.schools.firstOrNull { it.id == selectedSchoolId }?.name ?: "学校方案"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedSchoolId != null) selectedSchoolId = null else onBackClick()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectedSchoolId != null) "返回学校列表" else "返回"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Filled.Add, contentDescription = "导入自己的脚本")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }
                },
                placeholder = { Text("搜索学校、代码或方案") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                !uiState.hasAnyPreset -> NoPresetsContent(onCreateClick = onCreateClick)

                !uiState.hasVisiblePreset -> NoMatchContent(onCreateClick = onCreateClick)

                else -> if (selectedSchoolId == null) {
                    SchoolDirectory(
                        uiState = uiState,
                        onSchoolClick = { schoolId -> selectedSchoolId = schoolId },
                        onGeneralPresetClick = onPresetClick
                    )
                } else {
                    val selectedSchool = uiState.schools.firstOrNull { it.id == selectedSchoolId }
                    if (selectedSchool == null) {
                        selectedSchoolId = null
                        SchoolDirectory(
                            uiState = uiState,
                            onSchoolClick = { schoolId -> selectedSchoolId = schoolId },
                            onGeneralPresetClick = onPresetClick
                        )
                    } else {
                        SchoolScriptList(
                            school = selectedSchool,
                            uiState = uiState,
                            onPresetClick = onPresetClick,
                            onEditClick = onEditClick,
                            onDeleteRequest = { item -> pendingDelete = item }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除方案") },
            text = { Text("确认删除「${item.name}」吗？脚本文件也会一起删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(item.id)
                        pendingDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SchoolDirectory(
    uiState: ImportPresetListUiState,
    onSchoolClick: (String) -> Unit,
    onGeneralPresetClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.schools.isNotEmpty()) {
            item(key = "header-schools") {
                SectionHeader(title = "学校", subtitle = "选择学校后查看其导入方案")
            }
            items(uiState.schools, key = { school -> "school-${school.id}" }) { school ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSchoolClick(school.id) },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.School, contentDescription = null)
                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(school.name, style = MaterialTheme.typography.titleMedium)
                            val code = school.code?.let { " · $it" }.orEmpty()
                            Text(
                                "${school.scriptCount} 个导入方案$code",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        if (uiState.general.isNotEmpty()) {
            item(key = "header-general") { SectionHeader(title = "通用工具", subtitle = null) }
            items(uiState.general, key = { item -> item.id }) { item ->
                ImportPresetRow(
                    item = item,
                    onClick = { onGeneralPresetClick(item.id) },
                    onEditClick = null,
                    onDeleteRequest = null
                )
            }
        }
    }
}

@Composable
private fun SchoolScriptList(
    school: ImportPresetSchoolUi,
    uiState: ImportPresetListUiState,
    onPresetClick: (String) -> Unit,
    onEditClick: (String) -> Unit,
    onDeleteRequest: (ImportPresetItemUi) -> Unit
) {
    val scripts = remember(uiState, school.id) { uiState.presetsBySchool[school.id].orEmpty() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(scripts, key = { item -> item.id }) { item ->
            ImportPresetRow(
                item = item,
                onClick = { onPresetClick(item.id) },
                onEditClick = if (item.isCustom) ({ onEditClick(item.id) }) else null,
                onDeleteRequest = if (item.isCustom) ({ onDeleteRequest(item) }) else null
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One preset.
 *
 * Tapping the row starts an import with that preset; the menu appears only for presets the user added,
 * because the bundled ones are part of the APK and cannot be changed from here.
 */
@Composable
private fun ImportPresetRow(
    item: ImportPresetItemUi,
    onClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    onDeleteRequest: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (item.maintainer.isNotBlank()) {
                    Text(
                        text = "维护：${item.maintainer}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            if (onEditClick != null && onDeleteRequest != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("修改") },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                menuExpanded = false
                                onDeleteRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Shown when the device has no preset at all, which only a fresh install can be. */
@Composable
private fun NoPresetsContent(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStatePlaceholder(
            icon = Icons.Outlined.Language,
            title = "还没有可用的导入方案",
            subtitle = "目前还没有已验证的学校适配脚本。你可以导入自己编写的脚本。",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCreateClick) { Text("导入自己的脚本") }
    }
}

/**
 * Shown when a search matched nothing.
 *
 * Deliberately not a dead end: this is the moment a user from an uncovered school finds out, and the
 * only useful thing to offer them is the way to add their own.
 */
@Composable
private fun NoMatchContent(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "没有找到匹配的方案", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "如果列表里没有你的学校，可以导入一份适配脚本，或者在应用里调试好之后保存下来。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onCreateClick) { Text("导入自己的脚本") }
    }
}
