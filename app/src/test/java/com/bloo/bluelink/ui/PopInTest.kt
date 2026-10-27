package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A group pops in as a ripple: nothing shows before its turn, later slots lag earlier ones, and everything has landed when the clock does. */
class PopInTest {
    @Test fun nothingHasPoppedBeforeTheClockStarts() {
        for (slot in 0 until 8) assertEquals(0f, popProgress(0f, slot, 8))
    }

    @Test fun everythingHasLandedWhenTheClockIsDone() {
        for (slot in 0 until 8) assertEquals(1f, popProgress(1f, slot, 8), 0.0001f)
    }

    @Test fun laterSlotsLagEarlierOnes() {
        val mid = 0.5f
        for (slot in 1 until 8) assertTrue(popProgress(mid, slot, 8) <= popProgress(mid, slot - 1, 8), "slot $slot is no further on than slot ${slot - 1}")
        assertTrue(popProgress(mid, 0, 8) > popProgress(mid, 7, 8), "the first is well ahead of the last part-way through")
    }

    @Test fun anElementPopsMonotonicallyAsTheClockRuns() {
        var last = 0f
        for (i in 0..20) {
            val p = popProgress(i / 20f, 3, 8)
            assertTrue(p >= last, "never goes back")
            last = p
        }
    }

    @Test fun aSingleElementStartsAtOnce() {
        assertEquals(1f, popProgress(popProgress(0.4f, 0, 1), 0, 1) .coerceAtLeast(1f), 0.0001f)
        assertTrue(popProgress(0.2f, 0, 1) > 0f)
    }
}
