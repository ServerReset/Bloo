package com.bloo.bluelink.wear

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
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
import kotlin.math.roundToInt
import androidx.wear.compose.foundation.basicCurvedText
import com.bloo.bluelink.data.DeviceStatus
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.standardButtonIcon
import kotlinx.coroutines.launch

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

/** The display the face's fixed sizes (ring, gaps, glyphs) were drawn for; every size scales by (real display / this). */
private val ReferenceFace = 227.dp

/** How far the cross through the face moves, as a share of the face, when a quarter is pressed (the quarter grows). */
private const val PressGrow = 0.11f

/**
 * The face's size relative to the 227dp it was designed on, so a 192dp watch and a 240dp watch (and any display
 * size setting, which changes the dp the display has) get the same face, proportionally.
 */
internal val LocalFaceUnit = androidx.compose.runtime.compositionLocalOf { 1f }

/**
 * Which quarter is held down, and the cross through the middle of the face that moves because of it: pressing a
 * quarter makes it grow and the other three give way, and the hub (which sits on the cross) moves with it -- the
 * same push-and-give of a row of buttons on the phone. The cross is two springs, one per axis; they are read where
 * the face is laid out, so a press re-lays-out the tiles without recomposing them.
 */
@androidx.compose.runtime.Stable
internal class QuadrantPress(
    internal val x: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    internal val y: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
) {
    var pressed by androidx.compose.runtime.mutableIntStateOf(NONE)
        private set

    /** The cross's offset from the middle, as a share of the face's width and height. */
    val dx: Float get() = x.value
    val dy: Float get() = y.value

    fun press(quadrant: Int, down: Boolean) {
        if (down) pressed = quadrant else if (pressed == quadrant) pressed = NONE
    }

    companion object { const val NONE = -1 }
}

@Composable
internal fun rememberQuadrantPress(): QuadrantPress {
    val policy by DeviceStatus.policy.collectAsStateWithLifecycle()
    val state = androidx.compose.runtime.remember {
        QuadrantPress(androidx.compose.animation.core.Animatable(0f), androidx.compose.animation.core.Animatable(0f))
    }
    val q = state.pressed
    androidx.compose.runtime.LaunchedEffect(q, policy.bouncySprings) {
        // The pressed quarter's own corner stays put; the cross moves AWAY from it, which is what makes it bigger.
        val tx = when (q) { 0, 2 -> PressGrow; 1, 3 -> -PressGrow; else -> 0f }
        val ty = when (q) { 0, 1 -> PressGrow; 2, 3 -> -PressGrow; else -> 0f }
        val spec: androidx.compose.animation.core.AnimationSpec<Float> =
            if (policy.bouncySprings) spring(dampingRatio = 0.62f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium) else snap()
        kotlinx.coroutines.coroutineScope {
            launch { state.x.animateTo(tx, spec) }
            launch { state.y.animateTo(ty, spec) }
        }
    }
    return state
}

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
    /** Pressing the centre (the app opens the face editor). Null leaves the hub as plain text. */
    onHubClick: (() -> Unit)? = null,
) {
    // The aurora, at watch scale: a soft wash of the level's colour behind the tiles, as the phone's
    // background washes behind its cards. Drawn once, no animation.
    BoxWithConstraints(
        modifier.wearAuroraWash(tint),
        contentAlignment = Alignment.Center,
    ) {
        val unit = minOf(maxWidth, maxHeight) / ReferenceFace
        val press = rememberQuadrantPress()
        CompositionLocalProvider(LocalFaceUnit provides unit) {
            WearEdgeRing(
                progress = progress,
                tint = tint,
                modifier = Modifier.fillMaxSize().alpha(if (stale) 0.5f else 1f),
                stroke = RingStroke * unit,
                charging = charging || refreshing,
            )
            Box(Modifier.fillMaxSize().padding((RingStroke + RingGap) * unit), contentAlignment = Alignment.Center) {
                WearQuadrantGrid(actions = actions, modifier = Modifier.fillMaxSize(), press = press)
                // The labels run round the inside of the ring, each along its own quarter's outer edge.
                WearRingLabels(actions)
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val hubSize = maxWidth * HubFraction
                    // The hub sits where the cross is, so it moves with the quarter that was pressed.
                    Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((press.dx * w).roundToInt(), (press.dy * h).roundToInt()) }) {
                        WearFaceHub(value = value, caption = caption, tint = tint, size = hubSize, status = status, onClick = onHubClick)
                    }
                }
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
/**
 * The grid with press-and-give: [press] holds which quarter is down, and the cross between the four moves away from
 * it, so that quarter grows and the others shrink to make room. The grid is clipped to a circle, so the tiles are
 * plain rectangles that the circle trims -- whatever the cross does, the outer edge keeps following the display's.
 */
@Composable
internal fun WearQuadrantGrid(actions: List<WearQuickAction?>, modifier: Modifier = Modifier, press: QuadrantPress? = null) {
    val slots = (0 until 4).map { actions.getOrNull(it) }
    val unit = LocalFaceUnit.current
    val gap = TileGap * unit
    val rim = androidx.compose.ui.graphics.Brush.sweepGradient(
        listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.3f)),
    )
    androidx.compose.ui.layout.Layout(
        content = { for (q in 0 until 4) QuarterTile(q, slots[q], Modifier.fillMaxSize(), press) },
        modifier = modifier
            .clip(CircleShape)
            .drawWithContent {
                drawContent()
                // The glass rim on the curved outer edge, which the circle clip leaves bare.
                drawCircle(rim, radius = size.minDimension / 2f - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val g = gap.roundToPx()
        // The cross: the middle, moved by the press. Each quarter spans from the edge to half a gap short of it.
        val cx = (w / 2f + w * (press?.dx ?: 0f)).roundToInt()
        val cy = (h / 2f + h * (press?.dy ?: 0f)).roundToInt()
        val leftW = (cx - g / 2).coerceAtLeast(1)
        val rightW = (w - cx - g / 2).coerceAtLeast(1)
        val topH = (cy - g / 2).coerceAtLeast(1)
        val bottomH = (h - cy - g / 2).coerceAtLeast(1)
        val placeables = measurables.mapIndexed { q, m ->
            val tw = if (q % 2 == 0) leftW else rightW
            val th = if (q < 2) topH else bottomH
            m.measure(androidx.compose.ui.unit.Constraints.fixed(tw, th))
        }
        layout(w, h) {
            placeables[0].place(0, 0)
            placeables[1].place(w - rightW, 0)
            placeables[2].place(0, h - bottomH)
            placeables[3].place(w - rightW, h - bottomH)
        }
    }
}

/** One quarter of the face. [quadrant]: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
@Composable
private fun QuarterTile(quadrant: Int, action: WearQuickAction?, modifier: Modifier, press: QuadrantPress?) {
    val scheme = MaterialTheme.colorScheme
    val unit = LocalFaceUnit.current
    val shape = { morph: Float -> quarterShape(quadrant, innerCorner = (14f + 10f * morph).dp * unit) }
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
    // A press is shown by the tile growing and the others giving way ([QuadrantPress]), not by squashing it.
    androidx.compose.runtime.LaunchedEffect(pressed) { press?.press(quadrant, pressed) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { press?.press(quadrant, false) } }
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
                val s = 0.78f + 0.22f * e
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f) * (breathe?.value ?: 1f)
            }
            .wearGlassRim(shape(0f)),
        enabled = !action.pending,
        frostWhenDisabled = false,
        expressive = false,
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
        Box(Modifier.fillMaxSize().padding(10.dp * unit), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(androidx.compose.ui.BiasAlignment(biasX(toward), biasY(toward)))
                    .graphicsLayer { scaleX = glyphBump.value; scaleY = glyphBump.value },
            ) {
                glyph?.let {
                    CompositionLocalProvider(LocalContentColor provides tint) {
                        MorphButtonLabel(icon = it, label = "", pending = action.pending, iconSize = 28.dp * unit)
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
    // The label is part of the drawing, so it is sized with the face (and ignores the font-size setting, which would
    // grow it past the arc it is curved along) -- 12dp on the 227dp design.
    val labelSize = with(androidx.compose.ui.platform.LocalDensity.current) { (12f * LocalFaceUnit.current).dp.toSp() }
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
                            fontSize = labelSize,
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

/**
 * A quarter's own shape: a plain rectangle whose only rounded corner is the inner one, at the hub. The OUTER edge is not
 * the tile's business -- the grid is clipped to a circle -- which is what keeps it on the display's curve while the
 * tile grows and shrinks under a press.
 */
private fun quarterShape(quadrant: Int, innerCorner: Dp): RoundedCornerShape {
    val inner = CornerSize(innerCorner)
    val none = CornerSize(0.dp)
    return when (quadrant) {
        0 -> RoundedCornerShape(topStart = none, topEnd = none, bottomEnd = inner, bottomStart = none)
        1 -> RoundedCornerShape(topStart = none, topEnd = none, bottomEnd = none, bottomStart = inner)
        2 -> RoundedCornerShape(topStart = none, topEnd = inner, bottomEnd = none, bottomStart = none)
        else -> RoundedCornerShape(topStart = inner, topEnd = none, bottomEnd = none, bottomStart = none)
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
                        .semantics { contentDescription = "Change face controls" }
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
                    // In dp-sized text: the hub is a fixed circle, so the value must not grow with the font setting.
                    fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size.value * 0.26f).coerceIn(14f, 26f).dp.toSp() },
                ),
                maxLines = 1,
                reduceMotion = false,
            )
            RevealText(
                status?.text ?: caption,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (size.value * 0.15f).coerceIn(9f, 12f).dp.toSp() },
                ),
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

