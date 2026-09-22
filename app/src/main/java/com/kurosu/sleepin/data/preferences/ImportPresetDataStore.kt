package com.kurosu.sleepin.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * App-scoped DataStore for the user's own import presets.
 *
 * A separate file from the settings one on purpose: this is a small database of user-authored
 * content rather than app preferences, and keeping it apart means a reset of either cannot damage
 * the other.
 *
 * The extension property ensures a single DataStore object per process for this file name.
 */
val Context.importPresetDataStore by preferencesDataStore(name = "sleepin_import_presets")

/**
 * Alias to improve readability in DI module signatures.
 */
typealias ImportPresetPreferenceStore = androidx.datastore.core.DataStore<Preferences>
