package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles

/**
 * Live view state for one [CarMap]: zoom level, pixel pan offset from the car-centred origin, and
 * pinch scale.
 */
internal class CarMapState {
    var zoom by mutableIntStateOf(CarMapDefaultZoom)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set

    // Pinch scale, 1x at [zoom]'s tile resolution. CarMap applies it as a graphicsLayer scale every
    // frame for continuous zoom; only crossing a whole octave (2x/0.5x) swaps tiles (see pinch()).
    var scale by mutableFloatStateOf(1f)
        private set

    /**
     * True once the user has actually panned or pinched this map -- an automatic "fit both the car
     * and the device in view" (see [fitBothLocations]) should only ever run BEFORE that, never
     * yanking the view out from under a hand that's already deliberately looked somewhere else.
     * [recenter] clears this, since that's an explicit "start over".
     */
    private var userAdjusted = false

    /**
     * Puts the map at exactly this pan (in the same pixels as [pan]): the car that far from the middle of the box. The full-screen map
     * uses it to open with the car where it was in the pebble, and to hand the pan back to nothing when it closes.
     */
    fun panTo(x: Float, y: Float) {
        userAdjusted = true
        panX = x
        panY = y
    }

    /**
     * A continuous drag in screen pixels: the map slides with the finger, so the offset accumulates
     * in the drag direction.
     */
    fun pan(dx: Float, dy: Float) {
        // A pinch frame reports a pure pan with (0,0); skip the writes entirely rather than
        // invalidating panX/panY (and every layout lambda reading them) for no movement.
        if (dx == 0f && dy == 0f) return
        userAdjusted = true
        panX += dx
        panY += dy
    }

    /**
     * The zoom level the map has just left, whose tiles stay on screen underneath the new level's until those arrive, so a pinch across a
     * zoom step never shows a blank map. -1 when there is nothing to keep.
     */
    var ghostZoom by mutableIntStateOf(-1)

    /**
     * One frame of a pinch gesture's multiplicative ratio, folded straight into [scale] -- see that
     * property's own doc.
     */
    fun pinch(ratio: Float) {
        // A drag reports ratio 1f: nothing to fold in.
        if (ratio == 1f) return
        userAdjusted = true
        scale *= ratio
        while (scale >= 2f && zoom < CarMapMaxZoom) {
            ghostZoom = zoom
            zoom++; panX *= 2f; panY *= 2f; scale /= 2f
        }
        while (scale <= 0.5f && zoom > CarMapMinZoom) {
            ghostZoom = zoom
            zoom--; panX /= 2f; panY /= 2f; scale *= 2f
        }
        // At the zoom ceiling/floor, clamp the continuous scale: CarMap's tile grid is sized for
        // scale 1 (and over-fetches by 1/scale within this clamp), so a smaller scale would leave
        // blank margins.
        scale = scale.coerceIn(0.5f, 2f)
    }

    /**
     * Back to the car, dead centre, default zoom -- the full-screen map's "Recentre" feature.
     * Clears [userAdjusted]: an explicit "start over" that also re-allows a fresh
     * [fitBothLocations] to run, the same as a map that had never been touched at all.
     */
    fun recenter() {
        userAdjusted = false
        zoom = CarMapDefaultZoom
        panX = 0f
        panY = 0f
        scale = 1f
    }

    /**
     * Zooms out (never in past [CarMapDefaultZoom]) just enough that both the car (which stays
     * dead-centre, per [pan]'s own doc) and the device's own location fit on screen with a margin
     * -- called automatically, once, the first time both fixes are available and the map is still
     * at rest (see [userAdjusted]).
     */
    fun showDevice(carLat: Double, carLon: Double, deviceLat: Double, deviceLon: Double) {
        userAdjusted = true
        scale = 1f
        panX = (-(MapTiles.tileX(deviceLon, zoom) - MapTiles.tileX(carLon, zoom)) * MapTiles.TILE_PX).toFloat()
        panY = (-(MapTiles.tileY(deviceLat, zoom) - MapTiles.tileY(carLat, zoom)) * MapTiles.TILE_PX).toFloat()
    }

    /**
     * Zooms out (never in past [CarMapDefaultZoom]) so both the car (dead-centre) and the device
     * fit with a margin. Runs once, when both fixes are available and the map is still at rest (see
     * [userAdjusted]).
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
            // The car sits dead-centre, so the device only has to fit within HALF the viewport on
            // whichever side it's offset toward -- hence *2, not the raw distance. 0.8x leaves some
            // breathing room rather than placing the device right at the very edge.
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

/**
 * Cross-composable state for expanding a Location pebble's own compact map "in place" -- when a
 * host (currently only [GarageScreen]) provides one via [LocalExpandedMap], the SAME [CarMapState]
 * (so the same pan, zoom and already- loaded tiles) and the compact map's own last on-screen rect
 * carry across into a full-screen overlay drawn elsewhere in that host's own tree, instead of the
 * expanded view starting over at a fresh state with a fresh tile fetch.
 */
internal class ExpandedMapState {
    /**
     * The map for this car, drawn wherever it is called: the pebble's slot while the map is collapsed, the full-screen layer while it is
     * open. Each car's pan and zoom live in its [CarMapState], so the map that opens shows the same view it was left on. Nothing moves
     * between places, so there is no second copy to fall out of step with the first.
     */
    fun mapFor(vin: String): @Composable (Modifier, MapInputs) -> Unit {
        val state = mapStateFor(vin)
        val content: @Composable (Modifier, MapInputs) -> Unit = { modifier, inputs ->
            CarMap(
                inputs.location, modifier, state, inputs.deviceLocation, inputs.interactive, inputs.tileArea, inputs.panWeight,
                // Only the full-screen map shows the other cars: the pebble's own map is this car alone.
                otherCars = if (this@ExpandedMapState.vin == vin) this@ExpandedMapState.otherCars else emptyList(),
                chromeInsets = this@ExpandedMapState.chromeInsets,
                ownVin = vin,
                ownPhoto = this@ExpandedMapState.photoUrls[vin],
                selectedVin = this@ExpandedMapState.selectedVin,
                onPinTap = { tapped -> this@ExpandedMapState.tapPin(tapped) },
                onSelectedOffScreen = { this@ExpandedMapState.selectedVin = null },
            )
        }
        return content
    }

    /** The other cars, drawn as photo pins on the expanded map: every car but the one it is open on. */
    var otherCars by mutableStateOf<List<CarPin>>(emptyList())

    /** Each car's photo, for its own pin on the pebble's map and on the expanded one. */
    var photoUrls by mutableStateOf<Map<String, String>>(emptyMap())

    /**
     * The car the top bar is showing, by a tap on its pin. Null is no selection: the bar is just its handle. Tapping the selected pin
     * again clears it.
     */
    var selectedVin by mutableStateOf<String?>(null)

    /** Selects a car's pin. Tapping the selected pin again keeps it selected: the bar stays on its line; only leaving the screen clears it. */
    /**
     * A single tap on a car switches to it: it becomes the selection, and the map centres on it. The car the map is on is centred from
     * where it was given (see [expandedLocation]).
     */
    fun tapPin(vin: String) {
        selectedVin = vin
        val own = this.vin ?: return
        if (vin == own) {
            mapStateFor(own).recenter()
            return
        }
        val origin = expandedLocation ?: return
        val target = otherCars.firstOrNull { it.vin == vin }?.location ?: return
        mapStateFor(own).showDevice(origin.latitude, origin.longitude, target.latitude, target.longitude)
    }

    /** Where the car the full-screen map is on is, so a tap on another car can centre the map from it. */
    var expandedLocation: GeoLocation? = null

    /** Opens the full-screen map on [vin], with that car selected, so its bar is already on its line. */
    fun open(vin: String) {
        selectedVin = vin
        this.vin = vin
    }

    /**
     * How far the bar is being dragged toward closing, 0 (not at all) to 1: the garage behind the map blur fades with it, so the map is
     * seen clearly as it starts to close, and the blur comes back if the drag is let go before it closes.
     */
    var dismissFade by mutableFloatStateOf(0f)

    /** The space the expanded map's top bar and bottom buttons take, so the off-screen pointers keep clear of them. */
    val chromeInsets = MapChromeInsets()

    /** VIN of the car whose map is currently expanded, or null. */
    var vin by mutableStateOf<String?>(null)
    private val perVinMapState = mutableMapOf<String, CarMapState>()
    private val perVinOrigin = mutableMapOf<String, MutableState<Rect?>>()
    private val perVinLocation = mutableMapOf<String, MutableState<GeoLocation?>>()

    /**
     * The one [CarMapState] a car's compact map and expanded overlay share, created per VIN on
     * first use.
     */
    fun mapStateFor(vin: String): CarMapState = perVinMapState.getOrPut(vin) { CarMapState() }

    /**
     * The compact map's own last-measured on-screen rect for this VIN, kept live (via the compact
     * map's own `onGloballyPositioned`) whether or not it's the currently-expanded one, so the
     * moment it IS expanded there's already a real, current origin to grow from -- not a stale one
     * from whenever this was last measured, or none at all.
     */
    fun originBoundsFor(vin: String): MutableState<Rect?> = perVinOrigin.getOrPut(vin) { mutableStateOf(null) }

    /** The location for this VIN's map, kept live for the screen-level map layer. */
    fun locationFor(vin: String): MutableState<GeoLocation?> = perVinLocation.getOrPut(vin) { mutableStateOf(null) }
}

/** What the shared map is drawn from at the place it is hosted: where the car is, where the phone is, whether it takes touches, and the area to keep tiles for. */
/** One car drawn as a pin on the expanded map: where it is, and what its pin shows (its photo above the pin, its name for a11y). */
internal data class CarPin(val vin: String, val location: GeoLocation, val name: String, val photo: String?)

internal data class MapInputs(
    val location: GeoLocation,
    val deviceLocation: GeoLocation?,
    val interactive: Boolean,
    val tileArea: IntSize = IntSize.Zero,
    /** How much of the map's pan shows right now, 0 to 1: the full-screen map ramps it with its opening, so the car glides between the pebble's centre and where it was panned to. */
    val panWeight: () -> Float = { 1f },
)
