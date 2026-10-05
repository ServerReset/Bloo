package com.bloo.bluelink.ui

/** Settings' screen-header cluster: the mode-stagger helpers, the StatusHeaderRow badge and the SettingsHeaderRow title row. */

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bloo.bluelink.data.collapsedSections
import com.bloo.bluelink.data.setSeamlessInstallShizuku

/**
 * Delays an advanced-only card's entrance by `index * STAGGER_STEP_MS` once [advanced] flips
 * true, so Advanced mode cascades card by card instead of every card overshooting on one frame.
 *
 * The flip back to Simple is immediate: staggering a hide leaves items visible and idle, then
 * vanishing abruptly, which reads as broken.
 */
internal const val STAGGER_STEP_MS = 45L

/** How many advanced-only cards Settings staggers in as whole grid items. Nested advanced blocks
 *  drive themselves through [staggeredAdvancedVisible] with their own index and are not counted. */
internal const val ADVANCED_CARD_COUNT = 2

/**
 * The same staggered reveal as [staggeredAdvancedVisible] for ALL advanced cards, hoisted out of
 * the lazy list's item content into the screen's composition.
 *
 * Returned as a plain list so the screen can skip emitting an item entirely; a hidden item
 * would still hold a slot and its list spacing open (a phantom gap).
 */
@Composable
internal fun rememberAdvancedVisibility(advanced: Boolean, count: Int): List<Boolean> {
    val visible = remember { mutableStateListOf(*Array(count) { false }) }
    LaunchedEffect(advanced) {
        if (advanced) {
            // Cumulative delay equals the per-card index * STAGGER_STEP_MS.
            repeat(count) { i -> delay(STAGGER_STEP_MS); visible[i] = true }
        } else {
            // Immediate, never staggered (see STAGGER_STEP_MS).
            repeat(count) { i -> visible[i] = false }
        }
    }
    return visible
}

/**
 * Drives one advanced-only grid item's [AnimatedVisibility] and decides whether the caller's
 * `if (...) item { ... }` still emits that item.
 *
 * The returned transition is the [AnimatedVisibility]'s `visibleState`, so enter/exit play in both
 * directions. `transition.targetState || !transition.isIdle` keeps the item mounted until an
 * exit finishes; a plain boolean gate would tear it out on the next frame with no collapse.
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
 * An [AnimatedVisibility] state that starts hidden and animates in on first composition.
 *
 * For anything only composed once it should be visible (see [rememberAdvancedVisibility]), where
 * a plain `visible =` boolean has no false-to-true flip to animate.
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
 * The tonal icon badge, bold title and colour-coded status line at the top of several
 * SettingsCard bodies, giving an at-a-glance read of the card's state.
 *
 * [icon], [tint] and [status] animate on change, matching PebbleShell's header summary.
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
        Spacer(Modifier.width(GapGroup))
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
 * Settings' page-top hero card: the app's identity (name, version/build, update status) in the
 * same hero language as the garage's photo hero.
 *
 * Also the one home for updates. Collapsed it shows identity, car count, update chip and the big
 * build number; expanded (persisted via [AppViewModel.togglePebble] under the "Updates" key) it
 * reveals the Check/GitHub row, the Shizuku toggle and, when available, the download/install flow.
 */
@Composable
internal fun SettingsHeroCard(state: UiState, vm: AppViewModel) {
    val number = vm.currentBuildNumber
    val label = com.bloo.bluelink.data.buildLabel(number, com.bloo.bluelink.BuildConfig.BUILD_BRANCH)
    val carCount = state.vehicles.size
    // Same collapse store every SettingsCard persists through, under the "Updates" key.
    val collapsed by vm.collapsedSections.collectAsStateWithLifecycle()
    val expanded = "$SETTINGS_CARD_VIN:Updates" !in collapsed
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    UpdateBadgedCard(visible = state.updateAvailable != null, modifier = Modifier.fillMaxWidth()) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp)) {
            // The chip's compactness is handled by MorphButtonLabel.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    AppIcons.Settings,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    size = 48.dp,
                    iconSize = 24.dp,
                )
                Spacer(Modifier.width(GapGroup))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Bloo",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    AnimatedText(
                        if (carCount == 0) "No vehicles yet" else "$carCount car${if (carCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MorphExpandButton(
                    expanded = expanded,
                    onToggle = { vm.togglePebble(SettingsPseudoVehicle, "Updates") },
                )
            }
            Spacer(Modifier.height(16.dp))
            // The build number is the app's real version (versionName stays "0.1"), so it carries
            // the hero stat like a car's charge % or range, with the full label as caption.
            val headline = state.updateAvailable?.takeIf { !state.updateTileDismissed }
            if (headline != null) {
                // Update waiting: the version jump IS the headline (2463 to 2464), not a second line below it.
                UpdateDeltaHero(vm.currentBuildNumber, headline.run.runNumber)
            } else Row(verticalAlignment = Alignment.Bottom) {
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
                    Spacer(Modifier.height(GapGroup))
                    // Both update sources share one row: the in-app checker (primary) and the GitHub
                    // Releases page (works when the checker says up-to-date or GitHub's API is flaky).
                    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = GapRow) {
                        SafeMorphTextButton(
                            "Check",
                            onClick = { vm.checkForUpdateManually() },
                            enabled = !state.updateChecking,
                            icon = Icons.Filled.Refresh,
                            pending = state.updateChecking,
                            emphasis = ButtonEmphasis.Primary,
                        )
                        SafeMorphTextButton(
                            "GitHub",
                            onClick = {
                                context.tryStart(
                                    Intent(Intent.ACTION_VIEW, com.bloo.bluelink.data.UpdateApi.RELEASES_URL.toUri()),
                                )
                            },
                        )
                    }
                    // The Shizuku row is gated on Shizuku being present; the card and update controls are not.
                    if (state.shizukuAvailable) {
                        Spacer(Modifier.height(GapHairline))
                        ToggleRow("Install seamlessly (Shizuku)", appearance.seamlessInstallShizuku) {
                            vm.setSeamlessInstallShizuku(it)
                        }
                    }
                    // The full download-install flow, driven by the same state machine as the update
                    // pebble and rendered through the shared UpdateStatusLine.
                    val updateInfo = state.updateAvailable
                    PopVisible(visible = updateInfo != null && !state.updateTileDismissed, sizeAnimated = true) {
                        if (updateInfo != null) {
                            val seamless = appearance.seamlessInstallShizuku && state.shizukuAvailable
                            val act = updateAction(state, updateInfo, seamless)
                            val newLabel = com.bloo.bluelink.data.buildLabel(updateInfo.run.runNumber)
                            val deltaLabel = if (vm.currentBuildNumber > 0) {
                                "${com.bloo.bluelink.data.buildLabel(vm.currentBuildNumber)} → $newLabel"
                            } else newLabel
                            Spacer(Modifier.height(GapGroup))
                            // One tinted panel, no second outline: the version jump is already the big number above.
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(StandardShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(GapRow),
                            ) {
                                Text(
                                    "Update available",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                // The status line carries the download's own progress bar.
                                UpdateStatusLine(deltaLabel, seamless, state, vm, showDelta = false)
                                // Both actions full width, stacked: side by side the download button collapsed to an icon.
                                MorphButton(
                                    onClick = { runUpdateAction(state, vm, updateInfo, context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    active = act.ready,
                                    activeContainerColor = ChargeGreen,
                                    activeContentColor = Color.White,
                                    enabled = !state.updateInstalling && !state.updateDownloading,
                                    expressive = true,
                                ) {
                                    MorphButtonLabel(act.icon, act.label, pending = false)
                                }
                                // Shared dismissal row, giving the same undo window as the app tile.
                                UpdateDismissRow(state, vm)
                                UpdateReleaseNotes(updateInfo, collapsedLines = 3)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
