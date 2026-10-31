package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for the FormatUtils helpers that had NO coverage at all before
 * this file: [smartClimateTargetF]/[smartClimateIsCooling],
 * [tripDate], [maskEmail], [formatSpeedMph] and
 * [parseOdometerMiles]. Several of these functions' own doc comments describe a
 * real historical bug they were written to fix (a flat-offset smart-climate target
 * clamping to the WRONG end of the range on an extreme day, a phone trip-date parser
 * that silently fell back to raw text on a 'T'-separated timestamp, a speed formatter
 * that ran mph input through the km/h conversion and rendered "62 mph" as "38 mph")
 * -- a regression with no test is a regression waiting to happen again.
 */
class FormatUtilsTest {

    // ---- smartClimateTargetF / smartClimateIsCooling ----

    @Test
    fun smartClimateTargetF_hotOutside_coolsFurtherTheHotterItIs() {
        // A car in the heat gains warmth fast, so the setpoint drops as the feels-like rises, to the range's cool end.
        assertEquals(66, smartClimateTargetF(90))
        assertEquals(66, smartClimateTargetF(89))
        assertEquals(64, smartClimateTargetF(100))
    }

    @Test
    fun smartClimateTargetF_coldOutside_heatsFurtherTheColderItIs() {
        // The cabin has further to go as it gets colder, so the setpoint rises, to the range's warm end.
        assertEquals(78, smartClimateTargetF(40))
        assertEquals(80, smartClimateTargetF(30))
        assertEquals(82, smartClimateTargetF(0))
    }

    @Test
    fun smartClimateTargetF_comfortableOutside_holdsComfort() {
        assertEquals(70, smartClimateTargetF(75))
        assertEquals(70, smartClimateTargetF(70))
        assertEquals(70, smartClimateTargetF(64))
    }

    @Test
    fun smartClimateTargetF_coldDay_isNotTheLowestSetpoint() {
        // The bug this guards: a 48F day used to heat only to 62F, the car's coldest setting.
        assertEquals(75, smartClimateTargetF(48))
        assertEquals(78, smartClimateTargetF(41))
    }

    @Test
    fun smartClimateHint_saysTargetThenOutsideInThePersonsUnit() {
        assertEquals("Cool to ${degLabel("66", true)} · ${degLabel("89", true)} outside", smartClimateHint(89, fahrenheit = true))
    }

    @Test
    fun smartClimateMode_heatsBelowTheBand_coolsAboveIt_holdsBetween() {
        assertEquals(SmartClimateMode.HEAT, smartClimateMode(63))
        assertEquals(SmartClimateMode.HOLD, smartClimateMode(64))
        assertEquals(SmartClimateMode.HOLD, smartClimateMode(75))
        assertEquals(SmartClimateMode.COOL, smartClimateMode(76))
    }

    @Test
    fun smartClimateIsCooling_onlyAboveTheComfortableBand() {
        assertTrue(smartClimateIsCooling(76))
        assertFalse(smartClimateIsCooling(75))
        assertFalse(smartClimateIsCooling(69))
    }

    // ---- tripDate ----
    // Assertions avoid hardcoding a weekday name (June 1 2026's actual weekday is
    // incidental to what's being tested) -- they instead check for the parts that
    // ARE deterministic: the month/day/time render correctly, and includeWeekday
    // controls whether a weekday prefix is present at all.

    @Test
    fun tripDate_blankOrNull_fallsBackToPlainLabel() {
        assertEquals("Trip", tripDate(null))
        assertEquals("Trip", tripDate(""))
        assertEquals("Trip", tripDate("   "))
    }

    @Test
    fun tripDate_tSeparated_parsesAndFormats() {
        val result = tripDate("2026-06-01T18:22:31")
        assertTrue(result.contains("Jun 1"), result)
        assertTrue(result.contains("6:22 PM"), result)
    }

    @Test
    fun tripDate_reusesCachedFormattersWithoutCarryingStateBetweenCalls() {
        // tripDate now holds its SimpleDateFormats in ThreadLocals instead of building
        // three of them per call, which is a real speedup on the phone's trips list but
        // introduces a risk the old code could not have: SimpleDateFormat is stateful,
        // so a reused instance that carried anything between calls would corrupt later
        // rows. These pin that it does not.

        // Same input twice -> byte-identical output.
        assertEquals(tripDate("2026-06-01T18:22:31"), tripDate("2026-06-01T18:22:31"))

        // The two output formatters are separate instances and must not bleed into each
        // other, in either order.
        val withDay = tripDate("2026-06-01T18:22:31", includeWeekday = true)
        val noDay = tripDate("2026-06-01T18:22:31", includeWeekday = false)
        assertEquals(withDay, tripDate("2026-06-01T18:22:31", includeWeekday = true))
        assertEquals(noDay, tripDate("2026-06-01T18:22:31", includeWeekday = false))
        assertTrue(withDay.endsWith(noDay), "$withDay should be $noDay plus a weekday prefix")

        // A parse FAILURE followed by a success: the first pattern throws on a
        // space-separated value before the second one succeeds, and that must leave the
        // shared parsers usable.
        assertEquals("Trip", tripDate(null))
        val afterFallback = tripDate("not a timestamp at all")
        assertTrue(afterFallback.isNotBlank())
        assertTrue(tripDate("2026-06-01 18:22:31.0").contains("6:22 PM"))
        assertTrue(tripDate("2026-06-01T18:22:31").contains("Jun 1"))
    }

    @Test
    fun tripDate_spaceSeparatedWithFractionalSeconds_alsoParses() {
        // The exact bug this dual-pattern parse exists to fix: a space-separated
        // feed value (with the fractional-seconds suffix trimmed off first).
        val result = tripDate("2026-06-01 18:22:31.0")
        assertTrue(result.contains("Jun 1"), result)
        assertTrue(result.contains("6:22 PM"), result)
    }

    @Test
    fun tripDate_includeWeekdayFalse_dropsTheWeekdayPrefix() {
        val withWeekday = tripDate("2026-06-01T18:22:31", includeWeekday = true)
        val withoutWeekday = tripDate("2026-06-01T18:22:31", includeWeekday = false)
        assertTrue(withoutWeekday.startsWith("Jun 1"), withoutWeekday)
        assertFalse(withWeekday.startsWith("Jun 1"), withWeekday)
    }

    @Test
    fun tripDate_unparseableShape_fallsBackToTruncatedRawText() {
        assertEquals("garbage-timestam", tripDate("garbage-timestamp"))
    }

    // ---- maskEmail ----

    @Test
    fun maskEmail_normalAddress_keepsFirstCharAndDomain() {
        assertEquals("j***@gmail.com", maskEmail("jane.doe@gmail.com"))
    }

    @Test
    fun maskEmail_malformed_fullyRedacts() {
        assertEquals("***", maskEmail("noatsign"))
        // '@' as the very first character: no local part to keep either.
        assertEquals("***", maskEmail("@gmail.com"))
    }

    // ---- formatSpeedMph ----

    @Test
    fun formatSpeedMph_imperial_passesThroughUnchanged() {
        // The exact regression this function exists to fix: formatSpeed (km/h
        // input) rendered a real 62 mph EvTrip value as "38 mph".
        assertEquals("62 mph", formatSpeedMph(62.0, metric = false))
    }

    @Test
    fun formatSpeedMph_metric_convertsToKmh() {
        assertEquals("100 km/h", formatSpeedMph(62.0, metric = true))
    }

    // ---- parseOdometerMiles ----

    @Test
    fun parseOdometerMiles_stripsGroupingCommas() {
        assertEquals(12345, parseOdometerMiles("12,345.6"))
        assertEquals(1234, parseOdometerMiles("1,234"))
    }

    @Test
    fun parseOdometerMiles_blankOrUnparseable_returnsNull() {
        assertNull(parseOdometerMiles(null))
        assertNull(parseOdometerMiles(""))
        assertNull(parseOdometerMiles("  "))
        assertNull(parseOdometerMiles("n/a"))
    }
}
