package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.Vehicle

/**
 * How often the selected car's status is fetched while it is charging and the app is on screen, so its charge bar follows the car. The
 * background charge poll (LiveChargePollWorker) is for the notification only, and runs far less often.
 */
internal const val CHARGING_POLL_MS = 30_000L

/** The car the garage is on, or null when there is none. */
private fun AppViewModel.selectedVehicleOrNull(): Vehicle? =
    _state.value.vehicles.getOrNull(_currentIndex.value)

/** Whether [v] is charging, by the status the app holds for it. */
internal fun AppViewModel.isChargingNow(v: Vehicle?): Boolean =
    v != null && _state.value.statusFor(v)?.evStatus?.batteryCharge == true

/**
 * Fetches the selected car's status now, quietly, if it is charging: no spinner, and no error for a background read. Called when the app
 * comes to the screen and when a car's page settles, so the charge bar is current as soon as the car is looked at.
 */
fun AppViewModel.refreshSelectedIfCharging() {
    val v = selectedVehicleOrNull() ?: return
    if (!isChargingNow(v)) return
    loadStatus(v, refresh = true, errorMessage = "Charge refresh failed", surfaceErrors = false)
}

/** While the app is on screen, keep the selected car's status fresh every [CHARGING_POLL_MS] for as long as it is charging. */
internal fun AppViewModel.startChargingPoll() {
    viewModelScope.launch {
        appForeground.collectLatest { onScreen ->
            if (!onScreen) return@collectLatest
            refreshSelectedIfCharging()
            while (true) {
                delay(CHARGING_POLL_MS)
                refreshSelectedIfCharging()
            }
        }
    }
}
