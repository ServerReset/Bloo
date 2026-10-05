package com.bloo.bluelink.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.roundToInt

/** A small slippy map centred on the car, built from key-free OpenStreetMap raw tiles. */
@Composable
internal fun CarMap(
    location: GeoLocation,
    modifier: Modifier = Modifier,
    state: CarMapState = rememberCarMapState(),
    /**
     * The device's own last-known position ([UiState.deviceLocation]), not fetched here. Null (no
     * fix/permission) omits the marker.
     */
    deviceLocation: GeoLocation? = null,
) {
    val context = LocalContext.current
    // Cold-start diagnostic; see HeroVisual's matching mark.
    com.bloo.bluelink.data.StartupTrace.once("carmap-composing", "CarMap composing (first one)")

    // The car's pin uses the dynamic/custom-palette primary so it matches the car's screen.
    val pinColor = MaterialTheme.colorScheme.primary
    // The device marker uses the secondary role to stay distinct from the car pin.
    val deviceLocationColor = MaterialTheme.colorScheme.secondary
    // Resolve dark from the app's ThemeMode override, not isSystemInDarkTheme().
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
        // W3C hue-rotate(180deg) matrix, applied after `invert` to match CSS `invert(1)
        // hue-rotate(180deg)`.
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
            .pointerInput(state) {
                detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                    // Tiles sit in a layer scaled by state.scale (0.5..2), so a drag of d px moves
                    // them d * scale; divide by scale to track the finger 1:1.
                    val s = state.scale
                    state.pan(gesturePan.x / s, gesturePan.y / s)
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

        // Tiles, pin and device dot sit in their own scaled layer, not the outer Box (which hosts
        // the 1x expand button). state.scale is the continuous pinch part, applied every frame for
        // fluid zoom.
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
                // key() gives each tile a stable slot so remember() below is safe in a loop.
                key(wrappedX, ty) {
                    // Remembered: ImageRequest has no equals(), so a fresh build() would restart
                    // AsyncImage's load (flicker) on every location update.
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
                    // The car is the pan origin, so the layer translation already centres it.
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
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .padding(3.dp)
                    .background(deviceLocationColor, CircleShape),
            )
        }
        } // close the scaled layer
        // Expand/Open-in-Maps render as a MapFeatureRow in the caller, not over the map.
    }
}
