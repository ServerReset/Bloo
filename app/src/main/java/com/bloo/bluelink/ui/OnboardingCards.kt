package com.bloo.bluelink.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
    OnboardingStepKind.ALERTS -> OnboardingCardSpec(
        Icons.Filled.Notifications, "What to tell you",
        "Pick the alerts you want. Change them any time in Settings.",
    )
    OnboardingStepKind.WATCH -> OnboardingCardSpec(
        Icons.Filled.Watch, "Your watch",
        "Quick actions, a Tile and notifications on your wrist.",
    )
    OnboardingStepKind.CAR_POWERTRAIN -> OnboardingCardSpec(
        Icons.Filled.DirectionsCar, if (newCar) "Meet ${carName ?: "your new car"}" else "${carName ?: "Your car"}: what powers it",
        if (newCar) "A new car just joined your garage. First: what powers it?" else "Gas, hybrid, plug-in or electric.",
    )
    OnboardingStepKind.CAR_PLATFORM -> OnboardingCardSpec(
        Icons.Filled.Memory, "${carName ?: "Your car"}: head unit",
        "Which generation of infotainment it has.",
    )
    OnboardingStepKind.CAR_CLIMATE -> OnboardingCardSpec(
        Icons.Filled.AcUnit, "${carName ?: "Your car"}: seats and climate",
        "Heated and ventilated seats, heated wheel.",
    )
    OnboardingStepKind.TIPS -> OnboardingCardSpec(
        Icons.Filled.TouchApp, "Getting around",
        "A few things that make Bloo quick to use.",
    )
    OnboardingStepKind.FEATURES -> OnboardingCardSpec(
        Icons.Filled.AutoAwesome, "More Bloo can do",
        "Worth knowing about, whenever you're ready for them.",
    )
}

/** The accent a card glows with, so the deck changes colour as you move through it. */
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
 * The deck's progress at the top: a glowing glass track whose fill springs to the current card,
 * with a bright head and a plain "3 / 14" readout beside it. Replaces the old dot row.
 */
@Composable
internal fun OnboardingProgress(count: Int, current: Int, accent: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val frac by animateFloatAsState(
        targetValue = if (count <= 1) 1f else current.toFloat() / (count - 1).toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "onboardingProgress",
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GapGroup)) {
        Box(
            Modifier
                .weight(1f)
                .height(9.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest.copy(alpha = 0.55f))
                .drawBehind {
                    val w = (size.width * frac.coerceIn(0f, 1f)).coerceAtLeast(size.height)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.55f), accent),
                        ),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                    // The glowing head rides the tip of the fill.
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = size.height * 0.42f,
                        center = Offset(w - size.height / 2f, size.height / 2f),
                    )
                },
        )
        Text(
            "${current + 1} / $count",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * The glyph at the head of a card: a frosted disc inside a slowly spinning conic ring, floating over
 * a pulsing glow of the card's accent colour. The disc is tappable (a little burst of colour and a
 * buzz), and springs up when its card becomes the current one.
 */
@Composable
internal fun OnboardingHero(
    icon: ImageVector,
    accent: Color,
    current: Boolean,
    onTap: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // Only the card in view breathes, spins and floats; the neighbours the pager keeps ready stay
    // still, so a deck of fourteen runs one animation, not fourteen. All read inside the draw and
    // layer lambdas below, so a frame of the animation redraws the glyph without recomposing it.
    val glow: State<Float>
    val bob: State<Float>
    val spin: State<Float>
    if (current) {
        val loop = rememberInfiniteTransition(label = "heroLoop")
        glow = loop.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
        bob = loop.animateFloat(-6f, 6f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
        spin = loop.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "spin")
    } else {
        glow = remember { mutableFloatStateOf(0.75f) }
        bob = remember { mutableFloatStateOf(0f) }
        spin = remember { mutableFloatStateOf(0f) }
    }
    val pop by animateFloatAsState(
        if (current) 1f else 0.8f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "heroPop",
    )
    Box(
        Modifier
            .size(136.dp)
            .graphicsLayer {
                translationY = bob.value
                scaleX = pop; scaleY = pop
            },
        contentAlignment = Alignment.Center,
    ) {
        // Pulsing glow of the accent colour.
        Box(
            Modifier
                .size(136.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.60f * glow.value), accent.copy(alpha = 0.10f * glow.value), Color.Transparent),
                            radius = size.minDimension * 0.5f,
                        ),
                        radius = size.minDimension * 0.5f,
                    )
                },
        )
        // The spinning conic ring with three orbiting sparks riding it.
        Box(
            Modifier
                .size(104.dp)
                .graphicsLayer { rotationZ = spin.value }
                .drawBehind {
                    drawCircle(
                        Brush.sweepGradient(
                            listOf(
                                accent.copy(alpha = 0f),
                                accent,
                                accent.copy(alpha = 0f),
                                accent,
                                accent.copy(alpha = 0f),
                                accent,
                                accent.copy(alpha = 0f),
                            ),
                        ),
                        style = Stroke(width = 4.dp.toPx()),
                    )
                    val r = size.minDimension / 2f
                    listOf(0f, 120f, 240f).forEach { deg ->
                        val rad = Math.toRadians(deg.toDouble())
                        drawCircle(
                            color = Color.White,
                            radius = 3.5.dp.toPx(),
                            center = Offset(center.x + kotlin.math.cos(rad).toFloat() * r, center.y + kotlin.math.sin(rad).toFloat() * r),
                        )
                    }
                },
        )
        // The frosted disc with the glyph, and a specular glint along its top edge.
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerLow)),
                    CircleShape,
                )
                .clickable(onClick = onTap)
                .drawBehind {
                    drawCircle(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)),
                        style = Stroke(width = 1.8.dp.toPx()),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * A gentle, animated "swipe" hint under the button bar, shown on the opening card so the deck's
 * gesture is discoverable without a wall of text. The little arrow nudges side to side.
 */
@Composable
internal fun OnboardingSwipeHint() {
    val scheme = MaterialTheme.colorScheme
    val nudge by rememberInfiniteTransition(label = "swipeHint").animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
        label = "nudge",
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.SwapHoriz,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp).graphicsLayer { translationX = nudge },
        )
        Spacer(Modifier.width(GapHairline))
        Text("Swipe to continue", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

/**
 * One card of the deck: frosted glass over the blurred aurora, a glowing hero glyph, a big title and
 * its summary, then the card's own content.
 */
@Composable
internal fun OnboardingGlassCard(
    spec: OnboardingCardSpec,
    accent: Color,
    current: Boolean,
    onHeroTap: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth()) {
        // A soft accent halo behind the card, so it glows with the page's colour and reads as a lit
        // pane rather than a flat panel.
        Box(
            Modifier.matchParentSize().drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.05f), Color.Transparent),
                    ),
                    topLeft = Offset(-14.dp.toPx(), -14.dp.toPx()),
                    size = Size(size.width + 28.dp.toPx(), size.height + 28.dp.toPx()),
                    cornerRadius = CornerRadius(48.dp.toPx()),
                )
            },
        )
        Column(
            Modifier
                .fillMaxWidth()
                // Rim only (no shadow): the cards scale as they settle, and a baked shadow would clip
                // under that transform.
                .glassRim(ExtraLargeShape)
                // Cheap-blur glass: the aurora behind is blurred, tinted and sheened, exactly like a
                // pebble. Falls back to a readable tint when the blur cannot run.
                .glassCardFill(ExtraLargeShape, scheme.surfaceContainerHigh)
                .padding(GapBlock),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            OnboardingHero(spec.icon, accent, current, onTap = onHeroTap)
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
