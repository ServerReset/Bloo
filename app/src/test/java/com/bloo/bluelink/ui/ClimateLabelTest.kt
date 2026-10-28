package com.bloo.bluelink.ui

import com.bloo.bluelink.data.climatePresetDetail
import com.bloo.bluelink.data.climatePresetTemp
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.WheelHeatLevel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins for the small pure string layer that renders climate summaries --
 * the running pebble header / AI confirm card. The conversions
 * (["climatePresetDetail"]) had a documented history of re-inlining the °F->°C
 * rule and drifting from [degValue]; the shared-rule pin is what makes a
 * future drift fail loudly instead of displaying 68° in Celsius.
 */
class ClimateLabelTest {

    // --- climatePresetDetail ----------------------------------------------------------

    private fun req(tempF: Int, defrost: Boolean = false, heat: Boolean = false, cool: Boolean = false, wheel: Boolean = false) =
        ClimateRequest(
            tempF = tempF,
            defrost = defrost,
            durationMinutes = 15,
            seatFrontLeft = if (heat) SeatLevel.HIGH_HEAT else SeatLevel.OFF,
            seatFrontRight = if (cool) SeatLevel.HIGH_COOL else SeatLevel.OFF,
            steeringWheelHeat = if (wheel) WheelHeatLevel.HIGH else WheelHeatLevel.OFF,
        )

    @Test
    fun climatePresetDetail_bareTemperatureWithUnitRespected() {
        assertEquals("70°", climatePresetDetail(req(70), fahrenheit = true))
        // Fahrenheit input converted per the shared degValue rule: 68°F -> 20°C.
        assertEquals("20°", climatePresetDetail(req(68), fahrenheit = false))
    }

    @Test
    fun climatePresetTemp_isJustTheTemperatureNeverTheOptions() {
        // The preset row shows the name and this, right-aligned: defrost and seat heat are in the editor, not on the row.
        assertEquals("80°", climatePresetTemp(req(80, defrost = true, heat = true), fahrenheit = true))
        assertEquals("20°", climatePresetTemp(req(68), fahrenheit = false))
    }

    @Test
    fun climatePresetDetail_defrostAndSeatsAppend() {
        assertEquals("80° · Defrost", climatePresetDetail(req(80, defrost = true), fahrenheit = true))
        assertEquals("72° · Heat", climatePresetDetail(req(72, heat = true), fahrenheit = true))
        assertEquals("65° · Cool", climatePresetDetail(req(65, cool = true), fahrenheit = true))
        assertEquals("75° · Wheel", climatePresetDetail(req(75, wheel = true), fahrenheit = true))
    }

    @Test
    fun climatePresetDetail_rawTempTargetNeverLeaksIntoTheSummary() {
        // An unnamed temp target must never leak its raw value into the summary --
        // it should always carry a clean name or the generic word instead.
        val d = climatePresetDetail(req(70), fahrenheit = true)
        assertEquals("70°", d)
    }
}
