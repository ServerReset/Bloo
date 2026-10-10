package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * A small slippy map centred on the car, assembled from key-free OpenStreetMap raw tiles. We
 * compute the tiles needed to fill the box with the car at the centre, draw each at its pixel
 * offset, then drop a pin. This avoids the flaky static-map render services that painted blank.
 */
@Composable
internal fun CarMap(
    location: GeoLocation,
    modifier: Modifier = Modifier,
    state: CarMapState = rememberCarMapState(),
    /**
     * The DEVICE's own last-known position (not the car's) -- [UiState.deviceLocation], refreshed
     * on app open/refresh and by "Locate", NOT fetched by CarMap itself. Null (never fetched yet,
     * or no permission/fix) simply omits the marker.
     */
    deviceLocation: GeoLocation? = null,
    /**
     * False freezes the map: no pan and no zoom. The compact garage map is static so a horizontal
     * swipe over it still turns the car page; only the full-screen map is draggable.
     */
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    // Cold-start diagnostic; see HeroVisual's matching mark.
    com.bloo.bluelink.data.StartupTrace.once("carmap-composing", "CarMap composing (first one)")

    // The car's pin uses the dynamic/custom-palette primary so it matches the car's screen.
    val pinColor = MaterialTheme.colorScheme.primary
    // The device marker uses the secondary role to stay distinct from the car pin.
    val deviceLocationColor = MaterialTheme.colorScheme.secondary
    // Same fix as pebbleCardEdge/glassTint (GlassChrome.kt): resolve dark from the app's own
    // ThemeMode override, not a raw isSystemInDarkTheme() read -- otherwise a user who forced
    // Light/Dark against a differently-set system theme got a map whose colour filter didn't match
    // the rest of the app.
    val isDarkMode = appIsDarkTheme()

    // Tiles carry the map theme; background stays minimal.
    val mapBackground = if (isDarkMode) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    // Client-side dark filter over the same OSM tiles: invert() + hue-rotate(180deg) turns a light
    // raster into a passable dark one (inversion alone flips hues to their RGB complement).
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
        // The W3C hue-rotate(180deg) filter matrix, applied to the ALREADY-inverted colour rather
        // than the original -- postConcat runs `invert` first, then this, exactly matching the CSS
        // `filter: invert(1) hue-rotate(180deg)` order the whole trick is borrowed from.
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

    // Measured with onSizeChanged, not BoxWithConstraints (a SubcomposeLayout defers composition,
    // costly while `location` updates continuously). Zero for the first frame just draws no tiles.
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .background(mapBackground)
            .onSizeChanged { boxSizePx = it }
            .then(
                if (interactive) {
                    Modifier.pointerInput(state) {
                        detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                            // Tiles sit in a layer scaled by state.scale (0.5..2), so a drag of d px
                            // moves them d * scale; divide by scale to track the finger 1:1.
                            val s = state.scale
                            state.pan(gesturePan.x / s, gesturePan.y / s)
                            state.pinch(gestureZoom)
                        }
                    }
                } else {
                    Modifier
                },
            ),
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

        // Zooms out (never in) to bring the device into view the first time both fixes are known
        // and the map is at rest; see CarMapState.fitBothLocations.
        LaunchedEffect(location.latitude, location.longitude, deviceLocation?.latitude, deviceLocation?.longitude, wPx, hPx) {
            val dev = deviceLocation
            if (dev != null) {
                state.fitBothLocations(location.latitude, location.longitude, dev.latitude, dev.longitude, wPx, hPx)
            }
        }

        // Needed tile range, derived so a live pan/pinch only recomposes when it crosses a tile
        // boundary. fetchScaleCoverage over-fetches by 1/scale so a pinched-out grid (even past
        // CarMapMinZoom) never shows blank margins.
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

        // Warms Coil's cache for the next zoom level in each direction once zoom settles.
        // delay(300) debounces rapid zoom changes (enqueue() outlives this coroutine); the batch is
        // disposed in `finally` once superseded so it stops competing with visible tiles.
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
                            // enqueue() has no suspension point; check cancellation explicitly so a
                            // superseded batch stops.
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

        // Everything below (tiles, pin, device dot) sits inside its own scaled layer -- NOT the
        // outer Box, which also hosts the expand button (CarMap's own doc) and must stay at 1x
        // regardless of how far a pinch has scaled the map itself. state.scale is the CONTINUOUS
        // part of a pinch (see its own doc): applying it here, every frame of the gesture, is what
        // makes zooming feel fluid instead of jumping between the whole levels a fresh tile fetch
        // actually needs.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Pan is one layer translation; children are placed at pan-independent
                    // positions. Divide by `scale` to bring tile-space pan into parent space (keeps
                    // 1:1 finger tracking).
                    translationX = state.panX * state.scale
                    translationY = state.panY * state.scale
                    scaleX = state.scale
                    scaleY = state.scale
                },
        ) {
        for (tx in range.firstX..range.lastX) {
            for (ty in range.firstY..range.lastY) {
                if (ty < 0 || ty >= span) continue
                val wrappedX = MapTiles.wrapX(tx, zoom)
                // key(), not a bare loop body: gives each tile a stable slot keyed by its own tile
                // coordinate, so the remember() just below is safe to use inside a plain for-loop
                // (whose visible tile SET changes as the car/box moves) without its state silently
                // reattaching to the wrong tile between compositions.
                key(wrappedX, ty) {
                    // Remembered, not rebuilt on every recomposition of this composable (which
                    // happens on every `location` update, i.e. while the car/phone is moving):
                    // Coil's ImageRequest has no equals()/hashCode() override, so a fresh .build()
                    // every time is a reference-distinct object even when the URL/headers are
                    // identical -- AsyncImage keys its load launch on that identity, so an
                    // unremembered request restarted the whole load pipeline (a blank frame while
                    // it "reloads") for every visible tile on every location update, even for tiles
                    // already sitting in Coil's memory cache -- visible flicker across the whole
                    // map.
                    val request = remember(wrappedX, ty, zoom) {
                        ImageRequest.Builder(context)
                            .data(MapTiles.tileUrl(zoom, wrappedX, ty))
                            // OSM blocks clients whose User-Agent does not identify the app.
                            .setHeader("User-Agent", MapTiles.userAgent("Android"))
                            // No crossfade: Coil skips it only for memory hits, and the pebble map
                            // and full-screen sheet are separate CarMaps requesting the same URLs,
                            // so fading would look like a refresh.
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        colorFilter = darkMapFilter,
                        modifier = Modifier
                            .size(tileDp)
                            // Layout-phase placement: no recomposition per pan frame.
                            // Pan-independent placement; the container's graphicsLayer applies pan.
                            .offset {
                                val originX = xTileF * tilePx - wPx / 2f
                                val originY = yTileF * tilePx - hPx / 2f
                                IntOffset(
                                    (tx * tilePx - originX).roundToInt(),
                                    (ty * tilePx - originY).roundToInt(),
                                )
                            },
                    )
                }
            }
        }
        // On-screen distance between car pin and device dot in tile pixels (pan cancels out);
        // decides whether to merge them, accounting for zoom.
        val devTileOffsetPx = deviceLocation?.let { dev ->
            val dx = (MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx
            val dy = (MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx
            dx to dy
        }
        // Under this many px apart the pins overlap; merge them.
        val mergeThresholdPx = with(density) { 36.dp.toPx() }
        // Non-null only when the pins collide; carries the offset so the merged branch can use it
        // directly.
        val mergedOffsetPx = devTileOffsetPx?.takeIf { (dx, dy) ->
            kotlin.math.hypot(dx, dy) < mergeThresholdPx
        }
        if (mergedOffsetPx != null) {
            val (dx, dy) = mergedOffsetPx
            // One pin at the midpoint, tinted with an even mix of the car and device colours.
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "Car and your location",
                tint = lerp(pinColor, deviceLocationColor, 0.5f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset((dx / 2f).roundToInt(), (dy / 2f).roundToInt()) }
                    .size(40.dp)
                    .offset(y = (-20).dp),
            )
        } else {
            // Pin offset from the box centre; rides along with pan like the tiles.
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "Car location",
                tint = pinColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    // The car is the map's pan origin, so the container's own translation already
                    // puts it dead-centre -- no per-frame offset here any more.
                    .size(40.dp)
                    .offset(y = (-20).dp),
            )
        }
        // Device position, drawn whenever a fix exists, outside the merged branch so it never
        // vanishes. Its offset comes from its own tile coordinate (tile delta in px plus pan),
        // since it is not the view centre.
        devTileOffsetPx?.let { (dx, dy) ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                    .size(30.dp)
                    // The dot carries no text, so TalkBack would skip it entirely; name it.
                    .semantics { contentDescription = "Your location" },
                contentAlignment = Alignment.Center,
            ) {
                // A soft halo (clearer than a bare dot, and it survives a busy/satellite tile) under a
                // white ring and the accent dot.
                Box(Modifier.size(30.dp).background(deviceLocationColor.copy(alpha = 0.22f), CircleShape))
                Box(
                    Modifier
                        .size(16.dp)
                        .background(Color.White, CircleShape)
                        .padding(3.dp)
                        .background(deviceLocationColor, CircleShape),
                )
            }
        }
        } // close the scaled layer
        // Off-screen device indicator: when the phone is farther than the map shows, its dot is
        // clipped away entirely. Draw a marker clamped to the box edge pointing toward it, which
        // centres the map on it when tapped -- so "my location" is never simply nowhere.
        val dev = deviceLocation
        if (dev != null && wPx > 0f && hPx > 0f) {
            val halfW = wPx / 2f
            val halfH = hPx / 2f
            val sc = state.scale
            val sx = (((MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx).toFloat() + state.panX) * sc
            val sy = (((MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx).toFloat() + state.panY) * sc
            val marginPx = with(density) { 22.dp.toPx() }
            if (markerOffBox(sx, sy, halfW, halfH, marginPx)) {
                val (ex, ey) = clampMarkerToEdge(sx, sy, halfW, halfH, marginPx)
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(ex.roundToInt(), ey.roundToInt()) }
                        .size(30.dp)
                        .background(deviceLocationColor, CircleShape)
                        .clickable {
                            state.showDevice(location.latitude, location.longitude, dev.latitude, dev.longitude)
                        }
                        .semantics { contentDescription = "Your location is off screen. Tap to centre on it" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        // Expand/Open-in-Maps render as a MapFeatureRow in the caller, not over the map.
    }
}
