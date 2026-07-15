package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A small, on-disk projection of each vehicle's latest state. Home-screen
 * widgets and Quick Settings tiles run in separate processes and can't reach
 * the in-memory ViewModel, so the app mirrors what they need here.
 */
@Serializable
data class VehicleSnapshot(
    val vin: String,
    val name: String,
    val model: String,
    val isEv: Boolean,
    val regId: String = "",
    val generation: String = "2",
    val brandIndicator: String = "H",
    val percent: Int? = null,
    val rangeMi: Int? = null,
    val locked: Boolean? = null,
    val charging: Boolean? = null,
    val climateOn: Boolean? = null,
    val engineOn: Boolean? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val updated: String? = null,
    /** Wall-clock (ms) when this snapshot last got fresh data from the car; 0 =
     *  unknown. Lets glanceable surfaces flag stale data instead of showing an
     *  hours-old lock/charge state as if it were live. */
    val fetchedAt: Long = 0L,
) {
    /** Rebuild the command-capable Vehicle (used by widgets/tiles). */
    fun toVehicle(): Vehicle = Vehicle(
        vin = vin,
        regId = regId,
        name = name,
        model = model,
        generation = generation,
        brandIndicator = brandIndicator,
        isEv = isEv,
    )
}

/** Fold a freshly fetched status into an existing snapshot. */
fun VehicleSnapshot.merged(status: VehicleStatus): VehicleSnapshot {
    val pct = if (isEv) status.evStatus?.batteryStatus else status.fuelLevel
    val range = (status.evStatus?.drvDistance?.firstOrNull()
        ?.rangeByFuel?.totalAvailableRange?.value ?: status.dte?.value)?.toInt()
    return copy(
        percent = pct ?: percent,
        rangeMi = range ?: rangeMi,
        locked = status.doorLock ?: locked,
        charging = status.evStatus?.batteryCharge ?: charging,
        climateOn = status.airCtrlOn ?: climateOn,
        engineOn = status.engine ?: engineOn,
        lat = status.vehicleLocation?.coord?.lat ?: lat,
        lon = status.vehicleLocation?.coord?.lon ?: lon,
        updated = status.dateTime ?: updated,
        // merged() folds in a status we JUST fetched, so this data is now current.
        fetchedAt = System.currentTimeMillis(),
    )
}

@Serializable
private data class SnapshotPayload(
    val vehicles: List<VehicleSnapshot> = emptyList(),
    val selectedVin: String? = null,
)

// A corruption handler so a file damaged by an interrupted write/power loss
// resets to empty prefs instead of rethrowing an uncaught exception out of
// every read — this store is read from the widget, tiles, and complications,
// every one of which would otherwise crash on a corrupt file.
private val Context.snapshotDataStore by preferencesDataStore(
    name = "bloo_snapshots",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SnapshotStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val PAYLOAD = stringPreferencesKey("payload")
    }

    val payload: Flow<SnapshotData> = context.snapshotDataStore.data.map { prefs ->
        decode(prefs[Keys.PAYLOAD])
    }

    suspend fun current(): SnapshotData = decode(context.snapshotDataStore.data.first()[Keys.PAYLOAD])

    suspend fun saveVehicles(vehicles: List<VehicleSnapshot>) {
        context.snapshotDataStore.edit { prefs ->
            val existing = decode(prefs[Keys.PAYLOAD])
            val selected = existing.selectedVin?.takeIf { sel -> vehicles.any { it.vin == sel } }
                ?: vehicles.firstOrNull()?.vin
            prefs[Keys.PAYLOAD] = json.encodeToString(
                SnapshotPayload.serializer(),
                SnapshotPayload(vehicles, selected),
            )
        }
    }

    /** Replace a single vehicle's snapshot (e.g. after a widget refresh). */
    suspend fun updateVehicle(snapshot: VehicleSnapshot) {
        context.snapshotDataStore.edit { prefs ->
            val existing = decode(prefs[Keys.PAYLOAD])
            val updated = existing.vehicles.map { if (it.vin == snapshot.vin) snapshot else it }
            prefs[Keys.PAYLOAD] = json.encodeToString(
                SnapshotPayload.serializer(),
                SnapshotPayload(updated, existing.selectedVin),
            )
        }
    }

    suspend fun setSelected(vin: String) {
        context.snapshotDataStore.edit { prefs ->
            val existing = decode(prefs[Keys.PAYLOAD])
            prefs[Keys.PAYLOAD] = json.encodeToString(
                SnapshotPayload.serializer(),
                SnapshotPayload(existing.vehicles, vin),
            )
        }
    }

    /** Advance the widget/tile selection to the next car, looping. */
    suspend fun selectNext(): VehicleSnapshot? {
        var result: VehicleSnapshot? = null
        context.snapshotDataStore.edit { prefs ->
            val existing = decode(prefs[Keys.PAYLOAD])
            if (existing.vehicles.isEmpty()) return@edit
            val idx = existing.vehicles.indexOfFirst { it.vin == existing.selectedVin }
            val next = existing.vehicles[(idx + 1).mod(existing.vehicles.size)]
            result = next
            prefs[Keys.PAYLOAD] = json.encodeToString(
                SnapshotPayload.serializer(),
                SnapshotPayload(existing.vehicles, next.vin),
            )
        }
        return result
    }

    private fun decode(raw: String?): SnapshotData {
        val payload = raw?.let {
            runCatching { json.decodeFromString(SnapshotPayload.serializer(), it) }.getOrNull()
        } ?: SnapshotPayload()
        return SnapshotData(payload.vehicles, payload.selectedVin)
    }

    data class SnapshotData(
        val vehicles: List<VehicleSnapshot>,
        val selectedVin: String?,
    ) {
        val selected: VehicleSnapshot?
            get() = vehicles.firstOrNull { it.vin == selectedVin } ?: vehicles.firstOrNull()
    }
}
