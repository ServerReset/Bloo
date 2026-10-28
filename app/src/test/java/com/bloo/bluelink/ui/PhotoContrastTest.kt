package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The hero's text colour follows the photo behind it: light photo, dark text; dark photo, light text. The photo itself is never changed. */
class PhotoContrastTest {
    private fun band(mean: Float, spread: Float = 0f) = BandLuma(mean, (mean - spread / 2).coerceAtLeast(0f), (mean + spread / 2).coerceAtMost(1f))

    @Test fun aLightPhotoGetsDarkText() {
        val o = overlayFor(band(0.85f))
        assertTrue(o.darkText)
    }

    @Test fun aDarkPhotoGetsLightText() {
        val o = overlayFor(band(0.03f))
        assertFalse(o.darkText)
    }




    @Test fun aMidGreyPhotoGetsWhicheverTextContrastsMore() {
        // No scrim: the photo is left alone, and the text is the one with the higher contrast against its brightness.
        assertTrue(overlayFor(band(0.20f)).darkText)
        assertFalse(overlayFor(band(0.15f)).darkText)
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



    @Test fun aMorphBlendsBetweenTwoBackdrops() {
        val mid = lerpBand(BandLuma(1f, 1f, 1f), BandLuma(0f, 0f, 0f), 0.25f)
        assertEquals(0.75f, mid.mean, 0.0001f)
    }
}
