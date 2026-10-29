package com.bloo.uicommon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The blur strength a moving layer gets: still is clear, an ordinary slide is visibly soft, a fast flick is at the cap. */
class MotionBlurRadiusTest {
    @Test fun aStillLayer_isNotBlurred() {
        assertEquals(0f, motionBlurRadiusDp(0f))
    }

    @Test fun aSlowDrift_belowTheVisibleThreshold_isNotBlurred() {
        assertEquals(0f, motionBlurRadiusDp(100f))
    }

    @Test fun anOrdinarySlide_isVisiblySoft() {
        // A pebble sliding or growing at about 1000 px/s should read as a few dp of softness, not 1dp.
        assertTrue("a 1000 px/s slide blurs by at least 3dp (was ${motionBlurRadiusDp(1000f)})", motionBlurRadiusDp(1000f) >= 3f)
    }

    @Test fun aFastFlick_isCappedAtTheMaximum() {
        assertEquals(6f, motionBlurRadiusDp(10_000f))
    }

    @Test fun theRadiusRisesWithSpeed() {
        assertTrue(motionBlurRadiusDp(800f) < motionBlurRadiusDp(1200f))
        assertTrue(motionBlurRadiusDp(1200f) < motionBlurRadiusDp(1500f))
    }
}
