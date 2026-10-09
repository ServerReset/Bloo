package com.bloo.bluelink.ui

/** The pebble header row, split out of PebbleShell.kt. */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

/**
 * Minimum width the header leaves for the title before the action button may take the rest. Zero on
 * purpose: the title column is weighted and truncates, so it already yields to the button, and any
 * fixed floor here (this was 140dp) subtracts from a narrow card's button cap and forces icon-only
 * even when there is obvious room.
 */
private val HeaderActionTitleFloor = 0.dp

/**
 * The pebble's header row: glyph, title, summary, and at the far end the split action button or the
 * chevron.
 */
@Composable
internal fun PebbleHeaderRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    expanded: Boolean,
    forceExpanded: Boolean,
    canToggle: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier,
    titleColor: Color,
    growTitleOnExpand: Boolean,
    titleTrailing: (@Composable () -> Unit)?,
    titleTrailingAtEnd: Boolean,
    headerContent: (@Composable () -> Unit)?,
    headerAction: PebbleHeaderAction?,
    rowWidthDp: Dp,
    onMeasured: (heightPx: Int, widthDp: Dp) -> Unit,
    onChevronPressChange: (Boolean) -> Unit,
) {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    // Header: tap anywhere to toggle, long-press to drag-reorder; the action button and chevron
    // handle their own clicks. headerActionMaxWidth reserves room for the icon, gaps and a small
    // title floor before SplitExpandButton gets the rest. The floor is deliberately small because
    // the title column is WEIGHTED: it already takes whatever the button leaves, so a large fixed
    // floor (this was 140dp) left the button a near-zero cap on a narrow card and forced it to
    // icon-only even with obvious room beside a short title like "Charge".
    val headerActionMaxWidth = (
        rowWidthDp - 16.dp - 20.dp - ButtonIconGap - 10.dp - 12.dp - HeaderActionTitleFloor
        ).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            // Feeds collapsedCorner: this row's height is the collapsed card height and is stable
            // across the animation. Also feeds headerActionMaxWidth from the same callback (see
            // rowWidthDp).
            .onSizeChanged {
                onMeasured(it.height, with(density) { it.width.toDp() })
            }
            .then(
                if (forceExpanded || !canToggle) Modifier
                else Modifier.clickable {
                    if (expanded) haptics?.tick() else haptics?.click()
                    onToggle()
                },
            )
            .then(modifier)
            .heightIn(min = PebbleHeaderHeight)
            // Asymmetric padding: PebbleContentInset left, GapGroup right.
            .padding(start = PebbleContentInset, end = GapGroup, top = GapRow, bottom = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tinted with [titleColor] so the icon matches the title (the hero draws over a scrimmed
        // photo). takeOrElse: titleColor is Unspecified for other pebbles, which Icon would treat
        // as an untinted raw vector.
        Icon(
            icon,
            contentDescription = null,
            tint = titleColor.takeOrElse { LocalContentColor.current },
            modifier = Modifier.size(20.dp),
        )
        // ButtonIconGap: the standard icon-then-label gap.
        Spacer(Modifier.width(ButtonIconGap))
        Column(Modifier.weight(1f)) {
            PebbleTitleRow(
                title = title,
                expanded = expanded,
                growTitleOnExpand = growTitleOnExpand,
                titleColor = titleColor,
                titleTrailing = titleTrailing,
                titleTrailingAtEnd = titleTrailingAtEnd,
            )
            if (summary != null) {
                AnimatedContent(
                    targetState = summary,
                    transitionSpec = { expandContentTransform() },
                    label = "pebbleSummary",
                ) { s -> RollingNumber(
                    s,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = mutedContentColor(),
                ) }
            }
            headerContent?.invoke()
        }
        // AnimatedVisibility wraps the gap and control together so the trailing area slides/fades
        // as one unit when a single-setting pebble loses its chevron (inlineSettingInSimpleMode).
        AnimatedVisibility(
            visible = !forceExpanded && (headerAction != null || canToggle),
            enter = fadeIn(tween(MotionShort)) + expandHorizontally(tween(MotionShort)),
            exit = fadeOut(tween(MotionFast)) + shrinkHorizontally(tween(MotionFast)),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        // Gap between the weighted text column and the trailing control.
        Spacer(Modifier.width(GapGroup))
            if (headerAction != null) {
                // Renders the action half regardless; canToggle decides whether the chevron half
                // comes with it (see SplitExpandButton).
                SplitExpandButton(
                    action = headerAction,
                    expanded = expanded,
                    onToggle = onToggle,
                    canToggle = canToggle,
                    modifier = Modifier.widthIn(max = headerActionMaxWidth),
                    onChevronPressChange = onChevronPressChange,
                )
            } else if (canToggle) {
                // Gated on canToggle so a pebble with nothing to disclose draws no chevron.
                MorphExpandButton(
                    expanded = expanded,
                    onToggle = onToggle,
                    onPressChange = onChevronPressChange,
                )
            }
        }
        }
    }
}
