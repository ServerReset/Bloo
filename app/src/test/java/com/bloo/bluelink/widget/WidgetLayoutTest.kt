package com.bloo.bluelink.widget

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every size the widget can be given: the whole 2..5 x 2..5 cell grid maps to a real shape, and the
 * sizes advertised to Glance cover that same grid.
 */
class WidgetLayoutTest {

    @Test fun cellsAreThePlatformRuleClampedToTheAdvertisedRange() {
        assertEquals(2, widgetCells(110.dp))
        assertEquals(3, widgetCells(180.dp))
        assertEquals(4, widgetCells(250.dp))
        assertEquals(5, widgetCells(320.dp))
        // Below the 2x2 minimum and above the 5x5 maximum the launcher should never ask, but clamp
        // rather than produce a 1 or a 6.
        assertEquals(2, widgetCells(40.dp))
        assertEquals(5, widgetCells(900.dp))
    }

    @Test fun cellDpRoundTripsThroughWidgetCells() {
        for (cells in WIDGET_MIN_CELLS..WIDGET_MAX_CELLS) {
            assertEquals(cells, widgetCells(cellDp(cells)))
        }
    }

    @Test fun everyCellInTheGridMapsToTheExpectedShape() {
        // Both axes long -> FULL; one long -> ROW or COLUMN; neither -> MINIMAL.
        for (cols in 2..5) {
            for (rows in 2..5) {
                val expected = when {
                    cols >= 3 && rows >= 3 -> WidgetShape.FULL
                    cols >= 3 -> WidgetShape.ROW
                    rows >= 3 -> WidgetShape.COLUMN
                    else -> WidgetShape.MINIMAL
                }
                assertEquals(expected, widgetShapeFor(cellDp(cols), cellDp(rows)), "$cols x $rows")
            }
        }
    }

    @Test fun onlyARealTwoByTwoIsMinimal() {
        assertEquals(WidgetShape.MINIMAL, widgetShapeFor(cellDp(2), cellDp(2)))
    }

    @Test fun aFiveByFiveIsFull() {
        assertEquals(WidgetShape.FULL, widgetShapeFor(cellDp(5), cellDp(5)))
    }

    @Test fun theAdvertisedSizesCoverEveryCellSpan() {
        for (cols in 2..5) {
            for (rows in 2..5) {
                assertTrue(
                    WidgetSizes.any { widgetCells(it.width) == cols && widgetCells(it.height) == rows },
                    "no advertised size for $cols x $rows",
                )
            }
        }
        // And nothing outside the advertised range sneaks in.
        assertTrue(WidgetSizes.all { it.width.value in 110f..320f && it.height.value in 110f..320f })
    }
}
