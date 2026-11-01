package com.bloo.bluelink.wear

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The watch's one-way scroll and its pull to refresh.
 *
 * The pager only moves forward: a downward drag is taken on every page, so swiping down never goes back a page. On the face, at rest, that
 * downward drag is a pull: the face stretches under the finger, and letting go past [thresholdPx] asks the car for a fresh reading. Letting
 * go before that springs the face back with a little overshoot, which is the squash and stretch.
 */
@Stable
internal class WearPullRefresh(
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    /** True when the face is the page on screen and is at rest, so a downward drag is the face's pull. */
    private val canPull: () -> Boolean,
    private val onRefresh: () -> Unit,
) {
    /** How far the face has been pulled, in pixels, before resistance. Read in draw and layer code, so a pull redraws without recomposing. */
    var pulledPx by mutableFloatStateOf(0f)
        private set

    private var release: Job? = null

    /** How far the pull has got, as a share of the threshold (1 is the refresh). */
    fun progress(): Float = pulledPx / thresholdPx

    /** True while the face is the page on screen and at rest: the only place a pull refreshes and shows the refresh indicator. */
    fun onFace(): Boolean = canPull()

    /** The face's drop under the finger: pulls get harder the further they go, so it stretches rather than following one to one. */
    fun offsetPx(): Float = pulledPx * 0.5f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || !canPull()) return Offset.Zero
            // On the face at rest: nothing pulled and not pulling down is the pager's own scroll (upward).
            if (pulledPx <= 0f && available.y <= 0f) return Offset.Zero
            val next = (pulledPx + available.y).coerceAtLeast(0f)
            val used = next - pulledPx
            release?.cancel()
            pulledPx = next
            return Offset(0f, used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            // Every page: what its own content could not take is the stretch. A downward drag the list leaves over stretches the page, so
            // the pager never sees it and never goes back a page; an upward drag takes a stretch back first.
            val left = available.y - consumed.y
            if (left > 0f) {
                release?.cancel()
                pulledPx += left
                return Offset(0f, left)
            }
            if (left < 0f && pulledPx > 0f) {
                val back = minOf(-left, pulledPx)
                release?.cancel()
                pulledPx -= back
                return Offset(0f, -back)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // A downward fling is never the pager's: it is the stretch's, or nothing.
            val pulling = pulledPx > 0f
            if (pulling && onFace() && pulledPx >= thresholdPx) onRefresh()
            release = scope.launch {
                animate(pulledPx, 0f, initialVelocity = 0f, animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) { value, _ ->
                    pulledPx = value
                }
            }
            return if (pulling || available.y > 0f) Velocity(available.x, 0f) else Velocity.Zero
        }
    }
}

/**
 * The refresh indicator: an arc at the top of the screen that fills as the face is pulled, and stretches with it, so it squashes wide and
 * tall as the pull grows. While the car is being asked it spins and pulses. It is drawn in layers that read the pull directly, so a pull
 * moves it without recomposing the page.
 */
@Composable
internal fun BoxScope.WearPullIndicator(pull: WearPullRefresh, refreshing: Boolean, size: Dp = 26.dp) {
    val colour = MaterialTheme.colorScheme.primary
    if (refreshing) {
        val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "refreshSpin")
        val turn by spin.animateFloat(
            0f, 360f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing)),
            label = "refreshTurn",
        )
        val beat by spin.animateFloat(
            0.9f, 1.1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(450), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "refreshBeat",
        )
        Canvas(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(size).graphicsLayer { rotationZ = turn; scaleX = beat; scaleY = 1f / beat; alpha = if (pull.onFace()) 1f else 0f }) {
            drawArc(colour, startAngle = -90f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    } else {
        Canvas(
            Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(size).graphicsLayer {
                val p = pull.progress().coerceIn(0f, 1.25f)
                // Squash and stretch: it widens as it is pulled out and narrows as it is pulled tall.
                scaleX = 1f - 0.15f * p
                scaleY = 1f + 0.2f * p
                alpha = (pull.pulledPx / 12.dp.toPx()).coerceIn(0f, 1f) * if (pull.onFace()) 1f else 0f
            },
        ) {
            val sweep = 360f * pull.progress().coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(colour, startAngle = -90f, sweepAngle = sweep, useCenter = false, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
