package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Update checks (extracted from AppViewModel) --

/** Shared by the cold-start check and every refreshStatus() call. Debounced/
 *  snoozed internally (see UpdateChecker) -- safe to call as often as this is.
 *  [force] bypasses the debounce/snooze (for a user-initiated "Check now");
 *  [surfaceResult] reports UpToDate/Failed to the snackbar (auto checks stay silent). */
internal fun AppViewModel.checkForUpdate(force: Boolean = false, surfaceResult: Boolean = false) {
    viewModelScope.launch {
        if (surfaceResult) _state.update { it.copy(updateChecking = true) }
        try {
            val result = com.bloo.bluelink.update.UpdateChecker.checkPhone(getApplication(), force = force)
            when (result) {
                is com.bloo.bluelink.update.UpdateCheckResult.Available -> {
                    _state.update {
                        // A previously-downloaded APK is only still good if it's
                        // for this same build -- a newer one showing up means the
                        // cached file is stale.
                        val sameBuild = it.updateAvailable?.run?.runNumber == result.info.run.runNumber
                        it.copy(
                            updateAvailable = result.info,
                            updateApkReady = it.updateApkReady && sameBuild,
                            // "Not now" only hides the tile until the NEXT check: any
                            // Available result (even the same build re-found on a
                            // refresh) clears the dismissed flag so the tile comes back.
                            // ("Remind me" is the one that stays hidden longer — it sets a
                            // snooze so checkPhone short-circuits to UpToDate until the
                            // reminder worker clears it, so we never reach this branch
                            // while snoozed.)
                            updateTileDismissed = false,
                        )
                    }
                    // Tell a paired watch about the newer WATCH build (if this release
                    // carries a watch APK) so it can offer its own update -- the watch has
                    // no network of its own. Advertised only when a watch is actually
                    // paired, so an unwatched phone never does the extra Data Layer work.
                    com.bloo.bluelink.wear.WatchPresence.pushWatchUpdateAdvice(getApplication(), result.info.run)
                    // A manual check found a newer build — the update tile appears on
                    // the garage screen, which isn't visible from Settings, so also
                    // confirm via the snackbar (else the button looks like a no-op).
                    if (surfaceResult) _state.update {
                        it.copy(message = "Update available: ${com.bloo.bluelink.data.buildLabel(result.info.run.runNumber)}", messageType = "info")
                    }
                }
                is com.bloo.bluelink.update.UpdateCheckResult.Failed ->
                    if (surfaceResult) _state.update { it.copy(message = "Couldn't reach GitHub to check for updates.", messageType = "error") }
                // else: silent -- next refresh tries again
                is com.bloo.bluelink.update.UpdateCheckResult.Skipped -> {
                    // No network call happened at all (debounce or an active snooze), so
                    // this says nothing about whether an update exists -- must NOT touch
                    // updateAvailable. It used to arrive here as UpToDate, indistinguishable
                    // from a genuine "checked, nothing newer" -- so a pull-to-refresh that
                    // landed inside the previous check's 1-minute debounce window (the
                    // common case: cold start finds an update, user immediately pulls to
                    // refresh to confirm) cleared the tile for an update that was still
                    // there, never actually re-verified. Reported from a real device.
                    //
                    // surfaceResult is effectively never true here in practice --
                    // checkForUpdateManually always passes force = true, which bypasses the
                    // debounce/snooze entirely -- but if that ever changes, staying silent
                    // (like Failed's own "not force" reasoning) beats claiming a checked
                    // result that didn't happen.
                }
                is com.bloo.bluelink.update.UpdateCheckResult.UpToDate -> {
                    _state.update {
                        // Never yank the tile out from under work already in
                        // flight. A ready-to-install APK is kept for the same
                        // reason: the user still has an Install button to press.
                        // (This branch is now a network-VERIFIED "nothing newer" --
                        // the debounce/snooze short-circuit moved to Skipped above --
                        // so clearing the tile here is always a real answer, not a
                        // guess made from silence.)
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

/** User-initiated "Check for updates" from Settings: forces past the debounce/
 *  snooze and surfaces the result (up-to-date / can't-reach) to the snackbar. If a
 *  newer build is found it just appears as the usual update tile. */
fun AppViewModel.checkForUpdateManually() = checkForUpdate(force = true, surfaceResult = true)
