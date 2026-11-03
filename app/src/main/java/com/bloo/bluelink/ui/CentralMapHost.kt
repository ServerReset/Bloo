package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalWindowInfo
import com.bloo.bluelink.data.CarCommandIds
import dev.chrisbanes.haze.HazeState

/*
 * The one map's host: the only place the map is drawn. Closed, it sits in the Location card of its home car, at that card's rectangle.
 * Open, it grows to the full screen. Every other part of the app calls [CentralMap] to open or dismiss it; none of them draws it.
 */

/**
 * The one map, drawn at screen level so it sits above the status bar's blur. Its rectangle comes from [ExpandableMapLayer]: the home car's
 * card when closed, the sheet when open. The map is composed here and nowhere else.
 */
@Composable
internal fun CentralMapHost(state: State<UiState>, vm: AppViewModel, centralMap: CentralMap, frostHaze: HazeState) {
    // The map's feeds: every car with a location is an icon on it, and the open car's place is its centre. The search bubble reads whether
    // the map is open, through the shared state.
    SideEffect {
        centralMap.cars = state.value.vehicles.mapNotNull { v ->
            state.value.locations[v.vin]?.let { CarPin(v.vin, it, v.name, state.value.imageUrls[v.vin]) }
        }
    }
    LaunchedEffect(centralMap.vin) { vm.setMapExpanded(centralMap.vin != null) }
    // A car chosen on the map becomes the app's current car, so the car page swaps to it behind the map.
    SideEffect {
        centralMap.onCarChosen = { vin ->
            state.value.vehicles.indexOfFirst { it.vin == vin }.takeIf { it >= 0 }?.let { vm.selectIndex(it) }
        }
    }
    // Closed, the map sits in the Location card of the car being looked at, so a swipe to another car brings it along.
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    LaunchedEffect(currentIndex, state.value.vehicles) {
        if (centralMap.vin == null) state.value.vehicles.getOrNull(currentIndex)?.vin?.let { centralMap.homeVin = it }
    }
    // The map starts in the first car's Location card, until it is opened on another car.
    LaunchedEffect(state.value.vehicles.firstOrNull()?.vin) {
        if (centralMap.homeVin == null) centralMap.homeVin = state.value.vehicles.firstOrNull()?.vin
    }
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    val deviceLocation by remember { derivedStateOf { state.value.deviceLocation } }
    val appearance = LocalAppearance.current
    // Open, the map is on the car it was opened for; closed, it is on its home car. Either way it needs that car and its location.
    val openVin = centralMap.vin
    val homeVin = centralMap.homeVin
    val hostVin = openVin ?: homeVin
    val hostVehicle = if (hostVin != null) vehicles.firstOrNull { it.vin == hostVin } else null
    val hostLocation = hostVin?.let { state.value.locations[it] }
    // A map that has nothing to draw from is nothing on screen: say so in the log, rather than leave it silent.
    LaunchedEffect(hostVin, hostVehicle == null, hostLocation == null) {
        if (openVin != null && (hostVehicle == null || hostLocation == null)) {
            com.bloo.bluelink.data.AppLog.log("Map: $openVin not drawn (car found: ${hostVehicle != null}, location: ${hostLocation != null})")
        }
    }
    // Closed, the map sits on its home card, so it needs that card's place on screen. Until the card has been placed, nothing is drawn.
    val closedAt: Rect? = if (openVin == null && homeVin != null) centralMap.originBoundsFor(homeVin).value else null
    if (hostVehicle == null || hostLocation == null) return
    val windowSize = LocalWindowInfo.current.containerSize
    // Open, the map grows from the car's own card; until that card has been on screen, from a box in the middle, so the map always opens.
    val fallback = Rect(windowSize.width * 0.05f, windowSize.height * 0.35f, windowSize.width * 0.95f, windowSize.height * 0.6f)
    // Opening grows from the card it was opened from; closing shrinks onto the card of the car chosen on it.
    val originBounds = if (openVin != null && centralMap.closingTo == null) {
        centralMap.originBoundsFor(openVin).value ?: fallback
    } else {
        centralMap.closingTo?.let { centralMap.originBoundsFor(it).value } ?: closedAt ?: return
    }

    val locateHostVehicle = rememberLocateAction(vm, hostVehicle)
    // The bar describes the selected car: the one the map is on, or another whose pin was tapped. The map itself stays on the first.
    val barVehicle = centralMap.selectedVin?.let { vin -> vehicles.firstOrNull { it.vin == vin } } ?: hostVehicle
    val (chargePercent, chargeRange) = mapChargeStats(state.value, barVehicle, appearance.metricDistance)
    val carDetails = buildList {
        barVehicle.modelYear?.let { add("Year" to it) }
        barVehicle.trim?.let { add("Trim" to it) }
        add("Model" to barVehicle.model)
        chargePercent?.let { add("Charge" to it) }
        chargeRange?.let { add("Range" to it) }
    }
    ExpandableMapLayer(
        isExpanded = openVin != null,
        originBounds = originBounds,
        location = hostLocation,
        vehicleName = barVehicle.name,
        chargePercent = chargePercent,
        chargeRange = chargeRange,
        details = carDetails,
        photo = state.value.imageUrls[barVehicle.vin],
        barSelected = centralMap.selectedVin != null,
        selectedIsExpanded = centralMap.selectedVin == null || centralMap.selectedVin == hostVehicle.vin,
        onDeselect = { centralMap.selectedVin = null },
        onDismissDrag = { centralMap.dismissFade = it },
        deviceLocation = deviceLocation,
        mapState = centralMap.mapState,
        map = { m, i -> centralMap.Content(m, i) },
        onRefreshLocation = locateHostVehicle,
        deviceRequest = rememberDeviceLocationRequest(vm),
        // The real command-pending flag, not a guessed timer -- see MapTopBar's own doc -- so the refresh icon's spin genuinely
        // tracks the in-flight fetch this same button just kicked off.
        refreshing = state.value.isPending(hostVehicle.vin, CarCommandIds.LOCATE),
        frostHaze = frostHaze,
        chromeInsets = centralMap.chromeInsets,
        beforeClose = {
            // The car page moves to the chosen car behind the map; the map collapses onto its card once that card is on screen.
            centralMap.chosenVin?.let { chosen ->
                centralMap.closingTo = chosen
                val screen = windowSize
                withTimeoutOrNull(CloseWaitMs) {
                    snapshotFlow { centralMap.originBoundsFor(chosen).value }
                        .first { r -> r != null && r.overlaps(Rect(0f, 0f, screen.width.toFloat(), screen.height.toFloat())) }
                }
            }
        },
        onDismiss = {
            // The map closes onto the car last chosen on it, and the app is on that car, whatever the pager settled on meanwhile.
            centralMap.chosenVin?.let { chosen ->
                centralMap.homeVin = chosen
                state.value.vehicles.indexOfFirst { it.vin == chosen }.takeIf { it >= 0 }?.let { vm.selectIndex(it) }
            }
            centralMap.selectedVin = null
            centralMap.vin = null
            centralMap.closingTo = null
        },
    )
}

/** The longest the map waits for the car page to reach the car it collapses onto, before it collapses regardless. */
private const val CloseWaitMs = 1_500L
