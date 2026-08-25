package com.bloo.bluelink.ui

import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins for the small pure string layer that renders quick-tile and climate
 * summaries. These strings are user-facing copy in two places at once
 * (the configured tile list under Settings, and the running pebble header /
 * AI confirm card), so a typo or a changed unit rule appears on both -- and
 * the climate conversions (["presetDetail"]) had a documented history of
 * re-inlining the °F->°C rule and drifting from [degValue]; the shared-rule
 * pin is what makes a future drift fail loudly instead of displaying 68°
 * in Celsius.
 */
class TileAndClimateLabelTest {

    // --- tileActionLabel / tileSummary --------------------------------------

    @Test
    fun tileActionLabel_knownCommandsMapToCopy() {
        assertEquals("Lock / unlock", tileActionLabel("doors"))
        assertEquals("Climate", tileActionLabel("climate"))
        assertEquals("Charge", tileActionLabel("charge"))
    }

    @Test
    fun tileActionLabel_unknownFallsBackToRawCommand() {
        assertEquals("frobnicate", tileActionLabel("frobnicate"))
    }

    @Test
    fun tileSummary_staticCommands() {
        assertEquals("Lock / unlock", tileSummary("doors", "default", null))
        assertEquals("Start / stop charge", tileSummary("charge", "default", null))
        assertEquals("Opens the app", tileSummary("open", "default", null))
    }

    @Test
    fun tileSummary_climateVariants() {
        assertEquals("Climate · Smart", tileSummary("climate", "smart", null))
        assertEquals("Climate · Basic", tileSummary("climate", "default", null))
        // A named preset shows its name; an unnamed temp target falls back
        // to "Preset" rather than leaking the raw temp:temp:70 target.
        assertEquals("Climate · Arctic blast", tileSummary("climate", "temp:70", "Arctic blast"))
        assertEquals("Climate · Preset", tileSummary("climate", "temp:70", null))
    }

    // --- climateChunksLabel ---------------------------------------------------

    @Test
    fun climateChunks_singleRun() {
        // One run within the cap renders without the "+" chain.
        assertEquals("10 min", climateChunksLabel(CLIMATE_DURATION_RANGE.last))
    }

    @Test
    fun climateChunks_composedFromRuns() {
        // 13 = the cap (10) + the remainder (3); the label chains the run
        // lengths and says " min" once at the end: "10 + 3 min".
        assertEquals("${CLIMATE_DURATION_RANGE.last} + 3 min", climateChunksLabel(13))
        // 70 = seven capped runs; every chunk is chained.
        assertEquals(
            List(7) { "${CLIMATE_DURATION_RANGE.last}" }.joinToString(" + ") + " min",
            climateChunksLabel(70),
        )
    }

    @Test
    fun climateChunks_zeroCoercesToOneMinute() {
        // climateChunks coerces a 0-minute request to 1 (a "run for 0" would
        // otherwise produce an empty chunks list); the LABEL documents that --
        // and pins it, because "1 min" for zero is deliberate, not a glitch.
        assertEquals("1 min", climateChunksLabel(0))
    }

    // --- presetDetail ----------------------------------------------------------

    private fun req(tempF: Int, defrost: Boolean = false, heat: Boolean = false, cool: Boolean = false, wheel: Boolean = false) =
        ClimateRequest(
            tempF = tempF,
            defrost = defrost,
            durationMinutes = 15,
            seatFrontLeft = if (heat) SeatLevel.HIGH_HEAT else SeatLevel.OFF,
            seatFrontRight = if (cool) SeatLevel.HIGH_COOL else SeatLevel.OFF,
            steeringWheelHeat = wheel,
        )

    @Test
    fun presetDetail_bareTemperatureWithUnitRespected() {
        assertEquals("70°", presetDetail(req(70), fahrenheit = true))
        // Fahrenheit input converted per the shared degValue rule: 68°F -> 20°C.
        assertEquals("20°", presetDetail(req(68), fahrenheit = false))
    }

    @Test
    fun presetDetail_defrostAndSeatsAppend() {
        assertEquals("80° · Defrost", presetDetail(req(80, defrost = true), fahrenheit = true))
        assertEquals("72° · Heat", presetDetail(req(72, heat = true), fahrenheit = true))
        assertEquals("65° · Cool", presetDetail(req(65, cool = true), fahrenheit = true))
        assertEquals("75° · Wheel", presetDetail(req(75, wheel = true), fahrenheit = true))
    }

    @Test
    fun presetDetail_rawTempTargetNeverLeaksIntoTheSummary() {
        // The label path used to fall back to the raw target when unnamed
        // (see tileSummary's own "Preset" fallback); at the settings picker the
        // summary always carries a clean name or the generic word.
        val d = presetDetail(req(70), fahrenheit = true)
        assertEquals("70°", d)
    }
}
