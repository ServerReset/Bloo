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
        // A pinch frame reports a pure pan with (0,0); skip the writes entirely rather than
        // invalidating panX/panY (and every layout lambda reading them) for no movement.
        if (dx == 0f && dy == 0f) return
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
        // A drag reports ratio 1f; there is nothing to fold in, and running the while-loops +
        // coerce + scale write on every pan frame was pure overhead (and a needless scale
        // invalidation for a value that did not change).
        if (ratio == 1f) return
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
    /** Pan so the DEVICE is at the centre of the view, at the current zoom. The car is the map's origin
     *  (pan 0 = car centred), so the offset is the device's tile distance from it, negated. */
    fun showDevice(carLat: Double, carLon: Double, deviceLat: Double, deviceLon: Double) {
        userAdjusted = true
        scale = 1f
        panX = (-(MapTiles.tileX(deviceLon, zoom) - MapTiles.tileX(carLon, zoom)) * MapTiles.TILE_PX).toFloat()
        panY = (-(MapTiles.tileY(deviceLat, zoom) - MapTiles.tileY(carLat, zoom)) * MapTiles.TILE_PX).toFloat()
    }

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
