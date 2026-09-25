@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
// No `motionScheme` import: it is a member of the MaterialTheme object (verified as
// MaterialTheme.getMotionScheme in the resolved material3 AAR), as are defaultEffectsSpec
// and defaultSpatialSpec on MotionScheme. Screens.kt imports none of them either.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
// State<T>'s `by` delegate isn't a member -- it resolves to this file-scope operator
// extension, which the compiler will not find without an explicit import (unlike most of
// this file's other extension functions, which show up as unresolved-reference errors
// instead of this one's more oblique "has no method getValue... cannot serve as a delegate").
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small shared composables built on UiTokens' design tokens: text styles, icon badges,
 * dividers, and Modifier extensions for padding/no-ripple-clickable. Split out of
 * UiTokens.kt to keep that file to plain design tokens -- see its own doc.
 */

// ---- Common Composable Helpers -----------------------------------------------

/**
 * Body text styling (bodySmall, onSurfaceVariant) for secondary/muted content.
 * Use this instead of `Text(text, style = MaterialTheme.typography.bodySmall, color = ...)`
 * to consolidate the most-repeated text pattern in the app (27+ sites).
 */
@Composable
internal fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Label text styling (labelMedium) for subtle secondary text.
 * Consolidates another common text pattern.
 */
@Composable
internal fun LabelText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Body text (bodySmall, onSurfaceVariant) -- muted/secondary content.
 * Consolidates the single most common text pattern (54 sites).
 */
@Composable
internal fun BodySmallText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/**
 * Body text (bodyMedium, onSurface) -- regular secondary content.
 * Used for descriptions, supplementary text (41 sites).
 */
@Composable
internal fun BodyMediumText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
}

/**
 * Label text (labelSmall) -- compact labels and captions (27 sites).
 */
@Composable
internal fun LabelSmallText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/**
 * Label text (labelLarge) -- prominent labels and tags (16 sites).
 */
@Composable
internal fun LabelLargeText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color,
    )
}

/**
 * Title text (titleSmall) -- section headers, card titles (15 sites).
 */
@Composable
internal fun TitleSmallText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = color,
    )
}

/**
 * Icon with a single color and size. Consolidates the most-repeated icon pattern.
 * Used for: status icons, nav icons, control icons throughout the app.
 */
@Composable
internal fun ThemedIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 24.dp,
) {
    androidx.compose.material3.Icon(
        imageVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

/**
 * The circular tinted container [IconBadge] draws itself into -- pulled out under its own,
 * non-overloaded name (rather than a second `IconBadge` overload) after that overload was the
 * trigger for a Kotlin "recursive type checking" compiler error at several call sites that fed
 * it a conditional (`if (x) iconA else iconB`) [ImageVector]: overload resolution across a
 * lambda-slot and an icon+tint overload, combined with a branch whose two arms' common
 * supertype the compiler has to infer, sent K2 into that recursive loop. [content] is a plain
 * [Icon] in the common case, but takes a full `@Composable` slot so sites that animate the icon
 * (e.g. `AnimatedContent` between install-state icons) can still share the same
 * circle/size/tint chrome instead of hand-rolling it.
 */
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
 * [IconBadgeContainer] pre-filled with a single centered [Icon] -- the common case, and the
 * only thing named `IconBadge` (see [IconBadgeContainer]'s own doc for why the lambda-slot
 * version is not a second overload of this name). [containerColor] defaults to a 14%-alpha
 * tint of [tint] itself (the "soft tonal chip" look used by search results and the update
 * pebble); pass an explicit container colour (e.g. a *Container role) for the "solid tonal
 * circle" look settings headers and onboarding steps use instead.
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
 * The "[IconBadge] leading a title + optional muted subtitle, with the text column claiming
 * the rest of the row" skeleton -- copied verbatim across settings card headers, onboarding
 * step rows, search results and the guard PIN prompt before this existed. [trailing] is an
 * optional slot after the text column (a chevron, a switch, a status chip).
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, tint, containerColor = containerColor, size = badgeSize)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = titleColor)
            if (subtitle != null) MutedText(subtitle)
        }
        trailing?.invoke()
    }
}

/**
 * A [HorizontalDivider] at the app's standard faint weight. [SettingsScreen] alone had this
 * spelled out inline six separate times with the alpha drifting between 0.3/0.4/0.5 from
 * site to site with no apparent reason -- one call site, one alpha, chosen as the most
 * common of the three.
 */
@Composable
internal fun SectionDivider(modifier: Modifier = Modifier, alpha: Float = 0.3f) {
    HorizontalDivider(modifier = modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))
}

/**
 * `Modifier.clickable` with the ripple suppressed and a fresh, unshared interaction
 * source -- the app's standard "tappable but no visual press feedback" treatment for
 * full-bleed scrims and tap-swallowing surfaces (a sheet's backdrop, a lock screen's
 * backdrop, a sheet's own body eating taps so they don't fall through to the scrim
 * behind it). Copy-pasted as the same `interactionSource = remember { MutableInteractionSource() },
 * indication = null` pair at 8 separate call sites before this existed.
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
 * Consolidates the most-repeated padding patterns. Used instead of
 * `.padding(horizontal = X, vertical = Y)` across 40+ sites.
 */
internal fun Modifier.paddingHorizontal(horizontal: Dp) = padding(horizontal = horizontal)
internal fun Modifier.paddingVertical(vertical: Dp) = padding(vertical = vertical)
internal fun Modifier.paddingAll(all: Dp) = padding(all)

/**
 * Spacing-token based padding helpers use the Settings gap scale directly.
 * `.padding16` is `.padding(horizontal = SettingsGapSection)` -- replaces
 * the ad-hoc `padding(horizontal = 16.dp)` pattern across the app.
 */
internal fun Modifier.padding24() = padding(24.dp)
internal fun Modifier.padding16() = padding(16.dp)
internal fun Modifier.padding12() = padding(12.dp)
internal fun Modifier.padding8() = padding(8.dp)
internal fun Modifier.padding4() = padding(4.dp)

internal fun Modifier.paddingHorizontal16() = paddingHorizontal(16.dp)
internal fun Modifier.paddingHorizontal24() = paddingHorizontal(24.dp)
internal fun Modifier.paddingHorizontal12() = paddingHorizontal(12.dp)
internal fun Modifier.paddingHorizontal8() = paddingHorizontal(8.dp)

internal fun Modifier.paddingVertical16() = paddingVertical(16.dp)
internal fun Modifier.paddingVertical12() = paddingVertical(12.dp)
internal fun Modifier.paddingVertical24() = paddingVertical(24.dp)
internal fun Modifier.paddingVertical8() = paddingVertical(8.dp)

/** Combined padding patterns that appear across multiple files. */
internal fun Modifier.paddingHorizontal24Vertical16() = padding(horizontal = 24.dp, vertical = 16.dp)
internal fun Modifier.paddingHorizontal16Vertical12() = padding(horizontal = 16.dp, vertical = 12.dp)

// StandardRow() and StandardColumn() were deleted here. Both claimed to "consolidate" the
// Row/Column + fillMaxWidth() + spacedBy pattern, but neither ever gained a single call site
// anywhere in the app -- unlike LabelText and ThemedIcon above them, which did and stay. They
// also could not have been used as written: the whole point of the pattern they wrapped is
// that each site picks its OWN spacing from the layout tokens, and a wrapper whose only
// contribution is a DEFAULT spacing saves nothing at a site that has to pass its spacing
// anyway. A helper nothing calls is not a token, it is a guess at one.
