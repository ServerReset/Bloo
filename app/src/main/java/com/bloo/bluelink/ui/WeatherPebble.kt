@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Location + weather pebbles: LocationPebble, WeatherStripe, WeatherPebble,
 * CarMap, weatherIcon/weatherTint and the openUrl/openApp/dial launchers --
 * extracted from Pebbles.kt.
 */

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.formatSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt


@Composable
internal fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val fahrenheit = LocalAppearance.current.useFahrenheit
    val location = state.locations[v.vin]
    // ACCESS_FINE_LOCATION -- needed for the map's own "device location" blue dot
    // ([UiState.deviceLocation], via LocationHelper -- see its own doc) -- is
    // otherwise only ever requested from AutoLock's settings screen. A user who
    // has never touched AutoLock had no way to grant it at all, so the dot
    // silently never appeared: not a rendering bug, [AppViewModel.refreshDeviceLocation]
    // was faithfully calling LocationHelper every refresh and getting null back
    // every single time from a permission check that had nothing to request it.
    // Reported directly as "the map is also not showing the person's location" --
    // tying the request to this pebble's own existing "Locate" action means
    // granting it happens as a direct result of something the user already does
    // to use this card, not a surprise prompt the moment it renders.
    val fineLocationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.locate(v) }
    fun locateWithPermission() {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) vm.locate(v) else fineLocationLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
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
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            Text("Tap Locate to query the car's current position.")
        }
        // Mirror of the "not located yet" AnimatedVisibility above -- same
        // pebble, same boolean flip, only the empty side had the treatment.
        AnimatedVisibility(
            visible = location != null,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            val loc = location
            if (loc != null) {
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
                        onExpand = { expandedMap.vin = v.vin },
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
                        onExpand = { showMapSheet = true },
                    )
                    if (showMapSheet) {
                        CarMapSheet(loc, v.name, state.deviceLocation, mapOriginBounds) { showMapSheet = false }
                    }
                }
                // Same reasoning as the cover hero above: a resolved address is
                // already the pebble's header/summary, so a permanent raw-coordinate
                // row here was redundant with it every single time -- exactly what
                // "should be an address, not coordinates" was pointing at. Only shown
                // as a fallback while geocoding hasn't (yet, or ever) resolved a name.
                if (!coverGlance && place == null) StatusRow("Location", loc.coordString())
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
                // same idiom the Climate pebble's smart-climate section uses.
                PopVisible(visible = carWeather != null) {
                    if (carWeather != null) WeatherStripe(carWeather, fahrenheit, place ?: "At the car")
                }
                CommandButton("Open in maps", Icons.Filled.Map, Modifier.fillMaxWidth(), true) {
                    openInExternalMaps(context, loc, v.name)
                }
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
 * The Weather pebble: current conditions at the user's configured "home"
 * location, with a big temperature, condition icon and a few detail rows. Shown
 * identically on every car (it's a global readout). If no location is set it
 * nudges the user to Settings.
 */
@Composable
internal fun WeatherPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val appearance = LocalAppearance.current
    val hasLocation = appearance.weatherLat != null && appearance.weatherLon != null
    val fahrenheit = appearance.useFahrenheit
    val w = state.homeWeather
    var weatherSpinning by remember { mutableStateOf(false) }
    var spinStartedAt by remember { mutableLongStateOf(0L) }
    // Refresh on first show (the VM throttles to a 15-minute TTL).
    LaunchedEffect(appearance.weatherLat, appearance.weatherLon) {
        if (hasLocation) vm.loadHomeWeather()
    }
    // Stop the spinner once new weather data arrives, but keep it visible for a
    // minimum duration so a cached/instant response still shows the animation.
    LaunchedEffect(state.homeWeather?.fetchedAt) {
        if (weatherSpinning) {
            val elapsed = System.currentTimeMillis() - spinStartedAt
            val minSpin = 900L
            if (elapsed < minSpin) delay(minSpin - elapsed)
            weatherSpinning = false
        }
    }
    val summary = when {
        !hasLocation -> "Set a location"
        w != null -> "${w.tempLabel(fahrenheit)} · ${w.condition.label}"
        else -> "Loading…"
    }
    Pebble(
        v, "weather", "Weather", Icons.Filled.WbSunny, state, vm, dragHandle, summary = summary,
        headerAction = PebbleHeaderAction(
            label = "Refresh",
            icon = Icons.Filled.Refresh,
            onClick = {
                weatherSpinning = true
                spinStartedAt = System.currentTimeMillis()
                vm.loadHomeWeather(force = true)
            },
            enabled = hasLocation,
            spinning = weatherSpinning,
        ),
        // NOT alwaysExpandedInSimpleMode: that flag is for pebbles with a single setting
        // that reads better inline without an expand/collapse control (see its own doc).
        // This one renders temperature, condition, and several more StatusRows below,
        // so forcing it always open in simple mode just removed the ability to collapse it.
    ) {
        when {
            !hasLocation -> Text(
                "Set your weather location in Settings → Weather to see local conditions here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            w == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Fetching current conditions…")
            }
            else -> {
                val tint = weatherTint(w.condition, w.isDay)
                // COVER SCREEN: center the icon+temp and make the temp bigger so the
                // tile reads as a weather face; the phone keeps the left-aligned
                // icon+column layout. Gated on LocalForceExpanded.
                val coverGlance = LocalForceExpanded.current
                if (coverGlance) {
                    // The COVER's headline is already this pebble's summary -- "72° · Partly
                    // cloudy" -- so the temperature and the condition word are both spoken for
                    // before the body starts. What was here repeated them at display size beside
                    // a 64dp icon, which is why this tile alone needed a font-scale guard
                    // against ellipsizing its own temperature: it was fighting for width it did
                    // not need to spend.
                    //
                    // The picture is the one thing the header cannot carry (its icon is a fixed
                    // sun, not the live condition), so the icon stays and the words go.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Icon(
                            weatherIcon(w.condition, w.isDay),
                            contentDescription = w.condition.label,
                            tint = tint,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    appearance.weatherLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            weatherIcon(w.condition, w.isDay),
                            contentDescription = w.condition.label,
                            tint = tint,
                            modifier = Modifier.size(64.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            RollingNumber(
                                text = w.tempLabel(fahrenheit),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(w.condition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            appearance.weatherLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                // No trailing Spacer — the cover shell's spacedBy owns the gap uniformly.
                StatusRow("Feels like", w.feelsLikeLabel(fahrenheit))
                w.highLowLabel(fahrenheit)?.let { StatusRow("High / low", it) }
                // Humidity + wind are secondary; hide them on the cover so it reads as
                // a clean weather face (feels-like + high/low stay).
                if (!coverGlance) {
                    w.humidity?.let { StatusRow("Humidity", "$it%") }
                    StatusRow("Wind", formatSpeed(w.windKph, appearance.unitSystem == "metric"))
                }
            }
        }
    }
}

/** Zoom bounds for [CarMap]'s zoom, pinch or button-driven -- 3 is "half the
 *  continent", 19 is past what OSM actually serves tiles for. */
private const val CarMapMinZoom = 3
private const val CarMapMaxZoom = 19

/** Where every [CarMapState] starts: street level, car dead-centre. */
private const val CarMapDefaultZoom = 15

/** The standard "you are here" blue, distinct from the car's own [MaterialTheme]
 *  error-toned pin so the two markers never read as the same kind of thing. */
private val DeviceLocationBlue = Color(0xFF4285F4)

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
    /** A small expand icon in the top-end corner, calling this when tapped --
     *  the compact map's way into [CarMapFullScreenDialog]. Null (the default)
     *  hides the button entirely; the full-screen map itself passes null, since
     *  it has nowhere further to expand to. */
    onExpand: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // Contrast-aware pin color: bright on dark maps, dark on light maps
    // OSM maps use a light color scheme with blues/greens/grays
    // so we use a bright red pin on the map, but ensure readability
    val pinColor = MaterialTheme.colorScheme.error
    val isDarkMode = isSystemInDarkTheme()

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
                            .crossfade(true)
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
        deviceLocation?.let { dev ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset {
                        val devOffX = ((MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx) + state.panX
                        val devOffY = ((MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx) + state.panY
                        IntOffset(devOffX.roundToInt(), devOffY.roundToInt())
                    }
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .padding(3.dp)
                    .background(DeviceLocationBlue, CircleShape),
            )
        }
        } // close the scaled tiles/pin/dot layer
        // FloatingIcon, not a bespoke one-off circle -- the same translucent chrome
        // every other floating corner button in the app already uses (Guard.kt's
        // Reload/Settings, the cover screen's own headers), so this reads as "the
        // app's floating button" rather than a control invented just for the map.
        // Reported directly as wanting this "more integrated". A smaller outerPadding
        // than FloatingIcon's own 12dp default keeps its footprint compact enough for
        // the small cover-screen map tile, which FloatingIcon's usual 72dp corner
        // clearance was never sized for.
        if (onExpand != null) {
            FloatingIcon(
                Icons.Filled.Fullscreen,
                "Expand map",
                onExpand,
                modifier = Modifier.align(Alignment.TopEnd),
                outerPadding = 4.dp,
            )
        }
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
 * trades) instead of each growing independently into free space. That was the first
 * version here: each button wrapped in a standalone [SafeExpansiveButton], which
 * grows for real -- correct for a lone button, but reported directly as "the map
 * buttons don't push each other, they just expand" once there were two of them
 * sharing a row. `wrap = true` (not a scrolling Row): a future feature list long
 * enough to overflow one line wraps to a second instead of needing its own
 * horizontal-scroll affordance.
 */
@Composable
private fun MapFeatureRow(features: List<MapFeature>, modifier: Modifier = Modifier) {
    ExpressiveButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        spacing = 10.dp,
        wrap = true,
    ) {
        features.forEach { feature ->
            val source = remember { MutableInteractionSource() }
            SafeExpansiveButton(interactionSource = source, enabled = feature.enabled) {
                MorphButton(
                    onClick = feature.onClick,
                    interactionSource = source,
                    enabled = feature.enabled,
                ) {
                    MorphButtonLabel(feature.icon, feature.label, pending = false)
                }
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
            onDismiss = onDismiss,
        )
    }
}

/**
 * THE single CarMap instance, repositionable between pebble and full-screen.
 * Literally one Box growing from the pebble location to fill the screen.
 * Not two separate maps or a morphing animation -- the actual component
 * expanding with smooth size/position animation.
 */
@Composable
internal fun ExpandableMapLayer(
    isExpanded: Boolean,
    originBounds: Rect,
    location: GeoLocation,
    vehicleName: String,
    deviceLocation: GeoLocation?,
    mapState: CarMapState,
    hazeState: HazeState?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapHazeState = remember { HazeState() }

    // Animate from pebble size to full screen
    val expandFraction = remember { Animatable(if (isExpanded) 1f else 0f) }

    LaunchedEffect(isExpanded) {
        expandFraction.animateTo(
            if (isExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }

    // Captured at full-screen layout
    var fullBounds by remember { mutableStateOf<Rect?>(null) }

    // Back handler for closing
    BackHandler(enabled = isExpanded) {
        scope.launch {
            expandFraction.animateTo(0f, animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium))
            onDismiss()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Scrim background (dims/blurs content behind)
        if (isExpanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Color.Black, alpha = (if (hazeState != null) 0.35f else 0.5f) * expandFraction.value.coerceIn(0f, 1f))
                    }
            )
            if (hazeState != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) }
                        .hazeEffect(state = hazeState) {
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        }
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        scope.launch {
                            expandFraction.animateTo(0f, animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium))
                            onDismiss()
                        }
                    }
            )
        }

        // The map container - single Box that animates from pebble to full-screen
        // Using graphicsLayer to scale and position, creating the visual effect
        // of the same Box growing without morphing or scale tricks
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = expandFraction.value.coerceIn(0f, 1f)
                    // Calculate scale from pebble size to full-screen
                    val scaleX = originBounds.width / this@graphicsLayer.size.width
                    val scaleY = originBounds.height / this@graphicsLayer.size.height
                    this.scaleX = scaleX + (1f - scaleX) * t
                    this.scaleY = scaleY + (1f - scaleY) * t
                    // Calculate translation from pebble center to screen center
                    val screenCenterX = this@graphicsLayer.size.width / 2f
                    val screenCenterY = this@graphicsLayer.size.height / 2f
                    val pebbleCenterX = originBounds.center.x
                    val pebbleCenterY = originBounds.center.y
                    this.translationX = (pebbleCenterX - screenCenterX) * (1f - t)
                    this.translationY = (pebbleCenterY - screenCenterY) * (1f - t)
                }
                .clip(
                    // Clip corners: 18dp when collapsed, 0dp when expanded
                    RoundedCornerShape(18.dp * (1f - expandFraction.value.coerceIn(0f, 1f)))
                )
                .clipToBounds()
        ) {
            // The actual map content
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { fullBounds = Rect(it.positionOnScreen(), it.size.toSize()) }
            ) {
                CarMap(
                    location,
                    Modifier.fillMaxSize().hazeSource(mapHazeState),
                    state = mapState,
                    deviceLocation = deviceLocation,
                    onExpand = null,
                )
            }
        }

        // Vehicle name pill (appears when expanded)
        if (isExpanded && expandFraction.value > 0.1f) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) }
                    .dropShadow(RoundedCornerShape(50))
                    .appGlassRim(RoundedCornerShape(50)),
            ) {
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Bottom buttons (appear when expanded)
        if (isExpanded && expandFraction.value > 0.1f) {
            MapFeatureRow(
                features = listOf(
                    MapFeature(Icons.Filled.MyLocation, "Recentre") { mapState.recenter() },
                    MapFeature(Icons.Filled.Map, "Open in Maps") {
                        openInExternalMaps(context, location, vehicleName)
                    },
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
            )
        }

        // Drag handle (appears when expanded)
        if (isExpanded && expandFraction.value > 0.1f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, _ -> },
                            onDragEnd = {
                                scope.launch {
                                    expandFraction.animateTo(0f, animationSpec = spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium))
                                    onDismiss()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // A small frosted chip behind the handle pill, same pattern as
                // CarMapSheetBody's own drag handle -- blurs the map on API 31+
                // (where Haze's RenderEffect backing exists), or just darkens on
                // older devices, so the pill stays visible over any tile content.
                val canBlurHandle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(width = 56.dp, height = 20.dp)
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (canBlurHandle) Modifier.hazeEffect(state = mapHazeState)
                            else Modifier,
                        )
                        .background(Color.Black.copy(alpha = if (canBlurHandle) 0.2f else 0.35f))
                        .graphicsLayer { alpha = expandFraction.value.coerceIn(0f, 1f) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
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
    CarMapSheetBody(location, vehicleName, deviceLocation, mapState, originBounds, hazeState, onDismiss)
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

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            // Both play at once so a mid-drag dismiss doesn't visibly snap dragPx
            // back to 0 before the slide-out starts. Use a spring for close to match
            // the bouncy open, but with much more damping so it settles quickly.
            val a = scope.launch { visible.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)) }
            val b = scope.launch { dragPx.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = Spring.StiffnessMedium)) }
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
        visible.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Box(Modifier.fillMaxSize()) {
        // The scrim -- dims (and, with a real hazeState, blurs) the app behind the
        // sheet, and a tap on it dismisses.
        Box(Modifier.fillMaxSize()) {
            // The flat tint alone, present from the first frame -- fades in/out
            // with [visible] rather than being either fully on or fully off the
            // instant this composes. Clamped to [0, 1]: unlike the scale/
            // translation elsewhere in this function, an overshoot past 1 from
            // the open spring's own bounce (see its own doc) has no sensible
            // meaning for an alpha, which both Color and graphicsLayer expect in
            // that range regardless.
            //
            // drawBehind, NOT `.background(Color.Black.copy(alpha = ...))`: the
            // latter takes a plain Color, computed while building the Modifier
            // chain -- i.e. read at COMPOSITION time, not draw time -- so reading
            // `visible.value` there recomposed this entire sheet (the map's own
            // tile loop, the button row, everything) on every single frame of
            // the open/close spring, real and reported directly as bad
            // performance/a broken-looking animation. drawBehind's lambda is a
            // draw-phase read, same as the graphicsLayer{} blocks everywhere else
            // in this function: the colour updates every frame without ever
            // triggering a recomposition.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(Color.Black, alpha = (if (hazeState != null) 0.35f else 0.5f) * visible.value.coerceIn(0f, 1f))
                    },
            )
            // The real blur, layered on top and CROSSFADED in over the flat tint
            // above via its own alpha -- Haze's own blur intensity has no
            // reliably animatable hook on this pinned 1.7.0 (the same library
            // version whose `hazeEffect(state=, style=)` two-arg overload already
            // hit a compile-time "overload ambiguity" once), so rather than
            // gamble on a second uncertain API surface, the ALREADY fully-blurred
            // layer simply fades in as a whole, the same graphicsLayer-alpha
            // technique every other reveal in this file already uses. Reported
            // directly as wanting the blur's own opening/closing refined, not
            // just popping fully blurred in on the first frame.
            if (hazeState != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Read inside the lambda (layout/draw-phase), not hoisted
                        // to a composable-scope val -- the latter would force a
                        // full recomposition of this whole sheet on every single
                        // animation frame instead of a cheap draw-phase update,
                        // the same mistake the map's OWN pan/zoom code was just
                        // fixed for.
                        .graphicsLayer { alpha = visible.value.coerceIn(0f, 1f) }
                        .hazeEffect(state = hazeState) {
                            // Same "strong at the top, none by the bottom" gradient
                            // StatusBarScrim uses, for the same reason: this scrim
                            // covers a much taller strip of app than that one does,
                            // and a flat blur across all of it read as a uniform
                            // smear with a hard edge at the sheet's own top corner
                            // rather than a soft transition into it.
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        },
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() },
            )
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}, // swallow taps so they don't fall through to the scrim behind
                ),
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
                    onExpand = null,
                )
            }
            // The vehicle name, as its own small floating pill -- the same
            // translucent glass chrome every other floating control in the app
            // uses ([FloatingIcon]'s own colour/rim/shadow), not bare text with a
            // drop shadow. Reported directly as wanting it "in some sort of
            // floating element" rather than text alone sitting on the map.
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .graphicsLayer { alpha = visible.value.coerceIn(0f, 1f) }
                    .dropShadow(RoundedCornerShape(50))
                    .appGlassRim(RoundedCornerShape(50)),
            ) {
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
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
            // The drag handle -- and, with no ModalBottomSheet swipe gesture of its
            // own underneath this, the ONLY thing on this sheet a user can pull down
            // to dismiss (see [dragPx]'s own doc up top). A generous 48dp touch
            // target around a slim visible pill, the same "small glyph, big hit
            // area" shape every other icon-only control in the app already uses.
            // Slimmer and dimmer than the first version here (28dp wide, 0.4 alpha,
            // down from 32dp/0.6) -- reported directly as "a bit obtuse": a heavy,
            // high-contrast bar read as its own UI element rather than the quiet
            // affordance a drag handle is supposed to be.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(Unit) {
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
                                        dragPx.animateTo(0f, spring(dampingRatio = SoftDamping))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragPx.animateTo(0f, spring(dampingRatio = SoftDamping)) }
                            },
                        )
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                // A small frosted chip behind the pill itself, blurring the map
                // (via mapHazeState -- see its own doc for why this one, not the
                // sheet's own [hazeState]) -- reported directly as wanting the
                // handle "more visible": a bare translucent bar sitting directly on
                // the map read fine over open sky but vanished over a light road or
                // a bright building roof right under it. Blurring (on API 31+,
                // where Haze's RenderEffect backing actually exists -- same
                // canBlur guard StatusBarScrim uses) or, on older devices, just
                // darkening the map immediately behind this one small area
                // guarantees contrast for the pill regardless of what's drawn
                // under it.
                val canBlurHandle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(width = 56.dp, height = 20.dp)
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (canBlurHandle) {
                                Modifier.hazeEffect(state = mapHazeState)
                            } else {
                                Modifier
                            },
                        )
                        .background(Color.Black.copy(alpha = if (canBlurHandle) 0.2f else 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
                    )
                }
            }
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
    val uri = Uri.parse(
        "geo:${location.latitude},${location.longitude}" +
            "?q=${location.latitude},${location.longitude}($label)"
    )
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

internal fun openUrl(context: Context, url: String, inApp: Boolean) {
    val uri = Uri.parse(url)
    val external = { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    if (inApp) {
        runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
            .onFailure { runCatching { external() } }
    } else {
        runCatching { external() }
    }
}

internal fun openApp(context: Context, packages: List<String>, fallbackUrl: String, inApp: Boolean) {
    for (p in packages) {
        context.packageManager.getLaunchIntentForPackage(p)?.let {
            runCatching { context.startActivity(it) }.onSuccess { return }
        }
    }
    openUrl(context, fallbackUrl, inApp)
}

internal fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
