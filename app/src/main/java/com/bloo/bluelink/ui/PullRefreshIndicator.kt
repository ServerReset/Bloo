package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.PullRefreshState
import com.bloo.uicommon.bounceSpring
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.bloo.uicommon.LocalHaptics

/**
 * The app's ONE pull-to-refresh indicator, drawn as a single floating overlay at the app root
 * rather than per screen.
 */
internal class RefreshIndicatorState : PullRefreshState() {
    // Several Refreshables are alive at once (the pager keeps neighbour car pages composed), so "is
    // a refresh in flight" is an OR across them, tracked by identity rather than a single bool one
    // page could clear while another is still working.
    private val refreshingKeys = mutableStateListOf<Any>()

    /** True while ANY page has a refresh in flight. */
    val refreshing: Boolean get() = refreshingKeys.isNotEmpty()

    fun setRefreshing(key: Any, on: Boolean) {
        if (on) { if (key !in refreshingKeys) refreshingKeys.add(key) } else refreshingKeys.remove(key)
    }
}

/** The one app-wide [RefreshIndicatorState]. Null only if no host is mounted (a preview). */
internal val LocalRefreshIndicator =
    staticCompositionLocalOf<RefreshIndicatorState?> { null }

private val IndicatorSize = 52.dp
private val SpinnerSize = 44.dp

/** How far below the status bar's lower edge the indicator rests. */
private val RestBelowStatusBar = 12.dp

/** How much further (in disc-travel fractions) a pull past the arm point keeps going, resisted. */
private const val OverPullTravel = 0.35f

/** The single floating indicator. */
@Composable
internal fun PullRefreshIndicatorHost(state: RefreshIndicatorState, hazeState: HazeState?, modifier: Modifier = Modifier) {
    // Held once the pull is released into a refresh (either the real flag or the synchronous
    // request), driven by the finger otherwise.
    val active = state.refreshing || state.requested
    // Past the arm point the pull keeps travelling further, but resisted (a rubber band), so the disc
    // can be pulled BELOW its resting spot and then springs back up onto it on release.
    val pullRaw = state.pull
    val raw = pullRaw.coerceIn(0f, 1f)
    val over = (pullRaw - 1f).coerceAtLeast(0f)
    val finger = raw + over / (1f + over) * OverPullTravel
    // The disc is driven DIRECTLY by the finger while pulling (1:1, no spring lag), and only
    // springs for the refreshing endpoint -- a pull that trails the finger on a spring reads as
    // disconnected.
    val refreshSpring = remember { Animatable(0f) }
    // True only while the disc springs away after a refresh lands, so that retract is our own smooth
    // spring rather than whatever the underlying pull state does on its way back to zero.
    var retracting by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            retracting = false
            // Continue from wherever the finger left the disc -- INCLUDING an over-pull below the
            // resting spot -- so the spring visibly bounces up onto 1 instead of easing flat.
            if (refreshSpring.value < 0.01f) refreshSpring.snapTo(finger)
            // Bouncy: an over-pull (dragged below the resting spot) springs back up and settles,
            // rather than easing flat.
            refreshSpring.animateTo(1f, bounceSpring(stiffness = Spring.StiffnessLow))
        } else if (refreshSpring.value > 0.01f) {
            retracting = true
            refreshSpring.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            retracting = false
        }
    }
    // The real flag takes over from the synchronous request...
    LaunchedEffect(state.refreshing) { if (state.refreshing) state.requested = false }
    // ...and if a caller's refresh never sets one at all, don't hold the disc forever.
    LaunchedEffect(state.requested) {
        if (state.requested) {
            delay(700)
            if (!state.refreshing) state.requested = false
        }
    }
    // A light tick the moment the pull crosses the point where releasing arms a refresh, and again
    // if you ease back under it and pull again.
    val haptics = LocalHaptics.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.pull }.collect { p ->
            val nowArmed = p >= 1f
            if (nowArmed && !armed) haptics?.tick()
            armed = nowArmed
        }
    }
    // While a refresh is in flight (or the disc is springing away) the spring owns the disc;
    // otherwise it tracks the finger 1:1.
    val t = if (active || retracting) refreshSpring.value else finger
    val liveStretch = over / (1f + over)
    val releaseStretch by animateFloatAsState(
        if (active || retracting) 0f else liveStretch,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium),
        label = "refreshStretch",
    )
    val stretch = if (active || retracting) releaseStretch else liveStretch
    if (t <= 0.01f) return

    val density = LocalDensity.current
    // Rests well clear of the status bar's glass, in the middle of the content it refreshes.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sizePx = with(density) { IndicatorSize.toPx() }
    val restPx = with(density) { (topInset + RestBelowStatusBar).toPx() }
    Box(modifier.fillMaxSize()) {
        GlassSurface(
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.align(Alignment.TopCenter).size(IndicatorSize)
                .graphicsLayer {
                    // Drops from OFF the top edge as the pull grows and lands below the status bar.
                    //
                    // A pure SLIDE, no scale. It used to also scale 0.75 -> 1 as it dropped, which
                    // read as the disc "growing in" rather than falling into place -- and a
                    // graphicsLayer alpha below 1 (also tried) clipped the drop shadow to a square.
                    // Sliding the full-size disc down from off-screen is the whole motion; the
                    // spinner's own progress arc does the rest.
                    translationY = -sizePx + t * (sizePx + restPx)
                    // Past the arm point the disc stretches downward, then springs back on release.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    scaleY = 1f + 0.45f * stretch
                },
            hazeState = hazeState,
            // Bare liquid glass throughout: no accent tint at the bottom of the pull.
            tint = androidx.compose.ui.graphics.Color.Transparent,
            clear = true,
            shadow = true,
        ) {
            if (active || retracting) {
                LoadingIndicator(Modifier.size(SpinnerSize))
            } else {
                // While dragging, the spinner's own progress follows the pull exactly.
                LoadingIndicator(
                    progress = { raw },
                    modifier = Modifier.size(SpinnerSize),
                )
            }
        }
    }
}
