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
        // Pill centred, top at 1800. With a 400px keyboard and 48px nav, the content bottom is
        // 2000-400-48 = 1552, so the lift is negative -> coerced to 0 + two gaps... make the
        // pill high enough to be positive.
        val c = toastClearance(
            searchRect = Rect(440f, 1200f, 560f, 1252f),
            windowWidthPx = window,
            windowHeightPx = height,
            imeBottomPx = 400f,
            navBottomPx = 48f,
            baseEdgePx = edge,
            gapPx = gap,
        )
        // content bottom = 1552; pill top = 1200 -> 352 + 2*gap.
        assertEquals(352f + gap + gap, c.bottomLiftPx, 0.001f)
        assertEquals(0f, c.startInsetPx, 0.001f)
        assertEquals(0f, c.endInsetPx, 0.001f)
    }

    @Test
    fun `lift never goes negative`() {
        // Pill BELOW the content bottom (keyboard up under it): lift floors at two gaps.
        val c = toastClearance(
            searchRect = Rect(440f, 1900f, 560f, 1952f),
            windowWidthPx = window,
            windowHeightPx = height,
            imeBottomPx = 400f,
            navBottomPx = 48f,
            baseEdgePx = edge,
            gapPx = gap,
        )
        assertEquals(gap + gap, c.bottomLiftPx, 0.001f)
    }
}
