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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.formatSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor


@Composable
internal fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val fahrenheit = LocalAppearance.current.useFahrenheit
    val location = state.locations[v.vin]
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
            onClick = { vm.locate(v) },
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
                // Expanding opens CarMapSheet, a bottom sheet rather than the small map
                // growing in place -- see CarMap's own onExpand doc. Local to this pebble
                // instance: opening it here never affects another car's own map.
                var showMapSheet by remember { mutableStateOf(false) }
                CarMap(
                    loc,
                    Modifier
                        .fillMaxWidth()
                        .height(if (coverGlance) 130.dp else 220.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    deviceLocation = state.deviceLocation,
                    onExpand = { showMapSheet = true },
                )
                if (showMapSheet) {
                    CarMapSheet(loc, v.name, state.deviceLocation) { showMapSheet = false }
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

    // Accumulates a pinch gesture's own multiplicative ratio between whole
    // zoom-level steps -- detectTransformGestures reports a per-frame RATIO, not
    // an absolute scale, so this is what a pinch has to build up against before
    // it's worth redrawing a whole new tile grid. Reset (not just decremented)
    // every time a level actually flips, so the next level change needs a full
    // pinch of its own rather than coasting on leftover ratio.
    private var pinchAccum by mutableFloatStateOf(1f)

    /** A continuous drag, in screen pixels. Drag right -> the map (and
     *  everything drawn on it) should slide right with the finger, exactly like
     *  sliding a sheet of paper -- so the WORLD-pixel offset accumulates in the
     *  same direction as the drag; see CarMap's own originX/originY, which
     *  subtract this to shift what's visible. */
    fun pan(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    /** One frame of a pinch gesture's multiplicative ratio. The pan offset is
     *  halved/doubled in step with every level change: it is measured in tile
     *  PIXELS at the OLD zoom, and a zoom step doubles/halves how many pixels
     *  the same world distance covers. */
    fun pinch(ratio: Float) {
        pinchAccum *= ratio
        while (pinchAccum >= 2f && zoom < CarMapMaxZoom) {
            zoom++; panX *= 2f; panY *= 2f; pinchAccum /= 2f
        }
        while (pinchAccum <= 0.5f && zoom > CarMapMinZoom) {
            zoom--; panX /= 2f; panY /= 2f; pinchAccum *= 2f
        }
    }

    /** Back to the car, dead centre, default zoom -- the full-screen map's
     *  "Recentre" feature. */
    fun recenter() {
        zoom = CarMapDefaultZoom
        panX = 0f
        panY = 0f
        pinchAccum = 1f
    }
}

@Composable
internal fun rememberCarMapState(): CarMapState = remember { CarMapState() }

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
        val panX = state.panX
        val panY = state.panY
        val span = MapTiles.span(zoom)
        val xTileF = MapTiles.tileX(location.longitude, zoom)
        val yTileF = MapTiles.tileY(location.latitude, zoom)
        // World-pixel of the box's top-left: car-centred, then shifted by whatever the
        // user has panned away from that centre. .toFloat() matters here, not just
        // style: xTileF/yTileF are Double (MapTiles.tileX/Y), so without it originX/Y
        // silently promote to Double via Kotlin's numeric-tower rules -- fine for the
        // arithmetic a few lines down, but Double has no .toDp() extension, only
        // Float/Int, so every offX.toDp()/offY.toDp() call below stopped resolving
        // at all. Caught by CI, not by this compiling clean before the pan/zoom work
        // (originX/Y used to end in an explicit .toFloat() of the whole expression).
        val originX = (xTileF * tilePx - wPx / 2f - panX).toFloat()
        val originY = (yTileF * tilePx - hPx / 2f - panY).toFloat()
        val firstX = floor(originX / tilePx).toInt()
        val firstY = floor(originY / tilePx).toInt()
        val lastX = floor((originX + wPx) / tilePx).toInt()
        val lastY = floor((originY + hPx) / tilePx).toInt()
        val tileDp = with(density) { tilePx.toDp() }
        for (tx in firstX..lastX) {
            for (ty in firstY..lastY) {
                if (ty < 0 || ty >= span) continue
                val wrappedX = MapTiles.wrapX(tx, zoom)
                val offX = tx * tilePx - originX
                val offY = ty * tilePx - originY
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
                            .offset(x = with(density) { offX.toDp() }, y = with(density) { offY.toDp() }),
                    )
                }
            }
        }
        // The pin's screen offset from the box's own centre: zero (i.e. dead-centre) at
        // rest, and it rides along with panX/panY exactly like the tiles do -- so panning
        // away from the car slides the pin off toward wherever the car actually is
        // relative to the new view, instead of it staying glued to the middle of the box.
        val pinOffsetX = with(density) { panX.toDp() }
        val pinOffsetY = with(density) { panY.toDp() }
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = "Car location",
            tint = pinColor,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = pinOffsetX, y = pinOffsetY)
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
            val devOffX = (((MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx) + panX).toFloat()
            val devOffY = (((MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx) + panY).toFloat()
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = with(density) { devOffX.toDp() }, y = with(density) { devOffY.toDp() })
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .padding(3.dp)
                    .background(DeviceLocationBlue, CircleShape),
            )
        }
        if (onExpand != null) {
            MapOverlayIconButton(
                Icons.Filled.Fullscreen,
                "Expand map",
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                onClick = onExpand,
            )
        }
    }
}

/**
 * One small round chrome button floating over the map -- today, just the expand
 * corner button (zoom is pinch-only; see CarMap's own doc). Not [MorphIconButton]:
 * that one is deliberately containerless chrome (see its own doc), which reads fine
 * against a card's flat background but disappears against a map whose colour
 * underneath it is whatever terrain happens to be there. A filled, semi-opaque circle
 * behind the glyph keeps it legible over any tile.
 */
@Composable
private fun MapOverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
    Box(
        modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable(enabled = enabled) { haptics?.click(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
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

/** The full-screen map's bottom toolbar: one plain (unconnected) [MorphButton] pill
 *  per [MapFeature], in a horizontally scrolling row so the list can grow past
 *  whatever fits on one screen width without needing its own overflow menu. */
@Composable
private fun MapFeatureRow(features: List<MapFeature>, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        features.forEach { feature ->
            val source = remember { MutableInteractionSource() }
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

/**
 * The map, expanded into a bottom sheet -- reached from [CarMap]'s own corner button.
 * Its own [CarMapState] ([rememberCarMapState]), independent of whatever the small
 * inline map is panned/zoomed to: expanding is a bigger canvas to look at the SAME
 * car on, not a continuation of one specific gesture.
 *
 * A real [ModalBottomSheet], not a hand-rolled [Dialog]. That was the first attempt
 * here, driving its own `graphicsLayer` scale/translate from a captured on-screen
 * rect -- reported directly as not actually seamless, not full screen, and covering
 * its own close button. A bottom sheet gets the genuinely wanted behaviour for free
 * from a component the platform already gets right: it rises from the bottom rather
 * than appearing in place, covers most but not all of the screen (the app stays
 * visible, dimmed, above it -- `sheetGesturesEnabled` sizing leaves the status bar
 * clear rather than the previous attempt's edge-to-edge Dialog), carries a real drag
 * handle, and -- most importantly for "pull it back down to collapse" -- already
 * supports swipe-to-dismiss as a first-class gesture instead of something this file
 * would have to reinvent on top of a Dialog.
 *
 * [CarMap] itself still grows in with a short scale+fade (see `entryScale` below) so
 * opening reads as the map continuing to expand rather than a flat cut, without
 * reaching for the previous attempt's cross-window position math to do it.
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Runs the sheet's own hide animation (a slide back down, same motion a drag-to-
    // dismiss ends in) before actually dismissing, so the close button matches
    // whatever a swipe already does rather than snapping the sheet away instantly.
    val dismissAnimated = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
        Unit
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // The map's own short grow-in -- NOT tied to the sheet's own slide-up (that
        // already has its own motion; doubling it up read as the map "catching up"
        // late). Scale from a touch under full size rather than from zero: this is
        // "the map continuing to expand", not a new element materialising.
        val entryScale = remember { Animatable(0.92f) }
        LaunchedEffect(Unit) {
            entryScale.animateTo(1f, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow))
        }
        Column(
            Modifier
                // ~90% of the sheet's own available height, itself already capped
                // short of the full display by ModalBottomSheet -- together the app
                // stays visible (dimmed by the sheet's own scrim) in the gap above,
                // reported directly as wanting the background to still read as "the
                // app," not a second full screen replacing it outright.
                .fillMaxHeight(0.9f)
                .fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FloatingIcon(Icons.Filled.Close, "Close", dismissAnimated)
            }
            val sheetMapState = rememberCarMapState()
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        scaleX = entryScale.value
                        scaleY = entryScale.value
                    },
            ) {
                CarMap(
                    location,
                    Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                    state = sheetMapState,
                    deviceLocation = deviceLocation,
                    onExpand = null,
                )
            }
            MapFeatureRow(
                features = listOf(
                    MapFeature(Icons.Filled.MyLocation, "Recentre") { sheetMapState.recenter() },
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
                modifier = Modifier.navigationBarsPadding(),
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
