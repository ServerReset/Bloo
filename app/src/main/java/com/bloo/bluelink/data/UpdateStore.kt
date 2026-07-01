package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateDataStore by preferencesDataStore(name = "bloo_update")

/**
 * Debounce/dismissal state for the update checker so it doesn't hit GitHub on
 * every cold start, and so a "Not now" on one release doesn't get re-asked
 * until the *next* release ships.
 */
class UpdateStore(private val context: Context) {

    private val keyLastCheckedAt = longPreferencesKey("last_checked_at")
    private val keyDismissedVersionCode = intPreferencesKey("dismissed_version_code")

    suspend fun lastCheckedAt(): Long =
        context.updateDataStore.data.first()[keyLastCheckedAt] ?: 0L

    suspend fun setLastCheckedAt(millis: Long) {
        context.updateDataStore.edit { it[keyLastCheckedAt] = millis }
    }

    suspend fun dismissedVersionCode(): Int? =
        context.updateDataStore.data.first()[keyDismissedVersionCode]

    suspend fun setDismissedVersionCode(versionCode: Int) {
        context.updateDataStore.edit { it[keyDismissedVersionCode] = versionCode }
    }
}
