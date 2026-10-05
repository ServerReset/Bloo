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

/**
 * A chunky stateful control: shows the current state and a button offering the opposite action. The
 * button morphs from a pill (calm) to a rounded square (highlighted).
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
    // The row's measured width via onSizeChanged (BoxWithConstraints would subcompose on every car
    // page). The 1000.dp default is deliberately high so the first frame is never falsely
    // compacted.
    var rowWidthDp by remember { mutableStateOf(1000.dp) }
    val density = LocalDensity.current
    // Caps the button group's width: Row measures non-weighted children first, and the column's
    // widthIn(min) cannot raise a smaller incoming max, so without a cap "Unlocked" truncates.
    val groupMaxWidth = (rowWidthDp - 120.dp - 12.dp).coerceAtLeast(0.dp)
    Row(
        Modifier
            .fillMaxWidth()
            // heightIn(min): the label and button text outgrow ControlHeight at large font sizes.
            .heightIn(min = ControlHeight)
            .onSizeChanged { rowWidthDp = with(density) { it.width.toDp() } },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Fill the button's height so the status reads as one tall control.
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
                !enabled -> mutedContentColor()
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> mutedContentColor()
            }
            val stateColor by androidx.compose.animation.animateColorAsState(
                stateColorTarget,
                animationSpec = tween(MotionMedium),
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
                            horizontalArrangement = Arrangement.spacedBy(GapRow),
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
                                (fadeIn(tween(MotionShort)) + scaleIn(initialScale = 0.85f, animationSpec = tween(MotionShort))) togetherWith
                                (fadeOut(tween(MotionFast)) + scaleOut(targetScale = 1.1f, animationSpec = tween(MotionFast)))
                            },
                            // Centre-aligned: "Locked"/"Unlocked" differ in intrinsic height, which
                            // nudged the control.
                            contentAlignment = Alignment.CenterStart,
                            label = "lockStateAnim",
                        ) { (ic, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GapRow),
                            ) {
                                // null: the Text beside it carries the same words (avoids a
                                // redundant swipe stop).
                                Icon(ic, contentDescription = null, tint = stateColor, modifier = Modifier.size(22.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
                                    // Ellipsize rather than wrap mid-word when squeezed.
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
        // 12dp spacing between text and buttons.
        Spacer(Modifier.width(GapGroup))
        val haptics = LocalHaptics.current
        // Extra icon actions plus the lock/unlock button form one connected button group (a single
        // child of the outer Row, so the outer spacing isn't piled on top).
        val segmentCount = groupActions.size + 1
        // Bigger, thumb-friendly hit targets on the cover screen (operated by a thumb on a ~1-inch
        // square) than on the phone (mouse-precise finger taps in a full pebble). Only the cover
        // provides LocalPebbleFillHeight (see its doc in Widgets.kt), so that is the one that
        // actually means "cover".
        val groupBtnSize = 50.dp
        val actionIconSize = 22.dp
        // Modifier.size on a segment is coerced to the width the group assigns.
        val mainSource = remember { MutableInteractionSource() }
        // Reserves width for both "Lock" and "Unlock" labels so the button doesn't collapse mid
        // optimistic flip.
        val lockContent: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = actionIconSize)
                Box(Modifier.alpha(0f)) {
                    MorphButtonLabel(icon, if (isOn == true) turnOn else turnOff, false, iconSize = actionIconSize)
                }
            }
        }
        // The lock/unlock button itself: alone it keeps the pill<->rounded-square morph; as the
        // last segment of a connected group the group's static silhouette ([shapeForCorner]) takes
        // over.
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
            // A lone member has no neighbour to take width from, so wrapping it in the group gave
            // it a press fraction that was faithfully computed and just as faithfully multiplied by
            // a reserve of zero -- not a bug in the animation itself, a design that only works with
            // two or more real segments, silently applied to the one-segment case too.
            mainButton(null)
        } else {
            ExpressiveButtonRow(
                // Caps the group's room at groupMaxWidth (see above).
                modifier = Modifier.widthIn(max = groupMaxWidth),
                spacing = SplitSeam,
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
                // Pill when off, rounded rectangle + highlight when on; in a group the static
                // connected shape takes over.
                mainButton { morph, cp -> connectedGroupShape(segmentCount - 1, segmentCount, cp, morph) }
            }
        }
    }
}
