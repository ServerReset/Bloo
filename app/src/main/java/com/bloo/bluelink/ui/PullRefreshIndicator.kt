package com.bloo.bluelink.ui

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
 * The app's ONE pull-to-refresh indicator, drawn as a single floating overlay at
 * the app root rather than per screen.
 *
 * Every [Refreshable] (each car page, the status card) feeds its live pull
 * distance and in-flight state into [RefreshIndicatorState] instead of drawing
 * anything itself, so there is exactly one indicator for the whole app: pull on
 * any screen and the same liquid-glass disc drops from the top edge, stays put
 * across a screen change mid-refresh, and disappears once the work is done.
 *
 * The background is the app's real liquid glass -- the same [GlassSurface] /
 * [appGlassEffect] refraction every floating chip uses -- so it warps what is
 * behind it instead of being a flat tinted disc.
 */
internal class RefreshIndicatorState {
    /** How far the visible page has been pulled, 0..1+ (the raw M3 distanceFraction). Written by
     *  whichever page is being pulled; only the pulled page ever has a non-zero fraction, so this
     *  needs no per-page bookkeeping. */
    val pull: MutableState<Float> = mutableFloatStateOf(0f)

    // Several Refreshables are alive at once (the pager keeps neighbour car pages composed), so
    // "is a refresh in flight" is an OR across them, tracked by identity rather than a single bool
    // one page could clear while another is still working.
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

/**
 * The single floating indicator. Reads [state] and draws a liquid-glass disc that
 * slides in from the top edge under the pull and then spins while the refresh runs.
 *
 * [hazeState] is the app's backdrop source (see [LocalBackdropHaze]); passing it is
 * what makes the disc real refracting glass rather than a flat tint.
 */
@Composable
internal fun PullRefreshIndicatorHost(
    state: RefreshIndicatorState,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    // How far the disc is "shown": the pull while dragging, or fully once refreshing.
    val shown = if (state.refreshing) 1f else state.pull.value.coerceIn(0f, 1f)
    // Keep the endpoint drawn through a refresh even if a page resets its own pull to 0.
    val emerge = remember { Animatable(0f) }
    LaunchedEffect(shown, state.refreshing) {
        emerge.animateTo(
            targetValue = shown,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        )
    }
    val t = emerge.value
    if (t <= 0.01f) return

    val density = LocalDensity.current
    val settlePx = with(density) { 12.dp.toPx() }
    Box(modifier.fillMaxSize()) {
        // The one glass disc: floating chrome, so real liquid glass (refraction + a specular rim).
        GlassSurface(
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(52.dp)
                .graphicsLayer {
                    // Drops from OFF the top edge as the pull grows, lands just below it at rest.
                    translationY = (t - 1f) * 56.dp.toPx() + t * settlePx
                    alpha = t.coerceIn(0f, 1f)
                    val k = 0.6f + 0.4f * t.coerceIn(0f, 1f)
                    scaleX = k
                    scaleY = k
                },
            hazeState = hazeState,
            shadow = true,
        ) {
            if (state.refreshing) {
                LoadingIndicator(Modifier.size(24.dp))
            } else {
                // While dragging, the spinner's own progress follows the pull, so the ring draws
                // itself as you pull and is ready to spin the instant the gesture commits.
                LoadingIndicator(
                    progress = { state.pull.value.coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
