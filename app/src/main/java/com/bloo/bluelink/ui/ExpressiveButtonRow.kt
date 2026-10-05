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
    /** See [ExpressiveButtonGroup]'s own `connected`. */
    connected: Boolean = false,
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
        connected = connected,
    ) {
        content()
    }
}

/**
 * The standard full-width row of action buttons under a card's or group's content, a connected [ButtonCluster]:
 * the buttons of a line share one silhouette (a lone one is fully round), and when they do not fit on one line
 * they wrap, each line its own cluster.
 */
@Composable
fun ActionRow(content: @Composable () -> Unit) =
    ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = SplitSeam, lineSpacing = GapRow, connected = true, content = content)
