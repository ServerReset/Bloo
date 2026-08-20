package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
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
 * Bloo's fully custom hand-drawn slider. Shared between phone (via :app) and
 * watch (via :wear) so the track/thumb/tick rendering and gesture logic live in
 * exactly one place.
 *
 * Callers supply all colour/haptic/motion context as parameters so this module
 * stays neutral to compose.material3 vs wear.compose.material3.
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
    // Tracked via onSizeChanged below (Compose only knows this after the first
    // layout pass), since every gesture-to-value conversion needs the control's
    // actual pixel width to turn a touch x-coordinate into a fraction of the track.
    var widthPx by remember { mutableFloatStateOf(0f) }

    // The single source of truth for the thumb/track's rendered position. Driven
    // either by a live drag (snapTo, 1:1 with the finger) or by a settle spring
    // (animateTo) once the finger lifts; the Canvas below reads anim.value every
    // frame to draw the thumb without needing a recomposition per frame.
    val anim = remember { Animatable(value) }
    var dragging by remember { mutableStateOf(false) }
    var prevStep by remember { mutableFloatStateOf(snapToStep(value, valueRange, steps)) }
    // settleTo() below calls onValueChange(target) synchronously, which recomposes
    // with the new `value` and re-triggers this effect, racing the scope.launch{}
    // bounce animation settleTo just started: if this effect's snapTo(value) runs
    // before that launched coroutine has actually started animating (a scheduling
    // gap, not a guaranteed ordering), it jumps anim.value straight to the target
    // and the just-started spring then animates from target to target -- a no-op
    // that reads as the bounce snapping partway through instead of completing.
    // isRunning alone can't detect this window reliably since it doesn't flip
    // true until the launched coroutine actually starts; an explicit flag set
    // synchronously inside settleTo (before any suspension point) closes it.
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(value) {
        if (!dragging && !settling && !anim.isRunning && anim.value != value) anim.snapTo(value)
    }

    val trackThickness = 14.dp
    val thumbW = 6.dp
    val thumbH = 44.dp
    val gap = 6.dp
    val dotR = 2.5.dp
    val edgePad = 14.dp
    val edgePadPx = with(density) { edgePad.toPx() }

    // Converts a raw touch x-coordinate (pixels, relative to this control) into a
    // value on [valueRange], accounting for edgePad insets on both sides so the
    // thumb's usable travel distance excludes the padding. Not step-snapped --
    // callers snap separately depending on whether they want free-flow (trackTo)
    // or quantized (settleTo) behaviour.
    fun rawForX(x: Float): Float {
        val travel = (widthPx - 2 * edgePadPx).coerceAtLeast(1f)
        val frac = (x - edgePadPx) / travel
        return valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
    }
    // Called on every pointer-move while a drag is in progress. Lets the thumb
    // "free-flow" with the finger rather than jumping between discrete steps mid-
    // drag: the visually-rendered anim value is allowed a small overshoot (4.5% of
    // the range) past either end so dragging past an edge feels elastic rather than
    // hard-stopping, while the value reported to onValueChange stays clamped inside
    // the real range. Also fires onStepTick whenever the drag crosses a step
    // boundary (compared against prevStep), independent of anim's own smoothing --
    // this is what drives the per-notch haptic while dragging.
    fun trackTo(x: Float) {
        val raw = rawForX(x)
        val span = (valueRange.endInclusive - valueRange.start)
        val overshoot = span * 0.045f
        val visual = raw.coerceIn(valueRange.start - overshoot, valueRange.endInclusive + overshoot)
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
    // Called once a drag ends (or on a plain tap) with the final, already
    // step-snapped [target] value. Commits the logical value to the caller first,
    // then animates (or snaps, under reduceMotion) the visual thumb into that
    // resting position with a bouncy settle spring. Cancels any settle already in
    // flight (settleJob) so rapid taps/drags don't leave two competing springs
    // both writing to `anim` at once.
    fun settleTo(target: Float) {
        prevStep = target
        settling = true
        // onValueChange BEFORE onSettle: callers commonly track "the last value
        // we saw" in their own onValueChange closure and read it back inside
        // onSettle (see Screens.kt's AnimatedSlider wrapper). A plain tap never
        // calls onValueChange before settleTo (only a drag does, via trackTo),
        // so firing onSettle first meant those callers read a stale value from
        // before this interaction — this order guarantees it's already current.
        onValueChange(target)
        onSettle()
        settleJob?.cancel()
        settleJob = scope.launch {
            // finally (not a trailing statement) so `settling` is reset even when a
            // new drag's snapTo cancels this settle via the shared MutatorMutex --
            // animateTo/snapTo then throws CancellationException, and without the
            // finally the control would stay stuck at settling=true (blurred, value
            // sync blocked) for the whole interrupting drag.
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

    // Mirrors MorphSegmented's motion-blur trick: jumps to a blur amount instantly
    // (snap()) the moment a settle bounce begins, then eases back to zero via a
    // spring as the bounce finishes, giving the thumb's post-release wobble a soft
    // motion-blur look instead of a hard-edged bounce.
    val settleBlur by animateFloatAsState(
        targetValue = if (settling) 4f else 0f,
        animationSpec = if (settling) snap() else spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "settleBlur",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(thumbH)
            // A deferred graphicsLayer read, not the previous body-level
            // `.then(if (settleBlur > 0.5f) Modifier.blur(...) else Modifier)` --
            // that read settleBlur (an animateFloatAsState value ticking every
            // frame of the post-release settle bounce) directly in the
            // composable body, recomposing this whole Box every one of those
            // frames instead of just redrawing it -- the exact anti-pattern
            // this file's own Canvas draw below (which reads anim.value inside
            // its DrawScope) exists specifically to avoid.
            .graphicsLayer {
                renderEffect = if (settleBlur > 0.5f) {
                    BlurEffect(settleBlur, settleBlur, TileMode.Clamp)
                } else {
                    null
                }
            }
            // Captures the control's actual laid-out pixel width once it's known,
            // feeding widthPx (used by rawForX above to convert touch x-coordinates
            // into slider values).
            .onSizeChanged { widthPx = it.width.toFloat() }
            // Same tap-vs-drag disambiguation as MorphSegmented's gesture handler:
            // `claimed` starts false (undecided) and only flips true once horizontal
            // movement exceeds touch slop and dominates vertical movement, at which
            // point this becomes a drag and every subsequent move updates the thumb
            // live via trackTo(). A release while still undecided is a tap, jumping
            // straight to that x position. Confirmed vertical movement before
            // claiming cedes the gesture entirely (breaks without consuming) so an
            // ancestor scrollable still works over this control.
            .pointerInput(valueRange, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Finger lifted before a drag was ever claimed: treat as a
                            // tap at the release point and settle straight there.
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
                            // Already dragging: keep the thumb tracking the finger every
                            // frame via trackTo's free-flow + overshoot logic.
                            trackTo(change.position.x)
                            change.consume()
                        }
                    }
                    // Drag ended (loop exited via `break` after `pressed` went false):
                    // commit whichever step the final anim.value quantizes to as the
                    // settled resting value.
                    if (claimed) {
                        dragging = false
                        settleTo(snapToStep(anim.value, valueRange, steps))
                    }
                }
            }
            // The LOGICAL value, not anim.value: reading the Animatable here
            // invalidated composition on every frame of a drag or settle bounce
            // just to keep semantics fresh (the Canvas below reads anim.value in
            // its own draw scope, which redraws without recomposing). The stepped
            // value is also what assistive tech should announce.
            .progressSemantics(value, valueRange, steps)
            // progressSemantics alone only publishes the value for announcement
            // (read-only, meant for plain progress indicators) -- this control
            // is adjustable, so without setProgress a screen-reader user could
            // hear the current value but had no supported way to change it
            // (touch-exploration intercepts the raw drag gesture the pointerInput
            // above depends on). TalkBack's adjust gesture computes its own step
            // size from progressSemantics' steps and calls this directly.
            .semantics {
                setProgress { target ->
                    settleTo(snapToStep(target, valueRange, steps))
                    true
                }
            },
    ) {
        // Everything the slider looks like is hand-drawn here each frame: an
        // inactive (remaining) track segment, an active (traveled) track segment,
        // optional step dots, and the thumb itself -- all positioned from a single
        // `frac` derived from anim.value, so reading anim.value in this draw scope
        // (rather than in a @Composable read further up) means dragging/settling
        // repaints without triggering a recomposition of this whole function.
        Canvas(Modifier.fillMaxWidth().height(thumbH)) {
            // Where the thumb sits along the track, as a 0..1 fraction of valueRange.
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val frac = (anim.value - valueRange.start) / span
            val halfThumb = thumbW.toPx() / 2f
            val gapPx = gap.toPx()
            val padPx = edgePad.toPx()
            // Usable horizontal travel distance for the thumb centre, after
            // subtracting the edge padding on both sides.
            val travel = (size.width - 2 * padPx).coerceAtLeast(0f)
            // Absolute pixel x-position of the thumb's centre.
            val thumbX = padPx + travel * frac
            val cy = size.height / 2f
            val th = trackThickness.toPx()
            val top = cy - th / 2f
            val radius = CornerRadius(th / 2f)
            // Half-gap kept clear on either side of the thumb so the track segments
            // (and step dots) don't visually collide with it.
            val cut = halfThumb + gapPx

            // Inactive track: from just past the thumb's right edge (thumbX + cut) to
            // the far right end of the control. Only drawn if there's room left --
            // i.e. the thumb isn't already sitting at (or past) the far right edge.
            val inStart = (thumbX + cut).coerceAtMost(size.width)
            if (inStart < size.width) {
                drawRoundRect(
                    inactiveColor,
                    topLeft = Offset(inStart, top),
                    size = Size(size.width - inStart, th),
                    cornerRadius = radius,
                )
            }
            // Active (traveled) track: from the left edge of the control up to just
            // before the thumb's left edge (thumbX - cut). Mirror of the inactive
            // segment above, only drawn if there's room left before the thumb.
            val acEnd = (thumbX - cut).coerceAtLeast(0f)
            if (acEnd > 0f) {
                drawRoundRect(
                    accent,
                    topLeft = Offset(0f, top),
                    size = Size(acEnd, th),
                    cornerRadius = radius,
                )
            }
            // Step dots: one at each snap position plus the two endpoints (n = steps
            // + 2), evenly spaced across the full travel distance. A dot within `cut`
            // pixels of the thumb is skipped entirely so it doesn't peek out from
            // underneath the thumb; each remaining dot is tinted "already passed"
            // (dotOnActive) or "still ahead" (dotOnInactive) based on which side of
            // thumbX it falls on.
            if (steps > 0) {
                val n = steps + 2
                val rPx = dotR.toPx()
                for (i in 0 until n) {
                    val tf = i.toFloat() / (n - 1)
                    val x = padPx + travel * tf
                    if (abs(x - thumbX) < cut) continue
                    drawCircle(if (x <= thumbX) dotOnActive else dotOnInactive, rPx, Offset(x, cy))
                }
            }
            // The thumb itself: a tall, narrow, fully-rounded (pill-shaped, since its
            // corner radius equals half its own width) rectangle centred on thumbX and
            // spanning the full height of the control.
            val twPx = thumbW.toPx()
            drawRoundRect(
                accent,
                topLeft = Offset(thumbX - twPx / 2f, 0f),
                size = Size(twPx, size.height),
                cornerRadius = CornerRadius(twPx / 2f),
            )
        }
    }
}

/**
 * Quantizes [v] to the nearest of [steps] evenly-spaced increments across
 * [range]. With `steps` intermediate stops, the range is divided into
 * `steps + 1` equal-sized increments (so a slider with 1 step has 3 valid
 * positions: start, midpoint and end; 0 steps means no quantization at all,
 * just a plain clamp to the range). Works by dividing the offset from
 * range.start by the increment size, rounding to the nearest whole increment, then
 * multiplying back and clamping — the same "round to nearest multiple"
 * technique used for indexFor()-style quantization elsewhere in this module.
 */
fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + (v - range.start).div(inc).roundToInt() * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}
