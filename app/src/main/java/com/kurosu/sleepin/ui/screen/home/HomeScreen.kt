package com.kurosu.sleepin.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurosu.sleepin.ui.menu.HomeMenuSheet

/**
 * Phase 1 home screen: empty week-view placeholder + top-right menu entry.
 * The structure is intentionally close to the final planned interaction model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTimetableListClick: () -> Unit,
    onScheduleListClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenuSheet by remember { mutableStateOf(false) }
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("第 ${uiState.selectedWeek} 周", style = MaterialTheme.typography.titleMedium)
                        Text("2026-03-10", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenuSheet = true }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "打开菜单")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "周视图网格将在 Phase 5 实现",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            sheetState = menuSheetState,
            onDismissRequest = { showMenuSheet = false }
        ) {
            HomeMenuSheet(
                selectedTimetable = uiState.selectedTimetable,
                selectedSchedule = uiState.selectedSchedule,
                selectedWeek = uiState.selectedWeek,
                totalWeeks = uiState.totalWeeks,
                onWeekChange = viewModel::onWeekChanged,
                onTimetableListClick = {
                    showMenuSheet = false
                    onTimetableListClick()
                },
                onScheduleListClick = {
                    showMenuSheet = false
                    onScheduleListClick()
                },
                onSettingsClick = {
                    showMenuSheet = false
                    onSettingsClick()
                }
            )
        }
    }
}

