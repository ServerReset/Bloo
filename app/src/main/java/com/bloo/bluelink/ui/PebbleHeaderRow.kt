package com.bloo.bluelink.ui

/**
 * The collapsible "pebble" shell family, peeled out of Pebbles.kt (which keeps
 * the per-section pebble composites and list plumbing). This file owns the
 * generic [Pebble] wrapper, the [PebbleShell] expand/collapse card, its
 * [PebbleHeaderAction] action model, and the split [SplitExpandButton] control
 * (action + chevron nub). Same package, so Pebbles.kt's call sites stay
 * internal-visible and verbatim.
 */

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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
 * The pebble's header row: its glyph, title and summary, and at the far end either the split
 * action button or the expand chevron. Peeled out of [PebbleShell]. Tapping anywhere on it toggles
 * the pebble (unless it is forced open or cannot toggle); [onMeasured] reports its size back so the
 * shell can round its corner off the row's own height and cap the action button's width.
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
    // Header: tap anywhere to toggle, long-press to drag-reorder. The
    // action button and chevron handle their own clicks. Fixed min height
    // so every collapsed pebble lines up.
    //
    // headerActionMaxWidth reserves room for "the other stuff in the row"
    // (the leading icon, the two gaps either side of the title column, the
    // row's own start/end padding, and a floor for the title itself) before
    // handing whatever's left to SplitExpandButton. Without this, the action
    // button -- a plain, non-weighted Row child -- was measured against the
    // row's FULL width (Row measures non-weighted children before it knows
    // what its weighted sibling, the title column, will need), so its own
    // already-existing compact-to-icon-only fit rule never had a reason to
    // fire: it always concluded it had more than enough room for "Summarize",
    // even while the title beside it ("AI Summary" -> "AI summ...") and its
    // status line ("Not summarized" -> "Not summar...") were being ellipsized
    // for space the button was never actually using. Reported from a real
    // screenshot: text truncating on the left while the button keeps its full
    // label on the right, the opposite of the intended priority.
    //
    // 140dp for the title floor -- raised from an initial 90dp, which turned
    // out to still lose the tug-of-war: 90dp was sized for the TITLE alone
    // ("AI Summary", "Diagnostics") and this column also carries the STATUS
    // line right underneath it ("Not summarized", "Imperial", "Atkinson"),
    // a second, independently-ellipsizing Text competing for the exact same
    // width. 140dp comfortably covers either line at 1x font scale with real
    // margin, not just the shorter of the two -- explicitly biased toward the
    // text over the button, per the follow-up report that it should be. This
    // is a floor for how much the ACTION BUTTON gives way, not a guarantee the
    // text never ellipsizes: a title long enough to still exceed 140dp keeps
    // ellipsizing on its own past that, same as before -- this only changes
    // who gives way FIRST when the two compete.
    val headerActionMaxWidth = (
        rowWidthDp - 16.dp - 20.dp - ButtonIconGap - 10.dp - 12.dp - 140.dp
        ).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            // Feeds collapsedCorner above: this row's height IS the
            // whole card's collapsed height (the body is hidden then),
            // and it's stable across the expand/collapse animation
            // itself (only the body grows/shrinks below it), so this
            // never fires mid-bounce with a transient wrong value. Also
            // feeds headerActionMaxWidth above, off the SAME callback --
            // see rowWidthDp's own doc for why this replaced a
            // BoxWithConstraints wrapper here.
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
            // Asymmetric padding: 16dp left, 12dp right (was 16dp),
            // pushing buttons slightly right while keeping symmetry.
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tinted with [titleColor], not left to inherit LocalContentColor.
        // This icon sits IMMEDIATELY before the title on the same row and on
        // the same backdrop, so the two must resolve their colour from the
        // same place -- and until now only the title did. That split is the
        // reported "the car icon before the car's name doesn't follow the
        // theme" bug, raised repeatedly: on the hero the header is drawn over
        // a scrimmed car photo and the title travels to heroOnPhoto for it
        // (see titleColor's own doc), while this Icon kept inheriting the
        // CARD's content colour -- near-black in a light theme, and tinted by
        // whatever slice of the seed colour an active custom palette
        // feeds onSurfaceVariant. So the name was legible over the photo and
        // the glyph 10dp to its left was not, in the app's light theme AND in
        // any custom palette.
        //
        // takeOrElse, not a bare pass-through: titleColor defaults to
        // Color.Unspecified for every pebble but the hero, and Icon treats
        // Unspecified as "no ColorFilter at all" -- i.e. the raw vector's own
        // colour rather than the inherited content colour -- which would have
        // turned this fix into a regression on all ~10 other pebbles. Falling
        // back to LocalContentColor keeps them byte-identical to before and
        // leaves the hero as the one card that overrides.
        Icon(
            icon,
            contentDescription = null,
            tint = titleColor.takeOrElse { LocalContentColor.current },
            modifier = Modifier.size(20.dp),
        )
        // ButtonIconGap, not a bespoke 10dp: this is the exact same "icon, then
        // label" pair MorphButtonLabel standardises everywhere else in the app,
        // and it was the one place still stating that gap by hand.
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
                    color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                ) }
            }
            headerContent?.invoke()
        }
        // AnimatedVisibility around BOTH the gap and the control it precedes --
        // together, so the whole trailing area slides/fades away as one unit
        // instead of the Spacer's width popping independently of what follows
        // it. This is the OTHER half of the settings-mode header animation (see
        // titleTrailing's own AnimatedVisibility above): a single-setting pebble
        // with no headerAction loses its chevron entirely the moment
        // inlineSettingInSimpleMode takes over in simple mode (canToggle flips
        // false, the `else if (canToggle)` branch below stops matching at all),
        // and that used to just vanish with no transition while titleTrailing's
        // control popped in at the same instant on the same row. No "last known
        // value" snapshot needed here unlike titleTrailing's: headerAction/
        // onToggle/expanded aren't conditionally null the way titleTrailing was,
        // they're just not rendered while `visible` is false.
        AnimatedVisibility(
            visible = !forceExpanded && (headerAction != null || canToggle),
            enter = fadeIn(tween(MotionShort)) + expandHorizontally(tween(MotionShort)),
            exit = fadeOut(tween(MotionFast)) + shrinkHorizontally(tween(MotionFast)),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        // The gap between the header's text column and whatever control ends the
        // row. Without it a title-row status ran straight into the chevron --
        // "Imperial" and "Atkinson" touching the button beside them, reported
        // from a real screenshot. The text column is weighted, so nothing else
        // was ever going to introduce this space.
        Spacer(Modifier.width(10.dp))
            if (headerAction != null) {
                // Renders the action half regardless; canToggle decides whether
                // the chevron half comes with it (and reshapes the action's
                // seam corner when it does not -- see SplitExpandButton).
                SplitExpandButton(
                    action = headerAction,
                    expanded = expanded,
                    onToggle = onToggle,
                    canToggle = canToggle,
                    modifier = Modifier.widthIn(max = headerActionMaxWidth),
                    onChevronPressChange = onChevronPressChange,
                )
            } else if (canToggle) {
                // Gated on canToggle, which this branch used to ignore. Without
                // the gate a pebble with nothing to disclose still drew a
                // chevron and, since onToggle is a no-op in that state, tapping
                // it did nothing -- the exact "there should be no chevron" case
                // that inlineSettingInSimpleMode and SettingsCard's inlineSetting
                // exist to produce. Only pebbles carrying a headerAction ever
                // honoured canToggle, purely because that path happened to
                // forward it.
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
