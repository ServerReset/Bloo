package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Update checks (extracted from AppViewModel) --

/** Shared by the cold-start check and every refreshStatus(); debounced/snoozed internally (see UpdateChecker).
 *  [force] bypasses the debounce/snooze; [surfaceResult] reports UpToDate/Failed to the snackbar (auto checks stay silent). */
internal fun AppViewModel.checkForUpdate(force: Boolean = false, surfaceResult: Boolean = false) {
    viewModelScope.launch {
        if (surfaceResult) _state.update { it.copy(updateChecking = true) }
        try {
            val result = com.bloo.bluelink.update.UpdateChecker.checkPhone(getApplication(), force = force)
            when (result) {
                is com.bloo.bluelink.update.UpdateCheckResult.Available -> {
                    _state.update {
                        // A downloaded APK is stale if a different build is now available.
                        val sameBuild = it.updateAvailable?.run?.runNumber == result.info.run.runNumber
                        it.copy(
                            updateAvailable = result.info,
                            updateApkReady = it.updateApkReady && sameBuild,
                            // Any Available result clears "Not now", so the tile returns on the next check.
                            updateTileDismissed = false,
                        )
                    }
                    // Advertise a newer watch build to a paired watch (it has no network); skipped when no watch is paired.
                    com.bloo.bluelink.wear.WatchPresence.pushWatchUpdateAdvice(getApplication(), result.info.run)
                    // A manual check also confirms via snackbar, since the update tile isn't visible from Settings.
                    if (surfaceResult) _state.update {
                        it.copy(message = "Update available: ${com.bloo.bluelink.data.buildLabel(result.info.run.runNumber)}", messageType = "info")
                    }
                }
                is com.bloo.bluelink.update.UpdateCheckResult.Failed ->
                    if (surfaceResult) _state.update { it.copy(message = "Couldn't reach GitHub to check for updates.", messageType = "error") }
                // else: silent -- next refresh tries again
                is com.bloo.bluelink.update.UpdateCheckResult.Skipped -> {
                    // No network call happened (debounce or snooze), so this says nothing about updates and must
                    // not touch updateAvailable. surfaceResult stays silent for the same reason.
                }
                is com.bloo.bluelink.update.UpdateCheckResult.UpToDate -> {
                    _state.update {
                        // Never remove the tile from under in-flight work; a ready APK is kept so Install stays available.
                        if (it.updateDownloading || it.updateInstalling || it.updateApkReady) it
                        else it.copy(updateAvailable = null, updateApkReady = false, updateTileDismissed = false)
                    }
                    if (surfaceResult) _state.update { it.copy(message = "You're on the latest build.", messageType = "info") }
                }
            }
        } finally {
            if (surfaceResult) _state.update { it.copy(updateChecking = false) }
        }
    }
}

/** User-initiated "Check for updates" from Settings: forces past the debounce/snooze and surfaces the result to the snackbar. */
fun AppViewModel.checkForUpdateManually() = checkForUpdate(force = true, surfaceResult = true)
