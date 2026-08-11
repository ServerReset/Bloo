package com.bloo.bluelink.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The home-screen GRID this widget is actually placed on, expressed the way a
 * user (and every launcher's own resize UI) thinks about it: a column count
 * and a row count, not an arbitrary dp rectangle.
 *
 * This exists because the widget used to classify a measured size by
 * aspect-ratio thresholds tuned by trial and error against real device
 * screenshots -- correct, eventually, but with no relationship to the actual
 * question a user is answering when they drag a widget's resize handles
 * ("how many cells wide/tall do I want this"). [gridFor] answers that
 * question directly, from the same [Dp] measurement, and [WidgetBlueprint]
 * reasons in those terms -- the same information, reframed around the one
 * abstraction every launcher and this widget's own manifest
 * (`car_widget_info.xml`) already agree on.
 *
 * The clearest case for the reframing is the one-row strip. Under aspect
 * ratios a 2x1 and a 3x1 were not "wide enough" to count as strips and fell
 * through to an icon, while a 5x1 got a real layout. A tile is a strip
 * because it has ONE ROW, which is what [GridSize.rows] says outright.
 *
 * The cell formula (`70 * n - 30`) is the standard one Android's own widget
 * design guidance has used since App Widgets shipped: a 70dp cell pitch with
 * a 30dp fixed inset baked in, so `n` cells of width report `70n - 30` dp of
 * USABLE space rather than `70n`. It is deliberately a NOMINAL model, not a
 * live one -- real launchers vary their actual cell pitch by screen size and
 * grid density, so a placed widget is never measured at exactly `70n - 30`.
 * [gridFor] rounds the real measurement to the nearest cell count on that
 * nominal scale, which is robust to that variance: a launcher whose cells
 * run a little larger or smaller than 70dp still rounds to the same (cols,
 * rows) as the nominal grid, because the classification only needs to be
 * right to the nearest whole cell, not to the dp.
 *
 * [WidgetBlueprint] still finishes the job with the REAL measured [DpSize]
 * (via [Scale]'s continuous, budget-checked arithmetic) for everything about
 * how big things render -- only WHICH shape of layout applies is
 * grid-driven. That split is deliberate: a fixed dp-per-cell
 * assumption at render time would either waste real launcher space (round
 * DOWN) or overflow it (round UP), which is exactly the "clean, nothing cut
 * off" property this file's only job is to keep true. Grid membership is a
 * classification, not a canvas.
 */
internal object WidgetGrid {
    /** Cell pitch and fixed inset behind the `70n - 30` nominal-size formula. */
    private const val CELL_DP = 70f
    private const val INSET_DP = 30f

    /**
     * The narrowest a placed widget is ever asked to be -- the manifest's
     * declared `minWidth`/`targetCellWidth` floor. A widget genuinely never
     * has fewer than 2 columns to work with: `car_widget_info.xml` doesn't
     * offer a 1-column drop size, and this whole rework is scoped to the
     * 2..7 column, 1..7 row space the widget is actually meant to cover --
     * a host that ignores minWidth and measures smaller than that is
     * clamped up to it rather than given a shape nothing knows how to draw.
     */
    const val MIN_COLS = 2
    const val MAX_COLS = 7

    /** A widget can be exactly one row tall (a banner strip) up through the
     *  full 7-row dashboard shape; see [WidgetBlueprint]'s strip path. */
    const val MIN_ROWS = 1
    const val MAX_ROWS = 7

    /** One shape on the grid: [cols] columns wide, [rows] rows tall, each
     *  clamped to the widget's actual supported range ([MIN_COLS]..[MAX_COLS],
     *  [MIN_ROWS]..[MAX_ROWS]) so a measurement outside the nominal grid
     *  (a host that ignores the manifest's declared bounds) still resolves
     *  to the nearest shape this widget knows how to draw, rather than an
     *  out-of-range value nothing downstream has a case for. */
    data class GridSize(val cols: Int, val rows: Int) {
        /** True once both axes have grown past the single-row/-column
         *  banner shapes into the full grid -- the "2x2 to 7x7" half of the
         *  space, as opposed to the "2x1 to 7x1" strip half. */
        val isFullGrid: Boolean get() = rows >= 2
    }

    /** Nominal dp size of the widget's own manifest floor (2 columns, 1
     *  row) -- the smallest shape this grid ever names. Exposed for
     *  previews/tests that want to render or reason about that exact shape. */
    fun nominalSize(cols: Int, rows: Int): DpSize {
        val c = cols.coerceIn(MIN_COLS, MAX_COLS)
        val r = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        return DpSize((CELL_DP * c - INSET_DP).dp, (CELL_DP * r - INSET_DP).dp)
    }

    /** Rounds a real dp span to the nearest whole cell count on the nominal
     *  `70n - 30` scale -- the inverse of [nominalSize]'s own formula. */
    private fun cellsFor(spanDp: Float): Int =
        ((spanDp + INSET_DP) / CELL_DP).roundToInt()

    fun colsFor(width: Dp): Int = cellsFor(width.value).coerceIn(MIN_COLS, MAX_COLS)
    fun rowsFor(height: Dp): Int = cellsFor(height.value).coerceIn(MIN_ROWS, MAX_ROWS)

    /** The grid shape a real measured [size] rounds to -- see the class doc
     *  for why this is a classification, never the canvas itself. */
    fun gridFor(size: DpSize): GridSize = GridSize(colsFor(size.width), rowsFor(size.height))
}
