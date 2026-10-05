package com.bloo.bluelink.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.first

/**
 * The morph family's icon-only member: [IconButton]'s containerless chrome and 40dp target, plus
 * the family's click haptic and a press scale dip on the [SoftDamping] spring (no container
 * corner to morph). Same parameters as [IconButton]; remove any hand-called haptic at converted
 * call sites since it fires here.
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
 * A unified selectable chip: a pill when unselected, morphing into a filled rounded box when selected.
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
        // The shared label (standard glyph gap, compacts to glyphs when short on room). SemiBold when
        // selected like every button label; the filled container already signals selection.
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

/**
 * Right-side expand control for pebbles with no action button: a pill that morphs to a
 * rounded square when the section is open.
 */
@Composable
internal fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    /** Reports this button's live pressed state so the pebble card can square its outer shape
     *  with the chevron's. Null for callers that don't care. */
    onPressChange: ((Boolean) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    // Shared with SplitExpandButton's chevron; see [rememberChevronSpin].
    val chevron = rememberChevronSpin(expanded, label = "morphChevron")
    // A FIXED 50dp square: 50% is a true circle and 10dp is 20%; overrides the default 28 to keep
    // the 10dp corners. With expansion animation.
    val chevronSource = remember { MutableInteractionSource() }
    if (onPressChange != null) {
        val pressed by chevronSource.collectIsPressedAsState()
        LaunchedEffect(pressed) { onPressChange(pressed) }
        // Clear on leaving composition (e.g. a collapse unmounting mid-press); the effect above
        // only reacts to changes.
        DisposableEffect(Unit) { onDispose { onPressChange(false) } }
    }
    SafeExpansiveButton(
        interactionSource = chevronSource,
        enabled = true,
    ) {
        MorphButton(
            onClick = { onToggle() },
            onClickHaptic = { if (expanded) haptics?.tick() else haptics?.click() },
            onLongClick = {
                // Easter egg: hold the chevron to spin it + vibrate.
                chevron.spin()
                haptics?.heavy()
            },
            // Expanded highlight = the same active state as lock/unlock (MorphButton defaults).
            active = expanded,
            interactionSource = chevronSource,
            contentPadding = PaddingValues(0.dp),
            pillCornerPercent = 50f,
            morphedCornerPercent = 20f,
            minHeight = 0.dp,
            // As SplitExpandButton's chevron: contentDescription is the next action, this is the current
            // state. Tap toggles; holding spins the chevron without toggling.
            // ButtonTargetHeight like everything tappable.
            modifier = Modifier
                .size(ButtonTargetHeight)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            ExpandChevronIcon(expanded, chevron)
        }
    }
}
