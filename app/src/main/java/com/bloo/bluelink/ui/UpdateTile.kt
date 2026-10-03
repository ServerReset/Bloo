@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Update surfaces split out of Hero.kt: [UpdateAvailableTile], the standalone
 * update tile pinned below the hero tile, and [UpdateStatusLine], the shared
 * live-status row used by the tile and [SettingsHeroCard]'s expanded body.
 */

import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bloo.bluelink.update.UpdateInfo
import kotlin.math.roundToInt

/**
 * Bloo isn't on the Play Store, so this is its own update surface: a
 * standalone tile pinned directly below the hero tile whenever the checker
 * has found a newer build, animating in/out instead of interrupting with a
 * popup. Collapse/expand reuses the exact same [PebbleShell] every other
 * pebble is built on (this isn't tied to a car/section, hence PebbleShell
 * directly rather than the [Pebble] wrapper) -- collapsed, the header action
 * button doubles as the primary control and shows live download state
 * (Update / downloading % / Install); expanded, it adds install steps, this
 * build's release notes, and Remind-me/Not-now. Every push publishes a
 * rolling GitHub Release (see android.yml) with the raw phone APK
 * attached as a plain public asset, so the primary action can download the
 * APK directly instead of opening a browser page.
 */
@Composable
internal fun UpdateAvailableTile(
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    val info = state.updateAvailable
    // Stays visible during the pending-dismiss (undo) window — only the committed
    // updateTileDismissed truly hides it.
    AnimatedVisibility(
        visible = info != null && !state.updateTileDismissed,
        enter = expandEnter(Alignment.Bottom),
        exit = expandExit(Alignment.Bottom),
    ) {
        if (info == null) return@AnimatedVisibility
        val context = LocalContext.current
        // Download progress is collected from its own StateFlow rather than read off UiState,
        // so a per-chunk tick invalidates only this tile's bar/percent, not every pebble on the
        // live pager pages. state.updateDownloading (the boolean that gates the display below)
        // stays on UiState -- it changes twice per download, not hundreds of times.
        val downloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
        val hasDirectDownload = info.run.phoneApkUrl != null
        val current = vm.currentBuildNumber
        // Build delta: "build 812 → build 828" when we know the installed build,
        // else just the target. buildLabel is the one canonical version formatter.
        val newLabel = com.bloo.bluelink.data.buildLabel(info.run.runNumber)
        val deltaLabel = if (current > 0) {
            "${com.bloo.bluelink.data.buildLabel(current)} → $newLabel"
        } else {
            newLabel
        }
        val seamless = LocalAppearance.current.seamlessInstallShizuku && state.shizukuAvailable
        // Keyed on the build number so a genuinely different build (see
        // checkForUpdate's sameBuild check) starts collapsed again rather
        // than inheriting whatever expand state an earlier build was left in.
        var expanded by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
        // Always visible=true: this whole composable only ever renders once an update
        // actually exists (the AnimatedVisibility above gates that), so the badge and
        // the card's own existence say the same thing -- consistent with SettingsHeroCard
        // wearing the identical dot, rather than this being the one update surface without it.
        UpdateBadgedCard(visible = true, modifier = Modifier.fillMaxWidth()) {
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = modifier,
            icon = Icons.Filled.SystemUpdate,
            title = "Update available",
            summary = info.run.displayTitle?.takeIf { it.isNotBlank() } ?: deltaLabel,
            // No containerColor override -- PebbleShell's own default
            // (surfaceVariant) is what every ordinary pebble uses too
            // (Climate, Charge, Info, ...); this used primaryContainer,
            // which read as a special/different-looking tile instead of
            // fitting in with the rest of the per-car stack. AI's pebble is
            // the one deliberate exception (tertiaryContainer) -- this
            // wasn't meant to be another one.
            headerAction = PebbleHeaderAction(
                label = when {
                    state.updateInstalling -> "Installing…"
                    // Rounded to the nearest 5% -- this chip is now the ONE place a
                    // download percentage shows at all (see UpdateStatusLine's own doc:
                    // the inline "Downloading X%" text and the number beside the
                    // progress bar were both removed as redundant with this exact same
                    // value, reported directly from a screenshot showing the same
                    // percentage twice on screen at once). Coarser increments read as
                    // "still moving" just as clearly as 1% ticks while recomposing this
                    // pill a fifth as often.
                    state.updateDownloading -> downloadProgress?.let { "${(it * 100 / 5f).roundToInt() * 5}%" } ?: "Downloading…"
                    state.updateApkReady -> if (seamless) "Install now" else "Install"
                    hasDirectDownload -> "Update"
                    else -> "Open"
                },
                icon = if (state.updateApkReady) Icons.Filled.SystemUpdate else Icons.Filled.Download,
                pending = state.updateDownloading || state.updateInstalling,
                enabled = !state.updateInstalling,
                // Same ChargeGreen/white pairing ChargePebble's own headerAction
                // uses for its "charging" active state -- this button used to stay
                // the same neutral, low-contrast default container/text regardless
                // of state, so the one moment this tile has a real "tap this now"
                // call to action (the download finished, install is one tap away)
                // looked identical to every other, less urgent state.
                active = state.updateApkReady,
                activeContainer = ChargeGreen,
                activeContent = Color.White,
                onClick = {
                    when {
                        state.updateApkReady -> vm.installDownloadedUpdate()
                        hasDirectDownload -> vm.downloadUpdateInBackground()
                        else -> {
                            // Dismiss ONLY if the page really opened. Swallowing an
                            // ActivityNotFoundException and dismissing anyway meant a
                            // tap did visibly nothing AND cost the user the tile.
                            val opened = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, info.run.htmlUrl.toUri()))
                            }.isSuccess
                            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                        }
                    }
                },
            ),
        ) {
            val scheme = MaterialTheme.colorScheme
            UpdateDeltaHero(current, info.run.runNumber)
            // ONE state-driven status line (icon + text), replacing the old duplicated
            // delta row + scattered downloading/seamless/installing rows. The build
            // delta already lives in the header summary; here we say what's happening
            // NOW. Ready uses ChargeGreen as a success tick; everything else stays
            // neutral (no charging-green Bolt cross-metaphor).
            //
            // statusKind, not the rendered string, is what drives the AnimatedContent below --
            // it stays "downloading" for the WHOLE download instead of becoming a new string
            // on every percentage tick, which is what used to make "Downloading 45%" slide/fade
            // out and "Downloading 46%" slide/fade in as if they were two different states:
            // the static word was animating right along with the number that actually changed.
            // Only the percent itself is a moving target now (rendered with its own
            // AnimatedValue below), and the sentence around it stays put.
        // Glass surface instead of tonal -- matches the unified glass styling throughout the app.
        // Now has real blur when available, instead of a plain tonal fill.
        // shadow = false: this sits INSIDE the pebble's own already-elevated card,
        // not floating over the screen -- see glassEdge's own doc for why a second
        // full-strength shadow on a small nested panel read as a harsh dark smudge,
        // reported directly from a screenshot.
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
            hazeState = hazeState,
            shadow = false,
        ) {
            // Column, not Box: UpdateStatusLine emits two top-level siblings of its own (the
            // icon+status Row, then the PopVisible progress bar) with no Column of its own
            // wrapping them -- see its own call site in SettingsScreen.kt, which already
            // places it inside a Column and renders correctly. A bare Box here instead
            // stacked those two children ON TOP of each other at the same position (Box's
            // default behavior for un-aligned children) rather than one above the other,
            // which is exactly what put "Downloading" directly over the progress bar's own
            // trailing percentage label -- reported from a real screenshot.
            Column(Modifier.padding(12.dp)) {
                UpdateStatusLine(
                    deltaLabel, seamless, state, vm,
                    showDelta = info.run.displayTitle?.isNotBlank() == true,
                )
            }
        }
            // Release notes ("What's new"), capped, with a "Full notes" link to the
            // release page when there's more than we show. One shared block -- see
            // UpdateReleaseNotes for why the Settings card no longer keeps its own copy.
            PopVisible(visible = info.run.releaseNotes != null) {
                UpdateReleaseNotes(info, maxLines = 5, hazeState = hazeState)
            }
            // Progressive install help: only in the tap-through (non-seamless) path, and
            // only as an opt-in disclosure — the Play-Protect steps are scaffolding, not
            // something to shout before the user has even tapped Update.
            if (!seamless) {
                var showHelp by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
                SafeMorphTextButton(
                    if (showHelp) "Hide install help" else "Trouble installing?",
                    onClick = { showHelp = !showHelp },
                )
                PopVisible(visible = showHelp) {
                    // Glass surface instead of tonal -- unified styling with glass blur.
                    // fillMaxWidth() to match sibling panels. shadow = false -- see the
                    // status panel's own comment above for why a nested panel doesn't
                    // get a second full-strength drop shadow.
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SmallShape,
                        hazeState = hazeState,
                        shadow = false,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                            Text(
                                if (hasDirectDownload) "1. Tap \"Update\", then \"Install\" once it downloads" else "1. Download the APK, then open it",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // Play Protect flags any non-Play-Store APK; without this tip,
                            // "Blocked by Play Protect" reads like a real failure.
                            Text(
                                "2. If you see \"Blocked by Play Protect\", tap \"More details\" → \"Install anyway\"",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            // The header pill is this exact control -- same onClick, same
            // download/install/open branch -- and it is visible whether the card is
            // collapsed or open, which a body-level copy underneath an already-open
            // card can never be more discoverable than. Repeating it down here used
            // to be the "two moments the header button can be missed" argument, but
            // once the card is open there is no such moment: the header is right
            // there. That duplicate control -- plus everything already stacked below
            // it (status line, release notes, install-help) -- was the reported
            // "too much content/too busy". Only "Keep it" has no other home: it
            // exists purely for the pending-dismiss undo window, so it is the one
            // piece that stays.
            if (state.updatePendingDismiss) {
                SafeMorphTextButton(
                    "Keep it",
                    onClick = vm::undoDismissUpdate,
                )
                Spacer(Modifier.height(GapHairline))
            }
            // Dismiss / undo / remind — hierarchy: during the undo window "Keep it" is
            // the recoverable emphasis; otherwise "Remind me" (deferral) is emphasized
            // over the plainer "Not now".
            if (state.updatePendingDismiss) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GapRow),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dismissing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MorphTextButton(
                        "Keep it",
                        onClick = { vm.undoDismissUpdate() },
                        enabled = !state.updateDownloading,
                        emphasis = ButtonEmphasis.Primary,
                    )
                }
            } else {
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = GapRow) {
                    MorphTextButton(
                        "Remind me",
                        onClick = { vm.snoozeUpdate() },
                        enabled = !state.updateDownloading,
                    )
                    SafeMorphTextButton(
                        "Not now",
                        onClick = vm::dismissUpdate,
                        enabled = !state.updateDownloading,
                    )
                }
            }
        }
        }
    }
}

/**
 * What the update's primary action says and does, in one place.
 *
 * The pebble surfaces it as its header action and the Settings card as a full-width button, so
 * the CHROME differs -- but the label, the glyph and the branch it takes must not, and they had
 * a copy each. Both now read from here.
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
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, info.run.htmlUrl.toUri())
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                )
            }.isSuccess
            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
        }
    }
}
