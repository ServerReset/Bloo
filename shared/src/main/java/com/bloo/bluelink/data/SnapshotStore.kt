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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A small, on-disk projection of each vehicle's latest state. Background
 * workers and command runners run outside the in-memory ViewModel and can't
 * reach it, so the app mirrors what they need here.
 */
@Serializable
data class VehicleSnapshot(
    val vin: String,
    val name: String,
    val model: String,
    val isEv: Boolean,
    /** Whether this car has a chargeable battery, per the user's manual powertrain override
     *  (a PHEV the API reports as gas). Defaults to [isEv]. */
    val hasBattery: Boolean = isEv,
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
    /** The car's raw reported speed VALUE from the API's `{value, unit}` pair, unit discarded at capture,
     *  so the actual unit is unestablished. Only safe for "above zero" checks ([isDriving]); resolve the
     *  unit (and rename) before displaying it. */
    val speedMph: Double? = null,
    val updated: String? = null,
    /** Wall-clock (ms) of the last fresh data from the car; 0 = unknown. Lets surfaces flag stale data. */
    val fetchedAt: Long = 0L,
    val odometer: String? = null,
    /** User-entered license plate and service-due tracking, mirrored for other snapshot readers. */
    val licensePlate: String? = null,
    val lastServiceMiles: Int? = null,
    val serviceIntervalMiles: Int? = null,
    /** The car's charge limit for the plug it is on (see [EvStatus.targetForCurrentPlug]), 1..100,
     *  or null when unplugged or unreported. */
    val chargeLimitPct: Int? = null,
) {
    /** Rebuild the command-capable Vehicle. */
    fun toVehicle(): Vehicle = Vehicle(
        vin = vin,
        regId = regId,
        name = name,
        model = model,
        generation = generation,
        brandIndicator = brandIndicator,
        isEv = isEv,
        odometer = odometer,
    )
}

/** True when the last known speed reading says the car is moving (snapshot-based AppViewModel.isDriving()). */
val VehicleSnapshot.isDriving: Boolean get() = (speedMph ?: 0.0) > 0.0

/**
 * Fold a freshly fetched status into an existing snapshot.
 *
 * [location] is a separately-fetched position for brands (Canada, Europe) whose STATUS carries
 * none. The status wins when it has a coordinate.
 */
fun VehicleSnapshot.merged(status: VehicleStatus, location: GeoLocation? = null): VehicleSnapshot {
    // hasBattery (the user's powertrain override), not isEv: a misreported PHEV must not get fuel data in percent/range.
    val pct = status.percentFor(hasBattery)
    val range = status.rangeMiFor(hasBattery)
    // Local so the nullable property smart-casts.
    val ev = status.evStatus
    return copy(
        percent = pct ?: percent,
        rangeMi = range ?: rangeMi,
        locked = status.doorLock ?: locked,
        charging = status.evStatus?.batteryCharge ?: charging,
        climateOn = status.airCtrlOn ?: climateOn,
        engineOn = status.engine ?: engineOn,
        // GeoLocation is flat (latitude/longitude/speed); VehicleLocation nests them under coord/speed.value.
        lat = status.vehicleLocation?.coord?.lat ?: location?.latitude ?: lat,
        lon = status.vehicleLocation?.coord?.lon ?: location?.longitude ?: lon,
        speedMph = status.vehicleLocation?.speed?.value ?: location?.speed ?: speedMph,
        updated = status.dateTime ?: updated,
        // Not the `new ?: old` shape: only a status with no evStatus keeps the old value.
        // displayChargeLimit (not targetForCurrentPlug) so an unplugged EV still resolves to its configured limit.
        chargeLimitPct = if (ev != null) ev.displayChargeLimit() else chargeLimitPct,
        // A just-fetched status makes the data current.
        fetchedAt = System.currentTimeMillis(),
    )
}

/**
 * This snapshot with any status field it lacks filled in from [old] (`new ?: old` per field, as in
 * [merged]), for [SnapshotStore.saveVehiclesKeepingStatus]. Identity and user-entered fields come
 * from the caller and are not carried forward, so a renamed or re-plated car stays updatable.
 */
internal fun VehicleSnapshot.keepingStatusOf(old: VehicleSnapshot): VehicleSnapshot = copy(
    percent = percent ?: old.percent,
    rangeMi = rangeMi ?: old.rangeMi,
    locked = locked ?: old.locked,
    charging = charging ?: old.charging,
    climateOn = climateOn ?: old.climateOn,
    engineOn = engineOn ?: old.engineOn,
    lat = lat ?: old.lat,
    lon = lon ?: old.lon,
    speedMph = speedMph ?: old.speedMph,
    updated = updated ?: old.updated,
    chargeLimitPct = chargeLimitPct ?: old.chargeLimitPct,
    // 0 means "unknown", so it follows the same rule.
    fetchedAt = if (fetchedAt > 0L) fetchedAt else old.fetchedAt,
)

/** The payload persisted as a single JSON string under one DataStore key, so reads and writes
 *  are atomic over the whole vehicle list and selection. */
@Serializable
private data class SnapshotPayload(
    val vehicles: List<VehicleSnapshot> = emptyList(),
    val selectedVin: String? = null,
)

// Corruption resets to empty prefs instead of crashing every background reader.
private val Context.snapshotDataStore by preferencesDataStore(
    name = "bloo_snapshots",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Reads and writes the on-disk [VehicleSnapshot] cache. Mutations are read-modify-write inside
 * DataStore's transactional [edit], so concurrent writers do not stomp on each other.
 */
/**
 * Apply [updates] onto [existing] by VIN (extracted for JVM testing). Order is the existing
 * list's (user-visible pager order); VINs not in [existing] are ignored, not appended; the last
 * duplicate in [updates] wins.
 */
internal fun mergeVehicleUpdates(
    existing: List<VehicleSnapshot>,
    updates: List<VehicleSnapshot>,
): List<VehicleSnapshot> {
    if (updates.isEmpty()) return existing
    val byVin = updates.associateBy { it.vin }
    return existing.map { byVin[it.vin] ?: it }
}

class SnapshotStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val PAYLOAD = stringPreferencesKey("payload")
    }

    /** Live stream of the snapshot data; re-emits when the DataStore file changes, even from another process. */
    val payload: Flow<SnapshotData> = context.snapshotDataStore.data.map { prefs ->
        decode(prefs[Keys.PAYLOAD])
    }
        // decode() parses every vehicle; keep it off the collector's (often main) thread.
        .flowOn(Dispatchers.IO)

    /** One-shot read of the current snapshot data. */
    suspend fun current(): SnapshotData = withContext(Dispatchers.IO) {
        StartupTrace.markIfStarting("SnapshotStore.current(): begin (disk read)")
        // .first() resumes on the caller's dispatcher, often main.
        decode(context.snapshotDataStore.data.first()[Keys.PAYLOAD])
    }

    /**
     * Forces the first DataStore read now (file-open + parse) on the startup warm-up thread,
     * so the cold-start garage publish via [current] skips that disk read.
     */
    suspend fun warmUp() {
        runCatching { context.snapshotDataStore.data.first() }
    }

    /**
     * Replace the vehicle LIST while keeping each surviving car's last-known status.
     *
     * For re-fetches that know identity (name, model, odometer, plate) but not state. Reads the
     * on-disk payload inside the same edit transaction (no race with the status cache). Carry-forward
     * is per field where the incoming value is absent ([merged]'s `new ?: old`); identity and
     * user-entered fields take the incoming value. [saveVehicles] stays wholesale (sign-out clears with it).
     */
    suspend fun saveVehiclesKeepingStatus(vehicles: List<VehicleSnapshot>) {
        if (vehicles.isEmpty()) return
        // IO: decodes/re-encodes the whole payload, called per car from Main.immediate coroutines.
        withContext(Dispatchers.IO) {
            context.snapshotDataStore.edit { prefs ->
                val existing = decode(prefs[Keys.PAYLOAD])
                val known = existing.vehicles.associateBy { it.vin }
                val merged = vehicles.map { fresh -> known[fresh.vin]?.let { fresh.keepingStatusOf(it) } ?: fresh }
                val selected = existing.selectedVin?.takeIf { sel -> merged.any { it.vin == sel } }
                    ?: merged.firstOrNull()?.vin
                prefs[Keys.PAYLOAD] = json.encodeToString(
                    SnapshotPayload.serializer(),
                    SnapshotPayload(merged, selected),
                )
            }
        }
    }

    /** Replace the entire vehicle list. Keeps the selected VIN if still present, else selects the first car. */
    suspend fun saveVehicles(vehicles: List<VehicleSnapshot>) {
        // IO, as in saveVehiclesKeepingStatus.
        withContext(Dispatchers.IO) {
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
    }

    /** Replace a single vehicle's snapshot (e.g. after a status refresh). */
    suspend fun updateVehicle(snapshot: VehicleSnapshot) = updateVehicles(listOf(snapshot))

    /**
     * Merge several vehicles in ONE store write: cost and [payload] emissions are per write
     * (the payload is one JSON blob), not per vehicle. VINs not in the store are ignored;
     * adding cars is [saveVehicles]' job.
     */
    suspend fun updateVehicles(snapshots: List<VehicleSnapshot>) {
        if (snapshots.isEmpty()) return
        // IO, as in saveVehiclesKeepingStatus.
        withContext(Dispatchers.IO) {
            context.snapshotDataStore.edit { prefs ->
                val existing = decode(prefs[Keys.PAYLOAD])
                prefs[Keys.PAYLOAD] = json.encodeToString(
                    SnapshotPayload.serializer(),
                    SnapshotPayload(mergeVehicleUpdates(existing.vehicles, snapshots), existing.selectedVin),
                )
            }
        }
    }

    /**
     * Fold freshly-fetched statuses into the stored snapshots by VIN in one atomic
     * read-modify-write, for pollers that hold a [VehicleStatus] but no snapshot. Doing it inside
     * `edit` avoids overwriting a concurrent writer. Unknown VINs are skipped; per-field semantics
     * are [merged]'s, so a partial status only adds information.
     */
    suspend fun mergeStatuses(statuses: Map<String, VehicleStatus>) {
        if (statuses.isEmpty()) return
        // IO, as in saveVehiclesKeepingStatus.
        withContext(Dispatchers.IO) {
            context.snapshotDataStore.edit { prefs ->
                val existing = decode(prefs[Keys.PAYLOAD])
                if (existing.vehicles.isEmpty()) return@edit
                prefs[Keys.PAYLOAD] = json.encodeToString(
                    SnapshotPayload.serializer(),
                    SnapshotPayload(
                        existing.vehicles.map { snap ->
                            statuses[snap.vin]?.let { snap.merged(it) } ?: snap
                        },
                        existing.selectedVin,
                    ),
                )
            }
        }
    }

    /** Parse the stored JSON into [SnapshotData]; null or corrupt input yields an empty payload rather than throwing. */
    private fun decode(raw: String?): SnapshotData {
        val payload = raw?.let {
            runCatching { json.decodeFromString(SnapshotPayload.serializer(), it) }.getOrNull()
        } ?: SnapshotPayload()
        return SnapshotData(payload.vehicles, payload.selectedVin)
    }

    /** Decoded view of the store: every known vehicle plus the selected VIN. */
    data class SnapshotData(
        val vehicles: List<VehicleSnapshot>,
        val selectedVin: String?,
    ) {
        /** The selected vehicle's snapshot, else the first vehicle, else null. */
        val selected: VehicleSnapshot?
            get() = vehicles.firstOrNull { it.vin == selectedVin } ?: vehicles.firstOrNull()
    }
}
