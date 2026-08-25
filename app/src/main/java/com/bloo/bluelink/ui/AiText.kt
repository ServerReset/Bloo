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
import android.app.Application
import android.location.Geocoder
import androidx.biometric.BiometricManager
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkException
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.PinRecord
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.CanadaAuth
import com.bloo.bluelink.data.CanadaRepository
import com.bloo.bluelink.data.EuRepository
import com.bloo.bluelink.data.KiaAuth
import com.bloo.bluelink.data.KiaRepository
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.shouldRelockAfter
import com.bloo.bluelink.data.wireKey
import com.bloo.bluelink.data.maskEmail
import com.bloo.bluelink.data.ReservChargeInfos
import com.bloo.bluelink.data.TargetSOC
import com.bloo.bluelink.data.STALE_STATUS_MS
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.toGeoLocation
import com.bloo.bluelink.data.DEFAULT_SECTIONS
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.toClimateSync
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
