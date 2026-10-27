package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pebble edge's sheen: always present, slides with the way the phone is held, and swells while it moves. */
class EdgeGlintTest {
    @Test fun motionIsZeroWhenHeldStillAndCapsAtOne() {
        assertEquals(0f, glintStrength(0f, 0f))
        assertTrue(glintStrength(0.03f, 0f) < glintStrength(0.06f, 0f))
        assertEquals(1f, glintStrength(TiltMax, TiltMax))
    }

    @Test fun theSheenIsVisibleAtRestAndSwellsWithMotion() {
        assertTrue(sheenStrength(0f) > 0.4f, "a held phone still shows a sheen")
        assertTrue(sheenStrength(1f) > sheenStrength(0f))
        assertEquals(1f, sheenStrength(5f), 0.0001f)
    }

    @Test fun theSheenCentresWhenLevelAndSlidesWithTheLean() {
        assertEquals(0.5f, sheenCentre(0f, 0f), 0.0001f)
        assertTrue(sheenCentre(0.8f, 0f) > 0.5f, "leaning right moves it toward the right end")
        assertTrue(sheenCentre(-0.8f, 0f) < 0.5f, "leaning left moves it toward the left end")
    }

    @Test fun theSheenReachesBothEndsSoTheWholeFaceGetsATurn() {
        assertTrue(sheenCentre(-1f, 0f) < 0.15f, "a full lean left puts it at the left end, not stuck two-thirds across")
        assertTrue(sheenCentre(1f, 0f) > 0.85f, "a full lean right puts it at the right end")
    }

    @Test fun theSheenLeavesThePebbleButNotByMuch() {
        for (t in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
            assertTrue(sheenCentre(t, t) in -0.2f..1.2f)
            assertTrue(sheenCentre(t, -t) in -0.2f..1.2f)
        }
    }
}
