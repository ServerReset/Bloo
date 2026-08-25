package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct JVM pins for the pure wrap-pager arithmetic in [WrapPager.kt].
 *
 * These two functions were extracted out of [WrapPagerState] precisely so
 * they could be tested as numbers instead of through a live Compose
 * [androidx.compose.foundation.pager.PagerState]: the pager needs a real
 * composition to exist, but every bug in this family was a math bug -- a
 * modulo that could go negative, a clamp that let an off-by-one page read a
 * non-existent item, a delta computed from the virtual index instead of the
 * real one. All of those are deterministic and cheap to pin here.
 */
class WrapPagerMathTest {

    // --- wrapRealIndex ------------------------------------------------------

    @Test
    fun singleRealItem_alwaysZero() {
        // realCount <= 1: there is one item; every virtual page shows it.
        assertEquals(0, wrapRealIndex(0, 1))
        assertEquals(0, wrapRealIndex(1499, 1))
        assertEquals(0, wrapRealIndex(-7, 1))
        // Degenerate case a car list can actually hit (one car in the garage).
        assertEquals(0, wrapRealIndex(0, 0))
    }

    @Test
    fun identityAtReducedPage() {
        // Virtual page n with n < realCount maps to itself.
        assertEquals(0, wrapRealIndex(0, 3))
        assertEquals(2, wrapRealIndex(2, 3))
    }

    @Test
    fun wrapsForwardAtVirtualCountBoundary() {
        // page == realCount is the first copy of item 0 in the next lap.
        assertEquals(0, wrapRealIndex(3, 3))
        assertEquals(1, wrapRealIndex(10, 3))
    }

    @Test
    fun wrapsNegativePagesWithoutGoingNegative() {
        // A page below zero (wrap pushed past the virtual midpoint by a
        // snapshot/offset quirk) must land in [0, realCount), NOT go negative
        // -- a negative real index would read a non-existent item.
        assertEquals(2, wrapRealIndex(-1, 3))
        assertEquals(1, wrapRealIndex(-2, 3))
        assertEquals(0, wrapRealIndex(-3, 3))
        assertEquals(2, wrapRealIndex(-4, 3))
    }

    @Test
    fun twoItemsAlternate() {
        assertEquals(0, wrapRealIndex(0, 2))
        assertEquals(1, wrapRealIndex(1, 2))
        assertEquals(0, wrapRealIndex(2, 2))
        assertEquals(1, wrapRealIndex(11, 2))
        assertEquals(1, wrapRealIndex(-1, 2))
    }

    // --- wrapPageToward -----------------------------------------------------

    @Test
    fun noWrapWhenSingleItem() {
        assertEquals(7, wrapPageToward(7, 10, 1, 0))
    }

    @Test
    fun alreadyThere_returnsCurrentPage() {
        // currentPage 5 is real index 2 (realCount 3); asking for real 2 must
        // not jump anywhere.
        assertEquals(5, wrapPageToward(5, 10, 3, 2))
    }

    @Test
    fun shortestRealStepFromAnyVirtualCopy() {
        // currentPage 8 -> real 2; target real 0 => delta -2, page 6. NOT
        // the long way around through a full virtual lap.
        assertEquals(6, wrapPageToward(8, 10, 3, 0))
        // One step forward: real 2 -> real 1 => delta -1, page 7.
        assertEquals(7, wrapPageToward(8, 10, 3, 1))
    }

    @Test
    fun coherentBoundaryClamping() {
        // pageCount guards the virtual end: a delta that would push past
        // pageCount-1 clamps, never wraps the VIRTUAL range (only the real
        // one can wrap). Page 9 is real 0; target 1 => delta +1 => 10,
        // clamped to pageCount-1 = 9.
        assertEquals(9, wrapPageToward(9, 10, 3, 1))
    }

    @Test
    fun targetClampedToRealRange() {
        // A target beyond realCount-1 clamps to the last real item: real(5)=1,
        // t clamps to 1, delta stays 0.
        assertEquals(5, wrapPageToward(5, 8, 2, 99))
        // Page 4 is real 0; target 99 clamps to real 1 => delta +1 => page 5.
        assertEquals(5, wrapPageToward(4, 8, 2, 99))
    }

    @Test
    fun neverExceedsVirtualPageBounds() {
        // Fuzz-lite: for a spread of start/target combos the result must
        // always stay inside the pager's virtual range AND match the target's
        // real index.
        for (realCount in intArrayOf(1, 2, 3, 5)) {
            val pageCount = realCount * 1000
            for (currentPage in intArrayOf(0, pageCount / 2, pageCount / 2 - 1, pageCount - 1)) {
                for (target in intArrayOf(0, realCount - 1, realCount, -1)) {
                    val p = wrapPageToward(currentPage, pageCount, realCount, target)
                    assertTrue(p in 0..pageCount - 1, "page $p out of range")
                    if (realCount > 1) {
                        assertEquals(
                            target.coerceIn(0, realCount - 1),
                            wrapRealIndex(p, realCount),
                            "page $p for target $target (from $currentPage) maps wrong",
                        )
                    }
                }
            }
        }
    }
}
