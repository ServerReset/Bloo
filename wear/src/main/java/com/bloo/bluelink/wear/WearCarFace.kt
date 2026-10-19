package com.bloo.bluelink.wear

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.basicCurvedText
import com.bloo.bluelink.data.DeviceStatus
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.standardButtonIcon

/*
 * The watch face for one car: the charge (or fuel) as a ring around the very edge of the display, and inside
 * it the car's four actions as quarter circles, with the value in a small hub where they meet.
 *
 * Built from three pieces that each stand alone -- [WearEdgeRing], [WearQuadrantGrid] and [WearFaceHub] --
 * so a screen that wants only the ring (a loading state) or only the grid can use just that piece.
 */

/** The ring's stroke and the gap between it and the tiles inside it. */
private val RingStroke = 8.dp
private val RingGap = 5.dp

/** The gap between the four quarter tiles (the cross through the middle of the face). */
private val TileGap = 6.dp

/** The hub's share of the inner circle's diameter. */
private const val HubFraction = 0.36f

/**
 * The whole face. Fills its square (on a round watch, the display); everything inside is sized from it, so the
 * same face works on a 192dp and a 240dp watch.
 */
@Composable
fun WearCarFace(
    value: String,
    caption: String,
    /** 0..1, or null for a car that reports no charge or fuel (the ring then shows its track only). */
    progress: Float?,
    tint: Color,
    actions: List<WearQuickAction?>,
    modifier: Modifier = Modifier,
    /** Charging: a soft light travels along the filled part of the ring. */
    charging: Boolean = false,
    /** The reading is old enough to doubt: the ring dims, so a glance does not trust it. */
    stale: Boolean = false,
    /** A short line that takes the caption's place for a moment ("Sending…", "Done", "Updated 2 h ago"). */
    status: FaceStatus? = null,
    /** A refresh is in flight: the light travels the ring as it does while charging. */
    refreshing: Boolean = false,
    /** Pressing the centre: refresh. Null leaves the hub as plain text. */
    onHubClick: (() -> Unit)? = null,
) {
    // The aurora, at watch scale: a soft wash of the level's colour behind the tiles, as the phone's
    // background washes behind its cards. Drawn once, no animation.
    Box(
        modifier.wearAuroraWash(tint),
        contentAlignment = Alignment.Center,
    ) {
        WearEdgeRing(
            progress = progress,
            tint = tint,
            modifier = Modifier.fillMaxSize().alpha(if (stale) 0.5f else 1f),
            charging = charging || refreshing,
        )
        Box(Modifier.fillMaxSize().padding(RingStroke + RingGap), contentAlignment = Alignment.Center) {
            WearQuadrantGrid(actions = actions, modifier = Modifier.fillMaxSize())
            // The labels run round the inside of the ring, each along its own quarter's outer edge.
            WearRingLabels(actions)
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WearFaceHub(value = value, caption = caption, tint = tint, size = maxWidth * HubFraction, status = status, onClick = onHubClick)
            }
        }
    }
}

/**
 * The charge or fuel as a ring that hugs the display's edge: a soft track all the way round, and the level
 * from 12 o'clock clockwise. Springs to a new level; under the watch's lean mode it jumps.
 */
@Composable
fun WearEdgeRing(
    progress: Float?,
    tint: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = RingStroke,
    /** A short light travels along the filled arc while charging (not under the watch's lean mode). */
    charging: Boolean = false,
) {
    val policy by DeviceStatus.policy.collectAsStateWithLifecycle()
    val sheenOn = charging && policy.ambientMotion && progress != null
    val sheen = if (sheenOn) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "chargeSheen").animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)),
            label = "chargeSheenPhase",
        )
    } else {
        null
    }
    val sweep by animateFloatAsState(
        (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = if (policy.bouncySprings) spring() else snap(),
        label = "edgeRing",
    )
    val track = tint.copy(alpha = 0.18f)
    Box(
        modifier.drawBehind {
            val w = stroke.toPx()
            val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            if (progress != null && sweep > 0f) {
                drawArc(tint, -90f, 360f * sweep, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                // Read in the draw phase only: the sheen repaints the ring, nothing recomposes.
                sheen?.value?.let { phase ->
                    val span = minOf(28f, 360f * sweep)
                    val start = -90f + (360f * sweep - span) * phase
                    drawArc(Color.White.copy(alpha = 0.45f), start, span, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
        },
    )
}

/**
 * Up to four actions as the four quarters of a circle: each is a square whose OUTER corner is rounded by its
 * whole width (which makes it a quarter disc) and whose inner corner, where the four meet, is a soft corner the
 * hub sits over. Order: top-left, top-right, bottom-left, bottom-right. A missing action leaves a quiet,
 * inactive quarter, so the circle stays whole.
 *
 * Each quarter is the app's own [MorphButton] -- its press feedback, haptic, frost and pending spinner -- given
 * the quarter shape; a press softens the inner corner a little more, the way every button squares off.
 */
@Composable
fun WearQuadrantGrid(actions: List<WearQuickAction?>, modifier: Modifier = Modifier) {
    val slots = (0 until 4).map { actions.getOrNull(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TileGap)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                for (col in 0..1) {
                    val q = row * 2 + col
                    QuarterTile(q, slots[q], Modifier.weight(1f).fillMaxSize())
                }
            }
        }
    }
}

/** One quarter of the face. [quadrant]: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
@Composable
private fun QuarterTile(quadrant: Int, action: WearQuickAction?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = { morph: Float -> quarterShape(quadrant, innerCorner = (14f + 10f * morph).dp) }
    if (action == null) {
        Box(modifier.clip(shape(0f)).background(scheme.surfaceContainer.copy(alpha = 0.5f)))
        return
    }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tint = if (action.active) scheme.onPrimary else scheme.onSurface
    val glyph = action.icon ?: standardButtonIcon(action.label)
    // --- The tile's motion: it pops in (staggered by quadrant), squashes under a finger and springs back, breathes
    // while it is on, and its glyph bounces when what it says changes. All gated by the watch's own power policy.
    val policy by DeviceStatus.policy.collectAsStateWithLifecycle()
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        animationSpec = if (policy.bouncySprings) spring(dampingRatio = 0.42f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium) else snap(),
        label = "tilePress",
    )
    val enter = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (policy.bouncySprings) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (enter.value < 1f) {
            kotlinx.coroutines.delay(quadrant * 70L)
            enter.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow))
        }
    }
    val breathe = if (action.active && policy.ambientMotion) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "tileBreath").animateFloat(
            0.86f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "tileBreathAlpha",
        )
    } else null
    // The glyph bounces when the tile's meaning changes (Lock becomes Unlock, Climate turns on).
    val glyphBump = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(action.label, action.active) {
        if (policy.bouncySprings) {
            glyphBump.snapTo(1.3f)
            glyphBump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
        }
    }
    MorphButton(
        onClick = action.onClick,
        interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = action.label }
            .graphicsLayer {
                val e = enter.value
                val s = (0.78f + 0.22f * e) * pressScale
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f) * (breathe?.value ?: 1f)
            }
            .wearGlassRim(shape(0f)),
        enabled = !action.pending,
        frostWhenDisabled = false,
        active = action.active,
        // Glass with the phone's rim: a translucent quarter that lets the wash behind it through.
        containerColor = scheme.surfaceContainerHigh.copy(alpha = 0.55f),
        contentColor = scheme.onSurface,
        activeContainerColor = scheme.primary,
        activeContentColor = scheme.onPrimary,
        contentPadding = PaddingValues(0.dp),
        minHeight = 0.dp,
        shapeForCorner = { morph, _ -> shape(morph) },
        onClickHaptic = { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
    ) {
        // The glyph sits in the middle of the quarter's visible area, a little away from the hub.
        val toward = when (quadrant) {
            0 -> Alignment.TopStart
            1 -> Alignment.TopEnd
            2 -> Alignment.BottomStart
            else -> Alignment.BottomEnd
        }
        Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(androidx.compose.ui.BiasAlignment(biasX(toward), biasY(toward)))
                    .graphicsLayer { scaleX = glyphBump.value; scaleY = glyphBump.value },
            ) {
                glyph?.let {
                    CompositionLocalProvider(LocalContentColor provides tint) {
                        MorphButtonLabel(icon = it, label = "", pending = action.pending, iconSize = 28.dp)
                    }
                }
            }
        }
    }
}

/**
 * The four labels, curved to the ring: anchored at each quarter's middle (clockwise from 3 o'clock: top-left 225,
 * top-right 315, bottom-right 45, bottom-left 135), the bottom two reversed so they still read left to right.
 */
@Composable
private fun WearRingLabels(actions: List<WearQuickAction?>) {
    val scheme = MaterialTheme.colorScheme
    val anchors = floatArrayOf(225f, 315f, 135f, 45f)
    Box(Modifier.fillMaxSize()) {
        for (q in 0 until 4) {
            val action = actions.getOrNull(q) ?: continue
            val color = if (action.active) scheme.onPrimary else scheme.onSurface
            androidx.wear.compose.foundation.CurvedLayout(
                modifier = Modifier.fillMaxSize(),
                anchor = anchors[q],
                angularDirection = if (q >= 2) androidx.wear.compose.foundation.CurvedDirection.Angular.Reversed
                else androidx.wear.compose.foundation.CurvedDirection.Angular.Normal,
            ) {
                basicCurvedText(
                    action.label,
                    style = {
                        androidx.wear.compose.foundation.CurvedTextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                    },
                )
            }
        }
    }
}

/** A little toward the hub: the curved label takes the outer edge. */
private fun biasX(a: Alignment) = when (a) { Alignment.TopStart, Alignment.BottomStart -> 0.1f; else -> -0.1f }
private fun biasY(a: Alignment) = when (a) { Alignment.TopStart, Alignment.TopEnd -> 0.1f; else -> -0.1f }

/** The quarter-disc: the outer corner rounded by the tile's whole width, the inner corner by [innerCorner]. */
private fun quarterShape(quadrant: Int, innerCorner: Dp): RoundedCornerShape {
    val outer = CornerSize(100)
    val inner = CornerSize(innerCorner)
    val none = CornerSize(4.dp)
    return when (quadrant) {
        0 -> RoundedCornerShape(topStart = outer, topEnd = none, bottomEnd = inner, bottomStart = none)
        1 -> RoundedCornerShape(topStart = none, topEnd = outer, bottomEnd = none, bottomStart = inner)
        2 -> RoundedCornerShape(topStart = none, topEnd = inner, bottomEnd = none, bottomStart = outer)
        else -> RoundedCornerShape(topStart = inner, topEnd = none, bottomEnd = outer, bottomStart = none)
    }
}

/** The value where the four quarters meet: the charge or fuel (or the lock state), and the car's name under it. */
@Composable
fun WearFaceHub(value: String, caption: String, tint: Color, size: Dp, status: FaceStatus? = null, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // The hub squeezes under a finger and springs back, like the tiles round it.
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.45f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "hubPress")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(CircleShape)
            .background(scheme.background)
            .then(
                if (onClick != null) {
                    Modifier
                        .semantics { contentDescription = "Refresh" }
                        .clickable(interactionSource = interaction, indication = null) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                            onClick()
                        }
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    textAlign = TextAlign.Center,
                    fontSize = (size.value * 0.26f).coerceIn(14f, 26f).sp,
                ),
                maxLines = 1,
                reduceMotion = false,
            )
            RevealText(
                status?.text ?: caption,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    status == null -> scheme.onSurfaceVariant
                    status.error -> scheme.error
                    else -> scheme.primary
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(size * 0.8f),
            )
        }
    }
}

/** A moment's line in the hub, in place of the car's name: what just happened, or why to doubt the reading. */
data class FaceStatus(val text: String, val error: Boolean = false)

/**
 * The ring's colour, from the level: red when low, amber when getting there, green otherwise, and the
 * charging blue while it charges. A car with no level reads in [fallback].
 */
fun faceTint(percent: Int?, charging: Boolean, fallback: Color): Color =
    Color(levelColorArgb(percent, charging, fallback.toArgb()))

/** [faceTint] as a plain ARGB int, so the Tile (which draws without Compose) colours its ring the same way. */
fun levelColorArgb(percent: Int?, charging: Boolean, fallback: Int): Int =
    com.bloo.bluelink.data.BlooColors.levelColor(percent, charging, fallback)

/** Levels below which the ring warns (amber) and then alarms (red). */
const val LowLevel = com.bloo.bluelink.data.BlooColors.LOW_LEVEL
const val WarnLevel = com.bloo.bluelink.data.BlooColors.WARN_LEVEL

