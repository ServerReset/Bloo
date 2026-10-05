package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassStopsTest {
    @Test
    fun ultraIsTheLastStopAndTheOnlyUltraOne() {
        assertTrue(GlassStops.last().ultra)
        assertEquals(1, GlassStops.count { it.ultra })
    }

    @Test
    fun nearestStopNeverPicksUltra() {
        // Ultra shares Crystal's transparency (1.0), so a stored 1.0 must resolve to Crystal.
        val crystal = GlassStops.indexOfFirst { it.name == "Crystal" }
        assertEquals(crystal, nearestGlassStop(1f))
        assertTrue(!GlassStops[nearestGlassStop(0.9f)].ultra)
    }

    @Test
    fun legacyFreeValuesSnapToTheClosestStop() {
        assertEquals(0, nearestGlassStop(0.02f))
        assertEquals(GlassStops.indexOfFirst { it.name == "Misted" }, nearestGlassStop(0.7f))
    }
}
