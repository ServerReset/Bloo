package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.PullRefreshState
import com.bloo.uicommon.PullRefreshDisc
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

/** How far below the status bar's lower edge the indicator rests. */
private val RestBelowStatusBar = 12.dp

/**
 * The single floating indicator: the shared pull-to-refresh disc, in the phone's liquid glass. What the phone adds to the shared motion is the
 * haptic tick at the arm point, and the hand-off from a release's own request to the real refreshing flag.
 */
@Composable
internal fun PullRefreshIndicatorHost(state: RefreshIndicatorState, hazeState: HazeState?, modifier: Modifier = Modifier) {
    // Held once the pull is released into a refresh (either the real flag or the synchronous request).
    val active = state.refreshing || state.requested
    // The real flag takes over from the synchronous request...
    LaunchedEffect(state.refreshing) { if (state.refreshing) state.requested = false }
    // ...and if a caller's refresh never sets one at all, don't hold the disc forever.
    LaunchedEffect(state.requested) {
        if (state.requested) {
            delay(700)
            if (!state.refreshing) state.requested = false
        }
    }
    // A light tick the moment the pull crosses the point where releasing arms a refresh, and again if you ease back under it and pull again.
    val haptics = LocalHaptics.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.pull }.collect { p ->
            val nowArmed = p >= 1f
            if (nowArmed && !armed) haptics?.tick()
            armed = nowArmed
        }
    }
    val density = LocalDensity.current
    // Rests well clear of the status bar's glass, in the middle of the content it refreshes.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val restPx = with(density) { (topInset + RestBelowStatusBar).toPx() }
    PullRefreshDisc(
        pull = state.pull,
        active = active,
        restPx = restPx,
        size = IndicatorSize,
        modifier = modifier,
    ) { discModifier, content ->
        GlassSurface(
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = discModifier,
            hazeState = hazeState,
            // Bare liquid glass throughout: no accent tint at the bottom of the pull.
            tint = androidx.compose.ui.graphics.Color.Transparent,
            clear = true,
            shadow = true,
            content = content,
        )
    }
}
