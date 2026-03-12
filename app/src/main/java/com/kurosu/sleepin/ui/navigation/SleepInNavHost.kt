package com.kurosu.sleepin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kurosu.sleepin.SleepInApplication
import com.kurosu.sleepin.ui.screen.home.HomeScreen
import com.kurosu.sleepin.ui.screen.schedule.ScheduleEditorScreen
import com.kurosu.sleepin.ui.screen.schedule.ScheduleListScreen
import com.kurosu.sleepin.ui.screen.schedule.ScheduleListViewModel
import com.kurosu.sleepin.ui.screen.settings.SettingsScreen
import com.kurosu.sleepin.ui.screen.timetable.TimetableListScreen

/**
 * App-level navigation graph for all top-level screens.
 *
 * DI is still manual in this phase, so this graph obtains use cases from [SleepInApplication]
 * and passes them into ViewModel factories.
 */
@Composable
fun SleepInNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as SleepInApplication

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onTimetableListClick = { navController.navigate(Screen.TimetableList.route) },
                onScheduleListClick = { navController.navigate(Screen.ScheduleList.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.TimetableList.route) {
            TimetableListScreen(onBackClick = { navController.popBackStack() })
        }

        // Schedule list route: create ViewModel using app-scoped use cases.
        composable(Screen.ScheduleList.route) {
            val vm: ScheduleListViewModel = viewModel(
                factory = ScheduleListViewModel.factory(
                    getSchedulesUseCase = app.getSchedulesUseCase,
                    getScheduleDetailUseCase = app.getScheduleDetailUseCase,
                    getScheduleUsageCountUseCase = app.getScheduleUsageCountUseCase,
                    deleteScheduleUseCase = app.deleteScheduleUseCase
                )
            )
            ScheduleListScreen(
                onBackClick = { navController.popBackStack() },
                onCreateClick = { navController.navigate(Screen.ScheduleEditor.createRoute()) },
                onEditClick = { scheduleId ->
                    navController.navigate(Screen.ScheduleEditor.createRoute(scheduleId))
                },
                viewModel = vm
            )
        }

        // Editor accepts optional scheduleId to support both create and edit.
        composable(
            route = Screen.ScheduleEditor.route,
            arguments = listOf(
                navArgument(Screen.ScheduleEditor.ARG_SCHEDULE_ID) {
                    type = NavType.LongType
                    // LongType is non-nullable in Navigation; use sentinel default for create mode.
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getLong(Screen.ScheduleEditor.ARG_SCHEDULE_ID) ?: -1L
            val scheduleId = raw.takeIf { it > 0L }
            val vm = com.kurosu.sleepin.ui.screen.schedule.rememberScheduleEditorViewModel(
                scheduleId = scheduleId,
                getScheduleDetailUseCase = app.getScheduleDetailUseCase,
                saveScheduleUseCase = app.saveScheduleUseCase
            )
            ScheduleEditorScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = vm
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
