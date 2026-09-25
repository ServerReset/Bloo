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

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * hoisted OUT of the lazy list's item content and into the screen's own composition.
 *
 * That hoist is the point. Read from inside `item { AnimatedVisibility(visible = ...) }`, the
 * visibility can only ever hide a card's CONTENT -- the `item {}` itself still occupies a slot
 * in the LazyColumn, and the list still applies its own item spacing around
 * that now zero-height slot. Every advanced-only card left a phantom gap behind in simple mode,
 * which is what "bad spacing when pebbles are hidden" is: not one wrong padding, but eight
 * invisible items each holding a list gap open. Returned as a plain list the list's DSL can
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
            TitleSmallText(title)
            AnimatedContent(
                targetState = status,
                transitionSpec = { expandContentTransform() },
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
 *
 * Also THE one home for updates now -- there used to be a separate "Updates"
 * SettingsCard further down the grid, carrying its own (duplicate) copy of the
 * build-number stat plus the actual check/download/install controls. Reported
 * directly as one card too many: this hero already shows the build number and
 * an at-a-glance update chip, so a whole second card just to act on it read as
 * the same information twice with the action bolted onto the wrong copy.
 * Collapsed, this card is unchanged -- identity, car count, update chip, big
 * build number. Expanded (the same chevron/[AppViewModel.togglePebble]
 * persistence every other card and every car pebble uses, under the "Updates"
 * key so a prior collapse/expand choice carries over), it reveals the
 * Check/GitHub row, the Shizuku toggle, and -- only when one is actually
 * available -- the full download/install flow with release notes, exactly
 * what the old card showed once opened.
 */
@Composable
internal fun SettingsHeroCard(state: UiState, vm: AppViewModel, compact: Boolean) {
    val number = vm.currentBuildNumber
    val label = com.bloo.bluelink.data.buildLabel(number, com.bloo.bluelink.BuildConfig.BUILD_BRANCH)
    val carCount = state.vehicles.size
    // Same collapse store every SettingsCard persists through, under the "Updates" key --
    // this card replaces that one entirely, so it inherits whatever expand/collapse choice
    // was already saved for it rather than starting every install back at one default.
    val collapsed by vm.collapsedSections.collectAsStateWithLifecycle()
    val expanded = "$SETTINGS_CARD_VIN:Updates" !in collapsed
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    UpdateBadgedCard(visible = state.updateAvailable != null, modifier = Modifier.fillMaxWidth()) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    AppIcons.Settings,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    size = if (compact) 40.dp else 48.dp,
                    iconSize = 24.dp,
                )
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
                Spacer(Modifier.width(4.dp))
                MorphExpandButton(
                    expanded = expanded,
                    onToggle = { vm.togglePebble(SettingsPseudoVehicle, "Updates") },
                )
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
                LabelText(label, modifier = Modifier.padding(bottom = 6.dp))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandEnterSized(),
                exit = expandExitSized(fade = false),
            ) {
                Column {
                    Spacer(Modifier.height(SettingsGapGroup))
                    // Both update sources share one row instead of two stacked full-width
                    // pills: the in-app checker (primary) and the GitHub Releases page (a
                    // second source that still works when the checker says up-to-date or
                    // GitHub's API is flaky).
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 8.dp) {
                        val checkSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = checkSource,
                            enabled = !state.updateChecking,
                        ) {
                            MorphButton(
                                onClick = { vm.checkForUpdateManually() },
                                interactionSource = checkSource,
                                enabled = !state.updateChecking,
                                // Explicit primary colours, not active=true -- see the
                                // original Updates card's own history for why: active
                                // pins the button's morphed square corner permanently,
                                // which is wrong for "Check" (it never has an "on" state
                                // to stay morphed for).
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                MorphButtonLabel(
                                    icon = Icons.Filled.Refresh,
                                    label = "Check",
                                    pending = state.updateChecking,
                                )
                            }
                        }
                        val githubSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = githubSource,
                            enabled = true,
                        ) {
                            MorphTextButton(
                                "GitHub",
                                interactionSource = githubSource,
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, com.bloo.bluelink.data.UpdateApi.RELEASES_URL.toUri())
                                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                    // Shizuku silent-install: the ROW is gated on Shizuku being present, but
                    // the card is not -- so the update controls above always show.
                    if (state.shizukuAvailable) {
                        Spacer(Modifier.height(SettingsGapHairline))
                        ToggleRow("Install seamlessly (Shizuku)", appearance.seamlessInstallShizuku) {
                            vm.setSeamlessInstallShizuku(it)
                        }
                    }
                    // The full download -> install flow, right here in the card. Drives the
                    // same state machine the update pebble does -- download, progress,
                    // install -- rendered through the shared UpdateStatusLine so neither
                    // surface can drift.
                    val updateInfo = state.updateAvailable
                    // PopVisible, not a bare `if`: this used to just appear the instant a
                    // manual "Check" landed on a hit, which was jarring right in the middle
                    // of an already-open card -- every other piece of content that arrives
                    // into an open card (a weather stripe, a status line) pops in, and this
                    // was the one exception. sizeAnimated = true because the card ABOVE this
                    // (a car's own detail page, or another Settings card below it in the
                    // grid) needs to make room for the height this adds, not just fade/scale
                    // in place. Column, not a bare PopVisible body: PopVisible measures its
                    // content as ONE child, and this emits a Spacer + a Surface as two
                    // siblings -- see WeatherDetail's own history for exactly this bug
                    // (AnimatedVisibility stacks multiple un-grouped children on top of each
                    // other instead of one after another).
                    PopVisible(visible = updateInfo != null && !state.updateTileDismissed, sizeAnimated = true) {
                    if (updateInfo != null) {
                    Column {
                        Spacer(Modifier.height(SettingsGapGroup))
                        // Its own outlined container, separate from the check/GitHub/Shizuku
                        // controls above -- marks where "current state" ends and "here's
                        // what's new" begins. Outlined, not filled: UpdateReleaseNotes below
                        // already fills with surfaceContainerHighest, and nesting two
                        // same-tone fills inside each other would read as flat padding
                        // rather than a real boundary.
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = StandardShape,
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                        Column(Modifier.padding(14.dp)) {
                        Text(
                            "Update available",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val newLabel = com.bloo.bluelink.data.buildLabel(updateInfo.run.runNumber)
                        val deltaLabel = if (vm.currentBuildNumber > 0) {
                            "${com.bloo.bluelink.data.buildLabel(vm.currentBuildNumber)} → $newLabel"
                        } else newLabel
                        val seamless = appearance.seamlessInstallShizuku && state.shizukuAvailable
                        Spacer(Modifier.height(SettingsGapHairline))
                        // Same tonal Surface the update PEBBLE wraps this exact shared
                        // composable in (UpdateTile.kt) -- same shared composable, same
                        // chrome around it, in both places.
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = SmallShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                UpdateStatusLine(
                                    deltaLabel, seamless, state, vm,
                                    // The heading two rows up already says "Update
                                    // available", not the delta, so unlike the pebble
                                    // this surface always has room for it.
                                    showDelta = true,
                                )
                            }
                        }
                        Spacer(Modifier.height(SettingsGapGroup))
                        // Label, glyph and branch all come from the shared updateAction /
                        // runUpdateAction, so this button and the pebble's header action
                        // cannot disagree about what the update flow is currently offering.
                        val act = updateAction(state, updateInfo, seamless)
                        val updateSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = updateSource,
                            enabled = !state.updateInstalling && !state.updateDownloading,
                        ) {
                            MorphButton(
                                onClick = { runUpdateAction(state, vm, updateInfo, context) },
                                active = act.ready,
                                activeContainerColor = ChargeGreen,
                                activeContentColor = Color.White,
                                enabled = !state.updateInstalling && !state.updateDownloading,
                                interactionSource = updateSource,
                            ) {
                                MorphButtonLabel(act.icon, act.label, pending = false)
                            }
                        }
                        // Shared with the update pebble -- see UpdateReleaseNotes. The two
                        // used to keep a copy each, identical but for the excerpt length and
                        // one of them forgetting FLAG_ACTIVITY_NEW_TASK on the intent.
                        Spacer(Modifier.height(SettingsGapRow))
                        UpdateReleaseNotes(updateInfo, maxLines = 3)
                        Spacer(Modifier.height(SettingsGapRow))
                        Row(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            val notNowSource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = notNowSource,
                                enabled = !state.updateDownloading && !state.updateInstalling,
                            ) {
                                MorphTextButton(
                                    "Not now",
                                    interactionSource = notNowSource,
                                    onClick = vm::dismissUpdate,
                                    enabled = !state.updateDownloading && !state.updateInstalling,
                                )
                            }
                        }
                        }
                        }
                    }
                    }
                    }
                }
            }
        }
    }
    }
}
