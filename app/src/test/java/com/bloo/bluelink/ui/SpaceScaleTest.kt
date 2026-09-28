package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the display-scale → gap-scale mapping. The gap tokens are all multiples of a single base
 * unit scaled by [spaceScaleFor], so this one function moves the whole app's spacing; a sign
 * flip or a wrong damping factor would silently scale every gap the wrong way.
 */
class SpaceScaleTest {

    @Test
    fun unityScaleIsUnscaled() {
        assertEquals(1f, spaceScaleFor(1f))
    }

    @Test
    fun scalesAtHalfTheDisplaySwing() {
        // The app's uiScale range is 0.85..1.3; gaps follow at half the swing.
        assertEquals(1.15f, spaceScaleFor(1.3f), 1e-6f)
        assertEquals(0.925f, spaceScaleFor(0.85f), 1e-6f)
    }

    @Test
    fun monotonicAndBoundedByTheDisplayScale() {
        var prev = spaceScaleFor(0.5f)
        var u = 0.55f
        while (u <= 1.6f) {
            val s = spaceScaleFor(u)
            assertTrue(s >= prev, "not monotonic at $u")
            // Gaps never scale MORE than the display scale itself (damped, so strictly less
            // unless equal), and never move opposite to it.
            assertTrue(kotlin.math.abs(s - 1f) <= kotlin.math.abs(u - 1f) + 1e-6f, "over-scaled at $u")
            prev = s
            u += 0.05f
        }
    }
}
