package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlin.properties.ReadOnlyProperty

/**
 * The app's one DataStore factory. Every store is a preferences DataStore that resets to empty on a
 * corrupt file instead of rethrowing an uncaught exception out of every read: a file damaged by an
 * interrupted write or a power loss should force a re-login or an empty cache, never a crash loop.
 *
 * Use it exactly like [preferencesDataStore][preferencesDataStore]:
 * `private val Context.fooStore by safePreferencesDataStore("bloo_foo")`.
 */
fun safePreferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    preferencesDataStore(
        name = name,
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    )
