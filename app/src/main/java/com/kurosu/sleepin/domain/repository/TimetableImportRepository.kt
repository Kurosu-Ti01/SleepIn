package com.kurosu.sleepin.domain.repository

import com.kurosu.sleepin.domain.model.CourseWithSessions
import com.kurosu.sleepin.domain.model.SchedulePeriod
import java.time.LocalDate

/**
 * A schedule template an import should create before linking its result to it.
 *
 * Only exists so that an import which read class times from a portal can store them as a real
 * schedule. The periods carry no ids: the implementation assigns them, because the schedule row
 * does not exist until the write runs.
 */
data class ImportScheduleDraft(
    val name: String,
    val periods: List<SchedulePeriod>
)

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
     * @param scheduleId existing schedule template the timetable should reference. The import writes
     *   schedule periods only when [newSchedule] is given, because a class-time template is normally
     *   a deliberate user decision while an academic portal often only knows period numbers.
     * @param setActive whether the new timetable should become the active one. Newly imported
     *   timetables normally become active so the user immediately sees the result.
     * @param newSchedule class times the payload reported, when the user chose to build a schedule
     *   from them. Non-null means the import creates that schedule and links the result to it, so
     *   [scheduleId] is ignored.
     */
    data class CreateNew(
        val name: String,
        val totalWeeks: Int,
        val startDate: LocalDate,
        val scheduleId: Long,
        val setActive: Boolean,
        val newSchedule: ImportScheduleDraft? = null
    ) : TimetableImportPlan

    /**
     * Replaces the courses of an existing timetable.
     *
     * @param timetableId target timetable; its name, week count and start date are preserved so a
     *   user who re-imports into the same semester keeps their own labels.
     * @param scheduleId schedule template the timetable should point at after the import. It is
     *   applied even when it differs from the current one, because picking the schedule is part of
     *   the import setup the user just completed.
     * @param newSchedule same meaning as in [CreateNew]: when present, the import creates this
     *   schedule and re-points the timetable at it instead of using [scheduleId].
     */
    data class Overwrite(
        val timetableId: Long,
        val scheduleId: Long,
        val newSchedule: ImportScheduleDraft? = null
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
        val sessionCount: Int,
        /**
         * Schedule template the result references *after* the write.
         *
         * Carried back because an import that created a schedule from the payload's own times knows
         * that id only once the schedule row exists, and the "go fix your schedule" action has to
         * point at the schedule the user actually ended up with.
         */
        val scheduleId: Long = 0L
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
 * the whole operation inside a single database transaction — including creating the schedule an
 * import may carry, so a failure cannot leave an orphan template behind either.
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
