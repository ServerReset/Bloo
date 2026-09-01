@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Settings' screen-header cluster, peeled out of SettingsScreen.kt (which still owns
 * the big `SettingsScreen` composable): the mode-stagger constant and
 * staggeredAdvancedVisible helper, the tonal StatusHeaderRow badge, the non-hoisted
 * LocalSettingsPillState badge state, and the floating SettingsHeaderRow title row
 * with its position-flight latch.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Delays an advanced-only card's own entrance by `index * STAGGER_STEP_MS` once [advanced]
 * flips true, so switching into Advanced mode cascades card by card instead of every
 * advanced-only section overshooting on the exact same frame -- the same "one shared
 * progress, remapped per item" idea [StaggeredRevealColumn] uses for a pebble's rows,
 * adapted here for a handful of independent [AnimatedVisibility] instances rather than one
 * Layout's worth of children (there's no single shared container to run a Layout-based
 * cascade over: these are whole, separately-composed [SettingsCard]s scattered through one
 * long screen, not rows of one component).
 *
 * The flip back to Simple mode is immediate -- no stagger, no delay -- on purpose, not by
 * omission: [StaggeredRevealColumn]'s own close side went through exactly this mistake
 * first. Staggering a HIDE means most items sit fully visible doing nothing while they wait
 * their turn, then disappear abruptly right at the end, which reads as broken rather than
 * polished (see that composable's doc for the fuller account). Revealing in sequence looks
 * deliberate; hiding in sequence looks like a bug, so only the reveal gets one.
 */
internal const val STAGGER_STEP_MS = 45L

/** How many advanced-only cards Settings staggers in as whole grid items. Kept beside the
 *  stagger itself so the count and the sequence cannot drift apart when a card is added.
 *
 *  Six, not nine: three further advanced blocks are nested INSIDE other cards and drive
 *  themselves through [staggeredAdvancedVisible], which takes its own index and never touches
 *  this list. The count only ever covers the cards this screen gates as items. */
internal const val ADVANCED_CARD_COUNT = 6

/**
 * The same staggered reveal as [staggeredAdvancedVisible], but for ALL advanced cards at once,
 * hoisted OUT of the lazy grid's item content and into the screen's own composition.
 *
 * That hoist is the point. Read from inside `item { AnimatedVisibility(visible = ...) }`, the
 * visibility can only ever hide a card's CONTENT -- the `item {}` itself still occupies a slot
 * in the LazyVerticalStaggeredGrid, and the grid still applies its `verticalItemSpacing` around
 * that now zero-height slot. Every advanced-only card left a phantom gap behind in simple mode,
 * which is what "bad spacing when pebbles are hidden" is: not one wrong padding, but eight
 * invisible items each holding a grid gap open. Returned as a plain list the grid's DSL can
 * read, the screen can decide not to emit the item at all -- no slot, no spacing, no gap.
 */
@Composable
internal fun rememberAdvancedVisibility(advanced: Boolean, count: Int): List<Boolean> {
    val visible = remember { mutableStateListOf(*Array(count) { false }) }
    LaunchedEffect(advanced) {
        if (advanced) {
            // Cumulative delay == the index * STAGGER_STEP_MS the per-card version used.
            repeat(count) { i -> delay(STAGGER_STEP_MS); visible[i] = true }
        } else {
            // Immediate, never staggered -- see STAGGER_STEP_MS' own doc on why hiding in
            // sequence reads as a bug where revealing in sequence reads as deliberate.
            repeat(count) { i -> visible[i] = false }
        }
    }
    return visible
}

/**
 * An [AnimatedVisibility] state that starts hidden and animates itself in on first composition.
 *
 * Needed by anything only COMPOSED once it should already be visible (see
 * [rememberAdvancedVisibility]): there is no false -> true flip left inside composition for a
 * plain `visible =` boolean to animate from, so it would otherwise just appear.
 */
@Composable
internal fun rememberAppearedState(): MutableTransitionState<Boolean> =
    remember { MutableTransitionState(false) }.apply { targetState = true }

@Composable
internal fun staggeredAdvancedVisible(advanced: Boolean, index: Int): Boolean {
    var visible by remember { mutableStateOf(advanced) }
    LaunchedEffect(advanced) {
        if (advanced) {
            delay(index * STAGGER_STEP_MS)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}

/**
 * The tonal icon badge + bold title + colour-coded status line used at the top
 * of several SettingsCard bodies (Accounts, AI, Backup & sync, Notifications,
 * Security, Theme) to give an at-a-glance read of the card's current state
 * before it's opened any further.
 *
 * [icon], [tint] and [status] all animate on change -- the same transition
 * PebbleShell's own header summary uses for its `summary` text -- rather than
 * snapping instantly the moment the setting behind them flips. Every other
 * piece of state change in Settings springs or fades; a status line that
 * just jump-cut to "Off" when everything around it animates was the one
 * inconsistency left.
 */
@Composable
internal fun StatusHeaderRow(icon: ImageVector, tint: Color, title: String, status: String) {
    val animTint by androidx.compose.animation.animateColorAsState(tint, label = "statusHeaderTint")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(animTint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(targetState = icon, label = "statusHeaderIcon") { i ->
                Icon(i, contentDescription = null, tint = animTint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AnimatedContent(
                targetState = status,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                },
                label = "statusHeaderText",
            ) { s ->
                Text(s, style = MaterialTheme.typography.labelMedium, color = animTint, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * `SettingsScreen`'s own (non-hoisted) badge state, bundled so it can be
 * built inside an `if (hoisted == null)` branch as one value -- see
 * `VehicleDetailContent`'s identical `LocalNamePillState` for the full
 * reasoning.
 */
internal class LocalSettingsPillState(
    val flight: HeroTitleFlight,
)

/**
 * Settings' own in-content header: title + a short context line, using
 * headlineSmall/Bold for the name (bodySmall/onSurfaceVariant subtitle) --
 * matching the base [PebbleShell] scales its own hero title from (see
 * TitleFlightOverlay's content Text sites in Screens.kt for the same fix),
 * not [CarHeaderRow][com.bloo.bluelink.ui]'s own titleLarge name text.
 *
 * Reports its own real, measured position via [LocalHeroTitleFlight] (the
 * same mechanism [HeroHeader]'s car-page title uses) whenever a flight
 * controller is present, and stays permanently INVISIBLE while it does --
 * see `TitleFlightOverlay`'s own doc (Screens.kt) for why: this slot's only
 * job is to report where the real, single, visible Text should sit when
 * undocked.
 */
@Composable
internal fun SettingsHeaderRow(state: UiState, compact: Boolean = false) {
    val titleFlight = LocalHeroTitleFlight.current
    // Same fix as HeroHeader's own identical block (Screens.kt) -- force a
    // fresh report the instant the ambient flight identity changes (this
    // slot becoming/ceasing to be the hoisted one), instead of waiting on
    // an incidental relayout that might not come. Uses onSettled, not
    // onPositioned, so it doesn't inherit hysteresis left over from
    // whichever DIFFERENT page (a car) was settled on the shared flight
    // before this one -- see HeroTitleFlight.onSettled's own doc.
    //
    // Runs SYNCHRONOUSLY, during composition -- NOT inside a LaunchedEffect.
    // This WAS a LaunchedEffect(titleFlight) until an audit caught that it
    // never actually got the fix its own comment claimed: a coroutine only
    // starts running after the composition pass that adopts the new flight
    // has already committed, which is strictly AFTER TitleFlightOverlay's
    // own synchronous `val docked by flight.docked` read (and its cold-mount
    // snapTo) has already consumed whatever STALE state the newly-adopted
    // flight was left holding by whichever car page drove it last -- one
    // whole recomposition too late, reading as a visible pop/flash right on
    // the Settings-slot hand-off. See HeroHeader's identical
    // `lastCorrectedFlight` latch (Screens.kt) for the proven fix this
    // mirrors.
    val lastCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lastCorrectedFlight by remember { mutableStateOf<HeroTitleFlight?>(null) }
    if (lastCorrectedFlight !== titleFlight) {
        lastCoords.value?.let { titleFlight?.onSettled(it.positionInRoot()) }
        lastCorrectedFlight = titleFlight
    }
    // Entrance animation: the page header slides up and fades in (same motion
    // the empty screen and hero use) instead of appearing with no motion --
    // "the settings header is not animated" -- while the invisible title
    // anchor inside still reports its real position every frame, so the
    // floating overlay tracks the animated header perfectly.
    val headerAppear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { headerAppear.animateTo(1f, tween(400)) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                alpha = headerAppear.value
                translationY = (1f - headerAppear.value) * 12.dp.toPx()
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Tonal icon badge, the same visual language every SettingsCard
            // header uses -- the page top previously stood bare next to cards
            // that all carry this badge, reading as a leftover plain heading.
            Box(
                Modifier
                    .size(if (compact) 36.dp else 46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column {
            Text(
                "Settings",
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = if (titleFlight != null) {
                    Modifier
                        .onGloballyPositioned {
                            lastCoords.value = it
                            titleFlight.onPositioned(it.positionInRoot())
                        }
                        .alpha(0f)
                        // Position anchor only -- see TitleFlightOverlay's matching
                        // measuring-copy comment (Screens.kt) for why this can't stay
                        // in the accessibility tree.
                        .clearAndSetSemantics {}
                } else {
                    Modifier
                },
            )
            val carCount = state.vehicles.size
            val modeLabel = if (state.settingsMode == "advanced") "Advanced" else "Simple"
            Text(
                "$carCount car${if (carCount == 1) "" else "s"} · $modeLabel mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
            // Simple/Advanced lives in the header now (it was a bare row of
            // segmented options further down, easily lost) -- a small tonal
            // chip carrying the current mode, standard surface treatment.
            // CRITICAL: This must align vertically with the floating back button
            // and floating Settings name pill. Removed the top padding that was
            // throwing off vertical alignment; the pill now centers naturally
            // with Alignment.CenterVertically in the Row.
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        if (state.settingsMode == "advanced") "Advanced" else "Simple",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
