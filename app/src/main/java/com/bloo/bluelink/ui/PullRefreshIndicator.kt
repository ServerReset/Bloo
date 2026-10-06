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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

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
}

/** The one app-wide [RefreshIndicatorState]. Null only if no host is mounted (a preview). */
internal val LocalRefreshIndicator =
    staticCompositionLocalOf<RefreshIndicatorState?> { null }

private val IndicatorSize = 52.dp
private val SpinnerSize = 40.dp

/** How far below the status bar's lower edge the indicator rests. */
private val RestBelowStatusBar = 12.dp

/** The single floating indicator. */
@Composable
internal fun PullRefreshIndicatorHost(
    state: RefreshIndicatorState,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    // The disc is driven DIRECTLY by the finger while pulling (1:1, no spring lag), and only
    // springs for the refreshing endpoint -- a pull that trails the finger on a spring reads as
    // disconnected.
    val refreshSpring = remember { Animatable(0f) }
    LaunchedEffect(state.refreshing) {
        refreshSpring.animateTo(
            targetValue = if (state.refreshing) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        )
    }
    val raw = state.pull.value.coerceIn(0f, 1f)
    // While refreshing, hold at the sprung-open endpoint; otherwise track the finger exactly.
    val t = if (state.refreshing) refreshSpring.value else raw
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
            // Bare liquid glass: the rim bends what is behind it, nothing milky over the middle.
            tint = androidx.compose.ui.graphics.Color.Transparent,
            clear = true,
            shadow = true,
        ) {
            if (state.refreshing) {
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
