
package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
// `motionScheme` is a MaterialTheme member; no import needed.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
// The `by` State delegate is a file-scope operator extension and needs this explicit import.
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.blockPageSwipe

/**
 * Small shared composables built on UiTokens: text styles, icon badges, dividers, and
 * padding/no-ripple Modifiers.
 */

// ---- Common Composable Helpers -----------------------------------------------

/**
 * A pebble body that needs live status: shows [content] with it once there is one, else the loading
 * or empty line.
 */
@Composable
internal fun <T : Any> PebbleStatusGate(status: T?, refreshing: Boolean, content: @Composable (T) -> Unit) {
    when {
        status != null -> content(status)
        refreshing -> Text("Fetching live status…")
        else -> Text("No status yet.")
    }
}

/** Label text styling (labelMedium) for subtle secondary text. */
@Composable
internal fun LabelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Body text (bodySmall, onSurfaceVariant) -- muted/secondary content. */
@Composable
internal fun BodySmallText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/** Body text (bodyMedium, onSurface) -- regular secondary content. */
@Composable
internal fun BodyMediumText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

/** Title text (titleSmall) -- section headers and card titles; bold by default. */
@Composable
internal fun TitleSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        fontWeight = fontWeight,
    )
}

/** The circular tinted container [IconBadge] draws into. */
@Composable
internal fun IconBadgeContainer(
    modifier: Modifier = Modifier,
    containerColor: Color,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * [IconBadgeContainer] with a single centered [Icon]. [containerColor] defaults to a 14%-alpha tint
 * of [tint]; pass a *Container role for the solid tonal look.
 */
@Composable
internal fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = tint.copy(alpha = 0.14f),
    size: Dp = 40.dp,
    iconSize: Dp = size * 0.5f,
) {
    IconBadgeContainer(modifier = modifier, containerColor = containerColor, size = size) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * An [IconBadge] leading a title + optional muted subtitle, text column filling the row. [trailing]
 * is an optional slot after the text (chevron, switch, status chip).
 */
@Composable
internal fun IconLeadRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerColor: Color = tint.copy(alpha = 0.14f),
    badgeSize: Dp = 40.dp,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GapGroup),
    ) {
        IconBadge(icon, tint, containerColor = containerColor, size = badgeSize)
        Column(Modifier.weight(1f)) {
            TitleSmallText(title, color = titleColor)
            if (subtitle != null) BodySmallText(subtitle)
        }
        trailing?.invoke()
    }
}

/** A [HorizontalDivider] at the app's standard faint weight. */
@Composable
internal fun SectionDivider(modifier: Modifier = Modifier, alpha: Float = 0.3f) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))
}

/**
 * `Modifier.clickable` with no ripple and a fresh interaction source, for full-bleed scrims and
 * tap-swallowing surfaces.
 */
@Composable
internal fun Modifier.noRippleClickable(onClickLabel: String? = null, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )

/**
 * Tappable modifier for non-[MorphButton] controls: [noRippleClickable] plus the shared click
 * haptic.
 */
@Composable
internal fun Modifier.hapticClickable(
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    return blockPageSwipe()
        .clickable(interactionSource = source, indication = null, onClickLabel = onClickLabel) { haptics?.click(); onClick() }
}
