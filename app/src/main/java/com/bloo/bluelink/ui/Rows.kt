package com.bloo.bluelink.ui

import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GroupHeading
import com.bloo.uicommon.LabelValueRow
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.AnimatedValue

// --- Settings -------------------------------------------------------------
// (The settings screen lives in SettingsScreen.kt's family.)

// --- Small reusable pieces ------------------------------------------------

/**
 * The label of a label/value row, in the muted tone every such row uses (a status row, the account's password
 * row): bodyMedium at [MutedContentAlpha] -- 0.92 in a forced-open context (LocalForceExpanded) so the pair
 * stays distinguishable -- on one line, so a long label clips instead of wrapping per character.
 */
@Composable
internal fun RowLabel(label: String, modifier: Modifier = Modifier) {
    RevealText(
        label,
        modifier = modifier,
        style = SecondaryTextStyle(),
        color = LocalContentColor.current.copy(alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun StatusRow(label: String, value: String, valueMono: Boolean = false) {
    // The shared label-and-value row (:uicommon's LabelValueRow); the app only chooses the label's muted tone.
    LabelValueRow(
        label = label,
        value = value,
        labelColor = LocalContentColor.current.copy(alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha),
        valueMono = valueMono,
    )
}

/** A fact whose value is long (an address): the label above it and the value below, full width, instead of a squeezed half row. */
@Composable
internal fun StackedRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        RowLabel(label)
        RevealText(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A small bold group heading used inside the Car-info pebble. */
@Composable
internal fun SectionLabel(text: String) {
    // The shared sub-heading (:uicommon's GroupHeading): a heading to screen readers, muted a little under its section's title.
    GroupHeading(text, modifier = Modifier.padding(top = 2.dp), color = LocalContentColor.current.copy(alpha = 0.85f))
}

@Composable
internal fun StepRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // bodySmall keeps slider labels compact.
        RevealText(
            label,
            Modifier.weight(1f),
            style = SecondaryTextStyle(),
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
        ) { v -> RevealText(v, style = SecondaryTextStyle(), fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1) }
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
    RevealText(
        text,
        style = secondaryTextStyle(),
        modifier = modifier.padding(top = 2.dp, bottom = bottomGap),
        color = secondaryTextColor(),
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
        RevealText(
            label,
            Modifier.weight(1f),
            style = SecondaryTextStyle(),
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
            // Cap at 2 lines; long labels wrap at spaces rather than growing the row.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(GapGroup))
        MorphToggleTrack(checked)
    }
}
