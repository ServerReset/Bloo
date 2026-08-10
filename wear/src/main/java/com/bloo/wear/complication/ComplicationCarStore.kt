package com.bloo.wear.complication

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.pinnedOrSelected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Read on every complication render, so it "must not throw": a file damaged by an
// interrupted write / power loss resets to empty prefs instead of rethrowing out
// of every read.
private val Context.complicationCarStore by preferencesDataStore(
    name = "bloo_complication_cars",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Maps a watch-face complication INSTANCE (one slot on one watch face, identified
 * by [androidx.wear.watchface.complications.datasource.ComplicationRequest.complicationInstanceId])
 * to the VIN of the car it should display. Keyed by "<dataSource>:<instanceId>" so
 * the three Bloo complications never collide even if a face reuses a numeric slot id.
 */
class ComplicationCarStore(private val context: Context) {

    private fun key(dataSource: String, instanceId: Int) =
        stringPreferencesKey("$dataSource:$instanceId")

    suspend fun vinFor(dataSource: String, instanceId: Int): String? =
        context.complicationCarStore.data.first()[key(dataSource, instanceId)]

    suspend fun setVin(dataSource: String, instanceId: Int, vin: String) {
        context.complicationCarStore.edit { it[key(dataSource, instanceId)] = vin }
    }

    suspend fun clear(dataSource: String, instanceId: Int) {
        context.complicationCarStore.edit { it.remove(key(dataSource, instanceId)) }
    }
}

private val complicationCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Drop a complication instance's per-slot car pin. Called from
 * onComplicationDeactivated so a removed complication doesn't leave a stale pin
 * that a future slot reusing the same id would inherit. Fire-and-forget on an IO
 * scope — the DataStore write completes independently of the (short-lived) service.
 */
fun clearComplicationConfig(context: Context, dataSource: String, instanceId: Int) {
    complicationCleanupScope.launch {
        runCatching { ComplicationCarStore(context.applicationContext).clear(dataSource, instanceId) }
    }
}

/**
 * Resolve the car a complication request should show: the per-instance configured
 * car if set (and still present in the synced snapshot), else the globally-selected
 * car as a default. Both reads are runCatching-guarded — an uncaught exception out
 * of onComplicationRequest would crash the data-source process, so a corrupt /
 * unreadable store degrades to "no data" (null) rather than a crash.
 */
suspend fun resolveComplicationCar(
    context: Context,
    dataSource: String,
    instanceId: Int,
): VehicleSnapshot? {
    val data = runCatching { SnapshotStore(context).current() }.getOrNull() ?: return null
    val vin = runCatching { ComplicationCarStore(context).vinFor(dataSource, instanceId) }.getOrNull()
    return data.pinnedOrSelected(vin)
}
