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

    /**
     * A Celsius reading is not run through the Fahrenheit formula.
     *
     * The bug this pins, reported from a real device: a car sitting at 22.5°C
     * displayed as -5°C, because every reading was assumed to be °F and
     * (22.5 - 32) * 5/9 = -5.3. The API sends its own unit code alongside the
     * value -- 0 Celsius, 1 Fahrenheit, matching every send path in this app --
     * and reading it is the whole fix.
     */
    @Test
    fun degLabelReadsTheApiUnitCodeInsteadOfAssumingFahrenheit() {
        assertEquals("22.5°C", degLabel("22.5", fahrenheit = false, sourceUnit = 0))
        assertEquals("73°F", degLabel("22.5", fahrenheit = true, sourceUnit = 0))
        // The exact reported case must not come back.
        kotlin.test.assertTrue(!degLabel("22.5", fahrenheit = false, sourceUnit = 0).startsWith("-"))
    }

    /** A Fahrenheit reading (unit 1, what both US backends send) converts exactly
     *  as it always did -- the unit code must not disturb what was already right. */
    @Test
    fun degLabelStillConvertsFahrenheitReadings() {
        assertEquals("72°F", degLabel("72", fahrenheit = true, sourceUnit = 1))
        assertEquals("24°C", degLabel("75", fahrenheit = false, sourceUnit = 1))
        for (raw in listOf("62", "70", "75", "82", "71.6")) {
            val f = raw.toDouble()
            assertEquals("${degValue(f, false)}°C", degLabel(raw, fahrenheit = false, sourceUnit = 1))
            // An unknown unit keeps the historical assumption, so nothing that was
            // reading correctly before the unit code existed changes now.
            assertEquals(degLabel(raw, fahrenheit = false, sourceUnit = 1), degLabel(raw, fahrenheit = false))
        }
    }

    /** No conversion means no rounding: Canada's setpoint table is in half degrees,
     *  so turning the 22.5°C the car actually reported into "23°C" would throw away
     *  precision on the common case rather than an edge one. */
    @Test
    fun degLabelKeepsAHalfDegreeWhenNoConversionIsNeeded() {
        assertEquals("22.5°C", degLabel("22.5", fahrenheit = false, sourceUnit = 0))
        assertEquals("22°C", degLabel("22.0", fahrenheit = false, sourceUnit = 0))
        // Fahrenheit stays whole -- the half-degree exception is Celsius-only,
        // because that is the unit the car's own setpoint table steps in.
        assertEquals("73°F", degLabel("72.5", fahrenheit = true, sourceUnit = 1))
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

/**
 * Tests for [vehicleDisplayName]. Separate class, same file as the temperature ones
 * because both are FormatUtils fallback rules with the same failure shape: a `?:`
 * chain that guards null and forgets blank.
 */
class VehicleDisplayNameTest {

    @Test
    fun prefersNicknameThenModelThenIdTail() {
        assertEquals("Mine", vehicleDisplayName("Mine", "Ioniq 5", "5NMS44"))
        assertEquals("Ioniq 5", vehicleDisplayName(null, "Ioniq 5", "KM8K2CAB4NU123456"))
        assertEquals("123456", vehicleDisplayName(null, null, "KM8K2CAB4NU123456"))
    }

    /**
     * The whole point. Kia US and Canada's JSON accessor filters the literal string
     * "null" but passes "" through, so a car with its nickname cleared arrived here as
     * an empty string and `?:` did not fire -- naming the car "" everywhere at once.
     */
    @Test
    fun treatsBlankAsAbsentAtEveryStep() {
        assertEquals("Ioniq 5", vehicleDisplayName("", "Ioniq 5", "KM8K2CAB4NU123456"))
        assertEquals("Ioniq 5", vehicleDisplayName("   ", "Ioniq 5", "KM8K2CAB4NU123456"))
        assertEquals("123456", vehicleDisplayName("", "", "KM8K2CAB4NU123456"))
        assertEquals("123456", vehicleDisplayName("  ", "  ", "KM8K2CAB4NU123456"))
    }

    /** Never returns blank, even given nothing usable at all -- the old chains ended
     *  in `"".takeLast(6)`, which is still nothing. */
    @Test
    fun neverReturnsBlank() {
        assertEquals("Car", vehicleDisplayName(null, null, ""))
        assertEquals("Car", vehicleDisplayName("", "", ""))
        kotlin.test.assertTrue(vehicleDisplayName(null, null, "AB").isNotBlank())
    }

    /** A short id yields whatever it has rather than padding or failing. */
    @Test
    fun handlesIdsShorterThanSixCharacters() {
        assertEquals("AB12", vehicleDisplayName(null, null, "AB12"))
    }
}

/**
 * Tests for [useFahrenheit], the unit-system rule the phone and the watch used to
 * derive two different ways.
 */
class UseFahrenheitTest {

    @Test
    fun metricMeansCelsiusAndAnythingElseMeansFahrenheit() {
        kotlin.test.assertFalse(useFahrenheit("metric"))
        kotlin.test.assertTrue(useFahrenheit("imperial"))
    }

    /**
     * Null and unrecognised values default to imperial, matching every other reader of
     * this setting. This is the case the watch got wrong: with no phone ever paired its
     * payload was null, and its old `null != false` test made that mean Fahrenheit even
     * for a watch whose own Units setting the user had put on metric.
     */
    @Test
    fun defaultsToFahrenheitForNullOrUnknown() {
        kotlin.test.assertTrue(useFahrenheit(null))
        kotlin.test.assertTrue(useFahrenheit(""))
        kotlin.test.assertTrue(useFahrenheit("Metric"))
    }

    /** The rule is exactly "metric means Celsius" -- so it agrees with the
     *  `unitSystem == "metric"` test that every distance and speed readout uses,
     *  which is the agreement that was missing. */
    @Test
    fun agreesWithTheMetricTestUsedByDistanceReadouts() {
        for (u in listOf("metric", "imperial", "", "MPH", null)) {
            assertEquals(u == "metric", !useFahrenheit(u), "disagreed for $u")
        }
    }
}

/**
 * Tests for [Battery12V.needsAttention] -- the rule the watch had hardcoded as
 * `batSoc < 20` in three separate places while displaying a "Low" label derived from
 * a different cutoff.
 */
class Battery12VTest {

    @Test
    fun agreesWithTheHealthLabelItIsShownNextTo() {
        for (soc in 0..100) {
            val b = Battery12V(batSoc = soc)
            val expected = b.health == "Low" || b.health == "Needs attention"
            assertEquals(expected, b.needsAttention, "disagreed at $soc%")
        }
    }

    /** The case that was wrong: a reading the label calls "Low" but the old `< 20`
     *  cutoff treated as fine, so the row read "35% · Low" in ordinary text and was
     *  left out of the "N to check" count. */
    @Test
    fun flagsReadingsTheOldCutoffMissed() {
        assertEquals("Low", Battery12V(batSoc = 35).health)
        kotlin.test.assertTrue(Battery12V(batSoc = 35).needsAttention)
        kotlin.test.assertTrue(Battery12V(batSoc = 49).needsAttention)
        kotlin.test.assertFalse(Battery12V(batSoc = 50).needsAttention)
        kotlin.test.assertFalse(Battery12V(batSoc = 80).needsAttention)
    }

    /** A car reporting no 12V state of charge is not an issue -- unknown must not
     *  count as a problem, the same yield-nothing rule the rest of the app follows. */
    @Test
    fun unknownIsNotAnIssue() {
        kotlin.test.assertFalse(Battery12V(batSoc = null).needsAttention)
        kotlin.test.assertEquals(null, Battery12V(batSoc = null).health)
    }

    /** The explicit bad-state flag counts regardless of how healthy the charge looks. */
    @Test
    fun explicitBadStateFlagCountsEvenAtFullCharge() {
        val b = Battery12V(batSoc = 95, batState = 0)
        assertEquals("Needs attention", b.health)
        kotlin.test.assertTrue(b.needsAttention)
    }
}

/**
 * Tests for [chargeTier] -- the charge/fuel bands the widget ring, the watch tile and
 * the watch home ring each used to define for themselves, with no two agreeing.
 */
class ChargeTierTest {

    /** Charging outranks the level, however low the level is. */
    @Test
    fun chargingWinsOverEveryLevel() {
        for (p in listOf(0, 5, 15, 30, 50, 100)) {
            assertEquals(ChargeTier.CHARGING, chargeTier(p, charging = true), "at $p%")
        }
        assertEquals(ChargeTier.CHARGING, chargeTier(null, charging = true))
    }

    /** Unknown is its own band, not folded into a level. The widget used to flatten a
     *  null percent to 0 before this, which made it CRITICAL -- a confident red arc for
     *  a car that had never reported a charge. */
    @Test
    fun unknownIsItsOwnBand() {
        assertEquals(ChargeTier.UNKNOWN, chargeTier(null, charging = false))
    }

    /** The boundaries are INCLUSIVE, which is the disagreement being resolved: the
     *  widget used `<=` and both watch surfaces used `<`, so a car at exactly 15% was
     *  red on the widget and not on the watch. */
    @Test
    fun boundariesAreInclusive() {
        assertEquals(ChargeTier.CRITICAL, chargeTier(CHARGE_CRITICAL_PCT, charging = false))
        assertEquals(ChargeTier.LOW, chargeTier(CHARGE_CRITICAL_PCT + 1, charging = false))
        assertEquals(ChargeTier.LOW, chargeTier(CHARGE_LOW_PCT, charging = false))
        assertEquals(ChargeTier.NORMAL, chargeTier(CHARGE_LOW_PCT + 1, charging = false))
    }

    @Test
    fun coversTheWholeRangeInOrder() {
        assertEquals(ChargeTier.CRITICAL, chargeTier(0, charging = false))
        assertEquals(ChargeTier.CRITICAL, chargeTier(15, charging = false))
        assertEquals(ChargeTier.LOW, chargeTier(20, charging = false))
        assertEquals(ChargeTier.LOW, chargeTier(30, charging = false))
        assertEquals(ChargeTier.NORMAL, chargeTier(31, charging = false))
        assertEquals(ChargeTier.NORMAL, chargeTier(100, charging = false))
    }

    /** The bands must be contiguous and non-overlapping across every percentage --
     *  three hand-written copies is exactly how a gap or an overlap creeps in. */
    @Test
    fun bandsAreContiguousAcrossEveryPercentage() {
        var seen = ChargeTier.CRITICAL
        for (p in 0..100) {
            val t = chargeTier(p, charging = false)
            kotlin.test.assertTrue(
                t == seen || (seen == ChargeTier.CRITICAL && t == ChargeTier.LOW) ||
                    (seen == ChargeTier.LOW && t == ChargeTier.NORMAL),
                "band went backwards or skipped at $p%: $seen -> $t",
            )
            seen = t
        }
        assertEquals(ChargeTier.NORMAL, seen)
    }
}
