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
        assertTrue(o.scrimAlpha > 0.2f, "a wide spread needs a real scrim, got ${o.scrimAlpha}")
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

    // ---- The hierarchy: text colours made readable on whatever is behind them ----

    private fun contrastOn(c: androidx.compose.ui.graphics.Color, bg: BandLuma) = worstContrast(relativeLuminanceOf(c), bg)
    private fun relativeLuminanceOf(c: androidx.compose.ui.graphics.Color) =
        relativeLuminance((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    @Test fun aColourThatAlreadyClearsItsBackdropIsLeftAlone() {
        val dark = androidx.compose.ui.graphics.Color(0xFF1A1B20)
        assertEquals(dark, dark.readableOn(BandLuma(0.9f, 0.85f, 0.95f)))
    }

    @Test fun aColourThatFailsIsNudgedUntilItClearsAA() {
        // Mid green on a pale band: unreadable as is, readable after.
        val green = androidx.compose.ui.graphics.Color(0xFF4CAF50)
        val bg = BandLuma(0.8f, 0.75f, 0.85f)
        assertTrue(contrastOn(green, bg) < 4.5f)
        val fixed = green.readableOn(bg)
        assertTrue(contrastOn(fixed, bg) >= 4.4f, "contrast ${contrastOn(fixed, bg)}")
        // Still green-ish: the green channel stays the strongest.
        assertTrue(fixed.green >= fixed.red && fixed.green >= fixed.blue, "kept its hue: $fixed")
    }

    @Test fun lightTextOnAPalePhotoGoesDark_andDarkTextOnADarkPhotoGoesLight() {
        val white = androidx.compose.ui.graphics.Color.White
        assertTrue(contrastOn(white.readableOn(BandLuma(0.85f, 0.8f, 0.9f)), BandLuma(0.85f, 0.8f, 0.9f)) >= 4.5f)
        val black = androidx.compose.ui.graphics.Color.Black
        assertTrue(contrastOn(black.readableOn(BandLuma(0.02f, 0.0f, 0.04f)), BandLuma(0.02f, 0.0f, 0.04f)) >= 4.5f)
    }

    @Test fun aBackdropThatSpansDarkAndLightTakesTheBestAvailableExtreme() {
        // Nothing reaches AA across 0.02..0.95; the answer is an extreme, never a grey in the middle.
        val bg = BandLuma(0.4f, 0.02f, 0.95f)
        val out = androidx.compose.ui.graphics.Color(0xFF808080).readableOn(bg)
        assertTrue(out == androidx.compose.ui.graphics.Color.White || out == androidx.compose.ui.graphics.Color.Black, "got $out")
    }

    @Test fun aTextColourInsideTheBandIsTreatedAsUnreadable() {
        assertEquals(1f, worstContrast(0.5f, BandLuma(0.5f, 0.3f, 0.7f)))
    }

    @Test fun aScrimMovesTheBandItSitsOver() {
        val under = BandLuma(0.2f, 0.1f, 0.3f).under(scrimLight = true, alpha = 0.5f)
        assertEquals(0.6f, under.mean, 0.0001f)
        val sunk = BandLuma(0.8f, 0.7f, 0.9f).under(scrimLight = false, alpha = 0.5f)
        assertEquals(0.4f, sunk.mean, 0.0001f)
    }

    @Test fun theOverlayRemembersWhatIsBehindItsText() {
        val o = overlayFor(BandLuma(0.45f, 0.02f, 0.90f))
        // Behind the (scrimmed) text, the worst part now clears AA for the text it chose.
        val text = if (o.darkText) relativeLuminance(0x1A, 0x1B, 0x20) else relativeLuminance(0xF7, 0xF7, 0xFA)
        assertTrue(worstContrast(text, o.behind) >= 4.5f, "worst ${worstContrast(text, o.behind)}")
    }

    @Test fun aMorphBlendsBetweenTwoBackdrops() {
        val mid = lerpBand(BandLuma(1f, 1f, 1f), BandLuma(0f, 0f, 0f), 0.25f)
        assertEquals(0.75f, mid.mean, 0.0001f)
    }
}
