package com.bloo.bluelink.wear

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.PullRefreshDisc
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape

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
 * The watch's pull-to-refresh indicator: the shared disc (see [PullRefreshDisc]), drawn as a translucent disc. It shows only on the face. It
 * sits in its own composable, so a drag moves it without recomposing the page around it.
 */
@Composable
internal fun BoxScope.WearPullDisc(pull: WearPullRefresh, refreshing: Boolean, backdrop: HazeState) {
    val density = LocalDensity.current
    val restPx = with(density) { DiscRestDp.toPx() }
    Box(
        Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = if (pull.onFace()) 1f else 0f },
    ) {
        PullRefreshDisc(pull = pull.progress(), active = refreshing, restPx = restPx, size = DiscDp) { discModifier, content ->
            WearGlassDisc(discModifier, content, backdrop)
        }
    }
}

/** The disc's size, and where it rests below the top of the display, clear of the time. */
private val DiscDp = 46.dp
private val DiscRestDp = 40.dp

/** The watch's disc: a translucent glass pane with a light rim, since the watch has no blur to draw with. */
@Composable
private fun WearGlassDisc(modifier: Modifier, content: @Composable () -> Unit, backdrop: HazeState) {
    Box(
        modifier
            .clip(CircleShape)
            .wearBlur(backdrop)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}
