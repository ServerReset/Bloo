package com.bloo.bluelink.wear

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/*
 * The watch's pull-to-refresh, in the phone's own mechanic: pull down from the top of the face and a disc drops
 * in from above the display, its arc filling as you go and stretching a little past the point where letting go
 * will refresh; a tick marks that point; on release it springs to rest and spins until the refresh lands, then
 * retracts. Nothing happens if you let go before the point.
 */

/** How far a pull goes (the disc's travel) before letting go refreshes. */
private val PullDistance = 64.dp
private val DiscSize = 38.dp

/** Where the disc rests, measured from the top of the display (clear of the clock). */
private val RestTop = 30.dp

/** The pull's progress: 0..1 up to the arm point, past 1 beyond it (resisted below). */
class WearPullState {
    var pull by mutableFloatStateOf(0f)
    /** Where the pull was when the finger lifted, so the disc can carry on from there instead of restarting. */
    var lastPull by mutableFloatStateOf(0f)
    /** Set the instant a release arms a refresh, before the repository's own flag flips, so the disc stays put. */
    var requested by mutableStateOf(false)
}

/**
 * Watches a downward drag on this element without taking it (tiles, the pager and the crown all still work),
 * writes its progress to [state], and calls [onRefresh] when the finger lifts past the arm point.
 */
@Composable
fun Modifier.wearPullToRefresh(state: WearPullState, onRefresh: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val distancePx = with(LocalDensity.current) { PullDistance.toPx() }
    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // A pull starts in the upper part of the face (the tiles below are for tapping); a drag that begins low
            // is left alone, so scrolling down from a bottom tile cannot refresh by accident.
            if (down.position.y > size.height * 0.65f) {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                return@awaitEachGesture
            }
            var dx = 0f
            var dy = 0f
            var armed = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                dx += change.positionChange().x
                dy += change.positionChange().y
                // Only a mostly-vertical pull counts: a sideways swipe between cars is not a refresh.
                state.pull = if (dy > 0f && dy > 2f * kotlin.math.abs(dx)) dy / distancePx else 0f
                val nowArmed = state.pull >= 1f
                if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                armed = nowArmed
            } while (event.changes.any { it.pressed })
            state.lastPull = state.pull
            if (state.pull >= 1f) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                state.requested = true
                onRefresh()
            }
            state.pull = 0f
        }
    }
}

/** The disc itself, drawn over the face. Call inside a Box that fills the display. */
@Composable
fun BoxScope.WearPullIndicator(state: WearPullState, refreshing: Boolean) {
    val active = refreshing || state.requested
    // The real flag takes over from the request; and a refresh that never reports one must not hold the disc.
    LaunchedEffect(state.requested) {
        if (state.requested) {
            kotlinx.coroutines.delay(700)
            if (!refreshing) state.requested = false
        }
    }
    LaunchedEffect(refreshing) { if (refreshing) state.requested = false }
    fun fingerOf(p: Float): Float {
        val over = (p - 1f).coerceAtLeast(0f)
        return p.coerceIn(0f, 1f) + over / (1f + over) * 0.35f
    }
    val raw = state.pull.coerceIn(0f, 1f)
    val over = (state.pull - 1f).coerceAtLeast(0f)
    val finger = fingerOf(state.pull)
    val dragging = state.pull > 0f
    val settle = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            // From where the finger left it (it is already back to 0 by now), not from the top.
            if (settle.value < 0.01f) settle.snapTo(fingerOf(state.lastPull))
            settle.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
        } else if (settle.value > 0.01f) {
            settle.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // A pull let go before the arm point springs back from where the finger left it.
    LaunchedEffect(dragging) {
        if (!dragging && !active && state.lastPull > 0.01f) {
            settle.snapTo(fingerOf(state.lastPull))
            settle.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    // Driven by the finger 1:1 while pulling; by the spring otherwise (a refresh, or springing back).
    val t = if (dragging && !active) finger else settle.value
    if (t <= 0.01f) return
    val density = LocalDensity.current
    val sizePx = with(density) { DiscSize.toPx() }
    val restPx = with(density) { RestTop.toPx() }
    val scheme = MaterialTheme.colorScheme
    val spin = if (active) {
        rememberInfiniteTransition(label = "pullSpin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "pullSpinAngle",
        ).value
    } else 0f
    val stretch = over / (1f + over)
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .size(DiscSize)
            .graphicsLayer {
                // Drops from off the top edge and lands below the clock; past the arm point it stretches.
                translationY = -sizePx + t * (sizePx + restPx)
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleY = 1f + 0.4f * stretch
            }
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f), CircleShape)
            .wearGlassRim(CircleShape)
            .padding(8.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val sweep = if (active) 110f else 300f * raw
            drawArc(
                color = scheme.primary,
                startAngle = -90f + spin,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

