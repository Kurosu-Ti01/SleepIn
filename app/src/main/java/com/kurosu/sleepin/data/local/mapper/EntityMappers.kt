package com.kurosu.sleepin.data.local.mapper

import com.kurosu.sleepin.data.local.entity.CourseEntity
import com.kurosu.sleepin.data.local.entity.CourseSessionEntity
import com.kurosu.sleepin.data.local.entity.ScheduleEntity
import com.kurosu.sleepin.data.local.entity.SchedulePeriodEntity
import com.kurosu.sleepin.data.local.entity.TimetableEntity
import com.kurosu.sleepin.domain.model.Course
import com.kurosu.sleepin.domain.model.CourseSession
import com.kurosu.sleepin.domain.model.Schedule
import com.kurosu.sleepin.domain.model.SchedulePeriod
import com.kurosu.sleepin.domain.model.Timetable

/**
 * Mapper extensions keep domain models independent from Room entities.
 */
fun TimetableEntity.toDomain(): Timetable = Timetable(
    id = id,
    name = name,
    totalWeeks = totalWeeks,
    startDate = startDate,
    scheduleId = scheduleId,
    colorScheme = colorScheme,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Timetable.toEntity(): TimetableEntity = TimetableEntity(
    id = id,
    name = name,
    totalWeeks = totalWeeks,
    startDate = startDate,
    scheduleId = scheduleId,
    colorScheme = colorScheme,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    timetableId = timetableId,
    name = name,
    teacher = teacher,
    color = color,
    note = note,
    createdAt = createdAt
)

fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    timetableId = timetableId,
    name = name,
    teacher = teacher,
    color = color,
    note = note,
    createdAt = createdAt
)

fun CourseSessionEntity.toDomain(): CourseSession = CourseSession(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    location = location,
    weekType = weekType,
    startWeek = startWeek,
    endWeek = endWeek,
    customWeeks = customWeeks
)

fun CourseSession.toEntity(): CourseSessionEntity = CourseSessionEntity(
    id = id,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startPeriod = startPeriod,
    endPeriod = endPeriod,
    location = location,
    weekType = weekType,
    startWeek = startWeek,
    endWeek = endWeek,
    customWeeks = customWeeks
)

fun ScheduleEntity.toDomain(): Schedule = Schedule(
    id = id,
    name = name,
    createdAt = createdAt
)

fun Schedule.toEntity(): ScheduleEntity = ScheduleEntity(
    id = id,
    name = name,
    createdAt = createdAt
)

fun SchedulePeriodEntity.toDomain(): SchedulePeriod = SchedulePeriod(
    id = id,
    scheduleId = scheduleId,
    periodNumber = periodNumber,
    startTime = startTime,
    endTime = endTime
)

fun SchedulePeriod.toEntity(): SchedulePeriodEntity = SchedulePeriodEntity(
    id = id,
    scheduleId = scheduleId,
    periodNumber = periodNumber,
    startTime = startTime,
    endTime = endTime
)

