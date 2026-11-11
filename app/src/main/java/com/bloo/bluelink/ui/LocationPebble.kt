package com.bloo.bluelink.ui

/**
 * The Location pebble (map, address, distance to car, and the car's local weather where it is parked)
 * and its supporting pieces: CarMap, weatherIcon/weatherTint and the openUrl/openApp/dial launchers.
 */

import com.bloo.bluelink.data.CarCommandIds
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.distanceMilesTo
import com.bloo.bluelink.data.formatDistance
import kotlinx.coroutines.launch
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable

/**
 * Asks for the phone's position so the map can draw "you are here". [ask] always asks; [askOnce]
 * asks at most once per app run (for a map opening with no fix); [refresh] grabs a fresh fix if
 * permission is already granted. A grant starts the live subscription immediately.
 */
internal class DeviceLocationRequest(
    val ask: () -> Unit,
    val askOnce: () -> Unit,
    val refresh: () -> Unit,
)

private var askedForDeviceLocationThisRun = false

@Composable
internal fun rememberDeviceLocationRequest(vm: AppViewModel): DeviceLocationRequest {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.beginLiveDeviceLocation(restart = true)
            vm.refreshDeviceLocation()
        }
    }
    return remember(vm) {
        val ask = {
            if (context.hasLocationPermission()) {
                vm.ensureLiveDeviceLocation()
                vm.refreshDeviceLocation()
            } else {
                launcher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        DeviceLocationRequest(
            ask = ask,
            askOnce = {
                if (!askedForDeviceLocationThisRun) {
                    askedForDeviceLocationThisRun = true
                    ask()
                }
            },
            refresh = { if (context.hasLocationPermission()) vm.refreshDeviceLocation() },
        )
    }
}

/**
 * A "Locate" action for [v] that requests ACCESS_FINE_LOCATION first if not granted (it backs the
 * map's device-location dot and is otherwise only requested from AutoLock settings).
 */
@Composable
internal fun rememberLocateAction(vm: AppViewModel, v: Vehicle): () -> Unit {
    val context = LocalContext.current
    val toasts = LocalToasts.current
    val fineLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // Fresh grant: restart the live subscription, which can't recover a job started
            // permission-less.
            vm.beginLiveDeviceLocation(restart = true)
            vm.locate(v)
        } else {
            // A permanent denial can only be undone from system app info, so the toast points there
            // instead of re-asking.
            toasts?.show("Location is off. Turn it on in Settings.", ToastKind.ERROR)
        }
    }
    return {
        val granted = context.hasLocationPermission()
        if (granted) vm.locate(v) else fineLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@Composable
internal fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    com.bloo.bluelink.perf.TrackRecompositions("LocationPebble")
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val fahrenheit = appearance.useFahrenheit
    val location = state.locations[v.vin]
    // See rememberLocateAction's own doc -- ACCESS_FINE_LOCATION is otherwise only ever requested
    // from AutoLock's settings screen, so tying the request to a "Locate" action is what lets a
    // user who's never touched AutoLock grant it at all.
    val locateWithPermission = rememberLocateAction(vm, v)
    val deviceRequest = rememberDeviceLocationRequest(vm)
    // In a pinned/glance context this is the identity pill's headline; the compact street + ZIP form
    // has no space to wrap on.
    val place = if (LocalForceExpanded.current) {
        state.placeZips[v.vin] ?: state.placeNames[v.vin]
    } else {
        state.placeNames[v.vin]
    }
    val locating = state.isPending(v.vin, CarCommandIds.LOCATE)
    // Show the place name (or a hint) in the header so it's visible even collapsed.
    val summary = place ?: if (location != null) "Located" else "Not located yet"
    Pebble(
        v, "location", "Location", Icons.Filled.LocationOn, state, vm, modifier, summary = summary,
        headerAction = PebbleHeaderAction(
            label = "Locate",
            icon = Icons.Filled.LocationOn,
            onClick = { locateWithPermission() },
            enabled = !locating,
            pending = locating,
            bounceIcon = true,
        ),
    ) {
        val glance = LocalForceExpanded.current
        ExpandVisibility(location == null, from = Alignment.Bottom) {
            RevealText("Tap Locate to query the car's current position.")
        }
        // Mirror of the "not located yet" AnimatedVisibility above -- same pebble, same boolean
        // flip, only the empty side had the treatment.
        ExpandVisibility(location != null, from = Alignment.Bottom) {
            val loc = location
            if (loc != null) {
                // Live device-location tracking runs app-wide (started in loadGarageInner), not per
                // pebble.
                Column(
                    verticalArrangement = Arrangement.spacedBy(GapGroup)
                ) {
                // The one map is drawn by the app's host (CentralMapHost), not here. This card only holds the map's place: the host draws the
                // map over it while it is closed, and grows it to the full screen when it is opened. Its rectangle is reported here.
                val host = LocalCentralMap.current
                // This card's own place for the map. The same car can be composed on two pages at once while the garage moves, so each
                // card reports into its own slot, and the map uses whichever is on screen.
                val cardSlot = remember(v.vin) { MapCardSlot(v.vin) }
                val revealRequester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                if (host != null) {
                    DisposableEffect(host, cardSlot) {
                        cardSlot.reveal = { revealRequester.bringIntoView() }
                        host.addCard(cardSlot)
                        onDispose { host.removeCard(cardSlot) }
                    }
                }
                val slot = Modifier.fillMaxWidth().height(if (glance) 130.dp else 220.dp)
                    .then(
                        if (host == null) Modifier else Modifier
                            .bringIntoViewRequester(revealRequester)
                            .onGloballyPositioned { cardSlot.bounds = Rect(it.positionOnScreen(), it.size.toSize()) },
                    )
                    .clip(StandardShape)
                    .semantics { contentDescription = "Map showing where ${v.name} is" }
                Box(slot) {
                    // Home car: the map covers this, and a tap that reaches it opens the map. Other cars: the same tap, with a label.
                    // The card's own map, of the shared map, centred on this car with every car on it. Tapping its empty part opens the map;
                    // tapping one of its icons selects that car. Taps on the map's own icons go to them, not to the card.
                    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(role = Role.Button) { host?.open(v.vin) },
                    )
                    // Only a card on screen builds its map: the others are off to the side of the pager, and building them costs time.
                    // Read through derivedStateOf: the card recomposes only when it goes on or off screen, not on every move of its rectangle.
                    val screenNow = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
                    val onScreen by remember(cardSlot, screenNow) {
                        androidx.compose.runtime.derivedStateOf {
                            cardSlot.bounds?.overlaps(Rect(0f, 0f, screenNow.width.toFloat(), screenNow.height.toFloat())) == true
                        }
                    }
                    if (onScreen) host?.CardContent(
                        Modifier.fillMaxSize(),
                        MapInputs(loc, state.deviceLocation, interactive = false, tileArea = windowSize, panWeight = { 1f }),
                    )
                }
                MapFeatureRow(
                    features = listOfNotNull(
                        host?.let { h -> MapFeature(Icons.Filled.Fullscreen, "Expand") { h.open(v.vin) } },
                        MapFeature(Icons.Filled.Map, "Open in Maps") { openInExternalMaps(context, loc, v.name) },
                    ),
                )
                if (!glance && place == null) StatusRow("Location", loc.coordString())
                // Phone-to-car distance; absent (not zero) until a device fix exists.
                if (!glance) {
                    state.deviceLocation?.let { device ->
                        StatusRow("Distance", formatDistance(device.distanceMilesTo(loc), appearance.metricDistance))
                    }
                }
                // Weather where the car is parked, fetched lazily once there is a fix and not
                // already loading.
                val carWeather = state.carWeather[v.vin]
                val weatherLoading = state.isPending(v.vin, "carWeather")
                LaunchedEffect(loc.latitude, loc.longitude) {
                    if (carWeather == null && !weatherLoading) vm.loadCarWeather(v)
                }
                // The phone's own weather; nudged here in case a fix landed without triggering a
                // refresh.
                val phoneWeather = state.phoneWeather
                LaunchedEffect(state.deviceLocation?.latitude, state.deviceLocation?.longitude) {
                    if (state.deviceLocation != null && phoneWeather == null) vm.loadPhoneWeather()
                }
                // Its own PopVisible: weather can arrive AFTER this pebble is already open (it's a
                // separate fetch triggered above), so this row pops in live rather than only ever
                // being present from the first frame -- same idiom the Climate pebble's
                // smart-climate section uses.
                PopVisible(visible = carWeather != null) {
                    val cw = carWeather
                    if (cw != null) {
                        if (glance) {
                            // The header already says where the car is; the caption is only for when it has no address to say.
                            WeatherStripe(cw, fahrenheit, if (place == null) "At the car" else null)
                        } else {
                            WeatherLocations(
                                car = cw,
                                phone = phoneWeather,
                                place = place,
                                devicePlace = state.devicePlace,
                                milesApart = state.deviceLocation?.let { it.distanceMilesTo(loc) },
                                fahrenheit = fahrenheit,
                                metric = appearance.metricDistance,
                            )
                        }
                    }
                }
                // "Open in maps" + "Expand" render under the map, via the MapFeatureRow above.
                }
            }
        }
    }
}

// --- Weather inside the Location pebble -----------------------------------

