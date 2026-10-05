package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    // Set via onSizeChanged; needed to turn a touch x into a track fraction.
    var widthPx by remember { mutableFloatStateOf(0f) }

    // Source of truth for the thumb position: snapTo during a drag, animateTo when settling; the Canvas reads it in the draw phase only.
    val anim = remember { Animatable(value) }
    var dragging by remember { mutableStateOf(false) }
    var prevStep by remember { mutableFloatStateOf(snapToStep(value, valueRange, steps)) }
    // settleTo() calls onValueChange synchronously, which re-triggers this effect; `settling` (set before
    // any suspension) stops its snapTo racing the just-launched bounce, as isRunning flips late.
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
            .blockPageSwipe()
            .fillMaxWidth()
            .height(thumbH)
            // No motion blur: a blurred render target reads as the slider going low-resolution.
            .onSizeChanged { widthPx = it.width.toFloat() }
            // Tap-vs-drag as in MorphSegmented: `claimed` flips once horizontal movement passes slop and dominates;
            // release while undecided is a tap; vertical movement cedes to an ancestor scrollable.
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
                            // Dragging: track the finger.
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
            // The LOGICAL value, not anim.value: reading the Animatable would recompose every frame; also what assistive tech announces.
            .progressSemantics(value, valueRange, steps)
            // progressSemantics is read-only; setProgress makes it adjustable for TalkBack (touch exploration intercepts the drag).
            .semantics {
                setProgress { target ->
                    settleTo(snapToStep(target, valueRange, steps))
                    true
                }
            },
    ) {
        // Hand-drawn each frame from one `frac` of anim.value, read in the draw scope so dragging does not recompose.
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
            // Half-gap kept clear around the thumb.
            val cut = halfThumb + gapPx

            // One continuous track behind the handle: the whole pill inactive, then the accent drawn over it, clipped left of the handle.
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
                // A crowded slider draws every k-th dot.
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
 * Quantizes [v] to the nearest of [steps] evenly-spaced intermediate stops across [range]
 * (`steps + 1` equal increments); 0 steps is a plain clamp.
 */
fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + (v - range.start).div(inc).roundToInt() * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}
