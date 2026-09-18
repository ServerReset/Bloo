package com.bloo.bluelink.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * The Web Mercator tile projection -- the "slippy map" scheme -- and the OpenStreetMap
 * request details, in one place.
 *
 * Kept in `:shared` from when multiple surfaces (the phone's interactive map, a since-
 * removed home-screen widget, and a since-removed Wear OS companion app) each drew a map
 * of the car and needed the same projection, tile URL, and User-Agent string. OSM's tile
 * usage policy requires a User-Agent that identifies the application, and their servers
 * return a "blocked" placeholder tile to clients that don't provide one -- so this exists
 * to make sure whichever surface asks sends one that actually satisfies that policy.
 */
object MapTiles {

    /** Edge of one OSM tile in pixels. Fixed by the tile server, not a preference. */
    const val TILE_PX = 256

    /** A User-Agent that satisfies OSM's usage policy, for [platform] (e.g. "Android"). */
    fun userAgent(platform: String): String = "Bloo-$platform/1.0 (https://claude.ai/code)"

    /**
     * The OSM tile URL for a z/x/y triple.
     *
     * This used to take a `dark` flag that switched to CARTO's "Dark Matter" basemap for a
     * dark-mode map -- reverted. CARTO's basemaps.cartocdn.com now serves a
     * "API KEY REQUIRED" watermark over that style without one, which reads as the app's own
     * bug (reported directly from a screenshot) rather than a third party's policy change, and
     * getting and shipping a key is exactly the kind of external dependency [userAgent]'s own
     * doc above already argues against taking on for a single map style. Dark mode for the map
     * is a CLIENT-SIDE colour filter over these same OSM tiles instead (see CarMap's
     * `darkMapColorFilter`) -- one tile source, no key, for every surface that draws a map.
     */
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
