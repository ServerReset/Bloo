package com.bloo.bluelink.ui

/**
 * Update surfaces split out of Hero.kt: [UpdateAvailableTile], the standalone update tile pinned
 * below the hero tile, and [UpdateStatusLine], the shared live-status row used by the tile and
 * [SettingsHeroCard]'s expanded body.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.update.UpdateInfo
import com.bloo.uicommon.lowPowerSoft
import com.bloo.uicommon.IconBadgeContainer
import com.bloo.uicommon.ProgressBar
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.SecondaryTextStyle
import com.bloo.uicommon.content

/**
 * A full-width glass panel nested inside an already-elevated card: `shadow = false` so it does not
 * draw a second drop shadow inside the card (see glassEdge's own doc). Shared by every nested panel
 * on the update surfaces.
 */
@Composable
internal fun NestedGlassPanel(hazeState: dev.chrisbanes.haze.HazeState?, content: @Composable () -> Unit) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(), shape = SmallShape, hazeState = hazeState, shadow = false,
        content = content,
    )
}

/** The release notes, in full, under a "What's new" heading. */
@Composable
internal fun UpdateReleaseNotes(info: UpdateInfo, hazeState: dev.chrisbanes.haze.HazeState? = null) {
    val notes = info.run.releaseNotes?.trim().orEmpty()
    if (notes.isBlank()) return
    // Glass surface with unified blur styling. shadow = false -- shared by the pebble body AND
    // SettingsHeroCard's expanded body (this composable's own doc), both of which already nest this
    // inside another elevated card/group; see glassEdge's own doc for why a nested panel skips the
    // second shadow.
    NestedGlassPanel(hazeState) {
        Column(Modifier.padding(GapGroup), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            // The footer's Releases link is the way out to GitHub, so the notes carry no link of their own.
            RevealText("What's new", style = SecondaryTextStyle(), fontWeight = FontWeight.SemiBold)
            RevealText(
                notes,
                style = SecondaryTextStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun UpdateDismissRow(state: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val busy = state.updateDownloading || state.updateInstalling
    ActionRow(modifier) {
        MorphTextButton(
            "Remind me",
            onClick = { vm.snoozeUpdate() },
            enabled = !busy,
            emphasis = ButtonEmphasis.Confirm,
        )
        MorphTextButton("Not now", onClick = vm::dismissUpdate, enabled = !busy, emphasis = ButtonEmphasis.Deny)
    }
}

/**
 * The live status half of the update flow: the tonal icon badge, the animated one-line status
 * ("Downloading", "Downloaded · tap Install", "Installing silently via Shizuku…") and the download
 * progress bar.
 */
@Composable
internal fun UpdateStatusLine(
    deltaLabel: String,
    seamless: Boolean,
    state: UiState,
    vm: AppViewModel,
    /**
     * False when the tile's own summary is already [deltaLabel], which happens whenever the release
     * has no display title -- the summary is `displayTitle ?: deltaLabel`.
     */
    showDelta: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val downloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
    // ONE state-driven status line -- see the tile's own long comment (git history) on why
    // statusKind, not the rendered string, drives the AnimatedContent: the static word must stay
    // put while the percent moves.
    val (statusIcon, statusKind, statusTint) = when {
        state.updateInstalling -> Triple(Icons.Filled.SystemUpdate, "installing", scheme.onSurfaceVariant)
        state.updateDownloading -> Triple(Icons.Filled.Download, "downloading", scheme.onSurfaceVariant)
        state.updateApkReady && seamless -> Triple(AppIcons.CheckCircle, "ready_seamless", ChargeGreen)
        state.updateApkReady -> Triple(AppIcons.CheckCircle, "ready", ChargeGreen)
        seamless -> Triple(AppIcons.Bolt, "seamless", scheme.onSurfaceVariant)
        else -> Triple(Icons.Filled.SystemUpdate, "update", scheme.primary)
    }
    // Sprung, not a snap -- the tint is what carries "this got a step further along" (neutral ->
    // ChargeGreen once the APK is ready), so it gets the same treatment the charge bar's own
    // fill-colour spring does rather than cutting on one frame.
    val animatedStatusTint by androidx.compose.animation.animateColorAsState(
        targetValue = statusTint,
        animationSpec = lowPowerSoft(Spring.StiffnessLow),
        label = "updateStatusTint",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapRow)) {
        // A tonal badge behind the icon, not a bare glyph -- the same "icon gets its own coloured
        // circle" weight every pebble's stat hero leads with.
        IconBadgeContainer(containerColor = animatedStatusTint.copy(alpha = 0.15f), size = 36.dp) {
            AnimatedContent(
                targetState = statusIcon,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
                label = "updateStatusIcon",
            ) { icon ->
                Icon(icon, contentDescription = null, tint = animatedStatusTint, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedSwap(statusKind, label = "updateStatusText", modifier = Modifier.weight(1f).announcesChanges()) { kind ->
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
            )
            when (kind) {
                "installing" -> RevealText("Installing silently via Shizuku…", style = textStyle)
                "downloading" -> RevealText("Downloading", style = textStyle)
                "ready_seamless" -> RevealText("Downloaded · installs silently via Shizuku", style = textStyle)
                "ready" -> RevealText("Downloaded · tap Install", style = textStyle)
                "seamless" -> RevealText("Installs silently via Shizuku, no prompts", style = textStyle)
                // Never blank: when showDelta is false the caller's own summary line already says
                // the delta, but leaving this Unit left the icon badge floating in the row with
                // nothing beside it -- a real empty gap, not just an unused word.
                else -> RevealText(if (showDelta) deltaLabel else "Ready to download", style = textStyle)
            }
        }
    }
    // Live download progress bar. See PopVisible's own doc.
    PopVisible(visible = state.updateDownloading, sizeAnimated = true) {
        // fillMaxWidth() is required here, not optional: a Row that's a DIRECT child of PopVisible
        // (AnimatedVisibility) and relies on weight() to size a child (the progress bar below)
        // collapses to a near-zero width without it -- AnimatedVisibility measures its content's
        // "natural" size before it has anything but the weighted child to size against, unlike a
        // Row inside an already-bounded parent.
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(GapRow))
            ProgressBar(downloadProgress)
        }
    }
}
