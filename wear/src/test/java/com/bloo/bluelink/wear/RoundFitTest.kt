package com.bloo.bluelink.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The per-element fit to the round display: full size at the middle, narrowing toward the edges, with a floor. */
class RoundFitTest {
    private val display = 400f

    @Test fun `an element in the middle keeps its size`() {
        assertEquals(1f, roundFitScale(centreY = 200f, width = 200f, height = 40f, displayWidth = display, displayHeight = display, minScale = 0.55f))
    }

    @Test fun `a wide element narrows as it nears the top`() {
        val middle = roundFitScale(200f, 300f, 40f, display, display, 0.55f)
        val nearTop = roundFitScale(40f, 300f, 40f, display, display, 0.55f)
        assertTrue(nearTop < middle, "$nearTop vs $middle")
    }

    @Test fun `it never shrinks below the floor, even past the edge`() {
        assertEquals(0.55f, roundFitScale(-50f, 300f, 40f, display, display, 0.55f))
    }

    @Test fun `top and bottom are symmetric`() {
        val top = roundFitScale(50f, 250f, 40f, display, display, 0.55f)
        val bottom = roundFitScale(350f, 250f, 40f, display, display, 0.55f)
        assertEquals(top, bottom, 0.0001f)
    }

    @Test fun `an unmeasured element is left alone`() {
        assertEquals(1f, roundFitScale(100f, 0f, 40f, display, display, 0.55f))
    }
}
