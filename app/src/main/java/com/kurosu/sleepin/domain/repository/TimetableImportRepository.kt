package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.CourseWithSessions
import java.time.LocalDate

/**
 * Describes what one academic-portal import should do with its target timetable.
 *
 * The plan is resolved by the domain layer *before* persistence starts, so the repository only has
 * to apply an already-validated decision and never has to guess user intent.
 */
sealed interface TimetableImportPlan {

    /**
     * Creates a brand-new timetable and fills it with imported courses.
     *
     * @param name display name for the new timetable; already validated as non-blank.
     * @param totalWeeks semester length; already validated against the range the app can display.
     * @param startDate first day of week 1.
     * @param scheduleId existing schedule template the timetable should reference. The import never
     *   writes schedule periods, because a class-time template is a deliberate user decision while
     *   an academic portal often only knows period numbers.
     * @param setActive whether the new timetable should become the active one. Newly imported
     *   timetables normally become active so the user immediately sees the result.
     */
    data class CreateNew(
        val name: String,
        val totalWeeks: Int,
        val startDate: LocalDate,
        val scheduleId: Long,
        val setActive: Boolean
    ) : TimetableImportPlan

    /**
     * Replaces the courses of an existing timetable.
     *
     * @param timetableId target timetable; its name, week count and start date are preserved so a
     *   user who re-imports into the same semester keeps their own labels.
     * @param scheduleId schedule template the timetable should point at after the import. It is
     *   applied even when it differs from the current one, because picking the schedule is part of
     *   the import setup the user just completed.
     */
    data class Overwrite(
        val timetableId: Long,
        val scheduleId: Long
    ) : TimetableImportPlan
}

/**
 * Outcome of one attempt to persist an imported timetable.
 *
 * [Rejected] is produced by domain validation before persistence runs; the repository itself only
 * returns [Imported], [TimetableNotFound] or [ScheduleNotFound].
 */
sealed interface TimetableImportResult {

    /** Everything was written; counts are provided so callers can describe the result to the user. */
    data class Imported(
        val timetableId: Long,
        val courseCount: Int,
        val sessionCount: Int
    ) : TimetableImportResult

    /** [TimetableImportPlan.Overwrite] referenced a timetable that no longer exists. */
    data object TimetableNotFound : TimetableImportResult

    /** The referenced schedule template does not exist, so the timetable cannot be linked. */
    data object ScheduleNotFound : TimetableImportResult

    /** The request was refused before any write happened. [message] is user-facing. */
    data class Rejected(val message: String) : TimetableImportResult
}

/**
 * Contract for persisting an imported timetable in one atomic step.
 *
 * This exists separately from [CourseRepository] because an import creates or selects a timetable
 * and then replaces all of its courses; splitting that across several repository calls would leave
 * a half-written timetable behind whenever one step failed. Implementations must therefore perform
 * the whole operation inside a single database transaction.
 */
interface TimetableImportRepository {

    /**
     * Applies [plan] and replaces the target timetable's courses with [courses].
     *
     * Session rows are derived from each course, and any course or session the timetable had before
     * is discarded, so this is a full replacement rather than a merge.
     *
     * [CourseWithSessions.course]'s own id and timetable id are ignored and assigned by the
     * implementation, which lets callers build the payload before the timetable id exists.
     */
    suspend fun commit(
        plan: TimetableImportPlan,
        courses: List<CourseWithSessions>
    ): TimetableImportResult
}
