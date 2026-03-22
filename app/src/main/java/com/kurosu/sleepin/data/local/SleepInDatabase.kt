package com.kurosu.sleepin.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kurosu.sleepin.data.local.converter.Converters
import com.kurosu.sleepin.data.local.dao.CourseDao
import com.kurosu.sleepin.data.local.dao.CourseSessionDao
import com.kurosu.sleepin.data.local.dao.ScheduleDao
import com.kurosu.sleepin.data.local.dao.SchedulePeriodDao
import com.kurosu.sleepin.data.local.dao.TimetableDao
import com.kurosu.sleepin.data.local.entity.CourseEntity
import com.kurosu.sleepin.data.local.entity.CourseSessionEntity
import com.kurosu.sleepin.data.local.entity.ScheduleEntity
import com.kurosu.sleepin.data.local.entity.SchedulePeriodEntity
import com.kurosu.sleepin.data.local.entity.TimetableEntity

/**
 * Main Room database for SleepIn app.
 *
 * This database uses Room to provide local persistence. It registers all standard entities
 * required by the app, and delegates standard database operations to corresponding DAOs.
 * It also registers [Converters] to handle custom data types like `LocalDate` or custom ENUMs
 * seamlessly when interacting with the SQLite database.
 */
@Database(
    entities = [
        TimetableEntity::class,
        CourseEntity::class,
        CourseSessionEntity::class,
        ScheduleEntity::class,
        SchedulePeriodEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SleepInDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun courseDao(): CourseDao
    abstract fun courseSessionDao(): CourseSessionDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun schedulePeriodDao(): SchedulePeriodDao
}

