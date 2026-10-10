package com.bloo.bluelink.ui

/**
 * The Location pebble (map, address, distance to car, the car's local weather) and its supporting
 * pieces: CarMap, weatherIcon/weatherTint and the openUrl/openApp/dial launchers.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.distanceMilesTo
import com.bloo.bluelink.data.formatDistance
import kotlinx.coroutines.launch

/**
 * Asks for the phone's position so the map can draw "you are here". [ask] always asks; [askOnce]
 * asks at most once per app run (for a map opening with no fix). A grant starts the live
 * subscription immediately.
 */
internal class DeviceLocationRequest(val ask: () -> Unit, val askOnce: () -> Unit)

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
            val granted = context.hasLocationPermission()
            if (granted) {
                vm.beginLiveDeviceLocation()
                vm.refreshDeviceLocation()
            } else {
                launcher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        DeviceLocationRequest(ask = ask, askOnce = {
            if (!askedForDeviceLocationThisRun) {
                askedForDeviceLocationThisRun = true
                ask()
            }
        })
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
            toasts?.show("Location permission denied. Enable it in Settings > Apps > Bloo to see your position.", ToastKind.ERROR)
        }
    }
    return {
        val granted = context.hasLocationPermission()
        if (granted) vm.locate(v) else fineLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@Composable
internal fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
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
    val locating = state.isPending(v.vin, "locate")
    // Show the place name (or a hint) in the header so it's visible even collapsed.
    val summary = place ?: if (location != null) "Located" else "Not located yet"
    Pebble(
        v, "location", "Location & Weather", Icons.Filled.LocationOn, state, vm, modifier, summary = summary,
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
        AnimatedVisibility(
            visible = location == null,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            Text("Tap Locate to query the car's current position.")
        }
        // Mirror of the "not located yet" AnimatedVisibility above -- same pebble, same boolean
        // flip, only the empty side had the treatment.
        AnimatedVisibility(
            visible = location != null,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            val loc = location
            if (loc != null) {
                // Live device-location tracking runs app-wide (started in loadGarageInner), not per
                // pebble.
                Column(
                    verticalArrangement = Arrangement.spacedBy(GapGroup)
                ) {
                // A live CarMap; inside the garage it shares pan/zoom/tiles with the full-screen map and can expand into it.
                val expandedMap = LocalExpandedMap.current
                val isExpanded = expandedMap?.vin == v.vin
                val ownMapState = remember { CarMapState() }
                CarMap(
                    loc,
                    Modifier
                        .fillMaxWidth()
                        .height(if (glance) 130.dp else 220.dp)
                        .clip(StandardShape)
                        .then(
                            if (expandedMap == null) Modifier else Modifier
                                .onGloballyPositioned {
                                    expandedMap.originBoundsFor(v.vin).value = Rect(it.positionOnScreen(), it.size.toSize())
                                    expandedMap.locationFor(v.vin).value = loc
                                }
                                .graphicsLayer { alpha = if (isExpanded) 0f else 1f },
                        ),
                    state = expandedMap?.mapStateFor(v.vin) ?: ownMapState,
                    deviceLocation = state.deviceLocation,
                    // Static in the garage: a horizontal swipe over it turns the car page.
                    interactive = false,
                )
                MapFeatureRow(
                    features = listOfNotNull(
                        expandedMap?.let { host -> MapFeature(Icons.Filled.Fullscreen, "Expand") { host.vin = v.vin } },
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
                            WeatherStripe(cw, fahrenheit, place ?: "At the car")
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

// --- Weather --------------------------------------------------------------
