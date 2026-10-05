package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Update checks (extracted from AppViewModel) --

/**
 * Shared by the cold-start check and every refreshStatus(); debounced/snoozed internally (see
 * UpdateChecker). [force] bypasses the debounce/snooze; [surfaceResult] reports UpToDate/Failed to
 * the snackbar (auto checks stay silent).
 */
internal fun AppViewModel.checkForUpdate(force: Boolean = false, surfaceResult: Boolean = false) {
    viewModelScope.launch {
        if (surfaceResult) _state.update { it.copy(updateChecking = true) }
        try {
            val result = com.bloo.bluelink.update.UpdateChecker.checkPhone(getApplication(), force = force)
            when (result) {
                is com.bloo.bluelink.update.UpdateCheckResult.Available -> {
                    _state.update {
                        val sameBuild = it.updateAvailable?.run?.runNumber == result.info.run.runNumber
                        it.copy(
                            updateAvailable = result.info,
                            updateApkReady = it.updateApkReady && sameBuild,
                            // ("Remind me" is the one that stays hidden longer — it sets a snooze
                            // so checkPhone short-circuits to UpToDate until the reminder worker
                            // clears it, so we never reach this branch while snoozed.)
                            updateTileDismissed = false,
                        )
                    }
                    // Tell a paired watch about the newer WATCH build (if this release carries a
                    // watch APK) so it can offer its own update -- the watch has no network of its
                    // own. Advertised only when a watch is actually paired, so an unwatched phone
                    // never does the extra Data Layer work.
                    com.bloo.bluelink.wear.WatchPresence.pushWatchUpdateAdvice(getApplication(), result.info.run)
                    // A manual check also confirms via snackbar, since the update tile isn't
                    // visible from Settings.
                    if (surfaceResult) _state.update {
                        it.copy(message = "Update available: ${com.bloo.bluelink.data.buildLabel(result.info.run.runNumber)}", messageType = "info")
                    }
                }
                is com.bloo.bluelink.update.UpdateCheckResult.Failed ->
                    if (surfaceResult) _state.update { it.copy(message = "Couldn't reach GitHub to check for updates.", messageType = "error") }
                // else: silent -- next refresh tries again
                is com.bloo.bluelink.update.UpdateCheckResult.Skipped -> {
                    // No network call happened (debounce or snooze), so this says nothing about
                    // updates and must not touch updateAvailable. surfaceResult stays silent for
                    // the same reason.
                }
                is com.bloo.bluelink.update.UpdateCheckResult.UpToDate -> {
                    _state.update {
                        // Never yank the tile out from under work already in flight. A
                        // ready-to-install APK is kept for the same reason: the user still has an
                        // Install button to press.
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

/**
 * User-initiated "Check for updates" from Settings: forces past the debounce/snooze and surfaces
 * the result to the snackbar.
 */
fun AppViewModel.checkForUpdateManually() = checkForUpdate(force = true, surfaceResult = true)
