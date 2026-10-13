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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        refreshing -> PebbleMessage("Fetching live status…")
        else -> PebbleMessage("No status yet.")
    }
}

/**
 * The app's one style for "small text that is just there" -- captions, labels, muted one-liners.
 * Bigger and Medium-weight so it reads without squinting, full-strength onSurface so it clears the
 * contrast bar on glass, and a soft blurred halo in the surface colour behind the glyphs so it holds
 * its own over the aurora instead of melting into it. Every secondary-text helper routes through
 * here, so the treatment is identical everywhere.
 */
@Composable
internal fun secondaryTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontWeight = FontWeight.Medium,
    shadow = secondaryTextShadow(),
)

/** Secondary text's colour: onSurface, barely eased -- contrast, not a grey wash. */
@Composable
internal fun secondaryTextColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

/**
 * The halo behind secondary text: a blurred copy of the surface colour the text sits on, not a hard
 * shadow, so glyph edges lift off a busy background without a visible drop shadow.
 */
@Composable
internal fun secondaryTextShadow(): Shadow = Shadow(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    blurRadius = SecondaryHaloBlurPx,
)

/** A decently strong halo, in device px (text shadows are not density-scaled). */
private const val SecondaryHaloBlurPx = 9f

/** Label text styling for subtle secondary text (a step up from the old labelMedium). */
@Composable
internal fun LabelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = secondaryTextStyle(),
        color = secondaryTextColor(),
    )
}

/**
 * A muted one-liner for a pebble body with nothing to show yet (loading or empty). One component so
 * every "fetching…" / "no data" line in the app reads the same, instead of each call site picking
 * its own style and colour.
 */
@Composable
internal fun PebbleMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = secondaryTextStyle(),
        color = secondaryTextColor(),
    )
}

/** Body text (bodyMedium, onSurface) -- muted/secondary content. */
@Composable
internal fun BodySmallText(text: String, modifier: Modifier = Modifier, color: Color = secondaryTextColor()) {
    Text(
        text,
        modifier = modifier,
        style = secondaryTextStyle(),
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
 * haptic. Deliberately does NOT block the page swipe: a finger that lands here and then moves
 * sideways is swiping the page, not tapping the control, and the only way to tell those apart is to
 * let the drag reach the pager. A real tap never exceeds the touch slop, so the tap still wins.
 */
@Composable
internal fun Modifier.hapticClickable(
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    return clickable(interactionSource = source, indication = null, onClickLabel = onClickLabel) { haptics?.click(); onClick() }
}
