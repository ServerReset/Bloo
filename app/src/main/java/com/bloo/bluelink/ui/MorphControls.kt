package com.bloo.bluelink.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The icon-only button: a [MorphButton] with a 40dp target and no padding. Quiet by default (no container, no rim:
 * close, copy, edit); [filled] gives it the tonal circle (or the [emphasis] colour), so it is the same button as every
 * other, just without a background until asked for one.
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    filled: Boolean = false,
    emphasis: ButtonEmphasis = ButtonEmphasis.Tonal,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    MorphButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
        enabled = enabled,
        interactionSource = interactionSource,
        containerColor = if (filled) emphasis.container() else Color.Transparent,
        contentColor = if (filled) emphasis.content() else LocalContentColor.current,
        border = if (filled) BorderStroke(1.dp, scheme.outline.copy(alpha = 0.18f)) else null,
        contentPadding = PaddingValues(0.dp),
        minHeight = 0.dp,
        groupWeight = 0f,
    ) { content() }
}

/**
 * A unified selectable chip: a pill when unselected, morphing into a filled rounded box when
 * selected.
 */
@Composable
fun MorphChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val haptics = LocalHaptics.current
    val chipSelected = selected
    // Wrapped like every other button so a chip presses the same way (takes width from neighbours
    // in an ExpressiveButtonRow, grows on its own).
    val chipSource = remember { MutableInteractionSource() }
    SafeExpansiveButton(interactionSource = chipSource, enabled = true) {
    // The same MorphButton as everywhere: pill when idle, primary fill + rounded box when selected,
    // with the shared default corner percents.
    MorphButton(
        onClick = { onClick() },
        onClickHaptic = { haptics?.tick() },
        interactionSource = chipSource,
        active = selected,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = GapRow),
        // Same target height as every other button.
        minHeight = ButtonTargetHeight,
        // `selected` semantics for TalkBack. Captured into a differently-named local because inside
        // semantics{} `selected` resolves to the SemanticsPropertyReceiver's property.
        modifier = modifier.semantics { this.selected = chipSelected },
    ) {
        // The shared label (standard glyph gap, compacts to glyphs when short on room). SemiBold
        // when selected like every button label; the filled container already signals selection.
        if (icon != null) {
            MorphButtonLabel(icon, label, pending = false)
        } else {
            ButtonLabelText(label)
        }
    }
    }
}

/** The expand control for pebbles with no action button: the chevron on its own, a single fully round button. */
@Composable
internal fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    /** Reports the chevron's held-down state, so the pebble card can square its own shape with it. */
    onPressChange: ((Boolean) -> Unit)? = null,
) = SplitExpandButton(action = null, expanded = expanded, onToggle = onToggle, onChevronPressChange = onPressChange)
