@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement
import com.bloo.uicommon.SegmentOption

/**
 * Small status-chip widgets: MetaChip, StatusChip, the update-available chip/dot/
 * badged-card family, and LastUpdatedLabel. Split out of Widgets.kt to group this
 * cohesive cluster on its own.
 */

/**
 * Small pill-shaped fact badge -- [CarHeaderRow]'s own model/powertrain and
 * "updated x ago" facts, which used to be two stacked plain caption lines
 * with no container of their own, reading as an afterthought next to the
 * rest of the app's chip/pill chrome.
 *
 * Uses the SAME floating-pill rim/shadow treatment as everything else that
 * has to stay legible over unpredictable content (the map's own name pill,
 * FloatingIcon), but a genuinely NEUTRAL fill -- plain black/white by theme,
 * not `surfaceContainerHighest`. That token is a Material3 tonal "neutral"
 * role, which is only ever a near-hueless gray for a plain, undynamic
 * theme; this app's own custom/dynamic palette feeds it a seed colour, and
 * a "neutral" tone that inherits even a little of a blue seed reads as a
 * flatly blue chip -- reported directly ("it should be a neutral colour...
 * not a primary") after an earlier fix here picked exactly that token. A
 * flat, un-rimmed `surfaceContainerHigh` (this composable's ORIGINAL
 * design) was ALSO reported, twice, as effectively invisible over the
 * wide/dual-car header's plain black background -- so this needs to be
 * both hue-independent AND definitely visible regardless of theme, which a
 * theme-role color token can't promise on its own either way.
 */
@Composable
internal fun MetaChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, hazeState: HazeState? = null) {
    // GlassSurface (GlassChrome.kt): hazeState is now threaded through from the hero
    // header's own screen-level HazeState (see CarHeaderRow), so this chip gets a real
    // backdrop blur wherever a caller can supply one -- callers with none in scope still
    // fall back to the same plain tint this always had.
    GlassSurface(
        shape = RoundedCornerShape(50),
        modifier = modifier,
        hazeState = hazeState,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The tinted sibling of [MetaChip]: a small pill for a LIVE status readout -- something
 * that changes while you are looking at it and whose colour carries meaning (AutoLock's
 * detection state, [SettingsHeroCard]'s update-check result).
 *
 * Separate from [MetaChip] rather than a `tint` parameter on it, because the two answer
 * different questions. MetaChip is a static fact over unpredictable content (a photo, the
 * map), so it is deliberately hue-free glass with a rim and a shadow -- see its own doc for
 * how hard-won that is. This one always sits on a known settings card, carries no rim, and
 * is tinted on purpose.
 *
 * It exists because two screens had hand-rolled the same pill at different sizes and fills:
 * AutoLock's detection state as a solid `primaryContainer` [Surface] at 12/6 padding,
 * [SettingsHeroCard]'s update status as a 0.15-alpha tint at 12/8. Same content, same place
 * in the app (both are Settings cards), two chips -- so both route through this now. The 0.15-alpha
 * fill is the one that survived: a status chip has to work in any of the tints a caller
 * passes (tertiary, error, a muted onSurfaceVariant "nothing to report"), and only the
 * alpha-of-the-tint form has a matching container tone for every one of them.
 */
@Composable
internal fun StatusChip(text: String, tint: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    StatusChip(tint = tint, modifier = modifier, icon = icon) { Text(text) }
}

/**
 * [StatusChip] with the label as a slot, for the callers that animate the text itself
 * ([SettingsHeroCard]'s own chip crossfades between "Checking…" / "Build N ready" /
 * "Up to date"). The style and the tint are still applied here, through LocalContentColor /
 * LocalTextStyle, so an animating caller cannot drift away from a static one.
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
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
 * [SettingsHeroCard]'s tonal update-status chip, split out of the card body so the
 * spring-animated tint (`updateTint`) only recomposes this small Row/Icon/Text
 * scope on every animation frame, instead of the whole card content lambda
 * (which also hosts the RollingNumber hero stat and outer Surface/Row layout).
 */
@Composable
internal fun UpdateStatusChip(state: UiState) {
    val updateTint by androidx.compose.animation.animateColorAsState(
        targetValue = when {
            state.updateAvailable != null -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        // Sprung rather than snapped -- "up to date" turning tertiary the instant
        // a check lands is the one moment this card actually has news, and a cut
        // read as flat next to how much of the rest of the app now springs.
        animationSpec = lowPowerAwareSpring(
            dampingRatio = SoftDamping,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "settingsUpdateTint",
    )
    StatusChip(
        tint = updateTint,
        icon = Icons.Filled.SystemUpdate,
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = when {
                state.updateChecking -> "Checking…"
                state.updateAvailable != null -> "Build ${state.updateAvailable.run.runNumber} ready"
                else -> "Up to date"
            },
            label = "settingsUpdateChipText",
        ) { text -> Text(text) }
    }
}

/**
 * The small "there's something new" notification dot every update card now wears on
 * its own top-end corner -- [SettingsHeroCard] and the garage's [UpdateAvailableTile]
 * pebble both host it identically, via [Modifier.updateAvailableBadge], rather than the
 * card's own full status chip/summary text being the only cue: a glance at either
 * card's corner, even collapsed, should say "go look at this" the same way an app
 * icon's own unread badge would, without needing to read anything.
 *
 * A plain dot, not a count -- there is only ever "an update" or not, never a number of
 * them, so a badge count would just be a fixed 1 with extra ceremony.
 */
@Composable
private fun UpdateAvailableDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .background(UpdateAvailableAmber, CircleShape)
            // A hairline ring in the card's own container tone -- otherwise the dot's
            // edge, sitting right at the card's rounded corner, has nothing separating
            // it from whatever happens to be directly behind that corner (status bar
            // icons, another card peeking from the next page over).
            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
    )
}

/**
 * Wraps [content] in a [Box] and overlays [UpdateAvailableDot] on its top-end corner,
 * scaling in/out as [visible] flips -- the shared entry point both update-card call
 * sites ([SettingsHeroCard], [UpdateAvailableTile]) use so the badge's position, size,
 * offset and pop animation can't drift between them.
 *
 * The dot sits slightly outside the corner (a small negative offset on both axes)
 * rather than flush against it, the same "notification badge overlaps the icon's own
 * edge" placement Android's own app-icon badges use -- flush against a card's own
 * ROUNDED corner would have the dot's bottom-left quarter sitting on the transparent
 * area the corner radius cuts away, reading as clipped rather than round.
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


/** "Updated x ago" fact, as a [MetaChip]. Null (renders nothing) until a
 *  first fetch has actually landed for [v]. */
@Composable
internal fun LastUpdatedLabel(fetchedAt: Long?, modifier: Modifier = Modifier, hazeState: HazeState? = null) {
    val rel = rememberRelativeTime(fetchedAt) ?: return
    MetaChip("Updated $rel", modifier, icon = Icons.Filled.Refresh, hazeState = hazeState)
}




/**
 * A vertical list whose items can be reordered by long-pressing the supplied
 * [dragHandle] and dragging. Item heights are measured so variable-height rows
 * reorder correctly; the live order is committed via [onReorder] on drop.
 *
 * Designed to live inside an existing scroll container (it is a plain Column).
 *
 * Drag mechanism: `order` is local mutable state (re-synced from [items]
 * whenever nothing is being dragged). `draggingKey` identifies which item is
 * currently held; that item is excluded from [animatePlacement] and instead
 * manually translated by `offsetY`, a running total of vertical drag delta
 * (via [detectDragGesturesAfterLongPress]'s `onDrag`). On every drag tick,
 * `offsetY` is compared against the *next* or *previous* item's measured
 * height (tracked per-key in `heights`, populated by each row's own
 * `onSizeChanged`): once the drag has moved past half that neighbor's
 * height, the two items swap places in `order` and `offsetY` is reduced by
 * that neighbor's height, so the dragged item's on-screen position stays
 * continuous through the swap rather than jumping. Every other (non-dragged)
 * row uses [animatePlacement] to glide smoothly to its new slot when the
 * list order changes underneath it. [staggerInOnColdStart]/[introKey] are
 * unrelated to dragging -- they drive a one-time entrance stagger, see
 * [coldStartIntroPlayed].
 */
