package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/**
 * The app's ONE pull-to-refresh indicator, drawn as a single floating overlay at the app root
 * rather than per screen.
 */
internal class RefreshIndicatorState {
    /**
     * How far the visible page has been pulled, 0..1+ (the raw M3 distanceFraction). Written by
     * whichever page is being pulled; only the pulled page ever has a non-zero fraction, so this
     * needs no per-page bookkeeping.
     */
    val pull: MutableState<Float> = mutableFloatStateOf(0f)

    // Several Refreshables are alive at once (the pager keeps neighbour car pages composed), so "is
    // a refresh in flight" is an OR across them, tracked by identity rather than a single bool one
    // page could clear while another is still working.
    private val refreshingKeys = mutableStateListOf<Any>()

    /** True while ANY page has a refresh in flight. */
    val refreshing: Boolean get() = refreshingKeys.isNotEmpty()

    fun setRefreshing(key: Any, on: Boolean) {
        if (on) { if (key !in refreshingKeys) refreshingKeys.add(key) } else refreshingKeys.remove(key)
    }

    /**
     * Set the instant the pull is released past the threshold (from Refreshable's own onRefresh),
     * BEFORE the caller's async refresh flag flips. Without it the disc slid back up during that gap
     * and then dropped down again once [refreshing] finally turned true -- reported as the indicator
     * "going away then pulling down from the top again". Holds the disc at the rest position instead.
     */
    var requested by mutableStateOf(false)
}

/** The one app-wide [RefreshIndicatorState]. Null only if no host is mounted (a preview). */
internal val LocalRefreshIndicator =
    staticCompositionLocalOf<RefreshIndicatorState?> { null }

private val IndicatorSize = 52.dp
private val SpinnerSize = 44.dp

/** How far below the status bar's lower edge the indicator rests. */
private val RestBelowStatusBar = 12.dp

/** How much further (in disc-travel fractions) a pull past the arm point keeps going, resisted. */
private const val OverPullTravel = 0.18f

/** The single floating indicator. */
@Composable
internal fun PullRefreshIndicatorHost(
    state: RefreshIndicatorState,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    // Held once the pull is released into a refresh (either the real flag or the synchronous
    // request), driven by the finger otherwise.
    val active = state.refreshing || state.requested
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
            // Continue from wherever the finger left the disc, so it never jumps back to the top
            // and re-drops.
            if (refreshSpring.value < 0.01f) refreshSpring.snapTo(state.pull.value.coerceIn(0f, 1f))
            refreshSpring.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
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
        snapshotFlow { state.pull.value }.collect { p ->
            val nowArmed = p >= 1f
            if (nowArmed && !armed) haptics?.tick()
            armed = nowArmed
        }
    }
    // Past the arm point the pull keeps travelling a little, but resisted (a rubber band), so a
    // deliberate over-pull still reads as doing something instead of freezing the disc at rest.
    val pullRaw = state.pull.value
    val raw = pullRaw.coerceIn(0f, 1f)
    val over = (pullRaw - 1f).coerceAtLeast(0f)
    val finger = raw + over / (1f + over) * OverPullTravel
    // While a refresh is in flight (or the disc is springing away) the spring owns the disc;
    // otherwise it tracks the finger 1:1.
    val t = if (active || retracting) refreshSpring.value else finger
    if (t <= 0.01f) return

    val density = LocalDensity.current
    // Rests well clear of the status bar's glass, in the middle of the content it refreshes.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sizePx = with(density) { IndicatorSize.toPx() }
    val restPx = with(density) { (topInset + RestBelowStatusBar).toPx() }
    Box(modifier.fillMaxSize()) {
        GlassSurface(
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(IndicatorSize)
                .graphicsLayer {
                    // Drops from OFF the top edge as the pull grows and lands below the status bar.
                    //
                    // A pure SLIDE, no scale. It used to also scale 0.75 -> 1 as it dropped, which
                    // read as the disc "growing in" rather than falling into place -- and a
                    // graphicsLayer alpha below 1 (also tried) clipped the drop shadow to a square.
                    // Sliding the full-size disc down from off-screen is the whole motion; the
                    // spinner's own progress arc does the rest.
                    translationY = -sizePx + t * (sizePx + restPx)
                },
            hazeState = hazeState,
            // Bare liquid glass while pulling; a faint accent shows once releasing will refresh.
            tint = if (armed) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            else androidx.compose.ui.graphics.Color.Transparent,
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
