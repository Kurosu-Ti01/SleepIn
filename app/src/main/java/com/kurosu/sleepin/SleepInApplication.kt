package com.kurosu.sleepin

import android.app.Application
import androidx.room.InvalidationTracker
import com.kurosu.sleepin.data.preferences.settingsDataStore
import com.kurosu.sleepin.di.DatabaseModule
import com.kurosu.sleepin.di.RepositoryModule
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.domain.usecase.schedule.DeleteScheduleUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleDetailUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetScheduleUsageCountUseCase
import com.kurosu.sleepin.domain.usecase.schedule.GetSchedulesUseCase
import com.kurosu.sleepin.domain.usecase.schedule.SaveScheduleUseCase
import com.kurosu.sleepin.domain.usecase.schedule.SeedDefaultScheduleUseCase
import com.kurosu.sleepin.domain.usecase.schedule.ImportScheduleCsvUseCase
import com.kurosu.sleepin.domain.usecase.schedule.ExportScheduleCsvUseCase
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
import com.kurosu.sleepin.domain.usecase.csv.ExportCsvUseCase
import com.kurosu.sleepin.domain.usecase.csv.ImportCsvUseCase
import com.kurosu.sleepin.domain.usecase.settings.ExportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ImportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ObserveSettingsUseCase
import com.kurosu.sleepin.domain.usecase.settings.PerformUpdateCheckUseCase
import com.kurosu.sleepin.domain.usecase.settings.UpdateSettingsUseCase
import com.kurosu.sleepin.reminder.CourseReminderScheduler
import com.kurosu.sleepin.update.UpdateCheckScheduler
import com.kurosu.sleepin.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application entry point and temporary manual DI container.
 *
 * Why this exists in Phase 2:
 * - Hilt integration is postponed, but screens still need shared use case instances.
 * - Keeping them in `Application` provides a simple app-wide graph without static globals.
 */
class SleepInApplication : Application() {

    private lateinit var database: SleepInDatabase

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
    lateinit var importScheduleCsvUseCase: ImportScheduleCsvUseCase
        private set
    lateinit var exportScheduleCsvUseCase: ExportScheduleCsvUseCase
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

    lateinit var observeSettingsUseCase: ObserveSettingsUseCase
        private set
    lateinit var updateSettingsUseCase: UpdateSettingsUseCase
        private set
    lateinit var exportSettingsBackupUseCase: ExportSettingsBackupUseCase
        private set
    lateinit var importSettingsBackupUseCase: ImportSettingsBackupUseCase
        private set
    lateinit var performUpdateCheckUseCase: PerformUpdateCheckUseCase
        private set

    lateinit var importCsvUseCase: ImportCsvUseCase
        private set
    lateinit var exportCsvUseCase: ExportCsvUseCase
        private set

    private lateinit var seedDefaultScheduleUseCase: SeedDefaultScheduleUseCase

    // Dedicated app scope for lightweight startup tasks.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initGraph()
        seedDefaultSchedule()
        scheduleWidgetRefreshPipeline()
        observeAutoUpdateScheduling()
        observeClassReminderScheduling()
        registerWidgetRefreshOnDatabaseChanges()
    }

    /** Builds the small dependency graph needed by the schedule module. */
    private fun initGraph() {
        database = DatabaseModule.provideDatabase(this)
        val scheduleRepository = RepositoryModule.provideScheduleRepository(database)
        val timetableRepository = RepositoryModule.provideTimetableRepository(database)
        val courseRepository = RepositoryModule.provideCourseRepository(database)
        val checkConflictUseCase = CheckConflictUseCase(courseRepository)
        val settingsRepository = RepositoryModule.provideSettingsRepository(settingsDataStore)
        val updateRepository = RepositoryModule.provideUpdateRepository()
        val csvImporter = RepositoryModule.provideCsvImporter()
        val csvExporter = RepositoryModule.provideCsvExporter()
        val scheduleCsvImporter = RepositoryModule.provideScheduleCsvImporter()
        val scheduleCsvExporter = RepositoryModule.provideScheduleCsvExporter()

        getSchedulesUseCase = GetSchedulesUseCase(scheduleRepository)
        getScheduleDetailUseCase = GetScheduleDetailUseCase(scheduleRepository)
        getScheduleUsageCountUseCase = GetScheduleUsageCountUseCase(scheduleRepository)
        saveScheduleUseCase = SaveScheduleUseCase(scheduleRepository)
        deleteScheduleUseCase = DeleteScheduleUseCase(scheduleRepository)
        seedDefaultScheduleUseCase = SeedDefaultScheduleUseCase(scheduleRepository)
        importScheduleCsvUseCase = ImportScheduleCsvUseCase(
            importer = scheduleCsvImporter,
            saveScheduleUseCase = saveScheduleUseCase
        )
        exportScheduleCsvUseCase = ExportScheduleCsvUseCase(
            getScheduleDetailUseCase = getScheduleDetailUseCase,
            exporter = scheduleCsvExporter
        )

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

        observeSettingsUseCase = ObserveSettingsUseCase(settingsRepository)
        updateSettingsUseCase = UpdateSettingsUseCase(settingsRepository)
        exportSettingsBackupUseCase = ExportSettingsBackupUseCase(settingsRepository)
        importSettingsBackupUseCase = ImportSettingsBackupUseCase(settingsRepository)
        performUpdateCheckUseCase = PerformUpdateCheckUseCase(
            settingsRepository = settingsRepository,
            updateRepository = updateRepository
        )

        importCsvUseCase = ImportCsvUseCase(csvImporter, courseRepository)
        exportCsvUseCase = ExportCsvUseCase(courseRepository, timetableRepository, csvExporter)
    }

    /**
     * Sets up periodic refresh so widgets keep updating even when no app screen is open.
     */
    private fun scheduleWidgetRefreshPipeline() {
        WidgetRefreshScheduler.schedulePeriodic(this)
        WidgetRefreshScheduler.requestImmediateUpdate(this)
    }

    /**
     * Keeps periodic update checks aligned with user preference.
     */
    private fun observeAutoUpdateScheduling() {
        appScope.launch {
            observeSettingsUseCase()
                .map { it.autoCheckUpdateEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    UpdateCheckScheduler.syncPeriodic(this@SleepInApplication, enabled)
                    if (enabled) {
                        UpdateCheckScheduler.requestImmediateCheck(this@SleepInApplication, force = false)
                    }
                }
        }
    }

    /**
     * Keeps class reminder workers aligned with reminder preference changes.
     */
    private fun observeClassReminderScheduling() {
        appScope.launch {
            observeSettingsUseCase()
                .map { it.notificationsEnabled to it.reminderMinutes }
                .distinctUntilChanged()
                .collect { (enabled, _) ->
                    CourseReminderScheduler.syncPeriodic(this@SleepInApplication, enabled)
                    if (enabled) {
                        CourseReminderScheduler.requestImmediateCheck(this@SleepInApplication)
                    }
                }
        }
    }

    /**
     * Subscribes to Room invalidation callbacks and requests immediate widget refreshes.
     *
     * This keeps widget data fresh after any repository write without touching repository code.
     */
    private fun registerWidgetRefreshOnDatabaseChanges() {
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(
                "timetables",
                "courses",
                "course_sessions",
                "schedules",
                "schedule_periods"
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    WidgetRefreshScheduler.requestImmediateUpdate(this@SleepInApplication)
                    CourseReminderScheduler.requestImmediateCheck(this@SleepInApplication)
                }
            }
        )
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
