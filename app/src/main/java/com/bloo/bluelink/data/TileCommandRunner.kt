package com.bloo.bluelink.data

import android.content.Context

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
        return runCatching {
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
        val req = when {
            target == "smart" -> {
                val lat = snap.lat
                val lon = snap.lon
                if (lat == null || lon == null) error("No location for smart climate")
                val w = WeatherApi.fetch(lat, lon) ?: error("No weather for smart climate")
                val ambientF = (w.tempC * 9.0 / 5.0 + 32).toInt()
                val tempF = if (ambientF >= 70) (ambientF - 10).coerceIn(60, 85)
                            else (ambientF + 10).coerceIn(60, 85)
                ClimateRequest(tempF = tempF, defrost = false, durationMinutes = 10)
            }
            target != "default" -> {
                val preset = SettingsStore(ctx).climatePresets(v.vin).firstOrNull { it.id == target }
                    ?: error("Preset unavailable")
                preset.request
            }
            else -> ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10)
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

    /** The snapshot a tile command is expected to produce, for instant feedback. */
    fun optimistic(snap: VehicleSnapshot, cmd: String): VehicleSnapshot = when (cmd) {
        "doors" -> snap.copy(locked = !(snap.locked ?: false))
        "lock" -> snap.copy(locked = true)
        "unlock" -> snap.copy(locked = false)
        "charge" -> snap.copy(charging = !(snap.charging ?: false))
        "climate" -> snap.copy(climateOn = !(snap.climateOn ?: false))
        else -> snap
    }
}
