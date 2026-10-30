package com.bloo.bluelink.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.UpdateApi
import com.bloo.bluelink.update.UpdateInfo
import com.bloo.bluelink.data.buildLabel
import com.bloo.bluelink.update.PlayUpdates
import com.bloo.bluelink.update.UpdateRuntime
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.SecondaryTextStyle
import com.bloo.uicommon.confirmTone
import com.bloo.uicommon.container
import com.bloo.uicommon.content
import dev.chrisbanes.haze.HazeState

/**
 * The Activity around this context, so a Play flow can be started from a composable. Null for anything that is not one.
 */
internal tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/**
 * Whether an update is waiting, on whichever track this copy follows: the GitHub build the checker found and was not dismissed, or the
 * newer build Play reports. The update pebble, its section gate and the Settings card all read this, so they appear and go away together.
 */
internal fun UiState.updateOffered(): Boolean = (updateAvailable != null && !updateTileDismissed) || playUpdateAvailable

/**
 * The one update body, shown by the update pebble and the Settings card. What it offers depends on the track this copy follows
 * ([UpdateRuntime.usesGitHub]); the layout does not, so the two surfaces cannot drift apart.
 *
 * [primaryInBody] is false on the pebble, whose header already carries the primary action, so it is not offered twice.
 */
@Composable
internal fun UpdatePanel(
    state: UiState,
    vm: AppViewModel,
    hazeState: HazeState? = null,
    primaryInBody: Boolean = true,
    /** The version jump at the top of the body. The tile shows it here; Settings already shows it in its own header. */
    showHeadline: Boolean = false,
) {
    // One column for the whole body, so the sections are spaced the same way whichever track is shown.
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        if (UpdateRuntime.usesGitHub(LocalContext.current)) {
            GitHubUpdateBody(state, vm, hazeState, primaryInBody, showHeadline)
        } else {
            PlayUpdateBody(hazeState, primaryInBody)
        }
    }
}

@Composable
private fun GitHubUpdateBody(state: UiState, vm: AppViewModel, hazeState: HazeState?, primaryInBody: Boolean, showHeadline: Boolean) {
    val context = LocalContext.current
    val seamless = LocalAppearance.current.seamlessInstallShizuku && state.shizukuAvailable
    val info = state.updateAvailable?.takeIf { !state.updateTileDismissed }
    val current = vm.currentBuildNumber
    // Keyed on the build, so a new one opens with its details closed.
    var showDetails by rememberSaveable(info?.run?.runNumber) { mutableStateOf(false) }

    // 1. The version jump, when this surface needs it (the tile does; Settings shows it in its header).
    // 2. What is happening now, in one line with its progress.
    // 3. The one thing to do, and the way out of it.
    if (info != null) {
        val newLabel = buildLabel(info.run.runNumber)
        // Build delta ("build 812 → build 828") when the installed build is known, else just the target.
        val deltaLabel = if (current > 0) "${buildLabel(current)} → $newLabel" else newLabel
        if (showHeadline) EnterOnce(expandFrom = Alignment.Top) { UpdateDeltaHero(current, info.run.runNumber) }
        EnterOnce(expandFrom = Alignment.Top) {
            NestedGlassPanel(hazeState) {
                Column(Modifier.padding(GapGroup)) {
                    // The version is already above, so the status line says what is happening, not the version again.
                    UpdateStatusLine(deltaLabel, seamless, state, vm, showDelta = false)
                }
            }
        }
        if (primaryInBody) {
            val act = updateAction(state, info, seamless)
            MorphButton(
                onClick = { runUpdateAction(state, vm, info, context) },
                modifier = Modifier.fillMaxWidth(),
                active = act.ready,
                activeContainerColor = confirmTone().container,
                activeContentColor = confirmTone().content,
                enabled = !state.updateInstalling && !state.updateDownloading,
                expressive = true,
            ) {
                MorphButtonLabel(act.icon, act.label, pending = false)
            }
        }
        EnterOnce(expandFrom = Alignment.Top) { UpdateDismissRow(state, vm) }
    }

    // 4. Everything secondary sits behind one disclosure: what is new, how to install, and Shizuku.
    MorphTextButton(
        if (showDetails) "Hide details" else "Details",
        onClick = { showDetails = !showDetails },
        modifier = Modifier.fillMaxWidth(),
    )
    PopVisible(visible = showDetails, sizeAnimated = true) {
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            if (info != null) {
                EnterOnce(expandFrom = Alignment.Top) { UpdateReleaseNotes(info, hazeState = hazeState) }
                if (!seamless) EnterOnce(expandFrom = Alignment.Top) { InstallHelp(state, info, hazeState) }
            }
            EnterOnce(expandFrom = Alignment.Top) {
                NestedGlassPanel(hazeState) {
                    Column(Modifier.padding(GapGroup)) { ShizukuSetting(state, vm) }
                }
            }
        }
    }

    // 5. The two ways to look for this yourself, always at the foot.
    EnterOnce(expandFrom = Alignment.Top) { ActionRow {
        MorphTextButton(
            "Check",
            onClick = { vm.checkForUpdateManually() },
            enabled = !state.updateChecking,
            icon = Icons.Filled.Refresh,
            pending = state.updateChecking,
            emphasis = ButtonEmphasis.Confirm,
        )
        MorphTextButton(
            "Releases",
            onClick = { context.openExternalUrl(UpdateApi.RELEASES_URL) },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            fillOnPress = false,
        )
    } }
}

/**
 * How to get past Android's installer, for the tap-through path only. Without Shizuku the OS installer asks every time, and Play Protect's
 * sheet is folded shut by default, so an update looks like it failed when the real answer is "expand it, then tap Install anyway".
 */
@Composable
private fun InstallHelp(state: UiState, info: UpdateInfo, hazeState: HazeState?) {
    NestedGlassPanel(hazeState) {
        Column(Modifier.padding(GapGroup), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
            if (!state.shizukuAvailable || !LocalAppearance.current.seamlessInstallShizuku) {
                RevealMutedText(
                    "Without Shizuku, tap \"More details\", then \"Install anyway\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RevealText(
                if (info.run.phoneApkUrl != null) "1. Tap \"Update\", then \"Install\" once it downloads" else "1. Download the APK, then open it",
                style = SecondaryTextStyle(),
            )
            RevealText("2. If you see \"Blocked by Play Protect\", tap \"More details\" → \"Install anyway\"", style = SecondaryTextStyle())
        }
    }
}

/** Seamless install is a Shizuku feature: a switch when Shizuku is present, otherwise how to get it. */
@Composable
private fun ShizukuSetting(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    // The switch and the install-it prompt trade places through the card's own content motion, not a snap.
    AnimatedContent(targetState = state.shizukuAvailable, transitionSpec = { expandContentTransform() }, label = "shizukuSetting") { available ->
        Column {
            if (available) {
                ToggleRow("Install seamlessly (Shizuku)", LocalAppearance.current.seamlessInstallShizuku) { vm.setSeamlessInstallShizuku(it) }
            } else {
                BodySmallText("Update without Android's installer. No root.")
                MorphTextButton(
                    "Get Shizuku",
                    onClick = { openUrl(context, "https://shizuku.rikka.app/") },
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                )
            }
        }
    }
}

@Composable
private fun PlayUpdateBody(hazeState: HazeState?, primaryInBody: Boolean) {
    val context = LocalContext.current
    val available by PlayUpdates.available.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    // Play keeps the copy current; this only says what Play last reported. The same status card as the GitHub track.
    UpdateStatusCard(
        icon = if (available) Icons.Filled.SystemUpdate else AppIcons.CheckCircle,
        tint = if (available) scheme.primary else scheme.onSurfaceVariant,
        title = if (available) "Ready in Google Play" else "Up to date on Google Play",
        subtitle = "Google Play installs updates for this copy.",
        hazeState = hazeState,
    )
    PopVisible(visible = available && primaryInBody, sizeAnimated = true) {
        // Play's own flow does the download and the install; this only opens it.
        MorphButton(
            onClick = { context.hostActivity()?.let { PlayUpdates.start(it) } },
            modifier = Modifier.fillMaxWidth(),
            active = true,
            activeContainerColor = confirmTone().container,
            activeContentColor = confirmTone().content,
            expressive = true,
        ) {
            MorphButtonLabel(Icons.Filled.SystemUpdate, "Update in Google Play", pending = false)
        }
    }
    EnterOnce(expandFrom = Alignment.Top) {
        ActionRow {
            MorphTextButton(
                "Check",
                onClick = { PlayUpdates.refresh(context.applicationContext) },
                icon = Icons.Filled.Refresh,
                emphasis = ButtonEmphasis.Confirm,
            )
        }
    }
    EnterOnce(expandFrom = Alignment.Top) {
        PlayStoreLinks()
    }
}
