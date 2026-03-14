package com.kurosu.sleepin

import android.app.Application
import com.kurosu.sleepin.di.DatabaseModule
import com.kurosu.sleepin.di.RepositoryModule
import com.kurosu.sleepin.domain.usecase.schedule.DeleteScheduleUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleUsageCountUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase
import com.kurosu.sleepin.domain.usecase.schedule.SeedDefaultScheduleUseCase
import com.kurosu.sleepin.domain.usecase.course.AddCourseUseCase
import com.kurosu.sleepin.domain.usecase.course.CheckConflictUseCase
import com.kurosu.sleepin.domain.usecase.course.DeleteCourseUseCase
import com.kurosu.sleepin.domain.usecase.course.GetCourseDetailUseCase
import com.kurosu.sleepin.domain.usecase.course.GetCoursesForTimetableUseCase
import com.kurosu.sleepin.domain.usecase.course.UpdateCourseUseCase
import com.kurosu.sleepin.domain.usecase.timetable.CreateTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.DeleteTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetActiveTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetableDetailUseCase
import com.kurosu.sleepin.domain.usecase.timetable.GetTimetablesUseCase
import com.kurosu.sleepin.domain.usecase.timetable.SetActiveTimetableUseCase
import com.kurosu.sleepin.domain.usecase.timetable.UpdateTimetableUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point and temporary manual DI container.
 *
 * Why this exists in Phase 2:
 * - Hilt integration is postponed, but screens still need shared use case instances.
 * - Keeping them in `Application` provides a simple app-wide graph without static globals.
 */
class SleepInApplication : Application() {

    // Public read-only handles consumed by navigation + ViewModel factories.
    lateinit var getSchedulesUseCase: GetSchedulesUseCase
        private set
    lateinit var getScheduleDetailUseCase: GetScheduleDetailUseCase
        private set
    lateinit var getScheduleUsageCountUseCase: GetScheduleUsageCountUseCase
        private set
    lateinit var saveScheduleUseCase: SaveScheduleUseCase
        private set
    lateinit var deleteScheduleUseCase: DeleteScheduleUseCase
        private set

    lateinit var getCoursesForTimetableUseCase: GetCoursesForTimetableUseCase
        private set
    lateinit var getCourseDetailUseCase: GetCourseDetailUseCase
        private set
    lateinit var addCourseUseCase: AddCourseUseCase
        private set
    lateinit var updateCourseUseCase: UpdateCourseUseCase
        private set
    lateinit var deleteCourseUseCase: DeleteCourseUseCase
        private set

    lateinit var getTimetablesUseCase: GetTimetablesUseCase
        private set
    lateinit var getActiveTimetableUseCase: GetActiveTimetableUseCase
        private set
    lateinit var getTimetableDetailUseCase: GetTimetableDetailUseCase
        private set
    lateinit var createTimetableUseCase: CreateTimetableUseCase
        private set
    lateinit var updateTimetableUseCase: UpdateTimetableUseCase
        private set
    lateinit var deleteTimetableUseCase: DeleteTimetableUseCase
        private set
    lateinit var setActiveTimetableUseCase: SetActiveTimetableUseCase
        private set

    private lateinit var seedDefaultScheduleUseCase: SeedDefaultScheduleUseCase

    // Dedicated app scope for lightweight startup tasks.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initGraph()
        seedDefaultSchedule()
    }

    /** Builds the small dependency graph needed by the schedule module. */
    private fun initGraph() {
        val database = DatabaseModule.provideDatabase(this)
        val scheduleRepository = RepositoryModule.provideScheduleRepository(database)
        val timetableRepository = RepositoryModule.provideTimetableRepository(database)
        val courseRepository = RepositoryModule.provideCourseRepository(database)
        val checkConflictUseCase = CheckConflictUseCase(courseRepository)

        getSchedulesUseCase = GetSchedulesUseCase(scheduleRepository)
        getScheduleDetailUseCase = GetScheduleDetailUseCase(scheduleRepository)
        getScheduleUsageCountUseCase = GetScheduleUsageCountUseCase(scheduleRepository)
        saveScheduleUseCase = SaveScheduleUseCase(scheduleRepository)
        deleteScheduleUseCase = DeleteScheduleUseCase(scheduleRepository)
        seedDefaultScheduleUseCase = SeedDefaultScheduleUseCase(scheduleRepository)

        getCoursesForTimetableUseCase = GetCoursesForTimetableUseCase(courseRepository)
        getCourseDetailUseCase = GetCourseDetailUseCase(courseRepository)
        addCourseUseCase = AddCourseUseCase(courseRepository, checkConflictUseCase)
        updateCourseUseCase = UpdateCourseUseCase(courseRepository, checkConflictUseCase)
        deleteCourseUseCase = DeleteCourseUseCase(courseRepository)

        getTimetablesUseCase = GetTimetablesUseCase(timetableRepository)
        getActiveTimetableUseCase = GetActiveTimetableUseCase(timetableRepository)
        getTimetableDetailUseCase = GetTimetableDetailUseCase(timetableRepository)
        createTimetableUseCase = CreateTimetableUseCase(timetableRepository)
        updateTimetableUseCase = UpdateTimetableUseCase(timetableRepository)
        deleteTimetableUseCase = DeleteTimetableUseCase(timetableRepository)
        setActiveTimetableUseCase = SetActiveTimetableUseCase(timetableRepository)
    }

    /**
     * Seeds one default schedule template on fresh databases.
     * The use case internally checks row count, so this call is safe on every startup.
     */
    private fun seedDefaultSchedule() {
        appScope.launch {
            seedDefaultScheduleUseCase()
        }
    }
}
