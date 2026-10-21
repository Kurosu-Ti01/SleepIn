package com.kurosu.sleepin.di

import android.content.ContentResolver
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.data.csv.ScheduleCsvExporter
import com.kurosu.sleepin.data.csv.ScheduleCsvImporter
import com.kurosu.sleepin.data.importer.CachedFamilyRepository
import com.kurosu.sleepin.data.importer.CustomImportPresetStore
import com.kurosu.sleepin.data.importer.ImportPayloadParser
import com.kurosu.sleepin.data.importer.ImportPresetFileStore
import com.kurosu.sleepin.data.importer.ImportSourceAssembler
import com.kurosu.sleepin.data.importer.ImportSourceFileStore
import com.kurosu.sleepin.data.importer.MutableImportPresetSource
import com.kurosu.sleepin.data.importer.ImportSourceIndexParser
import com.kurosu.sleepin.data.importer.ImportedSourceImporter
import com.kurosu.sleepin.data.importer.RemoteImportPresetStore
import com.kurosu.sleepin.data.preferences.ImportPresetPreferenceStore
import com.kurosu.sleepin.data.preferences.SettingsPreferenceStore
import com.kurosu.sleepin.data.repository.CourseRepositoryImpl
import com.kurosu.sleepin.data.importer.debugImportPresetSources
import com.kurosu.sleepin.data.repository.ImportPresetRepositoryImpl
import com.kurosu.sleepin.data.repository.ImportSourceRepositoryImpl
import com.kurosu.sleepin.data.repository.SettingsRepositoryImpl
import com.kurosu.sleepin.data.repository.TimetableImportRepositoryImpl
import com.kurosu.sleepin.data.repository.TimetableRepositoryImpl
import com.kurosu.sleepin.data.update.GitHubUpdateRepositoryImpl
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.data.repository.ScheduleRepositoryImpl
import com.kurosu.sleepin.domain.repository.ImportPresetRepository
import com.kurosu.sleepin.domain.repository.ImportSourceRepository
import com.kurosu.sleepin.domain.repository.LocalFamilyRepository
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import com.kurosu.sleepin.domain.repository.SettingsRepository
import com.kurosu.sleepin.domain.repository.TimetableImportRepository
import com.kurosu.sleepin.domain.repository.TimetableRepository
import com.kurosu.sleepin.domain.repository.UpdateRepository
import java.io.File

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

    fun provideImportSourceIndexParser(): ImportSourceIndexParser = ImportSourceIndexParser()

    fun provideImportSourceFileStore(filesDirectory: File): ImportSourceFileStore =
        ImportSourceFileStore(File(filesDirectory, ImportSourceFileStore.DIRECTORY_NAME))

    /**
     * Provides the collection of presets that came from an imported source.
     *
     * Internal because it is handed to two other providers in this module and never leaves the DI object.
     */
    internal fun provideRemoteImportPresetStore(
        dataStore: ImportPresetPreferenceStore,
        fileStore: ImportSourceFileStore
    ): RemoteImportPresetStore = RemoteImportPresetStore(dataStore, fileStore)

    /**
     * Provides the families cached by a source import, for a preset the user writes themselves.
     *
     * Uses the same assembler as an import: a preset built from a cached family and one imported from a
     * source have to be assembled identically.
     */
    fun provideLocalFamilyRepository(
        sourceFileStore: ImportSourceFileStore
    ): LocalFamilyRepository = CachedFamilyRepository(
        sourceFileStore = sourceFileStore,
        assembler = ImportSourceAssembler()
    )

    /**
     * Provides the importer behind both ways of importing a source.
     *
     * The reader (HTTPS or a directory on the device) is chosen per call, but the rules that follow are
     * the same instance of [ImportedSourceImporter] for both.
     */
    internal fun provideImportSourceRepository(
        remotePresets: RemoteImportPresetStore,
        sourceFileStore: ImportSourceFileStore,
        sourceIndexParser: ImportSourceIndexParser,
        contentResolver: ContentResolver
    ): ImportSourceRepository = ImportSourceRepositoryImpl(
        importer = ImportedSourceImporter(
            registry = remotePresets,
            sourceFileStore = sourceFileStore,
            indexParser = sourceIndexParser,
            assembler = ImportSourceAssembler()
        ),
        remotePresets = remotePresets,
        sourceFileStore = sourceFileStore,
        contentResolver = contentResolver
    )

    /** Provides the file store that keeps the scripts of the user's own presets. */
    fun provideImportPresetFileStore(filesDirectory: File): ImportPresetFileStore =
        ImportPresetFileStore(File(filesDirectory, ImportPresetFileStore.DIRECTORY_NAME))

    /** Provides the only writable preset source: the presets the user added themselves. */
    fun provideCustomImportPresetStore(
        dataStore: ImportPresetPreferenceStore,
        fileStore: ImportPresetFileStore
    ): MutableImportPresetSource = CustomImportPresetStore(dataStore = dataStore, fileStore = fileStore)

    /**
     * Provides the reader for import presets.
     *
     * The app ships no presets of its own, so every preset in the list was either imported from a
     * source or written by the user. Imported presets come first and the user's own after them, so a
     * preset the user added replaces an imported one with the same id.
     *
     * Debug builds add a sample source ([debugImportPresetSources]); release builds add nothing,
     * because the sample and its script live in the debug source set only.
     */
    internal fun provideImportPresetRepository(
        customPresets: MutableImportPresetSource,
        remotePresets: RemoteImportPresetStore
    ): ImportPresetRepository = ImportPresetRepositoryImpl(
        // The user's own presets take part in the merge and are also the only writable source, so they
        // appear in both roles.
        sources = listOf(remotePresets) +
            debugImportPresetSources() +
            listOf(customPresets),
        customPresets = customPresets
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
            // Also used to create the schedule template an import may carry from a portal.
            schedulePeriodDao = DatabaseModule.provideSchedulePeriodDao(database),
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
