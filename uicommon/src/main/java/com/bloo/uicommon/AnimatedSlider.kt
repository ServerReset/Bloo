package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    var widthPx by remember { mutableFloatStateOf(0f) }

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

    fun rawForX(x: Float): Float {
        val travel = (widthPx - 2 * edgePadPx).coerceAtLeast(1f)
        val frac = (x - edgePadPx) / travel
        return valueRange.start + frac * (valueRange.endInclusive - valueRange.start)
    }
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
        onValueChange(s)
    }
    fun settleTo(target: Float) {
        prevStep = target
        onSettle()
        settling = true
        onValueChange(target)
        settleJob?.cancel()
        settleJob = scope.launch {
            if (reduceMotion) {
                anim.snapTo(target)
            } else {
                anim.animateTo(
                    target,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                )
            }
            settling = false
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(thumbH)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(valueRange, steps) {
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
                            trackTo(change.position.x)
                            change.consume()
                        }
                    }
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
            .progressSemantics(value, valueRange, steps),
    ) {
        Canvas(Modifier.fillMaxWidth().height(thumbH)) {
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
            val frac = (anim.value - valueRange.start) / span
            val halfThumb = thumbW.toPx() / 2f
            val gapPx = gap.toPx()
            val padPx = edgePad.toPx()
            val travel = (size.width - 2 * padPx).coerceAtLeast(0f)
            val thumbX = padPx + travel * frac
            val cy = size.height / 2f
            val th = trackThickness.toPx()
            val top = cy - th / 2f
            val radius = CornerRadius(th / 2f)
            val cut = halfThumb + gapPx

            val inStart = (thumbX + cut).coerceAtMost(size.width)
            if (inStart < size.width) {
                drawRoundRect(
                    inactiveColor,
                    topLeft = Offset(inStart, top),
                    size = Size(size.width - inStart, th),
                    cornerRadius = radius,
                )
            }
            val acEnd = (thumbX - cut).coerceAtLeast(0f)
            if (acEnd > 0f) {
                drawRoundRect(
                    accent,
                    topLeft = Offset(0f, top),
                    size = Size(acEnd, th),
                    cornerRadius = radius,
                )
            }
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

fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return v.coerceIn(range.start, range.endInclusive)
    val inc = (range.endInclusive - range.start) / (steps + 1)
    val snapped = range.start + (v - range.start).div(inc).roundToInt() * inc
    return snapped.coerceIn(range.start, range.endInclusive)
}
