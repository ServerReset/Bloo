package com.bloo.bluelink.widget

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
    /** Whether the header carries the 32dp refresh button (it is taller than its text). */
    val refresh: Boolean = true,
    /** STACK: height left over after everything the plan placed, split into fixed gaps (weights proved unreliable). */
    val spareDp: Float = 0f,
    /** STACK: how many status chips fit across the width without clipping. */
    val maxChips: Int = 3,
) {
    val minimal: Boolean get() = arrangement == WidgetArrangement.MINIMAL

    /** Rows of controls in a STACK: one along the width, or the two-column grid's rows. */
    fun stackRows(): Int = if (gridColumns > 0) (maxActions + gridColumns - 1) / gridColumns else 1
}

internal const val WIDGET_UPDATED_SP = 11f
internal const val WIDGET_BAR_DP = 8f
internal const val WIDGET_CHIP_DP = 30f
private const val LINE = 1.3f
private const val VALUE_LINE = 1.15f
/** Average glyph width of the bold value, as a share of its size. */
private const val VALUE_GLYPH = 0.76f
private const val MIN_VALUE_SP = 24f
private const val GAP = 8f
/** The header's refresh button, which makes the header at least this tall. */
private const val REFRESH_DP = 32f
/** The least width the readout beside a SIDE control grid keeps. */
private const val MIN_INFO_W = 90f
/** What a launcher takes off the span it reports (its own padding and corner rounding), per axis. */
private const val LAUNCHER_INSET = 8f

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
    /** Whether the header has the refresh button (on by default). */
    refreshButton: Boolean = true,
): WidgetPlan {
    val pad = widgetPad(compact)
    // A launcher draws the widget inside its own inset and rounds the corners, so the span it reports is a
    // little bigger than what is visible: budget against less, so nothing is ever clipped at an edge.
    val innerW = (widthDp - 2 * pad - LAUNCHER_INSET).coerceAtLeast(1f)
    val innerH = (heightDp - 2 * pad - LAUNCHER_INSET).coerceAtLeast(1f)
    val fs = fontScale.coerceAtLeast(0.5f)
    val small = minOf(widthDp, heightDp)
    // Text shrinks with a narrow width (a 2-cell-wide widget is ~110dp), never below a readable floor.
    val ws = (innerW / 160f).coerceIn(0.8f, 1f)
    val nameSp = ((small * 0.085f).coerceIn(13f, 19f) * ws).coerceAtLeast(11.5f)
    val statusSp = ((small * 0.075f).coerceIn(12f, 17f) * ws).coerceAtLeast(10.5f)
    val detailSp = ((small * 0.062f).coerceIn(11f, 15f) * ws).coerceAtLeast(10f)
    fun line(sp: Float) = sp * fs * LINE
    val chars = valueChars.coerceAtLeast(3)
    fun valueByWidth(w: Float) = w / (chars * VALUE_GLYPH * fs)
    val actions = actionCount.coerceIn(1, 4)

    // The header is its text column or the refresh button, whichever is taller -- the button is 32dp and a line of
    // name is often shorter, so budgeting only the text let the rows below be pushed past the bottom.
    fun header(updated: Boolean) =
        maxOf(line(nameSp) + (if (updated) line(WIDGET_UPDATED_SP) else 0f), if (refreshButton) REFRESH_DP else 0f)

    val buttonH = when { innerH >= 300f -> 64f; innerH >= 230f -> 56f; else -> 46f }
    val stackNeed = header(false) + GAP + MIN_VALUE_SP * fs * VALUE_LINE + GAP + buttonH

    // SIDE: wide and too short for a control row under the readout -- the controls stand in a grid beside it. The grid
    // takes as many columns as the width can spare while the readout keeps at least MIN_INFO_W; when no layout leaves
    // it that, the widget is not SIDE at all (it would clip the name and value to a sliver).
    if (innerH < stackNeed + 24f && widthDp >= heightDp * 1.6f && actions >= 2) {
        for (cols in minOf(actions, 3) downTo 1) {
            val rows = (actions + cols - 1) / cols
            if (rows > 2) continue
            val diameter = minOf(56f, (innerH - GAP * (rows - 1)) / rows)
            if (diameter < 36f) continue
            val gridW = cols * diameter + (cols - 1) * GAP
            val infoW = innerW - gridW - 12f
            if (infoW < MIN_INFO_W) continue
            var showStatus = true
            var showUpdated = infoW >= 150f
            fun room() = (innerH - header(showUpdated) - GAP - (if (showStatus) line(statusSp) else 0f)) / (fs * VALUE_LINE)
            if (room() < 30f) showUpdated = false
            if (room() < MIN_VALUE_SP) showStatus = false
            val value = minOf(room(), valueByWidth(infoW), 72f).coerceAtLeast(20f)
            return WidgetPlan(
                arrangement = WidgetArrangement.SIDE, valueSp = value, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
                showUpdated = showUpdated, showStatus = showStatus, showDetails = false, showBar = false, showChips = false,
                labels = false, buttonHeightDp = diameter, maxActions = minOf(actions, rows * cols), gridColumns = cols,
                refresh = refreshButton,
            )
        }
    }

    // MINIMAL: no room for a row or a grid of controls -- the value and the lock share one row.
    if (innerH < stackNeed) {
        val rowH = innerH - header(false) - GAP
        // The lock circle shrinks to the room left (never under 30dp) instead of overflowing it.
        val lock = rowH.coerceIn(30f, 46f)
        val v = minOf(rowH / (fs * VALUE_LINE), valueByWidth(innerW - lock - GAP), 56f).coerceAtLeast(20f)
        return WidgetPlan(
            arrangement = WidgetArrangement.MINIMAL, valueSp = v, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
            showUpdated = false, showStatus = false, showDetails = false, showBar = false, showChips = false,
            labels = false, buttonHeightDp = lock, maxActions = 1, refresh = refreshButton,
        )
    }

    // STACK: the controls run along one row while they fit (at least a circle each); a narrow widget wraps them
    // into two columns instead of dropping any, as long as the height holds the extra row.
    val fit = ((innerW + GAP) / (buttonH + GAP)).toInt()
    // Every control on one row, each shrunk to fit (but never under a 40dp circle), before wrapping at all.
    val perButton = (innerW - GAP * (actions - 1)) / actions
    val singleRow = perButton >= 40f
    val rowButtonH = if (singleRow) minOf(buttonH, perButton) else buttonH
    val gridRows = (actions + 1) / 2
    val gridH = gridRows * buttonH + (gridRows - 1) * GAP
    val gridNeed = header(false) + GAP + MIN_VALUE_SP * fs * VALUE_LINE + GAP + gridH
    val useGrid = !singleRow && actions > 1 && innerH >= gridNeed
    val cols = if (useGrid) 2 else 0
    val rows = if (useGrid) gridRows else 1
    val maxActions = when {
        useGrid || singleRow -> actions
        else -> minOf(actions, fit).coerceAtLeast(1)
    }
    val controlsH = rows * rowButtonH + (rows - 1) * GAP
    val cellW = if (useGrid) (innerW - GAP) / 2 else (innerW - GAP * (maxActions - 1)) / maxActions
    val labels = !compact && cellW >= 84f

    var showStatus = true
    var showUpdated = widthDp >= 180f
    var showDetails = !compact
    fun fixed(): Float =
        header(showUpdated) + GAP +
            (if (showStatus) line(statusSp) else 0f) +
            (if (showDetails) 4f + line(detailSp) else 0f) + GAP + controlsH
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
    val plan = WidgetPlan(
        arrangement = WidgetArrangement.STACK, valueSp = value, nameSp = nameSp, statusSp = statusSp, detailSp = detailSp,
        showUpdated = showUpdated, showStatus = showStatus, showDetails = showDetails, showBar = showBar, showChips = showChips,
        labels = labels, buttonHeightDp = rowButtonH, maxActions = maxActions, gridColumns = cols, refresh = refreshButton,
        // A chip is about 100dp wide with its text; only as many as fit, so none is cut to "N...".
        maxChips = (innerW / 104f).toInt().coerceIn(1, 3),
    )
    // What is left once everything is placed becomes two fixed gaps (above the value, above the controls): a tall
    // widget is spaced evenly, and the whole plan fits by construction instead of depending on weighted spacers.
    return plan.copy(spareDp = (innerH - plan.heightDp(fs)).coerceAtLeast(0f))
}

/** The plan's total height, in dp, for tests: it must never exceed the widget's inner height. */
internal fun WidgetPlan.heightDp(fontScale: Float): Float {
    fun line(sp: Float) = sp * fontScale * LINE
    val value = valueSp * fontScale * VALUE_LINE
    val header = maxOf(line(nameSp) + (if (showUpdated) line(WIDGET_UPDATED_SP) else 0f), if (refresh) REFRESH_DP else 0f)
    return when (arrangement) {
        WidgetArrangement.MINIMAL -> maxOf(line(nameSp), if (refresh) REFRESH_DP else 0f) + GAP + maxOf(value, buttonHeightDp)
        WidgetArrangement.SIDE -> maxOf(
            header + GAP + value + (if (showStatus) line(statusSp) else 0f),
            buttonHeightDp * (if (maxActions > gridColumns) 2 else 1) + (if (maxActions > gridColumns) GAP else 0f),
        )
        WidgetArrangement.STACK -> header + GAP + value +
            (if (showStatus) line(statusSp) else 0f) + (if (showDetails) 4f + line(detailSp) else 0f) +
            (if (showBar) WIDGET_BAR_DP + 2 * GAP else 0f) + (if (showChips) WIDGET_CHIP_DP * fontScale.coerceAtLeast(1f) + GAP else 0f) +
            GAP + stackRows() * buttonHeightDp + (stackRows() - 1) * GAP
    }
}
