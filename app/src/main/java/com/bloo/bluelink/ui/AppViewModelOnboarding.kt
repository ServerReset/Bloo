package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.setCarConfigured
import com.bloo.bluelink.data.setOnboardingSeen
import com.bloo.bluelink.data.snapshot

// --- Onboarding completion and sync-restore choice (extracted from AppViewModel) --

/** [Screen.SyncChoice] -- user picked "Set up fresh" instead of restoring.
 *  Proceeds into the normal welcome wizard exactly as if this device had
 *  no sync option to offer at all. */
fun AppViewModel.declineSyncRestore() {
    _state.update { it.copy(screen = Screen.Onboarding) }
}

/**
 * [Screen.SyncChoice] -- user picked "Restore from sync". Joins the picked
 * file exactly like Settings' own "Backup & sync" card
 * ([importSettingsAndSync]) does, then re-resolves which screen this
 * device lands on from the freshly-imported config, instead of leaving it
 * parked on the choice screen forever: straight to the garage if the
 * import's onboarding_seen/isCarConfigured flags say this device is
 * already fully set up, [Screen.CarSetup] for whichever cars it doesn't
 * cover (a car added since the backup, say), or -- if the picked file
 * turned out not to actually resolve first-run status at all (a bad file,
 * a failed join, or one from before this app tracked those flags) -- the
 * normal onboarding wizard, deliberately NOT back to this same choice.
 */
fun AppViewModel.restoreFromSyncThenContinue(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
    importSettingsAndSyncSuspend(context, uri)
    val vehicles = _state.value.vehicles
    val screen = if (vehicles.isEmpty()) {
        Screen.Onboarding
    } else {
        resolveScreen(vehicles, settingsStore.snapshot(), firstRunScreen = Screen.Onboarding)
    }
    _state.update { it.copy(screen = screen) }
}

/** Finish first-run onboarding (wizard complete) and land in the app. */
fun AppViewModel.finishOnboarding() {
    val vins = _state.value.vehicles.map { it.vin }
    viewModelScope.launch {
        settingsStore.setOnboardingSeen()
        vins.forEach { settingsStore.setCarConfigured(it) }
    }
    landInApp()
}

/** Leave a setup flow and show the garage, clearing any stale load error. The
 *  trailing move shared by [finishOnboarding] and [finishCarSetup], which had
 *  it written out identically. */
private fun AppViewModel.landInApp() = _state.update { it.copy(screen = Screen.Garage, garageLoadError = null) }

/** Mark newly-detected cars as configured and return to the garage. */
fun AppViewModel.finishCarSetup(vins: List<String>) {
    viewModelScope.launch { vins.forEach { settingsStore.setCarConfigured(it) } }
    landInApp()
}

/** Dismiss the post-onboarding "check out Settings" hint on the garage
 *  (in-memory only -- it's a one-time nudge, not worth persisting). */
fun AppViewModel.dismissSettingsHint() = _state.update { it.copy(showSettingsHint = false) }
