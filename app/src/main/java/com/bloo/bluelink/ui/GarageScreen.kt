package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.STALE_STATUS_MS
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.bloo.uicommon.LocalHaptics

/**
 * Top-level garage screen: picks between two fundamentally different layouts based on screen size
 * and dispatches to the right one, then owns the pager(s) that let the user swipe between cars.
 */
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
    val count = vehicles.size
    // At least one non-Settings page even with zero cars: the "no connection"/ "not signed in"/"no
    // vehicles" status card (GarageStatusCard, Guard.kt) takes that one slot instead of a car, so
    // Settings is still just one swipe away rather than a whole separate unreachable-by-swipe
    // screen.
    val slots = maxOf(count, 1)
    val windowInfo = LocalWindowInfo.current
    val widthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    // See StatusBarScrim's doc for why plain Modifier.blur never worked here.
    val expandedMap = remember { ExpandedMapState() }
    // Mirrors expandedMap.vin into shared UiState -- see UiState.mapExpanded's own doc -- so
    // Screens.kt (a sibling of this screen, not a descendant, and so unable to read
    // expandedMap/LocalExpandedMap directly) can hide the floating search bubble while the map
    // sheet covers the same corner its own bottom action row occupies.
    LaunchedEffect(expandedMap.vin) { vm.setMapExpanded(expandedMap.vin != null) }
    DisposableEffect(Unit) { onDispose { vm.setMapExpanded(false) } }
    // As many whole two-column cars as fit side by side; capped at the number of items.
    val naturalColumns = carColumnsFor(widthDp.value).coerceIn(1, slots)
    // The hero shows its full-screen button only when there is more than one column to expand AWAY
    // from: with a single column the car already fills the width, so there is nothing to open.
    val multiColumn = naturalColumns >= 2
    // Full screen: one car takes the whole window (and so reflows to its own two columns) instead of
    // sharing it with its neighbours. Only meaningful in the grid, so a narrow window resets it.
    var fullScreen by remember { mutableStateOf(false) }
    LaunchedEffect(multiColumn) { if (!multiColumn) fullScreen = false }
    val perPage = if (fullScreen && multiColumn) 1 else naturalColumns
    // THE single experience, scaling to the device: a car reflows to two columns (hero beside its
    // pebbles, hot seat and all) when its own page is at least TWO_COLUMN_MIN_DP wide, and stays one
    // column on a narrow window. No manual expand state, no second screen, nothing to open or close --
    // except the full-screen button above, which only widens a car's PAGE, never swaps screens.
    val twoColumn = (widthDp / perPage.toFloat()) >= TWO_COLUMN_MIN_DP.dp
    // The two springs below are the SAME spring (a critically damped one is linear in its progress), so they move as one:
    // how many cars share the width eases between the grid and one car, and the car's own reflow between one column and
    // two eases with it. Before, the page snapped to full width at once and only the reflow animated -- the jump.
    val carSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val expandedT = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (twoColumn) 1f else 0f,
        animationSpec = carSpring,
        label = "expandT",
    )
    val perPageT = androidx.compose.animation.core.animateFloatAsState(
        targetValue = perPage.toFloat(),
        animationSpec = carSpring,
        label = "perPageT",
    )

    CompositionLocalProvider(LocalPullFraction provides pullFractionState, LocalExpandedMap provides expandedMap) {
    BackdropHost {
        // ONE page, ALWAYS the pager: a car's own layout scales with the device width, so there is no
        // expand or collapse to reconcile -- the pebbles slide from under the hero to beside it purely
        // as the page grows wide enough.
        CollapsedGaragePager(
            vehicles, count, slots, perPage, naturalColumns, perPageT, vm.currentIndex,
            windowInfo.containerSize.width, state, vm, hazeState,
            expandedT = expandedT,
            multiColumn = multiColumn,
            fullScreen = fullScreen,
            // ONE COLUMN's width, not the page's. ReflowPair measures both blocks at this and moves
            // the second from under the first to beside it -- so handing it the whole page made each
            // block full-width, the two could not fit, and the pebble column was placed off-screen.
            // The hero then stretched to fill and the pebbles stacked underneath, which is what a
            // single car expanded to full screen looked like.
            onToggleFullScreen = { real ->
                if (fullScreen) {
                    fullScreen = false
                } else {
                    // Expand the car whose button was pressed: park the pager on it first, so the
                    // single full-screen page is the one that was tapped, not whatever was centred.
                    vm.selectIndex(real)
                    fullScreen = true
                }
            },
        )
        // THE SINGLE CarMap instance (ExpandedMapState.mapFor), moved here from the pebble's slot and sized from the pebble's rectangle
        // to the screen's by ExpandableMapLayer, then moved back when it closes: the same composed map, not two.
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
                map = expandedMap.mapFor(expandedVehicle.vin),
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
