package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** One option in a [MorphSegmented] control. [icon] is optional (watch omits it). */
data class SegmentOption(val key: String, val label: String, val icon: ImageVector? = null)

/**
 * Bloo's full-width segmented selector, shared between phone (:app) and watch
 * (:wear) so the drag/tap gesture and sliding-highlight rendering live in exactly
 * one place. A tonal track whose active segment fills with [indicatorColor] and
 * morphs to a rounded square, the rest staying pill-calm; drag it and the
 * highlight bounces to wherever you let go, or tap a segment to jump there.
 *
 * Callers supply all colour/typography/haptic context as parameters so this
 * module stays neutral to compose.material3 vs wear.compose.material3.
 *
 * @param onTick Called each time the selection actually changes (for a haptic).
 */
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    containerColor: Color,
    indicatorColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    textStyle: TextStyle,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = if (options.any { it.icon != null }) 48.dp else 44.dp,
) {
    val selectedIndex = options.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val trackPad = 4.dp
    val gap = 4.dp
    // pointerInput below is keyed on (n, stepPx) only, so its gesture-handling
    // coroutine is launched once and keeps running (awaitEachGesture loops
    // internally) across many recompositions without restarting. A plain closure
    // over selectedKey/onSelect/onTick would freeze at whatever those were on that
    // first launch — every read inside the gesture handler would silently use the
    // ORIGINAL selection forever, not the current one ("stops working after a
    // couple of taps, can't reselect the original option"). rememberUpdatedState
    // keeps these reads live without relaunching (interrupting) an in-progress
    // gesture.
    val currentSelectedKey by rememberUpdatedState(selectedKey)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnTick by rememberUpdatedState(onTick)
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(containerColor),
    ) {
        BoxWithConstraints(Modifier.padding(trackPad).height(trackHeight)) {
            val n = options.size
            // Exact per-segment width so the highlight is flush with each pill's own
            // edges — no residual gap between the highlight and the track border.
            val segWidth = (maxWidth - gap * (n - 1)) / n
            val density = LocalDensity.current
            val stepPx = with(density) { (segWidth + gap).toPx() }
            val maxXPx = with(density) { (segWidth * (n - 1) + gap * (n - 1)).toPx() }

            // While the finger is down, the highlight tracks it 1:1 (no spring lag);
            // on release it snaps to a real selection and springs — "drag it and it
            // bounces to wherever you let go" — instead of only responding to a tap.
            var dragXPx by remember { mutableStateOf<Float?>(null) }
            val restingX = (segWidth + gap) * selectedIndex
            val indicatorX by animateDpAsState(
                targetValue = dragXPx?.let { with(density) { it.toDp() } } ?: restingX,
                // LowBouncy keeps a quick StiffnessMedium settle with just a light
                // touch of overshoot (MediumBouncy wobbled too much on every change).
                animationSpec = if (dragXPx != null) snap()
                                else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                label = "segIndicatorX",
            )

            val segWidthPx = with(density) { segWidth.toPx() }
            // Raw touch/drag X is where the finger physically is; the indicator's
            // rendered position is its LEFT edge. Centre the indicator on the touch
            // point (subtract half a segment) so it doesn't jump half a segment off
            // ("tracks your finger but not really").
            fun offsetFor(touchXPx: Float): Float = (touchXPx - segWidthPx / 2f).coerceIn(0f, maxXPx)

            // stepPx is the pitch (segment + gap); indexFor expects an X already in
            // "indicator left-edge" terms (run through offsetFor), so a segment's own
            // centre lands on an exact multiple of stepPx — the rounding boundary is
            // each segment's centre, not its trailing edge (which rounded a back-third
            // tap up into the next segment).
            fun indexFor(offsetXPx: Float): Int =
                (offsetXPx / stepPx).roundToInt().coerceIn(0, n - 1)

            // Which segment reads as "selected" (bold, tinted text) while dragging:
            // the live drag position, not the committed prop (which only changes on
            // release) — otherwise the moving highlight and the bold label disagree.
            val visualIndex = dragXPx?.let { indexFor(it) } ?: selectedIndex

            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(segWidth)
                    .fillMaxHeight()
                    .background(indicatorColor, RoundedCornerShape(14.dp)),
            )
            Row(
                Modifier
                    .fillMaxSize()
                    // Touch-slop race (same as AnimatedSlider): don't consume anything
                    // until the gesture is confirmed horizontal (dx > slop && dx >= dy).
                    // A release before that is a tap (selects at that position);
                    // confirmed vertical movement (dy > slop) cedes the gesture to an
                    // ancestor scroll, so a settings list still scrolls past this control.
                    .pointerInput(n, stepPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            var claimed = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!claimed) {
                                        change.consume()
                                        val idx = indexFor(offsetFor(down.position.x))
                                        if (options[idx].key != currentSelectedKey) {
                                            currentOnTick()
                                            currentOnSelect(options[idx].key)
                                        }
                                    }
                                    break
                                }
                                if (!claimed) {
                                    val dx = abs(change.position.x - down.position.x)
                                    val dy = abs(change.position.y - down.position.y)
                                    when {
                                        dx > slop && dx >= dy -> {
                                            claimed = true
                                            change.consume()
                                            dragXPx = offsetFor(change.position.x)
                                        }
                                        dy > slop -> break
                                    }
                                } else if (change.positionChanged()) {
                                    change.consume()
                                    dragXPx = offsetFor(change.position.x)
                                }
                            }
                            if (claimed) {
                                val x = dragXPx ?: offsetFor(down.position.x)
                                val idx = indexFor(x)
                                dragXPx = null
                                if (options[idx].key != currentSelectedKey) {
                                    currentOnTick()
                                    currentOnSelect(options[idx].key)
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                options.forEachIndexed { i, opt ->
                    val selected = i == visualIndex
                    val fg by animateColorAsState(
                        if (selected) selectedTextColor else unselectedTextColor,
                        spring(stiffness = Spring.StiffnessMediumLow),
                        label = "segFg",
                    )
                    Box(
                        modifier = Modifier
                            .width(segWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            opt.icon?.let { icon ->
                                Image(
                                    painter = rememberVectorPainter(icon),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(fg),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            BasicText(
                                opt.label,
                                style = textStyle.copy(
                                    color = fg,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
