package com.kurosu.sleepin.di

import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.csv.CsvExporter
import com.kurosu.sleepin.data.csv.CsvImporter
import com.kurosu.sleepin.data.preferences.SettingsPreferenceStore
import com.kurosu.sleepin.data.repository.CourseRepositoryImpl
import com.kurosu.sleepin.data.repository.ScheduleRepositoryImpl
import com.kurosu.sleepin.data.repository.SettingsRepositoryImpl
import com.kurosu.sleepin.data.repository.TimetableRepositoryImpl
import com.kurosu.sleepin.domain.repository.CourseRepository
import com.kurosu.sleepin.domain.repository.ScheduleRepository
import com.kurosu.sleepin.domain.repository.SettingsRepository
import com.kurosu.sleepin.domain.repository.TimetableRepository

/**
 * Temporary manual repository provider.
 * Mirrors future Hilt bindings and keeps call sites stable for later migration.
 */
object RepositoryModule {

    fun provideCsvImporter(): CsvImporter = CsvImporter()

    fun provideCsvExporter(): CsvExporter = CsvExporter()

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
}
