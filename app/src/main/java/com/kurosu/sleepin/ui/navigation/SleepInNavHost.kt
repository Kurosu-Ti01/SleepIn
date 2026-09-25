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
import com.kurosu.sleepin.domain.model.AppSettings
import com.kurosu.sleepin.ui.screen.home.HomeScreen
import com.kurosu.sleepin.ui.screen.home.HomeViewModel
import com.kurosu.sleepin.ui.screen.importer.ImportPresetEditorScreen
import com.kurosu.sleepin.ui.screen.importer.ImportPresetEditorViewModel
import com.kurosu.sleepin.ui.screen.importer.ImportPresetListScreen
import com.kurosu.sleepin.ui.screen.importer.ImportPresetListViewModel
import com.kurosu.sleepin.ui.screen.importer.web.ImportWebScreen
import com.kurosu.sleepin.ui.screen.importer.web.ImportWebViewModel
import com.kurosu.sleepin.ui.screen.course.CourseEditorScreen
import com.kurosu.sleepin.ui.screen.course.CourseListScreen
import com.kurosu.sleepin.ui.screen.course.CourseListViewModel
import com.kurosu.sleepin.ui.screen.course.rememberCourseEditorViewModel
import com.kurosu.sleepin.ui.screen.schedule.ScheduleEditorScreen
import com.kurosu.sleepin.ui.screen.schedule.ScheduleListScreen
import com.kurosu.sleepin.ui.screen.schedule.ScheduleListViewModel
import com.kurosu.sleepin.ui.screen.settings.SettingsScreen
import com.kurosu.sleepin.ui.screen.settings.SettingsViewModel
import com.kurosu.sleepin.ui.screen.timetable.TimetableEditorScreen
import com.kurosu.sleepin.ui.screen.timetable.TimetableListScreen
import com.kurosu.sleepin.ui.screen.timetable.TimetableListViewModel
import com.kurosu.sleepin.ui.screen.timetable.rememberTimetableEditorViewModel

/**
 * App-level navigation graph for all top-level screens.
 *
 * DI is manual, so this graph obtains use cases from [SleepInApplication]
 * and passes them into ViewModel factories.
 */
@Composable
fun SleepInNavHost(
    navController: NavHostController,
    appSettings: AppSettings,
    modifier: Modifier = Modifier
) {
    val app = LocalContext.current.applicationContext as SleepInApplication

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            val vm: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    getTimetablesUseCase = app.getTimetablesUseCase,
                    getSchedulesUseCase = app.getSchedulesUseCase,
                    getActiveTimetableUseCase = app.getActiveTimetableUseCase,
                    getCoursesForTimetableUseCase = app.getCoursesForTimetableUseCase,
                    getScheduleDetailUseCase = app.getScheduleDetailUseCase,
                    setActiveTimetableUseCase = app.setActiveTimetableUseCase,
                    observeSettingsUseCase = app.observeSettingsUseCase
                )
            )
            HomeScreen(
                onTimetableListClick = { navController.navigate(Screen.TimetableList.route) },
                onScheduleListClick = { navController.navigate(Screen.ScheduleList.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onEditScheduleClick = { scheduleId ->
                    navController.navigate(Screen.ScheduleEditor.createRoute(scheduleId))
                },
                courseRowHeightDp = appSettings.courseCellHeightDp,
                viewModel = vm
            )
        }

        composable(Screen.TimetableList.route) {
            val vm: TimetableListViewModel = viewModel(
                factory = TimetableListViewModel.factory(
                    getTimetablesUseCase = app.getTimetablesUseCase,
                    getSchedulesUseCase = app.getSchedulesUseCase,
                    setActiveTimetableUseCase = app.setActiveTimetableUseCase,
                    deleteTimetableUseCase = app.deleteTimetableUseCase
                )
            )
            TimetableListScreen(
                onBackClick = { navController.popBackStack() },
                onCreateClick = { navController.navigate(Screen.TimetableEditor.createRoute()) },
                onEditClick = { timetableId ->
                    navController.navigate(Screen.TimetableEditor.createRoute(timetableId))
                },
                onOpenCoursesClick = { timetableId ->
                    navController.navigate(Screen.CourseList.createRoute(timetableId))
                },
                viewModel = vm
            )
        }

        composable(
            route = Screen.CourseList.route,
            arguments = listOf(
                navArgument(Screen.CourseList.ARG_TIMETABLE_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val timetableId = backStackEntry.arguments?.getLong(Screen.CourseList.ARG_TIMETABLE_ID) ?: -1L
            val vm: CourseListViewModel = viewModel(
                key = "course_list_$timetableId",
                factory = CourseListViewModel.factory(
                    timetableId = timetableId,
                    getCoursesForTimetableUseCase = app.getCoursesForTimetableUseCase,
                    deleteCourseUseCase = app.deleteCourseUseCase
                )
            )
            CourseListScreen(
                onBackClick = { navController.popBackStack() },
                onCreateClick = { navController.navigate(Screen.CourseEditor.createRoute(timetableId)) },
                onEditClick = { courseId ->
                    navController.navigate(Screen.CourseEditor.createRoute(timetableId = timetableId, courseId = courseId))
                },
                viewModel = vm
            )
        }

        composable(
            route = Screen.CourseEditor.route,
            arguments = listOf(
                navArgument(Screen.CourseEditor.ARG_TIMETABLE_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(Screen.CourseEditor.ARG_COURSE_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val timetableRaw = backStackEntry.arguments?.getLong(Screen.CourseEditor.ARG_TIMETABLE_ID) ?: -1L
            val courseRaw = backStackEntry.arguments?.getLong(Screen.CourseEditor.ARG_COURSE_ID) ?: -1L
            val courseId = courseRaw.takeIf { it > 0L }

            val vm = rememberCourseEditorViewModel(
                timetableId = timetableRaw,
                courseId = courseId,
                getTimetableDetailUseCase = app.getTimetableDetailUseCase,
                getScheduleDetailUseCase = app.getScheduleDetailUseCase,
                getCourseDetailUseCase = app.getCourseDetailUseCase,
                addCourseUseCase = app.addCourseUseCase,
                updateCourseUseCase = app.updateCourseUseCase
            )
            CourseEditorScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = vm
            )
        }

        composable(
            route = Screen.TimetableEditor.route,
            arguments = listOf(
                navArgument(Screen.TimetableEditor.ARG_TIMETABLE_ID) {
                    type = NavType.LongType
                    // LongType cannot be nullable in nav args, so -1 means create mode.
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getLong(Screen.TimetableEditor.ARG_TIMETABLE_ID) ?: -1L
            val timetableId = raw.takeIf { it > 0L }
            val vm = rememberTimetableEditorViewModel(
                timetableId = timetableId,
                getSchedulesUseCase = app.getSchedulesUseCase,
                getScheduleDetailUseCase = app.getScheduleDetailUseCase,
                getTimetableDetailUseCase = app.getTimetableDetailUseCase,
                createTimetableUseCase = app.createTimetableUseCase,
                deleteTimetableUseCase = app.deleteTimetableUseCase,
                updateTimetableUseCase = app.updateTimetableUseCase,
                importCsvUseCase = app.importCsvUseCase,
                exportCsvUseCase = app.exportCsvUseCase
            )
            TimetableEditorScreen(
                onBackClick = { navController.popBackStack() },
                // Importing from a portal is the other way to fill a brand-new timetable, so it starts
                // from the same screen as CSV import and carries the schedule the form already picked.
                onImportFromPortal = { scheduleId ->
                    navController.navigate(
                        Screen.ImportPresetList.createRoute(scheduleId = scheduleId)
                    )
                },
                viewModel = vm
            )
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
                saveScheduleUseCase = app.saveScheduleUseCase,
                importScheduleCsvUseCase = app.importScheduleCsvUseCase,
                exportScheduleCsvUseCase = app.exportScheduleCsvUseCase
            )
            ScheduleEditorScreen(
                onBackClick = { navController.popBackStack() },
                viewModel = vm
            )
        }

        composable(
            route = Screen.ImportPresetList.route,
            arguments = listOf(
                navArgument(Screen.ImportPresetList.ARG_SCHEDULE_ID) {
                    type = NavType.LongType
                    defaultValue = Screen.ImportWeb.NO_SCHEDULE
                }
            )
        ) { backStackEntry ->
            val scheduleRaw = backStackEntry.arguments
                ?.getLong(Screen.ImportPresetList.ARG_SCHEDULE_ID)
                ?: Screen.ImportWeb.NO_SCHEDULE

            val vm: ImportPresetListViewModel = viewModel(
                factory = ImportPresetListViewModel.factory(
                    getImportPresetsUseCase = app.getImportPresetsUseCase,
                    removeCustomImportPresetUseCase = app.removeCustomImportPresetUseCase
                )
            )
            ImportPresetListScreen(
                onBackClick = { navController.popBackStack() },
                onPresetClick = { presetId ->
                    navController.navigate(
                        Screen.ImportWeb.createRoute(
                            presetId = presetId,
                            scheduleId = scheduleRaw
                        )
                    )
                },
                onCreateClick = { navController.navigate(Screen.ImportPresetEditor.createRoute()) },
                onEditClick = { presetId ->
                    navController.navigate(Screen.ImportPresetEditor.createRoute(presetId))
                },
                viewModel = vm
            )
        }

        composable(
            route = Screen.ImportPresetEditor.route,
            arguments = listOf(
                navArgument(Screen.ImportPresetEditor.ARG_PRESET_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val presetId = backStackEntry.arguments
                ?.getString(Screen.ImportPresetEditor.ARG_PRESET_ID)
                .orEmpty()
                .takeIf { it.isNotEmpty() }

            val vm: ImportPresetEditorViewModel = viewModel(
                key = "import_preset_editor_${presetId.orEmpty()}",
                factory = ImportPresetEditorViewModel.factory(
                    presetId = presetId,
                    getImportPresetsUseCase = app.getImportPresetsUseCase,
                    readImportScriptUseCase = app.readImportScriptUseCase,
                    addCustomImportPresetUseCase = app.addCustomImportPresetUseCase,
                    updateCustomImportPresetUseCase = app.updateCustomImportPresetUseCase
                )
            )
            ImportPresetEditorScreen(
                onBackClick = { navController.popBackStack() },
                onSaved = { savedPresetId, runNow ->
                    if (runNow) {
                        // The editor is replaced rather than pushed onto, so going back from the import
                        // page lands on the list instead of on a form the user has already finished with.
                        navController.navigate(Screen.ImportWeb.createRoute(presetId = savedPresetId)) {
                            popUpTo(Screen.ImportPresetEditor.route) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                viewModel = vm
            )
        }

        composable(
            route = Screen.ImportWeb.route,
            arguments = listOf(
                navArgument(Screen.ImportWeb.ARG_PRESET_ID) {
                    type = NavType.StringType
                },
                navArgument(Screen.ImportWeb.ARG_SCHEDULE_ID) {
                    type = NavType.LongType
                    defaultValue = Screen.ImportWeb.NO_SCHEDULE
                }
            )
        ) { backStackEntry ->
            val presetId = backStackEntry.arguments
                ?.getString(Screen.ImportWeb.ARG_PRESET_ID)
                .orEmpty()
            val scheduleRaw = backStackEntry.arguments
                ?.getLong(Screen.ImportWeb.ARG_SCHEDULE_ID)
                ?: Screen.ImportWeb.NO_SCHEDULE

            val vm: ImportWebViewModel = viewModel(
                key = "import_web_$presetId",
                factory = ImportWebViewModel.factory(
                    presetId = presetId,
                    scheduleId = scheduleRaw.takeIf { it > 0L },
                    getImportPresetsUseCase = app.getImportPresetsUseCase,
                    readImportScriptUseCase = app.readImportScriptUseCase,
                    getSchedulesUseCase = app.getSchedulesUseCase,
                    getTimetablesUseCase = app.getTimetablesUseCase,
                    parseImportSubmissionUseCase = app.parseImportSubmissionUseCase,
                    buildImportPreviewUseCase = app.buildImportPreviewUseCase,
                    commitImportUseCase = app.commitImportUseCase
                )
            )
            ImportWebScreen(
                onBackClick = { navController.popBackStack() },
                // The result is a timetable that has just been made current, so land on Home rather
                // than unwinding to wherever the import was started from. A period mismatch instead
                // continues into the schedule editor, which is the one task the import left undone.
                onFinished = { scheduleIdToFix ->
                    if (scheduleIdToFix == null) {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    } else {
                        navController.navigate(Screen.ScheduleEditor.createRoute(scheduleIdToFix)) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }
                },
                onOpenScheduleList = { navController.navigate(Screen.ScheduleList.route) },
                viewModel = vm
            )
        }

        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(
                    observeSettingsUseCase = app.observeSettingsUseCase,
                    updateSettingsUseCase = app.updateSettingsUseCase,
                    exportSettingsBackupUseCase = app.exportSettingsBackupUseCase,
                    importSettingsBackupUseCase = app.importSettingsBackupUseCase,
                    performUpdateCheckUseCase = app.performUpdateCheckUseCase
                )
            )
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToImportPresets = {
                    navController.navigate(Screen.ImportPresetList.createRoute())
                },
                viewModel = vm
            )
        }
    }
}
