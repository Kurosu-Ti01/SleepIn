package com.kurosu.sleepin

import android.app.Application
import androidx.room.InvalidationTracker
import androidx.glance.appwidget.updateAll
import com.kurosu.sleepin.data.preferences.importPresetDataStore
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
import com.kurosu.sleepin.domain.usecase.importer.AddCustomImportPresetUseCase
import com.kurosu.sleepin.domain.usecase.importer.BuildImportPreviewUseCase
import com.kurosu.sleepin.domain.usecase.importer.ChangeImportSourceUrlUseCase
import com.kurosu.sleepin.domain.usecase.importer.CommitImportUseCase
import com.kurosu.sleepin.domain.usecase.importer.GetImportPresetsUseCase
import com.kurosu.sleepin.domain.usecase.importer.GetRegisteredImportSourcesUseCase
import com.kurosu.sleepin.domain.usecase.importer.ForgetImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.ParseImportSubmissionUseCase
import com.kurosu.sleepin.domain.usecase.importer.ReadImportScriptUseCase
import com.kurosu.sleepin.domain.usecase.importer.RemoveCustomImportPresetUseCase
import com.kurosu.sleepin.domain.usecase.importer.ReuseLocalFamilyUseCase
import com.kurosu.sleepin.domain.usecase.importer.RefreshImportSourcesUseCase
import com.kurosu.sleepin.domain.usecase.importer.RenameImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.ImportSourceUseCase
import com.kurosu.sleepin.domain.usecase.importer.UpdateCustomImportPresetUseCase
import com.kurosu.sleepin.domain.usecase.settings.ExportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ImportSettingsBackupUseCase
import com.kurosu.sleepin.domain.usecase.settings.ObserveSettingsUseCase
import com.kurosu.sleepin.domain.usecase.settings.PerformUpdateCheckUseCase
import com.kurosu.sleepin.domain.usecase.settings.UpdateSettingsUseCase
import com.kurosu.sleepin.reminder.CourseReminderScheduler
import com.kurosu.sleepin.importer.ImportSourceRefreshScheduler
import com.kurosu.sleepin.update.UpdateCheckScheduler
import com.kurosu.sleepin.widget.TodayWidget
import com.kurosu.sleepin.widget.WidgetRefreshScheduler
import com.kurosu.sleepin.widget.WeekWidget
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

    lateinit var parseImportSubmissionUseCase: ParseImportSubmissionUseCase
        private set
    lateinit var buildImportPreviewUseCase: BuildImportPreviewUseCase
        private set
    lateinit var commitImportUseCase: CommitImportUseCase
        private set

    lateinit var getImportPresetsUseCase: GetImportPresetsUseCase
        private set
    lateinit var readImportScriptUseCase: ReadImportScriptUseCase
        private set
    lateinit var addCustomImportPresetUseCase: AddCustomImportPresetUseCase
        private set
    lateinit var removeCustomImportPresetUseCase: RemoveCustomImportPresetUseCase
        private set
    lateinit var updateCustomImportPresetUseCase: UpdateCustomImportPresetUseCase
        private set
    lateinit var reuseLocalFamilyUseCase: ReuseLocalFamilyUseCase
        private set
    lateinit var importSourceUseCase: ImportSourceUseCase
        private set
    lateinit var refreshImportSourcesUseCase: RefreshImportSourcesUseCase
        private set
    lateinit var renameImportSourceUseCase: RenameImportSourceUseCase
        private set
    lateinit var changeImportSourceUrlUseCase: ChangeImportSourceUrlUseCase
        private set
    lateinit var getRegisteredImportSourcesUseCase: GetRegisteredImportSourcesUseCase
        private set
    lateinit var forgetImportSourceUseCase: ForgetImportSourceUseCase
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
        observeImportSourceRefreshScheduling()
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
        val importPayloadParser = RepositoryModule.provideImportPayloadParser()
        val timetableImportRepository = RepositoryModule.provideTimetableImportRepository(database)
        val importSourceIndexParser = RepositoryModule.provideImportSourceIndexParser()
        val importSourceFileStore = RepositoryModule.provideImportSourceFileStore(filesDir)
        val remoteImportPresetStore = RepositoryModule.provideRemoteImportPresetStore(
            dataStore = importPresetDataStore,
            fileStore = importSourceFileStore
        )
        val importSourceRepository = RepositoryModule.provideImportSourceRepository(
            remotePresets = remoteImportPresetStore,
            sourceFileStore = importSourceFileStore,
            sourceIndexParser = importSourceIndexParser,
            contentResolver = contentResolver
        )
        val localFamilyRepository = RepositoryModule.provideLocalFamilyRepository(importSourceFileStore)
        val importPresetFileStore = RepositoryModule.provideImportPresetFileStore(filesDir)
        val customImportPresetStore = RepositoryModule.provideCustomImportPresetStore(
            dataStore = importPresetDataStore,
            fileStore = importPresetFileStore
        )
        val importPresetRepository = RepositoryModule.provideImportPresetRepository(
            customPresets = customImportPresetStore,
            remotePresets = remoteImportPresetStore
        )

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

        // Academic-portal import: parse a script payload, then persist it in one transaction.
        parseImportSubmissionUseCase = ParseImportSubmissionUseCase(importPayloadParser)
        // The preview and the write resolve their target through the same resolver, so the summary the
        // user confirms is the import that gets stored.
        buildImportPreviewUseCase = BuildImportPreviewUseCase(
            getTimetableDetailUseCase = getTimetableDetailUseCase,
            getScheduleDetailUseCase = getScheduleDetailUseCase
        )
        commitImportUseCase = CommitImportUseCase(
            timetableImportRepository = timetableImportRepository,
            getTimetableDetailUseCase = getTimetableDetailUseCase
        )
        getImportPresetsUseCase = GetImportPresetsUseCase(importPresetRepository)
        readImportScriptUseCase = ReadImportScriptUseCase(importPresetRepository)
        addCustomImportPresetUseCase = AddCustomImportPresetUseCase(importPresetRepository)
        removeCustomImportPresetUseCase = RemoveCustomImportPresetUseCase(importPresetRepository)
        updateCustomImportPresetUseCase = UpdateCustomImportPresetUseCase(importPresetRepository)
        reuseLocalFamilyUseCase = ReuseLocalFamilyUseCase(localFamilyRepository)
        importSourceUseCase = ImportSourceUseCase(importSourceRepository)
        changeImportSourceUrlUseCase = ChangeImportSourceUrlUseCase(importSourceRepository)
        getRegisteredImportSourcesUseCase = GetRegisteredImportSourcesUseCase(importSourceRepository)
        forgetImportSourceUseCase = ForgetImportSourceUseCase(importSourceRepository)
        refreshImportSourcesUseCase = RefreshImportSourcesUseCase(
            settingsRepository = settingsRepository,
            importSourceRepository = importSourceRepository
        )
        renameImportSourceUseCase = RenameImportSourceUseCase(importSourceRepository)
    }

    /**
     * Sets up periodic refresh so widgets keep updating even when no app screen is open.
     */
    private fun scheduleWidgetRefreshPipeline() {
        WidgetRefreshScheduler.schedulePeriodic(this)
        refreshWidgetsNow()
    }

    /**
     * Runs an in-process widget refresh immediately.
     *
     * Compared with WorkManager one-time work, this path updates launcher widgets in the same
     * process tick right after local database writes, so timetable switch and course edits feel
     * instant to users.
     */
    private fun refreshWidgetsNow() {
        appScope.launch {
            TodayWidget.updateAll(this@SleepInApplication)
            WeekWidget.updateAll(this@SleepInApplication)
        }
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
     * Keeps the built-in preset source fresh: one attempt per launch, then on the configured schedule.
     *
     * The launch attempt is what makes a fresh install usable — the app ships no presets, so an empty
     * list with nothing to explain it would be the first thing a new user sees. The interval is part of
     * the observed key so that changing it re-registers the periodic work instead of leaving the old
     * cadence in place.
     */
    private fun observeImportSourceRefreshScheduling() {
        appScope.launch {
            observeSettingsUseCase()
                .map { settings ->
                    settings.importSourceAutoRefreshEnabled to settings.importSourceRefreshIntervalDays
                }
                .distinctUntilChanged()
                .collect { (enabled, intervalDays) ->
                    ImportSourceRefreshScheduler.syncPeriodic(
                        context = this@SleepInApplication,
                        enabled = enabled,
                        intervalDays = intervalDays
                    )
                    if (enabled) {
                        ImportSourceRefreshScheduler.requestLaunchRefresh(this@SleepInApplication)
                    }
                }
        }
    }

    /**
     * Keeps class reminder workers and exact alarms aligned with reminder preference changes.
     */
    private fun observeClassReminderScheduling() {
        appScope.launch {
            observeSettingsUseCase()
                .map { it.notificationsEnabled to it.reminderMinutes }
                .distinctUntilChanged()
                .collect { (enabled, _) ->
                    CourseReminderScheduler.syncPeriodic(this@SleepInApplication, enabled)
                    if (enabled) {
                        CourseReminderScheduler.scheduleNextExactAlarm(this@SleepInApplication)
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
                    refreshWidgetsNow()
                    // Recompute exact alarm immediately so edited classes keep strict trigger timing.
                    appScope.launch {
                        CourseReminderScheduler.scheduleNextExactAlarm(this@SleepInApplication)
                    }
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
