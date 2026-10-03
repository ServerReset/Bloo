package com.bloo.uicommon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmentWeightsTest {
    @Test
    fun theChosenSegmentIsTheWidestAndTheRestAreEqual() {
        val w = segmentWeights(n = 3, pos = 1f, shown = 1f, ratio = 2f)
        assertEquals(listOf(1f, 2f, 1f), w.toList())
    }

    @Test
    fun nothingSelectedMeansEqualSegments() {
        assertEquals(listOf(1f, 1f, 1f), segmentWeights(3, 1f, 0f, 2f).toList())
    }

    @Test
    fun halfwayBetweenTwoSegmentsTheyShareTheStretchEvenly() {
        val w = segmentWeights(3, 0.5f, 1f, 2f)
        assertEquals(w[0], w[1], 1e-6f)
        assertTrue(w[2] == 1f)
    }

    @Test
    fun totalStretchStaysConstantWhileTravelling() {
        val totals = (0..20).map { segmentWeights(4, it / 20f * 3f, 1f, 1.6f).sum() }
        assertTrue(totals.max() - totals.min() < 1e-4f, "total weight must not jump: $totals")
    }
}
