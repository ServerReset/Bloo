package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

/**
 * Executes a [CarCommand] against the car backend using the stored session, and folds the result
 * into the on-disk [SnapshotStore].
 */
object CarCommandRunner {

    /**
     * Any thrown exception during dispatch is caught by the outer `runCatching`/`getOrElse` and
     * turned into a failed [CarCommandResult] with the exception's message, rather than
     * propagating.
     */
    suspend fun execute(context: Context, command: CarCommand): CarCommandResult {
        val store = SnapshotStore(context)
        return BlueLinkGate.statusMutex.withLock {
            // Read the target vehicle's snapshot INSIDE the lock so the toggle direction is decided
            // from state serialized against every other command path.
            val snap = store.current().vehicles.firstOrNull { it.vin == command.vin }
                ?: return@withLock CarCommandResult(command.vin, command.action, ok = false, message = "Car not found")
            val v = snap.toVehicle()
            val repo = repositoryFor(
                Brand.fromIndicator(v.brandIndicator),
                SessionStore(context), CredentialStore(context),
            )
            val climate = ClimateRequest(
                tempF = command.tempF,
                defrost = command.defrost,
                durationMinutes = command.durationMinutes,
                steeringWheelHeat = WheelHeatLevel.fromApi(command.steeringWheelHeat),
                seatFrontLeft = SeatLevel.fromApi(command.seatFrontLeft),
                seatFrontRight = SeatLevel.fromApi(command.seatFrontRight),
                seatRearLeft = SeatLevel.fromApi(command.seatRearLeft),
                seatRearRight = SeatLevel.fromApi(command.seatRearRight),
            )
            runCatching {
                // The optimistic climate flag, but only for a brand whose status can later CONFIRM
                // it (see Brand.reportsClimateState). Unknown is the honest answer.
                fun climateFlag(on: Boolean): Boolean? =
                    if (v.brand.reportsClimateState) on else null
                val updated = when (command.action) {
                    CarAction.TOGGLE_LOCK ->
                        if (snap.locked == true) { repo.unlock(v); snap.copy(locked = false) }
                        else { repo.lock(v); snap.copy(locked = true) }
                    CarAction.LOCK -> { repo.lock(v); snap.copy(locked = true) }
                    CarAction.UNLOCK -> { repo.unlock(v); snap.copy(locked = false) }
                    CarAction.TOGGLE_CLIMATE ->
                        if (snap.climateOn == true) { repo.stopClimate(v); snap.copy(climateOn = climateFlag(false)) }
                        else {
                            // The car rejects remote climate commands while it's moving (same gate
                            // the phone UI's own Start button applies) -- this runner is the
                            // bare-Context command path those callers all funnel through, and none
                            // of them checked this before.
                            if (snap.isDriving) error("Can't start climate while driving")
                            repo.startClimate(v, climate); snap.copy(climateOn = climateFlag(true))
                        }
                    CarAction.CLIMATE_ON -> {
                        if (snap.isDriving) error("Can't start climate while driving")
                        repo.startClimate(v, climate); snap.copy(climateOn = climateFlag(true))
                    }
                    CarAction.CLIMATE_OFF -> { repo.stopClimate(v); snap.copy(climateOn = climateFlag(false)) }
                    CarAction.TOGGLE_CHARGE ->
                        if (snap.charging == true) { repo.stopCharge(v); snap.copy(charging = false) }
                        else { repo.startCharge(v); snap.copy(charging = true) }
                    CarAction.CHARGE_ON -> { repo.startCharge(v); snap.copy(charging = true) }
                    CarAction.CHARGE_OFF -> { repo.stopCharge(v); snap.copy(charging = false) }
                    CarAction.SET_CHARGE_LIMITS -> { repo.setChargeTargets(v, command.acLimit, command.dcLimit); snap }
                    // Momentary, not stateful -- no snap field to flip, so these fall through
                    // optimistic()/resolveToggle()/ stateFor() below untouched (their `else`
                    // branches).
                    CarAction.FLASH_LIGHTS -> { repo.flashLights(v); snap }
                    CarAction.HORN_AND_LIGHTS -> { repo.hornAndLights(v); snap }
                    else -> return@withLock CarCommandResult(command.vin, command.action, ok = false, message = "Unknown action")
                }
                store.updateVehicle(updated)
                AppLog.log("${command.action} → ${v.name}")
                CarCommandResult(command.vin, command.action, ok = true)
            }.getOrElse { e ->
                AppLog.log("Command failed (${command.action}): ${e.message}")
                CarCommandResult(command.vin, command.action, ok = false, message = e.message ?: "Command failed")
            }
        }
    }

    /** Resolve a TOGGLE_* verb into its explicit direction from [snap]. */
    fun resolveToggle(snap: VehicleSnapshot, action: String): String = when (action) {
        CarAction.TOGGLE_LOCK ->
            if (snap.locked == true) CarAction.UNLOCK else CarAction.LOCK
        CarAction.TOGGLE_CLIMATE ->
            if (snap.climateOn == true) CarAction.CLIMATE_OFF else CarAction.CLIMATE_ON
        CarAction.TOGGLE_CHARGE ->
            if (snap.charging == true) CarAction.CHARGE_OFF else CarAction.CHARGE_ON
        else -> action
    }

    /**
     * Accepts TOGGLE_* as well as the resolved verbs so it can be called on either side of
     * [resolveToggle].
     */
    fun stateFor(snap: VehicleSnapshot, action: String): Boolean? = when (action) {
        CarAction.TOGGLE_LOCK, CarAction.LOCK, CarAction.UNLOCK -> snap.locked
        CarAction.TOGGLE_CLIMATE, CarAction.CLIMATE_ON, CarAction.CLIMATE_OFF -> snap.climateOn
        CarAction.TOGGLE_CHARGE, CarAction.CHARGE_ON, CarAction.CHARGE_OFF -> snap.charging
        else -> null
    }

    /** Puts a value read by [stateFor] back into the field [action] touches — the revert half. */
    fun withState(snap: VehicleSnapshot, action: String, value: Boolean?): VehicleSnapshot = when (action) {
        CarAction.TOGGLE_LOCK, CarAction.LOCK, CarAction.UNLOCK -> snap.copy(locked = value)
        CarAction.TOGGLE_CLIMATE, CarAction.CLIMATE_ON, CarAction.CLIMATE_OFF -> snap.copy(climateOn = value)
        CarAction.TOGGLE_CHARGE, CarAction.CHARGE_ON, CarAction.CHARGE_OFF -> snap.copy(charging = value)
        else -> snap
    }

    /**
     * The snapshot a command is expected to produce, for instant optimistic UI. Climate is gated by
     * [Brand.reportsClimateState], the same flag [execute]'s own `climateFlag` helper reads.
     */
    fun optimistic(snap: VehicleSnapshot, action: String): VehicleSnapshot {
        val climateKnown = Brand.fromIndicator(snap.brandIndicator).reportsClimateState
        return when (action) {
            CarAction.TOGGLE_LOCK -> snap.copy(locked = !(snap.locked ?: false))
            CarAction.LOCK -> snap.copy(locked = true)
            CarAction.UNLOCK -> snap.copy(locked = false)
            CarAction.TOGGLE_CLIMATE ->
                snap.copy(climateOn = if (climateKnown) !(snap.climateOn ?: false) else null)
            CarAction.CLIMATE_ON -> snap.copy(climateOn = if (climateKnown) true else null)
            CarAction.CLIMATE_OFF -> snap.copy(climateOn = if (climateKnown) false else null)
            CarAction.TOGGLE_CHARGE -> snap.copy(charging = !(snap.charging ?: false))
            CarAction.CHARGE_ON -> snap.copy(charging = true)
            CarAction.CHARGE_OFF -> snap.copy(charging = false)
            else -> snap
        }
    }

    /**
     * Refresh one car (blank [vin] → all), folding fresh status into snapshots. [force] true wakes
     * the car for a live pull (on-demand button); false reads the server's last-known status —
     * light enough for frequent background polls that keep the stored snapshots fresh without
     * draining the car's 12V battery.
     */
    /** Most callers legitimately ignore the result. */
    suspend fun refresh(
        context: Context,
        vin: String,
        force: Boolean = true,
        /**
         * Receives every VehicleStatus this call actually fetched, keyed by VIN. `refresh` folds
         * each status into the snapshot and drops the rest, which is all most callers need -- the
         * phone keeps its own StatusCache.
         */
        onStatuses: (suspend (Map<String, VehicleStatus>) -> Unit)? = null,
    ): Boolean {
        val store = SnapshotStore(context)
        val targets = store.current().vehicles.let { all ->
            if (vin.isBlank()) all else all.filter { it.vin == vin }
        }
        // Declared outside the lock only so the success signal below can read it; it is still
        // populated and written entirely inside it.
        val merged = mutableListOf<VehicleSnapshot>()
        // The full statuses, for [onStatuses]. The snapshot fold above keeps only the handful of
        // fields VehicleSnapshot carries; this keeps the whole thing.
        val fetched = mutableMapOf<String, VehicleStatus>()
        BlueLinkGate.statusMutex.withLock {
            // One repo instance per brand, reused across that brand's vehicles in this loop -- a
            // fresh KiaRepository per vehicle threw away its account-wide vehicle-list cache each
            // time, so "refresh all" on N Kia cars fired N redundant full-account list calls (each
            // already covering all N cars) instead of one.
            val reposByBrand = mutableMapOf<Brand, VehicleRepository>()
            // Collected and written ONCE at the end rather than per car.
            targets.forEach { snap ->
                runCatching {
                    val v = snap.toVehicle()
                    val brand = Brand.fromIndicator(v.brandIndicator)
                    val repo = reposByBrand.getOrPut(brand) {
                        repositoryFor(brand, SessionStore(context), CredentialStore(context))
                    }
                    repo.status(v, refresh = force)?.let {
                        // Only when the status carried no GPS.
                        val fix = if (it.vehicleLocation == null) {
                            runCatching { repo.location(v) }.getOrNull()
                        } else {
                            null
                        }
                        merged += snap.merged(it, fix)
                        fetched[v.vin] = it
                    }
                }
            }
            // Still INSIDE the lock, deliberately.
            store.updateVehicles(merged)
        }
        // Outside the lock: the callback is the caller's code and must not run holding the app-wide
        // status mutex.
        if (fetched.isNotEmpty()) onStatuses?.invoke(fetched)
        return merged.isNotEmpty()
    }
}
