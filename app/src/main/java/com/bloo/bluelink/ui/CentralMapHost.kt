package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import dev.chrisbanes.haze.HazeState

/*
 * The one map's host: draws [CentralMap] at the root of the app, as the full-screen map, and feeds it every car. The map is drawn here, at
 * screen level, so it sits above the status bar's blur.
 */

/**
 * The expanded car's map, over the whole screen. It is drawn by the screen under the status bar's frost, not inside the garage, so the
 * floating bar it brings in comes down behind that frost. [frostHaze] is the frost's source; the bar is part of it. The map itself is the
 * same composed instance the pebble hands over (see [CentralMap]).
 */
@Composable
internal fun CentralMapHost(state: State<UiState>, vm: AppViewModel, centralMap: CentralMap, frostHaze: HazeState) {
    // The map's feeds: every car with a location is an icon on it, and the open car's place is its centre. The search bubble reads whether
    // the map is open, through the shared state.
    SideEffect {
        val open = centralMap.vin
        centralMap.cars = state.value.vehicles.mapNotNull { v ->
            state.value.locations[v.vin]?.let { CarPin(v.vin, it, v.name, state.value.imageUrls[v.vin]) }
        }
        centralMap.expandedLocation = open?.let { state.value.locations[it] }
    }
    LaunchedEffect(centralMap.vin) { vm.setMapExpanded(centralMap.vin != null) }
    // The map starts in the first car's Location card, until it is opened on another car.
    LaunchedEffect(state.value.vehicles.firstOrNull()?.vin) {
        if (centralMap.homeVin == null) centralMap.homeVin = state.value.vehicles.firstOrNull()?.vin
    }
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    val deviceLocation by remember { derivedStateOf { state.value.deviceLocation } }
    val appearance = LocalAppearance.current
    val expandedVin = centralMap.vin
    val expandedVehicle = if (expandedVin != null) vehicles.firstOrNull { it.vin == expandedVin } else null
    val expandedLocation = expandedVehicle?.let { state.value.locations[it.vin] }
    // A map that was asked for but has no car or no location to draw from is nothing on screen: say so in the log, rather than leave it silent.
    LaunchedEffect(expandedVin, expandedVehicle == null, expandedLocation == null) {
        if (expandedVin != null && (expandedVehicle == null || expandedLocation == null)) {
            com.bloo.bluelink.data.AppLog.log("Map: $expandedVin not drawn (car found: ${expandedVehicle != null}, location: ${expandedLocation != null})")
        }
    }
    // Where the map grows from: the car's pebble, once it has been on screen. Until then (or after swiping to another car) a pebble-sized
    // box in the middle, so the map always opens.
    val windowSize = LocalWindowInfo.current.containerSize
    val originBounds = expandedVehicle?.vin?.let { centralMap.originBoundsFor(it).value }
        ?: Rect(windowSize.width * 0.05f, windowSize.height * 0.35f, windowSize.width * 0.95f, windowSize.height * 0.6f)

    if (expandedVehicle != null && expandedLocation != null) {
        val locateExpandedVehicle = rememberLocateAction(vm, expandedVehicle)
        // The bar describes the selected car: the one this map is on, or another whose pin was tapped. The map itself stays on the first.
        val barVehicle = centralMap.selectedVin?.let { vin -> vehicles.firstOrNull { it.vin == vin } } ?: expandedVehicle
        val (chargePercent, chargeRange) = mapChargeStats(state.value, barVehicle, appearance.metricDistance)
        val carDetails = buildList {
            barVehicle.modelYear?.let { add("Year" to it) }
            barVehicle.trim?.let { add("Trim" to it) }
            add("Model" to barVehicle.model)
            chargePercent?.let { add("Charge" to it) }
            chargeRange?.let { add("Range" to it) }
        }
        ExpandableMapLayer(
            isExpanded = true,
            originBounds = originBounds,
            location = expandedLocation,
            vehicleName = barVehicle.name,
            chargePercent = chargePercent,
            chargeRange = chargeRange,
            details = carDetails,
            photo = state.value.imageUrls[barVehicle.vin],
            barSelected = centralMap.selectedVin != null,
            selectedIsExpanded = centralMap.selectedVin == null || centralMap.selectedVin == expandedVehicle.vin,
            onDeselect = { centralMap.selectedVin = null },
            onDismissDrag = { centralMap.dismissFade = it },
            deviceLocation = deviceLocation,
            mapState = centralMap.mapState,
            map = { m, i -> centralMap.Content(m, i) },
            onRefreshLocation = locateExpandedVehicle,
            deviceRequest = rememberDeviceLocationRequest(vm),
            // The real command-pending flag, not a guessed timer -- see MapTopBar's own doc -- so the refresh icon's spin genuinely
            // tracks the in-flight fetch this same button just kicked off.
            refreshing = state.value.isPending(expandedVehicle.vin, CarCommandIds.LOCATE),
            frostHaze = frostHaze,
            onDismiss = {
                centralMap.selectedVin = null
                centralMap.vin = null
            },
        )
    }
}
