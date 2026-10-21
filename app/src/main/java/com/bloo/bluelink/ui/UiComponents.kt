package com.bloo.bluelink.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.TitleSubtitle
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.content

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
        refreshing -> SecondaryText("Fetching live status…")
        else -> SecondaryText("No status yet.")
    }
}

/** The style of secondary text, from the shared roles in :uicommon (see TextRoles.kt). */
@Composable
internal fun secondaryTextStyle(): TextStyle = com.bloo.uicommon.SecondaryTextStyle()

/** The primary role's style, from :uicommon. */
@Composable
internal fun PrimaryTextStyle(): TextStyle = com.bloo.uicommon.PrimaryTextStyle()

/** The secondary role's style, from :uicommon. */
@Composable
internal fun SecondaryTextStyle(): TextStyle = com.bloo.uicommon.SecondaryTextStyle()

/** Secondary text's colour, from :uicommon. */
@Composable
internal fun secondaryTextColor(): Color = com.bloo.uicommon.secondaryTextColor()

/** The halo behind secondary text, from :uicommon. */
@Composable
internal fun secondaryTextShadow(): Shadow = com.bloo.uicommon.secondaryTextShadow()

/** The app's ONE muted secondary text: [secondaryTextStyle] (bodyMedium, semi-bold, with a soft halo) in the muted colour. */
@Composable
internal fun BodySmallText(text: String, modifier: Modifier = Modifier, color: Color = secondaryTextColor()) {
    RevealText(text, style = SecondaryTextStyle(), modifier = modifier, color = color)
}

/** The PRIMARY role as text: what a line says, in the full text colour. */
@Composable
internal fun PrimaryText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = RevealText(
    text,
    style = PrimaryTextStyle(),
    modifier = modifier,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = maxLines,
    overflow = overflow,
)

/** The SECONDARY role as text: what a line is about. Same as [BodySmallText]. */
@Composable
internal fun SecondaryText(text: String, modifier: Modifier = Modifier) = BodySmallText(text, modifier)

/** The PRIMARY role as a line, in the full text colour. */
@Composable
internal fun BodyMediumText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    RevealText(text, style = PrimaryTextStyle(), modifier = modifier, color = color)
}

/** The PRIMARY role as a heading line: bold, for a section or card title. */
@Composable
internal fun TitleSmallText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    RevealText(text, style = PrimaryTextStyle(), modifier = modifier, color = color, fontWeight = fontWeight)
}

/** The circular tinted container [IconBadge] draws into (from :uicommon). */
@Composable
internal fun IconBadgeContainer(
    modifier: Modifier = Modifier,
    containerColor: Color,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit,
) = com.bloo.uicommon.IconBadgeContainer(modifier, containerColor, size, content)

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
) = com.bloo.uicommon.IconBadge(icon, tint, modifier, containerColor, size, iconSize)

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
        TitleSubtitle(
            title, subtitle, Modifier.weight(1f),
            titleColor = titleColor, titleWeight = FontWeight.Bold, subtitleColor = secondaryTextColor(),
        )
        trailing?.invoke()
    }
}

/** A [HorizontalDivider] at the app's standard faint weight. */
@Composable
/**
 * The hairline between sections.
 *
 * 0.5 is the default because it is what every caller was passing by hand: the default used to be 0.3,
 * which NOTHING used -- so the "default" was a value that never appeared on screen, and the divider's
 * actual look was decided separately at six call sites. One caller still passes 0.35 for a
 * deliberately lighter rule inside a panel.
 */
internal fun SectionDivider(modifier: Modifier = Modifier, alpha: Float = 0.5f) {
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
