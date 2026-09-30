package com.bloo.bluelink.ui

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.links
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- App-icon shortcuts and OEM app launch (extracted from AppViewModel) --

/**
 * Handle an app-icon shortcut. If the garage isn't loaded yet the request is
 * queued and run once it is.
 */
fun AppViewModel.handleShortcut(vin: String, cmd: String) {
    pendingShortcut = vin to cmd
    tryRunPendingShortcut()
}

internal fun AppViewModel.tryRunPendingShortcut() {
    val (vin, cmd) = pendingShortcut ?: return
    val v = _state.value.vehicles.firstOrNull { it.vin == vin } ?: return
    pendingShortcut = null
    val idx = _state.value.vehicles.indexOf(v)
    if (idx >= 0) selectIndex(idx)
    // selectIndex only updates which car is current, not which screen is
    // showing -- tapping a car-specific shortcut while the app was sitting
    // on Settings (or any other screen) previously selected the right car
    // underneath without ever bringing it into view. A shortcut
    // tap always means "look at this car," so force back to the garage.
    _state.update { it.copy(screen = Screen.Garage, expandedIndex = null) }
    val status = _state.value.statusFor(v)
    when (cmd) {
        // Toggles: do the opposite of the last-known state.
        "doors" -> if (status?.doorLock == true) unlock(v) else lock(v)
        "climate" -> if (status?.airCtrlOn == true) stopClimate(v) else {
            // Gate the start on !isDriving, matching the in-app control (which
            // goes read-only while driving) -- the car rejects remote climate
            // while moving, so firing it would only waste a serialized request
            // slot and surface a spurious "command failed".
            if (!_state.value.isDriving(v)) {
                startClimate(v, ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F, defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN))
            }
        }
        "lock" -> lock(v)
        "unlock" -> unlock(v)
        "locate" -> locate(v)
        "bluelink" -> openOemApp(v)
        // "open" just selects the car (done above).
    }
}

/** Launch the OEM Bluelink/Genesis/Kia app for this car's brand. */
private fun AppViewModel.openOemApp(v: Vehicle) {
    val ctx = getApplication<Application>()
    val links = brandOf(v).links
    val launch = ctx.packageManager.getLaunchIntentForPackage(links.appPackage)
        ?: android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            links.playStoreUrl.toUri(),
        )
    runCatching {
        ctx.startActivity(launch.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

/** Toggle whether a given car+action app-icon shortcut is shown. */
fun AppViewModel.setShortcutEnabled(vin: String, cmd: String, enabled: Boolean) {
    val universe = _state.value.vehicles.flatMap { v ->
        com.bloo.bluelink.Shortcuts.ACTIONS.map { "${it}_${v.vin}" }
    }.toSet()
    val current = _state.value.shortcutSet ?: universe
    val updated = if (enabled) current + "${cmd}_$vin" else current - "${cmd}_$vin"
    _state.update { it.copy(shortcutSet = updated) }
    viewModelScope.launch {
        settingsStore.setEnabledShortcuts(updated)
        com.bloo.bluelink.Shortcuts.refresh(getApplication(), _state.value.vehicles, updated)
    }
}
