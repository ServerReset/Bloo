package com.bloo.bluelink.ui

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
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch

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

/** A short, uppercase category label shown as a chip above a card's title. */
internal fun onboardingCategory(kind: OnboardingStepKind): String = when (kind) {
    OnboardingStepKind.WELCOME -> "Welcome"
    OnboardingStepKind.RESTORE -> "Restore"
    OnboardingStepKind.SETUP -> "Setup"
    OnboardingStepKind.LOOK -> "Look"
    OnboardingStepKind.ALERTS -> "Alerts"
    OnboardingStepKind.WATCH -> "Watch"
    OnboardingStepKind.CAR_POWERTRAIN -> "Your car"
    OnboardingStepKind.CAR_PLATFORM -> "Your car"
    OnboardingStepKind.CAR_CLIMATE -> "Your car"
    OnboardingStepKind.TIPS -> "Tips"
    OnboardingStepKind.FEATURES -> "Features"
}

/** The accent a card glows with, so the deck changes colour as you move through it. */
@Composable
internal fun onboardingAccent(kind: OnboardingStepKind): Color {
    val scheme = MaterialTheme.colorScheme
    val p = scheme.primary
    val s = scheme.secondary
    val t = scheme.tertiary
    // Mixes of the three theme accents, so consecutive cards read as distinctly different hues
    // rather than three colours on rotation.
    return when (kind) {
        OnboardingStepKind.WELCOME -> p
        OnboardingStepKind.RESTORE -> androidx.compose.ui.graphics.lerp(t, p, 0.35f)
        OnboardingStepKind.SETUP -> s
        OnboardingStepKind.LOOK -> androidx.compose.ui.graphics.lerp(p, s, 0.5f)
        OnboardingStepKind.ALERTS -> t
        OnboardingStepKind.WATCH -> androidx.compose.ui.graphics.lerp(s, t, 0.5f)
        OnboardingStepKind.CAR_POWERTRAIN -> androidx.compose.ui.graphics.lerp(p, t, 0.4f)
        OnboardingStepKind.CAR_PLATFORM -> androidx.compose.ui.graphics.lerp(s, p, 0.4f)
        OnboardingStepKind.CAR_CLIMATE -> androidx.compose.ui.graphics.lerp(t, s, 0.4f)
        OnboardingStepKind.TIPS -> androidx.compose.ui.graphics.lerp(p, s, 0.25f)
        OnboardingStepKind.FEATURES -> androidx.compose.ui.graphics.lerp(p, t, 0.6f)
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
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun OnboardingHero(
    icon: ImageVector,
    accent: Color,
    current: Boolean,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // Only the card in view breathes, spins and floats; the neighbours the pager keeps ready stay
    // still, so a deck of fourteen runs one animation, not fourteen. All read inside the draw and
    // layer lambdas below, so a frame of the animation redraws the glyph without recomposing it.
    val glow: State<Float>
    val bob: State<Float>
    val spin: State<Float>
    val pulse: State<Float>
    if (current) {
        val loop = rememberInfiniteTransition(label = "heroLoop")
        glow = loop.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
        bob = loop.animateFloat(-6f, 6f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
        spin = loop.animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "spin")
        pulse = loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2100, easing = LinearEasing)), label = "pulse")
    } else {
        glow = remember { mutableFloatStateOf(0.75f) }
        bob = remember { mutableFloatStateOf(0f) }
        spin = remember { mutableFloatStateOf(0f) }
        pulse = remember { mutableFloatStateOf(0f) }
    }
    val pop by animateFloatAsState(
        if (current) 1f else 0.8f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "heroPop",
    )
    // A tap squashes the disc and spins the ring up, then springs back.
    val tap = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    val onTapWithFlair = {
        scope.launch {
            tap.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
            tap.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        onTap()
    }
    Box(
        Modifier
            .size(136.dp)
            .graphicsLayer {
                translationY = bob.value
                val t = tap.value
                scaleX = pop - 0.10f * t
                scaleY = pop - 0.10f * t
            },
        contentAlignment = Alignment.Center,
    ) {
        // A ring that keeps rippling outward from the disc, like a sonar ping.
        Box(
            Modifier
                .size(136.dp)
                .drawBehind {
                    val r = (0.26f + 0.24f * pulse.value) * size.minDimension
                    drawCircle(
                        color = accent.copy(alpha = (1f - pulse.value) * 0.35f),
                        radius = r,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                },
        )
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
        // The spinning conic ring with a comet head and two orbiting sparks riding it.
        Box(
            Modifier
                .size(104.dp)
                .graphicsLayer { rotationZ = spin.value + tap.value * 220f }
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
                    // The comet: a bright head that sweeps the ring, with a soft glow behind it.
                    val head = Offset(center.x + r, center.y)
                    drawCircle(accent.copy(alpha = 0.35f), radius = 11.dp.toPx(), center = head)
                    drawCircle(Color.White, radius = 4.dp.toPx(), center = head)
                    listOf(120f, 240f).forEach { deg ->
                        val rad = Math.toRadians(deg.toDouble())
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = Offset(center.x + kotlin.math.cos(rad).toFloat() * r, center.y + kotlin.math.sin(rad).toFloat() * r),
                        )
                    }
                },
        )
        // The frosted disc with the glyph, and a specular glint along its top edge. Tap for a spark,
        // hold to set the whole deck off.
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerLow)),
                    CircleShape,
                )
                .combinedClickable(onClick = onTapWithFlair, onLongClick = onLongPress)
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
    category: String,
    accent: Color,
    current: Boolean,
    onHeroTap: () -> Unit = {},
    onHeroLongPress: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // A one-shot light sweep across the glass when this card becomes the current one.
    val sheen = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(current) {
        if (current) {
            sheen.snapTo(0f)
            sheen.animateTo(1f, tween(950, easing = LinearEasing))
        }
    }
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
                // A tinted, uneven edge on top of the frosted rim, so the glass catches the page's
                // colour along its edge.
                .border(
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.55f), Color.Transparent, accent.copy(alpha = 0.30f)),
                        ),
                    ),
                    ExtraLargeShape,
                )
                .padding(GapBlock),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            OnboardingHero(spec.icon, accent, current, onTap = onHeroTap, onLongPress = onHeroLongPress)
            // A small glass chip naming the card's part of the flow.
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = GapRow, vertical = GapHairline),
            ) {
                Text(
                    category,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
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
        // A big, faint watermark glyph in the corner, so each card reads as designed rather than a
        // plain sheet.
        Box(Modifier.matchParentSize().clip(ExtraLargeShape), contentAlignment = Alignment.BottomEnd) {
            Icon(
                spec.icon,
                contentDescription = null,
                tint = accent.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(230.dp)
                    .offset(x = 78.dp, y = 64.dp),
            )
        }
        // The one-shot sheen rides over the glass, clipped to the card, so a new card catches the
        // light as it lands.
        Box(
            Modifier.matchParentSize().clip(ExtraLargeShape).drawBehind {
                val p = sheen.value
                val x = -0.45f + 1.9f * p
                drawRoundRect(
                    brush = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                        start = Offset(size.width * (x - 0.22f), 0f),
                        end = Offset(size.width * (x + 0.22f), size.height),
                    ),
                    cornerRadius = CornerRadius(40.dp.toPx()),
                )
            },
        )
    }
}
