package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spinner's size inside the disc. */
private val SpinnerDp = 44.dp

/**
 * The pull-to-refresh disc, shared by the phone and the watch. It drops from off the top edge as the pull grows and settles at [restPx]; past
 * its arm point the pull keeps going, resisted, and the disc stretches downward; releasing springs it back onto the rest point. A refresh in
 * flight ([active]) holds it there with the spinner, and it springs away when the refresh lands.
 *
 * The disc itself is drawn by [surface], so each app supplies its own material: the phone's glass, the watch's translucent disc. The motion is
 * the same everywhere.
 *
 * @param pull 0 at rest, 1 at the point a release arms a refresh, past 1 while the pull is resisted (see [pullFingerPosition]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullRefreshDisc(
    pull: Float,
    active: Boolean,
    restPx: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    surface: @Composable (Modifier, @Composable () -> Unit) -> Unit,
) {
    // The disc is driven directly by the finger while pulling (1:1, no spring lag): a pull that trails the finger on a spring reads as
    // disconnected. Only the refreshing endpoint and the retract use a spring.
    val finger = pullFingerPosition(pull)
    val refreshSpring = remember { Animatable(0f) }
    // True only while the disc springs away after a refresh lands, so the retract is this spring rather than the pull's own zero-ing.
    var retracting by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            retracting = false
            // Continue from where the finger left the disc, including an over-pull below the rest point, so it springs up onto the rest point.
            if (refreshSpring.value < 0.01f) refreshSpring.snapTo(finger)
            refreshSpring.animateTo(1f, bounceSpring(stiffness = Spring.StiffnessLow))
        } else if (refreshSpring.value > 0.01f) {
            retracting = true
            refreshSpring.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            retracting = false
        }
    }
    // While a refresh is in flight, or the disc is springing away, the spring owns the disc; otherwise it follows the finger.
    val t = if (active || retracting) refreshSpring.value else finger
    val over = (pull - 1f).coerceAtLeast(0f)
    val liveStretch = over / (1f + over)
    val releaseStretch by animateFloatAsState(
        if (active || retracting) 0f else liveStretch,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
        label = "refreshStretch",
    )
    val stretch = if (active || retracting) releaseStretch else liveStretch
    if (t <= 0.01f) return

    val sizePx = with(LocalDensity.current) { size.toPx() }
    Box(modifier.fillMaxSize()) {
        surface(
            Modifier.align(Alignment.TopCenter).size(size).graphicsLayer {
                // Drops from off the top edge as the pull grows, and lands at the rest point.
                translationY = -sizePx + t * (sizePx + restPx)
                // Past the arm point the disc stretches downward, then springs back on release.
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleY = 1f + 0.45f * stretch
            },
        ) {
            if (active || retracting) {
                LoadingIndicator(Modifier.size(SpinnerDp))
            } else {
                // While pulling, the spinner's own progress follows the pull exactly.
                LoadingIndicator(progress = { pull.coerceIn(0f, 1f) }, modifier = Modifier.size(SpinnerDp))
            }
        }
    }
}
