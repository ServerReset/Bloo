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

/** How the widget's body is arranged, picked from its real proportions. */
internal enum class WidgetArrangement {
    /** Readout on top, the control row along the bottom (most sizes). */
    STACK,
    /** Wide and short: the readout on the left, a grid of round controls on the right. */
    SIDE,
    /** Too small for a control row or grid: the value and the lock share one row. */
    MINIMAL,
}

/**
 * Everything the widget body decides from its real size, as one pure plan: how it is arranged, what it shows
 * and how big. The text is budgeted against the actual height (in the user's font scale) so nothing is ever
 * cut off, the big value then takes the height left, and a tall widget that still has room gains a charge bar
 * and status chips instead of an empty band. Lines drop by importance (details, then the "updated" line, then
 * the status line) before the value would get too small.
 */
internal data class WidgetPlan(
    val arrangement: WidgetArrangement,
    val valueSp: Float,
    val nameSp: Float,
    val statusSp: Float,
    val detailSp: Float,
    val showUpdated: Boolean,
    val showStatus: Boolean,
    val showDetails: Boolean,
    /** A charge bar under the readout (tall widgets with room to spare). */
    val showBar: Boolean,
    /** A row of status chips (climate, charging) under the bar (taller still). */
    val showChips: Boolean,
    val labels: Boolean,
    /** Height of a control (STACK row) or the diameter of a round control (SIDE grid, MINIMAL lock). */
    val buttonHeightDp: Float,
    val maxActions: Int,
    /** SIDE only: how many columns the control grid has. */
    val gridColumns: Int = 0,
) {
    val minimal: Boolean get() = arrangement == WidgetArrangement.MINIMAL
}

internal const val WIDGET_UPDATED_SP = 11f
internal const val WIDGET_BAR_DP = 8f
internal const val WIDGET_CHIP_DP = 30f
private const val LINE = 1.3f
private const val VALUE_LINE = 1.15f
/** Average glyph width of the bold value, as a share of its size. */
private const val VALUE_GLYPH = 0.62f
private const val MIN_VALUE_SP = 24f
private const val GAP = 8f

internal fun widgetPad(compact: Boolean) = if (compact) 10f else 16f

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
    val pad = widgetPad(compact)
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
    val actions = actionCount.coerceIn(1, 4)

    val buttonH = if (innerH >= 230f) 56f else 46f
    val stackNeed = line(nameSp) + GAP + MIN_VALUE_SP * fs * VALUE_LINE + GAP + buttonH

    // SIDE: wide and too short for a control row under the readout -- the controls stand in a grid beside it.
    if (innerH < stackNeed + 24f && widthDp >= heightDp * 1.6f && actions >= 2) {
        val rows = if (actions > 2 && innerH >= 2 * 40f + GAP) 2 else 1
        val cols = minOf(actions, if (rows == 2) 2 else 3)
        val diameter = minOf(56f, (innerH - GAP * (rows - 1)) / rows).coerceAtLeast(36f)
        val gridW = cols * diameter + (cols - 1) * GAP
        val infoW = (innerW - gridW - 12f).coerceAtLeast(60f)
        var showStatus = true
        var showUpdated = infoW >= 150f
        fun room() = (innerH - line(nameSp) - (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f) - GAP -
            (if (showStatus) line(statusSp) else 0f)) / (fs * VALUE_LINE)
        if (room() < 30f) showUpdated = false
        if (room() < MIN_VALUE_SP) showStatus = false
        val value = minOf(room(), valueByWidth(infoW), 72f).coerceAtLeast(20f)
        return WidgetPlan(
            arrangement = WidgetArrangement.SIDE, valueSp = value, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
            showUpdated = showUpdated, showStatus = showStatus, showDetails = false, showBar = false, showChips = false,
            labels = false, buttonHeightDp = diameter, maxActions = rows * cols, gridColumns = cols,
        )
    }

    // MINIMAL: no room for a row or a grid of controls -- the value and the lock share one row.
    if (innerH < stackNeed) {
        val rowH = innerH - line(nameSp) - GAP
        val v = minOf(rowH / (fs * VALUE_LINE), valueByWidth(innerW - 46f - GAP), 56f).coerceAtLeast(20f)
        return WidgetPlan(
            arrangement = WidgetArrangement.MINIMAL, valueSp = v, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
            showUpdated = false, showStatus = false, showDetails = false, showBar = false, showChips = false,
            labels = false, buttonHeightDp = 46f, maxActions = 1,
        )
    }

    // STACK: as many controls as the width holds (at least a circle each), labels where each gets a word's width.
    val fit = ((innerW + GAP) / (buttonH + GAP)).toInt()
    val maxActions = minOf(actions, fit).coerceAtLeast(1)
    val labels = !compact && (innerW - GAP * (maxActions - 1)) / maxActions >= 84f

    var showStatus = true
    var showUpdated = widthDp >= 180f
    var showDetails = !compact
    fun fixed(): Float =
        line(nameSp) + (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f) + GAP +
            (if (showStatus) line(statusSp) else 0f) +
            (if (showDetails) 4f + line(detailSp) else 0f) + GAP + buttonH
    fun valueRoom() = (innerH - fixed()) / (fs * VALUE_LINE)
    val wanted = 34f
    if (valueRoom() < wanted) showDetails = false
    if (valueRoom() < wanted) showUpdated = false
    if (valueRoom() < MIN_VALUE_SP) showStatus = false
    val value = minOf(valueRoom(), valueByWidth(innerW), 88f).coerceAtLeast(MIN_VALUE_SP)
    // Room the value could not use (it hit its width cap): a charge bar, then a chip row, rather than a gap.
    var left = innerH - fixed() - value * fs * VALUE_LINE
    val showBar = !compact && left >= WIDGET_BAR_DP + 2 * GAP
    if (showBar) left -= WIDGET_BAR_DP + 2 * GAP
    val showChips = !compact && left >= WIDGET_CHIP_DP * fs.coerceAtLeast(1f) + GAP
    return WidgetPlan(
        arrangement = WidgetArrangement.STACK, valueSp = value, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
        showUpdated = showUpdated, showStatus = showStatus, showDetails = showDetails, showBar = showBar, showChips = showChips,
        labels = labels, buttonHeightDp = buttonH, maxActions = maxActions,
    )
}

/** The plan's total height, in dp, for tests: it must never exceed the widget's inner height. */
internal fun WidgetPlan.heightDp(fontScale: Float): Float {
    fun line(sp: Float) = sp * fontScale * LINE
    val value = valueSp * fontScale * VALUE_LINE
    return when (arrangement) {
        WidgetArrangement.MINIMAL -> line(nameSp) + GAP + maxOf(value, buttonHeightDp)
        WidgetArrangement.SIDE -> maxOf(
            line(nameSp) + (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f) + GAP + value + (if (showStatus) line(statusSp) else 0f),
            buttonHeightDp * (if (maxActions > gridColumns) 2 else 1) + (if (maxActions > gridColumns) GAP else 0f),
        )
        WidgetArrangement.STACK -> line(nameSp) + (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f) + GAP + value +
            (if (showStatus) line(statusSp) else 0f) + (if (showDetails) 4f + line(detailSp) else 0f) +
            (if (showBar) WIDGET_BAR_DP + 2 * GAP else 0f) + (if (showChips) WIDGET_CHIP_DP * fontScale.coerceAtLeast(1f) + GAP else 0f) +
            GAP + buttonHeightDp
    }
}
