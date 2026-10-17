package com.bloo.bluelink.wear

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The keypad has to FIT the face, and this is the check that it does.
 *
 * The version that shipped did not. The keys were a constant 48dp, which is 210dp over four rows on a
 * face that is 227dp at its widest -- before a title, the entry dots, an error line and the "which
 * PIN" line, which are another ~90dp. It overflowed by about a row, and the row it lost was del/0/ok:
 * the PIN could be typed and never confirmed.
 *
 * Nothing on a device would have said so. The top of the screen looked correct, and the part that fell
 * off the bottom was the part you only reach when you are finished. That is what makes it worth a test
 * rather than a look.
 */
class WearPinPadTest {

    /** Round Wear faces worth supporting, largest to smallest, in dp (454px @320dpi is 227dp). */
    private val faces = listOf(280, 260, 240, 233, 227, 210, 195, 180)

    private fun needed(maxHeight: Dp, sizing: PinPadSizing): Dp {
        val gaps = if (sizing.showCaption) 6 else 5
        return PIN_PAD_TEXT + (if (sizing.showCaption) PIN_PAD_CAPTION else 0.dp) +
            sizing.key * 4 + PIN_PAD_GAP * gaps
    }

    @Test
    fun `the keypad fits every face`() {
        for (h in faces) {
            val sizing = pinPadSizing(maxWidth = h.dp, maxHeight = h.dp)
            assertTrue(
                needed(h.dp, sizing) <= h.dp,
                "at ${h}dp the pad needs ${needed(h.dp, sizing)} (key ${sizing.key}, caption ${sizing.showCaption})",
            )
        }
    }

    @Test
    fun `a typical face keeps the explanation`() {
        // 227dp is the common Wear OS size, and "which PIN" is the one thing a first-time user cannot
        // work out for themselves.
        assertTrue(pinPadSizing(maxWidth = 227.dp, maxHeight = 227.dp).showCaption)
    }

    @Test
    fun `the keys stay pressable on the smallest face`() {
        val sizing = pinPadSizing(maxWidth = 180.dp, maxHeight = 180.dp)
        assertTrue(sizing.key.value >= 24f, "keys collapsed to ${sizing.key}")
    }

    @Test
    fun `a big face stops growing the keys`() {
        val big = pinPadSizing(maxWidth = 280.dp, maxHeight = 280.dp)
        assertTrue(big.showCaption)
        assertTrue(big.key.value <= 52f, "keys ran away to ${big.key}")
    }

    @Test
    fun `a narrow face is limited by its width`() {
        // Tall but narrow: three keys across can only ever be a third of the width, however much
        // height there is.
        val sizing = pinPadSizing(maxWidth = 150.dp, maxHeight = 400.dp)
        assertTrue(sizing.key.value <= (150f - 12f) / 3f + 0.01f, "keys overran the width: ${sizing.key}")
    }
}
