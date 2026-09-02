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
 * live-status row used by the tile and the Settings Updates card.
 */

import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.update.UpdateInfo
import com.bloo.bluelink.data.Weather
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
 * rolling GitHub Release (see android.yml) with the raw phone/watch APKs
 * attached as plain public assets, so the primary action can download the
 * APK directly instead of opening a browser page.
 */
@Composable
internal fun UpdateAvailableTile(state: UiState, vm: AppViewModel, dragHandle: Modifier = Modifier) {
    val info = state.updateAvailable
    // Stays visible during the pending-dismiss (undo) window — only the committed
    // updateTileDismissed truly hides it.
    AnimatedVisibility(
        visible = info != null && !state.updateTileDismissed,
        enter = collapseEnter(Alignment.Bottom),
        exit = collapseExit(Alignment.Bottom),
    ) {
        if (info == null) return@AnimatedVisibility
        val context = LocalContext.current
        // Download progress is collected from its own StateFlow rather than read off UiState,
        // so a per-chunk tick invalidates only this tile's bar/percent, not every pebble on the
        // live pager pages. state.updateDownloading (the boolean that gates the display below)
        // stays on UiState -- it changes twice per download, not hundreds of times.
        val downloadProgress by vm.updateDownloadProgress.collectAsState()
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
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            dragHandle = dragHandle,
            icon = Icons.Filled.SystemUpdate,
            title = "Update available",
            vm = vm,
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
                    state.updateDownloading -> downloadProgress?.let { "${(it * 100).roundToInt()}%" } ?: "Downloading…"
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
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl)))
                            }.isSuccess
                            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                        }
                    }
                },
            ),
        ) {
            val scheme = MaterialTheme.colorScheme
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
        UpdateStatusLine(
            deltaLabel, seamless, state, vm,
            showDelta = info.run.displayTitle?.isNotBlank() == true,
        )
            // Release notes ("What's new"), capped, with a "Full notes" link to the
            // release page when there's more than we show. One shared block -- see
            // UpdateReleaseNotes for why the Settings card no longer keeps its own copy.
            PopVisible(visible = info.run.releaseNotes != null) {
                UpdateReleaseNotes(info, maxLines = 5)
            }
            // Progressive install help: only in the tap-through (non-seamless) path, and
            // only as an opt-in disclosure — the Play-Protect steps are scaffolding, not
            // something to shout before the user has even tapped Update.
            if (!seamless) {
                var showHelp by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
                val helpSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = helpSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        if (showHelp) "Hide install help" else "Trouble installing?",
                        onClick = { showHelp = !showHelp },
                        interactionSource = helpSource,
                    )
                }
                PopVisible(visible = showHelp) {
                    // fillMaxWidth() to match the release-notes Surface right above --
                    // without it this panel wrap-contents to its widest line instead of
                    // matching its sibling's full-card width.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                val keepSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = keepSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        "Keep it",
                        onClick = vm::undoDismissUpdate,
                        interactionSource = keepSource,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            // Dismiss / undo / remind — hierarchy: during the undo window "Keep it" is
            // the recoverable emphasis; otherwise "Remind me" (deferral) is emphasized
            // over the plainer "Not now".
            if (state.updatePendingDismiss) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dismissing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    val undoSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = undoSource,
                        enabled = !state.updateDownloading,
                    ) {
                        MorphButton(
                            onClick = { vm.undoDismissUpdate() },
                            enabled = !state.updateDownloading,
                            active = true,
                            interactionSource = undoSource,
                        ) { Text("Keep it", fontWeight = FontWeight.SemiBold) }
                    }
                }
            } else {
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                    val remindSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = remindSource,
                        enabled = !state.updateDownloading,
                    ) {
                        MorphButton(
                            onClick = { vm.snoozeUpdate() },
                            enabled = !state.updateDownloading,
                            interactionSource = remindSource,
                        ) { Text("Remind me") }
                    }
                    val notNowSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = notNowSource,
                        enabled = !state.updateDownloading,
                    ) {
                        MorphTextButton(
                            "Not now",
                            onClick = vm::dismissUpdate,
                            enabled = !state.updateDownloading,
                            interactionSource = notNowSource,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The release notes block: "What's new", an excerpt, and a link to the full notes.
 *
 * Shared by the update pebble and the Settings Updates card, which had a copy each -- same
 * Surface, same header row, same "Full notes" button, differing only in how many lines of the
 * excerpt they showed and in one of them forgetting FLAG_ACTIVITY_NEW_TASK on the intent. That
 * is the shape of drift this exists to stop: two blocks that are the same idea, kept in step by
 * hand until one of them quietly is not.
 *
 * fillMaxWidth() is load-bearing, not decoration: this can sit inside a PopVisible, and with a
 * weight()-bearing Text in the header row and no explicit width anywhere in the chain, that Text
 * collapses to near-zero and wraps one character per line.
 */
@Composable
internal fun UpdateReleaseNotes(
    info: UpdateInfo,
    /** 5 in the pebble, which has the room; 3 in the Settings card, which does not. */
    maxLines: Int = 5,
) {
    val notes = info.run.releaseNotes?.trim().orEmpty()
    if (notes.isBlank()) return
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // "Full notes" rides in the section header rather than taking a whole row of its
            // own below the excerpt -- one less stacked block in a tile that already carries
            // status, notes and two dismissals.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "What's new",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val notesSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(interactionSource = notesSource, enabled = true) {
                    MorphTextButton(
                        "Full notes",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl))
                                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                )
                            }
                        },
                        interactionSource = notesSource,
                    )
                }
            }
            Text(
                notes,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
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
                    Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl))
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                )
            }.isSuccess
            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
        }
    }
}

/**
 * The live status half of the update flow: the tonal icon badge, the
 * animated one-line status ("Downloading 46%", "Downloaded · tap Install",
 * "Installing silently via Shizuku…") and the download progress bar.
 *
 * Shared by the update pebble's body and the Settings Updates card, so the
 * two can never drift apart -- this is the same state machine rendered the
 * same way in both places, exactly the "one implementation" rule the
 * Settings card's remake is about.
 *
 * [deltaLabel] ("build 812 → 828") is what the idle state says; [seamless]
 * selects the Shizuku phrasing and the "installs silently" hint.
 */
@Composable
internal fun UpdateStatusLine(
    deltaLabel: String,
    seamless: Boolean,
    state: UiState,
    vm: AppViewModel,
    /**
     * False when the tile's own summary is already [deltaLabel], which happens whenever the
     * release has no display title -- the summary is `displayTitle ?: deltaLabel`. The idle
     * branch below then said the same "build 812 → build 828" a second time, directly under the
     * first, which on the cover is the tile's headline and on the phone is the collapsed summary
     * of the very pebble you just expanded. The other branches all report live progress the
     * summary cannot know, so they are unaffected.
     */
    showDelta: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val downloadProgress by vm.updateDownloadProgress.collectAsState()
    // ONE state-driven status line -- see the tile's own long comment (git
    // history) on why statusKind, not the rendered string, drives the
    // AnimatedContent: the static word must stay put while the percent moves.
    val (statusIcon, statusKind, statusTint) = when {
        state.updateInstalling -> Triple(Icons.Filled.SystemUpdate, "installing", scheme.onSurfaceVariant)
        state.updateDownloading -> Triple(Icons.Filled.Download, "downloading", scheme.onSurfaceVariant)
        state.updateApkReady && seamless -> Triple(Icons.Filled.CheckCircle, "ready_seamless", ChargeGreen)
        state.updateApkReady -> Triple(Icons.Filled.CheckCircle, "ready", ChargeGreen)
        seamless -> Triple(Icons.Filled.Bolt, "seamless", scheme.onSurfaceVariant)
        else -> Triple(Icons.Filled.SystemUpdate, "update", scheme.primary)
    }
    // Sprung, not a snap -- the tint is what carries "this got a step further along"
    // (neutral -> ChargeGreen once the APK is ready), so it gets the same treatment
    // the charge bar's own fill-colour spring does rather than cutting on one frame.
    val animatedStatusTint by androidx.compose.animation.animateColorAsState(
        targetValue = statusTint,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "updateStatusTint",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // A tonal badge behind the icon, not a bare glyph -- the same "icon gets its
        // own coloured circle" weight CoverHero gives every stat it leads with.
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(animatedStatusTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            // AnimatedContent, not a bare Icon swap -- installing -> downloading ->
            // ready is a real sequence of distinct states, and a plain `when` cut
            // between their icons on one frame while everything else on this card is
            // now springing and cascading into place.
            AnimatedContent(
                targetState = statusIcon,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
                label = "updateStatusIcon",
            ) { icon ->
                Icon(icon, contentDescription = null, tint = animatedStatusTint, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedContent(
            targetState = statusKind,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
            },
            label = "updateStatusText",
            modifier = Modifier.weight(1f),
        ) { kind ->
            // color resolved explicitly, not left Color.Unspecified -- the
            // "Downloading X%" AnimatedValue below renders through BasicText,
            // which (unlike Text) does NOT fall back to LocalContentColor for
            // an unspecified color; it fell back to Android's own paint
            // default (black) instead.
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
            )
            when (kind) {
                "installing" -> Text("Installing silently via Shizuku…", style = textStyle)
                "downloading" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Downloading", style = textStyle)
                    downloadProgress?.let { p ->
                        Text(" ", style = textStyle)
                        // Its own AnimatedValue, not part of this AnimatedContent's own
                        // string -- this is the one piece of the line that legitimately
                        // changes every tick, so it's the only piece that should move.
                        // fontFeatureSettings = "tnum" enables tabular figures so only
                        // the changing digit animates up without the whole number
                        // shifting left/right.
                        com.bloo.uicommon.AnimatedValue(
                            "${(p * 100).roundToInt()}%",
                            style = textStyle.copy(fontFeatureSettings = "tnum"),
                            reduceMotion = LocalReduceMotion.current,
                        )
                    } ?: Text("…", style = textStyle)
                }
                "ready_seamless" -> Text("Downloaded · installs silently via Shizuku", style = textStyle)
                "ready" -> Text("Downloaded · tap Install", style = textStyle)
                "seamless" -> Text("Installs silently via Shizuku, no prompts", style = textStyle)
                else -> if (showDelta) Text(deltaLabel, style = textStyle) else Unit
            }
        }
    }
    // Live download progress bar. Own PopVisible rather than a bare `if` --
    // this bar arrives and leaves while the card is already open (download
    // starts, download finishes).
    PopVisible(visible = state.updateDownloading) {
        // fillMaxWidth() is required here, not optional: a Row that's a DIRECT child of
        // PopVisible (AnimatedVisibility) and relies on weight() to size a child (the
        // progress bar below) collapses to a near-zero width without it -- AnimatedVisibility
        // measures its content's "natural" size before it has anything but the weighted
        // child to size against, unlike a Row inside an already-bounded parent. See
        // SettingsScreen.kt's Weather-card place-name Row for the same bug, confirmed by
        // screenshot (text wrapped one character per line).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = downloadProgress
            Surface(
                modifier = Modifier.weight(1f).height(8.dp),
                shape = CircleShape,
                color = scheme.onSurface.copy(alpha = 0.12f),
            ) {
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                }
            }
            if (p != null) {
                com.bloo.uicommon.AnimatedValue(
                    "${(p * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current,
                        fontFeatureSettings = "tnum",
                    ),
                    reduceMotion = LocalReduceMotion.current,
                )
            }
        }
    }
}
