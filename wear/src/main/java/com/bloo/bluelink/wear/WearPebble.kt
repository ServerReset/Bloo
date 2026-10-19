package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphButton
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.standardButtonIcon
import com.bloo.uicommon.RevealText

/**
 * The watch's small card: a round-friendly container with a title row (icon + title) and a body.
 *
 * The body is a slot rather than a fixed layout, so a pebble is whatever the caller puts in it -- a
 * row of switches, a block of text, a status readout. [WearPebble] owns the card, nothing else.
 */
@Composable
fun WearPebble(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    // No card around it: on the phone a section is a quiet heading over its own pills, so on the watch the
    // pebble is a small heading and the rows beneath it carry the shape.
    // Inset from both edges: on a round face a full-width row is cut by the bezel at the top and bottom.
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().fitRound(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            RevealText(
                title,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelMedium,
                // Explicit: the shared component draws with the phone Material3's Text, whose
                // unspecified colour is its own LocalContentColor rather than this theme's.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        body()
    }
}

/**
 * A single action inside a [WearPebble] -- the shared phone [MorphButtonCore], so the watch's tap
 * target morphs and presses exactly like every other Bloo button.
 *
 * [switch] marks the row as an on/off setting rather than a one-shot action, and it changes two
 * things. A trailing indicator, because a filled container is a weak signal for "on" when every row
 * in the pebble is a filled container. And the spoken role, because a screen reader otherwise
 * announces a toggle as a plain button and never says which way it is set.
 */
@Composable
fun WearActionRow(
    label: String,
    /** Null asks the app's own vocabulary for the glyph this label uses (see [standardButtonIcon]). */
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    switch: Boolean = false,
    /** The command is in flight: the glyph becomes the app's shared spinner. */
    pending: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.onPrimary else scheme.onSurface
    // The app's glyph for this label when the caller has none, and the app's own icon+label layout --
    // the one that drops the label rather than truncating a word, and swaps the glyph for the shared
    // in-flight spinner. Boxed because the label has to take the row's spare width here, so the
    // trailing switch indicator stays on the end.
    val glyph = icon ?: standardButtonIcon(label)
    WearButton(
        active = active,
        enabled = enabled && !pending,
        modifier = modifier
            .fillMaxWidth()
            .fitRound()
            .then(
                if (switch) {
                    Modifier.semantics {
                        role = Role.Switch
                        toggleableState = if (active) ToggleableState.On else ToggleableState.Off
                    }
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        // The glyph (or the in-flight spinner) and the label side by side, the label taking the row and wrapping to a
        // second line before it is ever cut. The shared icon-and-label layout DROPPED the label when it did not fit,
        // which left long rows ("Lock when I walk away", "Top left: Lock / unlock") as an icon and nothing else.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides tint,
                ) {
                    MorphButtonLabel(icon = glyph, label = "", pending = pending, iconSize = 18.dp)
                }
                Spacer(Modifier.width(10.dp))
            }
            RevealText(
                label,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (switch) {
            Spacer(Modifier.width(8.dp))
            // A filled dot when on, a hollow ring when off -- drawn rather than an icon so it reads at
            // 16dp, where a glyph-based switch icon turns to mud.
            Spacer(
                Modifier
                    .size(16.dp)
                    .background(if (active) tint else Color.Transparent, CircleShape)
                    .border(2.dp, tint, CircleShape),
            )
        }
    }
}

/**
 * The watch's buttons ARE the app's buttons now, not a second set built on the same core: this
 * delegates to [MorphButton], so the pill-to-rounded-square morph, the inert frost and the press
 * feedback are the ones every phone button uses.
 *
 * Two things are the watch's own, and both are about a 227dp round face: no minimum height
 * (ButtonTargetHeight is 56dp, most of the screen) and no press-grow, which widens a button into its
 * row -- on a watch that means into the bezel. The heavy haptic is passed explicitly because the app's
 * is provided through LocalHaptics, which the watch does not set up.
 */
@Composable
internal fun WearButton(
    active: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        active = active,
        // Glass, as on the phone: a translucent pill that lets the aurora through; the filled primary is
        // kept for a switch that is on.
        containerColor = scheme.surfaceContainerHigh.copy(alpha = 0.6f),
        contentColor = scheme.onSurface,
        activeContainerColor = scheme.primary,
        activeContentColor = scheme.onPrimary,
        contentPadding = contentPadding,
        minHeight = 0.dp,
        expressive = false,
        onClickHaptic = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        },
        content = content,
    )
}

/**
 * A centred block of text for the round face -- the car name, the state line, empty/status
 * messages. Centred horizontally and constrained so nothing runs under the bezel.
 */
@Composable
fun WearCenteredText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onBackground,
    fontWeight: FontWeight? = FontWeight.SemiBold,
    maxLines: Int = 2,
) {
    RevealText(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp).fitRound(),
        textAlign = TextAlign.Center,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
