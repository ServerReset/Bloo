@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.connectedGroupShape
import kotlinx.coroutines.flow.first
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * A chunky stateful control: shows the current state and a button offering the
 * *opposite* action. The button is always a clearly filled control that morphs
 * from a pill (calm) to a rounded square (highlighted).
 */
@Composable
internal fun StateControl(
    name: String,
    isOn: Boolean?,
    stateOn: String,
    stateOff: String,
    turnOn: String,
    turnOff: String,
    icon: ImageVector,
    deactivateIcon: ImageVector? = null,
    pending: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    enabled: Boolean = true,
    disabledNote: String? = null,
    highlightWhenOff: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlightContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    offTextColor: Color? = null,
    groupActions: List<GroupIconAction> = emptyList(),
) {
    // Which state is the "highlighted" (on) one.
    val highlighted = enabled && (if (highlightWhenOff) isOn == false else isOn == true)
    // The row's own real, measured width -- onSizeChanged, not BoxWithConstraints. Both
    // report the same number, but BoxWithConstraints is a SubcomposeLayout under the hood:
    // its content is composed in a SEPARATE pass, deferred until the constraints are known,
    // which is real, avoidable overhead on something this exact composable is not rare --
    // it draws EVERY car's Lock/Unlock row, so every car page composing during a swipe
    // (beyondViewportPageCount = 1 means up to three at once) paid it. onSizeChanged is a
    // plain draw/layout callback on the SAME Row that was always here, no extra composition
    // pass at all -- the identical trade PebbleHeaderHeight's own rowHeightDp already makes
    // a few lines below in this same file's sibling function.
    //
    // Default a generous 1000.dp, not a small one: this value is read to CAP the button
    // group, so an inaccurate LOW default (before the real width lands, one frame after
    // first composition) would wrongly force it to compact on that first frame. A HIGH
    // default instead means the first frame renders exactly as it always did -- uncapped --
    // and the real cap takes over a frame later, imperceptible and safe in the direction
    // that matters (never a false compact, only a one-frame-late correct one).
    var rowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    // Without this cap, the button group -- a plain (non-weighted) Row child -- is measured
    // against the row's FULL width, because Row measures non-weighted children BEFORE it
    // knows what a weighted sibling (the name/state column, `widthIn(min = 120.dp)` a few
    // lines down) will need. That min is a REQUEST, not a guarantee the Row's own weight
    // arithmetic honours on its own: when the row overall is too narrow for both, Row still
    // hands the column `Constraints(minWidth = share, maxWidth = share)` off its OWN
    // (button-first) division of space, and widthIn(min) cannot raise that ABOVE an
    // already-fixed, SMALLER incoming max -- it coerces down to fit the parent's hard upper
    // bound instead of forcing the parent to overflow, which is the opposite of what a
    // "floor" sounds like it should do. So on a narrow enough row, "Unlocked" truncated to
    // "Unlock…" while the button group beside it kept its own full, unconstrained width --
    // reported from a real screenshot, and the fix genuinely requires the button group's own
    // width to be bounded first, not a bigger number on the column's own floor (already tried
    // once, on the pebble-header version of this exact bug, and it only bought a bit more
    // room rather than fixing the actual priority).
    val groupMaxWidth = (rowWidthDp - 120.dp - 12.dp).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(ControlHeight)
            .onSizeChanged { rowWidthDp = with(density) { it.width.toDp() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Fill the button's height so the status reads as one tall control.
        // Standard spacing between label and buttons, matching other pebble controls.
        Column(Modifier.fillMaxHeight().widthIn(min = 120.dp), verticalArrangement = Arrangement.Center) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val stateText = when {
                !enabled && disabledNote != null -> disabledNote
                pending -> "Sending…"
                isOn == true -> stateOn
                isOn == false -> stateOff
                else -> "Unknown"
            }
            val stateColorTarget = when {
                !enabled -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
            }
            val stateColor by androidx.compose.animation.animateColorAsState(
                stateColorTarget,
                animationSpec = tween(250),
                label = "stateColor",
            )
            when {
                // With no title, the lock state is the headline — icon AND word, side by side.
                name.isBlank() -> {
                    val stateIcon = when (isOn) {
                        true -> icon
                        false -> Icons.Filled.LockOpen
                        else -> icon
                    }
                    if (pending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(Modifier.size(22.dp))
                            Text(
                                "Sending…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = stateColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = Pair(stateIcon, stateText),
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 1.1f, animationSpec = tween(150)))
                            },
                            // Default is TopStart: "Locked"/"Unlocked" render at
                            // slightly different intrinsic heights, so without
                            // this the old and new icon+label rows didn't align
                            // to the same vertical center during the crossfade,
                            // reading as the whole control nudging on toggle.
                            contentAlignment = Alignment.CenterStart,
                            label = "lockStateAnim",
                        ) { (ic, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // null, not `label`: the Text right after already
                                // carries the same words, so a non-null
                                // description here was a redundant swipe stop
                                // ("Locked" from the icon, then "Locked" again
                                // from the text) -- purely decorative now that
                                // the label is announced once.
                                Icon(ic, contentDescription = null, tint = stateColor, modifier = Modifier.size(22.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
                                    // If this column ever gets squeezed tight
                                    // again (groupActions content changes,
                                    // narrower screens), ellipsize instead of
                                    // wrapping mid-word ("Locke"/"d" on two
                                    // lines) -- a clipped label at least still
                                    // reads as one intact word.
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                else -> AnimatedContent(
                    targetState = stateText,
                    transitionSpec = { expandContentTransform() },
                    label = "stateTextAnim",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        // 12dp spacing between text and buttons to match the previous spacedBy arrangement
        Spacer(Modifier.width(12.dp))
        val haptics = LocalHaptics.current
        // Any extra icon actions (horn/lights) plus the lock/unlock button
        // form one Material 3 "connected" button group -- a single Row (one
        // child of the outer Row, so its own 2dp spacing isn't also getting
        // the outer Row's 12dp spacedBy piled on top) instead of a separate
        // icon cluster sitting next to an unrelated pill.
        val segmentCount = groupActions.size + 1
        // Bigger, thumb-friendly hit targets on the cover screen (operated by a
        // thumb on a ~1-inch square) than on the phone (mouse-precise finger taps in
        // a full pebble).
        //
        // Gated on LocalPebbleFillHeight, NOT LocalForceExpanded: the comment here used to
        // claim LocalForceExpanded is "true only on the cover" and that was simply wrong --
        // the wide layout's HotspotSlot provides it too, for both of its slots (see
        // HotspotSlot). Only the cover provides LocalPebbleFillHeight (see its doc in
        // Widgets.kt), so that is the one that actually means "cover".
        //
        // This was the reported "the controls pebble looks different and worse" in the
        // multi-column layout, and it was visible ONLY there: ControlsPebble is this
        // composable's only caller, and "controls" has no cover tile at all (see
        // CompactCar's `tiles` mapping), so the cover-sized 58dp/26dp targets could never
        // reach the cover and only ever fired in the hot-spot column -- an over-filled band
        // of chunky buttons in a 76dp-tall card, 8dp taller and with 4dp bigger icons than
        // the identical control renders at in the single-column list, and eating 24dp more
        // of the row's width away from the "Locked"/"Unlocked" label beside it (see
        // groupMaxWidth). The phone sizes are the right ones for a pebble in a column,
        // which is what the hot-spot slot is.
        //
        // Kept as a branch rather than deleted: it is the correct treatment for a genuine
        // cover tile, and the cover already renders pebbles through this same SinglePebble
        // path, so a "controls" tile becoming available there needs no new plumbing.
        val coverTargets = LocalPebbleFillHeight.current
        val groupBtnSize = if (coverTargets) 58.dp else 50.dp
        val actionIconSize = if (coverTargets) 26.dp else 22.dp
        // Standard gap between connected button elements (matches SplitExpandButton's
        // own 3dp gap for visual consistency across all grouped controls).
        // ExpressiveButtonRow, not a plain Row. THIS is the app's clearest "several buttons
        // sharing one space" -- a connected group whose silhouette is one pill -- so it is
        // exactly where pressing one segment should widen it and squeeze the others, with the
        // group's own outer width never changing. Every segment was a bare MorphButton with no
        // press wrapper at all, which is why no amount of work on SafeExpansiveButton ever made
        // this cluster move: there was nothing here to animate.
        //
        // Modifier.size on a segment is not in the way: fixed-size constraints are still
        // coerced into the ones the parent hands down, so the width the group assigns wins.
        // wrap = false: this is a CONNECTED group -- one silhouette with seams in it -- so a
        // seam appearing on the next line down would not be a control at all. Too narrow, and
        // the segments compact instead.
        val mainSource = remember { MutableInteractionSource() }
        // Lock/unlock button content: reserves width for both "Lock" and "Unlock" labels
        // so the button doesn't collapse during the optimistic state flip mid-release.
        // See MorphButton call below for why this matters.
        val lockContent: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = actionIconSize)
                Box(Modifier.alpha(0f)) {
                    MorphButtonLabel(icon, if (isOn == true) turnOn else turnOff, false, iconSize = actionIconSize)
                }
            }
        }
        // The lock/unlock button itself: alone it keeps the pill<->rounded-square morph; as the last
        // segment of a connected group the group's static silhouette ([shapeForCorner]) takes over.
        val mainButton: @Composable (((Float, Int) -> androidx.compose.ui.graphics.Shape)?) -> Unit = { shapeForCorner ->
            MorphButton(
                onClick = { if (isOn == true) onDeactivate() else onActivate() },
                onClickHaptic = { haptics?.heavy() },
                enabled = enabled && !pending,
                interactionSource = mainSource,
                active = highlighted,
                activeContainerColor = highlightColor,
                activeContentColor = highlightContentColor,
                shapeForCorner = shapeForCorner,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = GapRow),
                modifier = Modifier.heightIn(min = groupBtnSize),
                expressive = true,
            ) {
                lockContent()
            }
        }
        if (groupActions.isEmpty()) {
            // No ExpressiveButtonRow at all here -- most cars have no horn/lights support
            // (Kia's US API has none, see the doc above), so this is the common case, and it
            // was reported directly as "the unlock button doesn't animate when tapped": a
            // connected group's OWN defining rule is that its outer footprint never changes
            // on press -- growth is redistributed FROM a neighbour, never from thin air (see
            // ExpressiveButtonGroup's/ExpressivePressGrowth's own doc: "a member with none
            // carries nothing to reserve for"). A lone member has no neighbour to take width
            // from, so wrapping it in the group gave it a press fraction that was faithfully
            // computed and just as faithfully multiplied by a reserve of zero -- not a bug in
            // the animation itself, a design that only works with two or more real segments,
            // silently applied to the one-segment case too. SafeExpansiveButton's OTHER path
            // (outside a group) grows the button for real, which is exactly the fallback a
            // solitary button wants and every other lone MorphButton in the app already gets.
            mainButton(null)
        } else {
            ExpressiveButtonRow(
                // Caps this group's own room at groupMaxWidth -- see that val's own doc above
                // for why the group cannot work this out for itself.
                modifier = Modifier.widthIn(max = groupMaxWidth),
                spacing = 3.dp,
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
                wrap = false,
            ) {
                groupActions.forEachIndexed { i, action ->
                    val actionSource = remember { MutableInteractionSource() }
                    MorphButton(
                        onClick = action.onClick,
                        enabled = action.enabled,
                        interactionSource = actionSource,
                        contentPadding = PaddingValues(0.dp),
                        shapeForCorner = { morph, cp -> connectedGroupShape(i, segmentCount, cp, morph) },
                        modifier = Modifier.size(groupBtnSize),
                        expressive = true,
                    ) { Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(actionIconSize)) }
                }
                // Pill when off, rounded rectangle + highlight colour when on - same
                // as the climate/charge controls -- except when it's part of a
                // group, where the connected shape takes over (see MorphButton's
                // shape param doc): a connected group's silhouette is static, not
                // something one segment morphs independently of the others.
                mainButton { morph, cp -> connectedGroupShape(segmentCount - 1, segmentCount, cp, morph) }
            }
        }
    }
}
