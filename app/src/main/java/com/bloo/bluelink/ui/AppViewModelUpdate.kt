package com.bloo.bluelink.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- In-app update tile: dismiss/snooze, background download, and install (extracted from AppViewModel) --
//
// The download-progress flow stays on the ViewModel because it must survive the calls;
// everything else is plain logic over UiState.

fun AppViewModel.dismissUpdate() {
    _state.update { it.copy(updateTileDismissed = true) }
}

fun AppViewModel.snoozeUpdate() {
    _state.update { it.copy(updateTileDismissed = true) }
    viewModelScope.launch {
        // Snooze for 1 day to match the reminder worker's 1-day delay: if the worker is delayed by
        // Doze, a normal refresh still revives the tile at ~1 day rather than it staying hidden for
        // the longer default window.
        com.bloo.bluelink.update.UpdateChecker.snooze(getApplication(), UPDATE_REMINDER_DELAY_MS)
        com.bloo.bluelink.work.UpdateReminderWorker.schedule(getApplication())
    }
}

/**
 * Fixed on-disk location for the downloaded update APK, inside the app's cache dir (so the system
 * can reclaim it under storage pressure, and it's automatically cleaned up on uninstall). Always
 * the same filename, so a later download simply overwrites a stale one.
 */
private fun AppViewModel.apkCacheFile(): java.io.File {
    val ctx = getApplication<Application>()
    return java.io.File(java.io.File(ctx.cacheDir, "apk"), "Bloo.apk")
}

/**
 * The update tile's primary button, first tap: downloads the APK in the background with no other UI
 * change yet -- lets someone start the update mid-something-else and come back to it.
 */
fun AppViewModel.downloadUpdateInBackground() {
    val url = _state.value.updateAvailable?.run?.phoneApkUrl
    if (url == null) {
        _state.update { it.copy(message = "No direct download for this build. Use the browser link.") }
        return
    }
    if (_state.value.updateDownloading || _state.value.updateApkReady) return
    // Starting a download is an explicit "keep this update" signal: abort any in-flight dismiss
    // (undo-window) timer and un-hide the tile, so the pending dismiss can't fire mid-download and
    // strand the finished APK behind a hidden tile.
    _updateDownloadProgress.value = 0f
    _state.update { it.copy(updateDownloading = true, updateTileDismissed = false) }
    viewModelScope.launch {
        val dest = apkCacheFile()
        // Throttled to whole percents. UpdateApi calls back once per 64KB buffer, which on a fast
        // connection is hundreds of emissions a second for a multi-MB APK -- and every one of them
        // recomposed the whole update tile, on the main thread, while the user may well be
        // scrolling.
        var lastPercent = -1
        val ok = com.bloo.bluelink.data.UpdateApi.downloadApk(url, dest) { progress ->
            val percent = (progress * 100f).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                _updateDownloadProgress.value = progress
            }
        }
        _updateDownloadProgress.value = null
        _state.update { it.copy(updateDownloading = false, updateApkReady = ok) }
        if (!ok) {
            _state.update { it.copy(message = "Download failed. Check your connection and try again.") }
        }
    }
}

/**
 * The update tile's second tap, once [downloadUpdateInBackground] has finished: the APK is already
 * sitting in cache. If the user opted into seamless install AND Shizuku is running, install
 * silently via ADB; otherwise (or on any Shizuku failure) hand it to the system installer as
 * before.
 */
fun AppViewModel.installDownloadedUpdate() {
    if (!_state.value.updateApkReady) return
    // Guard against a second tap (or a permission-grant retry) re-entering while a seamless install
    // is already running — otherwise two concurrent installer sessions write/commit the same APK.
    if (_state.value.updateInstalling) return
    val dest = apkCacheFile()
    if (!dest.exists()) {
        _state.update { it.copy(updateApkReady = false, message = "Update file missing. Tap Update to fetch it again.") }
        return
    }
    val installer = com.bloo.bluelink.update.ShizukuInstaller
    val wantSeamless = appearance.value.seamlessInstallShizuku && installer.isAvailable()
    if (!wantSeamless) {
        fallbackInstall(dest)
        return
    }
    if (!installer.hasPermission()) {
        // Ask; the grant arrives on onShizukuPermissionResult, which retries. Until then leave the
        // APK ready so a second tap (or the grant) completes it.
        _state.update { it.copy(message = "Grant Shizuku access to install updates silently.", messageType = "info") }
        installer.requestPermission(SHIZUKU_INSTALL_REQUEST_CODE)
        return
    }
    seamlessInstall(dest)
}

/**
 * Runs the Shizuku silent install off the main thread, falling back to the system installer on any
 * failure.
 */
private fun AppViewModel.seamlessInstall(dest: java.io.File) {
    if (_state.value.updateInstalling) return
    val ctx = getApplication<Application>()
    // messageType is REQUIRED here: it defaults to "error" (and clearMessage() resets it to
    // "error"), and the snackbar's colour `when` falls through to the red errorContainer branch for
    // anything it doesn't recognise.
    _state.update { it.copy(updateInstalling = true, message = "Installing update…", messageType = "info") }
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val result = com.bloo.bluelink.update.ShizukuInstaller.installApk(dest, ctx.packageName)
        if (result.isFailure) {
            // Silent path failed (Shizuku died, OEM restriction, timed out, etc.) — clear the
            // in-flight flag and fall back to the system installer.
            launch(kotlinx.coroutines.Dispatchers.Main) {
                _state.update { it.copy(updateInstalling = false) }
                fallbackInstall(dest)
            }
        } else {
            // Success. Usually the OS force-stops us as it swaps the APK, so this never renders —
            // BUT a replace-install commit reports STATUS_SUCCESS as soon as it's staged and some
            // OEMs defer the process kill.
            launch(kotlinx.coroutines.Dispatchers.Main) {
                _state.update {
                    it.copy(
                        updateInstalling = false,
                        updateApkReady = false,
                        message = "Update installed. Reopen Bloo to finish.",
                        messageType = "info",
                    )
                }
            }
        }
    }
}

/**
 * The classic tap-through system installer (also the fallback for the seamless path). Reports only
 * if even this can't be launched.
 */
private fun AppViewModel.fallbackInstall(dest: java.io.File) {
    if (!com.bloo.bluelink.data.installDownloadedApk(getApplication<Application>(), dest)) {
        _state.update { it.copy(message = "Couldn't open the installer. Find Bloo.apk in Downloads.") }
    }
}

/**
 * Shizuku permission result forwarded from MainActivity. If the user just granted it and an update
 * is still staged, complete the seamless install.
 */
fun AppViewModel.onShizukuPermissionResult(requestCode: Int, grantResult: Int) {
    if (requestCode != SHIZUKU_INSTALL_REQUEST_CODE) return
    if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        // Info, not error: the user made a choice and the normal installer still works, so nothing
        // is actually broken to report in red.
        _state.update { it.copy(message = "Shizuku access denied. Updates will use the normal installer.", messageType = "info") }
        return
    }
    val dest = apkCacheFile()
    if (_state.value.updateApkReady && dest.exists()) seamlessInstall(dest)
}
