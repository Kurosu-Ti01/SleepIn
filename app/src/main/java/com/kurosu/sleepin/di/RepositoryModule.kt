package com.kurosu.sleepin.di

import android.content.res.AssetManager
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.data.csv.ScheduleCsvExporter
import com.kurosu.sleepin.data.csv.ScheduleCsvImporter
import com.kurosu.sleepin.data.importer.BundledImportPresetSource
import com.kurosu.sleepin.data.importer.ImportPayloadParser
import com.kurosu.sleepin.data.importer.ImportPresetIndexParser
import com.kurosu.sleepin.data.preferences.SettingsPreferenceStore
import com.kurosu.sleepin.data.repository.CourseRepositoryImpl
import com.kurosu.sleepin.data.repository.ImportPresetRepositoryImpl
import com.kurosu.sleepin.data.repository.ScheduleRepositoryImpl
import com.kurosu.sleepin.data.repository.SettingsRepositoryImpl
import com.kurosu.sleepin.data.repository.TimetableImportRepositoryImpl
import com.kurosu.sleepin.data.repository.TimetableRepositoryImpl
import com.kurosu.sleepin.data.update.GitHubUpdateRepositoryImpl
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.domain.repository.ImportPresetRepository
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import com.kurosu.sleepin.domain.repository.SettingsRepository
import com.kurosu.sleepin.domain.repository.TimetableImportRepository
import com.kurosu.sleepin.domain.repository.TimetableRepository
import com.kurosu.sleepin.domain.repository.UpdateRepository

/**
 * Temporary manual repository provider.
 * Mirrors future Hilt bindings and keeps call sites stable for later migration.
 */
object RepositoryModule {

    fun provideCsvImporter(): CsvImporter = CsvImporter()

    fun provideCsvExporter(): CsvExporter = CsvExporter()

    fun provideScheduleCsvImporter(): ScheduleCsvImporter = ScheduleCsvImporter()

    fun provideScheduleCsvExporter(): ScheduleCsvExporter = ScheduleCsvExporter()

    fun provideImportPayloadParser(): ImportPayloadParser = ImportPayloadParser()

    fun provideImportPresetIndexParser(): ImportPresetIndexParser = ImportPresetIndexParser()

    /**
     * Provides the reader for import presets.
     *
     * Only the bundled source is wired for now. A user's own presets and a downloaded index are added
     * here as additional [com.kurosu.sleepin.data.importer.ImportPresetSource] entries; the repository
     * merges them in list order, so later entries override earlier ones with the same id.
     */
    fun provideImportPresetRepository(
        assets: AssetManager,
        parser: ImportPresetIndexParser
    ): ImportPresetRepository = ImportPresetRepositoryImpl(
        sources = listOf(BundledImportPresetSource(assets = assets, parser = parser))
    )

    /**
     * Provides the transactional writer used by the academic-portal import flow.
     *
     * It is wired directly against DAOs rather than [CourseRepository] because a bulk import must
     * create or retarget a timetable and replace all of its courses within a single transaction.
     */
    fun provideTimetableImportRepository(database: SleepInDatabase): TimetableImportRepository =
        TimetableImportRepositoryImpl(
            database = database,
            timetableDao = DatabaseModule.provideTimetableDao(database),
            scheduleDao = DatabaseModule.provideScheduleDao(database),
            courseDao = DatabaseModule.provideCourseDao(database),
            courseSessionDao = DatabaseModule.provideCourseSessionDao(database)
        )

    fun provideTimetableRepository(database: SleepInDatabase): TimetableRepository =
        TimetableRepositoryImpl(DatabaseModule.provideTimetableDao(database))

    fun provideCourseRepository(database: SleepInDatabase): CourseRepository =
        CourseRepositoryImpl(
            database = database,
            courseDao = DatabaseModule.provideCourseDao(database),
            courseSessionDao = DatabaseModule.provideCourseSessionDao(database)
        )

    fun provideScheduleRepository(database: SleepInDatabase): ScheduleRepository =
        ScheduleRepositoryImpl(
            database = database,
            scheduleDao = DatabaseModule.provideScheduleDao(database),
            schedulePeriodDao = DatabaseModule.provideSchedulePeriodDao(database),
            timetableDao = DatabaseModule.provideTimetableDao(database)
        )

    fun provideSettingsRepository(dataStore: SettingsPreferenceStore): SettingsRepository =
        SettingsRepositoryImpl(dataStore)

    /**
     * Provides GitHub-backed update repository without introducing extra DI frameworks.
     */
    fun provideUpdateRepository(): UpdateRepository = GitHubUpdateRepositoryImpl()
}
