package com.bloo.bluelink.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

/**
 * Executes a [WearCommand] against the car backend using the stored session,
 * and folds the result into the on-disk [SnapshotStore]. Lives in :shared so the
 * phone (relaying the watch's command) and the watch (running standalone on its
 * own connection) share one implementation — the same stored-session pattern the
 * Quick-Settings tiles use.
 */
object WearCommandRunner {

    suspend fun execute(context: Context, command: WearCommand): WearCommandResult {
        val store = SnapshotStore(context)
        val snap = store.current().vehicles.firstOrNull { it.vin == command.vin }
            ?: return WearCommandResult(command.vin, command.action, ok = false, message = "Car not found")
        val v = snap.toVehicle()
        val repo = repositoryFor(
            Brand.fromIndicator(v.brandIndicator),
            SessionStore(context), CredentialStore(context),
        )
        val climate = ClimateRequest(
            tempF = command.tempF,
            defrost = command.defrost,
            durationMinutes = command.durationMinutes,
            steeringWheelHeat = command.steeringWheelHeat,
            seatFrontLeft = SeatLevel.fromApi(command.seatFrontLeft),
            seatFrontRight = SeatLevel.fromApi(command.seatFrontRight),
            seatRearLeft = SeatLevel.fromApi(command.seatRearLeft),
            seatRearRight = SeatLevel.fromApi(command.seatRearRight),
        )
        // Same lock refresh() and the phone UI's own command path already use --
        // BlueLink 502s on overlapping requests for the same account, and this
        // was the one command-executing path that skipped it, so a resent watch
        // command (e.g. after a slow BLE ack) could fire the same command twice
        // concurrently, or race a phone-UI-driven command, with no protection.
        return BlueLinkGate.statusMutex.withLock {
            runCatching {
                val updated = when (command.action) {
                    WearAction.TOGGLE_LOCK ->
                        if (snap.locked == true) { repo.unlock(v); snap.copy(locked = false) }
                        else { repo.lock(v); snap.copy(locked = true) }
                    WearAction.LOCK -> { repo.lock(v); snap.copy(locked = true) }
                    WearAction.UNLOCK -> { repo.unlock(v); snap.copy(locked = false) }
                    WearAction.TOGGLE_CLIMATE ->
                        if (snap.climateOn == true) { repo.stopClimate(v); snap.copy(climateOn = false) }
                        else {
                            // The car rejects remote climate commands while it's
                            // moving (same gate the phone UI's own Start button
                            // applies) -- this is the standalone watch path and
                            // the widget/relay path (both funnel through here),
                            // neither of which checked this before.
                            if (snap.isDriving) error("Can't start climate while driving")
                            repo.startClimate(v, climate); snap.copy(climateOn = true)
                        }
                    WearAction.CLIMATE_ON -> {
                        if (snap.isDriving) error("Can't start climate while driving")
                        repo.startClimate(v, climate); snap.copy(climateOn = true)
                    }
                    WearAction.CLIMATE_OFF -> { repo.stopClimate(v); snap.copy(climateOn = false) }
                    WearAction.TOGGLE_CHARGE ->
                        if (snap.charging == true) { repo.stopCharge(v); snap.copy(charging = false) }
                        else { repo.startCharge(v); snap.copy(charging = true) }
                    WearAction.CHARGE_ON -> { repo.startCharge(v); snap.copy(charging = true) }
                    WearAction.CHARGE_OFF -> { repo.stopCharge(v); snap.copy(charging = false) }
                    WearAction.SET_CHARGE_LIMITS -> { repo.setChargeTargets(v, command.acLimit, command.dcLimit); snap }
                    // Momentary, not stateful -- no snap field to flip, so
                    // these fall through optimistic()/resolveToggle()/
                    // inverse() below untouched (their `else` branches).
                    WearAction.FLASH_LIGHTS -> { repo.flashLights(v); snap }
                    WearAction.HORN_AND_LIGHTS -> { repo.hornAndLights(v); snap }
                    else -> return@withLock WearCommandResult(command.vin, command.action, ok = false, message = "Unknown action")
                }
                store.updateVehicle(updated)
                AppLog.log("${command.action} → ${v.name}")
                WearCommandResult(command.vin, command.action, ok = true)
            }.getOrElse { e ->
                AppLog.log("Command failed (${command.action}): ${e.message}")
                WearCommandResult(command.vin, command.action, ok = false, message = e.message ?: "Command failed")
            }
        }
    }

    /**
     * Resolve a TOGGLE_* verb into its explicit direction from [snap]. Any caller
     * that persists [optimistic] BEFORE the command executes MUST resolve first:
     * [execute] decides toggle direction by re-reading the store, so a snapshot
     * that was already optimistically flipped would invert the command (tap
     * "unlock" on a locked car -> store flips to unlocked -> execute sees
     * unlocked -> sends LOCK).
     */
    fun resolveToggle(snap: VehicleSnapshot, action: String): String = when (action) {
        WearAction.TOGGLE_LOCK ->
            if (snap.locked == true) WearAction.UNLOCK else WearAction.LOCK
        WearAction.TOGGLE_CLIMATE ->
            if (snap.climateOn == true) WearAction.CLIMATE_OFF else WearAction.CLIMATE_ON
        WearAction.TOGGLE_CHARGE ->
            if (snap.charging == true) WearAction.CHARGE_OFF else WearAction.CHARGE_ON
        else -> action
    }

    /** The verb whose [optimistic] write undoes [action]'s — for reverting a
     *  failed command's optimistic flip. (TOGGLE_* maps to itself since a second
     *  flip restores the original state.) */
    fun inverse(action: String): String = when (action) {
        WearAction.LOCK -> WearAction.UNLOCK
        WearAction.UNLOCK -> WearAction.LOCK
        WearAction.CLIMATE_ON -> WearAction.CLIMATE_OFF
        WearAction.CLIMATE_OFF -> WearAction.CLIMATE_ON
        WearAction.CHARGE_ON -> WearAction.CHARGE_OFF
        WearAction.CHARGE_OFF -> WearAction.CHARGE_ON
        else -> action
    }

    /** The snapshot a command is expected to produce, for instant optimistic UI. */
    fun optimistic(snap: VehicleSnapshot, action: String): VehicleSnapshot = when (action) {
        WearAction.TOGGLE_LOCK -> snap.copy(locked = !(snap.locked ?: false))
        WearAction.LOCK -> snap.copy(locked = true)
        WearAction.UNLOCK -> snap.copy(locked = false)
        WearAction.TOGGLE_CLIMATE -> snap.copy(climateOn = !(snap.climateOn ?: false))
        WearAction.CLIMATE_ON -> snap.copy(climateOn = true)
        WearAction.CLIMATE_OFF -> snap.copy(climateOn = false)
        WearAction.TOGGLE_CHARGE -> snap.copy(charging = !(snap.charging ?: false))
        WearAction.CHARGE_ON -> snap.copy(charging = true)
        WearAction.CHARGE_OFF -> snap.copy(charging = false)
        else -> snap
    }

    /**
     * Refresh one car (blank [vin] → all), folding fresh status into snapshots.
     * [force] true wakes the car for a live pull (on-demand button); false reads
     * the server's last-known status — light enough for frequent background polls
     * that keep widgets/tiles fresh without draining the car's 12V battery.
     */
    suspend fun refresh(context: Context, vin: String, force: Boolean = true) {
        val store = SnapshotStore(context)
        val targets = store.current().vehicles.let { all ->
            if (vin.isBlank()) all else all.filter { it.vin == vin }
        }
        BlueLinkGate.statusMutex.withLock {
            // One repo instance per brand, reused across that brand's vehicles
            // in this loop -- a fresh KiaRepository per vehicle threw away its
            // account-wide vehicle-list cache each time, so "refresh all" on N
            // Kia cars fired N redundant full-account list calls (each already
            // covering all N cars) instead of one.
            val reposByBrand = mutableMapOf<Brand, VehicleRepository>()
            targets.forEach { snap ->
                runCatching {
                    val v = snap.toVehicle()
                    val brand = Brand.fromIndicator(v.brandIndicator)
                    val repo = reposByBrand.getOrPut(brand) {
                        repositoryFor(brand, SessionStore(context), CredentialStore(context))
                    }
                    repo.status(v, refresh = force)?.let { store.updateVehicle(snap.merged(it)) }
                }
            }
        }
    }
}
