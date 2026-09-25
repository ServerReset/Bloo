@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * The Location pebble (map, address, distance to car, and the car's own local
 * weather) plus its supporting pieces: WeatherStripe/WeatherDetail, CarMap,
 * weatherIcon/weatherTint and the openUrl/openApp/dial launchers -- extracted
 * from Pebbles.kt. The standalone "Weather" pebble (a separate global readout
 * of the user's configured "home" location) was folded into this one; see
 * [WeatherDetail]'s own doc.
 */

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.bloo.bluelink.data.ChargerFilters
import com.bloo.bluelink.data.ChargerStation
import com.bloo.bluelink.data.matches
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.distanceMilesTo
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.formatSpeed
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt


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
    val fineLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.locate(v) }
    return {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.locate(v) else fineLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

@Composable
internal fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val fahrenheit = appearance.useFahrenheit
    val location = state.locations[v.vin]
    // See rememberLocateAction's own doc -- ACCESS_FINE_LOCATION is otherwise only ever
    // requested from AutoLock's settings screen, so tying the request to a "Locate" action
    // is what lets a user who's never touched AutoLock grant it at all.
    val locateWithPermission = rememberLocateAction(vm, v)
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
        v, "location", "Location", Icons.Filled.LocationOn, state, vm, dragHandle, summary = summary,
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
        val coverGlance = LocalForceExpanded.current
        AnimatedVisibility(
            visible = location == null,
            enter = expandEnter(Alignment.Bottom),
            exit = expandExit(Alignment.Bottom),
        ) {
            Text("Tap Locate to query the car's current position.")
        }
        // Mirror of the "not located yet" AnimatedVisibility above -- same
        // pebble, same boolean flip, only the empty side had the treatment.
        AnimatedVisibility(
            visible = location != null,
            enter = expandEnter(Alignment.Bottom),
            exit = expandExit(Alignment.Bottom),
        ) {
            val loc = location
            if (loc != null) {
                // Live device-location tracking (see AppViewModel.beginLiveDeviceLocation)
                // now runs for the whole time the app is open, started once from
                // loadGarageInner -- not tied to this pebble's own visibility any more.
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // COVER SCREEN: lead with the place-name hero (the cover drops the header
                // where the place summary otherwise shows), and shrink the map so hero +
                // map + coords + weather + button fit without overflowing the ~1-inch tile.
                if (coverGlance) {
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
                            .height(if (coverGlance) 130.dp else 220.dp)
                            .clip(RoundedCornerShape(18.dp))
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
                    var showMapSheet by remember { mutableStateOf(false) }
                    var mapOriginBounds by remember { mutableStateOf<Rect?>(null) }
                    CarMap(
                        loc,
                        Modifier
                            .fillMaxWidth()
                            .height(if (coverGlance) 130.dp else 220.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .onGloballyPositioned { mapOriginBounds = Rect(it.positionOnScreen(), it.size.toSize()) },
                        state = remember { CarMapState() },
                        deviceLocation = state.deviceLocation,
                    )
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(Icons.Filled.Fullscreen, "Expand") { showMapSheet = true },
                            MapFeature(Icons.Filled.Map, "Open in Maps") { openInExternalMaps(context, loc, v.name) },
                        ),
                    )
                    if (showMapSheet) {
                        CarMapSheet(
                            loc, v.name, state.deviceLocation, mapOriginBounds,
                            // Same "Locate" entry point the pebble's own header action
                            // uses -- this flip-cover fallback sheet had no refresh
                            // action at all before; wiring it up matches the primary
                            // (LocalExpandedMap) path instead of leaving it behind.
                            onRefreshLocation = { locateWithPermission() },
                            refreshing = locating,
                        ) { showMapSheet = false }
                    }
                }
                // Same reasoning as the cover hero above: a resolved address is
                // already the pebble's header/summary, so a permanent raw-coordinate
                // row here was redundant with it every single time -- exactly what
                // "should be an address, not coordinates" was pointing at. Only shown
                // as a fallback while geocoding hasn't (yet, or ever) resolved a name.
                if (!coverGlance && place == null) StatusRow("Location", loc.coordString())
                // How far the phone is from the car right now -- the phone's own
                // last-known fix (kept live app-wide via AppViewModel.beginLiveDeviceLocation)
                // against this car's. Absent (not just zero) until a device fix has ever
                // landed, same "don't assert a number you don't actually have" rule the
                // lock-state / odometer rows elsewhere in this app already follow.
                if (!coverGlance) {
                    state.deviceLocation?.let { device ->
                        StatusRow("Distance", formatDistance(device.distanceMilesTo(loc), appearance.unitSystem == "metric"))
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
                        if (coverGlance) {
                            WeatherStripe(carWeather, fahrenheit, place ?: "At the car")
                        } else {
                            WeatherDetail(carWeather, fahrenheit, appearance.unitSystem == "metric")
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

/** The icon for a condition, picking a sun/moon variant by day vs night. */
internal fun weatherIcon(code: WeatherCode, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code.toCode(), isDay)

@Composable
internal fun weatherTint(code: WeatherCode, isDay: Boolean): Color =
    com.bloo.uicommon.weatherTint(code.toCode(), isDay, MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * A compact one-line weather readout: icon, temperature and condition, with a
 * small caption (place name) underneath. Used inside the Location pebble.
 */
@Composable
internal fun WeatherStripe(weather: Weather, fahrenheit: Boolean, caption: String) {
    val tint = weatherTint(weather.condition, weather.isDay)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(weatherIcon(weather.condition, weather.isDay), contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(weather.condition.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RollingNumber(
            text = weather.tempLabel(fahrenheit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Full weather detail for [weather]: icon, big temperature, condition, and
 * feels-like/high-low/humidity/wind rows. Used inside [LocationPebble] for the
 * car's own local weather -- this used to be the standalone "Weather" pebble's
 * body (a separate global readout of the user's configured "home" location,
 * shown identically on every car), folded in here once the Location pebble
 * became the one place a car's surroundings are shown. `state.homeWeather` and
 * [AppViewModel.loadHomeWeather] still exist -- [ClimatePebble] falls back to
 * home weather for its smart-climate ambient estimate when a car has no fix of
 * its own yet -- only the dedicated garage card for it is gone.
 */
@Composable
internal fun WeatherDetail(weather: Weather, fahrenheit: Boolean, metric: Boolean) {
    val tint = weatherTint(weather.condition, weather.isDay)
    // ONE child, not five. This is rendered inside [PopVisible]'s AnimatedVisibility, and
    // AnimatedVisibility's own layout places every root composable it is given at the SAME
    // origin -- it is a slot for one child (or for a container that stacks several). A bare
    // Row followed by four StatusRows therefore stacked all five on top of each other, which
    // is the reported "overlap on tons of the text" in the weather stats. A Column gives the
    // slot the single child it expects, and spaces the rows by the same 12dp the location
    // pebble's own Column was providing before this AnimatedVisibility sat between them.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                weatherIcon(weather.condition, weather.isDay),
                contentDescription = weather.condition.label,
                tint = tint,
                modifier = Modifier.size(64.dp),
            )
            Column(Modifier.weight(1f)) {
                RollingNumber(
                    text = weather.tempLabel(fahrenheit),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(weather.condition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        StatusRow("Feels like", weather.feelsLikeLabel(fahrenheit))
        weather.highLowLabel(fahrenheit)?.let { StatusRow("High / low", it) }
        weather.humidity?.let { StatusRow("Humidity", "$it%") }
        StatusRow("Wind", formatSpeed(weather.windKph, metric))
    }
}

/** Zoom bounds for [CarMap]'s zoom, pinch or button-driven -- 3 is "half the
 *  continent", 19 is past what OSM actually serves tiles for. */
private const val CarMapMinZoom = 3
private const val CarMapMaxZoom = 19

/** Where every [CarMapState] starts: street level, car dead-centre. */
private const val CarMapDefaultZoom = 15

/**
 * Live view state for one [CarMap] instance: zoom level and the pixel pan offset
 * from the car-centred origin, plus the pinch gesture's own running accumulator.
 * Pulled out of CarMap's body (three separate `remember`ed vars, previously) into
 * one object for two reasons:
 *
 *  - A caller that wants a DISCRETE action -- the +/- zoom buttons, the
 *    full-screen map's "Recentre" -- needs something to call into, not raw
 *    `remember` state private to the composable that drew the gesture.
 *  - The full-screen map ([CarMapFullScreenDialog]) needs its OWN state,
 *    independent of whatever the small inline map is currently panned/zoomed
 *    to -- expanding to full screen is a bigger canvas to look at the SAME car
 *    on, not a continuation of one specific pan gesture.
 *
 * This is also the intended extension point for future map features that carry
 * their own live view state (a drawn route, a second tracked point, a saved
 * "look here again" bookmark): add fields/methods here rather than threading
 * more loose state through CarMap's own parameters.
 */
internal class CarMapState {
    var zoom by mutableIntStateOf(CarMapDefaultZoom)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set

    // The pinch gesture's own running scale, 1x at [zoom]'s own fetched tile
    // resolution -- and NOT just internal bookkeeping the way an accumulator toward a
    // silent step change would be. CarMap applies this directly as a graphicsLayer
    // scale over the already-drawn tile grid, every frame of the gesture, which is
    // what makes zooming feel genuinely fluid instead of jumping between whole levels:
    // reported directly as wanting a continuous zoom, not steps. Only CROSSING a
    // whole octave (2x or 0.5x) actually swaps which tiles are fetched/on screen --
    // see pinch() below -- everything in between is pure visual scale, no new tiles,
    // no snapping.
    var scale by mutableFloatStateOf(1f)
        private set

    /** True once the user has actually panned or pinched this map -- an automatic
     *  "fit both the car and the device in view" (see [fitBothLocations]) should
     *  only ever run BEFORE that, never yanking the view out from under a hand
     *  that's already deliberately looked somewhere else. [recenter] clears this,
     *  since that's an explicit "start over". */
    private var userAdjusted = false

    /** A continuous drag, in screen pixels. Drag right -> the map (and
     *  everything drawn on it) should slide right with the finger, exactly like
     *  sliding a sheet of paper -- so the WORLD-pixel offset accumulates in the
     *  same direction as the drag; see CarMap's own originX/originY, which
     *  subtract this to shift what's visible. */
    fun pan(dx: Float, dy: Float) {
        userAdjusted = true
        panX += dx
        panY += dy
    }

    /** One frame of a pinch gesture's multiplicative ratio, folded straight into
     *  [scale] -- see that property's own doc. The pan offset is halved/doubled in
     *  step with every whole-LEVEL change (crossing 2x/0.5x): it is measured in tile
     *  PIXELS at the OLD zoom, and a zoom step doubles/halves how many pixels the
     *  same world distance covers. */
    fun pinch(ratio: Float) {
        userAdjusted = true
        scale *= ratio
        while (scale >= 2f && zoom < CarMapMaxZoom) {
            zoom++; panX *= 2f; panY *= 2f; scale /= 2f
        }
        while (scale <= 0.5f && zoom > CarMapMinZoom) {
            zoom--; panX /= 2f; panY /= 2f; scale *= 2f
        }
        // At the zoom ceiling/floor there's no further level left to snap into, so
        // the continuous scale itself has to be clamped instead of drifting
        // arbitrarily far past the octave boundary. Left unclamped, pinching out
        // past [CarMapMinZoom] kept shrinking the already-fetched tile grid
        // indefinitely -- CarMap's own tile fetch is sized for the box at scale
        // 1, so a scale well below that shrinks the grid smaller than the
        // viewport it needs to fill, leaving blank, tile-less margins around the
        // edges. Reported directly: "if you zoom out all the way, you don't see
        // stuff anymore, it cuts off the edges." CarMap's own tile-range fetch
        // also over-fetches by 1/scale to cover the worst case within this
        // clamp -- see its own `coverage` doc -- so between the two, the grid
        // now always covers the viewport regardless of where scale sits.
        scale = scale.coerceIn(0.5f, 2f)
    }

    /** Back to the car, dead centre, default zoom -- the full-screen map's
     *  "Recentre" feature. Clears [userAdjusted]: an explicit "start over" that
     *  also re-allows a fresh [fitBothLocations] to run, the same as a map that
     *  had never been touched at all. */
    fun recenter() {
        userAdjusted = false
        zoom = CarMapDefaultZoom
        panX = 0f
        panY = 0f
        scale = 1f
    }

    /**
     * Zooms out (never in past [CarMapDefaultZoom]) just enough that both the car
     * (which stays dead-centre, per [pan]'s own doc) and the device's own location
     * fit on screen with a margin -- called automatically, once, the first time
     * both fixes are available and the map is still at rest (see [userAdjusted]).
     *
     * Without this, the device-location dot -- drawn at its true tile-projected
     * offset from the car, same as the car's own pin -- was reported as simply
     * never appearing "alongside" the car: at the default street-level zoom, a
     * phone even a few blocks from the car sits many SCREENS of pixels outside
     * the visible box, not just near an edge, so nothing after that default ever
     * brought it into view on its own.
     */
    fun fitBothLocations(
        carLat: Double, carLon: Double,
        deviceLat: Double, deviceLon: Double,
        viewWidthPx: Float, viewHeightPx: Float,
    ) {
        if (userAdjusted || viewWidthPx <= 0f || viewHeightPx <= 0f) return
        var z = CarMapDefaultZoom
        while (z > CarMapMinZoom) {
            val dxPx = kotlin.math.abs(MapTiles.tileX(deviceLon, z) - MapTiles.tileX(carLon, z)) * MapTiles.TILE_PX
            val dyPx = kotlin.math.abs(MapTiles.tileY(deviceLat, z) - MapTiles.tileY(carLat, z)) * MapTiles.TILE_PX
            // The car sits dead-centre, so the device only has to fit within HALF
            // the viewport on whichever side it's offset toward -- hence *2, not
            // the raw distance. 0.8x leaves some breathing room rather than
            // placing the device right at the very edge.
            if (dxPx * 2f <= viewWidthPx * 0.8f && dyPx * 2f <= viewHeightPx * 0.8f) break
            z--
        }
        zoom = z
        panX = 0f
        panY = 0f
        scale = 1f
    }
}

@Composable
internal fun rememberCarMapState(): CarMapState = remember { CarMapState() }

/** The inclusive tile-index range [CarMap] currently needs fetched -- see its own
 *  `range`/`derivedStateOf` doc for why this is its own equatable value rather
 *  than four loose Ints computed inline. */
private data class TileRange(val firstX: Int, val firstY: Int, val lastX: Int, val lastY: Int)

/**
 * Cross-composable state for expanding a Location pebble's own compact map
 * "in place" -- when a host (currently only [GarageScreen]) provides one via
 * [LocalExpandedMap], the SAME [CarMapState] (so the same pan, zoom and already-
 * loaded tiles) and the compact map's own last on-screen rect carry across into
 * a full-screen overlay drawn elsewhere in that host's own tree, instead of the
 * expanded view starting over at a fresh state with a fresh tile fetch. That
 * fresh-state approach -- a genuinely separate [CarMap] composable driving its
 * own independent [CarMapState] inside a [CarMapSheet] Dialog -- is still what
 * happens when this is null (no host has set one up): it works, but reads as a
 * *copy* of the map fading in/growing rather than "literally that same
 * component" continuing, reported directly against the version that shipped
 * with only that path.
 *
 * Deliberately NOT `remember`ed inside [LocationPebble] itself: the compact map
 * and the expanded overlay are still two separate composable call sites (one
 * inline in this pebble's own layout slot, one in a full-screen overlay
 * elsewhere in the tree) even when they share this, so the state they share has
 * to live somewhere neither one owns exclusively -- the same reason
 * [HotSeatDrag] lives outside any one pebble.
 */
internal class ExpandedMapState {
    /** VIN of the car whose map is currently expanded, or null. */
    var vin by mutableStateOf<String?>(null)
    private val perVinMapState = mutableMapOf<String, CarMapState>()
    private val perVinOrigin = mutableMapOf<String, MutableState<Rect?>>()
    private val perVinLocation = mutableMapOf<String, MutableState<GeoLocation?>>()

    /** The one [CarMapState] a given car's compact map and expanded overlay both
     *  read/write -- created once per VIN, on first use, and kept for as long as
     *  this [ExpandedMapState] itself lives (its host's own lifetime). */
    fun mapStateFor(vin: String): CarMapState = perVinMapState.getOrPut(vin) { CarMapState() }

    /** The compact map's own last-measured on-screen rect for this VIN, kept
     *  live (via the compact map's own `onGloballyPositioned`) whether or not
     *  it's the currently-expanded one, so the moment it IS expanded there's
     *  already a real, current origin to grow from -- not a stale one from
     *  whenever this was last measured, or none at all. */
    fun originBoundsFor(vin: String): MutableState<Rect?> = perVinOrigin.getOrPut(vin) { mutableStateOf(null) }

    /** The location for this VIN's map, kept live so the screen-level map layer
     *  knows what location to display. */
    fun locationFor(vin: String): MutableState<GeoLocation?> = perVinLocation.getOrPut(vin) { mutableStateOf(null) }
}

/** Null (the default) when no host has set one up. See [ExpandedMapState]'s own doc. */
internal val LocalExpandedMap = staticCompositionLocalOf<ExpandedMapState?> { null }

/**
 * A small slippy map centred on the car, assembled from key-free OpenStreetMap raw
 * tiles. We compute the tiles needed to fill the box with the car at the centre,
 * draw each at its pixel offset, then drop a pin. This avoids the flaky static-map
 * render services that painted blank.
 *
 * Interactive: one- or two-finger drag pans, pinch zooms -- standard slippy-map
 * gestures, deliberately with no on-screen +/- buttons alongside them (tried once;
 * reported directly as unwanted -- pinch alone is the expected way to zoom a map,
 * and a button pair was one more thing sitting on top of the tiles). [state] is
 * pixel/tile-level view state for one CarMap instance; the default
 * `rememberCarMapState()` keeps it private to this call site, so panning one
 * Location pebble's map never affects another's, and a fresh `location` update
 * does not yank the view back to centre out from under a hand mid-pan/zoom --
 * only the ORIGIN each tile/the pin is drawn from moves with a new fix, same as
 * it always did.
 *
 * Also shows a second, smaller marker for the DEVICE's own last-known position
 * ([deviceLocation], owned by [UiState]/[AppViewModel] -- not fetched here) -- so
 * the map answers "how far away is the car from me" at a glance instead of only
 * ever showing where the car is with nothing to measure that against. Reported
 * directly. It is a reference point, not the map's subject: it never moves the
 * camera, which stays centred on the car exactly as before.
 */
@Composable
internal fun CarMap(
    location: GeoLocation,
    modifier: Modifier = Modifier,
    state: CarMapState = rememberCarMapState(),
    /**
     * The DEVICE's own last-known position (not the car's) -- [UiState.deviceLocation],
     * refreshed on app open/refresh and by "Locate", NOT fetched by CarMap itself. This
     * used to be a one-shot `LocationHelper.currentLocation()` call fired the moment any
     * CarMap composed, which meant it only ever reflected wherever the phone was the
     * FIRST time a Location pebble happened to render, never anything more current --
     * reported directly as wanting it to stay in step with the rest of the app's own
     * refresh cycle instead. Null (never fetched yet, or no permission/fix) simply
     * omits the marker.
     */
    deviceLocation: GeoLocation? = null,
    /** Nearby EV chargers to plot alongside the car/device pins -- see
     *  [com.bloo.bluelink.data.ChargerApi]'s own doc. Empty (the default) for every
     *  caller that hasn't opted into the "Chargers" map feature. */
    chargers: List<ChargerStation> = emptyList(),
    /** Tapping a charger pin -- null (the default) draws them but ignores taps,
     *  for a caller (like the compact pebble map) that never populates [chargers]
     *  in the first place. */
    onChargerClick: ((ChargerStation) -> Unit)? = null,
) {
    val context = LocalContext.current
    // Cold-start diagnostic -- see HeroVisual's matching mark for why. If the map is
    // expanded by default, its tile fetches (real network + decode work, if slower than
    // expected) are another candidate for the "black screen for a second or two" report.
    com.bloo.bluelink.data.StartupTrace.once("carmap-composing", "CarMap composing (first one)")

    // The car's own pin uses the app's actual dynamic/custom-palette primary --
    // not a fixed semantic role like `error` -- so a car with its own custom
    // palette shows ITS colour on the map, the same
    // way that colour already drives everything else on that car's screen.
    // Reported directly as wanting map colours "pulled from the dynamic color
    // of the app" rather than a hardcoded red.
    val pinColor = MaterialTheme.colorScheme.primary
    // The device's own position gets a second, DIFFERENT dynamic role
    // (secondary) rather than a hardcoded blue -- still theme-driven, just
    // visually distinct from the car's own primary-coloured pin.
    val deviceLocationColor = MaterialTheme.colorScheme.secondary
    // Same fix as pebbleCardEdge/glassTint (GlassChrome.kt): resolve dark from
    // the app's own ThemeMode override, not a raw isSystemInDarkTheme() read --
    // otherwise a user who forced Light/Dark against a differently-set system
    // theme got a map whose colour filter didn't match the rest of the app.
    // The `when` block this used to spell out in place is appIsDarkTheme() now
    // (Theme.kt), so all five sites that need this answer share one.
    val isDarkMode = appIsDarkTheme()

    // Adaptive map background: light map needs bright pins, dark needs adjustment
    // The map tiles themselves provide the visual theme, so minimal background needed
    val mapBackground = if (isDarkMode) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    // Client-side dark filter over these SAME OSM tiles, not a second tile source.
    // CARTO's Dark Matter basemap used to fill this role -- reverted after its
    // endpoint started serving an "API KEY REQUIRED" watermark over the whole tile
    // with no key configured, reported directly as a broken map. invert() +
    // hue-rotate(180deg) is the standard trick several map SDKs' own "quick dark
    // mode" use for turning a light raster map into a passable dark one without a
    // second tile source: inverting alone flips every hue to its raw RGB complement
    // (parks read magenta, water reads orange); the hue rotation brings each colour
    // back close to its original hue with the lightness still inverted. See
    // MapTiles.tileUrl's own doc for why this replaced a second tile provider
    // entirely rather than just swapping in a different (also key-gated) one.
    val darkMapFilter = remember(isDarkMode) {
        if (!isDarkMode) return@remember null
        val invert = android.graphics.ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        // The W3C hue-rotate(180deg) filter matrix, applied to the ALREADY-inverted
        // colour rather than the original -- postConcat runs `invert` first, then
        // this, exactly matching the CSS `filter: invert(1) hue-rotate(180deg)`
        // order the whole trick is borrowed from.
        val hueRotate180 = android.graphics.ColorMatrix(
            floatArrayOf(
                -0.574f, 1.430f, 0.144f, 0f, 0f,
                0.426f, 0.430f, 0.144f, 0f, 0f,
                0.426f, 1.430f, -0.856f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        invert.postConcat(hueRotate180)
        ColorFilter.colorMatrix(ColorMatrix(invert.array))
    }

    // The box's own real, measured size -- onSizeChanged, not BoxWithConstraints. Both
    // report the same numbers, but BoxWithConstraints is a SubcomposeLayout: its content
    // composes in a SEPARATE, deferred pass, which is avoidable overhead on a composable
    // that recomposes on every `location` update, i.e. continuously while the car/phone
    // is moving -- the exact frequency the rest of this app's onSizeChanged conversions
    // (PebbleShell.kt, Pebbles.kt) were made for. Zero-sized for the one frame before the
    // real size lands is safe here (unlike a width CAP elsewhere in the app, a wrong-low
    // default just means one frame with no tiles drawn yet, not a false layout decision).
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .background(mapBackground)
            .onSizeChanged { boxSizePx = it }
            .pointerInput(state) {
                detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                    state.pan(gesturePan.x, gesturePan.y)
                    state.pinch(gestureZoom)
                }
            },
    ) {
        val density = LocalDensity.current
        val tilePx = MapTiles.TILE_PX.toFloat()
        val wPx = boxSizePx.width.toFloat()
        val hPx = boxSizePx.height.toFloat()
        val zoom = state.zoom
        val span = MapTiles.span(zoom)
        val xTileF = MapTiles.tileX(location.longitude, zoom)
        val yTileF = MapTiles.tileY(location.latitude, zoom)
        val tileDp = with(density) { tilePx.toDp() }

        // Brings the device's own location into view (zooming out, never in) the
        // first time both fixes are known and the map is still at rest -- see
        // CarMapState.fitBothLocations's own doc for why: at the default
        // street-level zoom the device sits, more often than not, many SCREENS of
        // pixels outside the box, not just near an edge, so the blue dot below
        // was reported as simply never appearing "alongside" the car at all.
        LaunchedEffect(location.latitude, location.longitude, deviceLocation?.latitude, deviceLocation?.longitude, wPx, hPx) {
            val dev = deviceLocation
            if (dev != null) {
                state.fitBothLocations(location.latitude, location.longitude, dev.latitude, dev.longitude, wPx, hPx)
            }
        }

        // Which tiles are actually needed -- recomputed only when this genuinely
        // changes (a new zoom level, a resize, or the pan/scale crossing into a
        // different integer tile range), NOT on every single pixel of a live pan
        // or pinch. Reading state.panX/panY/scale directly as plain vals here
        // (the previous shape of this code) subscribed this whole composable --
        // and the per-tile AsyncImage/Modifier chain it drives, one full rebuild
        // PER VISIBLE TILE -- to recompose on every one of those pixels, reported
        // directly as poor map performance while panning. derivedStateOf reads
        // them the same way but only actually invalidates readers when the
        // DERIVED tile range changes, which for a drag is roughly once per whole
        // 256px tile crossed rather than every frame.
        //
        // fetchScaleCoverage over-fetches by 1/scale so this range still covers
        // the box even while a live pinch has shrunk the tile grid below scale 1
        // (see CarMapState.pinch's own clamp/doc): without it, the fetched range
        // matched the box's own unscaled size exactly, so the moment a pinch-out
        // shrank the content below that, blank/tile-less margins appeared around
        // the edges -- worst, and permanently, once pinched past CarMapMinZoom,
        // where scale can never recover via a further zoom-level snap. Reported
        // directly: "if you zoom out all the way, you don't see stuff anymore,
        // it cuts off the edges."
        val range by remember(zoom, xTileF, yTileF, wPx, hPx) {
            derivedStateOf {
                val fetchScaleCoverage = 1f / state.scale.coerceAtLeast(0.5f)
                val fetchW = wPx * fetchScaleCoverage
                val fetchH = hPx * fetchScaleCoverage
                val fetchOriginX = xTileF * tilePx - fetchW / 2f - state.panX
                val fetchOriginY = yTileF * tilePx - fetchH / 2f - state.panY
                TileRange(
                    firstX = floor(fetchOriginX / tilePx).toInt(),
                    firstY = floor(fetchOriginY / tilePx).toInt(),
                    lastX = floor((fetchOriginX + fetchW) / tilePx).toInt(),
                    lastY = floor((fetchOriginY + fetchH) / tilePx).toInt(),
                )
            }
        }

        // Warms Coil's cache for the NEXT whole zoom level in either direction, once
        // this one has been settled on for a moment -- reported directly as pinching
        // in/out being "pretty slow" because it "has to refresh all the tiles".
        // Crossing an octave boundary (see CarMapState.pinch) always needs genuinely
        // different tiles; the tiles AREN'T cached yet is the actual latency, not
        // anything about how they're requested.
        //
        // Two things this got wrong the first time, both reported directly as making
        // rapid pinching feel SLOWER than before this existed at all:
        //  1. loader.enqueue() starts its network fetch immediately and doesn't tie
        //     its own lifecycle to the calling coroutine -- cancelling this
        //     LaunchedEffect (which a fast zoom change does constantly, since it's
        //     keyed on `zoom`) stopped the FOR LOOP from enqueueing further tiles,
        //     but every request already handed to Coil kept running regardless.
        //     Someone "poking in or out really quickly" crosses several octaves in
        //     under a second, and each one fired off a fresh batch that never
        //     actually got cancelled -- competing with the VISIBLE tiles' own
        //     requests for the same small pool of OkHttp connections. `delay(300)`
        //     up front fixes this the same way any other debounce does: a rapid
        //     sequence of zoom changes just keeps restarting this delay, and only
        //     the level the gesture actually settles on ever reaches the code below.
        //  2. Even the delayed batch could still outlive its own usefulness if the
        //     user moves on before it finishes -- now explicitly disposed in a
        //     `finally` the moment a newer zoom supersedes it, so a superseded
        //     prefetch stops actively competing for bandwidth instead of finishing
        //     as though it still mattered.
        LaunchedEffect(zoom, xTileF, yTileF, wPx, hPx) {
            if (wPx <= 0f || hPx <= 0f) return@LaunchedEffect
            delay(300)
            val loader = context.imageLoader
            val halfTilesX = wPx / tilePx / 2f + 1f
            val halfTilesY = hPx / tilePx / 2f + 1f
            val disposables = mutableListOf<coil.request.Disposable>()
            try {
                for (targetZoom in intArrayOf(zoom - 1, zoom + 1)) {
                    if (targetZoom < CarMapMinZoom || targetZoom > CarMapMaxZoom) continue
                    val cx = MapTiles.tileX(location.longitude, targetZoom)
                    val cy = MapTiles.tileY(location.latitude, targetZoom)
                    val span = MapTiles.span(targetZoom)
                    val firstX = floor(cx - halfTilesX).toInt()
                    val lastX = floor(cx + halfTilesX).toInt()
                    val firstY = floor(cy - halfTilesY).toInt().coerceAtLeast(0)
                    val lastY = floor(cy + halfTilesY).toInt().coerceAtMost(span - 1)
                    for (tx in firstX..lastX) {
                        for (ty in firstY..lastY) {
                            // A plain enqueue() call has no suspension point of its own
                            // for cancellation to interrupt -- without this explicit
                            // check, a whole tight loop of them runs to completion
                            // regardless of a newer zoom superseding this effect the
                            // instant it starts, defeating the debounce above for
                            // anything already past its `delay(300)`.
                            currentCoroutineContext().ensureActive()
                            val wrappedX = MapTiles.wrapX(tx, targetZoom)
                            disposables += loader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(MapTiles.tileUrl(targetZoom, wrappedX, ty))
                                    .setHeader("User-Agent", MapTiles.userAgent("Android"))
                                    .build(),
                            )
                        }
                    }
                }
            } finally {
                if (!currentCoroutineContext().isActive) disposables.forEach { it.dispose() }
            }
        }

        // Everything below (tiles, pin, device dot) sits inside its own scaled layer --
        // NOT the outer Box, which also hosts the expand button (CarMap's own doc) and
        // must stay at 1x regardless of how far a pinch has scaled the map itself.
        // state.scale is the CONTINUOUS part of a pinch (see its own doc): applying it
        // here, every frame of the gesture, is what makes zooming feel fluid instead of
        // jumping between the whole levels a fresh tile fetch actually needs.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = state.scale
                    scaleY = state.scale
                },
        ) {
        for (tx in range.firstX..range.lastX) {
            for (ty in range.firstY..range.lastY) {
                if (ty < 0 || ty >= span) continue
                val wrappedX = MapTiles.wrapX(tx, zoom)
                // key(), not a bare loop body: gives each tile a stable slot
                // keyed by its own tile coordinate, so the remember() just
                // below is safe to use inside a plain for-loop (whose visible
                // tile SET changes as the car/box moves) without its state
                // silently reattaching to the wrong tile between compositions.
                key(wrappedX, ty) {
                    // Remembered, not rebuilt on every recomposition of this
                    // composable (which happens on every `location` update,
                    // i.e. while the car/phone is moving): Coil's ImageRequest
                    // has no equals()/hashCode() override, so a fresh .build()
                    // every time is a reference-distinct object even when the
                    // URL/headers are identical -- AsyncImage keys its load
                    // launch on that identity, so an unremembered request
                    // restarted the whole load pipeline (a blank frame while
                    // it "reloads") for every visible tile on every location
                    // update, even for tiles already sitting in Coil's memory
                    // cache -- visible flicker across the whole map.
                    val request = remember(wrappedX, ty, zoom) {
                        ImageRequest.Builder(context)
                            .data(MapTiles.tileUrl(zoom, wrappedX, ty))
                            // OSM returns a "blocked" placeholder tile to clients whose
                            // User-Agent doesn't identify the app. This one used to read
                            // "Bloo Bluelink companion app" -- no version, no contact URL,
                            // i.e. still shaped like the string that gets blocked, while
                            // the widget and watch had already been fixed.
                            .setHeader("User-Agent", MapTiles.userAgent("Android"))
                            // No crossfade. Coil only skips its own crossfade animation
                            // for an exact MEMORY cache hit -- a DISK cache hit (or a
                            // genuinely fresh fetch) still fades in. That mattered a lot
                            // here specifically: the pebble's compact map and the full-
                            // screen sheet are two SEPARATE CarMap composables (see
                            // ExpandedMapState's own doc), so expanding mounts a second,
                            // brand-new grid of AsyncImage tiles requesting the exact
                            // same URLs the pebble was already showing -- reported
                            // directly as pinch-zoom and the expand/collapse transition
                            // both feeling like the map "has to refresh" rather than
                            // being instant/seamless. A flat, no-fade appearance means a
                            // cache hit (memory OR disk -- the overwhelmingly common case
                            // here) is visually indistinguishable from "was already
                            // there", and a genuine new fetch just pops in the moment
                            // it's ready instead of visibly announcing itself.
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        colorFilter = darkMapFilter,
                        modifier = Modifier
                            .size(tileDp)
                            // Layout-phase placement, not a composition-time Dp
                            // offset(x=,y=): reads the live pan fresh every frame
                            // without ever recomposing this AsyncImage -- see
                            // `range`'s own doc above for why that matters.
                            .offset {
                                val originX = xTileF * tilePx - wPx / 2f - state.panX
                                val originY = yTileF * tilePx - hPx / 2f - state.panY
                                IntOffset(
                                    (tx * tilePx - originX).roundToInt(),
                                    (ty * tilePx - originY).roundToInt(),
                                )
                            },
                    )
                }
            }
        }
        // Screen-space (not real-world) distance between the car pin and the device
        // dot, in tile pixels -- panX/panY cancel out of this difference (both pins
        // ride along with pan identically), so this is purely "how far apart do
        // they actually look right now," which is exactly what should decide
        // whether to merge them: at a zoomed-out view a mile apart can overlap on
        // screen, and at a close zoom a genuinely close pair can still read as
        // two distinct pins. Recomputed from tile coordinates directly rather than
        // real-world lat/lon distance for that reason -- it already accounts for
        // the current zoom level the same way the pins' own on-screen positions do.
        val devTileOffsetPx = deviceLocation?.let { dev ->
            val dx = (MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx
            val dy = (MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx
            dx to dy
        }
        // Under this many screen px apart, the two pins would visually overlap
        // (each pin is ~40dp/16dp wide) -- merge them into one instead of drawing
        // two pins stacked on top of each other.
        val mergeThresholdPx = with(density) { 36.dp.toPx() }
        // Non-null only when the two pins would visually collide. Carrying the
        // offset itself (rather than a boolean plus a second null check on the
        // same value) is what lets the merged branch destructure it directly --
        // and it is what the compiler was already proving: "isMerged" could only
        // be true when the offset existed, so the old `&& devTileOffsetPx != null`
        // was dead weight.
        val mergedOffsetPx = devTileOffsetPx?.takeIf { (dx, dy) ->
            kotlin.math.hypot(dx, dy) < mergeThresholdPx
        }
        if (mergedOffsetPx != null) {
            val (dx, dy) = mergedOffsetPx
            // One pin at the midpoint between the two, tinted with an even mix of
            // the car's own colour and the device's -- "a combined pin that has
            // the mix of the two colors" rather than picking one or stacking both.
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "Car and your location",
                tint = lerp(pinColor, deviceLocationColor, 0.5f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((state.panX + dx / 2f).roundToInt(), (state.panY + dy / 2f).roundToInt()) }
                    .size(40.dp)
                    .offset(y = (-20).dp),
            )
        } else {
            // The pin's screen offset from the box's own centre: zero (i.e. dead-centre) at
            // rest, and it rides along with panX/panY exactly like the tiles do -- so panning
            // away from the car slides the pin off toward wherever the car actually is
            // relative to the new view, instead of it staying glued to the middle of the box.
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "Car location",
                tint = pinColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(state.panX.roundToInt(), state.panY.roundToInt()) }
                    .size(40.dp)
                    .offset(y = (-20).dp),
            )
            // The device's own position, if it fetched one -- offset from the box's centre
            // the same way the tiles/pin are, but derived from ITS OWN tile coordinate
            // rather than riding along with panX/panY: the pin's offset (panX, panY) is a
            // shortcut that only works because the pin IS what the view is centred on.
            // Anything else on the map has to go through the full conversion: its tile
            // position minus the car's, in pixels, plus however far the user has panned.
            devTileOffsetPx?.let { (dx, dy) ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset((state.panX + dx).roundToInt(), (state.panY + dy).roundToInt()) }
                        .size(16.dp)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                        .background(deviceLocationColor, CircleShape),
                )
            }
        }
        // Nearby chargers, each going through the SAME full tile-coordinate
        // conversion the device dot above does (its own tile position minus the
        // car's, in pixels, plus however far the user has panned) -- there can be
        // dozens of these, unlike the one device dot, so key() gives each pin a
        // stable identity across recompositions the way the tile loop above already
        // does for tiles. A fixed, always-visible green -- not a theme role like the
        // car/device pins -- since it needs to read as "a charger" against any
        // palette the car pin/device dot happen to be using today.
        for (charger in chargers) {
            key(charger.id) {
                val dx = (MapTiles.tileX(charger.longitude, zoom) - xTileF) * tilePx
                val dy = (MapTiles.tileY(charger.latitude, zoom) - yTileF) * tilePx
                Icon(
                    Icons.Filled.EvStation,
                    contentDescription = charger.name,
                    tint = ChargeGreen,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset((state.panX + dx).roundToInt(), (state.panY + dy).roundToInt()) }
                        .size(28.dp)
                        .offset(y = (-14).dp)
                        .then(
                            if (onChargerClick != null) {
                                Modifier.noRippleClickable(onClickLabel = charger.name) { onChargerClick(charger) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
        } // close the scaled tiles/pin/dot layer
        // No buttons drawn over the map any more -- reported directly from a
        // screenshot as unwanted ("no buttons floating on the map, they should
        // be underneath it as normal buttons"). Expand/Open-in-Maps now render
        // as a real MapFeatureRow in the caller, right after this composable,
        // the same shape the full-screen map's own bottom toolbar already uses.
    }
}

/**
 * One entry in the full-screen map's bottom toolbar -- an icon, a label and an
 * action, nothing else. This IS the "framework" for future map features (a traffic
 * layer, turn-by-turn directions, nearby search, a saved-places list, sharing a live
 * location...): each new capability is just another [MapFeature] appended to the list
 * [CarMapFullScreenDialog] builds, never a change to the row itself, the button
 * styling, or the layout around it. Two real ones exist today -- recentre and open in
 * the system Maps app -- and every future one is exactly this same shape.
 */
internal data class MapFeature(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The map sheet's bottom toolbar: one [MorphButton] pill per [MapFeature], sharing an
 * [ExpressiveButtonRow] -- the same connected-group framework the lock/horn cluster
 * and every other multi-button row in the app uses, so pressing one pill takes width
 * FROM its neighbour (both keep their own independent pill shape; only the WIDTH
 * trades) instead of each growing independently into free space. `equalWidths = true`
 * keeps both pills the same width regardless, matching the even split this row always
 * had. `wrap = true` (not a scrolling Row): a future feature list long enough to
 * overflow one line wraps to a second instead of needing its own horizontal-scroll
 * affordance.
 *
 * Icon AND label, via the shared [MorphActionButton] -- these two buttons (Expand, Open
 * in Maps) used to be icon-only with no visible label or border, reported directly as
 * wanting names and outlines like the app's other buttons. The outlined-tonal treatment
 * they were given here is now THE standard action-button look, so it lives in Morph.kt
 * rather than being re-typed inline here.
 */
@Composable
private fun MapFeatureRow(
    features: List<MapFeature>,
    modifier: Modifier = Modifier,
    /** False for three-plus features: rather than wrapping a lone last button onto
     *  its own full-width second line (reported directly from a screenshot, once
     *  "Chargers" became a third feature here), the whole row compacts to icon-only
     *  buttons instead -- see [ExpressiveButtonRow]'s own `wrap` doc. Still true (the
     *  default) for the two-feature rows this always fit fine. */
    wrap: Boolean = true,
    /** [Alignment.CenterHorizontally] when the row can compact to icon-only glyphs (three
     *  or more features): once those glyphs are capped at their own small size (see
     *  [ExpressiveButtonGroup]'s equalWidths doc) rather than stretched to fill the row, a
     *  left-packed cluster reads as broken/half-empty -- centering it in the full row width
     *  reads as one intentional, compact strip instead. Two-feature rows never compact, so
     *  their default `Start` never differs from Center in practice; left as `Start` (the
     *  group's own default) rather than changed for every existing caller. */
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    ExpressiveButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        spacing = 10.dp,
        equalWidths = true,
        wrap = wrap,
        horizontalAlignment = horizontalAlignment,
    ) {
        features.forEach { feature ->
            val source = remember { MutableInteractionSource() }
            SafeExpansiveButton(
                interactionSource = source,
                enabled = feature.enabled,
            ) {
                MorphActionButton(
                    label = feature.label,
                    icon = feature.icon,
                    onClick = feature.onClick,
                    interactionSource = source,
                    enabled = feature.enabled,
                )
            }
        }
    }
}

/**
 * The map, expanded into a bottom sheet -- reached from [CarMap]'s own corner button.
 * Its own [CarMapState] ([rememberCarMapState]), independent of whatever the small
 * inline map is panned/zoomed to: expanding is a bigger canvas to look at the SAME
 * car on, not a continuation of one specific gesture.
 *
 * A hand-rolled overlay on a plain [Dialog], NOT [androidx.compose.material3.ModalBottomSheet]
 * -- that was the SECOND attempt here (the first was a hand-rolled [Dialog] driving
 * its own `graphicsLayer` scale/translate from a captured on-screen rect, reported as
 * not actually seamless, not full screen, and covering its own close button).
 * `ModalBottomSheet` fixed all of that, but turned out to have a problem of its own
 * that no configuration can reach: it is *itself* implemented as a `Dialog` under the
 * hood, and Android/Compose Dialogs adapt to large screens by centering themselves
 * and capping their width (`sheetMaxWidth`) -- so on a tablet or unfolded foldable
 * this rendered as a boxed, dialog-shaped card floating in the middle of the screen
 * with the app visible on all four sides, including BELOW its own bottom edge.
 * Reported directly from a screenshot: "why does it float like that? That's wrong."
 * `ModalBottomSheetProperties` exposes no override for that adaptive centering --
 * it's baked into the Dialog underneath, not a configurable behaviour of the sheet.
 *
 * So this goes one level lower: a plain [Dialog] with `usePlatformDefaultWidth =
 * false` gets NONE of that adaptive treatment -- it's just a full-screen surface this
 * draws its own true bottom-anchored, full-width sheet onto, identically regardless
 * of how wide the window is. Everything `ModalBottomSheet` used to give for free now
 * lives here instead: [visible] (an [Animatable] the sheet's whole lifecycle runs on,
 * 0 = slid fully off the bottom edge, 1 = at rest) drives the slide-in/out and the
 * scrim's fade together, a tap on the scrim dismisses, and the drag handle (not the
 * whole sheet -- the map area already owns pan/pinch of its own) supports drag-to-
 * dismiss via [dragPx]. The one thing genuinely lost versus `ModalBottomSheet` is its
 * built-in fling-velocity dismiss; a plain distance threshold stands in for it below.
 *
 * [CarMap] itself still grows in from [originBounds] (see `morph`'s own doc, now
 * folded into [visible]) so opening reads as the map continuing to expand rather than
 * a flat cut.
 *
 * The bottom [MapFeatureRow] is deliberately sparse today (recentre, open in the
 * system Maps app) -- see [MapFeature]'s own doc. This sheet, not a new screen in the
 * app's own navigation, is the FRAMEWORK request this shipped alongside: a
 * self-contained expanded surface future map features can build against (a drawn
 * route, live traffic, nearby search, saved places) without first having to plumb a
 * new destination through the rest of the app.
 */
@Composable
internal fun CarMapSheet(
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    /**
     * The small map's own on-screen rect (absolute screen coordinates -- see the
     * call site's own doc) at the moment it was tapped. The sheet's own map area
     * morphs from this rect to its natural size/position (a `graphicsLayer` scale +
     * translate driven by one shared [Animatable]) instead of just fading/scaling in
     * from its own centre -- reported directly as wanting the card to expand FROM
     * the map pebble, not materialise over the bottom of the screen. Null (measured
     * too late, or the caller has no origin to offer) falls back to the plain
     * scale-from-a-touch-under-full-size CarMapSheet always had.
     */
    originBounds: Rect?,
    /** Null (the default) omits the refresh icon entirely -- see [MapTopBar]'s
     *  own doc. */
    onRefreshLocation: (() -> Unit)? = null,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    onDismiss: () -> Unit,
) {
    // The Dialog-based fallback for hosts that don't provide a LocalExpandedMap
    // (the flip-cover screen) -- see ExpandedMapState's own doc. A genuinely
    // separate CarMap/CarMapState of its own, not the compact map's, since there's
    // no shared state to reach here.
    Dialog(
        // A fallback only -- CarMapSheetBody's own BackHandler intercepts system
        // back first and runs its animated close() before this ever fires. Direct,
        // with no animation, since close() itself lives inside CarMapSheetBody and
        // isn't reachable from here.
        onDismissRequest = onDismiss,
        // false: a full-screen canvas, not a Dialog sized/positioned by the
        // platform's own adaptive rules -- see CarMapSheetBody's own doc for why
        // ModalBottomSheet (itself a Dialog) couldn't avoid that. decorFitsSystemWindows
        // = false so this draws genuinely edge-to-edge and positions its own content
        // (the map area's own insets/padding already handle the status/nav bars)
        // rather than having the window itself carve out a smaller content area.
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // As in GlassAlertDialog: this Dialog owns its own platform Window, entirely
        // separate from the main Activity window, so the platform's own default
        // dim/background behind it has to be turned off explicitly -- CarMapSheetBody
        // draws its own scrim instead. Also why hazeState is null here: Haze can only
        // blur content that's actually in the SAME window/composition as its source,
        // and this Dialog's window is not that -- see ExpandedMapState's own doc.
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0f)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        CarMapSheetBody(
            location, vehicleName, deviceLocation,
            mapState = rememberCarMapState(),
            originBounds = originBounds,
            hazeState = null,
            onRefreshLocation = onRefreshLocation,
            refreshing = refreshing,
            onDismiss = onDismiss,
        )
    }
}

/**
 * THE single CarMap instance, repositionable between pebble and full-screen.
 * Literally one Box growing from the pebble location to a SHEET -- the bottom
 * 85% of the screen, not the whole thing -- with the top 15% left showing the
 * blurred/dimmed app behind it, same shape [CarMapSheetBody] always used.
 * Reported directly: the sheet was covering the entire screen edge to edge,
 * and its drag handle sat glued to the literal top of the screen instead of
 * near the top of the sheet itself.
 *
 * Getting the "grow from the pebble, no distortion" morph right against a
 * TARGET smaller than the full screen means the map's own Box must already,
 * for real (not via a graphicsLayer trick), be laid out at that final 85%-
 * height/bottom-anchored size -- exactly [CarMapSheetBody]'s own technique.
 * A graphicsLayer scale toward a box whose OWN natural size is the full
 * screen (this composable's very first version) can only ever reach 1.0,
 * i.e. the full screen, at rest -- there is no way to "scale down" to a
 * smaller resting size without visibly squashing the map along the way.
 */
/**
 * Everything that used to float over the top of an expanded map sheet as three
 * separate glass pills -- the vehicle-name pill (top-left), the drag handle
 * (top-center), and the refresh chip (top-right) -- consolidated into ONE bar
 * with one shared floating pill background. They used to each carry their own
 * hand-rolled (or near-identical) [GlassSurface], each with its own blur, its
 * own shadow, its own rim -- three separate pieces of glass chrome doing what
 * reads, and should always have read, as a single control strip. Giving up on
 * making that strip feel "uniform" with every other floating surface in the
 * app in some deeper sense and just building it as one plain bar was the
 * actual ask: a name on the left, a drag handle centered above a divider, and
 * a refresh action on the right, all inside one rounded rect.
 *
 * [onRefreshLocation] null (the [CarMapSheet]/[CarMapSheetBody] default) omits
 * the refresh side entirely rather than showing a dead button.
 *
 * [dragModifier] carries the vertical-drag-to-dismiss gesture -- built by the
 * caller, since the two call sites each close over their own [dragPx]/`scope`/
 * `close()`, and passing the finished modifier in is simpler than exporting a
 * matching set of callback params for the same thing.
 *
 * ONE line, not the old two-row layout (a drag-handle row stacked above a
 * name/refresh row) -- name at [Alignment.CenterStart], the drag handle nub
 * genuinely centred on the bar regardless of the name's length or whether a
 * refresh icon is showing, and the refresh icon at [Alignment.CenterEnd], all
 * three positioned independently in one [Box] rather than flowing through a
 * [Row]. Reported directly as wanting one line with bigger text and the
 * handle in the middle.
 */
@Composable
private fun MapTopBar(
    vehicleName: String,
    mapHazeState: HazeState,
    dragModifier: Modifier,
    modifier: Modifier = Modifier,
    onRefreshLocation: (() -> Unit)? = null,
    /** True while a refresh this bar's own [onRefreshLocation] kicked off is
     *  still in flight -- the real command-pending flag from the caller
     *  (state.isPending(vin, "locate")), not a locally-faked timer, so the
     *  icon's spin genuinely tracks "still fetching" rather than a guessed
     *  duration. */
    refreshing: Boolean = false,
) {
    // Press-and-hold "pop" on the WHOLE pill -- not just the drag handle nub --
    // the instant any part of it is touched, before any actual drag motion,
    // reading as "I feel you, go ahead" rather than staying inert until it
    // moves. Reported directly as wanting the entire bar (name, handle AND
    // refresh indicator together) to pop, not just the nub in isolation.
    // requireUnconsumed = false: this OBSERVES the down/up sequence without
    // consuming it, so the same touch still reaches dragModifier's own gesture
    // detector -- popping must never steal the drag it's advertising, and the
    // refresh button's own click still fires normally (a click is a down+up
    // with no net movement, exactly what this passes through unconsumed).
    var pillPressed by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (pillPressed) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "mapBarPop",
    )
    GlassSurface(
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pillPressed = true
                    waitForUpOrCancellation()
                    pillPressed = false
                }
            }
            .graphicsLayer { scaleX = pillScale; scaleY = pillScale },
        hazeState = mapHazeState,
        // No contentColor override: [GlassSurface]'s own default (onSurface) is the
        // right answer here, and the `Color.White` this used to force was wrong in
        // light mode. This bar is glass over the MAP, and the map follows the app's
        // theme -- CarMap dark-filters these same OSM tiles only when
        // appIsDarkTheme() (see darkMapFilter's own doc), so in light mode the
        // backdrop behind this pill is a BRIGHT raster map. Unlike the hero's photo,
        // there is no dark scrim under it to make white legible: the only fill is
        // glassTint, which in light mode is surfaceContainer at 0.08-0.12 alpha, so
        // the car's name, the drag nub and the refresh glyph were near-white on
        // near-white -- the bar read as empty. onSurface tracks the same theme the
        // map filter does, so it is dark-on-light map and light-on-dark map by
        // construction, and it honours an active custom palette the way
        // the pin beside it already does. It is also what the map's own
        // MapFeatureRow buttons below already use.
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(dragModifier)
                // 48dp: tall enough for the bigger titleLarge name and a comfortable
                // touch target for both the drag handle and the refresh icon, all on
                // one line -- the old two-row layout (a 14dp handle strip stacked
                // above a name/refresh row) took noticeably more vertical space for
                // the same content.
                .height(48.dp)
                .padding(horizontal = 16.dp),
        ) {
            // Name and bigger, titleLarge (was titleMedium) -- reported directly as
            // wanting bigger text. Reserves room on the end for the refresh icon
            // (never under it) regardless of alignment, since both float independently
            // in this Box rather than sharing a Row's own space-distribution. Reserves
            // the SAME 40dp the refresh circle itself occupies (see below) -- previously
            // this reserved 44dp against a 36dp circle, a mismatched pair that was the
            // "refresh button is in the incorrect place, not equal" report: the circle
            // sat 8dp closer to the edge than the space carved out for it implied, so it
            // read as off-centre against its own reserved slot.
            Text(
                vehicleName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = if (onRefreshLocation != null) 40.dp else 0.dp),
            )
            // The drag handle nub -- purely visual now, no pointerInput of its own:
            // the pop above already tracks press for the whole pill, so a second,
            // separate press-tracker here would just be redundant (and, worse, could
            // read a DIFFERENT pressed state than the pill around it if the two ever
            // drifted, which is exactly the "parts of the pill disagree" look this is
            // meant to avoid).
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 32.dp, height = 4.dp)
                    // The bar's own inherited content tone, halved -- not a fixed
                    // white. It sits inside the GlassSurface above, whose
                    // CompositionLocalProvider already resolved the one colour that
                    // reads against this backdrop in both themes, so the nub cannot
                    // disagree with the name and the refresh glyph either side of it
                    // (a fixed white nub survived the contentColor fix above as a
                    // pale smudge on a light map). Same idiom as ChargeSegmentBar's
                    // own track tints (HeroReadout.kt): inherit the reader's colour
                    // and mute it, rather than guessing a colour here.
                    .background(LocalContentColor.current.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
            )
            if (onRefreshLocation != null) {
                // Icon-only (was a text chip: "Updated Xm ago" / "Refresh" beside a
                // static icon) -- reported directly as wanting a refresh INDICATOR,
                // not a label, that animates while it's actually working. Same
                // ramp-up/steady-spin language MorphButtonGlyph (Morph.kt) already
                // uses for every other in-progress icon in the app, driven by the
                // real pending flag rather than a guessed duration -- see
                // [refreshing]'s own doc.
                val angle = remember { Animatable(0f) }
                LaunchedEffect(refreshing) {
                    if (refreshing) {
                        angle.animateTo(angle.value + 360f, tween(850, easing = FastOutLinearInEasing))
                        while (true) {
                            angle.animateTo(angle.value + 360f, tween(600, easing = LinearEasing))
                        }
                    } else if (angle.value != 0f) {
                        val target = kotlin.math.ceil(angle.value / 360f) * 360f
                        angle.animateTo(target, tween(700, easing = LinearOutSlowInEasing))
                        angle.snapTo(0f)
                    }
                }
                // 40dp, matching the Text's own reserved end space above exactly --
                // was 36dp against a 44dp reservation, a mismatched pair (see the
                // Text's own comment). Centred in the 48dp-tall bar the same way the
                // drag handle and the name both are, so all three read as one row.
                // Extra right padding pushes the button further right (away from the edges).
                GlassSurface(
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.CenterEnd).size(40.dp).padding(end = 12.dp),
                    hazeState = mapHazeState,
                    onClick = onRefreshLocation,
                    contentDescription = "Refresh location",
                    // Same reason the bar around it dropped its own white override
                    // (see the outer GlassSurface): this nested circle is over the
                    // same theme-following map, and a forced white glyph vanished on
                    // a light one. Inherits GlassSurface's onSurface default.
                    // Nested inside the bar's own already-elevated GlassSurface --
                    // see glassEdge's own doc for why a nested panel skips the second
                    // shadow (GlassChrome.kt).
                    shadow = false,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = angle.value },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpandableMapLayer(
    isExpanded: Boolean,
    originBounds: Rect,
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    hazeState: HazeState?,
    onRefreshLocation: () -> Unit,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    onDismiss: () -> Unit,
    /** The "Chargers" [MapFeature] -- see [ChargerFinder]'s own doc for the whole
     *  group. All default to "off"/"nothing loaded" so every OTHER existing caller's
     *  behaviour is unchanged; GarageScreen's own call site is the only one that
     *  wires these to real state today. */
    chargersVisible: Boolean = false,
    chargersLoading: Boolean = false,
    /** Set only on a genuine fetch failure (network/auth/parse) -- see
     *  [com.bloo.bluelink.data.ChargerApi.nearby]'s own doc for why that's kept
     *  distinct from [chargers] simply being empty. */
    chargersError: String? = null,
    chargers: List<ChargerStation> = emptyList(),
    chargerFilters: ChargerFilters = ChargerFilters(),
    onToggleChargersVisible: () -> Unit = {},
    onRetryChargers: () -> Unit = {},
    onSetChargerMinKw: (Int) -> Unit = {},
    onToggleChargerNetwork: (String) -> Unit = {},
    onSetChargerApiKey: (String?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapHazeState = remember { HazeState() }

    // Animate from pebble size to full screen. Always starts at 0f: GarageScreen
    // only ever composes this with isExpanded=true (it stops rendering the whole
    // composable on close, rather than passing false) -- so `isExpanded` never
    // actually changes across this composable's lifetime, and initializing the
    // Animatable from it (`Animatable(if (isExpanded) 1f else 0f)`, the previous
    // version here) meant it started AT 1f on every mount, skipping the pop-out-
    // of-the-pebble open animation entirely. LaunchedEffect(Unit), not
    // keyed on isExpanded, for the same reason: that key never changes, so this
    // still runs exactly once per mount -- i.e. once per expand -- which is
    // exactly the intent.
    val expandFraction = remember { Animatable(0f) }
    // Read here, at composable scope, not inside the LaunchedEffect below --
    // lowPowerAwareSpring is itself @Composable, so it can't be called from a
    // suspend lambda. NoBouncy (critically damped, no overshoot) -- reported
    // directly as wanting the sheet to pull up "like a normal card... nice and
    // smooth", not with the springy pop this used to have.
    val openSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(Unit) {
        expandFraction.animateTo(1f, animationSpec = openSpring)
    }

    // How far the drag handle has actually been pulled down, in px -- the SAME
    // real drag-to-dismiss CarMapSheetBody's own handle uses (its own `dragPx`;
    // see that doc). The handle's own pointerInput below is the only thing that
    // feeds this: the map area already owns pan/pinch of its own, and a whole-
    // sheet drag-to-dismiss would fight it on every downward pan. Reported
    // directly as wanting the handle to be "genuinely a pull-down" -- the
    // previous version here ignored the drag delta entirely and closed on ANY
    // touch-up on the handle, including a tiny accidental tap.
    val dragPx = remember { Animatable(0f) }
    // Read here, at composable scope, not inline inside the scope.launch{} blocks below --
    // lowPowerAwareSpring is itself @Composable (it reads battery-saver state), so it can't
    // be called from inside a suspend lambda. Captured once and referenced from both.
    val dragSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium)
    // Same reason as dragSpring above -- read here, not inside the scope.launch{} in close().
    val closeSpring = lowPowerAwareSpring<Float>(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
    var closing by remember { mutableStateOf(false) }
    // Which charger pin (if any) the user just tapped -- see the info card below.
    // Cleared whenever the layer itself is hidden so reopening it never shows a
    // stale popup for a pin that isn't even drawn any more.
    var selectedCharger by remember { mutableStateOf<ChargerStation?>(null) }
    LaunchedEffect(chargersVisible) { if (!chargersVisible) selectedCharger = null }
    // Also cleared the moment the selected pin itself stops matching the active
    // filters (or drops out of a fresh fetch entirely) -- its pin is no longer
    // drawn on the map at that point (see the `chargers.filter { it.matches(...) }`
    // passed to CarMap below), so leaving the info card up would keep describing a
    // charger the user can no longer even see.
    LaunchedEffect(chargerFilters, chargers) {
        selectedCharger?.let { sel -> if (sel !in chargers || !sel.matches(chargerFilters)) selectedCharger = null }
    }
    // Memoized, not recomputed inline at the CarMap call site below: this composable
    // also recomposes on drag/pan/expand-animation state that has nothing to do with
    // chargers, chargersVisible or chargerFilters, and re-filtering the whole list on
    // every one of those frames would be pure waste -- the same reasoning ChargerFilterBar
    // (below) already applies to its own equivalent filter/count.
    val visibleChargers = remember(chargersVisible, chargers, chargerFilters) {
        if (chargersVisible) chargers.filter { it.matches(chargerFilters) } else emptyList()
    }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Both play at once so a mid-drag dismiss doesn't visibly snap dragPx
            // back to 0 before the sheet itself starts shrinking away.
            val a = scope.launch { expandFraction.animateTo(0f, animationSpec = closeSpring) }
            val b = scope.launch { dragPx.animateTo(0f, animationSpec = closeSpring) }
            a.join(); b.join()
            onDismiss()
        }
    }

    // Captured at the SHEET's own real layout -- 85% height, bottom-anchored,
    // full width -- not the whole screen. This is the "full" target the morph
    // below scales the pebble up to.
    var sheetBounds by remember { mutableStateOf<Rect?>(null) }

    // Back handler for closing
    BackHandler(enabled = isExpanded) { close() }

    Box(Modifier.fillMaxSize()) {
        // Scrim background (dims/blurs content behind) -- covers the WHOLE
        // screen, not just the sheet: this is what shows through the top 15%
        // strip the sheet itself doesn't reach.
        if (isExpanded) {
            ScrimBlur(hazeState = hazeState, progress = { expandFraction.value })
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }

        // The sheet: bottom-anchored, full-width, 85% of the screen's height --
        // its OWN real layout size (not a graphicsLayer trick), so the morph
        // below scales toward a target that's actually this size instead of
        // the whole screen. Everything belonging to "the sheet" (map, name
        // pill, buttons, drag handle) lives inside it now, so their alignments
        // anchor to the SHEET's own edges, not the screen's.
        //
        // Two graphicsLayer transforms, on two nested boxes, not one -- reported
        // directly as the open animation being "janky... hits a hard limit on
        // the outside of how big it can go". That was this OUTER box and the
        // pebble-to-full scale morph both being driven by the same clamped `t`
        // on a single layer: a bouncy spring overshoots past its target and
        // then dips back below it before settling (that dip is the actual
        // bounce), but clamping scale to [0, 1] hides the overshoot half of
        // that motion entirely while still fully showing the undershoot half --
        // so it read as "grows to full size, hits a wall, visibly shrinks back
        // a little, grows again to settle". Splitting it the way
        // CarMapSheetBody's own (working) sheet always has fixes this: THIS
        // outer box carries the sheet's own entrance -- an UNCLAMPED slide up
        // from fully below-screen, free to genuinely overshoot past rest and
        // settle back, which is what actually sells "pop" -- with no
        // `clipToBounds()`, so the whole sheet (its rounded-corner shape
        // included) moves as one rigid unit with nothing to hit a wall
        // against. The pebble-to-full SCALE morph moves to a separate INNER
        // box below, still clamped to [0, 1] (scale over 100% would still
        // need somewhere to go), but now decoupled from this entrance --
        // the two motions layer together instead of fighting over one value.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .graphicsLayer {
                    translationY = (1f - expandFraction.value) * size.height + dragPx.value
                }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            // The map itself: fills the whole sheet, clamped pebble-to-full morph,
            // clipped to its own bounds so the GROWING-from-pebble content never
            // spills past the sheet's edges on the way up -- independent of the
            // outer sheet's own entrance bounce above.
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { sheetBounds = Rect(it.positionOnScreen(), it.size.toSize()) }
                    .clipToBounds()
                    .graphicsLayer {
                        val full = sheetBounds
                        val t = expandFraction.value.coerceIn(0f, 1f)
                        if (full != null && full.width > 0f && full.height > 0f) {
                            val originScaleX = originBounds.width / full.width
                            val originScaleY = originBounds.height / full.height
                            scaleX = originScaleX + (1f - originScaleX) * t
                            scaleY = originScaleY + (1f - originScaleY) * t
                            translationX = (originBounds.center.x - full.center.x) * (1f - t)
                            translationY = (originBounds.center.y - full.center.y) * (1f - t)
                        } else {
                            scaleX = 0.92f + 0.08f * t
                            scaleY = 0.92f + 0.08f * t
                        }
                    },
            ) {
                CarMap(
                    location,
                    Modifier.fillMaxSize().hazeSource(mapHazeState),
                    state = mapState,
                    deviceLocation = deviceLocation,
                    // Filtered here, not passed raw: CarMap draws exactly what's handed to
                    // it and has no notion of ChargerFilters of its own -- keeping that
                    // narrowing at this call site is what lets every OTHER caller (the
                    // compact pebble map, the cover screen) stay on CarMap's plain
                    // empty-list default with nothing to opt out of.
                    chargers = visibleChargers,
                    onChargerClick = { selectedCharger = it },
                )
            }

            // One consolidated top bar -- name, drag handle, and refresh, all inside
            // one shared floating pill background instead of three separate glass
            // pieces (see MapTopBar's own doc). Positioned close to the sheet's own
            // top edge (16dp) rather than the old name pill's 40dp -- the drag
            // handle's nub now lives INSIDE this same bar instead of needing its
            // own 40dp-tall floating strip above it.
            if (isExpanded && expandFraction.value > 0.1f) {
                MapTopBar(
                    vehicleName = vehicleName,
                    mapHazeState = mapHazeState,
                    onRefreshLocation = onRefreshLocation,
                    refreshing = refreshing,
                    dragModifier = Modifier.pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                scope.launch { dragPx.snapTo((dragPx.value + amount).coerceAtLeast(0f)) }
                            },
                            onDragEnd = {
                                // Distance, not velocity -- a fixed 96dp pull is a
                                // close enough stand-in for "the user clearly meant
                                // to close this". Same threshold CarMapSheetBody's
                                // own handle uses.
                                val thresholdPx = with(density) { 96.dp.toPx() }
                                if (dragPx.value > thresholdPx) {
                                    close()
                                } else {
                                    scope.launch {
                                        dragPx.animateTo(0f, dragSpring)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragPx.animateTo(0f, dragSpring) }
                            },
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                )
            }

            // Bottom buttons (appear when expanded), plus -- above them, in the same
            // bottom-anchored column -- the charger info popup and/or filter bar,
            // whichever are relevant right now. Stacking them in one Column rather
            // than each with its own hand-placed padding is what lets the filter
            // bar's own height (it wraps, so it's taller with a network row than
            // without) push the buttons down naturally instead of the two
            // overlapping whenever the bar grows.
            if (isExpanded && expandFraction.value > 0.1f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        // This whole app runs edge-to-edge (MainActivity's enableEdgeToEdge()),
                        // which turns off the manifest's own adjustResize for every surface --
                        // each one has to lift itself above the keyboard explicitly now. This
                        // column is the one that actually needs it: the charger API key field
                        // inside ChargerFilterBar sits right where the keyboard covers it,
                        // reported directly from a screenshot.
                        .imePadding()
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                ) {
                    // lastSelectedCharger, not selectedCharger directly, inside the PopVisible
                    // content below: PopVisible's exit animation still has to render SOMETHING
                    // while it fades/shrinks out, and selectedCharger itself goes null the
                    // instant it's dismissed -- rendering that null directly would blank the
                    // card the moment the exit starts instead of letting it visibly fade away.
                    var lastSelectedCharger by remember { mutableStateOf<ChargerStation?>(null) }
                    LaunchedEffect(selectedCharger) {
                        selectedCharger?.let { lastSelectedCharger = it }
                    }
                    PopVisible(
                        visible = selectedCharger != null,
                        sizeAnimated = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        lastSelectedCharger?.let { charger ->
                            ChargerInfoCard(
                                charger = charger,
                                mapHazeState = mapHazeState,
                                onDismiss = { selectedCharger = null },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    PopVisible(
                        visible = chargersVisible,
                        sizeAnimated = true,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        ChargerFilterBar(
                            chargers = chargers,
                            filters = chargerFilters,
                            loading = chargersLoading,
                            error = chargersError,
                            mapHazeState = mapHazeState,
                            onSetMinKw = onSetChargerMinKw,
                            onToggleNetwork = onToggleChargerNetwork,
                            onRetry = onRetryChargers,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    MapFeatureRow(
                        features = listOf(
                            MapFeature(Icons.Filled.MyLocation, "Recentre") { mapState.recenter() },
                            MapFeature(Icons.Filled.EvStation, "Chargers") { onToggleChargersVisible() },
                            MapFeature(Icons.Filled.Map, "Open in Maps") {
                                openInExternalMaps(context, location, vehicleName)
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        wrap = false,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }
    }
}

/**
 * The same map, expanded to fill most of the screen, WITHOUT a Dialog -- reached
 * from a Location pebble's own compact map when its host provides an
 * [ExpandedMapState] (currently only [GarageScreen]). Rendered as a plain overlay
 * in that host's own composition (a sibling Box drawn last, so it's on top of
 * everything else) instead of a system Dialog: being in the SAME window as the
 * compact map is what lets it share that map's own [CarMapState] ([mapState] here
 * is [ExpandedMapState.mapStateFor], not a fresh one) instead of starting over at
 * a blank pan/zoom and re-fetching tiles Coil already has cached from the compact
 * view -- reported directly as wanting "literally that same component" to expand,
 * not a copy fading in. It also means [hazeState] can give the visible strip of
 * app above the sheet a REAL blur of the actual content behind it (the same
 * technique [StatusBarScrim] already uses) -- something a separate Dialog window
 * has no access to at all, so [CarMapSheet]'s own Dialog fallback above still just
 * dims that strip instead.
 */
@Composable
internal fun CarMapExpandedOverlay(
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    originBounds: Rect?,
    hazeState: HazeState?,
    onDismiss: () -> Unit,
) {
    CarMapSheetBody(
        location = location,
        vehicleName = vehicleName,
        deviceLocation = deviceLocation,
        mapState = mapState,
        originBounds = originBounds,
        hazeState = hazeState,
        onDismiss = onDismiss,
    )
}

/**
 * The map, expanded into a bottom sheet -- reached from [CarMap]'s own corner button,
 * either via [CarMapSheet] (a Dialog, for hosts with no [ExpandedMapState]) or
 * [CarMapExpandedOverlay] (an in-tree overlay, for [GarageScreen]). Shared body for
 * both: everything about the sheet itself -- its slide-in/out, scrim, drag-to-
 * dismiss, chrome -- is identical either way; only how it's HOSTED (a separate
 * Dialog window vs. a plain overlay in the same composition) differs between the
 * two callers, and that's entirely their own concern, not this one's.
 *
 * A hand-rolled overlay, NOT [androidx.compose.material3.ModalBottomSheet] -- that
 * was the second attempt here (the first was a hand-rolled [Dialog] driving its own
 * `graphicsLayer` scale/translate from a captured on-screen rect, reported as not
 * actually seamless, not full screen, and covering its own close button).
 * `ModalBottomSheet` fixed all of that, but turned out to have a problem of its own
 * that no configuration can reach: it is *itself* implemented as a `Dialog` under the
 * hood, and Android/Compose Dialogs adapt to large screens by centering themselves
 * and capping their width (`sheetMaxWidth`) -- so on a tablet or unfolded foldable
 * this rendered as a boxed, dialog-shaped card floating in the middle of the screen
 * with the app visible on all four sides, including BELOW its own bottom edge.
 * Reported directly from a screenshot: "why does it float like that? That's wrong."
 * `ModalBottomSheetProperties` exposes no override for that adaptive centering --
 * it's baked into the Dialog underneath, not a configurable behaviour of the sheet.
 *
 * [visible] (an [Animatable] the sheet's whole lifecycle runs on, 0 = slid fully off
 * the bottom edge, 1 = at rest) drives the slide-in/out and the scrim's fade
 * together, a tap on the scrim dismisses, and the drag handle (not the whole sheet
 * -- the map area already owns pan/pinch of its own) supports drag-to-dismiss via
 * [dragPx]. [CarMap] itself still grows in from [originBounds] so opening reads as
 * the map continuing to expand rather than a flat cut.
 *
 * The bottom [MapFeatureRow] is deliberately sparse today (recentre, open in the
 * system Maps app) -- see [MapFeature]'s own doc. This sheet, not a new screen in the
 * app's own navigation, is the FRAMEWORK request this shipped alongside: a
 * self-contained expanded surface future map features can build against (a drawn
 * route, live traffic, nearby search, saved places) without first having to plumb a
 * new destination through the rest of the app.
 */
@Composable
private fun CarMapSheetBody(
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    originBounds: Rect?,
    /** Non-null (only from [CarMapExpandedOverlay]) blurs the visible strip of app
     *  above the sheet for real -- see this function's own doc. Null (the Dialog
     *  path) falls back to a plain darkened scrim, since Haze cannot reach across
     *  windows. */
    hazeState: HazeState?,
    /** Null (the default) omits the refresh icon entirely -- see [MapTopBar]'s
     *  own doc. */
    onRefreshLocation: (() -> Unit)? = null,
    /** See [MapTopBar]'s own doc -- the real command-pending flag, not a guess. */
    refreshing: Boolean = false,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Its own HazeState, independent of [hazeState] (which sources the SCREEN
    // behind this sheet, for the scrim above) -- this one sources the map tiles
    // THEMSELVES, drawn inside this sheet, so the drag handle's own frosted chip
    // (below) can blur what's actually behind it regardless of whether this sheet
    // is hosted in a Dialog (where [hazeState] is always null) or GarageScreen's
    // own overlay.
    val mapHazeState = remember { HazeState() }

    // This map area's own full-size on-screen rect, captured once laid out --
    // constant for the life of this sheet (only the graphicsLayer transform below
    // moves, never the actual layout).
    var fullBounds by remember { mutableStateOf<Rect?>(null) }
    // 0 = fully hidden (slid off the bottom of the screen, or -- while there's an
    // origin to grow from -- sitting exactly over originBounds, what the small map
    // looked like the instant this opened), 1 = at rest / grown to this map area's
    // own natural size and position. One value drives BOTH the sheet's own
    // slide-in/out and the map's grow-from-the-pebble morph, so the two read as one
    // continuous motion instead of two separately-timed animations.
    val visible = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    // How far the drag handle has been pulled down from rest, in px -- 0 normally,
    // positive while a drag-to-dismiss gesture is in progress. Only the handle
    // itself feeds this (see its own pointerInput far below), never the sheet at
    // large: the map area already owns pan/pinch gestures of its own, and a
    // whole-sheet drag-to-dismiss would fight it for every downward pan.
    val dragPx = remember { Animatable(0f) }
    // Read here, at composable scope, not inline inside the scope.launch{} blocks below --
    // lowPowerAwareSpring is itself @Composable (it reads battery-saver state), so it can't
    // be called from inside a suspend lambda. Captured once and referenced from both.
    val dragSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium)
    // Same reason as dragSpring above -- read here, not inside the scope.launch{} in close().
    val closeSpring = lowPowerAwareSpring<Float>(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)
    // Same reason again -- read here, not inside the LaunchedEffect further down.
    // NoBouncy (critically damped, no overshoot) -- reported directly as wanting
    // the sheet to pull up "like a normal card... nice and smooth", not with the
    // springy pop this used to have.
    val openSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Both play at once so a mid-drag dismiss doesn't visibly snap dragPx
            // back to 0 before the slide-out starts. Use a spring for close to match
            // the bouncy open, but with much more damping so it settles quickly.
            val a = scope.launch { visible.animateTo(0f, closeSpring) }
            val b = scope.launch { dragPx.animateTo(0f, closeSpring) }
            a.join(); b.join()
            onDismiss()
        }
    }
    // System back plays the same animated close as the scrim tap/drag handle,
    // rather than [CarMapSheet]'s Dialog wrapper tearing this down immediately
    // with no animation at all via its own onDismissRequest (which still calls
    // onDismiss directly, as a fallback -- see its own doc).
    BackHandler(enabled = true) { close() }

    LaunchedEffect(originBounds) {
        if (originBounds != null) snapshotFlow { fullBounds }.filterNotNull().first()
        // Bouncier than the rest of the app's own SoftDamping default, specifically
        // for opening: the origin->full scale/position interpolation below (and the
        // sheet's own slide-in translation) is driven directly off this value with
        // no clamp, so letting the spring genuinely overshoot past 1 here is what
        // makes the map actually POP out of the pebble -- growing slightly past its
        // final size/position and settling back -- rather than smoothly easing into
        // place. Reported directly as wanting it to "pop out of the little map".
        visible.animateTo(1f, openSpring)
    }
    Box(Modifier.fillMaxSize()) {
        // The scrim -- dims (and, with a real hazeState, blurs) the app behind the
        // sheet, and a tap on it dismisses.
        Box(Modifier.fillMaxSize()) {
            // ScrimBlur (GlassChrome.kt): the exact same dim+blur scrim
            // ExpandableMapLayer's own full-screen overlay uses -- this sheet used to
            // carry a byte-for-byte identical copy of that Box chain, right down to
            // reading `visible`/`expandFraction` inside a draw-phase lambda instead
            // of at composition time (see ScrimBlur's own doc for why that distinction
            // is load-bearing here, not stylistic).
            ScrimBlur(hazeState = hazeState, progress = { visible.value })
            Box(Modifier.fillMaxSize().noRippleClickable { close() })
        }
        // The sheet itself: bottom-anchored and full-width on EVERY screen size --
        // see this function's own doc for why. `translationY` (not a plain offset)
        // so the same graphicsLayer that slides it in from fully off-screen at
        // [visible] == 0 also carries the live drag-to-dismiss gesture (`dragPx`)
        // without the two ever fighting over which owns the sheet's position.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 85% of the SCREEN (not counting the status bar). Sizing the SHEET
                // itself here, not the content inside it: this Box IS the sheet.
                .fillMaxHeight(0.85f)
                .graphicsLayer { translationY = (1f - visible.value) * size.height + dragPx.value }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                // Deliberately no background/Surface fill here -- painting a solid
                // tonal rectangle behind the sheet's ENTIRE bounds regardless of
                // content is what made a boxed dialog card read as a floating card
                // rather than a sheet. The map below fills this box edge to edge on
                // its own; nothing else here needs a background to sit on.
                .noRippleClickable { /* swallow taps so they don't fall through to the scrim behind */ },
        ) {
            // The map fills the WHOLE sheet, edge to edge -- no boxed-in margin --
            // with the header and toolbar floating semi-transparently ON TOP of it
            // instead of splitting the sheet into three stacked, non-overlapping
            // bands, the same "content flows behind floating elements" relationship
            // the rest of the app already gives its own scrolling content.
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { fullBounds = Rect(it.positionOnScreen(), it.size.toSize()) }
                    // Clips the growing content to this box's own bounds -- belt and
                    // braces against the translation below ever drawing outside its
                    // slot (an earlier attempt's reported "covers the close button"
                    // came from a similar transform on an UNCLIPPED Column, which
                    // Compose happily draws past its own measured bounds).
                    .clipToBounds()
                    .graphicsLayer {
                        val origin = originBounds
                        val full = fullBounds
                        if (origin != null && full != null && full.width > 0f && full.height > 0f) {
                            // Clamp t to [0, 1] so overshoot from the spring damping doesn't
                            // cause the scale to exceed 1.0 or the translation to reverse.
                            // This keeps the morphing feeling smooth and natural even as the
                            // spring overshoots on open.
                            val t = visible.value.coerceIn(0f, 1f)
                            val originScaleX = origin.width / full.width
                            val originScaleY = origin.height / full.height
                            scaleX = originScaleX + (1f - originScaleX) * t
                            scaleY = originScaleY + (1f - originScaleY) * t
                            translationX = (origin.center.x - full.center.x) * (1f - t)
                            translationY = (origin.center.y - full.center.y) * (1f - t)
                        } else {
                            // No origin to grow from (a caller that passed null) --
                            // the old fallback: a plain scale-in from a touch under
                            // full size rather than nothing at all.
                            val t = visible.value.coerceIn(0f, 1f)
                            scaleX = 0.92f + 0.08f * t
                            scaleY = 0.92f + 0.08f * t
                        }
                    },
            ) {
                CarMap(
                    location,
                    // hazeSource, not just fillMaxSize: marks the map's own tiles as
                    // blurrable content for the drag handle's own frosted chip below
                    // (mapHazeState) -- a SEPARATE HazeState from the sheet's own
                    // scrim blur ([hazeState] param), which sources the screen behind
                    // this sheet, not the map drawn on top of it. Blurring the handle
                    // against the map itself needs its own source regardless of
                    // whether this sheet is a Dialog or an in-tree overlay, so this
                    // works either way even when [hazeState] itself is null.
                    Modifier.fillMaxSize().hazeSource(mapHazeState),
                    state = mapState,
                    deviceLocation = deviceLocation,
                )
            }
            // One consolidated top bar -- name + drag handle, sharing one floating
            // pill background instead of two separate pieces of glass (see
            // MapTopBar's own doc). Refresh is whatever this sheet's own caller
            // passed in (null omits it entirely, same as before -- CarMapExpandedOverlay's
            // dead-code call site never wires one up). Also the ONLY thing on this sheet
            // a user can pull down to dismiss (see [dragPx]'s own doc up top) -- the
            // whole bar is now the drag target, not just a slim strip above it.
            MapTopBar(
                vehicleName = vehicleName,
                mapHazeState = mapHazeState,
                onRefreshLocation = onRefreshLocation,
                refreshing = refreshing,
                dragModifier = Modifier.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            scope.launch { dragPx.snapTo((dragPx.value + amount).coerceAtLeast(0f)) }
                        },
                        onDragEnd = {
                            // Distance, not velocity -- a fixed 96dp pull is a
                            // close enough stand-in for "the user clearly meant
                            // to close this".
                            val thresholdPx = with(density) { 96.dp.toPx() }
                            if (dragPx.value > thresholdPx) {
                                close()
                            } else {
                                scope.launch {
                                    dragPx.animateTo(0f, dragSpring)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragPx.animateTo(0f, dragSpring) }
                        },
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    .graphicsLayer { alpha = visible.value.coerceIn(0f, 1f) },
            )
            // No close (X) button -- swipe-to-dismiss (or tapping the scrim above the
            // sheet) is already how this closes; a second, redundant affordance for
            // the same action was reported directly as unwanted clutter.
            MapFeatureRow(
                features = listOf(
                    MapFeature(Icons.Filled.MyLocation, "Recentre") { mapState.recenter() },
                    MapFeature(Icons.Filled.Map, "Open in Maps") {
                        openInExternalMaps(context, location, vehicleName)
                    },
                    // FRAMEWORK: append future map features here -- each is just an
                    // icon, a label and an action, e.g.:
                    //   MapFeature(Icons.Filled.AltRoute, "Directions") { ... }
                    //   MapFeature(Icons.Filled.Layers, "Traffic") { ... }
                    //   MapFeature(Icons.Filled.Search, "Nearby") { ... }
                    //   MapFeature(Icons.Filled.Share, "Share location") { ... }
                ),
                // No background band here either -- each pill already carries its own
                // opaque chrome (MorphButton's own container fill), which is plenty of
                // legibility on its own without a second, full-width tint behind them.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = visible.value.coerceIn(0f, 1f) },
            )
        }
    }
}

// --- Service & links ------------------------------------------------------


/**
 * Opens the car's location in the device's default Maps app -- a `geo:` intent
 * rather than hardcoding Google Maps, since the OS resolves it to whatever the user
 * actually has set. Shared by [LocationPebble]'s own "Open in maps" button and
 * [CarMapFullScreenDialog]'s [MapFeature] row so the two never drift on the URI
 * format.
 */
internal fun openInExternalMaps(context: Context, location: GeoLocation, label: String) {
    val uri = (
        "geo:${location.latitude},${location.longitude}" +
            "?q=${location.latitude},${location.longitude}($label)"
    ).toUri()
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

internal fun openUrl(context: Context, url: String) {
    val uri = url.toUri()
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
        .onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        }
}

internal fun openApp(context: Context, packages: List<String>, fallbackUrl: String) {
    for (p in packages) {
        context.packageManager.getLaunchIntentForPackage(p)?.let {
            runCatching { context.startActivity(it) }.onSuccess { return }
        }
    }
    openUrl(context, fallbackUrl)
}

internal fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
