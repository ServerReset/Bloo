package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val DismissDistance = 110.dp

/** How long the fly-off takes: quick, and speeding up as it goes, like something being thrown. */
private const val FlyOffMs = 220

/**
 * Horizontal swipe-to-dismiss for a toast: it follows the finger, then, past [DismissDistance], flies off the screen on the
 * side it was swiped to (all the way past the edge, not just out of sight), or springs back. The finger's position is a plain float written straight from
 * the gesture (no coroutine per delta); the Animatable only runs the fly-off or spring-back once the
 * finger lifts.
 *
 * A [Modifier] extension (not a bare factory) so it chains: see Compose's own convention for
 * anything returning a modifier.
 */
@Composable
internal fun Modifier.rememberSwipeToDismiss(key: Any, onDismiss: () -> Unit): Modifier {
    var dragging by remember(key) { mutableStateOf(false) }
    val dragPx = remember(key) { mutableFloatStateOf(0f) }
    val settle = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismissPx = with(LocalDensity.current) { DismissDistance.toPx() }
    // Far enough to be fully past the edge of the window, whichever side it started on.
    val flyPx = LocalWindowInfo.current.containerSize.width.toFloat() + dismissPx
    val offsetX by remember { derivedStateOf { if (dragging) dragPx.floatValue else settle.value } }
    fun release(flyOff: Boolean) {
        dragging = false
        val released = dragPx.floatValue
        scope.launch {
            settle.snapTo(released)
            if (flyOff && abs(released) > dismissPx) {
                settle.animateTo(if (released > 0) flyPx else -flyPx, tween(FlyOffMs, easing = FastOutLinearInEasing))
                onDismiss()
            } else {
                settle.animateTo(0f)
            }
        }
    }
    return this
        .offset { IntOffset(offsetX.roundToInt(), 0) }
        // Fades a little with the drag so a swipe reads as leaving, but stays solid enough to be seen crossing the screen.
        .graphicsLayer { alpha = (1f - abs(offsetX) / (flyPx * 1.6f)).coerceIn(0.4f, 1f) }
        .pointerInput(key) {
            detectHorizontalDragGestures(
                onDragStart = { dragging = true; dragPx.floatValue = settle.value },
                onHorizontalDrag = { change, amount -> change.consume(); dragPx.floatValue += amount },
                onDragEnd = { release(flyOff = true) },
                onDragCancel = { release(flyOff = false) },
            )
        }
}
