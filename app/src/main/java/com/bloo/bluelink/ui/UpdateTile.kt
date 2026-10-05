package com.bloo.bluelink.ui

/**
 * Update surfaces split out of Hero.kt: [UpdateAvailableTile] (pinned below the hero) and
 * [UpdateStatusLine] (live-status row shared with [SettingsHeroCard]'s expanded body).
 */

import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bloo.bluelink.update.UpdateInfo
import kotlin.math.roundToInt

/**
 * Standalone update tile pinned below the hero whenever the checker finds a newer build (Bloo is
 * not on the Play Store). Built directly on [PebbleShell] since it is not tied to a car/section.
 */
@Composable
internal fun UpdateAvailableTile(
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    val info = state.updateAvailable
    // Visible during the pending-dismiss (undo) window; only the committed updateTileDismissed
    // hides it.
    AnimatedVisibility(
        visible = info != null && !state.updateTileDismissed,
        enter = expandEnter(Alignment.Bottom),
        exit = expandExit(Alignment.Bottom),
    ) {
        if (info == null) return@AnimatedVisibility
        val context = LocalContext.current
        // Progress is collected from its own StateFlow so per-chunk ticks invalidate only this
        // tile; state.updateDownloading stays on UiState since it changes rarely.
        val downloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
        val hasDirectDownload = info.run.phoneApkUrl != null
        val current = vm.currentBuildNumber
        // Build delta ("build 812 → build 828") when the installed build is known, else just the
        // target.
        val newLabel = com.bloo.bluelink.data.buildLabel(info.run.runNumber)
        val deltaLabel = if (current > 0) {
            "${com.bloo.bluelink.data.buildLabel(current)} → $newLabel"
        } else {
            newLabel
        }
        val seamless = LocalAppearance.current.seamlessInstallShizuku && state.shizukuAvailable
        // Keyed on the build number so a different build starts collapsed.
        var expanded by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
        // Always visible: this composable only renders when an update exists (matches
        // SettingsHeroCard's dot).
        UpdateBadgedCard(visible = true, modifier = Modifier.fillMaxWidth()) {
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = modifier,
            icon = Icons.Filled.SystemUpdate,
            title = "Update available",
            summary = info.run.displayTitle?.takeIf { it.isNotBlank() } ?: deltaLabel,
            // No containerColor override: uses PebbleShell's default like ordinary pebbles.
            headerAction = PebbleHeaderAction(
                label = when {
                    state.updateInstalling -> "Installing…"
                    // Rounded to the nearest 5%; this chip is the only place the download
                    // percentage shows.
                    state.updateDownloading -> downloadProgress?.let { "${(it * 100 / 5f).roundToInt() * 5}%" } ?: "Downloading…"
                    state.updateApkReady -> if (seamless) "Install now" else "Install"
                    hasDirectDownload -> "Update"
                    else -> "Open"
                },
                icon = if (state.updateApkReady) Icons.Filled.SystemUpdate else Icons.Filled.Download,
                pending = state.updateDownloading || state.updateInstalling,
                enabled = !state.updateInstalling,
                // The confirm green: the install-ready call to action.
                active = state.updateApkReady,
                activeContainer = confirmTone().container,
                activeContent = confirmTone().content,
                onClick = {
                    when {
                        state.updateApkReady -> vm.installDownloadedUpdate()
                        hasDirectDownload -> vm.downloadUpdateInBackground()
                        else -> {
                            // Dismiss only if the page really opened.
                            val opened = context.tryStart(
                                Intent(Intent.ACTION_VIEW, info.run.htmlUrl.toUri()),
                            )
                            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                        }
                    }
                },
            ),
        ) {
            val scheme = MaterialTheme.colorScheme
            UpdateDeltaHero(current, info.run.runNumber)
            // One state-driven status line (icon + text); the build delta is already in the header
            // summary.
        // Glass surface; shadow = false because it sits inside the pebble's already-elevated card
        // (see glassEdge).
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
            hazeState = hazeState,
            shadow = false,
        ) {
            // Column, not Box: UpdateStatusLine emits two top-level siblings that a Box would stack
            // on top of each other.
            Column(Modifier.padding(12.dp)) {
                UpdateStatusLine(
                    deltaLabel, seamless, state, vm,
                    showDelta = info.run.displayTitle?.isNotBlank() == true,
                )
            }
        }
            // Release notes ("What's new"), capped, with a "Full notes" link; shared block (see
            // UpdateReleaseNotes).
            PopVisible(visible = info.run.releaseNotes != null) {
                UpdateReleaseNotes(info, collapsedLines = 5, hazeState = hazeState)
            }
            // Install help only in the tap-through (non-seamless) path, as an opt-in disclosure.
            if (!seamless) {
                var showHelp by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
                SafeMorphTextButton(
                    if (showHelp) "Hide install help" else "Trouble installing?",
                    onClick = { showHelp = !showHelp },
                )
                PopVisible(visible = showHelp) {
                    // Glass surface, fillMaxWidth to match siblings; shadow = false (nested panel).
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape,
                        hazeState = hazeState,
                        shadow = false,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                            // Without Shizuku the OS installer interferes and the Play Protect
                            // sheet is collapsed by default, so say so up front.
                            if (!state.shizukuAvailable || !LocalAppearance.current.seamlessInstallShizuku) {
                                Text(
                                    "Shizuku is off, so Android asks you to confirm each update. On the next screen tap \"More details\" to expand it, then \"Install anyway\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (hasDirectDownload) "1. Tap \"Update\", then \"Install\" once it downloads" else "1. Download the APK, then open it",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // Play Protect flags any non-Play-Store APK; the tip stops "Blocked by
                            // Play Protect" reading as a failure.
                            Text(
                                "2. If you see \"Blocked by Play Protect\", tap \"More details\" → \"Install anyway\"",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            // The header pill already carries the primary action; only "Keep it" (pending-dismiss
            // undo) lives here. Dismiss / undo / remind in one shared row (UpdateDismissRow) so the
            // app tile and Settings card cannot drift.
            UpdateDismissRow(state, vm)
        }
        }
    }
}

/**
 * What the update's primary action says and does; shared by the pebble header action and the
 * Settings button so the label, glyph and branch never differ.
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
        state.updateApkReady -> Icons.Filled.CheckCircle
        state.updateDownloading -> Icons.Filled.Download
        else -> Icons.Filled.SystemUpdate
    },
    ready = state.updateApkReady,
)

/** Runs the update's primary action -- download, install, or open the release page. */
internal fun runUpdateAction(
    state: UiState,
    vm: AppViewModel,
    info: UpdateInfo,
    context: android.content.Context,
) {
    when {
        state.updateApkReady -> vm.installDownloadedUpdate()
        info.run.phoneApkUrl != null -> vm.downloadUpdateInBackground()
        else -> {
            val opened = context.tryStart(
                Intent(Intent.ACTION_VIEW, info.run.htmlUrl.toUri()),
            )
            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
        }
    }
}
