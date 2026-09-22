package com.kurosu.sleepin.domain.model.importer

/**
 * What a user submitted when adding an import preset of their own.
 *
 * The script text travels with the draft because the two have to be accepted or refused together: a
 * preset whose metadata was stored but whose script was not is a broken entry in the list.
 *
 * Values are taken verbatim here. Trimming, range checks and "is this a URL" belong to the use case
 * that accepts the draft, so the rules live in one place and the storage layer only has to store.
 */
data class CustomImportPresetDraft(
    val name: String,
    val group: ImportPresetGroup,
    /**
     * Single uppercase letter used to group and search the list.
     *
     * Supplied by the user rather than derived from the name: most school names are Chinese, and
     * turning one into a Latin initial needs a transliteration table this app does not ship.
     */
    val initial: String,
    /** Page to open before running the script. Empty means the WebView starts blank. */
    val startUrl: String,
    val description: String,
    val maintainer: String,
    val scriptText: String
)

/**
 * Outcome of adding a custom preset.
 */
sealed interface ImportPresetAddResult {

    /** The preset is stored and usable; [preset] is the definition that was stored. */
    data class Added(val preset: ImportPreset) : ImportPresetAddResult

    /**
     * The draft was refused and nothing was written.
     *
     * [message] is user-facing and describes what to fix, because the only way out of this state is
     * for the user to edit the form.
     */
    data class Rejected(val message: String) : ImportPresetAddResult

    /**
     * The draft was acceptable but could not be stored. [message] is user-facing.
     *
     * Kept separate from [Rejected] so the screen can tell "fix your input" apart from "try again",
     * which are different suggestions to put in front of the user.
     */
    data class Failed(val message: String) : ImportPresetAddResult
}
