package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassStopsTest {
    @Test
    fun stopsAreFrostedMediumClearVeryClearThenUltra() {
        assertEquals(listOf("Frosted", "Medium", "Clear", "Very clear", "Ultra"), GlassStops.map { it.name })
    }

    @Test
    fun ultraIsTheLastStopAndTheOnlyUltraOne() {
        assertTrue(GlassStops.last().ultra)
        assertEquals(1, GlassStops.count { it.ultra })
    }

    @Test
    fun nearestStopNeverPicksUltra() {
        // Ultra shares Very clear's transparency (1.0), so a stored 1.0 must resolve to Very clear.
        assertEquals(GlassStops.indexOfFirst { it.name == "Very clear" }, nearestGlassStop(1f))
        assertTrue(!GlassStops[nearestGlassStop(0.95f)].ultra)
    }

    @Test
    fun legacySolidAndFreeValuesSnapToTheClosestStop() {
        // The old Solid (0%) stop is gone; a stored 0 lands on the most frosted one.
        assertEquals(0, nearestGlassStop(0f))
        assertEquals(GlassStops.indexOfFirst { it.name == "Medium" }, nearestGlassStop(0.7f))
    }
}
