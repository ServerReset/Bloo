@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState

/** How far the floating overlays (dots, buttons) slide down during a refresh. */
internal val RefreshPullShift = 96.dp

/**
 * Wraps content with the pull-to-refresh gesture -- no visual indicator of its
 * own any more (removed app-wide after several rounds of real, reported visual
 * bugs across its different call sites: a stuck "blob" look, bleed-through
 * under the expanded map, and the same shared badge misfiring elsewhere). The
 * gesture and [onRefresh] still fire normally; only the badge is gone.
 *
 * [onRefresh] is a plain lambda, not a fixed `vm.refreshStatus(v)` call, so
 * this same wrapper also covers [GarageStatusCard] (Guard.kt) -- the "no
 * vehicles"/"no connection" page has no [Vehicle] to refresh, just
 * [AppViewModel.loadGarage] to retry, and reported directly as wanting to be
 * "just another card like the rest of them" rather than its own one-off
 * Reload button.
 */
@Composable
internal fun Refreshable(
    // The single UiState field this needs, and NOT the whole UiState: passing the state object
    // subscribed every caller's composition to every emission -- a weather tick for another car,
    // an AI probe, a log line -- for all three live pager pages at once, which is exactly what
    // the callers' State<UiState> indirection exists to avoid.
    refreshing: Boolean,
    onRefresh: () -> Unit,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val haptics = LocalHaptics.current

    // Publish the pull distance so GarageScreen's overlays track the pull live.
    val pullFractionState = LocalPullFraction.current
    // The ONE app-wide indicator (see GlobalRefresh): this page reports into it and draws nothing itself.
    val global = LocalGlobalRefresh.current
    val refreshKey = androidx.compose.runtime.remember { Any() }
    androidx.compose.runtime.DisposableEffect(global, refreshKey) {
        onDispose { global?.setRefreshing(refreshKey, false) }
    }
    LaunchedEffect(refreshing, global) { global?.setRefreshing(refreshKey, refreshing) }
    LaunchedEffect(ptrState) {
        var last = 0f
        snapshotFlow { ptrState.distanceFraction }.collect {
            pullFractionState.value = it
            // Only write while this page is actually being pulled (or just let go), so a page that is
            // merely composing never stomps another page's pull.
            if (it != 0f || last != 0f) global?.pull = it
            last = it
        }
    }

    // Material 3 EXPRESSIVE pull-to-refresh: [PullToRefreshDefaults.LoadingIndicator] is the
    // expressive loading shape, which pops in from the top edge as the user pulls and then spins
    // while the refresh runs -- replacing the plain circular indicator the bare pullToRefresh
    // modifier drew before.
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { haptics?.diceRoll(); onRefresh() },
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // The indicator's HOME is the very top edge of the screen, so it slides in from
            // off the top -- over the status bar -- and reads as coming off the edge of the
            // screen, instead of materialising below the status bar. It must NOT come to REST
            // behind the status bar, though, so the status-bar height is added as the pull
            // grows (frac 0 at the edge, frac 1 clear of the bar) rather than baked in as a
            // static offset, which would have shifted the whole travel down and killed the
            // "coming off the screen" entrance this is after.
            val density = LocalDensity.current
            val settlePx = WindowInsets.statusBars.getTop(density) +
                with(density) { GapRow.toPx() }
            PullToRefreshDefaults.LoadingIndicator(
                state = ptrState,
                isRefreshing = refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = ptrState.distanceFraction * settlePx },
            )
        },
    ) {
        // Content stays full-size and edge-to-edge; never shifted down.
        content()
    }
}
