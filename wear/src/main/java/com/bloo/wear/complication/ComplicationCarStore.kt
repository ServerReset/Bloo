package com.bloo.wear.complication

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.first

private val Context.complicationCarStore by preferencesDataStore(name = "bloo_complication_cars")

/**
 * Maps a watch-face complication INSTANCE (a specific slot on a specific watch
 * face, identified by ComplicationRequest.complicationInstanceId) to the VIN of
 * the car it should display. Keyed by "<dataSource>:<instanceId>" so the three
 * Bloo complications never collide even if a face reuses a numeric slot id.
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

/**
 * Resolve the car a complication request should show: the per-instance configured
 * car if set (and still present), else the globally-selected car as a default.
 */
suspend fun resolveComplicationCar(
    context: Context,
    dataSource: String,
    instanceId: Int,
): VehicleSnapshot? {
    val data = SnapshotStore(context).current()
    val vin = runCatching { ComplicationCarStore(context).vinFor(dataSource, instanceId) }.getOrNull()
    return vin?.let { v -> data.vehicles.firstOrNull { it.vin == v } } ?: data.selected
}
