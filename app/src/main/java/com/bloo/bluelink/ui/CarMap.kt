@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.bloo.bluelink.data.ChargerStation
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.roundToInt

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
