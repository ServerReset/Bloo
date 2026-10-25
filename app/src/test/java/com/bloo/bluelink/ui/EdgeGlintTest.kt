package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pebble edge's tilt glint: absent when held still, brighter the harder the tilt, and slides toward the side the phone leans to. */
class EdgeGlintTest {
    @Test fun heldStillHasNoGlint() {
        assertEquals(0f, glintStrength(0f, 0f))
        assertTrue(glintStrength(0.001f, 0.001f) < 0.04f, "a hair of tilt stays below the draw threshold")
    }

    @Test fun strengthRisesWithTiltAndCapsAtOne() {
        assertTrue(glintStrength(0.03f, 0f) < glintStrength(0.06f, 0f))
        assertEquals(1f, glintStrength(TiltMax, TiltMax))
    }

    @Test fun theGlintCentresWhenLevelAndSlidesWithTheLean() {
        assertEquals(0.5f, glintCentre(0f, 0f), 0.0001f)
        assertTrue(glintCentre(0.04f, 0.04f) > 0.5f, "leaning right/down moves it toward the bottom-right")
        assertTrue(glintCentre(-0.04f, -0.04f) < 0.5f, "leaning left/up moves it toward the top-left")
    }

    @Test fun theGlintNeverLeavesTheRim() {
        for (t in listOf(-TiltMax, -0.03f, 0f, 0.03f, TiltMax)) {
            val c = glintCentre(t, t)
            assertTrue(c in 0f..1f, "centre $c")
        }
    }
}
