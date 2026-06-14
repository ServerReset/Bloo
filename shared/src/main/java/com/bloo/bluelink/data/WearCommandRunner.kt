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
        )
        return runCatching {
            val updated = when (command.action) {
                WearAction.TOGGLE_LOCK ->
                    if (snap.locked == true) { repo.unlock(v); snap.copy(locked = false) }
                    else { repo.lock(v); snap.copy(locked = true) }
                WearAction.LOCK -> { repo.lock(v); snap.copy(locked = true) }
                WearAction.UNLOCK -> { repo.unlock(v); snap.copy(locked = false) }
                WearAction.TOGGLE_CLIMATE ->
                    if (snap.climateOn == true) { repo.stopClimate(v); snap.copy(climateOn = false) }
                    else { repo.startClimate(v, climate); snap.copy(climateOn = true) }
                WearAction.CLIMATE_ON -> { repo.startClimate(v, climate); snap.copy(climateOn = true) }
                WearAction.CLIMATE_OFF -> { repo.stopClimate(v); snap.copy(climateOn = false) }
                WearAction.TOGGLE_CHARGE ->
                    if (snap.charging == true) { repo.stopCharge(v); snap.copy(charging = false) }
                    else { repo.startCharge(v); snap.copy(charging = true) }
                WearAction.CHARGE_ON -> { repo.startCharge(v); snap.copy(charging = true) }
                WearAction.CHARGE_OFF -> { repo.stopCharge(v); snap.copy(charging = false) }
                else -> return WearCommandResult(command.vin, command.action, ok = false, message = "Unknown action")
            }
            store.updateVehicle(updated)
            AppLog.log("${command.action} → ${v.name}")
            WearCommandResult(command.vin, command.action, ok = true)
        }.getOrElse { e ->
            AppLog.log("Command failed (${command.action}): ${e.message}")
            WearCommandResult(command.vin, command.action, ok = false, message = e.message ?: "Command failed")
        }
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

    /** Refresh one car (blank [vin] → all), folding fresh status into snapshots. */
    suspend fun refresh(context: Context, vin: String) {
        val store = SnapshotStore(context)
        val targets = store.current().vehicles.let { all ->
            if (vin.isBlank()) all else all.filter { it.vin == vin }
        }
        BlueLinkGate.statusMutex.withLock {
            targets.forEach { snap ->
                runCatching {
                    val v = snap.toVehicle()
                    val repo = repositoryFor(
                        Brand.fromIndicator(v.brandIndicator),
                        SessionStore(context), CredentialStore(context),
                    )
                    repo.status(v, refresh = true)?.let { store.updateVehicle(snap.merged(it)) }
                }
            }
        }
    }
}
