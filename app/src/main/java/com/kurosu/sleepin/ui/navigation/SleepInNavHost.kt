package com.kurosu.sleepin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kurosu.sleepin.ui.screen.home.HomeScreen
import com.kurosu.sleepin.ui.screen.schedule.ScheduleListScreen
import com.kurosu.sleepin.ui.screen.settings.SettingsScreen
import com.kurosu.sleepin.ui.screen.timetable.TimetableListScreen

/**
 * App-level navigation graph. Phase 1 keeps feature screens as placeholders.
 */
@Composable
fun SleepInNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
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
        composable(Screen.ScheduleList.route) {
            ScheduleListScreen(onBackClick = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBackClick = { navController.popBackStack() })
        }
    }
}

