package com.bloo.bluelink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Palette
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
        "Let Bloo reach you, and lock the app.",
    )
    OnboardingStepKind.LOOK -> OnboardingCardSpec(
        Icons.Filled.Palette, "Look and feel",
        "How Bloo looks and which units it speaks.",
    )
    OnboardingStepKind.CAR -> if (newCar) {
        OnboardingCardSpec(
            Icons.Filled.DirectionsCar, "Meet ${carName ?: "your new car"}",
            "A new car just joined your garage. Tell Bloo what it can do and it's ready to go.",
        )
    } else {
        OnboardingCardSpec(
            Icons.Filled.DirectionsCar, carName ?: "Your car",
            "Set powertrain and features once so the right controls appear.",
        )
    }
    OnboardingStepKind.TIPS -> OnboardingCardSpec(
        Icons.Filled.TouchApp, "Getting around",
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

/** The accent a card glows with, so the deck changes colour as you move through it. */
@Composable
internal fun onboardingAccent(kind: OnboardingStepKind): Color {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        OnboardingStepKind.WELCOME, OnboardingStepKind.SETUP, OnboardingStepKind.FEATURES -> scheme.primary
        OnboardingStepKind.RESTORE, OnboardingStepKind.CAR -> scheme.tertiary
        OnboardingStepKind.LOOK, OnboardingStepKind.TIPS -> scheme.secondary
    }
}

/**
 * The glyph at the head of a card: a frosted disc that floats gently over a pulsing glow of the
 * card's accent colour, and springs up when its card becomes the current one.
 */
@Composable
internal fun OnboardingHero(icon: ImageVector, accent: Color, current: Boolean, onTap: () -> Unit = {}) {
    val pulse = rememberInfiniteTransition(label = "heroPulse")
    val glow by pulse.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
    val bob by pulse.animateFloat(-4f, 4f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
    val pop by animateFloatAsState(
        if (current) 1f else 0.82f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "heroPop",
    )
    // Poke it and it squishes: a small reward for curiosity (see the deck for what five pokes do).
    var squish by remember { androidx.compose.runtime.mutableStateOf(false) }
    val squishScale by animateFloatAsState(
        if (squish) 0.78f else 1f,
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "heroSquish",
        finishedListener = { squish = false },
    )
    Box(
        Modifier
            .size(92.dp)
            .graphicsLayer { translationY = bob; scaleX = pop * squishScale; scaleY = pop * squishScale }
            .clip(CircleShape)
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                squish = true
                onTap()
            }
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * glow), Color.Transparent), radius = size.minDimension * 0.95f),
                    radius = size.minDimension * 0.95f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.22f))
                .drawBehind {
                    drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * One card of the deck: frosted glass over the blurred aurora, a glowing hero glyph, a big title
 * and its summary, then the card's own content. Replaces the pebble chrome here -- these cards
 * are the app's first impression and want to be the most expressive thing in it.
 */
@Composable
internal fun OnboardingGlassCard(
    spec: OnboardingCardSpec,
    accent: Color,
    hazeState: HazeState,
    current: Boolean,
    onHeroTap: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        shape = ExtraLargeShape,
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth(),
        tint = scheme.surface.copy(alpha = if (canBlurBackdrops()) 0.30f else 0.90f),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            OnboardingHero(spec.icon, accent, current, onHeroTap)
            Column(verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                Text(
                    spec.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = scheme.onSurface,
                )
                Text(spec.summary, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(2.dp))
            content()
        }
    }
}
