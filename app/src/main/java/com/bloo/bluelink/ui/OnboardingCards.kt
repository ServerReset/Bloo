package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapPage
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.content
import androidx.compose.foundation.shape.RoundedCornerShape

/** What an onboarding card's header says: the same icon / title / summary a pebble carries. */
internal data class OnboardingCardSpec(val icon: ImageVector, val title: String, val summary: String)

internal fun onboardingCardSpec(kind: OnboardingStepKind, carName: String?, newCar: Boolean = false): OnboardingCardSpec = when (kind) {
    OnboardingStepKind.WELCOME -> OnboardingCardSpec(
        Icons.Filled.WavingHand, "Welcome to Bloo",
        "Lock, climate, charge and more for your Hyundai, Genesis or Kia.",
    )
    OnboardingStepKind.RESTORE -> OnboardingCardSpec(
        Icons.Filled.CloudSync, "Restore a setup",
        "Bring in everything from another device, or skip it.",
    )
    OnboardingStepKind.SETUP -> OnboardingCardSpec(
        Icons.Filled.Tune, "Quick setup",
        "Notifications, and a lock on the app.",
    )
    OnboardingStepKind.LOOK -> OnboardingCardSpec(
        Icons.Filled.Palette, "Look and feel",
        "Theme, and the units distances use.",
    )
    OnboardingStepKind.ALERTS -> OnboardingCardSpec(
        Icons.Filled.Notifications, "What to tell you",
        "Which alerts reach you.",
    )
    OnboardingStepKind.WATCH -> OnboardingCardSpec(
        Icons.Filled.Watch, "Your watch",
        "Quick actions, a Tile and notifications on your wrist.",
    )
    OnboardingStepKind.CAR_POWERTRAIN -> OnboardingCardSpec(
        // One shape for every car card -- "Car: aspect" -- including the new-car case, which used to
        // be the only card in the deck whose TITLE changed form ("Meet Ioniq 5"). The friendliness
        // lives in the summary instead, where it does not break the pattern the other two follow.
        AppIcons.DirectionsCar, "${carName ?: "Your car"}: what powers it",
        if (newCar) "A new car joined your garage. Gas, hybrid, plug-in or electric?" else "Gas, hybrid, plug-in or electric?",
    )
    OnboardingStepKind.CAR_PLATFORM -> OnboardingCardSpec(
        Icons.Filled.Memory, "${carName ?: "Your car"}: head unit",
        "Which generation of infotainment it has.",
    )
    OnboardingStepKind.CAR_CLIMATE -> OnboardingCardSpec(
        Icons.Filled.AcUnit, "${carName ?: "Your car"}: seats and climate",
        "Which seats heat or cool, and whether the wheel does.",
    )
    OnboardingStepKind.TIPS -> OnboardingCardSpec(
        Icons.Filled.TouchApp, "Getting around",
        "The gestures worth knowing.",
    )
    OnboardingStepKind.FEATURES -> OnboardingCardSpec(
        AppIcons.AutoAwesome, "More Bloo can do",
        "Things Bloo can do that you have not met yet.",
    )
}

/** The accent a step glows with, so the bar changes colour as you move through the deck. */
@Composable
internal fun onboardingAccent(kind: OnboardingStepKind): Color {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        OnboardingStepKind.WELCOME, OnboardingStepKind.SETUP, OnboardingStepKind.FEATURES -> scheme.primary
        OnboardingStepKind.RESTORE, OnboardingStepKind.CAR_POWERTRAIN, OnboardingStepKind.CAR_PLATFORM,
        OnboardingStepKind.CAR_CLIMATE -> scheme.tertiary
        OnboardingStepKind.LOOK, OnboardingStepKind.TIPS, OnboardingStepKind.ALERTS, OnboardingStepKind.WATCH -> scheme.secondary
    }
}

/**
 * A child that pops in shortly after its host, later the further down the list it sits, so a card
 * arrives as a little cascade rather than all at once.
 */
@Composable
internal fun OnboardingStagger(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L + index * 70L)
        visible = true
    }
    PopVisible(visible = visible) { content() }
}

/**
 * One onboarding step as the app's standard card: the same [PebbleShell] the garage and Settings
 * use, pinned open and non-collapsible, with the step's icon, title and summary in the header and
 * the step's own content in the body. No bespoke hero, no collapsing.
 */
@Composable
internal fun OnboardingStepCard(spec: OnboardingCardSpec, content: @Composable () -> Unit) {
    PebbleShell(
        expanded = true,
        onToggle = {},
        icon = spec.icon,
        title = spec.title,
        // No summary here: the shell's summary is a single-line readout, and a step's description is
        // a sentence that must wrap. It leads the body instead.
        forceExpanded = true,
        canToggle = false,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentGap = GapGroup,
    ) {
        RevealMutedText(spec.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/**
 * The onboarding's progress and navigation, together on one liquid-glass bar at the bottom of the
 * screen. A segment per step across the top, then Back/Next, then the reason Next is disabled (if
 * it is). The bar is the only way forward: the deck never swipes.
 */
@Composable
internal fun OnboardingBottomBar(
    current: Int,
    total: Int,
    accent: Color,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String,
    nextIcon: ImageVector,
    nextEnabled: Boolean,
    hint: String?,
    hazeState: HazeState? = null,
) {
    GlassSurface(
        // Match the step cards it sits under (PebbleShell's expanded corner), not the dialog radius.
        shape = RoundedCornerShape(PebbleCornerExpanded),
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = GapPage, vertical = GapGroup),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(GapGroup),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(total.coerceAtLeast(1)) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i <= current) accent
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            ),
                    )
                }
            }
            ActionRow(spacing = GapGroup) {
                onBack?.let { back -> MorphTextButton(text = "Back", onClick = back) }
                MorphActionButton(
                    label = nextLabel,
                    icon = nextIcon,
                    onClick = onNext,
                    enabled = nextEnabled,
                    emphasis = ButtonEmphasis.Confirm,
                )
            }
            hint?.let { BodySmallText(it) }
        }
    }
}
