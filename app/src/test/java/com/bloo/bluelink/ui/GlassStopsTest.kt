package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassStopsTest {
    @Test
    fun stopsRunFrostedToCrystalThenUltra() {
        assertEquals(listOf("Frosted", "Medium", "Soft clear", "Clear", "Crystal", "Ultra"), GlassStops.map { it.name })
    }

    @Test
    fun ultraIsTheLastStopAndTheOnlyUltraOne() {
        assertTrue(GlassStops.last().ultra)
        assertEquals(1, GlassStops.count { it.ultra })
    }

    @Test
    fun nearestStopNeverPicksUltra() {
        // Ultra shares Crystal's transparency (1.0), so a stored 1.0 must resolve to Crystal.
        assertEquals(GlassStops.indexOfFirst { it.name == "Crystal" }, nearestGlassStop(1f))
        assertTrue(!GlassStops[nearestGlassStop(0.95f)].ultra)
    }

    @Test
    fun softClearSitsHalfwayBetweenMediumAndClear() {
        val t = { n: String -> GlassStops.first { it.name == n }.transparency }
        assertEquals((t("Medium") + t("Clear")) / 2f, t("Soft clear"), 0.06f)
    }

    @Test
    fun legacySolidAndFreeValuesSnapToTheClosestStop() {
        // The old Solid (0%) stop is gone; a stored 0 lands on the most frosted one.
        assertEquals(0, nearestGlassStop(0f))
        assertEquals(GlassStops.indexOfFirst { it.name == "Medium" }, nearestGlassStop(0.7f))
    }
}
