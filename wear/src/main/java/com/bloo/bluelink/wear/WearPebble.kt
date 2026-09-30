package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphButtonCore

/**
 * One watch "pebble": a small round-friendly card with a title row (icon + title) and a body.
 *
 * Sized and padded for a watch bezel: generous internal padding and a squircle radius, and the
 * whole thing is meant to sit centred in the round face rather than edge-to-edge (Wear's
 * `ScalingLazyColumn` does that centring for the item under the crown). Kept local to :wear --
 * the phone's PebbleShell lives in :app and pulls in the whole app theme.
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

/** The small tinted circle a pebble's leading glyph sits in -- the phone's own card-header
 *  language (a tonal icon badge) at watch scale, so a pebble reads the same on both surfaces. */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.18f), modifier = Modifier.size(28.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(6.dp))
    }
}

/**
 * A single action inside a [WearPebble] -- the shared phone [MorphButtonCore], so the watch's
 * tap target morphs and presses exactly like every other Bloo button. The foundation core takes
 * its colours as parameters (it cannot reach Material's LocalContentColor), so the watch theme
 * supplies them here.
 */
@Composable
fun WearActionRow(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    MorphButtonCore(
        onClick = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        active = active,
        containerColor = scheme.secondaryContainer,
        activeContainerColor = scheme.primary,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) scheme.onPrimary else scheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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

/** The car's at-a-glance state, used as the page's hero value. */
@Composable
fun WearHero(value: String, caption: String, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
