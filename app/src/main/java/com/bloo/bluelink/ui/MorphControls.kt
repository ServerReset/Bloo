package com.bloo.bluelink.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape

/**
 * The morph family's icon-only member: [IconButton]'s containerless chrome and 40dp target, plus
 * the family's click haptic and a press scale dip on the [SoftDamping] spring (no container corner
 * to morph).
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    var everInert by remember { mutableStateOf(!enabled) }
    SideEffect { if (!enabled) everInert = true }
    val frost = if (everInert || !enabled) {
        Modifier.frosted(!enabled, CircleShape, blurRadius = 1.2.dp, rim = false, veil = false)
    } else {
        Modifier
    }
    val body: @Composable () -> Unit = {
        IconButton(
            onClick = { haptics?.click(); onClick() },
            modifier = modifier.then(frost),
            enabled = enabled,
            interactionSource = interactionSource,
            content = content,
        )
    }
    // Joins a group when in one (see MorphButton), e.g. the snackbar's copy/dismiss pair.
    if (LocalExpressiveGroup.current) {
        SafeExpansiveButton(
            interactionSource = interactionSource,
            enabled = enabled,
        ) { body() }
    } else {
        body()
    }
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
            Text(
                label,
                style = ButtonLabelStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
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
