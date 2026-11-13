package com.bloo.bluelink.ui

/**
 * The update pebble, pinned below the hero whenever an update is waiting on either track. Its header carries the one primary action; the
 * body is the shared [UpdatePanel], the same one the Settings card shows.
 */

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.update.PlayUpdates
import com.bloo.bluelink.update.UpdateInfo
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.confirmTone
import com.bloo.uicommon.container
import com.bloo.uicommon.content
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

@Composable
internal fun UpdateAvailableTile(
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    AnimatedVisibility(
        visible = state.updateOffered(),
        enter = expandEnterSized(Alignment.Bottom),
        exit = expandExitSized(Alignment.Bottom),
    ) {
        val context = LocalContext.current
        // Play's answer and the GitHub build are both read here, so the tile can name whichever one is waiting.
        val playAvailable by PlayUpdates.available.collectAsStateWithLifecycle()
        val info = state.updateAvailable?.takeIf { !state.updateTileDismissed }
        // Keyed on the build number so a different build starts collapsed.
        var expanded by rememberSaveable(info?.run?.runNumber) { mutableStateOf(false) }
        // Animates the card's own height as its state changes (the Play and GitHub bodies, the Details disclosure, a new summary).
        UpdateBadgedCard(visible = true, modifier = Modifier.fillMaxWidth().animateContentSize(pebbleOpenSizeSpec())) {
            PebbleShell(
                expanded = expanded,
                onToggle = { expanded = !expanded },
                modifier = modifier,
                icon = Icons.Filled.SystemUpdate,
                title = "Update available",
                summary = if (info != null) {
                    info.run.displayTitle?.takeIf { it.isNotBlank() }
                        ?: com.bloo.bluelink.data.buildLabel(info.run.runNumber)
                } else {
                    "Google Play"
                },
                headerAction = null,
            ) {
                // The header carries the primary action, so the body does not repeat it.
                UpdateCardBody(state, vm, info, playAvailable, hazeState)
            }
        }
    }
}

/**
 * What the update's primary action says and does, in one place. The pebble surfaces it as its header action and the Settings card as a
 * full-width button, so the CHROME differs -- but the label, the glyph and the branch it takes must not, and they had a copy each.
 */
internal data class UpdateAction(val label: String, val icon: ImageVector, val ready: Boolean)

@Composable
internal fun updateAction(state: UiState, info: UpdateInfo, seamless: Boolean): UpdateAction = UpdateAction(
    label = when {
        state.updateInstalling -> "Installing…"
        state.updateApkReady -> if (seamless) "Install now" else "Install"
        state.updateDownloading -> "Downloading…"
        info.run.phoneApkUrl != null -> "Download"
        else -> "Open release page"
    },
    icon = when {
        state.updateApkReady -> AppIcons.CheckCircle
        state.updateDownloading -> Icons.Filled.Download
        else -> Icons.Filled.SystemUpdate
    },
    ready = state.updateApkReady,
)

/** Runs the update's primary action -- download, install, or open the release page. */
internal fun runUpdateAction(state: UiState, vm: AppViewModel, info: UpdateInfo, context: Context) {
    when {
        state.updateApkReady -> vm.installDownloadedUpdate()
        info.run.phoneApkUrl != null -> vm.downloadUpdateInBackground()
        else -> {
            // Dismiss ONLY if the page really opened. Swallowing an ActivityNotFoundException and dismissing anyway meant a tap did visibly
            // nothing AND cost the user the tile.
            val opened = context.openExternalUrl(info.run.htmlUrl)
            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
        }
    }
}
