package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement

/**
 * Small status-chip widgets: MetaChip, StatusChip, the update-available chip/dot/badge family,
 * LastUpdatedLabel, and ReorderColumn.
 */

/**
 * Uses the floating-pill rim/shadow treatment, with a NEUTRAL fill (plain black/white by theme)
 * rather than a theme role like `surfaceContainerHighest`: dynamic palettes tint those with the
 * seed colour, and `surfaceContainerHigh` without a rim is invisible on the dark dual-car header.
 */
@Composable
internal fun MetaChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, hazeState: HazeState? = null) {
    // GlassSurface (GlassChrome.kt): hazeState comes from the hero header's screen-level HazeState
    // for a real backdrop blur; callers without one fall back to the plain tint.
    GlassSurface(
        shape = CircleShape,
        modifier = modifier,
        hazeState = hazeState,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = GapRow, vertical = GapHairline),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(GapHairline))
            }
            AnimatedText(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The tinted sibling of [MetaChip]: a pill for a LIVE status readout whose colour carries meaning
 * (AutoLock's detection state, [SettingsHeroCard]'s update-check result).
 */
@Composable
internal fun StatusChip(text: String, tint: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    StatusChip(tint = tint, modifier = modifier, icon = icon) { AnimatedText(text) }
}

/**
 * [StatusChip] with the label as a slot, for callers that animate the text itself. Style and tint
 * are applied through LocalContentColor / LocalTextStyle so they match a static chip.
 */
@Composable
internal fun StatusChip(
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: @Composable () -> Unit,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = GapGroup, vertical = GapRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(GapRow))
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint,
            androidx.compose.material3.LocalTextStyle provides
                MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        ) {
            label()
        }
    }
}

/**
 * The "there's something new" dot on the top-end corner of update cards ([SettingsHeroCard],
 * [UpdateAvailableTile]), applied via [Modifier.updateAvailableBadge]. A plain dot, not a count.
 */
@Composable
private fun UpdateAvailableDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(UpdateAvailableAmber, CircleShape)
            // A hairline ring in the card's own container tone -- otherwise the dot's edge, sitting
            // right at the card's rounded corner, has nothing separating it from whatever happens
            // to be directly behind that corner (status bar icons, another card peeking from the
            // next page over).
            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
    )
}

/**
 * Wraps [content] in a [Box] and overlays [UpdateAvailableDot] on its top-end corner, scaling
 * in/out as [visible] flips.
 */
@Composable
internal fun UpdateBadgedCard(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        androidx.compose.animation.AnimatedVisibility(
            visible = visible,
            enter = androidx.compose.animation.scaleIn(
                lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp),
        ) {
            UpdateAvailableDot()
        }
    }
}

@Composable
internal fun LastUpdatedLabel(fetchedAt: Long?, modifier: Modifier = Modifier, hazeState: HazeState? = null) {
    val rel = rememberRelativeTime(fetchedAt) ?: return
    MetaChip("Updated $rel", modifier, icon = Icons.Filled.Refresh, hazeState = hazeState)
}

/**
 * A vertical list whose items reorder by long-pressing the [dragHandle] and dragging; the order is
 * committed via [onReorder] on drop. A plain Column, meant for an existing scroll container.
 */
