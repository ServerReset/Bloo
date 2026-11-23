package com.bloo.bluelink.ui

import androidx.compose.runtime.State
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A car moving between one and two columns is laid out between its two end layouts, never squashed narrower than either. */
class ColumnMotionTest {
    private class Cell(initial: Float) : State<Float> {
        var current = initial
        override val value: Float get() = current
    }

    private fun motion(columns: Cell, from: Float, to: Float, width: Float) = ColumnMotion(columns, Cell(from), Cell(to), widthDp = { width })

    @Test fun aWideWindowReflowsOnlyWhenOneCarFillsIt() {
        val columns = Cell(1f)
        val m = motion(columns, 1f, 1f, 900f)
        assertEquals(1f, m.reflow.value, 0.0001f)
        assertEquals(450f, m.blockWidthDp(), 0.01f)
        val two = motion(Cell(2f), 2f, 2f, 900f)
        assertEquals(0f, two.reflow.value, 0.0001f, "two cars across 900dp leave 450dp each: one column")
        assertEquals(450f, two.blockWidthDp(), 0.01f)
    }

    @Test fun aNarrowWindowNeverReflows() {
        assertEquals(0f, motion(Cell(1f), 1f, 1f, 500f).reflow.value, 0.0001f)
    }

    @Test fun theBlocksKeepTheirWidthThroughTheWholeMotion() {
        // 900dp: one car in two 450dp columns, or two cars in one 450dp column each. The blocks are 450dp at both ends,
        // so they must stay 450dp all the way -- the old eased width dipped to 300dp halfway and squashed the car.
        val columns = Cell(2f)
        val m = motion(columns, 2f, 1f, 900f)
        var last = m.reflow.value
        var largestStep = 0f
        for (i in 0..100) {
            columns.current = 2f - i / 100f
            assertEquals(450f, m.blockWidthDp(), 0.01f, "at ${columns.current} columns")
            val now = m.reflow.value
            assertTrue(now >= last - 0.0001f, "never goes back as the car widens ($now < $last)")
            largestStep = maxOf(largestStep, abs(now - last))
            last = now
        }
        assertEquals(1f, last, 0.0001f)
        assertTrue(largestStep <= 0.02f, "the reflow follows the motion evenly (largest step $largestStep)")
    }

    @Test fun aSpringOvershootStaysWithinTheEndLayouts() {
        val m = motion(Cell(0.9f), 2f, 1f, 900f)
        assertEquals(1f, m.reflow.value, 0.0001f)
    }
}
