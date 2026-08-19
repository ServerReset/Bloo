package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for the FormatUtils helpers that had NO coverage at all before
 * this file: [smartClimateTargetF]/[smartClimateIsCooling], [climateChunks],
 * [tripDate], [maskEmail], [formatSpeedMph], [vehicleStateLabel] and
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
    fun smartClimateTargetF_extremeHeat_goesToColdestEnd() {
        assertEquals(62, smartClimateTargetF(100))
        assertEquals(62, smartClimateTargetF(90))
    }

    @Test
    fun smartClimateTargetF_extremeCold_goesToWarmestEnd() {
        assertEquals(82, smartClimateTargetF(40))
        assertEquals(82, smartClimateTargetF(0))
    }

    @Test
    fun smartClimateTargetF_warmModerate_offsetsDownTenDegrees() {
        assertEquals(79, smartClimateTargetF(89))
        assertEquals(65, smartClimateTargetF(75))
    }

    @Test
    fun smartClimateTargetF_coolModerate_offsetsUpTenDegrees_clampedIntoRange() {
        // 41 + 10 = 51, below CLIMATE_TEMP_RANGE_F's floor of 62 -- clamped up.
        assertEquals(62, smartClimateTargetF(41))
        // 70 - 10 = 60, same clamp from the other branch's boundary.
        assertEquals(62, smartClimateTargetF(70))
    }

    @Test
    fun smartClimateIsCooling_matchesTargetF_coolCutoff() {
        assertTrue(smartClimateIsCooling(70))
        assertFalse(smartClimateIsCooling(69))
    }

    // ---- climateChunks ----

    @Test
    fun climateChunks_splitsIntoTenMinuteChunksPlusRemainder() {
        assertEquals(listOf(10, 3), climateChunks(13))
        assertEquals(listOf(10, 10, 5), climateChunks(25))
    }

    @Test
    fun climateChunks_underTenMinutes_isOneChunk() {
        assertEquals(listOf(7), climateChunks(7))
        assertEquals(listOf(10), climateChunks(10))
    }

    @Test
    fun climateChunks_zeroOrNegative_coercesToOneMinute() {
        assertEquals(listOf(1), climateChunks(0))
        assertEquals(listOf(1), climateChunks(-5))
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

    // ---- vehicleStateLabel ----

    @Test
    fun vehicleStateLabel_priorityOrder_drivingBeatsEverything() {
        assertEquals("Driving", vehicleStateLabel(engineOn = true, charging = true, climateOn = true, locked = true))
    }

    @Test
    fun vehicleStateLabel_priorityOrder_chargingBeatsClimateAndLock() {
        assertEquals("Charging", vehicleStateLabel(engineOn = false, charging = true, climateOn = true, locked = true))
    }

    @Test
    fun vehicleStateLabel_priorityOrder_climateBeatsLock() {
        assertEquals("Climate on", vehicleStateLabel(engineOn = false, charging = false, climateOn = true, locked = true))
    }

    @Test
    fun vehicleStateLabel_lockStateWhenNothingElseIsTrue() {
        assertEquals("Locked", vehicleStateLabel(engineOn = false, charging = false, climateOn = false, locked = true))
        assertEquals("Unlocked", vehicleStateLabel(engineOn = false, charging = false, climateOn = false, locked = false))
    }

    @Test
    fun vehicleStateLabel_allUnknown_showsEmDash() {
        assertEquals("—", vehicleStateLabel(engineOn = null, charging = null, climateOn = null, locked = null))
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
