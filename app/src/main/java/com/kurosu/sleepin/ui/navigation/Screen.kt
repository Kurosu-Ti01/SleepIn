package com.kurosu.sleepin.ui.navigation

/**
 * Central route definitions for Compose Navigation.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object TimetableList : Screen("timetable_list")
    data object TimetableEditor : Screen("timetable_editor?timetableId={timetableId}") {
        const val ARG_TIMETABLE_ID = "timetableId"
        const val BASE_ROUTE = "timetable_editor"

        fun createRoute(timetableId: Long? = null): String =
            if (timetableId == null) BASE_ROUTE else "$BASE_ROUTE?$ARG_TIMETABLE_ID=$timetableId"
    }
    data object ScheduleList : Screen("schedule_list")
    data object ScheduleEditor : Screen("schedule_editor?scheduleId={scheduleId}") {
        const val ARG_SCHEDULE_ID = "scheduleId"
        const val BASE_ROUTE = "schedule_editor"

        fun createRoute(scheduleId: Long? = null): String =
            if (scheduleId == null) BASE_ROUTE else "$BASE_ROUTE?$ARG_SCHEDULE_ID=$scheduleId"
    }
    data object Settings : Screen("settings")
}
