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
        // Download progress is collected from its own StateFlow rather than read off UiState, so a
        // per-chunk tick invalidates only this tile's bar/percent, not every pebble on the live
        // pager pages. state.updateDownloading (the boolean that gates the display below) stays on
        // UiState -- it changes twice per download, not hundreds of times.
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
        // Always visible=true: this whole composable only ever renders once an update actually
        // exists (the AnimatedVisibility above gates that), so the badge and the card's own
        // existence say the same thing -- consistent with SettingsHeroCard wearing the identical
        // dot, rather than this being the one update surface without it.
        UpdateBadgedCard(visible = true, modifier = Modifier.fillMaxWidth()) {
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = modifier,
            icon = Icons.Filled.SystemUpdate,
            title = "Update available",
            summary = info.run.displayTitle?.takeIf { it.isNotBlank() } ?: newLabel,
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
                            // Dismiss ONLY if the page really opened. Swallowing an
                            // ActivityNotFoundException and dismissing anyway meant a tap did
                            // visibly nothing AND cost the user the tile.
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
        NestedGlassPanel(hazeState) {
            // Column, not Box: UpdateStatusLine emits two top-level siblings that a Box would stack
            // on top of each other.
            Column(Modifier.padding(GapGroup)) {
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
            // Progressive install help: only in the tap-through (non-seamless) path, and only as an
            // opt-in disclosure — the Play-Protect steps are scaffolding, not something to shout
            // before the user has even tapped Update.
            if (!seamless) {
                var showHelp by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
                SafeMorphTextButton(
                    if (showHelp) "Hide install help" else "Trouble installing?",
                    onClick = { showHelp = !showHelp },
                )
                PopVisible(visible = showHelp) {
                    // Glass surface, fillMaxWidth to match siblings; shadow = false (nested panel).
                    NestedGlassPanel(hazeState) {
                        Column(Modifier.padding(GapGroup), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                            // Without Shizuku the OS installer gets in the way EVERY time, and the
                            // player-protect sheet is folded shut by default, so an update looks
                            // like it failed when the real answer is "expand it, then tap Install
                            // anyway". Spell that out up front rather than only after someone
                            // reports it as broken.
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
 * What the update's primary action says and does, in one place. The pebble surfaces it as its
 * header action and the Settings card as a full-width button, so the CHROME differs -- but the
 * label, the glyph and the branch it takes must not, and they had a copy each.
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
