package com.bloo.bluelink.ui

/**
 * The on-device AI prompt builders: plain-text descriptions of a car's
 * state, ordered by importance, and the charge-time phrasing -- the only
 * strings the model sees as facts, so they pin exactly: a sentence that
 * says the doors are unlocked when they were never reported was a real
 * false-fact bug (see carText's Priority 1 comment), and it is pinned
 * below the same way. Extractable from AppViewModel.kt because they are
 * pure functions of (Vehicle, status, UiState).
 */
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.flow.first
import java.util.Locale

internal fun summaryPrompt(v: Vehicle, status: VehicleStatus?, state: UiState): String =
    "${v.name} vehicle status:\n" + carText(v, status, state)

    internal fun carText(v: Vehicle, status: VehicleStatus?, state: UiState): String {
        val parts = mutableListOf<String>()
        parts += "Vehicle: ${v.name} (${v.model})."
        if (status == null) {
            parts += "No live status has been fetched yet for this car."
            return parts.joinToString(" ")
        }
        // Priority 1 — doors.
        // Only when the car has actually REPORTED it. `doorLock == true` collapsed null into
        // "unlocked", so a car that has never reported its doors had "The doors are unlocked."
        // fed to the AI as a fact -- and the summary then tells the user their car is unlocked.
        status.doorLock?.let { parts += "The doors are ${if (it) "locked" else "unlocked"}." }
        // Priority 2 — charging + time to full (EV/PHEV only).
        if (state.hasBattery(v)) {
            if (status.evStatus?.batteryCharge == true) {
                val mins = status.evStatus?.minutesToFull
                parts += if (mins != null) {
                    "It is charging, with about ${fmtTimeToFull(mins)} until fully charged."
                } else {
                    "It is currently charging."
                }
            } else {
                parts += "It is not charging."
            }
        }
        // Priority 3 — driving / parked.
        state.drivingLabel(v)?.let { parts += "The car is currently ${it.lowercase(Locale.US)}." }
        status.engine?.let { parts += "The engine is ${if (it) "on" else "off"}." }
        // Remaining status, most useful first.
        if (state.hasBattery(v)) status.evStatus?.batteryStatus?.let { parts += "The drive battery is at $it%." }
        if (state.hasFuel(v)) status.fuelLevel?.let { parts += "Fuel is at $it%." }
        status.rangeMiFor(state.hasBattery(v))?.let { parts += "Estimated driving range is $it miles." }
        status.airCtrlOn?.let { parts += "Climate is ${if (it) "on" else "off"}." }
        status.battery?.batSoc?.let { parts += "The 12V starter battery is at $it%." }
        v.odometer?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "The odometer reads $it miles." }
        state.placeNames[v.vin]?.let { parts += "Last known location: $it." }
        // Warnings.
        if (status.tirePressureLamp?.hasWarning == true) parts += "There is a tire pressure warning."
        if (status.washerFluidStatus == true) parts += "The washer fluid is low."
        if (status.breakOilStatus == true) parts += "The brake fluid needs attention."
        if (status.smartKeyBatteryWarning == true) parts += "The key fob battery is low."
        return parts.joinToString(" ")
    }

    internal fun fmtTimeToFull(minutes: Int): String {
        if (minutes < 60) return "$minutes minutes"
        val h = minutes / 60
        val m = minutes % 60
        val hStr = if (h == 1) "1 hour" else "$h hours"
        return if (m == 0) hStr else "$hStr $m minutes"
    }
