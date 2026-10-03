package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle

/** How far the floating chrome slides down during a pull-to-refresh. */
internal val RefreshPullShift = 96.dp

/**
 * Wraps content with the pull-to-refresh gesture -- no visual indicator at all,
 * app-wide. The drawn indicator was removed after several rounds of real,
 * reported visual bugs across its call sites (a stuck "blob" look, bleed-through
 * under the expanded map, the same shared badge misfiring elsewhere). The gesture
 * and [onRefresh] still fire normally; only the drawing is gone.
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
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val haptics = LocalHaptics.current

    // Publish the pull distance so GarageScreen's floating chrome tracks the pull live.
    // The pull draws nothing itself; only the gesture and its chrome shift remain.
    val pullFractionState = LocalPullFraction.current
    LaunchedEffect(ptrState) {
        snapshotFlow { ptrState.distanceFraction }.collect {
            pullFractionState.value = it
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { haptics?.diceRoll(); onRefresh() },
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
        // No indicator: the gesture and onRefresh still fire, nothing is drawn.
        indicator = {},
    ) {
        // Content stays full-size and edge-to-edge; never shifted down.
        content()
    }
}
