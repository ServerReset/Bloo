package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure width math behind [ExpressiveButtonGroup]: the press redistribution must conserve a
 * line's total width, and the final rounding must land exactly on that total with no pixel drift.
 */
class ExpressiveButtonGroupMathTest {

    @Test
    fun seamReserveSizesEachHalfOffItsOwnContent() {
        val r = seamPressReserve(listOf(0, 1), intArrayOf(100, 200))
        assertEquals(15, r[0]) // 0.30 * 100 / 2
        assertEquals(30, r[1]) // 0.30 * 200 / 2
        assertEquals(2, r.size)
    }

    @Test
    fun pressRedistributionConservesTheLineTotal() {
        // b is stretched (130) above its content (100); pressing a takes only b's 30px slack.
        val exact = redistributeForPress(
            memberIdx = listOf(0, 1),
            base = doubleArrayOf(100.0, 130.0),
            basis = intArrayOf(100, 100),
            press = floatArrayOf(1f, 0f),
        )
        assertEquals(115.0, exact[0], 1e-3)
        assertEquals(115.0, exact[1], 1e-3)
        assertEquals(230.0, exact[0] + exact[1], 1e-3, "seam delta must be zero-sum")
    }

    @Test
    fun redistributeNeverSinksBelowContent() {
        // Pressing both, with b having no slack to give: content floors hold.
        val exact = redistributeForPress(
            memberIdx = listOf(0, 1),
            base = doubleArrayOf(100.0, 100.0),
            basis = intArrayOf(100, 100),
            press = floatArrayOf(1f, 1f),
        )
        assertTrue(exact[0] >= 100.0 && exact[1] >= 100.0)
    }

    @Test
    fun clampNeverExceedsTheBudget() {
        val exact = doubleArrayOf(100.0, 100.0)
        clampLineToBudget(listOf(0, 1), exact, intArrayOf(50, 50), total = 150)
        assertTrue(exact[0] + exact[1] <= 150.0 + 1e-6)
    }

    @Test
    fun largestRemainderSumsToTotalExactly() {
        val target = largestRemainderWidths(listOf(0, 1, 2), doubleArrayOf(10.4, 20.6, 9.0), total = 40)
        assertEquals(40, target.sum())
        // The two largest fractional parts (0.6 and 0.4) get the two leftover pixels.
        assertEquals(10, target[0])
        assertEquals(21, target[1])
        assertEquals(9, target[2])
    }
}
