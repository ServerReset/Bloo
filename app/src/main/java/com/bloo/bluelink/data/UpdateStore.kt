package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

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
    private val keyLastNotifiedRun = intPreferencesKey("last_notified_run")

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

    /** The run number UpdateCheckWorker last posted a notification for, so it
     *  doesn't nag with a fresh notification every ~12h about the same build
     *  someone hasn't installed yet -- only a genuinely newer one. */
    suspend fun lastNotifiedRun(): Int = context.updateDataStore.data.first()[keyLastNotifiedRun] ?: 0

    suspend fun setLastNotifiedRun(runNumber: Int) {
        context.updateDataStore.edit { it[keyLastNotifiedRun] = runNumber }
    }
}
