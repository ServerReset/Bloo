package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val keyAvailableRun = intPreferencesKey("available_run")
    private val keyAvailableTitle = stringPreferencesKey("available_title")
    private val keyAvailableUrl = stringPreferencesKey("available_url")
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

    val checksEnabled: Flow<Boolean> =
        context.updateDataStore.data.map { it[keyChecksEnabled] ?: true }

    suspend fun setChecksEnabled(enabled: Boolean) {
        context.updateDataStore.edit { it[keyChecksEnabled] = enabled }
    }

    /** The last build the checker found newer than what's installed, persisted
     *  (not just in AppViewModel's in-memory UiState) so UpdateCheckWorker --
     *  which runs on its own periodic schedule, independent of the app being
     *  open -- can read the same result the in-app check would have found.
     *  0 = none available. */
    suspend fun setAvailable(runNumber: Int, displayTitle: String?, htmlUrl: String) {
        context.updateDataStore.edit {
            it[keyAvailableRun] = runNumber
            if (displayTitle != null) it[keyAvailableTitle] = displayTitle else it.remove(keyAvailableTitle)
            it[keyAvailableUrl] = htmlUrl
        }
    }

    suspend fun clearAvailable() {
        context.updateDataStore.edit {
            it.remove(keyAvailableRun)
            it.remove(keyAvailableTitle)
            it.remove(keyAvailableUrl)
        }
    }

    suspend fun availableRunNumber(): Int = context.updateDataStore.data.first()[keyAvailableRun] ?: 0
    suspend fun availableTitle(): String? = context.updateDataStore.data.first()[keyAvailableTitle]
    suspend fun availableHtmlUrl(): String? = context.updateDataStore.data.first()[keyAvailableUrl]

    /** The run number UpdateCheckWorker last posted a notification for, so it
     *  doesn't nag with a fresh notification every ~12h about the same build
     *  someone hasn't installed yet -- only a genuinely newer one. */
    suspend fun lastNotifiedRun(): Int = context.updateDataStore.data.first()[keyLastNotifiedRun] ?: 0

    suspend fun setLastNotifiedRun(runNumber: Int) {
        context.updateDataStore.edit { it[keyLastNotifiedRun] = runNumber }
    }
}
