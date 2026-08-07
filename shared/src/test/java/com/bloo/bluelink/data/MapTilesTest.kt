package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [MapTiles], the Web Mercator projection the phone's map, the
 * widget's thumbnail and the watch's each used to carry their own copy of.
 *
 * The assertions are exact mathematical identities rather than hand-computed tile
 * numbers for real cities: the projection's defining properties are things like "the
 * antimeridian is x = 0" and "the equator is halfway down", which can be checked
 * without trusting an arithmetic I did in my head.
 */
class MapTilesTest {

    private val zooms = listOf(0, 1, 2, 10, 13, 15, 19)

    @Test
    fun spanIsTwoToTheZoom() {
        assertEquals(1, MapTiles.span(0))
        assertEquals(2, MapTiles.span(1))
        assertEquals(8192, MapTiles.span(13))
        assertEquals(32768, MapTiles.span(15))
    }

    /** Longitude is linear: the two edges of the world and its centre land exactly on
     *  0, span and span/2 at every zoom. */
    @Test
    fun longitudeEdgesAndCentreAreExact() {
        for (z in zooms) {
            val span = MapTiles.span(z).toDouble()
            assertEquals(0.0, MapTiles.tileX(-180.0, z), 1e-9, "west edge at z=$z")
            assertEquals(span, MapTiles.tileX(180.0, z), 1e-9, "east edge at z=$z")
            assertEquals(span / 2, MapTiles.tileX(0.0, z), 1e-9, "Greenwich at z=$z")
        }
    }

    /** The equator is exactly halfway down the map at every zoom -- the single most
     *  load-bearing property of this projection, and the one a sign slip breaks. */
    @Test
    fun equatorIsExactlyHalfway() {
        for (z in zooms) {
            assertEquals(MapTiles.span(z) / 2.0, MapTiles.tileY(0.0, z), 1e-9, "equator at z=$z")
        }
    }

    /** North is SMALLER y. Getting this backwards flips every map vertically, which is
     *  the kind of thing that looks plausible in a 60dp thumbnail. */
    @Test
    fun northIsSmallerY() {
        for (z in zooms) {
            assertTrue(
                MapTiles.tileY(60.0, z) < MapTiles.tileY(0.0, z),
                "north of the equator should be a smaller y at z=$z",
            )
            assertTrue(
                MapTiles.tileY(0.0, z) < MapTiles.tileY(-60.0, z),
                "south of the equator should be a larger y at z=$z",
            )
        }
    }

    /** Mercator is symmetric about the equator: a latitude and its negation are
     *  equidistant from the middle, so their tile Ys sum to the full span. */
    @Test
    fun mercatorIsSymmetricAboutTheEquator() {
        for (z in zooms) {
            for (lat in listOf(1.0, 23.5, 45.0, 51.5, 70.0, 84.0)) {
                assertEquals(
                    MapTiles.span(z).toDouble(),
                    MapTiles.tileY(lat, z) + MapTiles.tileY(-lat, z),
                    1e-6,
                    "asymmetric at lat=$lat z=$z",
                )
            }
        }
    }

    /** Both axes are monotonic, checked across the usable latitude range rather than at
     *  a couple of points. */
    @Test
    fun bothAxesAreMonotonic() {
        val z = 15
        var prevX = -1.0
        for (lon in -180..180 step 5) {
            val x = MapTiles.tileX(lon.toDouble(), z)
            assertTrue(x > prevX, "tileX not increasing at lon=$lon")
            prevX = x
        }
        var prevY = -1.0
        for (lat in 85 downTo -85 step 5) {
            val y = MapTiles.tileY(lat.toDouble(), z)
            assertTrue(y > prevY, "tileY not increasing as latitude falls, at lat=$lat")
            prevY = y
        }
    }

    /**
     * wrapX must land in `0 until span` for NEGATIVE indices too. Kotlin's `%` keeps
     * the sign of the dividend, so a bare `x % span` returns a negative tile index and
     * the tile server answers 404 -- which is what a window straddling the antimeridian
     * produces.
     */
    @Test
    fun wrapXHandlesNegativeAndOverflowingIndices() {
        for (z in listOf(1, 2, 13, 15)) {
            val span = MapTiles.span(z)
            for (x in -2 * span..2 * span) {
                val w = MapTiles.wrapX(x, z)
                assertTrue(w in 0 until span, "wrapX($x, $z) = $w, outside 0 until $span")
            }
            assertEquals(span - 1, MapTiles.wrapX(-1, z), "one west of the west edge wraps to the east edge")
            assertEquals(0, MapTiles.wrapX(span, z), "one east of the east edge wraps to the west edge")
            for (x in 0 until span) assertEquals(x, MapTiles.wrapX(x, z), "in-range index changed")
        }
    }

    /** Every surface must send an identifying User-Agent -- OSM returns a "blocked"
     *  placeholder tile otherwise, which is the failure this centralised. */
    @Test
    fun userAgentIdentifiesTheAppAndCarriesAContact() {
        for (platform in listOf("Android", "WearOS")) {
            val ua = MapTiles.userAgent(platform)
            assertTrue(ua.startsWith("Bloo-$platform/"), "no app/platform identity in: $ua")
            assertTrue(ua.contains("http"), "no contact URL in: $ua")
        }
        assertTrue(MapTiles.userAgent("Android") != MapTiles.userAgent("WearOS"))
    }

    @Test
    fun tileUrlIsTheStandardOsmZxyForm() {
        assertEquals("https://tile.openstreetmap.org/15/16372/10896.png", MapTiles.tileUrl(15, 16372, 10896))
    }
}

/**
 * Tests for [coordString]. The locale ones are the point: the previous implementation
 * used the default locale, so on a comma-decimal device a coordinate pair came out as
 * "48,8566, 2,3522" -- four commas, no way to tell the delimiter from the decimal
 * separators, and unusable if pasted into a map.
 */
class CoordStringTest {

    @Test
    fun formatsToTheRequestedPrecision() {
        assertEquals("48.85660, 2.35220", coordString(48.8566, 2.3522))
        assertEquals("48.8566, 2.3522", coordString(48.8566, 2.3522, decimals = 4))
        assertEquals("49, 2", coordString(48.8566, 2.3522, decimals = 0))
    }

    @Test
    fun handlesNegativeAndZeroCoordinates() {
        assertEquals("-33.8688, 151.2093", coordString(-33.8688, 151.2093, decimals = 4))
        assertEquals("0.0000, 0.0000", coordString(0.0, 0.0, decimals = 4))
        assertEquals("-0.1278, -51.0000", coordString(-0.1278, -51.0, decimals = 4))
    }

    /**
     * The decimal separator must be a dot no matter what locale the device is set to.
     * Swapping the default locale under the call is the only way to actually test this --
     * asserting against the ambient locale would pass on a CI runner set to en_US while
     * the bug shipped to everyone else.
     */
    @Test
    fun usesADotEvenUnderACommaDecimalLocale() {
        val original = java.util.Locale.getDefault()
        try {
            for (tag in listOf("de-DE", "fr-FR", "es-ES", "pt-BR", "ru-RU")) {
                java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
                val s = coordString(48.8566, 2.3522, decimals = 4)
                assertEquals("48.8566, 2.3522", s, "wrong separator under $tag")
                // Exactly one comma: the delimiter. More than that means the decimal
                // separator has collided with it.
                assertEquals(1, s.count { it == ',' }, "ambiguous comma count under $tag: $s")
            }
        } finally {
            java.util.Locale.setDefault(original)
        }
    }

    /** The GeoLocation extension and the raw-pair form must agree -- they exist so the
     *  watch, which holds bare Doubles, doesn't need its own format string. */
    @Test
    fun theExtensionAgreesWithTheRawPairForm() {
        val loc = GeoLocation(48.8566, 2.3522, null)
        for (d in listOf(0, 2, 4, 5, 6)) {
            assertEquals(coordString(48.8566, 2.3522, d), loc.coordString(d), "disagreed at $d decimals")
        }
        assertEquals(coordString(48.8566, 2.3522), loc.coordString())
    }
}
