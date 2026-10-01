package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins for how a group of buttons breaks into lines: as many as fit, then evened out. */
class BalancedLineBreaksTest {
    private fun lines(widths: IntArray, gap: Int, max: Int) =
        balancedLineBreaks(widths, gap, max).map { it.toList() }

    @Test
    fun everythingThatFitsStaysOnOneLine() {
        assertEquals(listOf(listOf(0, 1, 2)), lines(intArrayOf(100, 100, 100), 10, 400))
    }

    @Test
    fun anEmptyGroupHasNoLines() {
        assertEquals(emptyList(), lines(intArrayOf(), 10, 400))
    }

    @Test
    fun anUnboundedWidthIsOneLine() {
        assertEquals(listOf(listOf(0, 1, 2, 3)), lines(intArrayOf(300, 300, 300, 300), 10, Int.MAX_VALUE))
    }

    @Test
    fun aStrayButtonIsPulledBackToEvenOutTheLines() {
        // Greedy would give 3 + 1; the same two lines balanced are 2 + 2.
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), lines(intArrayOf(100, 100, 100, 100), 10, 330))
    }

    @Test
    fun theNumberOfLinesNeverGrows() {
        val greedy = lines(intArrayOf(120, 80, 150, 60, 90, 110), 8, 300).size
        assertEquals(greedy, balancedLineBreaks(intArrayOf(120, 80, 150, 60, 90, 110), 8, 300).size)
    }

    @Test
    fun orderIsPreserved() {
        val flat = lines(intArrayOf(120, 80, 150, 60, 90, 110), 8, 300).flatten()
        assertEquals(listOf(0, 1, 2, 3, 4, 5), flat)
    }

    @Test
    fun noLineIsWiderThanTheGroup() {
        val widths = intArrayOf(120, 80, 150, 60, 90, 110)
        balancedLineBreaks(widths, 8, 300).forEach { line ->
            val used = line.sumOf { widths[it] } + 8 * (line.size - 1)
            assertEquals(true, used <= 300, "line $line is $used wide")
        }
    }

    @Test
    fun aButtonWiderThanTheGroupGetsALineToItself() {
        assertEquals(listOf(listOf(0), listOf(1, 2)), lines(intArrayOf(500, 100, 100), 10, 300))
    }
}
