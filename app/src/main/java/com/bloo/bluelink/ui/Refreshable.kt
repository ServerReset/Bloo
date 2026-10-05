package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle

/** How far the floating chrome slides down during a pull-to-refresh. */
internal val RefreshPullShift = 96.dp

/** Wraps content with the pull-to-refresh gesture -- no visual indicator at all, app-wide. */
@Composable
internal fun Refreshable(
    // The single UiState field this needs, and NOT the whole UiState: passing the state object
    // subscribed every caller's composition to every emission -- a weather tick for another car, an
    // AI probe, a log line -- for all three live pager pages at once, which is exactly what the
    // callers' State<UiState> indirection exists to avoid.
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val haptics = LocalHaptics.current

    // Publish the pull distance so GarageScreen's floating chrome tracks the pull live. The pull
    // draws nothing itself; the app's ONE indicator (PullRefreshIndicatorHost, mounted at the root)
    // draws it.
    val pullFractionState = LocalPullFraction.current
    // The one app-wide indicator: this page feeds it and draws nothing of its own.
    val indicator = LocalRefreshIndicator.current
    LaunchedEffect(ptrState) {
        snapshotFlow { ptrState.distanceFraction }.collect {
            pullFractionState.value = it
            indicator?.pull?.value = it
        }
    }
    // One stable key per Refreshable instance, so this page's own in-flight flag contributes to the
    // app-wide OR without clearing a DIFFERENT page's (neighbour car pages stay composed).
    val indicatorKey = remember { Any() }
    LaunchedEffect(refreshing, indicator) {
        indicator?.setRefreshing(indicatorKey, refreshing)
    }
    DisposableEffect(indicator, indicatorKey) {
        onDispose { indicator?.setRefreshing(indicatorKey, false) }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { haptics?.diceRoll(); onRefresh() },
        state = ptrState,
        modifier = Modifier.fillMaxSize(),
        // No per-page indicator: the one app-wide disc is drawn by PullRefreshIndicatorHost.
        indicator = {},
    ) {
        // Content stays full-size and edge-to-edge; never shifted down.
        content()
    }
}
