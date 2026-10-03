package com.bloo.uicommon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared edge-overscroll physics: the resistance curve both the slider's
 * ends and the segmented control's first/last option use. It must be
 * monotonic, bounded by its budget, and start near 1:1 so the first pixel past
 * the edge feels connected to the finger.
 */
class EdgeBounceTest {

    @Test
    fun `zero or negative pull gives no overscroll`() {
        assertEquals(0f, rubberBand(0f, 10f), 0.0001f)
        assertEquals(0f, rubberBand(-5f, 10f), 0.0001f)
        assertEquals(0f, rubberBand(5f, 0f), 0.0001f)
    }

    @Test
    fun `overscroll is bounded by the budget`() {
        for (pull in listOf(1f, 10f, 100f, 10_000f)) {
            val out = rubberBand(pull, 10f)
            assertTrue("$pull -> $out must be < budget", out < 10f)
            assertTrue(out >= 0f)
        }
    }

    @Test
    fun `overscroll is monotonic in the pull`() {
        var prev = -1f
        for (pull in 0..500 step 5) {
            val out = rubberBand(pull.toFloat(), 10f)
            assertTrue("pull $pull overscrolled backwards", out >= prev)
            prev = out
        }
    }

    @Test
    fun `first pull moves close to one-to-one`() {
        // A tiny pull past the edge should track the finger almost exactly, so the
        // stretch feels connected rather than detached from the touch.
        val out = rubberBand(0.1f, 10f)
        assertTrue("first 0.1 of pull should be > 0.09 of movement", out > 0.09f)
    }
}
