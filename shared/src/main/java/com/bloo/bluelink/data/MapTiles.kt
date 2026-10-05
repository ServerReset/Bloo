package com.bloo.bluelink.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * The Web Mercator tile projection -- the "slippy map" scheme -- and the OpenStreetMap request
 * details, in one place.
 */
object MapTiles {

    /** Edge of one OSM tile in pixels. */
    const val TILE_PX = 256

    /** A User-Agent that satisfies OSM's usage policy, for [platform] (e.g. "Android"). */
    fun userAgent(platform: String): String = "Bloo-$platform/1.0 (https://claude.ai/code)"

    /**
     * The OSM tile URL for a z/x/y triple. Dark mode for the map is a CLIENT-SIDE colour filter
     * over these same OSM tiles instead (see CarMap's `darkMapColorFilter`) -- one tile source, no
     * key, for every surface that draws a map.
     */
    fun tileUrl(zoom: Int, x: Int, y: Int): String =
        "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    /** Number of tiles per axis at [zoom], i.e. 2^zoom. */
    fun span(zoom: Int): Int = 1 shl zoom

    /**
     * Fractional tile X for a longitude. Linear in longitude: -180 maps to 0 and +180 to [span],
     * exactly.
     */
    fun tileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * span(zoom)

    /**
     * Fractional tile Y for a latitude, via the standard Mercator formula, which keeps the y-axis
     * visually undistorted. North is SMALLER y: the equator lands at exactly half of [span].
     */
    fun tileY(lat: Double, zoom: Int): Double {
        val latRad = lat / 180.0 * PI
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * span(zoom)
    }

    /**
     * Wrap a tile X index into `0 until span` so a window straddling the antimeridian still asks
     * for real tiles rather than negative or out-of-range ones.
     */
    fun wrapX(x: Int, zoom: Int): Int {
        val n = span(zoom)
        return ((x % n) + n) % n
    }
}
