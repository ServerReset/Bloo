package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A drop-in replacement for `Row(horizontalArrangement = Arrangement.spacedBy(spacing))` whose
 * [SafeExpansiveButton] children shove each other aside on press, with the row's own width
 * unchanged.
 *
 * Children that are not buttons (a Spacer, a label) are left at their natural width and take no
 * part in the redistribution, so this is safe to drop onto a mixed row.
 */
@Composable
fun ExpressiveButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    equalWidths: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    /** See [ExpressiveButtonGroup]'s own `lineSpacing` -- this wraps like a FlowRow. */
    lineSpacing: Dp = spacing,
    /** See [ExpressiveButtonGroup]'s own `wrap`. */
    wrap: Boolean = true,
    /** See [ExpressiveButtonGroup]'s own `stretchCompact`. */
    stretchCompact: Boolean = false,
    content: @Composable () -> Unit,
) {
    ExpressiveButtonGroup(
        modifier = modifier,
        spacing = spacing,
        verticalAlignment = verticalAlignment,
        equalWidths = equalWidths,
        horizontalAlignment = horizontalAlignment,
        lineSpacing = lineSpacing,
        wrap = wrap,
        stretchCompact = stretchCompact,
    ) {
        content()
    }
}


/** The standard full-width row of action buttons under a card's or group's content: wraps, shares the line, rests at [GapRow]. */
@Composable
fun ActionRow(content: @Composable () -> Unit) =
    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = GapRow, content = content)
