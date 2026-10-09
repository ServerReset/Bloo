package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.STALE_STATUS_MS
import kotlinx.coroutines.delay

/**
 * Top-level garage screen: picks between two fundamentally different layouts based on screen size
 * and dispatches to the right one, then owns the pager(s) that let the user swipe between cars.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun GarageScreen(
    state: State<UiState>,
    vm: AppViewModel,
    /** Defaults to a fresh one for every existing caller's exact prior behavior. */
    hazeState: HazeState = remember { HazeState() },
) {
    // DERIVED reads, not body reads of `state.value`.
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    val expandedIndex by remember { derivedStateOf { state.value.expandedIndex } }
    val deviceLocation by remember { derivedStateOf { state.value.deviceLocation } }
    val showSettingsHint by remember { derivedStateOf { state.value.showSettingsHint } }
    val appearance = LocalAppearance.current

    // Reading it in THIS composable is fine and intended -- this is one of the few places that
    // genuinely needs it, and it is above the pages.
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    // lastIndex.coerceAtLeast(0): lastIndex is -1 on an empty list, and coerceIn(0, -1) throws (min
    // > max) before getOrNull ever gets a chance to just return null for it.
    val currentVehicle = vehicles.getOrNull(currentIndex.coerceIn(0, vehicles.lastIndex.coerceAtLeast(0)))
    val currentFetchedAt by remember(currentVehicle?.vin) {
        derivedStateOf { currentVehicle?.let { state.value.fetchedAt(it) } }
    }
    val sessionStartMs = remember { System.currentTimeMillis() }
    LaunchedEffect(currentVehicle?.vin, currentFetchedAt) {
        val fetchedAt = currentFetchedAt
        if (fetchedAt != null &&
            fetchedAt < sessionStartMs &&
            System.currentTimeMillis() - fetchedAt > STALE_STATUS_MS) {
            // Give the automatic background fetch time to land. If it returns fresh data,
            // currentFetchedAt changes → this effect restarts → delay is cancelled → user never
            // sees a spurious "stale" toast.
            delay(25_000)
            vm.reportInfo("Data is over 15 min old. Pull down to refresh")
        }
    }

    // Gentle one-time nudge after onboarding, encouraging a Settings visit.
    LaunchedEffect(showSettingsHint) {
        if (showSettingsHint) {
            vm.reportInfo("Tip: tune each car's seats, photo and pebble order in Settings")
            vm.dismissSettingsHint()
        }
    }

    // Settle haptic when a refresh lands.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (wasRefreshing && !refreshing) haptics?.slotSettle()
        wasRefreshing = refreshing
    }
    val pullFractionState = remember { mutableFloatStateOf(0f) }
    // Drives the expanded pager's programmatic car-switch (the hero card's own swipe -- see
    // ExpandedCar's onSwipeCar).
    val garageScope = rememberCoroutineScope()
    val count = vehicles.size
    // At least one non-Settings page even with zero cars: the "no connection"/ "not signed in"/"no
    // vehicles" status card (GarageStatusCard, Guard.kt) takes that one slot instead of a car, so
    // Settings is still just one swipe away rather than a whole separate unreachable-by-swipe
    // screen.
    val slots = maxOf(count, 1)
    val windowInfo = LocalWindowInfo.current
    val widthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val large = widthDp >= TWO_COLUMN_MIN_DP.dp
    // See StatusBarScrim's doc for why plain Modifier.blur never worked here.
    val expandedMap = remember { ExpandedMapState() }
    // Mirrors expandedMap.vin into shared UiState -- see UiState.mapExpanded's own doc -- so
    // Screens.kt (a sibling of this screen, not a descendant, and so unable to read
    // expandedMap/LocalExpandedMap directly) can hide the floating search bubble while the map
    // sheet covers the same corner its own bottom action row occupies.
    LaunchedEffect(expandedMap.vin) { vm.setMapExpanded(expandedMap.vin != null) }
    DisposableEffect(Unit) { onDispose { vm.setMapExpanded(false) } }
    // How many full-height cards fit side by side; pages advance by this many. `slots`, not
    // `count`: coerceIn(1, 0) throws (min > max) with zero cars, and there is exactly one
    // non-Settings page to show anyway in that case (the status card), so multi-column grid mode
    // never applies to it.
    val perPage = carColumnsFor(widthDp.value).coerceIn(1, slots)
    // Expanding to the dual-column view only makes sense on a wide screen.
    val canExpand = large && count > 1
    // There is deliberately no automatic "a lone car on a wide screen fills the screen by itself"
    // case -- that shortcut bypassed the block pager below, and with Settings folded into that
    // pager as its own page it would have made Settings unreachable by swipe for exactly that one
    // combination (a single car on a wide screen).
    val expandedIdx = expandedIndex?.takeIf { it in vehicles.indices && canExpand }

    BackHandler(enabled = expandedIdx != null) { vm.collapse() }

    // 0 collapsed, 1 expanded: the pebble list's own reflow (under the hero -> beside it) runs on
    // this, so expanding is an in-place animation of the same content rather than a screen swap.
    val expandedT by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expandedIdx != null) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "expandT",
    )

    CompositionLocalProvider(LocalPullFraction provides pullFractionState, LocalExpandedMap provides expandedMap) {
    BackdropHost {
        // ONE page, ALWAYS the pager: expanding reflows the focused car's page in place -- its pebbles
        // slide from under the hero to beside it, and the other car / Settings columns slide off as
        // the focused page grows to the full width. No second screen, no route change, nothing to
        // fade in.
        CollapsedGaragePager(
            vehicles, count, slots, perPage, canExpand, currentIndex,
            windowInfo.containerSize.width, state, vm, hazeState,
            expandedIdx = expandedIdx,
            expandedT = expandedT,
        )
        // THE SINGLE CarMap instance, positioned at the screen level. It animates from the pebble
        // location (collapsed) to full-screen (expanded). Same instance, literally growing -- not
        // two separate maps or morphing.
        val expandedVin = expandedMap.vin
        val expandedVehicle = if (expandedVin != null) vehicles.firstOrNull { it.vin == expandedVin } else null
        val expandedLocation = expandedVehicle?.let { state.value.locations[it.vin] }
        val originBounds = expandedVehicle?.vin?.let { expandedMap.originBoundsFor(it).value }

        if (expandedVehicle != null && expandedLocation != null && originBounds != null) {
            val locateExpandedVehicle = rememberLocateAction(vm, expandedVehicle)
            ExpandableMapLayer(
                isExpanded = true,
                originBounds = originBounds,
                location = expandedLocation,
                vehicleName = expandedVehicle.name,
                statusLine = mapStatusLine(state.value, expandedVehicle, appearance.metricDistance),
                deviceLocation = deviceLocation,
                mapState = expandedMap.mapStateFor(expandedVehicle.vin),
                hazeState = hazeState,
                onRefreshLocation = locateExpandedVehicle,
                deviceRequest = rememberDeviceLocationRequest(vm),
                // The real command-pending flag, not a guessed timer -- see MapTopBar's own doc --
                // so the refresh icon's spin genuinely tracks the in-flight fetch this same button
                // just kicked off.
                refreshing = state.value.isPending(expandedVehicle.vin, "locate"),
                onDismiss = { expandedMap.vin = null },
            )
        }
    }
    }
}
