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
    /** Whether this car has a chargeable battery, per the user's manual
     *  powertrain override on the phone (a PHEV the API misreports as gas
     *  still needs the Charge tile). Defaults to [isEv] so snapshots built
     *  without an override (e.g. the watch's own standalone vehicle fetch)
     *  behave exactly as before. */
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
    /** mph, from the last fetched status's vehicleLocation.speed, if the car
     *  reported one. Lets out-of-process command runners (Quick Settings
     *  tiles, the widget, the watch's own standalone/relay path) apply the
     *  same "car rejects climate commands while driving" gate the main phone
     *  UI's own AppViewModel.isDriving() already does -- those runners only
     *  ever see a [VehicleSnapshot], never the live location state the main
     *  UI tracks separately. */
    /** CAUTION -- the name is not a promise. This is the car's raw reported
     *  speed VALUE, copied straight from the API's `{value, unit}` pair with
     *  the unit code thrown away at capture (see AppViewModel, and Speed.unit
     *  in Models.kt). Nothing in this codebase decodes those unit codes for
     *  speed, distance or time, so which unit this actually holds is not
     *  established anywhere.
     *
     *  That has never mattered, because [isDriving] -- its only reader --
     *  merely asks whether it is above zero, which is true in any unit. It
     *  would matter immediately for anything that DISPLAYS it: a widget row
     *  or tile reading "62 mph" off a km/h value is worse than showing no
     *  speed at all. Resolve the unit against a real car before rendering
     *  this, and if you convert it, rename the field at the same time. */
    val speedMph: Double? = null,
    val updated: String? = null,
    /** Wall-clock (ms) when this snapshot last got fresh data from the car; 0 =
     *  unknown. Lets glanceable surfaces flag stale data instead of showing an
     *  hours-old lock/charge state as if it were live. */
    val fetchedAt: Long = 0L,
    val odometer: String? = null,
    /** User-entered license plate and service-due tracking (phone Settings),
     *  mirrored so surfaces other than the phone's own Info pebble -- the
     *  watch's Info tile in particular -- can show the same maintenance info. */
    val licensePlate: String? = null,
    val lastServiceMiles: Int? = null,
    val serviceIntervalMiles: Int? = null,
    /** The car's charge limit for the plug it's currently on (see
     *  [EvStatus.targetForCurrentPlug]), 1..100, or null when it isn't
     *  plugged in or didn't report one. Mirrored so the out-of-process
     *  surfaces can draw the same "will charge / won't" split the phone
     *  hero and the live charging notification both show. */
    val chargeLimitPct: Int? = null,
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
        odometer = odometer,
    )
}

/** True when the last known speed reading says the car is moving -- the
 *  snapshot-based equivalent of AppViewModel.isDriving(), for the
 *  out-of-process command runners that only ever see a [VehicleSnapshot]. */
val VehicleSnapshot.isDriving: Boolean get() = (speedMph ?: 0.0) > 0.0

/**
 * Fold a freshly fetched status into an existing snapshot.
 *
 * [location] is a separately-fetched position for the brands whose STATUS carries none.
 * Canada and Europe both expose GPS only through a dedicated find-my-car endpoint, so their
 * parsed VehicleStatus has no vehicleLocation at all -- which meant every background path
 * through this function (the alert worker, the live-charge poller, the watch's refresh) left
 * lat/lon/speed exactly as they were, however far the car had driven. The phone worked around
 * it in its own layer years ago (see Snapshots.kt's note on locate()); the shared fold that
 * every out-of-process surface uses never got the equivalent, so the fix only existed while
 * the app was open.
 *
 * The status still wins when it has a coordinate -- it is same-fetch fresh.
 */
fun VehicleSnapshot.merged(status: VehicleStatus, location: GeoLocation? = null): VehicleSnapshot {
    // Use hasBattery (the user's manual powertrain override), not the raw
    // isEv flag -- this reimplemented percentFor/rangeMiFor's own logic with
    // the wrong flag, so a PHEV the API misreports as gas would have every
    // refresh through this path (WearCommandRunner.refresh, used by the
    // watch's standalone/command-triggered refreshes) clobber percent/rangeMi
    // with fuel data instead of battery data.
    val pct = status.percentFor(hasBattery)
    val range = status.rangeMiFor(hasBattery)
    // Hoisted to a local: a nullable property of another class is only
    // smart-castable under conditions this file has already been bitten by
    // once (see rangeMi's note in AppViewModel). A local is free and removes
    // the question.
    val ev = status.evStatus
    return copy(
        percent = pct ?: percent,
        rangeMi = range ?: rangeMi,
        locked = status.doorLock ?: locked,
        charging = status.evStatus?.batteryCharge ?: charging,
        climateOn = status.airCtrlOn ?: climateOn,
        engineOn = status.engine ?: engineOn,
        // GeoLocation is flat (latitude/longitude/speed); VehicleLocation nests them under
        // coord/speed.value. Two different shapes for the same idea, so they are spelled out
        // separately rather than looking interchangeable.
        lat = status.vehicleLocation?.coord?.lat ?: location?.latitude ?: lat,
        lon = status.vehicleLocation?.coord?.lon ?: location?.longitude ?: lon,
        speedMph = status.vehicleLocation?.speed?.value ?: location?.speed ?: speedMph,
        updated = status.dateTime ?: updated,
        // The charge limit, which this function never carried -- so the only
        // path that set it was the phone app's own snapshotOf(). Every OTHER
        // refresh goes through here (the watch standalone, the QS tiles, the
        // widget's own), and each of those left the limit at whatever the
        // phone last wrote, or at null forever for a car the phone app had
        // never refreshed while plugged in. The dot that marks it is on five
        // surfaces now; four of them were reading a field nothing kept current.
        //
        // NOT the `new ?: old` shape the fields above use, deliberately. A status
        // with no evStatus at all (a gas car, a partial fetch) is still the only
        // case where the old value stands.
        //
        // displayChargeLimit, not targetForCurrentPlug -- this WAS
        // "unplugged genuinely means no limit applies, so trust an unplugged
        // status's null completely", on the reasoning that the old limit MARKER
        // should disappear once you unplug. That reasoning doesn't hold any more:
        // the marker is gone, replaced by a three-segment bar that's meant to show
        // the car's own configured limit "always," not just mid-session (see the
        // blue stuck-at-limit fill, which was explicitly asked to work the same way
        // regardless of active charging) -- so an EV status that's merely unplugged
        // right now still resolves to its AC default via displayChargeLimit rather
        // than genuinely clearing the field. Reported from a real device: a parked,
        // unplugged car's hero card lost its whole limit-aware bar.
        chargeLimitPct = if (ev != null) ev.displayChargeLimit() else chargeLimitPct,
        // merged() folds in a status we JUST fetched, so this data is now current.
        fetchedAt = System.currentTimeMillis(),
    )
}

/**
 * This snapshot, with any status field it does not know filled in from [old].
 *
 * The snapshot-to-snapshot counterpart of [merged] (which folds in a live
 * [VehicleStatus]), for [SnapshotStore.saveVehiclesKeepingStatus]. Same `new ?: old`
 * rule per field, so a fresh value always wins and an absent one never blanks a
 * stored one.
 *
 * Identity and user-entered fields are deliberately NOT carried forward -- name,
 * model, powertrain flags, regId, generation, brand, odometer, plate and the
 * service figures all come from the caller, which just read them. Carrying those
 * would make a renamed or re-plated car un-updatable.
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
    // 0 is this field's "unknown", not a timestamp, so it takes the same rule.
    fetchedAt = if (fetchedAt > 0L) fetchedAt else old.fetchedAt,
)

/** The exact shape persisted to disk as a single JSON string under one
 *  DataStore key — kept as one blob (rather than one DataStore entry per
 *  field) so a read or write is always a single atomic operation over the
 *  whole vehicle list + selection together. */
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

/**
 * Reads and writes the on-disk [VehicleSnapshot] cache described above.
 * Every mutating method follows the same read-modify-write shape via
 * DataStore's [edit]: decode whatever's currently on disk, apply the change,
 * re-encode the whole payload back. DataStore's edit block itself is
 * transactional (backed by a single file + mutex), so concurrent callers
 * from different processes (the widget refreshing while the watch relay also
 * writes, for instance) don't stomp on each other's writes.
 */
/**
 * Apply [updates] onto [existing] by VIN. Extracted from [SnapshotStore.updateVehicles]
 * so the batch-merge semantics are testable with no Android context in the way.
 *
 * Three properties the callers depend on:
 *  - ORDER is the existing list's. The vehicle order is user-visible (it's the car
 *    pager's order), so a refresh must never reshuffle it.
 *  - A VIN in [updates] that isn't in [existing] is IGNORED, not appended. Adding cars
 *    is saveVehicles' job; a stale update for a car that has since been removed from
 *    the account must not resurrect it.
 *  - On a duplicate VIN within [updates], the LAST entry wins, matching what repeated
 *    single-vehicle writes in the same order would have produced.
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

    /** Live stream of the current snapshot data — re-emits whenever the
     *  underlying DataStore file changes, so a Compose UI collecting this can
     *  react immediately to a write made from a different process. */
    val payload: Flow<SnapshotData> = context.snapshotDataStore.data.map { prefs ->
        decode(prefs[Keys.PAYLOAD])
    }
        // decode() is a full Json parse of every vehicle, and DataStore only guarantees the FILE
        // read is off the main thread -- a map{} transform runs in the collector's context.
        // The widget collects this, and Glance's provideGlance is suspend but NOT guaranteed an
        // IO dispatcher (Google's own guidance, and the reason WidgetPhoto/WidgetMap already
        // wrap their loads). So this parsed the whole blob on the main thread on every widget
        // repaint -- and a command tap deliberately emits twice, optimistic then settled.
        .flowOn(Dispatchers.IO)

    /** One-shot read of the current snapshot data (first() takes just the
     *  latest emission and then stops collecting), for callers that don't
     *  need to keep observing. */
    suspend fun current(): SnapshotData = withContext(Dispatchers.IO) {
        // withContext for the same reason as `payload` above: .first() resumes on the CALLER's
        // dispatcher, and the widget's caller is the main thread. StatusCache.load already does
        // exactly this; this store -- the one every module reads -- had been missed.
        decode(context.snapshotDataStore.data.first()[Keys.PAYLOAD])
    }

    /**
     * Replace the vehicle LIST while keeping each surviving car's last-known status.
     *
     * For the "we just re-fetched the account's vehicles" case, which knows every
     * car's identity (name, model, odometer, plate) and nothing about its state.
     * [saveVehicles] replaces the payload wholesale, so calling it with
     * status-less snapshots wipes percent, range, lock, charge, climate, engine,
     * location and fetchedAt for every car on disk -- and the widget, the twelve
     * Quick Settings tiles, the Wear tile and every complication read exactly that
     * file. The result was that every cold start, every login and every
     * pull-to-refresh blanked all of them until N sequential network round trips
     * had completed, one car at a time through statusMutex. fetchedAt = 0 also
     * trips the widget's own stale gate.
     *
     * Not fixed by passing the in-memory status cache instead: that cache is
     * restored on a SEPARATE viewModelScope.launch from the login path, so
     * whether it has arrived first is a race. This reads what is actually on
     * disk, inside the same edit transaction, so there is no window and no
     * second decode.
     *
     * Carry-forward is per field and only where the incoming value is absent, the
     * same `new ?: old` shape [merged] uses -- so a genuine update always wins and
     * a missing one can never blank a known value. Identity and user-entered
     * fields always take the incoming value, since those were just read fresh.
     *
     * [saveVehicles] is deliberately left alone: sign-out calls it with an empty
     * list to clear everything, and the watch's state writer uses it to apply the
     * phone's authoritative payload. Both want replacement.
     */
    suspend fun saveVehiclesKeepingStatus(vehicles: List<VehicleSnapshot>) {
        if (vehicles.isEmpty()) return
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

    /** Replace the entire vehicle list (e.g. after a full account refresh).
     *  Mechanism: preserves the previously-selected VIN if that car is still
     *  present in the new list; otherwise falls back to the first vehicle so
     *  there's always a selection as long as the list isn't empty. */
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
    suspend fun updateVehicle(snapshot: VehicleSnapshot) = updateVehicles(listOf(snapshot))

    /**
     * Merge several vehicles in ONE store write.
     *
     * Every write here costs a full decode of the whole vehicle payload, a full
     * re-encode of it, and a DataStore commit -- the cost is per WRITE, not per
     * vehicle, because the payload is one JSON blob. So a "refresh all" that called
     * [updateVehicle] once per car paid N decodes, N encodes and N fsyncs to change N
     * cars, where one of each would do. It also produced N emissions on [payload], so
     * every widget, tile and complication observing it repainted N times for one
     * refresh.
     *
     * A VIN in [snapshots] that isn't in the store is ignored rather than added, which
     * is [updateVehicle]'s existing behaviour -- adding cars is [saveVehicles]' job.
     */
    suspend fun updateVehicles(snapshots: List<VehicleSnapshot>) {
        if (snapshots.isEmpty()) return
        context.snapshotDataStore.edit { prefs ->
            val existing = decode(prefs[Keys.PAYLOAD])
            prefs[Keys.PAYLOAD] = json.encodeToString(
                SnapshotPayload.serializer(),
                SnapshotPayload(mergeVehicleUpdates(existing.vehicles, snapshots), existing.selectedVin),
            )
        }
    }

    /**
     * Fold freshly-fetched statuses into the stored snapshots, keyed by VIN, in one
     * atomic read-modify-write.
     *
     * For background pollers, which have a [VehicleStatus] in hand and no snapshot to
     * build one from. The alternative -- [current], then [merged] per car, then
     * [updateVehicles] -- costs an extra full decode of the payload and leaves a window
     * in which another writer (a command's optimistic flip, the app itself) can land
     * between the read and the write and be silently overwritten. Doing the fold inside
     * `edit` closes that window and drops the read.
     *
     * A VIN with no stored snapshot is skipped, matching [updateVehicles]: a poller
     * should not be able to invent a car the app has never seen. Per-field semantics
     * are [merged]'s -- `new ?: old` -- so a partial status can only ADD information,
     * never blank out a lock or charge state the store already had.
     */
    suspend fun mergeStatuses(statuses: Map<String, VehicleStatus>) {
        if (statuses.isEmpty()) return
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

    /** Change which car is the "active" one for widgets/tiles, without
     *  touching the vehicle data itself. */
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

    /** Parse the raw stored JSON string into [SnapshotData]. A null [raw]
     *  (nothing saved yet) or a JSON parse failure (corrupt/incompatible
     *  data — belt-and-suspenders alongside the DataStore-level
     *  corruptionHandler above) both fall back to an empty [SnapshotPayload]
     *  rather than throwing, since every caller of this store expects to be
     *  able to read from it even before anything has ever been written. */
    private fun decode(raw: String?): SnapshotData {
        val payload = raw?.let {
            runCatching { json.decodeFromString(SnapshotPayload.serializer(), it) }.getOrNull()
        } ?: SnapshotPayload()
        return SnapshotData(payload.vehicles, payload.selectedVin)
    }

    /** Decoded view of the store: every known vehicle plus which VIN is
     *  currently selected. */
    data class SnapshotData(
        val vehicles: List<VehicleSnapshot>,
        val selectedVin: String?,
    ) {
        /** The selected vehicle's snapshot, or the first vehicle if the
         *  recorded selection doesn't match any known VIN (e.g. that car was
         *  removed from the account since the selection was last saved), or
         *  null if there are no vehicles at all. */
        val selected: VehicleSnapshot?
            get() = vehicles.firstOrNull { it.vin == selectedVin } ?: vehicles.firstOrNull()
    }
}

/**
 * The car a WATCH glanceable surface (Tile, complication) should show: the one pinned to that
 * surface if [pinnedVin] is set AND still in the snapshot, otherwise the globally-[selected]
 * car so an unconfigured or stale slot is never blank.
 *
 * IMPORTANT -- this "stale pin falls back to selected" rule is a WATCH-only choice. The phone
 * widget and QS tile deliberately do the opposite: a pinned widget shows ITS car or nothing,
 * never silently swapping to another (see CarWidget.provideGlance). Do NOT route those through
 * this helper. The two watch surfaces (ComplicationCarStore.resolveComplicationCar and
 * BlooTileService.pick) had this exact expression copied byte-for-byte; one home keeps the rule
 * -- and any future change to how a stale pin is treated -- from drifting between them.
 */
fun SnapshotStore.SnapshotData.pinnedOrSelected(pinnedVin: String?): VehicleSnapshot? =
    pinnedVin?.let { v -> vehicles.firstOrNull { it.vin == v } } ?: selected
