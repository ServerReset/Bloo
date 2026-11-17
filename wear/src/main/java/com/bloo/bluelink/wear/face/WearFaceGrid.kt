package com.bloo.bluelink.wear

import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.MotionMedium
import com.bloo.uicommon.standardButtonIcon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The four actions on the face, as four quarters of the circle. Each quarter is a rounded annular sector that follows the display's edge.
 * The name of each action runs along the quarter's outer edge, and its glyph sits in the middle of the quarter. The outer edges never move:
 * pressing a quarter pushes the hub away from it, so the pressed quarter takes more of the middle and the others give up their inner sides.
 * All sizes are in face units (see [LocalFaceUnit]).
 */

/** How far every quarter's outer edge is kept in from the face's edge: past the ring and its gap. */
private const val OuterInset = 13f

/** The hub's radius, and the gap between the hub and the quarters. */
internal const val HubRadius = 38f
private const val InnerGap = 4f

/** The gap between two neighbouring quarters, measured along the arc. */
private const val QuarterGap = 3f

/** How round each quarter's corners are. */
private const val CornerRadius = 8f

/**
 * How far a press pushes the hub away from the pressed quarter, in face units. Only the hub moves: every quarter keeps its outer edge, so the
 * pressed quarter takes more of the middle and the others give up their inner sides.
 */
private const val PushCentre = 5f

/** The quarters' sides, by quadrant: x and y sign from the hub, so top-left is (-1, -1). */
private val QuarterSignX = floatArrayOf(-1f, 1f, -1f, 1f)
private val QuarterSignY = floatArrayOf(-1f, -1f, 1f, 1f)


/** The name's distance in from the quarter's outer edge, and the name's size, in face units. */
private const val NameInset = 3f
private const val NameSize = 10.5f

/** Where a quarter's glyph sits, in face units, inside the middle of the quarter. */
private const val GlyphSize = 26f

/** The quarters' first angle, by quadrant: top-left, top-right, bottom-left, bottom-right. Each quarter sweeps 90 degrees. */
private val QuarterStart = floatArrayOf(180f, -90f, 90f, 0f)

/**
 * One of the car's actions on the watch face: one quarter of the face.
 *
 * The label is shown under the glyph and is the button's spoken description; with no [icon] the glyph comes from the app's own vocabulary
 * for the label (see [standardButtonIcon]).
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
 * Up to four actions as the four quarters of the circle, with the hub in the middle. Each quarter's name runs along its outer edge and its
 * glyph sits in its middle. Pressing a quarter makes it grow and the others give way (see [QuadrantPress]). A long press on the hub calls
 * [onHold]. Order: top-left, top-right, bottom-left, bottom-right.
 */
@Composable
internal fun WearQuadrantGrid(
    actions: List<WearQuickAction?>,
    modifier: Modifier = Modifier,
    press: QuadrantPress? = null,
    onHold: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val unit = LocalFaceUnit.current
    val pxU = with(LocalDensity.current) { unit.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val latestActions by rememberUpdatedState(actions)
    val latestHold by rememberUpdatedState(onHold)
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val slots = (0 until 4).map { actions.getOrNull(it) }
    // Each quarter eases to its colour when its control changes (lit when on, quiet when empty). Read in the draw phase, so the change
    // animates without recomposing the grid.
    val fills = (0 until 4).map { q ->
        val target = when {
            slots[q] == null -> scheme.surfaceContainer
            slots[q]!!.active -> scheme.primary
            else -> scheme.surfaceContainerHigh
        }
        animateColorAsState(target, tween(MotionMedium), label = "quarterFill")
    }
    val inks = (0 until 4).map { q -> if (slots[q]?.active == true) scheme.onPrimary else scheme.onSurface }
    val namePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD } }
    // Each quarter's name cross-fades when it changes, as the Compose text does.
    val nameFades = (0 until 4).map { q -> rememberCanvasTextFade(slots[q]?.label.orEmpty()) }
    Box(
        modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(press, pxU) {
                detectTapGestures(
                    onPress = { at ->
                        val q = quarterAt(at, boxSize, pxU)
                        val action = if (q in 0..3) latestActions.getOrNull(q) else null
                        // The hub presses too (it springs under a finger); an empty or busy quarter does not.
                        val down = q == QuadrantPress.HUB || (action != null && !action.pending)
                        if (down) press?.press(q, true)
                        tryAwaitRelease()
                        press?.press(q, false)
                    },
                    onTap = { at ->
                        val q = quarterAt(at, boxSize, pxU)
                        val action = if (q in 0..3) latestActions.getOrNull(q) else null
                        if (action != null && !action.pending) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            action.onClick()
                        }
                    },
                    onLongPress = { at ->
                        if (quarterAt(at, boxSize, pxU) == QuadrantPress.HUB) {
                            latestHold?.let {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                it()
                            }
                        }
                    },
                )
            },
    ) {
        // The quarters and their names, drawn in the draw phase: a press reads the grow amount there, so it redraws without recomposing.
        Canvas(Modifier.fillMaxSize()) {
            val geometry = FaceGeometry(size.width, size.height, pxU, press)
            for (q in 0 until 4) {
                val path = quarterPath(geometry, q)
                val fill = if (geometry.pressed == q) lerp(fills[q].value, scheme.surfaceContainerHighest, 0.5f) else fills[q].value
                // The polygon's corners are rounded by a round-joined stroke of the same colour.
                drawPath(path, fill)
                drawPath(path, fill, style = Stroke(width = 2 * CornerRadius * pxU, join = StrokeJoin.Round))
                slots[q]?.label?.takeIf { it.isNotBlank() }?.let { name ->
                    nameFades[q].draw(name) { shown, a -> drawNameOnEdge(shown, geometry, q, inks[q], namePaint, a) }
                }
            }
        }
        // The glyphs, each in the middle of its quarter, moving with the quarter as it is pressed.
        Layout(
            content = {
                for (q in 0 until 4) {
                    val action = slots[q]
                    if (action != null) QuarterGlyph(action, inks[q], unit) else Box {}
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val geometry = FaceGeometry(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pxU, press)
            val placed = measurables.mapIndexed { q, m ->
                val p = m.measure(Constraints())
                val at = geometry.glyphCentre(q)
                p to ((at.x - p.width / 2f).roundToInt() to (at.y - p.height / 2f).roundToInt())
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placed.forEach { (p, at) -> p.place(at.first, at.second) }
            }
        }
    }
}

/**
 * The face's geometry for one frame. The face's centre and every outer edge are fixed; the hub moves with a press, away from the pressed
 * quarter, so the inner sides give way. Shared by the drawing, the glyphs, the hub and the touch.
 */
private class FaceGeometry(width: Float, height: Float, val pxU: Float, press: QuadrantPress?) {
    val centre = Offset(width / 2f, height / 2f)
    private val radius = min(width, height) / 2f
    val pressed: Int = press?.pressed ?: QuadrantPress.NONE
    private val grow: Float = press?.grow?.value ?: 0f

    /** Where the hub sits: the middle, pushed away from the pressed quarter. */
    val hub: Offset = if (pressed in 0..3) {
        val push = grow * PushCentre * pxU / kotlin.math.sqrt(2f)
        Offset(centre.x - QuarterSignX[pressed] * push, centre.y - QuarterSignY[pressed] * push)
    } else centre

    /** The hub's inner edge: the gap the quarters keep from the hub. */
    val inner: Float get() = (HubRadius + InnerGap) * pxU

    /** The outer edge of every quarter, measured from the face's centre. It never moves, so the rim stays consistent through a press. */
    val outer: Float = radius - OuterInset * pxU

    /** Where the glyph of quarter [q] sits: the middle of its band, on the quarter's diagonal from the hub. */
    fun glyphCentre(q: Int): Offset {
        val r = (inner + outer) / 2f
        val k = r / kotlin.math.sqrt(2f)
        return Offset(hub.x + QuarterSignX[q] * k, hub.y + QuarterSignY[q] * k)
    }
}

/**
 * The rounded quarter [q]: the region between the hub's gap and the face's edge, bounded by the two lines from the hub that divide the
 * quarters. Built as a polygon whose four corners are then rounded by a round-joined stroke of the same colour, so the corners are round.
 */
private fun quarterPath(g: FaceGeometry, q: Int): Path {
    val sx = QuarterSignX[q]
    val sy = QuarterSignY[q]
    val round = CornerRadius * g.pxU
    // The lines sit half a gap in from the hub's cross, and the arcs are pulled in by the round, so the grown shape keeps the gap.
    val gap = QuarterGap * g.pxU / 2f + round
    val c = g.hub
    val f = g.centre
    val ro = g.outer - round
    val ri = g.inner + round
    val xL = c.x + sx * gap
    val yL = c.y + sy * gap
    // Where the outer edge meets each line, on the quarter's side.
    val l1 = Offset(xL, f.y + sy * kotlin.math.sqrt(max(0f, ro * ro - (xL - f.x) * (xL - f.x))))
    val l2 = Offset(f.x + sx * kotlin.math.sqrt(max(0f, ro * ro - (yL - f.y) * (yL - f.y))), yL)
    // Where the hub's edge meets each line.
    val i1 = Offset(xL, c.y + sy * kotlin.math.sqrt(max(0f, ri * ri - (xL - c.x) * (xL - c.x))))
    val i2 = Offset(c.x + sx * kotlin.math.sqrt(max(0f, ri * ri - (yL - c.y) * (yL - c.y))), yL)
    return Path().apply {
        moveTo(l1.x, l1.y)
        arcSamples(f, ro, l1, l2)
        lineTo(i2.x, i2.y)
        arcSamples(c, ri, i2, i1)
        close()
    }
}

private const val HalfTurn = 3.1415927f
private const val FullTurn = 6.2831855f

/** Adds the short arc of a circle from [from] to [to] (both on it), as straight segments. */
private fun Path.arcSamples(centre: Offset, radius: Float, from: Offset, to: Offset) {
    val a0 = atan2(from.y - centre.y, from.x - centre.x)
    var sweep = atan2(to.y - centre.y, to.x - centre.x) - a0
    // The short way round: the quarter's corner is never more than a half turn.
    if (sweep > HalfTurn) sweep -= FullTurn
    if (sweep < -HalfTurn) sweep += FullTurn
    val steps = 16
    for (i in 1..steps) {
        val a = a0 + sweep * i / steps
        lineTo(centre.x + radius * cos(a), centre.y + radius * sin(a))
    }
}

/** The hub's offset for the current press, in pixels: where [WearCarFace] draws the hub so it moves with the quarters. */
internal fun quadrantHubOffset(press: QuadrantPress?, width: Float, height: Float, pxU: Float): Offset {
    val g = FaceGeometry(width, height, pxU, press)
    return Offset(g.hub.x - g.centre.x, g.hub.y - g.centre.y)
}

/**
 * The quarter's name, along its outer edge, reading upright: along the top it runs left to right with its tops outward; along the bottom it
 * runs left to right with its tops toward the middle, so it is never upside down. It sits inside the quarter's edge, clear of the ring.
 */
private fun DrawScope.drawNameOnEdge(name: String, g: FaceGeometry, q: Int, ink: Color, paint: Paint, alpha: Float) {
    val top = q < 2
    val inset = NameInset * g.pxU
    val size = NameSize * g.pxU
    paint.color = ink.toArgb()
    paint.textSize = size
    paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    // The baseline: for the top, the letters rise outward from it, so it sits inside the edge by their height; for the bottom, they rise
    // inward from it, so it sits at the edge.
    val baseline = if (top) g.outer - inset - size * 0.72f else g.outer - inset
    val halfGapDeg = Math.toDegrees(((QuarterGap * g.pxU / 2f) / baseline).toDouble()).toFloat()
    val centreAngle = QuarterStart[q]
    val rect = RectF(g.centre.x - baseline, g.centre.y - baseline, g.centre.x + baseline, g.centre.y + baseline)
    val path = android.graphics.Path().apply {
        if (top) arcTo(rect, centreAngle + halfGapDeg, 90f - 2f * halfGapDeg, true)
        else arcTo(rect, centreAngle + 90f - halfGapDeg, -(90f - 2f * halfGapDeg), true)
    }
    val length = PathMeasure(path, false).length
    val width = paint.measureText(name)
    // A name too long for its quarter shrinks to fit, rather than running off the edge.
    if (width > length * 0.9f) paint.textSize = size * (length * 0.9f / width)
    val offset = (length - paint.measureText(name)) / 2f
    drawIntoCanvas { it.nativeCanvas.drawTextOnPath(name, path, offset, 0f, paint) }
}

/** The glyph of one quarter, with the action's spoken name and tap. Its name is drawn on the edge by the grid, so this is glyph only. */
@Composable
private fun QuarterGlyph(action: WearQuickAction, ink: Color, unit: Float) {
    val glyph = action.icon ?: standardButtonIcon(action.label)
    Box(
        Modifier.semantics {
            contentDescription = action.label
            onClick(action.label) {
                if (!action.pending) action.onClick()
                true
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            glyph?.let { MorphButtonLabel(icon = it, label = "", pending = action.pending, iconSize = GlyphSize.dp * unit) }
        }
    }
}

/** The quarter under [at], [QuadrantPress.HUB] for the hub, or [QuadrantPress.NONE] for the gaps and the ring. */
private fun quarterAt(at: Offset, size: IntSize, pxU: Float): Int {
    if (size.width == 0 || size.height == 0) return QuadrantPress.NONE
    val dx = at.x - size.width / 2f
    val dy = at.y - size.height / 2f
    val d = hypot(dx, dy)
    if (d < HubRadius * pxU) return QuadrantPress.HUB
    if (d < (HubRadius + InnerGap) * pxU) return QuadrantPress.NONE
    val q = (if (dy < 0) 0 else 2) + (if (dx < 0) 0 else 1)
    val outer = min(size.width, size.height) / 2f - OuterInset * pxU
    return if (d > outer) QuadrantPress.NONE else q
}
