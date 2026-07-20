package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
    /** Hairline rim colour, or null for the old borderless look. Every other
     *  interactive surface (MorphButton, cards) got a rim once real glass
     *  blur stopped giving flat surfaces a second depth cue; this control was
     *  the one left out. Null-default keeps any caller that hasn't been
     *  updated visually unchanged. */
    borderColor: Color? = null,
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
    // Slightly less rounded than before (was 20.dp) -- a small tune-down per feedback.
    val trackShape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.fillMaxWidth().clip(trackShape).background(containerColor)
            .then(if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), trackShape) else Modifier),
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
            val segWidthPx = with(density) { segWidth.toPx() }
            val gapPx = with(density) { gap.toPx() }
            // On drag release, dragXPx clears immediately, but the new
            // selectedIndex only arrives after currentOnSelect's state change
            // round-trips back down through the caller -- for that gap (often
            // a full frame or more) restingXPx still reflected the OLD index,
            // so the indicator visibly snapped back toward where it used to
            // be before jerking again to the correct spot ("jumpy when you
            // let go"). pendingIndex holds the just-picked index as the
            // resting target the instant the drag ends, so there's nothing
            // to snap back to; it clears itself once the real prop catches up.
            var pendingIndex by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(selectedIndex) {
                if (pendingIndex == selectedIndex) pendingIndex = null
            }
            val restingXPx = (segWidthPx + gapPx) * (pendingIndex ?: selectedIndex)
            // A raw pixel Animatable driving graphicsLayer's translationX, not
            // animateDpAsState + Modifier.offset(x = ...dp) -- offset() moves
            // the layout position, so every single pixel of finger movement
            // during a drag forced a full relayout pass of this control. At
            // touch-sampling rates that read as visibly janky ("junky when
            // you're dragging your finger around on it") rather than a smooth
            // 1:1 follow. graphicsLayer's translation is a pure draw-phase
            // transform -- same visual position, no relayout.
            val indicatorXPx = remember { Animatable(restingXPx) }
            val targetXPx = dragXPx ?: restingXPx
            LaunchedEffect(targetXPx, dragXPx != null) {
                if (dragXPx != null) {
                    indicatorXPx.snapTo(targetXPx)
                } else {
                    // LowBouncy keeps a quick StiffnessMedium settle with just a
                    // light touch of overshoot (MediumBouncy wobbled too much on
                    // every change).
                    indicatorXPx.animateTo(
                        targetXPx,
                        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                    )
                }
            }
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

            // Motion blur on the indicator: subtle while still, intensifies during drag
            // and fades as the spring settles — gives the sliding highlight a fluid,
            // refractive feel without a real directional blur.
            val isMoving = dragXPx != null
            val motionBlurX by animateFloatAsState(
                targetValue = if (isMoving) 6f else 0f,
                animationSpec = if (isMoving) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                label = "segMotionBlur",
            )
            Box(
                Modifier
                    .width(segWidth)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = indicatorXPx.value }
                    .then(if (motionBlurX > 0.5f) Modifier.blur(motionBlurX.dp, 0.dp) else Modifier)
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
                                pendingIndex = idx
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
                    val isSelected = i == visualIndex
                    val fg by animateColorAsState(
                        if (isSelected) selectedTextColor else unselectedTextColor,
                        spring(stiffness = Spring.StiffnessMediumLow),
                        label = "segFg",
                    )
                    Box(
                        modifier = Modifier
                            .width(segWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            // The whole control's touch/drag handling lives in
                            // the parent Row's single pointerInput above (a
                            // real clickable/selectable here would register its
                            // own gesture detector and fight that custom drag
                            // logic), so this is a semantics-only, additive
                            // accessibility action -- TalkBack invokes it via
                            // the AccessibilityNodeInfo click action, which
                            // doesn't go through the normal touch dispatch
                            // pipeline the drag gesture depends on. Previously
                            // this control had NO accessibility semantics at
                            // all: unlabelled, unfocusable, state unannounced.
                            .semantics {
                                contentDescription = opt.label
                                role = Role.Tab
                                selected = isSelected
                                onClick(label = "Select ${opt.label}") {
                                    if (opt.key != currentSelectedKey) {
                                        currentOnTick()
                                        currentOnSelect(opt.key)
                                    }
                                    true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            opt.icon?.let { icon ->
                                Image(
                                    painter = rememberVectorPainter(icon),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(fg),
                                    modifier = Modifier.size(if (isSelected) 16.dp else 14.dp),
                                )
                                Spacer(Modifier.width(if (isSelected) 6.dp else 4.dp))
                            }
                            BasicText(
                                opt.label,
                                // BasicText doesn't consult LocalContentColor like Text()
                                // does, so the animated selected/unselected color has to be
                                // baked into the style here — it was computed as `fg` above
                                // but never actually reached the label, silently rendering
                                // at TextStyle's default (unspecified → black) regardless of
                                // selection or theme. Also lighter than the caller's style:
                                // Medium when selected reads as the active choice; Normal
                                // for the rest keeps the whole control visually quiet.
                                style = if (isSelected) textStyle.copy(color = fg, fontWeight = FontWeight.Medium)
                                        else textStyle.copy(color = fg, fontWeight = FontWeight.Normal, fontSize = textStyle.fontSize * 0.88f),
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
