package com.bloo.bluelink.ui

/**
 * Update surfaces split out of Hero.kt: [UpdateAvailableTile], the standalone
 * update tile pinned below the hero tile, and [UpdateStatusLine], the shared
 * live-status row used by the tile and [SettingsHeroCard]'s expanded body.
 */

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.bloo.bluelink.update.UpdateInfo
import com.bloo.bluelink.data.Weather

/**
 * The release notes block: "What's new", an excerpt, and a link to the full notes.
 *
 * Shared by the update pebble and [SettingsHeroCard]'s expanded body, which had a copy each --
 * same Surface, same header row, same "Full notes" button, differing only in how many lines of
 * the excerpt they showed and in one of them forgetting FLAG_ACTIVITY_NEW_TASK on the intent.
 * That is the shape of drift this exists to stop: two blocks that are the same idea, kept in
 * step by hand until one of them quietly is not.
 *
 * fillMaxWidth() is load-bearing, not decoration: this can sit inside a PopVisible, and with a
 * weight()-bearing Text in the header row and no explicit width anywhere in the chain, that Text
 * collapses to near-zero and wraps one character per line.
 */
@Composable
internal fun UpdateReleaseNotes(
    info: UpdateInfo,
    /** Lines shown while COLLAPSED. 5 in the pebble, which has the room; 3 in the
     *  Settings card, which does not. Tapping "Show more" expands in place to the whole
     *  note, so a long changelog is readable in the app instead of forcing a GitHub trip. */
    collapsedLines: Int = 5,
    hazeState: dev.chrisbanes.haze.HazeState? = null,
) {
    val notes = info.run.releaseNotes?.trim().orEmpty()
    if (notes.isBlank()) return
    val context = LocalContext.current
    // Expanded in place: held across the card's own recompositions (keyed on the build),
    // so a long note can be read fully without leaving the app. "GitHub" stays as the
    // escape hatch to the formatted release page.
    var expanded by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
    // Glass surface with unified blur styling. shadow = false -- shared by the
    // pebble body AND SettingsHeroCard's expanded body (this composable's own doc),
    // both of which already nest this inside another elevated card/group; see
    // glassEdge's own doc for why a nested panel skips the second shadow.
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = SmallShape,
        hazeState = hazeState,
        shadow = false,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            // Header + actions in a FlowRow, not a weighted Row: at a huge font the two action
            // buttons plus the title no longer fit one line, and a `weight(1f)` title squeezed
            // between them collapsed/clipped instead of the row simply wrapping. FlowRow lets
            // the actions drop to their own line when they must.
            FlowRow(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(GapHairline),
                horizontalArrangement = Arrangement.spacedBy(GapRow),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "What's new",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // Deterministic, not onTextLayout-dependent: `hasVisualOverflow` from a
                // single-line-clamped Text is unreliable (it can arrive a pass late or not at
                // all for the ellipsized case), which is what made the old "Show more" appear
                // and disappear unpredictably. A plain estimate -- more newlines than the
                // collapsed view shows, OR more characters than a collapsed view could hold --
                // is stable the moment we have the text, so the affordance is always right.
                val looksLong =
                    notes.count { it == '\n' } >= collapsedLines || notes.length > collapsedLines * 48
                if (looksLong || expanded) {
                    SafeMorphTextButton(
                        if (expanded) "Show less" else "Show more",
                        onClick = { expanded = !expanded },
                        fillOnPress = false,
                    )
                }
                SafeMorphTextButton(
                    "GitHub",
                    onClick = { context.tryStart(Intent(Intent.ACTION_VIEW, info.run.htmlUrl.toUri())) },
                    fillOnPress = false,
                )
            }
            Text(
                notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (expanded) {
                    // Expanded: the whole changelog inline, but BOUNDED and scrollable so a very
                    // long release note can't grow the card to several screens tall and push the
                    // buttons off the bottom. The height is generous but finite.
                    Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
                // Collapsed: capped at [collapsedLines], ellipsized. Expanded: no line cap (the
                // scroll bound above is what limits it), so the changelog reads fully.
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The update card's dismissal row, shared by the app tile and the Settings card so the two
 * behave and read identically (they used to differ: the Settings card had only "Not now" and no
 * undo at all, and the app tile stacked a second "Keep it" on its own line above the real row).
 *
 * Idle: "Remind me" (a deferral) and "Not now" (dismiss). During the undo window after a
 * dismiss: a single row -- "Dismissing…" with a prominent "Keep it" -- instead of the old
 * two-row jumble.
 */
@Composable
internal fun UpdateDismissRow(
    state: UiState,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    val busy = state.updateDownloading || state.updateInstalling
    if (state.updatePendingDismiss) {
        Row(
            modifier.fillMaxWidth(),
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
                emphasis = ButtonEmphasis.Primary,
            )
        }
    } else {
        ExpressiveButtonRow(modifier = modifier.fillMaxWidth(), spacing = GapRow) {
            MorphTextButton(
                "Remind me",
                onClick = { vm.snoozeUpdate() },
                enabled = !busy,
            )
            SafeMorphTextButton(
                "Not now",
                onClick = vm::dismissUpdate,
                enabled = !busy,
            )
        }
    }
}

/**
 * The live status half of the update flow: the tonal icon badge, the
 * animated one-line status ("Downloading", "Downloaded · tap Install",
 * "Installing silently via Shizuku…") and the download progress bar. The live
 * percentage itself shows only on the header action pill above this (this
 * file's own PebbleHeaderAction) -- it used to also repeat here AND next to
 * the bar below, reported directly as the same number appearing three times.
 *
 * Shared by the update pebble's body and [SettingsHeroCard]'s expanded body, so the
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
    val downloadProgress by vm.updateDownloadProgress.collectAsStateWithLifecycle()
    // ONE state-driven status line -- see the tile's own long comment (git
    // history) on why statusKind, not the rendered string, drives the
    // AnimatedContent: the static word must stay put while the percent moves.
    val (statusIcon, statusKind, statusTint) = when {
        state.updateInstalling -> Triple(Icons.Filled.SystemUpdate, "installing", scheme.onSurfaceVariant)
        state.updateDownloading -> Triple(Icons.Filled.Download, "downloading", scheme.onSurfaceVariant)
        state.updateApkReady && seamless -> Triple(Icons.Filled.CheckCircle, "ready_seamless", ChargeGreen)
        state.updateApkReady -> Triple(Icons.Filled.CheckCircle, "ready", ChargeGreen)
        seamless -> Triple(AppIcons.Bolt, "seamless", scheme.onSurfaceVariant)
        else -> Triple(Icons.Filled.SystemUpdate, "update", scheme.primary)
    }
    // Sprung, not a snap -- the tint is what carries "this got a step further along"
    // (neutral -> ChargeGreen once the APK is ready), so it gets the same treatment
    // the charge bar's own fill-colour spring does rather than cutting on one frame.
    val animatedStatusTint by androidx.compose.animation.animateColorAsState(
        targetValue = statusTint,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "updateStatusTint",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // A tonal badge behind the icon, not a bare glyph -- the same "icon gets its
        // own coloured circle" weight every pebble's stat hero leads with.
        IconBadgeContainer(containerColor = animatedStatusTint.copy(alpha = 0.15f), size = 36.dp) {
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
            transitionSpec = { expandContentTransform() },
            label = "updateStatusText",
            modifier = Modifier.weight(1f),
        ) { kind ->
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
            )
            when (kind) {
                "installing" -> Text("Installing silently via Shizuku…", style = textStyle)
                // Plain "Downloading", no trailing percentage -- this line and the
                // number beside the progress bar below used to EACH carry their own
                // copy of the same value the header action pill above already shows
                // (PebbleHeaderAction's own label, up in this file), reported directly
                // from a screenshot as the same percentage appearing twice on screen at
                // once. That header pill is now the one place it's shown.
                "downloading" -> Text("Downloading", style = textStyle)
                "ready_seamless" -> Text("Downloaded · installs silently via Shizuku", style = textStyle)
                "ready" -> Text("Downloaded · tap Install", style = textStyle)
                "seamless" -> Text("Installs silently via Shizuku, no prompts", style = textStyle)
                // Never blank: when showDelta is false the caller's own summary line already
                // says the delta, but leaving this Unit left the icon badge floating in the row
                // with nothing beside it -- a real empty gap, not just an unused word.
                else -> Text(if (showDelta) deltaLabel else "Ready to download", style = textStyle)
            }
        }
    }
    // Live download progress bar. Own PopVisible rather than a bare `if` --
    // this bar arrives and leaves while the card is already open (download
    // starts, download finishes). sizeAnimated: this pebble sits in the
    // reorderable stack, and the pebble below it repositions itself with its
    // own ~300ms spring (ReorderColumn's animatePlacement) whenever this one's
    // height changes -- without this, the bar popped in at full height on one
    // layout pass while the sibling below was still catching up, so the two
    // visibly overlapped for that whole window. See PopVisible's own doc.
    PopVisible(visible = state.updateDownloading, sizeAnimated = true) {
        // fillMaxWidth() is required here, not optional: a Row that's a DIRECT child of
        // PopVisible (AnimatedVisibility) and relies on weight() to size a child (the
        // progress bar below) collapses to a near-zero width without it -- AnimatedVisibility
        // measures its content's "natural" size before it has anything but the weighted
        // child to size against, unlike a Row inside an already-bounded parent. See
        // SettingsScreen.kt's Weather-card place-name Row for the same bug, confirmed by
        // screenshot (text wrapped one character per line).
        Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(GapRow))
            val p = downloadProgress
            Surface(
                modifier = Modifier.fillMaxWidth().height(8.dp),
                shape = CircleShape,
                color = scheme.onSurface.copy(alpha = 0.12f),
            ) {
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                }
            }
        }
    }
}
