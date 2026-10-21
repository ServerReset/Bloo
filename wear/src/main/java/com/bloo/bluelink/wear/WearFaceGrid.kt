package com.bloo.bluelink.wear

import com.bloo.uicommon.LocalPowerPolicy
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.wear.compose.foundation.basicCurvedText
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.standardButtonIcon

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One of the car's actions on the watch face: one quarter of [WearCarFace]'s circle.
 *
 * The label is not lost when it is not drawn -- it becomes the button's spoken description, and it is
 * what asks the app's own vocabulary for the glyph when [icon] is null.
 */
data class WearQuickAction(
    val label: String,
    /** Null asks the app's own vocabulary for the glyph this label uses (see [standardButtonIcon]). */
    val icon: ImageVector? = null,
    val active: Boolean = false,
    /** The command is in flight: the glyph becomes the app's shared spinner and taps are ignored. */
    val pending: Boolean = false,
    val onClick: () -> Unit,
)

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
    val rim = androidx.compose.ui.graphics.Brush.sweepGradient(WearRimStops + WearRimStops.first())
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
    val policy = LocalPowerPolicy.current
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
            glyphBump.snapTo(1.04f)
            glyphBump.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
        }
    }
    MorphButton(
        onClick = action.onClick,
        interactionSource = interaction,
        modifier = modifier.semantics { contentDescription = action.label }
            .graphicsLayer {
                val e = enter.value
                val s = 0.96f + 0.04f * e
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f) * (breathe?.value ?: 1f)
            }
            .wearGlassBlur(shape(0f))
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
internal fun WearRingLabels(actions: List<WearQuickAction?>) {
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
                    // A preset's name is the phone's to choose: cut so it stays on its own quarter's arc.
                    action.label.let { if (it.length > 14) it.take(13) + "…" else it },
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

/**
 * Toward the circle's centre: a glyph is not centred in its quarter but drawn in toward the hub (the curved label takes the
 * outer edge), so the four read as one group round the middle.
 */
private fun biasX(a: Alignment) = when (a) { Alignment.TopStart, Alignment.BottomStart -> 0.34f; else -> -0.34f }
private fun biasY(a: Alignment) = when (a) { Alignment.TopStart, Alignment.TopEnd -> 0.34f; else -> -0.34f }

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
