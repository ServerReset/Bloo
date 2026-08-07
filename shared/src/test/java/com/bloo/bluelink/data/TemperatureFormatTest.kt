package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-JVM tests for the temperature label helpers in FormatUtils -- [degValue],
 * [degLabel] and [weatherTemp].
 *
 * These exist because the bug they pin was invisible by inspection and systematic in
 * effect. Both shared helpers truncated instead of rounding, so every metric user saw
 * almost every temperature in the app up to a whole degree cold, while every
 * conversion written inline elsewhere in the codebase rounded correctly. Nothing
 * crashed and nothing looked obviously wrong -- 75°F simply read as "23°C".
 */
class TemperatureFormatTest {

    // ---- degValue: the shared rounding rule ----

    /** The Fahrenheit branch rounds rather than truncating. This is the branch that
     *  only matters for regions sending fractions -- Canada reports fractional °F --
     *  which is why it went unnoticed. */
    @Test
    fun degValueRoundsFahrenheit() {
        assertEquals(72, degValue(72.0, fahrenheit = true))
        assertEquals(72, degValue(71.6, fahrenheit = true))
        assertEquals(71, degValue(71.4, fahrenheit = true))
        assertEquals(72, degValue(71.5, fahrenheit = true))
    }

    /** The Celsius branch rounds. Every one of these truncated a degree low before. */
    @Test
    fun degValueRoundsCelsius() {
        // 75°F = 23.888…°C
        assertEquals(24, degValue(75.0, fahrenheit = false))
        // 82°F = 27.777…°C -- the top of CLIMATE_TEMP_RANGE_F
        assertEquals(28, degValue(82.0, fahrenheit = false))
        // 62°F = 16.666…°C -- the bottom of it
        assertEquals(17, degValue(62.0, fahrenheit = false))
        // Exact conversions must not be nudged: 32°F is 0°C, 212°F is 100°C.
        assertEquals(0, degValue(32.0, fahrenheit = false))
        assertEquals(100, degValue(212.0, fahrenheit = false))
    }

    /** Below freezing the rounding has to go the right way too -- truncation toward
     *  zero and rounding disagree in the opposite direction for negatives, so this is
     *  the case a "just use toInt()" regression would get wrong most visibly. */
    @Test
    fun degValueRoundsBelowFreezing() {
        // 20°F = -6.666…°C. Truncation toward zero would give -6, which is WARMER
        // than the real value; rounding gives -7.
        assertEquals(-7, degValue(20.0, fahrenheit = false))
        // 0°F = -17.777…°C
        assertEquals(-18, degValue(0.0, fahrenheit = false))
        assertEquals(0, degValue(-0.4, fahrenheit = true))
    }

    /** No °F value in the car's own climate range may map to a °C label that is off
     *  by more than half a degree. Swept rather than spot-checked, because the
     *  truncation bug was uniform across the range and a couple of hand-picked cases
     *  is exactly how it survived. */
    @Test
    fun degValueIsWithinHalfADegreeAcrossTheClimateRange() {
        for (f in CLIMATE_TEMP_RANGE_F) {
            val exact = (f - 32) * 5 / 9.0
            val shown = degValue(f.toDouble(), fahrenheit = false)
            val err = kotlin.math.abs(shown - exact)
            kotlin.test.assertTrue(err <= 0.5, "$f°F showed ${shown}°C, off by $err from $exact")
        }
    }

    // ---- degLabel: unit suffix and passthrough ----

    @Test
    fun degLabelAppendsTheRightUnitAndAgreesWithDegValue() {
        assertEquals("72°F", degLabel("72", fahrenheit = true))
        assertEquals("24°C", degLabel("75", fahrenheit = false))
        for (raw in listOf("62", "70", "75", "82", "71.6")) {
            val f = raw.toDouble()
            assertEquals("${degValue(f, true)}°F", degLabel(raw, fahrenheit = true))
            assertEquals("${degValue(f, false)}°C", degLabel(raw, fahrenheit = false))
        }
    }

    /** A non-numeric reading passes through with a bare degree sign rather than
     *  crashing or being hidden -- the car occasionally reports blanks or codes here,
     *  and showing the raw value beats showing nothing. */
    @Test
    fun degLabelPassesThroughNonNumericValues() {
        assertEquals("--°", degLabel("--", fahrenheit = true))
        assertEquals("--°", degLabel("--", fahrenheit = false))
        assertEquals("°", degLabel("", fahrenheit = true))
        assertEquals("LO°", degLabel("LO", fahrenheit = false))
    }

    // ---- weatherTemp: Celsius in, either unit out ----

    @Test
    fun weatherTempRoundsBothBranches() {
        assertEquals("23°C", weatherTemp(22.8, fahrenheit = false))
        assertEquals("22°C", weatherTemp(22.4, fahrenheit = false))
        // 22.8°C = 73.04°F
        assertEquals("73°F", weatherTemp(22.8, fahrenheit = true))
        assertEquals("0°C", weatherTemp(0.0, fahrenheit = false))
        assertEquals("32°F", weatherTemp(0.0, fahrenheit = true))
    }

    /**
     * The metric branch must round the Celsius it was GIVEN, not a value that has
     * been through Fahrenheit and back.
     *
     * Routing weatherTemp through degValue would be algebraically identical and
     * numerically wrong: °C -> °F -> °C is not exact in binary floating point, and
     * over -40..50°C there are nine half-degree inputs where the trip lands on the
     * wrong side of the tie. Both directions are covered below.
     *
     * 24.5 comes back as 24.499999999999996, so a round trip would round DOWN to 24
     * where rounding the input gives 25. -4.5 comes back as -4.500000000000001, so a
     * round trip would round DOWN to -5 where the input rounds to -4 (Kotlin's
     * roundToInt breaks ties toward positive infinity, which is why the negative
     * tie goes to -4 and not -5).
     */
    @Test
    fun weatherTempDoesNotRoundTripThroughFahrenheit() {
        assertEquals("25°C", weatherTemp(24.5, fahrenheit = false))
        assertEquals("-4°C", weatherTemp(-4.5, fahrenheit = false))
        // Not a round-trip-sensitive value, just confirming ordinary ties go up.
        assertEquals("23°C", weatherTemp(22.5, fahrenheit = false))
    }
}
