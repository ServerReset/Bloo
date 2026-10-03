package com.bloo.bluelink.ui

/**
 * The Location pebble (map, address, distance to car, and the car's own local
 * weather) plus its supporting pieces: WeatherStripe/WeatherDetail, CarMap,
 * weatherIcon/weatherTint and the openUrl/openApp/dial launchers -- extracted
 * from Pebbles.kt. The standalone "Weather" pebble (a separate global readout
 * of the user's configured "home" location) was folded into this one; see
 * [WeatherDetail]'s own doc.
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.distanceMilesTo
import com.bloo.bluelink.data.formatDistance
import kotlinx.coroutines.launch

/**
 * Asking for the phone's own position, so the map can draw "you are here". [ask] always asks (the
 * Me button); [askOnce] is for the moment a map opens with no fix yet, and asks at most once per
 * app run so a refusal is not nagged about every time the map is opened. Granting starts the live
 * subscription straight away, so the dot appears in the same session.
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
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
 * A "Locate" action for [v] that requests ACCESS_FINE_LOCATION first if it isn't already
 * granted, instead of calling [AppViewModel.locate] straight into a permission check that
 * silently returns nothing.
 *
 * ACCESS_FINE_LOCATION is what backs the map's own "device location" blue dot
 * ([UiState.deviceLocation], via [com.bloo.bluelink.autolock.LocationHelper] -- see its own
 * doc), and is otherwise only ever requested from AutoLock's settings screen -- a user who's
 * never touched AutoLock had no way to grant it at all, so the dot silently never appeared:
 * not a rendering bug, [AppViewModel.refreshDeviceLocation] was faithfully calling
 * LocationHelper every refresh and getting null back every single time from a permission
 * check that had nothing to request it. Reported directly as "the map is also not showing
 * the person's location" -- tying the request to a "Locate" action means granting it happens
 * as a direct result of something the user already does, not a surprise prompt.
 *
 * Originally only wired into [LocationPebble]'s own compact map -- GarageScreen's
 * full-screen [ExpandableMapLayer] had its OWN "Locate" (the top bar's refresh icon) calling
 * [AppViewModel.locate] directly, bypassing this permission check entirely. Anyone who only
 * ever used the expanded map (never the compact pebble's own button) could never be prompted
 * at all, reported directly a second time against that exact screen. Hoisted here so both
 * call sites share the one request flow instead of it living on only one of them.
 */
@Composable
internal fun rememberLocateAction(vm: AppViewModel, v: Vehicle): () -> Unit {
    val context = LocalContext.current
    val toasts = LocalToasts.current
    val fineLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // A fresh grant, not the "already had it" path below -- restart the live
            // subscription now that it can actually produce something (see
            // beginLiveDeviceLocation's own doc for why the plain no-arg call can't
            // recover a job that started permission-less).
            vm.beginLiveDeviceLocation(restart = true)
            vm.locate(v)
        } else {
            // Silently doing nothing here was its own bug: a denial (including a PERMANENT
            // one, where Android won't show this dialog again at all) looked identical to
            // the dot just never appearing, with no way for the user to tell WHY or how to
            // fix it -- reported as "the map still doesn't show the location of the phone"
            // with no further symptom to go on. A permanent denial can only be undone from
            // the system's own app-info screen, not from another in-app prompt, so the toast
            // points there instead of re-asking.
            toasts?.show("Location permission denied. Enable it in Settings > Apps > Bloo to see your position.", "error")
        }
    }
    return {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.locate(v) else fineLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@Composable
internal fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val fahrenheit = appearance.useFahrenheit
    val location = state.locations[v.vin]
    // See rememberLocateAction's own doc -- ACCESS_FINE_LOCATION is otherwise only ever
    // requested from AutoLock's settings screen, so tying the request to a "Locate" action
    // is what lets a user who's never touched AutoLock grant it at all.
    val locateWithPermission = rememberLocateAction(vm, v)
    val deviceRequest = rememberDeviceLocationRequest(vm)
    // On the cover, this becomes the identity pill's own headline, riding beside the car name
    // ("810 Devonshire Way, Sunnyvale  ·  Daisy") -- the long form there reliably wrapped
    // that pill onto two lines, a real reported "looks bad" bug. The compact form (street +
    // ZIP, not street + city) is shorter and has no internal space to wrap on. Phone keeps the
    // long form in its own header, which has the room for it.
    val place = if (LocalForceExpanded.current) {
        state.placeZips[v.vin] ?: state.placeNames[v.vin]
    } else {
        state.placeNames[v.vin]
    }
    val locating = state.isPending(v.vin, "locate")
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
        // NOT alwaysExpandedInSimpleMode. That flag is for a pebble whose entire body is one
        // setting, where a chevron guarding a single switch is worse than no chevron at all --
        // and it removes the chevron, so a card that has it CANNOT be collapsed in simple mode.
        // This one renders a map, a weather stripe and a button; locking it permanently open was
        // the reported "can no longer collapse the location card". Five other pebbles carried
        // the same mistake and were fixed earlier; these were the two that were missed.
    ) {
        val glance = LocalForceExpanded.current
        AnimatedVisibility(
            visible = location == null,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            Text("Tap Locate to query the car's current position.")
        }
        // Mirror of the "not located yet" AnimatedVisibility above -- same
        // pebble, same boolean flip, only the empty side had the treatment.
        AnimatedVisibility(
            visible = location != null,
            enter = expandEnterSized(Alignment.Bottom),
            exit = expandExitSized(Alignment.Bottom),
        ) {
            val loc = location
            if (loc != null) {
                // Live device-location tracking (see AppViewModel.beginLiveDeviceLocation)
                // now runs for the whole time the app is open, started once from
                // loadGarageInner -- not tied to this pebble's own visibility any more.
                Column(
                    verticalArrangement = Arrangement.spacedBy(GapGroup)
                ) {
                // COVER SCREEN: lead with the place-name hero (the cover drops the header
                // where the place summary otherwise shows), and shrink the map so hero +
                // map + coords + weather + button fit without overflowing the ~1-inch tile.
                if (glance) {
                    // The subline used to always be the raw coordinate string, even
                    // once `place` had resolved into the headline right next to it --
                    // showing an address and its own coordinates in the same glance.
                    // Only fall back to coordinates here while nothing better exists
                    // yet; once an address resolves, it's the only thing shown.
                    // No cover hero: `place` is already the tile's summary and therefore its
                    // headline. This tile rendered the address three times at once -- headline,
                    // hero and the map stripe's caption.
                }
                // A live CarMap, visible in the pebble at all times -- NOT a
                // placeholder. Sharing expandedMap.mapStateFor(v.vin) with the
                // full-screen overlay (ExpandableMapLayer, in GarageScreen) means
                // both read/write the exact same pan/zoom/tile cache; this one is
                // simply hidden (alpha 0, not removed -- it stays composed and
                // measured so onGloballyPositioned keeps reporting a live,
                // current originBounds) for the moment its OWN vehicle is the one
                // expanded full-screen.
                val expandedMap = LocalExpandedMap.current
                if (expandedMap != null) {
                    val isExpanded = expandedMap.vin == v.vin
                    CarMap(
                        loc,
                        Modifier
                            .fillMaxWidth()
                            .height(if (glance) 130.dp else 220.dp)
                            .clip(StandardShape)
                            .onGloballyPositioned {
                                expandedMap.originBoundsFor(v.vin).value = Rect(it.positionOnScreen(), it.size.toSize())
                                expandedMap.locationFor(v.vin).value = loc
                            }
                            .graphicsLayer { alpha = if (isExpanded) 0f else 1f },
                        state = expandedMap.mapStateFor(v.vin),
                        deviceLocation = state.deviceLocation,
                    )
                    // Real buttons underneath the map, not floating on top of it --
                    // reported directly from a screenshot as unwanted. Same
                    // MapFeature/MapFeatureRow shape the full-screen map's own
                    // bottom toolbar already uses, so a "do something with this
                    // map" pill reads the same everywhere it appears.
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(Icons.Filled.Fullscreen, "Expand") { expandedMap.vin = v.vin },
                            MapFeature(Icons.Filled.Map, "Open in Maps") { openInExternalMaps(context, loc, v.name) },
                        ),
                    )
                } else {
                    // Not inside the garage's expandable map host: a plain map, with no expand to offer.
                    CarMap(
                        loc,
                        Modifier
                            .fillMaxWidth()
                            .height(if (glance) 130.dp else 220.dp)
                            .clip(StandardShape),
                        state = remember { CarMapState() },
                        deviceLocation = state.deviceLocation,
                    )
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(Icons.Filled.Map, "Open in Maps") { openInExternalMaps(context, loc, v.name) },
                        ),
                    )
                }
                if (!glance && place == null) StatusRow("Location", loc.coordString())
                // How far the phone is from the car right now -- the phone's own
                // last-known fix (kept live app-wide via AppViewModel.beginLiveDeviceLocation)
                // against this car's. Absent (not just zero) until a device fix has ever
                // landed, same "don't assert a number you don't actually have" rule the
                // lock-state / odometer rows elsewhere in this app already follow.
                if (!glance) {
                    state.deviceLocation?.let { device ->
                        StatusRow("Distance", formatDistance(device.distanceMilesTo(loc), appearance.metricDistance))
                    }
                }
                // Weather where the car is parked. Fetched lazily once we have a fix.
                // Only load if not already fetched/loading to prevent redundant requests
                val carWeather = state.carWeather[v.vin]
                val weatherLoading = state.isPending(v.vin, "carWeather")
                LaunchedEffect(loc.latitude, loc.longitude) {
                    if (carWeather == null && !weatherLoading) vm.loadCarWeather(v)
                }
                // Its own PopVisible: weather can arrive AFTER this pebble is already
                // open (it's a separate fetch triggered above), so this row pops in
                // live rather than only ever being present from the first frame --
                // same idiom the Climate pebble's smart-climate section uses. Full detail
                // (feels like, high/low, humidity, wind), not just the compact WeatherStripe
                // -- this absorbed the old standalone Weather pebble, which showed exactly
                // this, so folding it in here as a one-line stripe would have been a real
                // loss of detail, not just a relocation.
                PopVisible(visible = carWeather != null) {
                    if (carWeather != null) {
                        if (glance) {
                            WeatherStripe(carWeather, fahrenheit, place ?: "At the car")
                        } else {
                            WeatherDetail(carWeather, fahrenheit, appearance.metricDistance)
                        }
                    }
                }
                // "Open in maps" + "Expand" render right under the map itself, via
                // the MapFeatureRow call inside the if/else above -- see there.
                }
            }
        }
    }
}

// --- Weather --------------------------------------------------------------
