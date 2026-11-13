package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalWindowInfo
import com.bloo.bluelink.data.CarCommandIds
import dev.chrisbanes.haze.HazeState

/*
 * The one map's host: the only place the map is drawn. Closed, it sits in the Location card of the car the garage is on, at that card's
 * rectangle. Open, it grows to the full screen. Every other part of the app calls [CentralMap] to open or dismiss it; none of them draws it.
 */

/**
 * The one map, drawn at screen level so it sits above the status bar's blur. Its rectangle comes from [ExpandableMapLayer]: the current
 * car's card when closed, the sheet when open. The map is composed here and nowhere else.
 *
 * The garage's current car is the one car everything follows: the bar shows it, a swipe on the bar or a tap on a pin changes it (and the
 * garage travels to it behind the map), the bottom buttons act on it, and the map closes onto its card.
 */
@Composable
internal fun CentralMapHost(state: State<UiState>, vm: AppViewModel, centralMap: CentralMap, frostHaze: HazeState) {
    // The map's feeds: every car with a location is an icon on it, and the open car's place is its centre. The search bubble reads whether
    // the map is open, through the shared state.
    //
    // Everything this host takes from the app's state is a derived slice compared by value, never a read of the whole state: the state
    // changes on every status poll, charging tick and pending flag, and a whole-state read recomposed the map host, rebuilt every car's
    // bar and pins, and recomposed the map each time, even when nothing the map shows had changed.
    val pins by remember {
        derivedStateOf(structuralEqualityPolicy()) {
            val s = state.value
            s.vehicles.mapNotNull { v -> s.locations[v.vin]?.let { CarPin(v.vin, it, v.name, s.imageUrls[v.vin]) } }
        }
    }
    // Handed to the map only when the pins really change.
    LaunchedEffect(Unit) { snapshotFlow { pins }.collect { centralMap.cars = it } }
    val locations by remember { derivedStateOf(structuralEqualityPolicy()) { state.value.locations } }
    LaunchedEffect(centralMap.vin) { vm.setMapExpanded(centralMap.vin != null) }
    // A car chosen on the map becomes the app's current car, so the garage travels to it behind the map.
    SideEffect {
        centralMap.onCarChosen = { vin ->
            state.value.vehicles.indexOfFirst { it.vin == vin }.takeIf { it >= 0 }?.let { vm.selectIndex(it) }
        }
    }
    val windowSize = LocalWindowInfo.current.containerSize
    SideEffect { centralMap.screen = Size(windowSize.width.toFloat(), windowSize.height.toFloat()) }
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    // The garage's current car is the app's car: the bar shows it, and closed, the map rests in its card, so a swipe in the garage brings
    // the map along.
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    LaunchedEffect(currentIndex, vehicles) {
        val vin = vehicles.getOrNull(currentIndex)?.vin ?: return@LaunchedEffect
        centralMap.currentVin = vin
        if (centralMap.vin == null && centralMap.closingTo == null) centralMap.rest(vin)
    }
    val deviceLocation by remember { derivedStateOf { state.value.deviceLocation } }
    val appearance = LocalAppearance.current
    // Open, the map is anchored on the car it was opened for; closed, it is on its home car. Either way it needs that car and its location.
    val openVin = centralMap.vin
    val homeVin = centralMap.homeVin
    val hostVin = openVin ?: homeVin
    val hostVehicle = if (hostVin != null) vehicles.firstOrNull { it.vin == hostVin } else null
    val hostLocation = hostVin?.let { locations[it] }
    // A map that has nothing to draw from is nothing on screen: say so in the log, rather than leave it silent.
    LaunchedEffect(hostVin, hostVehicle == null, hostLocation == null) {
        if (openVin != null && (hostVehicle == null || hostLocation == null)) {
            com.bloo.bluelink.data.AppLog.log("Map: $openVin not drawn (car found: ${hostVehicle != null}, location: ${hostLocation != null})")
        }
    }
    if (hostVehicle == null || hostLocation == null) return
    // Opening, the map grows from the card it was opened from; until that card has been on screen, and when the card it closes onto is
    // nowhere on screen, it uses a box in the middle, so the map always opens and never flies off the screen to close.
    val floatingRect = Rect(windowSize.width * 0.04f, windowSize.height * 0.52f, windowSize.width * 0.96f, windowSize.height * 0.84f)
    val fallback = Rect(windowSize.width * 0.05f, windowSize.height * 0.35f, windowSize.width * 0.95f, windowSize.height * 0.6f)
    val closingTo = centralMap.closingTo
    val opening = openVin != null && closingTo == null
    val originBounds: () -> Rect = {
        when {
            // Opening grows from the card that was tapped. Closed, and closing, the map is a fixed floating overlay over the whole app,
            // so it never depends on a car's card being on screen (nor on the garage travelling to a car to find it).
            opening -> centralMap.cardBounds(openVin!!) ?: fallback
            else -> floatingRect
        }
    }

    // The car the bar shows: the garage's current car (the anchor until the garage has reported one).
    val barVin = centralMap.currentVin?.takeIf { vin -> vehicles.any { it.vin == vin } } ?: hostVehicle.vin
    val barVehicle = vehicles.firstOrNull { it.vin == barVin } ?: hostVehicle
    val barLocation = locations[barVehicle.vin] ?: hostLocation
    val metric = appearance.metricDistance
    // Every car, as the bar's carousel shows it. Derived and compared by value, so the bar is rebuilt only when a car's name, photo,
    // charge or range actually changes.
    val barItems by remember(metric) {
        derivedStateOf(structuralEqualityPolicy()) {
            val s = state.value
            s.vehicles.map { v ->
                val (pct, range) = mapChargeStats(s, v, metric)
                BarItem(
                    vin = v.vin,
                    name = v.name,
                    photo = s.imageUrls[v.vin],
                    charge = pct,
                    range = range,
                    details = buildList {
                        v.modelYear?.let { add("Year" to it) }
                        v.trim?.let { add("Trim" to it) }
                        add("Model" to v.model)
                        pct?.let { add("Charge" to it) }
                        range?.let { add("Range" to it) }
                    },
                )
            }
        }
    }
    val refreshing by remember(barVehicle.vin) {
        derivedStateOf { state.value.isPending(barVehicle.vin, CarCommandIds.LOCATE) }
    }
    val locateBarVehicle = rememberLocateAction(vm, barVehicle)
    ExpandableMapLayer(
        isExpanded = openVin != null,
        originBounds = originBounds,
        location = hostLocation,
        barItems = barItems,
        barIndex = vehicles.indexOfFirst { it.vin == barVehicle.vin }.coerceAtLeast(0),
        // A swipe on the bar is the same as a tap on that car's icon: the car changes, the garage travels to it, the map glides to it.
        onBarIndexChange = { i -> state.value.vehicles.getOrNull(i)?.vin?.let { centralMap.select(it) } },
        selectedName = barVehicle.name,
        selectedLocation = barLocation,
        selectedPan = { centralMap.panTo(barVehicle.vin) },
        onCenterCar = {
            if (barVehicle.vin == hostVehicle.vin) centralMap.mapState.recenter() else centralMap.glideTo(barVehicle.vin)
        },
        barSelected = centralMap.selectedVin != null,
        selectedIsExpanded = centralMap.selectedVin == null || centralMap.selectedVin == hostVehicle.vin,
        onDeselect = { centralMap.barShown = false },
        onDismissDrag = { centralMap.dismissFade = it },
        deviceLocation = deviceLocation,
        mapState = centralMap.mapState,
        map = { m, i -> centralMap.Content(m, i) },
        onRefreshLocation = locateBarVehicle,
        deviceRequest = rememberDeviceLocationRequest(vm),
        // The real command-pending flag, not a guessed timer, so the refresh spin tracks the fetch this same button just started.
        refreshing = refreshing,
        frostHaze = frostHaze,
        chromeInsets = centralMap.chromeInsets,
        // Closing goes straight to the floating place: the car it closes for is the one the bar shows, which a swipe or a pin tap has already chosen.
        onOpen = { centralMap.open(hostVehicle.vin) },
        onDismiss = {
            // The closed map rests in the card it closed onto: the garage's current car.
            (centralMap.closingTo ?: centralMap.currentVin)?.let { centralMap.rest(it) }
            centralMap.barShown = false
            centralMap.vin = null
            centralMap.closingTo = null
        },
    )
}

/** [index] wrapped into 0 until [size]: past either end, the list continues from the other end, as the car pager does. */
internal fun wrapIndex(index: Int, size: Int): Int = ((index % size) + size) % size
