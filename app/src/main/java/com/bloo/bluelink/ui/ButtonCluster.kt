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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphedCornerPercent
import com.bloo.uicommon.PillCornerPercent
import com.bloo.uicommon.seamCorner

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
    /** Never narrower than the cluster is tall, so an icon-only button is a true circle (or a round nub in a cluster). */
    val square: Boolean = false,
    val onClickHaptic: (() -> Unit)? = null,
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
    // Only measured when some button must stay as wide as the row is tall.
    val needsHeight = buttons.any { it.square }
    var rowHeight by remember { mutableStateOf(0.dp) }
    val scheme = MaterialTheme.colorScheme
    val standardPadding = PaddingValues(horizontal = 14.dp, vertical = GapRow)
    ExpressiveButtonGroup(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .then(if (needsHeight) Modifier.onSizeChanged { rowHeight = with(density) { it.height.toDp() } } else Modifier),
        spacing = SplitSeam,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = horizontalAlignment,
        // One connected control must never break onto two lines: when room runs out it compacts to symbols.
        wrap = false,
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
                    shapeForCorner = { morph, cornerPercent -> clusterShape(buttons, i, morph, cornerPercent) },
                    pillCornerPercent = PillCornerPercent,
                    morphedCornerPercent = MorphedCornerPercent,
                    minHeight = 0.dp,
                    groupWeight = b.weight,
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(if (b.square) Modifier.widthIn(min = rowHeight) else Modifier)
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

/** The silhouette of button [index] in a cluster: round at the cluster's ends, a seam corner wherever it meets a neighbour. */
internal fun clusterShape(buttons: List<ClusterButton>, index: Int, morph: Float, cornerPercent: Int): Shape =
    connectedShape(index == 0, index == buttons.lastIndex, cornerPercent, morph, morph)
