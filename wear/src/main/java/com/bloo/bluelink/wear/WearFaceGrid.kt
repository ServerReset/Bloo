package com.bloo.bluelink.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.onSizeChanged
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.standardButtonIcon
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The four actions on the face, as four quarters of the circle. Each quarter is an annular sector that follows the display's edge, so the
 * corners a rectangle would waste are used. The quarters' inner edge is the hub's gap; the bottom two stop short of the curved name's band.
 * All sizes are in face units (see [LocalFaceUnit]).
 */

/** The top quarters reach this far in from the face's edge: past the ring and its gap. */
private const val TopOuterInset = 13f

/** The bottom quarters stop this far in, clear of the curved name, which runs round the bottom just inside the ring. */
private const val BottomOuterInset = 22f

/** The hub's radius, and the gap between the hub and the quarters. */
internal const val HubRadius = 38f
private const val InnerGap = 4f

/** The gap between two neighbouring quarters, measured along the arc. */
private const val QuarterGap = 3f

/** How far a pressed quarter grows outward. */
private const val PressOutward = 2.5f

/** How far a quarter's label sits out from the middle of its band. */
private const val LabelOutward = 3f

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
 * Up to four actions as the four quarters of the circle, with the hub in the middle. Each shows its glyph and its name, and a name wraps
 * rather than being cut short. Pressing a quarter makes it grow (see [QuadrantPress]). A long press on the hub calls [onHold]. Order:
 * top-left, top-right, bottom-left, bottom-right.
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
        // The quarters are drawn in the face's own colours; a pressed quarter grows outward (read in the draw phase, so a press redraws
        // without recomposing).
        Canvas(Modifier.fillMaxSize()) {
            val r = min(this.size.width, this.size.height) / 2f
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            for (q in 0 until 4) {
                val action = slots[q]
                val grow = growOf(press, q, pxU)
                val rIn = (HubRadius + InnerGap) * pxU
                val rOut = r - outerInset(q) * pxU + grow
                val fill = when {
                    action == null -> scheme.surfaceContainer
                    action.active -> scheme.primary
                    press?.pressed == q -> scheme.surfaceContainerHighest
                    else -> scheme.surfaceContainerHigh
                }
                drawPath(sectorPath(centre, rIn, rOut, q), fill)
            }
        }
        // The labels, each at the middle of its quarter, moved out with the press.
        Layout(
            content = {
                for (q in 0 until 4) {
                    val action = slots[q]
                    if (action != null) {
                        val ink = if (action.active) scheme.onPrimary else scheme.onSurface
                        QuarterLabel(action, ink, unit)
                    } else {
                        Box {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val cx = w / 2f
            val cy = h / 2f
            val r = min(w, h) / 2f
            val placed = measurables.mapIndexed { q, m ->
                val rIn = (HubRadius + InnerGap) * pxU
                val rOut = r - outerInset(q) * pxU
                val grow = growOf(press, q, pxU)
                val mid = Math.toRadians((QuarterStart[q] + 45f).toDouble())
                // Clear of the hub's gap on the diagonal: the label is no wider than the band it sits in, and wraps onto another line first.
                val radius = (rIn + rOut) / 2f + LabelOutward * pxU + grow
                val boxW = ((rOut - rIn) * 0.9f).roundToInt().coerceAtLeast(1)
                val p = m.measure(Constraints(maxWidth = boxW, maxHeight = h))
                val x = cx + radius * cos(mid).toFloat() - p.width / 2f
                val y = cy + radius * sin(mid).toFloat() - p.height / 2f
                p to (x.roundToInt() to y.roundToInt())
            }
            layout(w, h) {
                placed.forEach { (p, at) -> p.place(at.first, at.second) }
            }
        }
    }
}

/** How far quarter [q] has grown outward: the press's amount while it is the one held, nothing otherwise. */
private fun growOf(press: QuadrantPress?, q: Int, pxU: Float): Float =
    if (press != null && press.pressed == q) press.grow.value * PressOutward * pxU else 0f

/** How far a quarter's outer edge is kept in from the face's edge, in face units. */
private fun outerInset(quadrant: Int): Float = if (quadrant >= 2) BottomOuterInset else TopOuterInset

/** The quarter under [at], [QuadrantPress.HUB] for the hub, or [QuadrantPress.NONE] for the gaps and the ring. */
private fun quarterAt(at: Offset, size: IntSize, pxU: Float): Int {
    if (size.width == 0 || size.height == 0) return QuadrantPress.NONE
    val dx = at.x - size.width / 2f
    val dy = at.y - size.height / 2f
    val d = hypot(dx, dy)
    if (d < HubRadius * pxU) return QuadrantPress.HUB
    if (d < (HubRadius + InnerGap) * pxU) return QuadrantPress.NONE
    val q = (if (dy < 0) 0 else 2) + (if (dx < 0) 0 else 1)
    val outer = min(size.width, size.height) / 2f - outerInset(q) * pxU
    return if (d > outer) QuadrantPress.NONE else q
}

/** The annular sector for quadrant [q], with a gap on each side of it so neighbouring quarters stay apart along the arc. */
private fun sectorPath(centre: Offset, rIn: Float, rOut: Float, q: Int): Path {
    val midRadius = (rIn + rOut) / 2f
    // Half the gap, as an angle at the sector's middle radius.
    val gapDeg = Math.toDegrees(((QuarterGap * 0.5f) / midRadius).toDouble()).toFloat()
    val start = QuarterStart[q] + gapDeg
    val sweep = 90f - 2f * gapDeg
    val outer = Rect(centre.x - rOut, centre.y - rOut, centre.x + rOut, centre.y + rOut)
    val inner = Rect(centre.x - rIn, centre.y - rIn, centre.x + rIn, centre.y + rIn)
    return Path().apply {
        arcTo(outer, start, sweep, forceMoveTo = true)
        arcTo(inner, start + sweep, -sweep, forceMoveTo = false)
        close()
    }
}

/** One quarter's glyph and name, centred in the quarter. Its touch is the grid's; this only carries the spoken name and action. */
@Composable
private fun QuarterLabel(action: WearQuickAction, ink: Color, unit: Float) {
    val glyph = action.icon ?: standardButtonIcon(action.label)
    Column(
        Modifier.semantics {
            contentDescription = action.label
            onClick(action.label) {
                if (!action.pending) action.onClick()
                true
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            glyph?.let { MorphButtonLabel(icon = it, label = "", pending = action.pending, iconSize = 26.dp * unit) }
        }
        RevealText(
            action.label,
            style = TextStyle(fontSize = (12f * unit).sp),
            fontWeight = FontWeight.SemiBold,
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
}
