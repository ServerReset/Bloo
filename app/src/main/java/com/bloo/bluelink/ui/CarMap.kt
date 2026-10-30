package com.bloo.bluelink.ui

import com.bloo.bluelink.data.StartupLabels
import com.bloo.bluelink.data.StartupTrace
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import com.bloo.uicommon.AppIcons
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.isActive
import com.bloo.uicommon.LocalPowerPolicy

/** How many frames the first map in a process waits before it composes, so it lands after the cards and photos the garage draws first. */
private const val MapStartFrames = 6

/** Open once any map has started: later maps (a second car's, the expanded one) compose at once. */
private object MapStartGate {
    @Volatile var open = false
}

/**
 * The car map ([CarMapBody]), started a few frames after the first screen rather than in it. The map's first composition and its burst
 * of tile requests landed in the same frames as the hero photos on a cold start (a 400 ms frame in a measured log); the box is there at
 * once, so nothing moves when the map arrives.
 */
@Composable
internal fun CarMap(
    location: GeoLocation,
    modifier: Modifier = Modifier,
    state: CarMapState = rememberCarMapState(),
    deviceLocation: GeoLocation? = null,
    interactive: Boolean = true,
    tileArea: IntSize = IntSize.Zero,
    panWeight: () -> Float = { 1f },
    /** The other cars on this map, each drawn at its own place. */
    otherCars: List<CarPin> = emptyList(),
    chromeInsets: MapChromeInsets = MapChromeInsets(),
    /** The car this map is centred on, and its photo for its pin; null leaves its pin a plain glyph. */
    ownVin: String? = null,
    ownPhoto: String? = null,
    /** The car the top bar is showing: its pin is outlined, so the one the bar describes is easy to find. */
    selectedVin: String? = null,
    /** A pin was tapped: called with that car's VIN. */
    onPinTap: (String) -> Unit = {},
) {
    var started by remember { mutableStateOf(MapStartGate.open) }
    if (!started) {
        LaunchedEffect(Unit) {
            repeat(MapStartFrames) { withFrameNanos { } }
            MapStartGate.open = true
            started = true
        }
        Box(modifier)
        return
    }
    CarMapBody(
        location, modifier, state, deviceLocation, interactive, tileArea, panWeight, otherCars, chromeInsets,
        ownVin, ownPhoto, selectedVin, onPinTap,
    )
}

/**
 * A small slippy map centred on the car, assembled from key-free OpenStreetMap raw tiles. We
 * compute the tiles needed to fill the box with the car at the centre, draw each at its pixel
 * offset, then drop a pin. This avoids the flaky static-map render services that painted blank.
 */
@Composable
private fun CarMapBody(
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
    /**
     * The area, in px, to keep tiles loaded for when it is larger than the box itself: a map that is about to be (or is being)
     * resized, as it grows from the pebble to the full screen, asks for the largest size it will reach, so the grid does not
     * change on every frame of the resize. Zero means the box's own size.
     */
    tileArea: IntSize = IntSize.Zero,
    /** How much of [state]'s pan shows right now (0 to 1); read in the draw phase, so ramping it recomposes nothing. */
    panWeight: () -> Float = { 1f },
    otherCars: List<CarPin> = emptyList(),
    chromeInsets: MapChromeInsets = MapChromeInsets(),
    ownVin: String? = null,
    ownPhoto: String? = null,
    selectedVin: String? = null,
    onPinTap: (String) -> Unit = {},
) {
    com.bloo.bluelink.perf.TrackRecompositions("CarMapBody")
    val context = LocalContext.current
    val weight by rememberUpdatedState(panWeight)
    // Cold-start diagnostic; see HeroVisual's matching mark.
    StartupTrace.once("carmap-composing", StartupLabels.CAR_MAP_COMPOSING_FIRST)

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
    val boxSize = remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .background(mapBackground)
            .clipToBounds()
            .onSizeChanged { boxSize.value = it }
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
        // The size tiles are kept for: the box, or [tileArea] when that is larger. A derived value, so a box that is being resized
        // (the map opening out of its pebble) recomposes nothing while it stays inside the area, and the tiles are placed from the live
        // layout size in [TileLayer], not from this.
        val extent by remember(tileArea) {
            derivedStateOf { IntSize(maxOf(boxSize.value.width, tileArea.width), maxOf(boxSize.value.height, tileArea.height)) }
        }
        val wPx = extent.width.toFloat()
        val hPx = extent.height.toFloat()
        val zoom = state.zoom
        val span = MapTiles.span(zoom)
        val xTileF = MapTiles.tileX(location.longitude, zoom)
        val yTileF = MapTiles.tileY(location.latitude, zoom)

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
                val w = weight().coerceIn(0f, 1f)
                val fetchOriginX = xTileF * tilePx - fetchW / 2f - state.panX * w
                val fetchOriginY = yTileF * tilePx - fetchH / 2f - state.panY * w
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
        val prefetchAllowed = LocalPowerPolicy.current.prefetchMapTiles
        LaunchedEffect(zoom, xTileF, yTileF, wPx, hPx, prefetchAllowed) {
            // A static map can never zoom, so there is no next level to warm: skipping saves a burst of
            // dozens of image requests right as a car page settles.
            if (!interactive || !prefetchAllowed || wPx <= 0f || hPx <= 0f) return@LaunchedEffect
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
            Modifier.fillMaxSize()
                .graphicsLayer {
                    // Pan is one layer translation; children are placed at pan-independent
                    // positions. Divide by `scale` to bring tile-space pan into parent space (keeps
                    // 1:1 finger tracking).
                    val w = weight().coerceIn(0f, 1f)
                    translationX = state.panX * state.scale * w
                    translationY = state.panY * state.scale * w
                    scaleX = state.scale
                    scaleY = state.scale
                },
        ) {
        TileLayer(xTileF, yTileF) {
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
                            // No crossfade: Coil skips it only for memory hits, and a fade on a tile that was
                            // already shown (the map moving between the pebble and the full screen) would look
                            // like a refresh.
                            .crossfade(false)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        colorFilter = darkMapFilter,
                        // Sized and placed by TileLayer, from the layout size of the moment.
                        modifier = Modifier.tileSlot(tx, ty),
                    )
                }
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
        // The car's own pin: centred (the car is the map's pan origin, so the container's translation already puts it there). Tapping it
        // selects the car for the top bar.
        val ownTap: (() -> Unit)? = ownVin?.let { v -> { onPinTap(v) } }
        val ownSelected = ownVin != null && selectedVin == ownVin
        if (mergedOffsetPx != null) {
            val (dx, dy) = mergedOffsetPx
            // One pin at the midpoint, tinted with an even mix of the car and device colours.
            CarPinMarker(
                photo = ownPhoto,
                tint = lerp(pinColor, deviceLocationColor, 0.5f),
                selected = ownSelected,
                contentDesc = "Car and your location",
                onClick = ownTap,
                modifier = Modifier.align(Alignment.Center)
                    .offset { IntOffset((dx / 2f).roundToInt(), (dy / 2f).roundToInt()) },
            )
        } else {
            CarPinMarker(
                photo = ownPhoto,
                tint = pinColor,
                selected = ownSelected,
                contentDesc = "Car location",
                onClick = ownTap,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // Every other car on the map, at its own place. They ride with the map's pan, as the car's own pin does.
        for (other in otherCars) {
            val ox = ((MapTiles.tileX(other.location.longitude, zoom) - xTileF) * tilePx).toFloat()
            val oy = ((MapTiles.tileY(other.location.latitude, zoom) - yTileF) * tilePx).toFloat()
            CarPinMarker(
                photo = other.photo,
                tint = pinColor.copy(alpha = 0.7f),
                selected = selectedVin == other.vin,
                contentDesc = "${other.name}, another car",
                onClick = { onPinTap(other.vin) },
                modifier = Modifier.align(Alignment.Center).offset { IntOffset(ox.roundToInt(), oy.roundToInt()) },
            )
        }
        } // close the scaled layer
        // The phone's own position, drawn whenever a fix exists: a marker on the map when it is in
        // view, or one clamped to the box edge pointing toward it when it is farther than the map
        // shows. OUTSIDE the scaled layer, so it stays a constant size (a marker should not grow when
        // you pinch). Its screen offset is its tile delta from the car plus the pan, all times scale.
        val dev = deviceLocation
        if (dev != null) {
            DeviceMarker(
                dev = dev,
                zoom = zoom,
                xTileF = xTileF,
                yTileF = yTileF,
                tilePx = tilePx,
                panX = state.panX,
                panY = state.panY,
                panWeight = { weight().coerceIn(0f, 1f) },
                scale = state.scale,
                box = boxSize,
                marginPx = with(density) { 22.dp.toPx() },
                color = deviceLocationColor,
                onCenter = { state.showDevice(location.latitude, location.longitude, dev.latitude, dev.longitude) },
                chromeInsets = chromeInsets,
                carColor = pinColor,
                onCenterCar = { state.recenter() },
            )
        }
        // Expand/Open-in-Maps render as a MapFeatureRow in the caller, not over the map.
    }
}

/** The photo's size above a car's pin, and the pin glyph below it. The pin's tip is the car's place, so the stack is lifted by half its height. */
private val PinPhotoWidth = 44.dp
private val PinPhotoHeight = 30.dp
private val PinGlyphSize = 24.dp
private val PinStackHeight = PinPhotoHeight + 2.dp + PinGlyphSize

/**
 * A car's pin on the map: the car's photo as a rectangle above the pin, so it is clear which car each pin is. With no photo it shows the car
 * glyph in the same rectangle. [selected] outlines the rectangle in the pin's colour.
 */
@Composable
private fun CarPinMarker(
    photo: String?,
    tint: Color,
    selected: Boolean,
    contentDesc: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier
            .offset(y = -PinStackHeight / 2)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = contentDesc },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(width = PinPhotoWidth, height = PinPhotoHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(if (selected) 2.dp else 1.dp, if (selected) tint else Color.White.copy(alpha = 0.8f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = rememberPhotoModel(photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(AppIcons.DirectionsCar, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(2.dp))
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = tint, modifier = Modifier.size(PinGlyphSize))
    }
}

/** A tile's own coordinates, read by [TileLayer] to place it. */
private class TileSlot(val tx: Int, val ty: Int) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@TileSlot
}

private fun Modifier.tileSlot(tx: Int, ty: Int): Modifier = this.then(TileSlot(tx, ty))

/**
 * The map's tiles, each one tile square, placed from the car's tile position so the car sits dead centre of whatever size the layer
 * is at the moment. The size is read here, in layout, so a map being resized (opening out of its pebble) keeps every tile on its
 * place frame by frame instead of lagging a frame behind a size held in state.
 */
@Composable
private fun TileLayer(xTileF: Double, yTileF: Double, content: @Composable () -> Unit) {
    val tile = MapTiles.TILE_PX
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val fixed = Constraints.fixed(tile, tile)
        val originX = xTileF * tile - w / 2.0
        val originY = yTileF * tile - h / 2.0
        val placed = measurables.map { it.measure(fixed) to (it.parentData as TileSlot) }
        layout(w, h) {
            for ((p, slot) in placed) p.place((slot.tx * tile - originX).roundToInt(), (slot.ty * tile - originY).roundToInt())
        }
    }
}
