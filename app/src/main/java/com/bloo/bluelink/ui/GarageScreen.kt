package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import com.bloo.bluelink.data.CarCommandIds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.STALE_STATUS_MS
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import com.bloo.uicommon.lowPowerBounce
import com.bloo.uicommon.LocalHaptics

/**
 * Top-level garage screen: picks between two fundamentally different layouts based on screen size
 * and dispatches to the right one, then owns the pager(s) that let the user swipe between cars.
 */
@Composable
internal fun GarageScreen(
    state: State<UiState>,
    vm: AppViewModel,
    /** The one expanded-map holder, shared with [GarageMapOverlay] which the screen draws above the status bar's blur. */
    expandedMap: ExpandedMapState,
    /** Defaults to a fresh one for every existing caller's exact prior behavior. */
    hazeState: HazeState = remember { HazeState() },
) {
    com.bloo.bluelink.perf.TrackRecompositions("GarageScreen")
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
    // THE single experience, scaling to the device: a car reflows to two columns (hero beside its pebbles, hot seat and all) when its
    // own page is wide enough, and stays one column on a narrow window. No second screen, nothing to open or close, except the
    // full-screen button, which only widens a car's PAGE.
    //
    // ONE motion drives all of it. The number of cars sharing the width (perPageAnim) springs, and everything else is derived from
    // that value on every frame: the page width, the block width and the reflow (expandedT, below). Nothing is a separate boolean
    // that flips on its own track, so the column change, the block growth and the two-column reflow move as one, the way the map's
    // own open does.
    val perPageTarget = if (fullScreen && multiColumn) 1 else naturalColumns
    // The app's bounce spring, the same one the pebbles and the search bar use, so a car opening into two columns settles the way
    // everything else on screen does instead of arriving flat.
    val carSpring = lowPowerBounce<Float>(stiffness = Spring.StiffnessMediumLow)
    val perPageAnim = remember { androidx.compose.animation.core.Animatable(perPageTarget.toFloat()) }
    LaunchedEffect(perPageTarget) { perPageAnim.animateTo(perPageTarget.toFloat(), carSpring) }
    val density = LocalDensity.current
    val expandedT = remember(density) {
        ColumnReflow(perPageAnim.asState(), widthDp = { windowInfo.containerSize.width / density.density })
    }

    // The other cars, drawn as pins on the expanded map. The car that is expanded is the map's own pin, so it is left out.
    SideEffect {
        expandedMap.otherCars = state.value.vehicles.mapNotNull { v -> if (v.vin == expandedMap.vin) null else state.value.locations[v.vin] }
    }
    CompositionLocalProvider(LocalPullFraction provides pullFractionState, LocalExpandedMap provides expandedMap) {
    BackdropHost {
        // ONE page, ALWAYS the pager: a car's own layout scales with the device width, so there is no
        // expand or collapse to reconcile -- the pebbles slide from under the hero to beside it purely
        // as the page grows wide enough.
        BlurredBehindMap(expandedMap) {
        CollapsedGaragePager(
            vehicles, count, slots, perPage, naturalColumns, perPageAnim.asState(), vm.currentIndex,
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
        }
    }
    }
}

/**
 * The expanded car's map, over the whole screen. It is drawn by the screen under the status bar's frost, not inside the garage, so the
 * floating bar it brings in comes down behind that frost. [frostHaze] is the frost's source; the bar is part of it. The map itself is the
 * same composed instance the pebble hands over (see [ExpandedMapState]).
 */
@Composable
internal fun GarageMapOverlay(state: State<UiState>, vm: AppViewModel, expandedMap: ExpandedMapState, frostHaze: HazeState) {
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    val deviceLocation by remember { derivedStateOf { state.value.deviceLocation } }
    val appearance = LocalAppearance.current
    val expandedVin = expandedMap.vin
    val expandedVehicle = if (expandedVin != null) vehicles.firstOrNull { it.vin == expandedVin } else null
    val expandedLocation = expandedVehicle?.let { state.value.locations[it.vin] }
    val originBounds = expandedVehicle?.vin?.let { expandedMap.originBoundsFor(it).value }

    if (expandedVehicle != null && expandedLocation != null && originBounds != null) {
        val locateExpandedVehicle = rememberLocateAction(vm, expandedVehicle)
        val (chargePercent, chargeRange) = mapChargeStats(state.value, expandedVehicle, appearance.metricDistance)
        val carDetails = buildList {
            expandedVehicle.modelYear?.let { add("Year" to it) }
            expandedVehicle.trim?.let { add("Trim" to it) }
            add("Model" to expandedVehicle.model)
            chargePercent?.let { add("Charge" to it) }
            chargeRange?.let { add("Range" to it) }
        }
        ExpandableMapLayer(
            isExpanded = true,
            originBounds = originBounds,
            location = expandedLocation,
            vehicleName = expandedVehicle.name,
            chargePercent = chargePercent,
            chargeRange = chargeRange,
            details = carDetails,
            photo = state.value.imageUrls[expandedVehicle.vin],
            deviceLocation = deviceLocation,
            mapState = expandedMap.mapStateFor(expandedVehicle.vin),
            map = expandedMap.mapFor(expandedVehicle.vin),
            onRefreshLocation = locateExpandedVehicle,
            deviceRequest = rememberDeviceLocationRequest(vm),
            // The real command-pending flag, not a guessed timer -- see MapTopBar's own doc -- so the refresh icon's spin genuinely
            // tracks the in-flight fetch this same button just kicked off.
            refreshing = state.value.isPending(expandedVehicle.vin, CarCommandIds.LOCATE),
            frostHaze = frostHaze,
            onDismiss = { expandedMap.vin = null },
        )
    }
}

/** Milliseconds the garage takes to blur or clear behind an open map. */
private const val MapBlurMs = 320

/** How soft the garage gets behind an open full-screen map, at its fullest. */
private const val MapBlurDp = 14f

/** The garage behind an open full-screen map: softly blurred while it is open, and clear again once the map closes. */
@Composable
private fun BlurredBehindMap(expandedMap: ExpandedMapState, content: @Composable () -> Unit) {
    val target = if (expandedMap.vin != null) 1f else 0f
    val amount by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(MapBlurMs),
        label = "garageBehindMap",
    )
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val px = MapBlurDp * density * amount
            renderEffect = if (amount > 0.01f) androidx.compose.ui.graphics.BlurEffect(px, px) else null
        },
    ) {
        content()
    }
}
