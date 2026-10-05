package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlinx.coroutines.flow.first
import kotlin.math.floor

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
     * True once the user has panned or pinched; the automatic [fitBothLocations] only runs before
     * that. [recenter] clears it.
     */
    private var userAdjusted = false

    /**
     * A continuous drag in screen pixels: the map slides with the finger, so the offset accumulates
     * in the drag direction.
     */
    fun pan(dx: Float, dy: Float) {
        // A pinch frame reports a pure pan of (0,0); skip the writes so readers aren't invalidated.
        if (dx == 0f && dy == 0f) return
        userAdjusted = true
        panX += dx
        panY += dy
    }

    /** One pinch frame's ratio, folded into [scale]. */
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
     * Back to the car, centred, default zoom (the Recentre action). Clears [userAdjusted] so
     * [fitBothLocations] can run again.
     */
    fun recenter() {
        userAdjusted = false
        zoom = CarMapDefaultZoom
        panX = 0f
        panY = 0f
        scale = 1f
    }

    /**
     * Pans so the device is centred at the current zoom. The car is the origin (pan 0), so the
     * offset is the device's tile distance, negated.
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
            // The car sits dead-centre, so the device need only fit in half the viewport per side
            // (*2); 0.8x leaves margin.
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
 * Cross-composable state for expanding a Location pebble's compact map in place: when a host
 * ([GarageScreen]) provides one via [LocalExpandedMap], the same [CarMapState] and the compact
 * map's last on-screen rect carry into a full-screen overlay.
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
     * The compact map's last-measured on-screen rect, kept live even when not expanded so there is
     * always a current origin to grow from.
     */
    fun originBoundsFor(vin: String): MutableState<Rect?> = perVinOrigin.getOrPut(vin) { mutableStateOf(null) }

    /** The location for this VIN's map, kept live for the screen-level map layer. */
    fun locationFor(vin: String): MutableState<GeoLocation?> = perVinLocation.getOrPut(vin) { mutableStateOf(null) }
}
