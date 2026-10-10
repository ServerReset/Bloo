package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.setCarConfigured
import com.bloo.bluelink.data.setOnboardingSeen
import com.bloo.bluelink.data.snapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Onboarding completion and sync-restore choice (extracted from AppViewModel) --

/** The onboarding deck's restore card. */
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

/**
 * Leave a setup flow and show the garage, clearing any stale load error. The trailing move shared
 * by [finishOnboarding] and [finishCarSetup], which had it written out identically.
 */
private fun AppViewModel.landInApp() = _state.update { it.copy(screen = Screen.Garage, garageLoadError = null) }

/** Mark newly-detected cars as configured and return to the garage. */
fun AppViewModel.finishCarSetup(vins: List<String>) {
    viewModelScope.launch { vins.forEach { settingsStore.setCarConfigured(it) } }
    landInApp()
}

/**
 * Dismiss the post-onboarding "check out Settings" hint on the garage (in-memory only -- it's a
 * one-time nudge, not worth persisting).
 */
fun AppViewModel.dismissSettingsHint() = _state.update { it.copy(showSettingsHint = false) }

/** Summon the welcome cards again from Settings. They sit over whatever screen is showing. */
fun AppViewModel.showWelcomeCards() = _state.update { it.copy(welcomeCardsOpen = true) }

/** Dismiss the welcome cards; they go away until summoned from Settings again. */
fun AppViewModel.dismissWelcomeCards() = _state.update { it.copy(welcomeCardsOpen = false) }
