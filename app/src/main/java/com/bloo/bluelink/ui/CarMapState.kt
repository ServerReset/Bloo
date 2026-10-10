package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlin.math.floor
import kotlinx.coroutines.flow.first

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
     * One frame of a pinch gesture's multiplicative ratio, folded straight into [scale] -- see that
     * property's own doc.
     */
    fun pinch(ratio: Float) {
        // A drag reports ratio 1f: nothing to fold in.
        if (ratio == 1f) return
        userAdjusted = true
        scale *= ratio
        while (scale >= 2f && zoom < CarMapMaxZoom) {
            zoom++; panX *= 2f; panY *= 2f; scale /= 2f
        }
        while (scale <= 0.5f && zoom > CarMapMinZoom) {
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
