package com.bloo.bluelink.ui

import androidx.compose.runtime.State
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A car's reflow into two columns follows the column count as it eases, with no switch to fall out of step with it. */
class ColumnReflowTest {
    private class Cell(initial: Float) : State<Float> {
        var current = initial
        override val value: Float get() = current
    }

    @Test fun aWideWindowShowsTwoColumnsOnceTheColumnsAreWideEnough() {
        val columns = Cell(1f)
        val reflow = ColumnReflow(columns, widthDp = { 900f })
        assertEquals(1f, reflow.value, 0.0001f)
        columns.current = 2f
        assertEquals(0f, reflow.value, 0.0001f, "two cars across 900dp leave 450dp each: still one column")
    }

    @Test fun aNarrowWindowNeverReflows() {
        val columns = Cell(1f)
        val reflow = ColumnReflow(columns, widthDp = { 500f })
        assertEquals(0f, reflow.value, 0.0001f)
    }

    @Test fun theReflowFollowsTheColumnCountSmoothlyAsItEases() {
        // A car widening to full screen: the column count eases from two to one over the same frames as the reflow.
        val columns = Cell(2f)
        val reflow = ColumnReflow(columns, widthDp = { 900f })
        var last = reflow.value
        var largestStep = 0f
        for (i in 1..100) {
            columns.current = 2f - i / 100f
            val now = reflow.value
            assertTrue(now >= last - 0.0001f, "never goes back as the car widens ($now < $last)")
            largestStep = maxOf(largestStep, abs(now - last))
            last = now
        }
        assertEquals(1f, last, 0.0001f)
        assertTrue(largestStep <= 0.25f, "no single step jumps the whole reflow (largest $largestStep)")
    }

    @Test fun theColumnCountNeverDropsBelowOne() {
        val reflow = ColumnReflow(Cell(0f), widthDp = { 900f })
        assertEquals(1f, reflow.value, 0.0001f)
    }
}
