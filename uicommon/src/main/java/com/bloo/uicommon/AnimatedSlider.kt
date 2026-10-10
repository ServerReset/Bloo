package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Bloo's hand-drawn slider (track, thumb, ticks, gestures). Colour, haptic and motion context
 * come in as parameters so the module stays theme-neutral.
 *
 * @param reduceMotion When true the settle spring is replaced by a snap so the
 *   thumb jumps to its step immediately instead of bouncing into place.
 * @param onStepTick Called every time the dragged value crosses a step boundary.
 * @param onSettle Called once when the drag is released and the thumb settles.
 */
@Composable
fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    inactiveColor: Color,
    dotOnActive: Color,
    dotOnInactive: Color,
    reduceMotion: Boolean,
    onStepTick: () -> Unit,
    onSettle: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Tracked via onSizeChanged below (Compose only knows this after the first layout pass), since
    // every gesture-to-value conversion needs the control's actual pixel width to turn a touch
    // x-coordinate into a fraction of the track.
    var widthPx by remember { mutableFloatStateOf(0f) }

    // The single source of truth for the thumb/track's rendered position. Driven either by a live
    // drag (snapTo, 1:1 with the finger) or by a settle spring (animateTo) once the finger lifts;
    // the Canvas below reads anim.value every frame to draw the thumb without needing a
    // recomposition per frame.
    val anim = remember { Animatable(value) }
    var dragging by remember { mutableStateOf(false) }
    var prevStep by remember { mutableFloatStateOf(snapToStep(value, valueRange, steps)) }
    // settleTo() below calls onValueChange(target) synchronously, which recomposes with the new
    // `value` and re-triggers this effect, racing the scope.launch{} bounce animation settleTo just
    // started: if this effect's snapTo(value) runs before that launched coroutine has actually
    // started animating (a scheduling gap, not a guaranteed ordering), it jumps anim.value straight
    // to the target and the just-started spring then animates from target to target -- a no-op that
    // reads as the bounce snapping partway through instead of completing. isRunning alone can't
    // detect this window reliably since it doesn't flip true until the launched coroutine actually
    // starts; an explicit flag set synchronously inside settleTo (before any suspension point)
    // closes it.
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(value) {
        if (!dragging && !settling && !anim.isRunning && anim.value != value) anim.snapTo(value)
    }

    // 0 at rest, 1 while held: the thumb narrows, glows, and nearby dots swell.
    val press by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "sliderPress",
    )
    val trackThickness = 14.dp
    val thumbW = 6.dp
    val thumbH = 44.dp
    val gap = 6.dp
    val dotR = 2.5.dp
    val edgePad = 14.dp
    val edgePadPx = with(density) { edgePad.toPx() }

    // Touch x (px) to a value on [valueRange], excluding edgePad insets. Not step-snapped.
    fun rawForX(x: Float): Float {
        val travel = (widthPx - 2 * edgePadPx).coerceAtLeast(1f)
        val frac = (x - edgePadPx) / travel
        return valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
    }
    // Per-move drag handler: the rendered anim value may overshoot by EdgeOverscrollFraction for an elastic
    // edge while the reported value stays clamped; fires onStepTick on each step boundary (per-notch haptic).
    fun trackTo(x: Float) {
        val raw = rawForX(x)
        val span = (valueRange.endInclusive - valueRange.start)
        // Past either end the handle rubber-bands (see [rubberBand]) and the track stretches with it.
        val over = span * EdgeOverscrollFraction
        val visual = when {
            raw < valueRange.start -> valueRange.start - rubberBand(valueRange.start - raw, over)
            raw > valueRange.endInclusive -> valueRange.endInclusive + rubberBand(raw - valueRange.endInclusive, over)
            else -> raw
        }
        scope.launch { anim.snapTo(visual) }
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        val s = snapToStep(clamped, valueRange, steps)
        if (steps > 0 && s != prevStep) {
            onStepTick()
            prevStep = s
        }
        // Free-flow during drag; the snapped step is applied on settle.
        onValueChange(clamped)
    }
    // Commits the final step-snapped [target], then animates (or snaps under reduceMotion) the thumb with a
    // bouncy spring. Cancels any settle in flight so two springs never write `anim` at once.
    fun settleTo(target: Float) {
        prevStep = target
        settling = true
        // onValueChange before onSettle: callers read the last value inside onSettle, and a plain tap never called onValueChange.
        onValueChange(target)
        onSettle()
        settleJob?.cancel()
        settleJob = scope.launch {
            // finally so `settling` resets when a new drag cancels this settle (CancellationException).
            try {
                if (reduceMotion) {
                    anim.snapTo(target)
                } else {
                    anim.animateTo(
                        target,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                    )
                }
            } finally {
                settling = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(thumbH)
            // No motion blur: a blurred render target reads as the slider going low-resolution.
            .onSizeChanged { widthPx = it.width.toFloat() }
            // Same tap-vs-drag disambiguation as MorphSegmented's gesture handler: `claimed` starts
            // false (undecided) and only flips true once horizontal movement exceeds touch slop and
            // dominates vertical movement, at which point this becomes a drag and every subsequent
            // move updates the thumb live via trackTo().
            .pointerInput(valueRange, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Released before claiming: a tap, settle at the release point.
                            if (!claimed) {
                                change.consume()
                                settleTo(snapToStep(rawForX(down.position.x), valueRange, steps))
                            }
                            break
                        }
                        if (!claimed) {
                            val dx = abs(change.position.x - down.position.x)
                            val dy = abs(change.position.y - down.position.y)
                            when {
                                dx > slop && dx >= dy -> {
                                    claimed = true
                                    dragging = true
                                    change.consume()
                                    trackTo(change.position.x)
                                }
                                dy > slop -> break
                            }
                        } else if (change.positionChanged()) {
                            // Already dragging: keep the thumb tracking the finger every frame via
                            // trackTo's free-flow + overshoot logic.
                            trackTo(change.position.x)
                            change.consume()
                        }
                    }
                    // Drag ended: settle at the step the final anim.value quantizes to.
                    if (claimed) {
                        dragging = false
                        settleTo(snapToStep(anim.value, valueRange, steps))
                    }
                }
            }
            // The LOGICAL value, not anim.value: reading the Animatable here invalidated
            // composition on every frame of a drag or settle bounce just to keep semantics fresh
            // (the Canvas below reads anim.value in its own draw scope, which redraws without
            // recomposing). The stepped value is also what assistive tech should announce.
            .progressSemantics(value, valueRange, steps)
            // progressSemantics alone only publishes the value for announcement (read-only, meant
            // for plain progress indicators) -- this control is adjustable, so without setProgress
            // a screen-reader user could hear the current value but had no supported way to change
            // it (touch-exploration intercepts the raw drag gesture the pointerInput above depends
            // on).
            .semantics {
                setProgress { target ->
                    settleTo(snapToStep(target, valueRange, steps))
                    true
                }
            },
    ) {
        // Everything the slider looks like is hand-drawn here each frame: an inactive (remaining)
        // track segment, an active (traveled) track segment, optional step dots, and the thumb
        // itself -- all positioned from a single `frac` derived from anim.value, so reading
        // anim.value in this draw scope (rather than in a @Composable read further up) means
        // dragging/settling repaints without triggering a recomposition of this whole function.
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(thumbH),
        ) {
            // Thumb position as a 0..1 fraction of valueRange.
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val frac = (anim.value - valueRange.start) / span
            val halfThumb = thumbW.toPx() / 2f
            val gapPx = gap.toPx()
            val padPx = edgePad.toPx()
            // Usable travel for the thumb centre.
            val travel = (size.width - 2 * padPx).coerceAtLeast(0f)
            // Thumb centre x.
            val thumbX = padPx + travel * frac
            val cy = size.height / 2f
            val th = trackThickness.toPx()
            val top = cy - th / 2f
            val radius = CornerRadius(th / 2f)
            // Inactive track: from just past the thumb's right edge (thumbX + cut) to the far right
            // end of the control. Only drawn if there's room left -- i.e. the thumb isn't already
            // sitting at (or past) the far right edge.
            drawRoundRect(inactiveColor, topLeft = Offset(0f, top), size = Size(size.width, th), cornerRadius = radius)
            if (thumbX > 0f) {
                clipRect(left = 0f, top = 0f, right = thumbX, bottom = size.height) {
                    drawRoundRect(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.82f), accent),
                            startX = 0f,
                            endX = thumbX.coerceAtLeast(1f),
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, th),
                        cornerRadius = radius,
                    )
                }
            }
            if (steps > 0) {
                val n = steps + 2
                val rPx = dotR.toPx()
                // A crowded slider draws every k-th dot so the track never turns into a dotted
                // line.
                val every = ((n * (dotR.toPx() * 5f)) / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    if (i % every != 0 && i != n - 1) continue
                    val tf = i.toFloat() / (n - 1)
                    val x = padPx + travel * tf
                    if (abs(x - thumbX) < halfThumb + 4.dp.toPx()) continue
                    // Near the held thumb the dots swell.
                    val near = (1f - abs(x - thumbX) / (padPx * 4f)).coerceIn(0f, 1f)
                    drawCircle(if (x <= thumbX) dotOnActive else dotOnInactive, rPx * (1f + 0.7f * near * press), Offset(x, cy))
                }
            }
            // A glow under the thumb while held, then the thumb, narrowing as it is pressed.
            if (press > 0.01f) {
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.38f * press), Color.Transparent),
                        center = Offset(thumbX, cy),
                        radius = 30.dp.toPx(),
                    ),
                    radius = 30.dp.toPx(),
                    center = Offset(thumbX, cy),
                )
            }
            val twPx = thumbW.toPx() * (1f - 0.25f * press)
            drawRoundRect(
                accent,
                topLeft = Offset(thumbX - twPx / 2f, -2.dp.toPx() * press),
                size = Size(twPx, size.height + 4.dp.toPx() * press),
                cornerRadius = CornerRadius(twPx / 2f),
            )
        }
    }
}

/**
 * Quantizes [v] to the nearest of [steps] evenly-spaced increments across [range]. With `steps`
 * intermediate stops, the range is divided into `steps + 1` equal-sized increments (so a slider
 * with 1 step has 3 valid positions: start, midpoint and end; 0 steps means no quantization at all,
 * just a plain clamp to the range).
 */
fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + (v - range.start).div(inc).roundToInt() * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}
