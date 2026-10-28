package com.bloo.bluelink.ui

/**
 * Settings' screen-header cluster: the mode-stagger helpers, the StatusHeaderRow badge and the
 * SettingsHeaderRow title row.
 */

import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.container
import com.bloo.uicommon.content
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.collapsedSections
import com.bloo.uicommon.IconBadge
import com.bloo.uicommon.IconBadgeContainer
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.SecondaryTextStyle
import kotlinx.coroutines.delay
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.GapPage

/**
 * Delays an advanced-only card's own entrance by `index * STAGGER_STEP_MS` once [advanced] flips
 * true, so switching into Advanced mode cascades card by card instead of every advanced-only
 * section overshooting on the exact same frame -- the same "one shared progress, remapped per item"
 * idea [StaggeredRevealColumn] uses for a pebble's rows, adapted here for a handful of independent
 * [AnimatedVisibility] instances rather than one Layout's worth of children (there's no single
 * shared container to run a Layout-based cascade over: these are whole, separately-composed
 * [SettingsCard]s scattered through one long screen, not rows of one component).
 */
internal const val STAGGER_STEP_MS = 45L

/**
 * How many advanced-only cards Settings staggers in as whole grid items. Nested advanced blocks
 * drive themselves through [staggeredAdvancedVisible] with their own index and are not counted.
 */
internal const val ADVANCED_CARD_COUNT = 2

/**
 * The same staggered reveal as [staggeredAdvancedVisible] for ALL advanced cards, hoisted out of
 * the lazy list's item content into the screen's composition.
 */
@Composable
internal fun rememberAdvancedVisibility(advanced: Boolean, count: Int): List<Boolean> {
    val visible = remember { mutableStateListOf(*Array(count) { false }) }
    LaunchedEffect(advanced) {
        if (advanced) {
            // Cumulative delay equals the per-card index * STAGGER_STEP_MS.
            repeat(count) { i -> delay(STAGGER_STEP_MS); visible[i] = true }
        } else {
            // Immediate, never staggered -- see STAGGER_STEP_MS' own doc on why hiding in sequence
            // reads as a bug where revealing in sequence reads as deliberate.
            repeat(count) { i -> visible[i] = false }
        }
    }
    return visible
}

/**
 * That was the bug behind "expanding/collapsing is entirely broken, the animations don't work":
 * [rememberAppearedState] only ever goes false -> true, once, and nothing inside the card ever set
 * it back to false -- its `exit` spec was dead code.
 */
@Composable
internal fun rememberGridItemVisibility(visible: Boolean): MutableTransitionState<Boolean> {
    val transition = remember { MutableTransitionState(visible) }
    LaunchedEffect(visible) { transition.targetState = visible }
    return transition
}

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
 * The tonal icon badge, bold title and colour-coded status line at the top of several SettingsCard
 * bodies, giving an at-a-glance read of the card's state. [icon], [tint] and [status] animate on
 * change, matching PebbleShell's header summary.
 */
@Composable
internal fun StatusHeaderRow(icon: ImageVector, tint: Color, title: String, status: String) {
    val animTint by androidx.compose.animation.animateColorAsState(tint, label = "statusHeaderTint")
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The standard badge container; only the glyph inside it animates between icons.
        IconBadgeContainer(containerColor = animTint.copy(alpha = 0.15f)) {
            AnimatedContent(targetState = icon, label = "statusHeaderIcon") { i ->
                Icon(i, contentDescription = null, tint = animTint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(GapGroup))
        Column {
            TitleSmallText(title)
            AnimatedSwap(status, label = "statusHeaderText") { s ->
                RevealText(s, style = SecondaryTextStyle(), color = animTint, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Settings' page-top hero card: the app's identity (name, version/build, update status) in the same
 * hero language as the garage's photo hero. Also the one home for updates.
 */
@Composable
internal fun SettingsHeroCard(state: UiState, vm: AppViewModel) {
    val number = vm.currentBuildNumber
    val carCount = state.vehicles.size
    // Same collapse store every SettingsCard persists through, under the "Updates" key.
    val collapsed by vm.collapsedSections.collectAsStateWithLifecycle()
    val expanded = "$SETTINGS_CARD_VIN:Updates" !in collapsed
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    UpdateBadgedCard(visible = state.updateOffered(), modifier = Modifier.fillMaxWidth()) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { heading() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(GapPage)) {
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
                    RevealText("Bloo", style = PrimaryTextStyle(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    RevealMutedText(if (carCount == 0) "No vehicles yet" else "$carCount car${if (carCount == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MorphExpandButton(
                    expanded = expanded,
                    onToggle = { vm.togglePebble(SettingsPseudoVehicle, "Updates") },
                )
            }
            Spacer(Modifier.height(GapSection))
            // The build number is the app's real version (versionName stays "0.1"), so it carries
            // the hero stat like a car's charge % or range, with the full label as caption.
            val headline = state.updateAvailable?.takeIf { !state.updateTileDismissed }
            if (headline != null) {
                // Update waiting: the version jump IS the headline (2463 to 2464), not a second
                // line below it.
                UpdateDeltaHero(vm.currentBuildNumber, headline.run.runNumber)
            } else Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(
                    text = if (number > 0) "$number" else "dev",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(GapGroup))
                SecondaryText(com.bloo.bluelink.BuildConfig.BUILD_BRANCH.takeIf { it.isNotBlank() } ?: "this build", modifier = Modifier.padding(bottom = GapRow))
            }
            AnimatedVisibility(visible = expanded, enter = expandEnterSized(), exit = expandExitSized(fade = false)) {
                Column {
                    Spacer(Modifier.height(GapGroup))
                    // One update body for both tracks: the Play copy and the GitHub build differ in what they offer, not in layout.
                    UpdatePanel(state, vm)
                }
            }
        }
    }
    }
}
