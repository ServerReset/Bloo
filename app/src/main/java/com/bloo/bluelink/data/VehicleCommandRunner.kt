package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

/**
 * Runs a vehicle command with the stored session and folds the expected result into the
 * [SnapshotStore] so snapshot-fed surfaces reflect the new state immediately.
 */
object VehicleCommandRunner {

    /**
     * Marks a climate target that carries an explicit temperature, e.g. "temp:64". See
     * [runClimateStart].
     */
    const val TEMP_PREFIX = "temp:"

    /** Appended to a [TEMP_PREFIX] target to also run the defroster. */
    const val DEFROST_SUFFIX = ":defrost"

    /**
     * Outcome of a single command: whether it succeeded, plus a short human-readable status/error
     * message suitable for a toast or log line.
     */
    data class Result(val ok: Boolean, val message: String)

    /**
     * Executes one command end to end and returns a user-facing [Result]. This is the single place
     * that mechanism (locking, optimistic snapshot update, error formatting) lives -- callers don't
     * reimplement any of it. Order of operations.
     */
    suspend fun run(ctx: Context, vin: String, cmd: String, climateTarget: String): Result {
        // Step 0, before the lock: the weather lookup a "smart" target needs is a request to
        // Open-Meteo, not to the car, so it has no business inside a mutex that exists to stop
        // overlapping requests to the CAR. See prepareSmartClimate.
        val smart = prepareSmartClimate(ctx, vin, cmd, climateTarget)
        // The snapshot read and optimistic write live inside the lock too, so the toggle direction
        // and the flip are atomic with the network dispatch.
        return BlueLinkGate.statusMutex.withLock {
            // Snapshot read + repo construction both funnel through carContext, so a background
            // command surface starts from the shared helper rather than a second copy of the
            // resolve dance.
            val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
                ?: return@withLock Result(false, "Car not found")
            val car = carContext(ctx, snap)
            val v = car.vehicle
            val brand = car.brand
            val repo = car.repository()
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
                    "charge_on" -> { repo.startCharge(v); "Starting charge" }
                    "charge_off" -> { repo.stopCharge(v); "Stopping charge" }
                    "climate" -> runClimate(ctx, repo, v, snap, climateTarget, smart)
                    "climate_on" -> runClimateStart(ctx, repo, v, snap, climateTarget, smart)
                    "climate_off" -> stopClimateAndChain(ctx, repo, v)
                    // No arguments, no state to predict: these two make the car do something
                    // audible/visible and change nothing that any surface displays, which is why
                    // they need no optimistic write below.
                    "lights" -> { repo.flashLights(v); "Flashing lights on ${v.name}" }
                    "horn" -> { repo.hornAndLights(v); "Sounding horn on ${v.name}" }
                    // The percentage rides in on the same string parameter the climate target uses
                    // -- it is the command's argument slot, and giving it a second one for the sake
                    // of naming would change every call site for one command's benefit.
                    "charge_limit" -> {
                        // Guarded centrally here, so EVERY dispatch route (the natural-language
                        // Settings-search command included) is covered, not just the rendered
                        // sliders. Canada can't read its charge targets, so it must not write one
                        // either -- setChargeTargets would POST the unverified evc/setsoc "level"
                        // field.
                        if (!brand.supportsChargeLimits) {
                            error("Charge limit isn't available on ${v.name}")
                        }
                        val pct = climateTarget.toIntOrNull()?.coerceIn(CHARGE_LIMIT_RANGE)
                            ?: error("Bad charge limit")
                        repo.setChargeTargets(v, pct, pct)
                        "Charge limit set to $pct% on ${v.name}"
                    }
                    else -> "Done"
                }
            }.fold(
                onSuccess = { msg ->
                    AppLog.log(msg)
                    // Best-effort: if this write fails, the real status poll will eventually
                    // correct the snapshot anyway, so it's not worth failing the whole command
                    // over.
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
    }

    /**
     * Start/stop climate; when starting, resolve the caller's chosen target. Called from inside
     * [run]'s [BlueLinkGate.statusMutex] critical section, so this itself does not (and must not)
     * take the lock again. Mechanism: 1.
     */
    /**
     * Stop climate AND cancel any pending auto-extend chain. Both of this runner's stop paths
     * ("climate_off" and the "climate" toggle landing on off) go through here so they cannot drift
     * apart.
     */
    private suspend fun stopClimateAndChain(ctx: Context, repo: VehicleRepository, v: Vehicle): String {
        repo.stopClimate(v)
        runCatching { com.bloo.bluelink.work.ClimateExtendWorker.cancel(ctx, v.vin) }
        return "Stopping climate"
    }

    private suspend fun runClimate(
        ctx: Context,
        repo: VehicleRepository,
        v: Vehicle,
        snap: VehicleSnapshot,
        target: String,
        smart: ClimateRequest?,
    ): String {
        if (snap.climateOn == true) return stopClimateAndChain(ctx, repo, v)
        return runClimateStart(ctx, repo, v, snap, target, smart)
    }

    /**
     * The one climate target whose resolution needs the network: build the "smart" [ClimateRequest]
     * BEFORE [run] takes [BlueLinkGate.statusMutex], so the weather lookup doesn't hold an app-wide
     * lock on the car's session hostage.
     */
    private suspend fun prepareSmartClimate(
        ctx: Context,
        vin: String,
        cmd: String,
        target: String,
    ): ClimateRequest? {
        if (target != "smart") return null
        if (cmd != "climate" && cmd != "climate_on") return null
        val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin } ?: return null
        if (cmd == "climate" && snap.climateOn == true) return null
        val lat = snap.lat ?: return null
        val lon = snap.lon ?: return null
        return WeatherApi.fetch(lat, lon)?.let(::smartClimateRequest)
    }

    /**
     * It has no timeout, which is worth stating outright because a timeout is the obvious thing to
     * reach for here and it does not work. [WeatherApi.fetch] is a `withContext` on the IO
     * dispatcher wrapped around OkHttp's BLOCKING `execute`, with no `callTimeout` and no
     * suspension point inside.
     */
    private suspend fun smartClimateInLock(snap: VehicleSnapshot): ClimateRequest {
        val lat = snap.lat
        val lon = snap.lon
        if (lat == null || lon == null) error("No location for smart climate")
        val w = WeatherApi.fetch(lat, lon) ?: error("No weather for smart climate")
        return smartClimateRequest(w)
    }

    /** The smart-climate request for a given weather reading. */
    private fun smartClimateRequest(w: Weather): ClimateRequest = ClimateRequest(
        tempF = smartClimateTargetF(ambientFahrenheit(w.tempC)),
        defrost = false,
        durationMinutes = DEFAULT_CLIMATE_DURATION_MIN,
    )

    /**
     * Force-start climate for the "climate_on" command (explicit start phrasing from settings
     * search).
     */
    private suspend fun runClimateStart(
        ctx: Context,
        repo: VehicleRepository,
        v: Vehicle,
        snap: VehicleSnapshot,
        target: String,
        smart: ClimateRequest?,
    ): String {
        if (snap.isDriving) error("Can't start climate while driving")
        val req = when {
            target == "smart" -> smart ?: smartClimateInLock(snap)
            // "temp:64" -- an explicit temperature in Fahrenheit, which is what search produces for
            // "start climate at the coldest temperature on X". Additive to the existing string
            // protocol rather than a new parameter: every other caller keeps passing what it always
            // did, and a preset id can never collide with this because ids are UUIDs.
            target.startsWith(TEMP_PREFIX) -> {
                // "temp:64" or "temp:82:defrost". Suffix rather than a second prefix so the two can
                // be asked for together, which is what "defrost the windscreen" actually wants:
                // heat AND defrost.
                val body = target.removePrefix(TEMP_PREFIX)
                val defrost = body.endsWith(DEFROST_SUFFIX)
                val f = body.removeSuffix(DEFROST_SUFFIX).toIntOrNull() ?: error("Bad temperature")
                ClimateRequest(
                    tempF = f.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last),
                    defrost = defrost,
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

    /** The snapshot a command is expected to produce, for instant feedback. */
    fun optimistic(snap: VehicleSnapshot, cmd: String): VehicleSnapshot {
        val action = when (cmd) {
            "doors" -> CarAction.TOGGLE_LOCK
            "lock" -> CarAction.LOCK
            "unlock" -> CarAction.UNLOCK
            "charge" -> CarAction.TOGGLE_CHARGE
            "charge_on" -> CarAction.CHARGE_ON
            "charge_off" -> CarAction.CHARGE_OFF
            "climate" -> CarAction.TOGGLE_CLIMATE
            "climate_on" -> CarAction.CLIMATE_ON
            "climate_off" -> CarAction.CLIMATE_OFF
            else -> return snap
        }
        return CarCommandRunner.optimistic(snap, action)
    }
}
