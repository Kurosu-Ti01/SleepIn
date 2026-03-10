package com.kurosu.sleepin.data.local.converter

import androidx.room.TypeConverter
import com.kurosu.sleepin.domain.model.WeekType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room converters for java.time and custom collection/enum fields.
 */
class Converters {

    private val json = Json

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun localTimeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun weekTypeToString(value: WeekType?): String? = value?.name

    @TypeConverter
    fun stringToWeekType(value: String?): WeekType? = value?.let(WeekType::valueOf)

    @TypeConverter
    fun intListToJson(value: List<Int>?): String? = value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToIntList(value: String?): List<Int>? = value?.let { json.decodeFromString<List<Int>>(it) }
}

