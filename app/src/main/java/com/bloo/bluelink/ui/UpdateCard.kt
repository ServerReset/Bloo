package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.update.PlayUpdates
import com.bloo.bluelink.update.UpdateInfo
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.ProgressBar
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SecondaryTextStyle
import dev.chrisbanes.haze.HazeState

/*
 * The update card, rebuilt from the ground up. It is one function of the state: the same card for both distributions.
 *  - A Google Play copy hands Play its own flow, and Play owns the download and the install.
 *  - A GitHub copy downloads the APK in place, then installs it: silently through Shizuku when that is on and running, otherwise through
 *    the system installer after the install guide.
 * Progress is the app's own wavy line (ProgressBar). Nothing here keeps a second copy of the update's state: the card reads UiState and
 * Play's availability, and every action goes through the view model.
 */
@Composable
internal fun UpdateCardBody(state: UiState, vm: AppViewModel, info: UpdateInfo?, playAvailable: Boolean, hazeState: HazeState?) {
    val context = LocalContext.current
    val downloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
    val seamless = LocalAppearance.current.seamlessInstallShizuku && state.shizukuAvailable

    val status = when {
        playAvailable -> "A newer version is on Google Play."
        state.updateInstalling -> "Installing…"
        state.updateApkReady -> "Downloaded. Ready to install."
        state.updateDownloading -> "Downloading…"
        info?.run?.phoneApkUrl != null -> "A newer version is ready to download."
        else -> "A newer version is on GitHub."
    }

    // The one primary action, whichever state the update is in. Each branch is the next step, and a busy step is shown but not tappable.
    val primaryLabel: String
    val primaryEnabled: Boolean
    val primaryPending: Boolean
    val primaryOnClick: () -> Unit
    when {
        playAvailable -> {
            primaryLabel = "Update"; primaryEnabled = true; primaryPending = false
            primaryOnClick = { context.hostActivity()?.let { PlayUpdates.start(it) } }
        }
        state.updateInstalling -> {
            primaryLabel = "Installing…"; primaryEnabled = false; primaryPending = true
            primaryOnClick = {}
        }
        state.updateApkReady -> {
            primaryLabel = if (seamless) "Install now" else "Install"; primaryEnabled = true; primaryPending = false
            primaryOnClick = { vm.installDownloadedUpdate() }
        }
        state.updateDownloading -> {
            primaryLabel = "Downloading…"; primaryEnabled = false; primaryPending = true
            primaryOnClick = {}
        }
        info?.run?.phoneApkUrl != null -> {
            primaryLabel = "Download"; primaryEnabled = true; primaryPending = false
            primaryOnClick = { vm.downloadUpdateInBackground() }
        }
        else -> {
            primaryLabel = "Open release page"; primaryEnabled = true; primaryPending = false
            primaryOnClick = {
                // Dismiss only if the page really opened: a tap that did nothing must not cost the user the card.
                val url = info?.run?.htmlUrl
                if (url != null && context.openExternalUrl(url)) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(GapRow), modifier = Modifier.fillMaxWidth()) {
        RevealText(status, style = SecondaryTextStyle())
        // The wavy line: a share of the download while it runs, indeterminate while the install runs.
        when {
            state.updateDownloading -> ProgressBar(progress = downloadProgress)
            state.updateInstalling -> ProgressBar(progress = null)
        }
        ActionRow {
            MorphTextButton(
                primaryLabel,
                onClick = primaryOnClick,
                enabled = primaryEnabled,
                pending = primaryPending,
                emphasis = ButtonEmphasis.Confirm,
            )
            MorphTextButton("Later", onClick = { vm.snoozeUpdate() })
        }
        if (!playAvailable && info != null) {
            ActionRow {
                MorphTextButton("Release notes", onClick = { context.openExternalUrl(info.run.htmlUrl) })
            }
        }
    }
}
