package com.bloo.bluelink.ui

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

/** Null (the default) when no host has set one up. See [ExpandedMapState]'s own doc. */
internal val LocalExpandedMap = staticCompositionLocalOf<ExpandedMapState?> { null }

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

