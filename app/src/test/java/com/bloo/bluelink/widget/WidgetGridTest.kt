package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [WidgetGrid]'s cell math: the `70n - 30` nominal formula, its inverse
 * rounding, and the clamping at the widget's declared 2..7 column / 1..7 row
 * range. [WidgetBlueprintTest] separately proves the RENDERING consequences
 * of grid classification (every cell reaches a real layout, nothing is cut
 * off); this file is just the arithmetic underneath it.
 */
class WidgetGridTest {

    @Test
    fun `nominalSize is the inverse of gridFor across the whole declared range`() {
        // Round-trip: the nominal dp size for (cols, rows) must itself
        // classify back to (cols, rows) -- otherwise WidgetGrid's own
        // "the size a user thinks they asked for" and "the size that size
        // classifies as" would disagree with each other.
        for (cols in WidgetGrid.MIN_COLS..WidgetGrid.MAX_COLS) {
            for (rows in WidgetGrid.MIN_ROWS..WidgetGrid.MAX_ROWS) {
                val nominal = WidgetGrid.nominalSize(cols, rows)
                val back = WidgetGrid.gridFor(nominal)
                assertEquals(
                    WidgetGrid.GridSize(cols, rows), back,
                    "nominalSize($cols,$rows) = ${nominal.width.value}x${nominal.height.value} " +
                        "round-tripped to ${back.cols}x${back.rows}",
                )
            }
        }
    }

    @Test
    fun `nominalSize matches the documented 70n-30 formula`() {
        assertEquals(110f, WidgetGrid.nominalSize(2, 1).width.value)
        assertEquals(40f, WidgetGrid.nominalSize(2, 1).height.value)
        assertEquals(460f, WidgetGrid.nominalSize(7, 7).width.value)
        assertEquals(460f, WidgetGrid.nominalSize(7, 7).height.value)
    }

    @Test
    fun `cols and rows clamp to the declared 2-7 by 1-7 range`() {
        // A host that ignores the manifest's declared min/max and measures
        // outside it must still resolve to the nearest shape this widget
        // actually knows how to draw, not an out-of-table value.
        assertEquals(WidgetGrid.MIN_COLS, WidgetGrid.colsFor(0.dp))
        assertEquals(WidgetGrid.MIN_COLS, WidgetGrid.colsFor(40.dp))
        assertEquals(WidgetGrid.MAX_COLS, WidgetGrid.colsFor(1000.dp))
        assertEquals(WidgetGrid.MIN_ROWS, WidgetGrid.rowsFor(0.dp))
        assertEquals(WidgetGrid.MAX_ROWS, WidgetGrid.rowsFor(1000.dp))
    }

    @Test
    fun `gridFor rounds to the nearest cell, not just floors`() {
        // A real launcher's actual cell pitch varies from the nominal 70dp;
        // gridFor has to be robust to landing a few dp either side of a
        // nominal boundary and still report the intended cell count.
        val justUnder3Cols = WidgetGrid.nominalSize(3, 1).width - 1.dp
        val justOver3Cols = WidgetGrid.nominalSize(3, 1).width + 1.dp
        assertEquals(3, WidgetGrid.colsFor(justUnder3Cols))
        assertEquals(3, WidgetGrid.colsFor(justOver3Cols))
    }

    @Test
    fun `isFullGrid is true from 2 rows up, false for the 1-row banner strip`() {
        assertTrue(!WidgetGrid.GridSize(4, 1).isFullGrid)
        assertTrue(WidgetGrid.GridSize(4, 2).isFullGrid)
        assertTrue(WidgetGrid.GridSize(4, 7).isFullGrid)
    }

    @Test
    fun `gridFor of a genuinely awkward real size still lands sensibly`() {
        // The user's own callout: a 7-column, 2-row shape (very wide, short).
        val awkward = DpSize(455.dp, 108.dp)
        val grid = WidgetGrid.gridFor(awkward)
        assertEquals(WidgetGrid.GridSize(7, 2), grid)
    }
}
