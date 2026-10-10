package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

// --- Settings -------------------------------------------------------------
// (The settings screen lives in SettingsScreen.kt's family.)

// --- Small reusable pieces ------------------------------------------------

@Composable
internal fun StatusRow(label: String, value: String, valueMono: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        // Top-align so a wrapped value leaves the label on the first line.
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            // Natural width, not weight(1f): a 50/50 split starved the value (the long side) into
            // wrapping. The value below is the row's one weighted child.
            style = MaterialTheme.typography.bodyMedium,
            // MutedContentAlpha reads as a secondary label; 0.92 in a forced-open context
            // (LocalForceExpanded) so the pair stays distinguishable.
            color = LocalContentColor.current.copy(
                alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
            ),
            // Capped defensively so a long label clips to one line instead of wrapping per
            // character.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        // Right-aligning Box owns the label's leftover width: short values sit flush right, long
        // ones (coordinates, VIN, email) use the extra width. A filling Box gives textAlign=End
        // something to align against.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // AnimatedValue (uicommon). Colour pinned to onSurface: Pebble's Card would otherwise
            // give onSurfaceVariant, barely distinct from the dimmed label.
            val baseStyle = LocalTextStyle.current
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            // Memoized to avoid recreating TextStyle.copy() every recomposition.
            val valueStyle = remember(baseStyle, onSurfaceColor, valueMono) {
                baseStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor,
                    textAlign = TextAlign.End,
                    fontFamily = if (valueMono) FontFamily.Monospace else baseStyle.fontFamily,
                )
            }
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = valueStyle,
                maxLines = 2,
                reduceMotion = LocalReduceMotion.current,
            )
        }
    }
}

/** A small bold group heading used inside the Car-info pebble. */
@Composable
internal fun SectionLabel(text: String) {
    TitleSmallText(
        text,
        modifier = Modifier.padding(top = 2.dp),
        color = LocalContentColor.current.copy(alpha = 0.85f),
    )
}

@Composable
internal fun StepRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // bodySmall keeps slider labels compact.
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapRow))
        // Roll the value when it changes (e.g. dragging a slider).
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stepValue",
        ) { v -> Text(v, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1) }
    }
}

/**
 * The app's one toggle control for boolean settings: a custom pill track+thumb (spring-timed like
 * [MorphButton]) in place of a stock Material [Switch].
 */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    /**
     * The explanatory line under the switch. Owning it here gives one style and rhythm, and keeps
     * it outside the toggleable so TalkBack reports a single switch.
     */
    description: String? = null,
    onChange: (Boolean) -> Unit,
) {
    // No wrapper at all when there is no caption, so every existing call site keeps exactly the
    // layout it had -- a Column around a single fillMaxWidth Row measures the same, but "the same"
    // is not worth asserting across ~25 call sites for a branch that costs nothing.
    if (description == null) {
        ToggleRowControl(label, checked, modifier, onChange)
    } else {
        Column(modifier.fillMaxWidth()) {
            ToggleRowControl(label, checked, onChange = onChange)
            SettingsCaption(description)
        }
    }
}

/**
 * The caption style shared by [ToggleRow]'s `description` and switchless settings rows. Bottom
 * padding: it belongs to the control it explains.
 */
@Composable
internal fun SettingsCaption(
    text: String,
    modifier: Modifier = Modifier,
    /**
     * The gap below. The default is the group gap, because a caption normally trails the control it
     * explains and what matters is the distance to the NEXT one. Pass a smaller one where the
     * caption instead LEADS its own control, so the two read as a pair.
     */
    bottomGap: Dp = GapGroup,
) {
    Text(
        text,
        modifier = modifier.padding(top = 2.dp, bottom = bottomGap),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The `toggleable` (for checked/Role.Switch semantics), no-ripple and toggle-haptics wrapper shared
 * by [InlineToggle] and [ToggleRowControl].
 */
@Composable
private fun Modifier.hapticToggleable(checked: Boolean, onChange: (Boolean) -> Unit): Modifier {
    val haptics = LocalHaptics.current
    // No blockPageSwipe: a sideways drag from a toggle swipes the page (a real tap is inside the
    // touch slop, so the toggle still wins).
    return toggleable(
        value = checked,
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Switch,
    ) {
        val next = !checked
        if (next) haptics?.toggleOn() else haptics?.toggleOff()
        onChange(next)
    }
}

/**
 * A bare toggle (no row or label) for a card whose whole body is one setting; same track and
 * semantics as [ToggleRow].
 */
@Composable
internal fun InlineToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Box(modifier.hapticToggleable(checked, onChange)) {
        MorphToggleTrack(checked)
    }
}

@Composable
private fun ToggleRowControl(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            // toggleable gives a Role.Switch + checked node; the track clears its own so TalkBack
            // sees one toggle.
            .hapticToggleable(checked, onChange)
            .padding(vertical = GapHairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            // Cap at 2 lines; long labels wrap at spaces rather than growing the row.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapGroup))
        MorphToggleTrack(checked)
    }
}
