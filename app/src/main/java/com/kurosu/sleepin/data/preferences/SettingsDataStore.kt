package com.kurosu.sleepin.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * App-scoped preferences DataStore instance.
 *
 * The extension property ensures a single DataStore object per process for this file name.
 */
val Context.settingsDataStore by preferencesDataStore(name = "sleepin_settings")

/**
 * Alias to improve readability in DI module signatures.
 */
typealias SettingsPreferenceStore = androidx.datastore.core.DataStore<Preferences>

