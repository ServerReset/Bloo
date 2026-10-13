package com.bloo.bluelink.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The widget's shape, chosen from the size the launcher gives it. Four shapes cover the whole 2x2 to
 * 5x5 range: [MINIMAL] when there is no room for actions beside the value, [ROW] and [COLUMN] when
 * exactly one axis is long enough to run actions alongside it, and [FULL] when both are.
 */
internal enum class WidgetShape { MINIMAL, ROW, COLUMN, FULL }

/** The widget's advertised range, in launcher cells: a 2x2 minimum, a 5x5 maximum. */
internal const val WIDGET_MIN_CELLS = 2
internal const val WIDGET_MAX_CELLS = 5

/** Launcher cells a [dp] span covers: the platform's own `(size + 30) / 70`, clamped to the range. */
internal fun widgetCells(dp: Dp): Int =
    ((dp.value + 30f) / 70f).roundToInt().coerceIn(WIDGET_MIN_CELLS, WIDGET_MAX_CELLS)

/** dp for [cells] launcher cells -- the inverse of [widgetCells]. */
internal fun cellDp(cells: Int): Dp = (70 * cells - 30).dp

/**
 * The shape for a size span. Pure, so every cell of the 2..5 grid is unit-tested rather than
 * eyeballed.
 */
internal fun widgetShapeFor(width: Dp, height: Dp): WidgetShape {
    val cols = widgetCells(width)
    val rows = widgetCells(height)
    return when {
        cols >= 3 && rows >= 3 -> WidgetShape.FULL
        cols >= 3 -> WidgetShape.ROW
        rows >= 3 -> WidgetShape.COLUMN
        else -> WidgetShape.MINIMAL
    }
}

/**
 * Every size the widget asks Glance to render for: all 2..5 x 2..5 cell spans. Enumerating the whole
 * grid means the launcher always finds its exact span instead of scaling a neighbouring one.
 */
internal val WidgetSizes: Set<DpSize> =
    (WIDGET_MIN_CELLS..WIDGET_MAX_CELLS).flatMap { cols ->
        (WIDGET_MIN_CELLS..WIDGET_MAX_CELLS).map { rows -> DpSize(cellDp(cols), cellDp(rows)) }
    }.toSet()
