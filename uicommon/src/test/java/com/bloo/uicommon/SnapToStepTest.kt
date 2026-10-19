package com.bloo.uicommon

import kotlin.test.Test
import kotlin.test.assertEquals

/** How a slider quantizes a value: its ends, its steps, no steps, and a range with no span. */
class SnapToStepTest {
    private val range = 0f..10f

    @Test fun `no steps is a plain clamp`() {
        assertEquals(3.7f, snapToStep(3.7f, range, 0))
        assertEquals(10f, snapToStep(14f, range, 0))
        assertEquals(0f, snapToStep(-2f, range, 0))
    }

    @Test fun `one step has three positions`() {
        assertEquals(0f, snapToStep(2f, range, 1))
        assertEquals(5f, snapToStep(3f, range, 1))
        assertEquals(10f, snapToStep(8f, range, 1))
    }

    @Test fun `the ends are reachable and values past them clamp`() {
        assertEquals(0f, snapToStep(-5f, range, 3))
        assertEquals(10f, snapToStep(50f, range, 3))
    }

    @Test fun `a range with no span has one position and does not crash`() {
        assertEquals(4f, snapToStep(9f, 4f..4f, 2))
    }
}
