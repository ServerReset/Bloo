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
 * staggeredAdvancedVisible helper, the tonal StatusHeaderRow badge, and the floating
 * SettingsHeaderRow title row.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 *  Three, not eight: three further advanced blocks are nested INSIDE other cards and drive
 *  themselves through [staggeredAdvancedVisible], which takes its own index and never touches
 *  this list. The count only ever covers the cards this screen gates as items. */
internal const val ADVANCED_CARD_COUNT = 3

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
 * Drives one advanced-only grid item's [AnimatedVisibility] AND decides whether the caller's
 * `if (...) item { ... }` should still emit that item at all -- the two used to be the same
 * raw boolean ([rememberAdvancedVisibility]'s own per-index flag), gating the grid `item {}`
 * directly while an inner `AnimatedVisibility(visibleState = rememberAppearedState(), ...)`
 * sat inside it purely for its enter spring.
 *
 * That was the bug behind "expanding/collapsing is entirely broken, the animations don't
 * work": [rememberAppearedState] only ever goes false -> true, once, and nothing inside the
 * card ever set it back to false -- its `exit` spec was dead code. The actual disappearance was
 * the OUTER `if` flipping false and tearing the whole item out of the grid on the very next
 * recomposition, same frame, with no transition able to run at all. Advanced -> simple didn't
 * play a broken collapse; it played no collapse, which reads exactly as "the animation doesn't
 * work" -- the grid item, and everything on it, was just gone.
 *
 * The transition returned here is both the [AnimatedVisibility]'s own `visibleState` (so its
 * enter/exit specs are what actually plays, in both directions) and the item-emission gate:
 * `transition.targetState || !transition.isIdle` stays true for as long as an exit is still
 * mid-flight, so the grid keeps the item mounted until the shrink/fade genuinely finishes, and
 * only then lets it fall away -- preserving the "no phantom gap" reason the item was skipped
 * entirely in the first place ([rememberAdvancedVisibility]'s own doc), just no longer skipping
 * the one animation the whole flip is supposed to show.
 */
@Composable
internal fun rememberGridItemVisibility(visible: Boolean): MutableTransitionState<Boolean> {
    val transition = remember { MutableTransitionState(visible) }
    LaunchedEffect(visible) {
        transition.targetState = visible
    }
    return transition
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
 * Settings' page-top hero card: the app's own identity (name, version/build,
 * update status) in the same big-number hero language the garage's photo hero
 * uses for %/range. It plays the role a car page's hero photo plays, so the
 * Settings page reads as one standard page in the pager -- hero up top, cards
 * below -- instead of a header bolted onto a grid.
 */
@Composable
internal fun SettingsHeroCard(state: UiState, vm: AppViewModel, compact: Boolean) {
    val number = vm.currentBuildNumber
    val label = com.bloo.bluelink.data.buildLabel(number, com.bloo.bluelink.BuildConfig.BUILD_BRANCH)
    val carCount = state.vehicles.size
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(if (compact) 40.dp else 48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Bloo",
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (carCount == 0) "No vehicles yet" else "$carCount car${if (carCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                UpdateStatusChip(state)
            }
            Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
            // The build number is the app's real version here (versionName stays
            // "0.1" on purpose), so it carries the hero stat the same way a car's
            // charge % or range does -- with the full label as its caption.
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(
                    text = if (number > 0) "$number" else "dev",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
