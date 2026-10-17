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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.bloo.uicommon.MorphButtonCore

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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon)
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            body()
        }
    }
}

/**
 * The small tinted circle a pebble's leading glyph sits in -- the phone's own card-header language
 * (a tonal icon badge) at watch scale, so a pebble reads the same on both surfaces.
 */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.18f), modifier = Modifier.size(28.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(6.dp))
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
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    switch: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (active) scheme.onPrimary else scheme.onSecondaryContainer
    WearButton(
        active = active,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
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
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
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
 * The watch's own [MorphButtonCore]: a heavy haptic on tap and the watch's container roles
 * (secondaryContainer idle, primary active). Shared by [WearActionRow] and [WearQuickActions] so the
 * two can't drift on either -- internal rather than private because those two now live in their own
 * files, which is the point of the split.
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
    MorphButtonCore(
        onClick = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        active = active,
        containerColor = scheme.secondaryContainer,
        activeContainerColor = scheme.primary,
        contentPadding = contentPadding,
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
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        textAlign = TextAlign.Center,
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
