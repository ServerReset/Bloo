package com.bloo.bluelink.wear

import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The climate tile's one decision: which rows a car gets, and what a press on each runs. */
class WearClimateTileRowsTest {
    private fun car(climateOn: Boolean?) = VehicleSnapshot(vin = "V", name = "Car", model = "M", isEv = true, climateOn = climateOn)

    private fun preset(id: String, name: String) = ClimatePreset(id, name, ClimateRequest(tempF = 70, defrost = false, durationMinutes = 10))

    private val detail: (ClimateRequest) -> String = { "${it.tempF}°" }

    @Test fun aRunningClimateOffersOnlyStop() {
        val rows = WearClimateTileRows.rows(car(climateOn = true), listOf(preset("1", "Warm")), detail)
        assertEquals(1, rows.size)
        assertEquals("climate_off", rows[0].key)
        assertTrue(rows[0].active)
    }

    @Test fun anIdleClimateOffersSmartThenThePhonesPresets() {
        val rows = WearClimateTileRows.rows(car(climateOn = false), listOf(preset("1", "Warm"), preset("2", "Cool")), detail)
        assertEquals(listOf("climate_smart", "preset:1", "preset:2"), rows.map { it.key })
        assertEquals(listOf("Smart climate", "Warm", "Cool"), rows.map { it.title })
        assertEquals("70°", rows[1].subtitle)
    }

    @Test fun onlyAsManyPresetsAsFitAreShown() {
        val presets = (1..6).map { preset("$it", "P$it") }
        val rows = WearClimateTileRows.rows(car(climateOn = null), presets, detail)
        assertEquals(WearClimateTileRows.MAX_ROWS, rows.size)
        assertEquals(listOf("climate_smart", "preset:1", "preset:2"), rows.map { it.key })
    }

    @Test fun aCarWithNoPresetsStillOffersSmartClimate() {
        val rows = WearClimateTileRows.rows(car(climateOn = false), emptyList(), detail)
        assertEquals(listOf("climate_smart"), rows.map { it.key })
    }
}
