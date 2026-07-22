package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

/**
 * Runs a Quick-Settings-tile command with the stored session and folds the
 * expected result into the [SnapshotStore] so the tile (and widgets) reflect the
 * new state immediately. Toggle commands flip against the last-known snapshot;
 * the climate command honours the tile's chosen target (a saved preset, smart
 * climate, or basic). Shared by the tile (background mode) and the transparent
 * tile-action activity (open-and-close mode).
 */
object TileCommandRunner {

    data class Result(val ok: Boolean, val message: String)

    suspend fun run(ctx: Context, vin: String, cmd: String, climateTarget: String): Result {
        val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
            ?: return Result(false, "Car not found")
        val v = snap.toVehicle()
        val repo = repositoryFor(Brand.fromIndicator(v.brandIndicator), SessionStore(ctx), CredentialStore(ctx))
        // Same lock WearCommandRunner.execute()/the phone UI's own command path
        // already take -- BlueLink 502s on overlapping requests for the same
        // account, and this was the one command-executing path (Quick Settings
        // tile taps) that skipped it, so a tile tap racing a background status
        // refresh or another in-flight command had no protection at all.
        return BlueLinkGate.statusMutex.withLock {
            runCatching {
                when (cmd) {
                    "doors" ->
                        if (snap.locked == true) { repo.unlock(v); "Unlocking ${v.name}" }
                        else { repo.lock(v); "Locking ${v.name}" }
                    "lock" -> { repo.lock(v); "Locking ${v.name}" }
                    "unlock" -> { repo.unlock(v); "Unlocking ${v.name}" }
                    "charge" ->
                        if (snap.charging == true) { repo.stopCharge(v); "Stopping charge" }
                        else { repo.startCharge(v); "Starting charge" }
                    "climate" -> runClimate(ctx, repo, v, snap, climateTarget)
                    else -> "Done"
                }
            }
        }.fold(
            onSuccess = { msg ->
                AppLog.log(msg)
                runCatching { SnapshotStore(ctx).updateVehicle(optimistic(snap, cmd)) }
                Result(true, msg)
            },
            onFailure = { e ->
                val err = e.message ?: "Command failed"
                AppLog.log("⚠ $err (${cmd} → ${v.name})")
                Result(false, err)
            },
        )
    }

    /** Start/stop climate; when starting, resolve the tile's chosen target. */
    private suspend fun runClimate(
        ctx: Context,
        repo: VehicleRepository,
        v: Vehicle,
        snap: VehicleSnapshot,
        target: String,
    ): String {
        if (snap.climateOn == true) { repo.stopClimate(v); return "Stopping climate" }
        // The car rejects remote climate commands while it's moving (same
        // gate the main phone UI's AppViewModel.isDriving() already applies
        // to its own Start button) -- this was the one climate-starting path
        // with no such check at all, so a Quick Settings tile tap while
        // driving used to just silently fail against the car with no
        // explanation surfaced to the user.
        if (snap.isDriving) error("Can't start climate while driving")
        val req = when {
            target == "smart" -> {
                val lat = snap.lat
                val lon = snap.lon
                if (lat == null || lon == null) error("No location for smart climate")
                val w = WeatherApi.fetch(lat, lon) ?: error("No weather for smart climate")
                ClimateRequest(
                    tempF = smartClimateTargetF(ambientFahrenheit(w.tempC)),
                    defrost = false,
                    durationMinutes = DEFAULT_CLIMATE_DURATION_MIN,
                )
            }
            target != "default" -> {
                val preset = SettingsStore(ctx).climatePresets(v.vin).firstOrNull { it.id == target }
                    ?: error("Preset unavailable")
                preset.request
            }
            else -> ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F, defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN)
        }
        repo.startClimate(v, req)
        return "Starting climate"
    }

    /** Short "doing it" toast text for a tap, based on the last-known state. */
    fun ackText(cmd: String, snap: VehicleSnapshot?): String = when (cmd) {
        "doors" -> if (snap?.locked == true) "Unlocking…" else "Locking…"
        "lock" -> "Locking…"
        "unlock" -> "Unlocking…"
        "climate" -> if (snap?.climateOn == true) "Stopping climate…" else "Starting climate…"
        "charge" -> if (snap?.charging == true) "Stopping charge…" else "Starting charge…"
        else -> "Sending…"
    }

    /** The snapshot a tile command is expected to produce, for instant feedback.
     *  Delegates to [WearCommandRunner.optimistic] (mapping the tile's own
     *  "doors"/"lock"/"unlock"/"charge"/"climate" vocabulary onto [WearAction])
     *  instead of re-deriving the same lock/charge/climate flips independently
     *  -- this used to be a byte-for-byte duplicate of that function. */
    fun optimistic(snap: VehicleSnapshot, cmd: String): VehicleSnapshot {
        val action = when (cmd) {
            "doors" -> WearAction.TOGGLE_LOCK
            "lock" -> WearAction.LOCK
            "unlock" -> WearAction.UNLOCK
            "charge" -> WearAction.TOGGLE_CHARGE
            "climate" -> WearAction.TOGGLE_CLIMATE
            else -> return snap
        }
        return WearCommandRunner.optimistic(snap, action)
    }
}
