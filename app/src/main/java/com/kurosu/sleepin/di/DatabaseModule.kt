package com.kurosu.sleepin.di

import android.content.Context
import androidx.room.Room
import com.kurosu.sleepin.data.local.SleepInDatabase
import com.kurosu.sleepin.data.local.dao.CourseDao
import com.kurosu.sleepin.data.local.dao.CourseSessionDao
import com.kurosu.sleepin.data.local.dao.ScheduleDao
import com.kurosu.sleepin.data.local.dao.SchedulePeriodDao
import com.kurosu.sleepin.data.local.dao.TimetableDao

/**
 * Temporary manual DI provider for Room graph.
 * This keeps module boundaries aligned with planned Hilt structure,
 * while avoiding AGP 9 + Hilt plugin compatibility issues in Phase 1.
 */
object DatabaseModule {

    @Volatile
    private var database: SleepInDatabase? = null

    fun provideDatabase(context: Context): SleepInDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                SleepInDatabase::class.java,
                "sleepin.db"
            )
                // During early development schema churn is high.
                .fallbackToDestructiveMigration()
                .build()
                .also { database = it }
        }

    fun provideTimetableDao(database: SleepInDatabase): TimetableDao = database.timetableDao()

    fun provideCourseDao(database: SleepInDatabase): CourseDao = database.courseDao()

    fun provideCourseSessionDao(database: SleepInDatabase): CourseSessionDao = database.courseSessionDao()

    fun provideScheduleDao(database: SleepInDatabase): ScheduleDao = database.scheduleDao()

    fun provideSchedulePeriodDao(database: SleepInDatabase): SchedulePeriodDao = database.schedulePeriodDao()
}
