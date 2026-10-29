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

    @Test fun anOrdinaryCardMove_isSoftButNotBlurry() {
        // A card moving at about 2500 px/s reads as a soft smear; a slow drift stays clear.
        assertTrue(motionBlurRadiusDp(2500f) >= 2f, "a 2500 px/s move blurs by at least 2dp (was ${motionBlurRadiusDp(2500f)})")
        assertEquals(0f, motionBlurRadiusDp(600f))
    }

    @Test fun aFastFlick_isCappedAtTheMaximum() {
        assertEquals(3f, motionBlurRadiusDp(10_000f))
    }

    @Test fun theRadiusRisesWithSpeed() {
        assertTrue(motionBlurRadiusDp(1000f) < motionBlurRadiusDp(2000f))
        assertTrue(motionBlurRadiusDp(2000f) < motionBlurRadiusDp(2500f))
    }
}
