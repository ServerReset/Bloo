package com.bloo.bluelink.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * The Web Mercator tile projection -- the "slippy map" scheme -- and the OpenStreetMap
 * request details, in one place.
 *
 * Three surfaces draw a map of the car -- the phone's interactive map, the widget's
 * thumbnail, and the watch's -- and each had its own copy of the projection, its own
 * tile URL, and, most consequentially, its own User-Agent string:
 *
 *     phone   "Bloo Bluelink companion app"
 *     widget  "Bloo-Android/1.0 (https://claude.ai/code)"
 *     watch   "Bloo-WearOS/0.1 (https://claude.ai/code)"
 *
 * That last one is not cosmetic. OSM's tile usage policy requires a User-Agent that
 * identifies the application, and their servers return a "blocked" placeholder tile
 * to clients that don't provide one -- which is exactly the 403 the watch's WearImage
 * comment records fixing. The phone's string carried no version and no contact URL,
 * i.e. it was the one still shaped like the thing that gets blocked.
 *
 * Only the maths and the request are shared. How each surface DRAWS the result is
 * legitimately its own business: the phone lays out a mosaic of Coil images sized to
 * its box, the widget bakes a mosaic into a Bitmap for RemoteViews, the watch shows a
 * single tile in a small circle. Zoom stays per-surface too -- the widget deliberately
 * uses 13 where the phone uses 15, and its own comment explains why (13 reads as
 * "which part of town", 15 as "which driveway").
 */
object MapTiles {

    /** Edge of one OSM tile in pixels. Fixed by the tile server, not a preference. */
    const val TILE_PX = 256

    /**
     * A User-Agent that satisfies OSM's usage policy, for [platform] ("Android",
     * "WearOS").
     *
     * The shape is the one the widget and the watch already proved works against the
     * real servers, so those two keep sending byte-identical strings (bar the watch's
     * version number, previously 0.1). Only the phone's changes, because the phone's
     * was the broken one.
     */
    fun userAgent(platform: String): String = "Bloo-$platform/1.0 (https://claude.ai/code)"

    /** The OSM tile URL for a z/x/y triple. */
    fun tileUrl(zoom: Int, x: Int, y: Int): String =
        "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    /** Number of tiles per axis at [zoom], i.e. 2^zoom. */
    fun span(zoom: Int): Int = 1 shl zoom

    /**
     * Fractional tile X for a longitude. Linear in longitude: -180 maps to 0 and
     * +180 to [span], exactly.
     */
    fun tileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * span(zoom)

    /**
     * Fractional tile Y for a latitude, via the standard Mercator formula, which keeps the
     * y-axis visually undistorted. North is SMALLER y: the equator lands at
     * exactly half of [span].
     *
     * Written as `lat / 180.0 * PI` rather than reaching for the java.lang helper --
     * identical arithmetic, and it keeps this module free of that dependency.
     */
    fun tileY(lat: Double, zoom: Int): Double {
        val latRad = lat / 180.0 * PI
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * span(zoom)
    }

    /**
     * Wrap a tile X index into `0 until span` so a window straddling the antimeridian
     * still asks for real tiles rather than negative or out-of-range ones.
     *
     * The `((x % span) + span) % span` form matters for negative x: Kotlin's `%` keeps
     * the sign of the dividend, so a bare `x % span` yields a negative index and a
     * 404. Y is deliberately NOT wrapped -- there is no tile above the north edge or
     * below the south one, so callers skip those rows instead.
     */
    fun wrapX(x: Int, zoom: Int): Int {
        val n = span(zoom)
        return ((x % n) + n) % n
    }
}
