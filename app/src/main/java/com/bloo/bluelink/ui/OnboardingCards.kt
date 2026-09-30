package com.bloo.bluelink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** What a welcome card's header says: the same icon / title / summary a pebble carries. */
internal data class OnboardingCardSpec(val icon: ImageVector, val title: String, val summary: String)

internal fun onboardingCardSpec(kind: OnboardingStepKind, carName: String?): OnboardingCardSpec = when (kind) {
    OnboardingStepKind.INTRO -> OnboardingCardSpec(
        Icons.Filled.WavingHand, "Welcome to Bloo",
        "Lock, climate, charge and more for your Hyundai, Genesis or Kia. Swipe to set it up.",
    )
    OnboardingStepKind.SETUP -> OnboardingCardSpec(
        Icons.Filled.Tune, "Quick setup",
        "Two quick things and you're in: let Bloo reach you, and lock the app.",
    )
    OnboardingStepKind.CAR -> OnboardingCardSpec(
        Icons.Filled.DirectionsCar, carName ?: "Your car",
        "Set powertrain and features once so the right controls appear.",
    )
    OnboardingStepKind.CRASH_COURSE -> OnboardingCardSpec(
        Icons.Filled.TouchApp, "You're all set",
        "A few things that make Bloo quick to use.",
    )
    OnboardingStepKind.FEATURES -> OnboardingCardSpec(
        Icons.Filled.AutoAwesome, "More Bloo can do",
        "Worth knowing about, whenever you're ready for them.",
    )
}

/** One dot per card; the current one stretches into a pill, so the deck's length and your place in it read at a glance. */
@Composable
internal fun OnboardingDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, label = "dotColor")
            androidx.compose.foundation.layout.Box(
                Modifier.width(width).height(8.dp).clip(CircleShape).background(color),
            )
        }
    }
}
