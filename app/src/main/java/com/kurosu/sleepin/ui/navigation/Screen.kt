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
    data object CourseList : Screen("course_list/{timetableId}") {
        const val ARG_TIMETABLE_ID = "timetableId"

        fun createRoute(timetableId: Long): String = "course_list/$timetableId"
    }
    data object CourseEditor : Screen("course_editor?timetableId={timetableId}&courseId={courseId}") {
        const val ARG_TIMETABLE_ID = "timetableId"
        const val ARG_COURSE_ID = "courseId"
        const val BASE_ROUTE = "course_editor"

        fun createRoute(timetableId: Long, courseId: Long? = null): String {
            val resolvedCourseId = courseId ?: -1L
            return "$BASE_ROUTE?$ARG_TIMETABLE_ID=$timetableId&$ARG_COURSE_ID=$resolvedCourseId"
        }
    }
    data object Settings : Screen("settings")

    /**
     * Runs an import script inside a WebView.
     *
     * The schedule is part of the route because linking the result to a class-time template is a
     * decision the user makes before the script runs, and the app never writes schedule periods
     * itself. Whether the import creates a timetable or replaces an existing one is *not* on the route:
     * it is the first thing the import page asks, so there is one place that decides it.
     */
    data object ImportWeb : Screen("import_web/{presetId}?scheduleId={scheduleId}") {
        const val ARG_PRESET_ID = "presetId"
        const val ARG_SCHEDULE_ID = "scheduleId"
        const val BASE_ROUTE = "import_web"

        /** Sentinel for "no schedule chosen yet"; the screen asks for one before starting. */
        const val NO_SCHEDULE = -1L

        fun createRoute(
            presetId: String,
            scheduleId: Long = NO_SCHEDULE
        ): String = "$BASE_ROUTE/$presetId?$ARG_SCHEDULE_ID=$scheduleId"
    }

    /**
     * Picks the preset to import with, and is also where the user manages the ones they added.
     *
     * The schedule is carried only to be forwarded to [ImportWeb]: entry points that already have one
     * supply it, and the entry that does not leaves it at its default.
     */
    data object ImportPresetList : Screen(
        "import_preset_list?scheduleId={scheduleId}"
    ) {
        const val ARG_SCHEDULE_ID = "scheduleId"
        const val BASE_ROUTE = "import_preset_list"

        fun createRoute(scheduleId: Long = ImportWeb.NO_SCHEDULE): String =
            "$BASE_ROUTE?$ARG_SCHEDULE_ID=$scheduleId"
    }

    /** Creates or changes one of the user's own presets. No id means create. */
    data object ImportPresetEditor : Screen("import_preset_editor?presetId={presetId}") {
        const val ARG_PRESET_ID = "presetId"
        const val BASE_ROUTE = "import_preset_editor"

        fun createRoute(presetId: String? = null): String =
            if (presetId == null) BASE_ROUTE else "$BASE_ROUTE?$ARG_PRESET_ID=$presetId"
    }
}
