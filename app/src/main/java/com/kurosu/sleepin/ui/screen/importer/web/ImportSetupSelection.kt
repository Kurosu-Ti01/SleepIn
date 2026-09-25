package com.kurosu.sleepin.ui.screen.importer.web

/**
 * Picks the target to select when the user switches the import target to "overwrite an existing
 * timetable".
 *
 * Keeps the timetable that was already chosen and falls back to the first one only when that choice is
 * gone, so toggling between "new" and "overwrite" does not silently move the selection to the top of
 * the list.
 *
 * Returns the whole option rather than an id because the caller also needs the schedule the timetable
 * currently uses. Replacing a timetable's courses is not a request to change when its classes happen,
 * so the import has to default to that timetable's own schedule — defaulting to the first schedule in
 * the list would silently relink the timetable and change every class time it shows.
 */
internal fun chooseOverwriteTarget(
    options: List<ImportTimetableOptionUi>,
    currentTimetableId: Long?
): ImportTimetableOptionUi? = options.firstOrNull { option -> option.id == currentTimetableId }
    ?: options.firstOrNull()
