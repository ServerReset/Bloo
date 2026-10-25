package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The hero's text colour follows the photo behind it: light photo, dark text; dark photo, light text; a gradient is judged by its worst part. */
class PhotoContrastTest {
    private fun band(mean: Float, spread: Float = 0f) = BandLuma(mean, (mean - spread / 2).coerceAtLeast(0f), (mean + spread / 2).coerceAtMost(1f))

    @Test fun aLightPhotoGetsDarkTextAndNoScrim() {
        val o = overlayFor(band(0.85f))
        assertTrue(o.darkText)
        assertEquals(0f, o.scrimAlpha)
    }

    @Test fun aDarkPhotoGetsLightTextAndNoScrim() {
        val o = overlayFor(band(0.03f))
        assertFalse(o.darkText)
        assertEquals(0f, o.scrimAlpha)
    }

    @Test fun aMidTonePhotoGetsTextAndAScrimInTheOppositeTone() {
        val o = overlayFor(band(0.20f))
        assertTrue(o.scrimAlpha > 0f, "mid-grey needs help")
        assertEquals(if (o.darkText) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black, o.scrimTone)
    }

    @Test fun aGradientIsJudgedByItsWorstPartNotItsAverage() {
        // Dark on one side, light on the other: the average is mid-grey, but neither text clears both ends unaided.
        val o = overlayFor(BandLuma(mean = 0.45f, low = 0.02f, high = 0.90f))
        assertTrue(o.scrimAlpha > 0.3f, "a wide spread needs a real scrim, got ${o.scrimAlpha}")
        // Whatever it chose, the scrimmed worst part now clears AA against the chosen text.
        val worst = if (o.darkText) 0.02f + (1f - 0.02f) * o.scrimAlpha else 0.90f * (1f - o.scrimAlpha)
        val text = if (o.darkText) relativeLuminance(0x1A, 0x1B, 0x20) else relativeLuminance(0xF7, 0xF7, 0xFA)
        assertTrue(contrastRatio(text, worst) >= 4.5f, "contrast ${contrastRatio(text, worst)}")
    }

    @Test fun theScrimNeverExceedsItsCap() {
        assertTrue(overlayFor(BandLuma(0.5f, 0f, 1f)).scrimAlpha <= 0.8f)
    }

    @Test fun luminanceMatchesTheKnownEndpoints() {
        assertEquals(0f, relativeLuminance(0, 0, 0), 0.0001f)
        assertEquals(1f, relativeLuminance(255, 255, 255), 0.0001f)
        assertEquals(21f, contrastRatio(1f, 0f), 0.001f)
    }

    @Test fun bandsAreMeasuredWhereTheTextSits() {
        // 10 wide x 10 tall: white top, black bottom.
        val w = 10; val h = 10
        val pixels = IntArray(w * h) { i -> if (i / w < 5) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val tone = heroToneOf(pixels, w, h)
        assertTrue(assertNotNull(tone.top, "top").darkText, "white at the top wants dark text")
        assertFalse(assertNotNull(tone.bottom, "bottom").darkText, "black at the bottom wants light text")
    }

    @Test fun aMostlyTransparentBandIsNotMeasured() {
        val w = 10; val h = 10
        val pixels = IntArray(w * h) { 0x00FFFFFF }
        val tone = heroToneOf(pixels, w, h)
        assertNull(tone.top)
        assertNull(tone.bottom)
    }
}
