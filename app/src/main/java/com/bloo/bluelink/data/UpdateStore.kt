package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// A corruption handler so a file damaged by an interrupted write/power loss
// resets to empty prefs instead of rethrowing an uncaught exception out of
// every read.
private val Context.updateDataStore by preferencesDataStore(
    name = "bloo_update",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Debounce/snooze/enable state for the update checker. "Not now" needs no
 * persisted state at all - the checker only ever runs once per cold start
 * (gated by [lastCheckedAt]'s debounce), so simply clearing the in-memory
 * prompt is enough; the next real check is already >=12h away. "Remind me
 * in a few days" is the one that needs to survive past that, hence
 * [snoozeUntil].
 */
class UpdateStore(private val context: Context) {

    private val keyLastCheckedAt = longPreferencesKey("last_checked_at")
    private val keySnoozeUntil = longPreferencesKey("snooze_until")
    private val keyChecksEnabled = booleanPreferencesKey("checks_enabled")

    suspend fun lastCheckedAt(): Long =
        context.updateDataStore.data.first()[keyLastCheckedAt] ?: 0L

    suspend fun setLastCheckedAt(millis: Long) {
        context.updateDataStore.edit { it[keyLastCheckedAt] = millis }
    }

    suspend fun snoozeUntil(): Long =
        context.updateDataStore.data.first()[keySnoozeUntil] ?: 0L

    suspend fun setSnoozeUntil(millis: Long) {
        context.updateDataStore.edit { it[keySnoozeUntil] = millis }
    }

    val checksEnabled: Flow<Boolean> =
        context.updateDataStore.data.map { it[keyChecksEnabled] ?: true }

    suspend fun setChecksEnabled(enabled: Boolean) {
        context.updateDataStore.edit { it[keyChecksEnabled] = enabled }
    }
}
