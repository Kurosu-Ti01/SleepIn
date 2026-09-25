package com.kurosu.sleepin.ui.screen.importer

/**
 * Rules of the preset editor form that are worth testing without a screen attached.
 */

/** Initial used when a name contains no Latin letter to derive one from. */
internal const val FALLBACK_PRESET_INITIAL: String = "A"

/**
 * The initial a name suggests, or null when it suggests none.
 *
 * Deriving the initial of a Chinese name needs a transliteration table this app does not ship, so this
 * only finds what it can: the first Latin letter in the name.
 */
internal fun initialFromName(name: String): String? =
    name.firstOrNull { character -> character in 'A'..'Z' || character in 'a'..'z' }?.uppercase()

/**
 * The initial to show after the name changes to [newName].
 *
 * The initial follows the name only while it still holds what [previousName] suggested. Without that
 * check, renaming a preset whose initial was typed by hand would silently overwrite the user's choice;
 * with it, the field is helpful until the moment the user disagrees with it.
 */
internal fun initialAfterNameChange(
    previousName: String,
    newName: String,
    currentInitial: String
): String {
    val suggestedBefore = initialFromName(previousName) ?: FALLBACK_PRESET_INITIAL
    if (currentInitial != suggestedBefore) return currentInitial
    return initialFromName(newName) ?: FALLBACK_PRESET_INITIAL
}

/**
 * Narrows what the initial field accepts to a single A-Z letter, or to blank while it is being edited.
 *
 * The stored preset requires exactly one letter, so the field is filtered as the user types rather than
 * validated afterwards: a rule the input cannot break needs no error message.
 */
internal fun sanitizeInitialInput(raw: String): String =
    raw.firstOrNull { character -> character in 'A'..'Z' || character in 'a'..'z' }?.uppercase() ?: ""
