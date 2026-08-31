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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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
    val place = state.placeNames[v.vin]
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
        alwaysExpandedInSimpleMode = true,
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
                    CoverHero(
                        icon = Icons.Filled.LocationOn,
                        value = place ?: "Located",
                        subline = if (place == null) "Resolving address…" else null,
                    )
                }
                CarMap(
                    loc,
                    Modifier
                        .fillMaxWidth()
                        .height(if (coverGlance) 130.dp else 220.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
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
                    val uri = Uri.parse(
                        "geo:${loc.latitude},${loc.longitude}" +
                            "?q=${loc.latitude},${loc.longitude}(${v.name})"
                    )
                    // Use the default maps app instead of hardcoding Google Maps
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                        )
                    }
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
                // Only up-size the temp when the user's font scale is modest — at a
                // large display/font size (the mom's setup) displayMedium + the fixed
                // 64dp icon can exceed the narrow cover width and ellipsize the temp
                // to "72…". Above ~1.15x, keep displaySmall so the value stays whole.
                val bigTemp = coverGlance && LocalDensity.current.fontScale <= 1.15f
                Row(
                    modifier = if (coverGlance) Modifier.fillMaxWidth() else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (coverGlance) Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                            else Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        weatherIcon(w.condition, w.isDay),
                        contentDescription = w.condition.label,
                        tint = tint,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(if (coverGlance) Modifier else Modifier.weight(1f)) {
                        RollingNumber(
                            text = w.tempLabel(fahrenheit),
                            style = if (bigTemp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(w.condition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        appearance.weatherLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * A small slippy map centred on the car, assembled from key-free OpenStreetMap
 * raw tiles (tile.openstreetmap.org). We compute the tiles needed to fill the box
 * with the car at the centre, draw each at its pixel offset, then drop a pin in
 * the middle. This avoids the flaky static-map render services that painted blank.
 */
@Composable
internal fun CarMap(location: GeoLocation, modifier: Modifier = Modifier) {
    val zoom = 15
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

    BoxWithConstraints(
        modifier.background(mapBackground),
    ) {
        val density = LocalDensity.current
        val tilePx = MapTiles.TILE_PX.toFloat()
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val span = MapTiles.span(zoom)
        val xTileF = MapTiles.tileX(location.longitude, zoom)
        val yTileF = MapTiles.tileY(location.latitude, zoom)
        // World-pixel of the box's top-left so the car lands dead-centre.
        val originX = (xTileF * tilePx - wPx / 2f).toFloat()
        val originY = (yTileF * tilePx - hPx / 2f).toFloat()
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
                        modifier = Modifier
                            .size(tileDp)
                            .offset(x = with(density) { offX.toDp() }, y = with(density) { offY.toDp() }),
                    )
                }
            }
        }
        // A pin whose tip points at the centred car position.
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = "Car location",
            tint = pinColor,
            modifier = Modifier.align(Alignment.Center).size(40.dp).offset(y = (-20).dp),
        )
    }
}

// --- Service & links ------------------------------------------------------


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
