package com.bloo.bluelink.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import com.bloo.bluelink.data.MapTiles

/**
 * Zoom bounds for [CarMap]'s zoom, pinch or button-driven -- 3 is "half the continent", 19 is past
 * what OSM actually serves tiles for.
 */
internal const val CarMapMinZoom = 3

internal const val CarMapMaxZoom = 19

/** Where every [CarMapState] starts: street level, car dead-centre. */
internal const val CarMapDefaultZoom = 15

/**
 * The inclusive tile-index range [CarMap] currently needs fetched -- see its own
 * `range`/`derivedStateOf` doc for why this is its own equatable value rather than four loose Ints
 * computed inline.
 */
data class TileRange(val firstX: Int, val firstY: Int, val lastX: Int, val lastY: Int)

/** Null (the default) when no host has set one up. See [CentralMap]'s own doc. */
internal val LocalCentralMap = staticCompositionLocalOf<CentralMap?> { null }

/**
 * Where a map point sits on screen from the map's centre. [placeX]/[placeY] are its place in tile pixels from the car (the map's origin),
 * [panWeight] is how much of the pan shows (0 to 1), and [scale] is the pinch. Every marker on the map (the car, the phone, other cars, their
 * clusters and their pointers) is placed by this one rule.
 */
internal fun mapScreenOffset(placeX: Float, placeY: Float, panX: Float, panY: Float, panWeight: Float, scale: Float): Offset =
    Offset((placeX + panX * panWeight) * scale, (placeY + panY * panWeight) * scale)

/** A point's place in tile pixels from the car at [xTileF]/[yTileF], at [zoom]. */
internal fun tilePlace(lat: Double, lon: Double, zoom: Int, xTileF: Double, yTileF: Double, tilePx: Float): Offset =
    Offset(((MapTiles.tileX(lon, zoom) - xTileF) * tilePx).toFloat(), ((MapTiles.tileY(lat, zoom) - yTileF) * tilePx).toFloat())


/**
 * The map's chrome in window coordinates, measured by the layer: where the map is, and where its top bar and bottom buttons sit. The map's
 * markers read it, so their edge window follows the real bars on any screen rather than fixed insets. Closed, there are no bars, so the
 * insets are zero.
 */
internal class MapChromeInsets {
    /** True while the map's top bar and bottom buttons are shown (open, and their own track has come in). */
    var chromeOn by androidx.compose.runtime.mutableStateOf(false)
    var mapTop by androidx.compose.runtime.mutableFloatStateOf(0f)
    var mapBottom by androidx.compose.runtime.mutableFloatStateOf(0f)
    var barBottom by androidx.compose.runtime.mutableFloatStateOf(0f)
    var rowTop by androidx.compose.runtime.mutableFloatStateOf(0f)

    /** How far the top bar comes down into the map, in pixels from the map's top. */
    fun topPx(): Float = if (chromeOn) (barBottom - mapTop).coerceAtLeast(0f) else 0f

    /** How far the bottom buttons come up into the map, in pixels from the map's bottom. */
    fun bottomPx(): Float = if (chromeOn) (mapBottom - rowTop).coerceAtLeast(0f) else 0f
}

/** The map's [MapChromeInsets], provided by [CentralMap] around its map. Null where no map is drawn. */
internal val LocalMapChrome = staticCompositionLocalOf<MapChromeInsets?> { null }
