package com.bloo.bluelink.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.ExpressiveButtonGroup
import com.bloo.uicommon.MorphedCornerPercent
import com.bloo.uicommon.PillCornerPercent
import com.bloo.uicommon.seamCorner
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.content

/**
 * One button of a [ButtonCluster]. Its shape, its seams and its sizing are the cluster's job; this only says what
 * the button looks like and does.
 */
internal class ClusterButton(
    val onClick: () -> Unit,
    val modifier: Modifier = Modifier,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
    val activeContainerColor: Color? = null,
    val activeContentColor: Color? = null,
    val disabledContentColor: Color? = null,
    /** Null = the standard cluster padding. */
    val contentPadding: PaddingValues? = null,
    /** Share of the row's leftover width this button takes; 0 keeps its natural width. */
    val weight: Float = 0f,
    val onClickHaptic: (() -> Unit)? = null,
    /** Hold-to-act action (the chevron's spin easter egg). Null = plain click-only. */
    val onLongClick: (() -> Unit)? = null,
    /** Pass one to observe presses from outside; otherwise the cluster makes its own. */
    val interactionSource: MutableInteractionSource? = null,
    val content: @Composable RowScope.() -> Unit,
)

/**
 * The app's one framework for a row of connected buttons: the pebble header's action + chevron, the preset and
 * charge-limit pills, the lock group. One button is fully round (a circle when it is icon-only, a pill with a
 * label). Two or more share one silhouette: the outer ends are fully round, every seam between two buttons is a
 * small soft corner, the gap between them is [SplitSeam], and a press gives way to its neighbours without the
 * cluster changing size.
 */
@Composable
internal fun ButtonCluster(
    buttons: List<ClusterButton>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    if (buttons.isEmpty()) return
    val density = LocalDensity.current
    // The floor for EVERY button: never narrower than the row is tall. A collapsed button is a pill
    // (corner percent 50), so with width == height that floor makes it a true CIRCLE; expanded it
    // morphs to the rounded square as before. A label wider than the row's height grows the button
    // past it; nothing shrinks below a circle.
    var rowHeight by remember { mutableStateOf(0.dp) }
    val scheme = MaterialTheme.colorScheme
    // 10.dp, not 14: the horizontal padding is what a header action takes out of the row, and at the
    // app's bigger type scale 14 was eating the title beside it. Vertical stays GapRow.
    val standardPadding = PaddingValues(horizontal = 10.dp, vertical = GapRow)
    ExpressiveButtonGroup(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .onSizeChanged { rowHeight = with(density) { it.height.toDp() } },
        spacing = SplitSeam,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = horizontalAlignment,
        // One connected control must never break onto two lines: when room runs out it compacts to symbols.
        wrap = false,
        // A cluster of ONE is a standalone control (the pebble header chevron): it grows like any
        // other button on press. A real cluster (two or more) must not balloon.
        growWhenAlone = buttons.size == 1,
    ) {
        buttons.forEachIndexed { i, b ->
            key(i) {
                MorphButton(
                    onClick = b.onClick,
                    enabled = b.enabled,
                    active = b.active,
                    containerColor = b.containerColor ?: scheme.secondaryContainer,
                    contentColor = b.contentColor ?: scheme.onSecondaryContainer,
                    activeContainerColor = b.activeContainerColor ?: scheme.primary,
                    activeContentColor = b.activeContentColor ?: scheme.onPrimary,
                    disabledContentColor = b.disabledContentColor,
                    contentPadding = b.contentPadding ?: standardPadding,
                    interactionSource = b.interactionSource ?: remember { MutableInteractionSource() },
                    onClickHaptic = b.onClickHaptic,
                    onLongClick = b.onLongClick,
                    // A lone button is round at rest and squares off when it is open or pressed, like
                    // any standalone button. Only a real cluster needs the connected-corner shape,
                    // which reacts to press alone so a member's corners agree with its neighbours.
                    // Remembered by position, not rebuilt from the list: a fresh lambda (capturing a fresh list) on
                    // every recomposition kept every member button from ever skipping.
                    shapeForCorner = run {
                        val size = buttons.size
                        remember(size, i) {
                            if (size == 1) null else { morph: Float, cornerPercent: Int -> connectedShape(i == 0, i == size - 1, cornerPercent, morph, morph) }
                        }
                    },
                    pillCornerPercent = PillCornerPercent,
                    morphedCornerPercent = MorphedCornerPercent,
                    minHeight = 0.dp,
                    groupWeight = b.weight,
                    modifier = Modifier.fillMaxHeight()
                        // Never narrower than the row is tall: a collapsed button is a circle.
                        .widthIn(min = rowHeight)
                        .then(b.modifier),
                    content = b.content,
                )
            }
        }
    }
}

/** The silhouette of a connected button: round at the ends of its line, a seam corner wherever it meets a neighbour. */
internal fun connectedShape(first: Boolean, last: Boolean, outerPercent: Int, startSeamMorph: Float, endSeamMorph: Float): RoundedCornerShape {
    val outer = CornerSize(percent = outerPercent)
    val start = if (first) outer else seamCorner(startSeamMorph)
    val end = if (last) outer else seamCorner(endSeamMorph)
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}
