package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles

/*
 * The app's one map. Every screen references this controller to open, select and dismiss the map; the map itself is drawn once, by
 * [CentralMapHost] at the root of the app, above the status bar's blur.
 */

/**
 * One Location card's place for the map: where its map slot is on screen, and how to scroll it into view. A car can be on screen in more
 * than one card at a time (the garage pager can compose the same car on two pages while it moves), so each card holds its own slot and the
 * map picks the one that is actually on screen (see [CentralMap.cardBounds]).
 */
internal class MapCardSlot(val vin: String) {
    /** The card's map slot, in screen coordinates. Written by the card as it is laid out. */
    var bounds by mutableStateOf<Rect?>(null)

    /** Scrolls the card's page so the map slot is in view. Set by the card. */
    var reveal: (suspend () -> Unit)? = null
}

/**
 * The garage's one map. A single [mapState] and a single map instance serve every car: the map is anchored on the car it was opened for
 * ([vin]), and every other car is an icon at its own place relative to that anchor. It is drawn only by [CentralMapHost]: closed, at the
 * Location card of the car the garage is on ([homeVin]); open, grown to the full screen.
 *
 * There is ONE car the app is on: the garage's current car ([currentVin]). The map's bar always shows that car, a swipe on the bar or a tap
 * on a pin changes it (through [select], which moves the garage too), and the map closes onto that car's card. Nothing else keeps its own
 * idea of "the selected car", so the bar, the garage and the close can never disagree.
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

    /** The car whose Location card shows the map while it is closed: the garage's current car. */
    var homeVin by mutableStateOf<String?>(null)

    /** The car the app is on: the garage's current car. Mirrored from the garage by the host; changed by [open] and [select] only. */
    var currentVin by mutableStateOf<String?>(null)

    /** Whether the open map's bar shows the current car's line. False shows only its handle (the car was deselected on the map). */
    var barShown by mutableStateOf(false)

    /** The car the open map's bar shows, or null for just the handle. Always the garage's current car, never a separate pick. */
    val selectedVin: String? get() = if (vin != null && barShown) currentVin else null

    /** How far the bar is being dragged toward closing, 0 (not at all) to 1: the garage behind the map fades with it. */
    var dismissFade by mutableFloatStateOf(0f)

    /** While the map is collapsing, the car whose card it collapses onto. */
    var closingTo by mutableStateOf<String?>(null)

    /** The window's size, kept by the host, so a card can be judged on or off screen. */
    var screen: Size = Size.Zero

    /** Set by the host: moves the app's garage to a car (see [select]). */
    var onCarChosen: ((String) -> Unit)? = null

    private val slots = mutableStateListOf<MapCardSlot>()

    /** Adds a Location card's slot. Each card adds its own and removes it when it leaves composition. */
    fun addCard(slot: MapCardSlot) { slots += slot }

    fun removeCard(slot: MapCardSlot) { slots -= slot }

    /**
     * Where [vin]'s Location card is on screen: of all the cards showing that car, the one most on screen (the others are on pages off to
     * the side). Reads snapshot state, so a reader in layout or draw follows the card as it moves.
     */
    fun cardBounds(vin: String): Rect? = bestSlot(vin)?.bounds

    private fun bestSlot(vin: String): MapCardSlot? {
        var best: MapCardSlot? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (s in slots) {
            if (s.vin != vin) continue
            val r = s.bounds ?: continue
            val area = visibleArea(r)
            // On screen beats off screen; among cards off screen, the nearest to the screen's middle.
            val score = if (area > 0f) area else -kotlin.math.hypot(r.center.x - screen.width / 2f, r.center.y - screen.height / 2f)
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }
        return best
    }

    private fun visibleArea(r: Rect): Float {
        if (screen == Size.Zero) return r.width * r.height
        val w = (minOf(r.right, screen.width) - maxOf(r.left, 0f)).coerceAtLeast(0f)
        val h = (minOf(r.bottom, screen.height) - maxOf(r.top, 0f)).coerceAtLeast(0f)
        return w * h
    }

    /** The closed map rests in [car]'s card. The host calls this as the garage moves, so the map comes along with the car page. */
    fun rest(car: String) {
        homeVin = car
    }

    /**
     * Opens the map on [vin], showing that car in the bar. A map already open is not switched under the person. The garage is not moved:
     * the map grows out of this car's card, which must stay where it is while it does (on a wide screen it can be any column, not only
     * the first). The garage moves once a swipe or a pin tap chooses another car.
     */
    fun open(vin: String) {
        if (this.vin != null) return
        com.bloo.bluelink.data.AppLog.log("Map: expand tapped for $vin")
        this.vin = vin
        barShown = true
        currentVin = vin
        homeVin = vin
    }

    /** Closes the map: the bar and the anchor clear, and the map goes back to the card it rests in. */
    fun dismiss() {
        barShown = false
        vin = null
    }

    /**
     * Makes [car] the app's car: the garage moves to it, the bar shows it, and an open map glides to centre it. The one place the car
     * changes, for a bar swipe and a pin tap alike.
     */
    fun select(car: String, glide: Boolean = true) {
        currentVin = car
        if (vin == null) homeVin = car
        onCarChosen?.invoke(car)
        if (glide) glideTo(car)
    }

    /** Glides the open map to centre [car]. */
    fun glideTo(car: String) {
        val anchor = vin ?: return
        val from = cars.firstOrNull { it.vin == anchor }?.location ?: return
        val to = cars.firstOrNull { it.vin == car }?.location ?: return
        val (tx, ty) = panBetween(from, to, mapState.zoom)
        glide.glideTo(tx, ty)
    }

    /** Where [car] sits relative to the anchor, as a map pan: (0, 0) for the anchor itself. */
    fun panTo(car: String): Pair<Float, Float> {
        val anchor = vin ?: return 0f to 0f
        val from = cars.firstOrNull { it.vin == anchor }?.location ?: return 0f to 0f
        val to = cars.firstOrNull { it.vin == car }?.location ?: return 0f to 0f
        return panBetween(from, to, mapState.zoom)
    }

    /** A tap on a car's icon. Open, the bar shows that car and the map glides to it; closed, the garage moves to that car's page. */
    fun tapPin(tapped: String) {
        if (vin != null) barShown = true
        select(tapped)
    }

    private val glide = MapGlide(mapState)

    /**
     * A car card's own copy of the map: it keeps its own pan and zoom, so it stays put while the big map opens and closes. It is centred on
     * the car it is in, with every car on it.
     */
    @Composable
    fun CardContent(modifier: Modifier, inputs: MapInputs) {
        val own = rememberCarMapState()
        CompositionLocalProvider(LocalMapChrome provides chromeInsets) {
            CarMap(
                location = inputs.location,
                modifier = modifier,
                state = own,
                deviceLocation = inputs.deviceLocation,
                interactive = false,
                tileArea = inputs.tileArea,
                panWeight = { 1f },
                cars = cars,
                anchorVin = vin,
                selectedVin = selectedVin,
                onCarTap = { tapPin(it) },
            )
        }
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

/** How long the map takes to glide to a car chosen on it. */
private const val GlideMs = 420

/**
 * The pan that centres the map on [to] when it is anchored on [from], at zoom [zoom]. The map glides this far, rather than jumping.
 */
internal fun panBetween(from: GeoLocation, to: GeoLocation, zoom: Int): Pair<Float, Float> {
    val tx = (-(MapTiles.tileX(to.longitude, zoom) - MapTiles.tileX(from.longitude, zoom)) * MapTiles.TILE_PX).toFloat()
    val ty = (-(MapTiles.tileY(to.latitude, zoom) - MapTiles.tileY(from.latitude, zoom)) * MapTiles.TILE_PX).toFloat()
    return tx to ty
}

/**
 * Eases the map's pan to a point over [GlideMs]. A plain animator on the main thread: a coroutine on Dispatchers.Main has no frame clock,
 * and crashed the app on the first tap.
 */
internal class MapGlide(private val state: CarMapState) {
    private var animator: android.animation.ValueAnimator? = null

    fun glideTo(tx: Float, ty: Float) {
        animator?.cancel()
        val sx = state.panX
        val sy = state.panY
        animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GlideMs.toLong()
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                state.panTo(sx + (tx - sx) * t, sy + (ty - sy) * t)
            }
            start()
        }
    }
}
