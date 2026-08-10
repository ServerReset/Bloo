package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-JVM tests for [activeDotIndex], the watch page-indicator index->lit-dot mapping extracted
 * from CurvedDots. The mapping is off-by-one-prone (the `count - 1` divisor, the endpoint
 * rounding), so these pin the two properties that matter: 1:1 when the dots aren't compressed,
 * and first-item->first-dot / last-item->last-dot when they are.
 */
class ActiveDotIndexTest {

    @Test
    fun oneToOneWhenNotCompressed() {
        // count <= shown: the item index is its own dot (clamped).
        for (i in 0 until 5) assertEquals(i, activeDotIndex(count = 5, shown = 7, index = i))
        assertEquals(4, activeDotIndex(count = 5, shown = 5, index = 4))
    }

    @Test
    fun clampsOutOfRangeIndex() {
        // The not-compressed branch clamps to shown-1 (the dot range), not count-1: an index
        // past the end lights the last drawn dot. With count=5 <= shown=7 that's dot 6.
        assertEquals(0, activeDotIndex(count = 5, shown = 7, index = -3))
        assertEquals(6, activeDotIndex(count = 5, shown = 7, index = 99))
    }

    @Test
    fun compressedEndpointsMapToFirstAndLastDot() {
        // 20 items onto 5 dots: item 0 -> dot 0, item 19 -> dot 4, always.
        assertEquals(0, activeDotIndex(count = 20, shown = 5, index = 0))
        assertEquals(4, activeDotIndex(count = 20, shown = 5, index = 19))
    }

    @Test
    fun compressedMiddleRescalesAndRounds() {
        // 20 items onto 5 dots: dot = round(index/19 * 4).
        // index 9 -> round(9/19*4)=round(1.89)=2; index 10 -> round(10/19*4)=round(2.10)=2.
        assertEquals(2, activeDotIndex(count = 20, shown = 5, index = 9))
        assertEquals(2, activeDotIndex(count = 20, shown = 5, index = 10))
        // A monotonic sweep never decreases and never exceeds the last dot.
        var prev = 0
        for (i in 0 until 20) {
            val d = activeDotIndex(count = 20, shown = 5, index = i)
            assert(d in prev..4) { "dot $d for index $i out of range/monotonicity (prev=$prev)" }
            prev = d
        }
    }

    @Test
    fun degenerateCountsReturnZeroSafely() {
        // count/shown <= 1 must not divide by (count-1)==0; caller draws nothing anyway.
        assertEquals(0, activeDotIndex(count = 1, shown = 1, index = 0))
        assertEquals(0, activeDotIndex(count = 1, shown = 5, index = 0))
        assertEquals(0, activeDotIndex(count = 5, shown = 1, index = 3))
        assertEquals(0, activeDotIndex(count = 0, shown = 0, index = 0))
    }
}
