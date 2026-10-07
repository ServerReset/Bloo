package com.bloo.bluelink.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure search/toast negotiation geometry ([toastClearance]). The bug this guards against
 * was real and shipped twice: the corner math compared the pill's raw ROOT edges against the
 * toast's own base edge, so a pill parked near a side produced a NEGATIVE inset that got
 * coerced to zero -- the toast never shortened and simply covered the pill.
 */
class ToastClearanceTest {
    private val window = 1000f
    private val height = 2000f
    private val edge = 16f
    private val gap = 8f

    private fun calc(rect: Rect) = toastClearance(
        searchRect = rect,
        windowWidthPx = window,
        windowHeightPx = height,
        imeBottomPx = 0f,
        navBottomPx = 0f,
        baseEdgePx = edge,
        gapPx = gap,
    )

    @Test
    fun `no search rect means no clearance`() {
        val c = toastClearance(null, window, height, 0f, 0f, edge, gap)
        assertEquals(0f, c.startInsetPx, 0.001f)
        assertEquals(0f, c.endInsetPx, 0.001f)
        assertEquals(0f, c.bottomLiftPx, 0.001f)
    }

    @Test
    fun `left-docked pill insets the start`() {
        // Pill hugging the left edge: its right edge ~ 56. Toast starts just right of it.
        val c = calc(Rect(16f, 1800f, 56f, 1852f))
        assertEquals((56f + gap - edge), c.startInsetPx, 0.001f)
        assertEquals(0f, c.endInsetPx, 0.001f)
        assertEquals(0f, c.bottomLiftPx, 0.001f)
    }

    @Test
    fun `right-docked pill insets the end`() {
        // Pill hugging the right edge: left ~ 944. Toast ends just left of it.
        val c = calc(Rect(944f, 1800f, 984f, 1852f))
        assertEquals(0f, c.startInsetPx, 0.001f)
        assertEquals(((window - edge) - (944f - gap)), c.endInsetPx, 0.001f)
        assertEquals(0f, c.bottomLiftPx, 0.001f)
    }

    @Test
    fun `centred pill lifts the stack above it, excluding ime and nav`() {
        // Pill centred, top at 1200. The stack's own content bottom is window minus max(nav, ime)
        // minus the edge: 2000 - 400 - 16 = 1584, so the lift is 1584 - 1200 + gap.
        val c = toastClearance(
            searchRect = Rect(440f, 1200f, 560f, 1252f),
            windowWidthPx = window,
            windowHeightPx = height,
            imeBottomPx = 400f,
            navBottomPx = 48f,
            baseEdgePx = edge,
            gapPx = gap,
        )
        assertEquals((height - 400f - edge - 1200f) + gap, c.bottomLiftPx, 0.001f)
        assertEquals(0f, c.startInsetPx, 0.001f)
        assertEquals(0f, c.endInsetPx, 0.001f)
    }

    @Test
    fun `lift never goes negative`() {
        // Pill BELOW the toast content bottom (keyboard up under it): lift floors at zero.
        val c = toastClearance(
            searchRect = Rect(440f, 1900f, 560f, 1952f),
            windowWidthPx = window,
            windowHeightPx = height,
            imeBottomPx = 400f,
            navBottomPx = 48f,
            baseEdgePx = edge,
            gapPx = gap,
        )
        assertEquals(0f, c.bottomLiftPx, 0.001f)
    }

    @Test
    fun `a docked search beside a too-narrow toast lifts instead of squeezing`() {
        // Pill hugging the left edge, but the window is so narrow the beside toast cannot fit.
        val c = toastClearance(
            searchRect = Rect(16f, 1800f, 300f, 1852f),
            windowWidthPx = 360f,
            windowHeightPx = height,
            imeBottomPx = 0f,
            navBottomPx = 0f,
            baseEdgePx = edge,
            gapPx = gap,
            minToastWidthPx = 220f,
        )
        assertEquals(0f, c.startInsetPx, 0.001f)
        assertEquals(0f, c.endInsetPx, 0.001f)
        kotlin.test.assertTrue(c.bottomLiftPx > 0f, "a squeezed beside toast lifts instead")
    }

    @Test
    fun `the message's own width, not a fixed minimum, decides beside vs above`() {
        // One docked search, two messages: a short one slots beside it, a long one lifts above.
        val rect = Rect(16f, 1800f, 56f, 1852f)
        val short = toastClearance(rect, window, height, 0f, 0f, edge, gap, minToastWidthPx = 400f)
        val long = toastClearance(rect, window, height, 0f, 0f, edge, gap, minToastWidthPx = 1000f)
        kotlin.test.assertTrue(short.startInsetPx > 0f && short.bottomLiftPx == 0f, "a short message slots beside")
        kotlin.test.assertTrue(long.bottomLiftPx > 0f, "a long message lifts above")
    }
}
