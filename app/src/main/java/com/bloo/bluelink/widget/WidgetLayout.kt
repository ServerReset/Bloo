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

/**
 * Everything the widget body decides from its real size, as one pure plan: what to show and how big. The text
 * is budgeted against the actual height (in the user's font scale), so nothing is ever cut off, and the big
 * value then takes whatever height is left, so no size is left half empty. Lines drop in order of importance
 * (details, then the "updated" line, then the status line) before the value would get too small.
 */
internal data class WidgetPlan(
    val minimal: Boolean,
    val valueSp: Float,
    val nameSp: Float,
    val statusSp: Float,
    val detailSp: Float,
    val showUpdated: Boolean,
    val showStatus: Boolean,
    val showDetails: Boolean,
    val labels: Boolean,
    val buttonHeightDp: Float,
    val maxActions: Int,
)

internal const val WIDGET_UPDATED_SP = 11f
private const val LINE = 1.3f
private const val VALUE_LINE = 1.15f
/** Average glyph width of the bold value, as a share of its size. */
private const val VALUE_GLYPH = 0.62f
private const val MIN_VALUE_SP = 24f

internal fun planWidget(
    widthDp: Float,
    heightDp: Float,
    fontScale: Float,
    compact: Boolean,
    /** Characters in the big value ("92%", "Unlocked"), so it never runs wider than the widget. */
    valueChars: Int,
    /** How many controls the options allow. */
    actionCount: Int,
): WidgetPlan {
    val pad = if (compact) 10f else 16f
    val gap = 8f
    val innerW = (widthDp - 2 * pad).coerceAtLeast(1f)
    val innerH = (heightDp - 2 * pad).coerceAtLeast(1f)
    val fs = fontScale.coerceAtLeast(0.5f)
    val small = minOf(widthDp, heightDp)
    val nameSp = (small * 0.085f).coerceIn(13f, 19f)
    val statusSp = (small * 0.075f).coerceIn(12f, 17f)
    val detailSp = (small * 0.062f).coerceIn(11f, 15f)
    fun line(sp: Float) = sp * fs * LINE
    val chars = valueChars.coerceAtLeast(3)
    fun valueByWidth(w: Float) = w / (chars * VALUE_GLYPH * fs)

    val buttonH = if (innerH >= 230f) 56f else 46f
    // Controls: as many icon buttons as the width holds (at least a circle each), at most four.
    val fit = ((innerW + gap) / (buttonH + gap)).toInt()
    val maxActions = minOf(actionCount, fit, 4).coerceAtLeast(1)
    // Labels only where each control gets a comfortable word-width.
    val labels = !compact && maxActions > 0 && (innerW - gap * (maxActions - 1)) / maxActions >= 84f && buttonH >= 46f

    // Not enough height for header + a readable value + the control row: the 2x2 form (value and lock share a row).
    val fullNeed = line(nameSp) + gap + MIN_VALUE_SP * fs * VALUE_LINE + gap + buttonH
    if (innerH < fullNeed) {
        val rowH = innerH - line(nameSp) - gap
        val v = minOf(rowH / (fs * VALUE_LINE), valueByWidth(innerW - 46f - gap), 56f).coerceAtLeast(20f)
        return WidgetPlan(
            minimal = true, valueSp = v, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
            showUpdated = false, showStatus = false, showDetails = false,
            labels = false, buttonHeightDp = 46f, maxActions = 1,
        )
    }

    var showStatus = true
    var showUpdated = widthDp >= 180f
    var showDetails = !compact
    fun fixed(): Float =
        line(nameSp) + (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f) + gap +
            (if (showStatus) line(statusSp) else 0f) +
            (if (showDetails) 4f + line(detailSp) else 0f) + gap + buttonH
    fun valueRoom() = (innerH - fixed()) / (fs * VALUE_LINE)
    // A comfortable value first: drop the least important lines until it has room.
    val wanted = 34f
    if (valueRoom() < wanted) showDetails = false
    if (valueRoom() < wanted) showUpdated = false
    if (valueRoom() < MIN_VALUE_SP) showStatus = false
    val value = minOf(valueRoom(), valueByWidth(innerW), 88f).coerceAtLeast(MIN_VALUE_SP)
    return WidgetPlan(
        minimal = false, valueSp = value, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
        showUpdated = showUpdated, showStatus = showStatus, showDetails = showDetails,
        labels = labels, buttonHeightDp = buttonH, maxActions = maxActions,
    )
}

/** The plan's total height, in dp, for tests: it must never exceed the widget's inner height. */
internal fun WidgetPlan.heightDp(fontScale: Float, compact: Boolean, widthDp: Float): Float {
    val gap = 8f
    fun line(sp: Float) = sp * fontScale * LINE
    return if (minimal) {
        line(nameSp) + gap + maxOf(valueSp * fontScale * VALUE_LINE, buttonHeightDp)
    } else {
        line(nameSp) + (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f) + gap + valueSp * fontScale * VALUE_LINE +
            (if (showStatus) line(statusSp) else 0f) + (if (showDetails) 4f + line(detailSp) else 0f) + gap + buttonHeightDp
    }
}
