package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.bloo.bluelink.data.GeoLocation

/*
 * The app's one map. Every screen references this controller to open, select and dismiss the map; the map itself is drawn once, by
 * [CentralMapHost] at the root of the app, above the status bar's blur.
 */

/**
 * The garage's one map. A single [mapState] and a single map instance serve every car: the map is anchored on the car it was opened for
 * ([vin]), and every other car is an icon at its own place relative to that anchor. It is drawn only by [CentralMapHost]: closed, at the
 * Location card of the car it was last opened for ([homeVin]); open, grown to the full screen. Every other part of the app calls [open] and
 * [dismiss]; none of them draws it.
 */
internal class CentralMap {
    /** The map's pan and zoom: the one state every view of the map uses. */
    val mapState = CarMapState()

    /** The map's measured chrome (its bars' places), written by the host and read by the markers (see [MapChromeInsets]). */
    val chromeInsets = MapChromeInsets()

    /** Every car that has a location, each drawn as its own icon. */
    var cars by mutableStateOf<List<CarPin>>(emptyList())

    /** The car the map is anchored on: the one it was opened for, or null while it is closed. */
    var vin by mutableStateOf<String?>(null)

    /** The car whose Location card shows the map while it is closed. */
    var homeVin by mutableStateOf<String?>(null)

    /** The car the top bar is showing, by a tap on its icon. Null is no selection: the bar is just its handle. */
    var selectedVin by mutableStateOf<String?>(null)

    /** The anchor's location, so the full-screen map knows where it is centred. */
    var expandedLocation: GeoLocation? = null

    /** How far the bar is being dragged toward closing, 0 (not at all) to 1: the garage behind the map fades with it. */
    var dismissFade by mutableFloatStateOf(0f)

    /** Each Location card's own on-screen rectangle, so the full-screen map grows from the card it was opened from. */
    private val origins = mutableMapOf<String, MutableState<Rect?>>()

    fun originBoundsFor(vin: String): MutableState<Rect?> = origins.getOrPut(vin) { mutableStateOf(null) }

    /** Opens the map on [vin], with that car selected. A map already open is not switched under the person. */
    fun open(vin: String) {
        if (this.vin != null) return
        com.bloo.bluelink.data.AppLog.log("Map: expand tapped for $vin")
        this.vin = vin
        homeVin = vin
        selectedVin = vin
    }

    /** Closes the map: the selection and the anchor clear, and the map goes back to the card it rests in. */
    fun dismiss() {
        selectedVin = null
        vin = null
    }

    /** A tap on a car's icon: the bar shows that car, and the map pans to centre it from the anchor. */
    fun tapPin(tapped: String) {
        selectedVin = tapped
        val anchor = vin ?: return
        if (tapped == anchor) {
            mapState.recenter()
            return
        }
        val from = cars.firstOrNull { it.vin == anchor }?.location ?: return
        val to = cars.firstOrNull { it.vin == tapped }?.location ?: return
        mapState.showDevice(from.latitude, from.longitude, to.latitude, to.longitude)
    }

    /** The map, as it is drawn wherever it is hosted: the one instance, anchored on the car it was opened for. */
    @Composable
    fun Content(modifier: Modifier, inputs: MapInputs) {
        CompositionLocalProvider(LocalMapChrome provides chromeInsets) {
            CarMap(
                location = inputs.location,
                modifier = modifier,
                state = mapState,
                deviceLocation = inputs.deviceLocation,
                interactive = inputs.interactive,
                tileArea = inputs.tileArea,
                panWeight = inputs.panWeight,
                cars = cars,
                anchorVin = vin,
                selectedVin = selectedVin,
                onCarTap = { tapPin(it) },
            )
        }
    }
}
