@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.coldStartIntroPlayed
import com.bloo.uicommon.animatePlacement

/**
 * A soft blurred scrim behind the status bar so scrolling content underneath
 * (a car photo, Aurora, dense text) doesn't fight the system clock/battery
 * icons drawn on top of it. Not the normal (non-cover-screen) layouts -- the
 * cover screen already reserves real space above its content instead of
 * drawing under the status bar at all, so it has nothing to scrim.
 */
@Composable
internal fun StatusBarScrim() {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(topInset + 28.dp)
            .background(
                Brush.verticalGradient(
                    listOf(scheme.surface.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            .blur(18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
    )
}

/** The one shared "gap below the status bar" every free-floating header
 *  element -- [FloatingIcon]'s own default [FloatingIcon.outerPadding], every
 *  [TitleFlightOverlay] call site's `cornerY`, the page-dot overlays -- lines
 *  up against, so they all sit on the same row instead of each surface
 *  reproducing its own close-but-not-quite value (this used to be `12.dp` in
 *  some places and `10.dp` in others, an inconsistency invisible on any one
 *  screen alone but obvious the moment two headers are compared side by
 *  side). */
internal val HeaderCornerGap = 12.dp

/** The one shared size every free-floating header BUTTON -- [FloatingIcon]'s
 *  circle, and anything meant to sit in the same row as one -- is drawn at,
 *  so two buttons on the same header always share a vertical centre. Used to
 *  be re-typed as a bare `48.dp` at each call site (and, in one place,
 *  [LockOverlay]'s own hand-rolled back button, mistyped as `46.dp` -- a
 *  silent 2dp size/alignment drift from every other header button in the
 *  app). */
internal val HeaderButtonSize = 48.dp

/** Extra breathing room reserved *below* a header button's own footprint
 *  (`HeaderCornerGap + HeaderButtonSize`) before real content is allowed to
 *  start, on top of whatever `Arrangement.spacedBy` a column already adds.
 *  Needed because a button's true on-screen silhouette is bigger than its
 *  logical box: [FloatingIcon] draws `ambientRing()`/`dropShadow()` glow
 *  outside its 48dp circle, and content below it (e.g. a [Pebble] row) has
 *  its own card shadow -- so reserving exactly the button's geometric
 *  footprint (as ExpandedCar's dual-column header used to) leaves only the
 *  column's incidental 12dp `spacedBy` gap as buffer, which those two halos
 *  can visibly eat into. Mirrors the same "bare inset isn't enough, add a
 *  named clearance" pattern [PagerDotClearance] already uses below. */
internal val HeaderContentClearance = 12.dp

/** A small translucent circular icon button used as a floating overlay control.
 *  [outerPadding] is the breathing room around the [HeaderButtonSize] circle -
 *  the default ([HeaderCornerGap], a 72dp footprint) suits free-floating
 *  overlay corners; tight rows (the cover screen's title row, at 2dp) keep
 *  that footprint down to 52dp on a ~260dp-tall screen. */
@Composable
internal fun FloatingIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: Dp = HeaderCornerGap,
    // Overrides for surfaces that float over something other than the app's
    // content: the lock overlay's back arrow sits on a dark scrim, not a
    // card, so it deliberately uses plain white instead of the glass fill
    // (see LockOverlay's own note -- the old hand-rolled Surface there was
    // this exact shape re-built by hand; it now passes these instead).
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "floatIconScale",
    )
    // Plain semi-transparent fill (see GlassChrome.kt) -- more transparent
    // than the original flat version per feedback that it read as too opaque.
    // The ambient halo/shadow frame it over car photos.
    Surface(
        onClick = { haptics?.click(); onClick() },
        shape = CircleShape,
        color = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = modifier
            .padding(outerPadding)
            .size(HeaderButtonSize)
            // Lambda form: the press spring is read at DRAW time, so the animation
            // never recomposes this button (the arg-taking overload reads it in
            // composition instead -- see ExpressiveButtons.kt for the same fix).
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .ambientRing(CircleShape)
            .dropShadow(CircleShape)
            .appGlassRim(CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}






/**
 * When true (cover-screen tiles), pebbles render permanently open with no
 * collapse chevron or drag handle - collapsing a full-screen tile makes no sense.
 */
/**
 * The current [SettingsStore.Appearance], provided once at the app root (see
 * BlooApp) so pebbles/tiles read it via LocalAppearance.current instead of each
 * opening its own vm.appearance.collectAsState() coroutine collector. ~20 hot
 * per-pebble/per-tile collectors collapse to one. Default is a fresh Appearance()
 * (all defaults) so a reader outside the provider degrades gracefully rather than
 * crashing — but every real screen is inside the provider.
 */
internal val LocalAppearance = staticCompositionLocalOf { SettingsStore.Appearance() }

internal val LocalForceExpanded = staticCompositionLocalOf { false }

/**
 * When true (cover-screen tiles), a pebble stretches to fill the available height
 * and scrolls internally if its content is taller - so each tile fills the screen.
 */
internal val LocalPebbleFillHeight = staticCompositionLocalOf { false }

/** Tile names that [CompactCar] can render — unknown sections are excluded. */
internal val CompactKnownTiles = setOf(
    // No "controls" here, deliberately. It was added when the lock/horn
    // controls were unreachable on the cover, but as its own page it was one
    // short row of buttons above two thirds of an empty screen. Those same
    // controls now live in CoverMainTile's permanent action bar, on the page
    // the cover opens on -- so a separate page for them would be a second,
    // emptier copy of something already on screen.
    // "update" IS here: the update-available card is a first-class pebble on
    // every phone page, and it silently vanished from the cover (reported).
    // Rendered through the same SinglePebble routing as every other tile, so
    // the Install/Remind-me/Not-now card works on the flip screen exactly as
    // it does unfolded.
    "climate", "charge", "location", "weather", "trips", "info", "diagnostics", "ai", "update"
)

/**
 * When set, [Pebble] in fill-height cover-screen mode uses this scroll state
 * instead of creating a local one — lets the parent observe scroll position
 * to decide whether to switch pager pages or scroll tile content.
 */
internal val LocalCoverScrollState = compositionLocalOf<ScrollState?> { null }

/**
 * Shared flag set true while the cover-screen page scrubber is active, so the
 * parent [CompactGarage] can suspend horizontal car-switching swipes during a
 * scrub. Provided around the HorizontalPager content.
 */
internal val LocalCoverScrubbing = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/**
 * The live pull-to-refresh distance (0..1+), published by [Refreshable] so the
 * floating overlays in [GarageScreen] (page dots, settings/back/flip buttons)
 * can track the pull in real time instead of only animating once refresh starts.
 */
internal val LocalPullFraction =
    staticCompositionLocalOf<androidx.compose.runtime.MutableState<Float>> { mutableStateOf(0f) }

/**
 * A headline number that rolls when it changes: it slides up when the value
 * grows and down when it shrinks (digits extracted from [text] decide the
 * direction), falling back to a cross-fade when there's no number to compare.
 */
@Composable
internal fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    // Split into the rolling digits and the STATIC suffix ("%"): only the
    // digits roll up/down, the unit glyph rides with them as one unmoved
    // companion -- rolling the whole string including the "%" read as the
    // entire readout lifting off, which is not what a digit roll is.
    val digits = text.takeWhile { it.isDigit() }
    val suffix = text.drop(digits.length)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.wrapContentWidth()) {
        AnimatedContent(
            targetState = digits,
            transitionSpec = {
                // Direction is derived HERE from the transition's own
                // initialState/targetState, not from a separately-tracked
                // "previous value" state: AnimatedContent already knows both
                // ends of the very transition it's composing, so the derived
                // direction can never lag the actual change (a two-step flip
                // landing in the same frame used to compared against a
                // previous value a LaunchedEffect wrote one frame later --
                // rolling UP on a number that had just gone DOWN).
                val dir = if ((targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)) 1 else -1
                (fadeIn(tween(180)) + slideInVertically { dir * it / 2 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically { -dir * it / 2 })
            },
            label = "num",
        ) { t -> WiggleText(t, style = style, fontWeight = fontWeight, color = color) }
        if (suffix.isNotEmpty()) {
            WiggleText(suffix, style = style, fontWeight = fontWeight, color = color)
        }
    }
}

/**
 * A coarse, self-ticking "x min ago" string for [millis] (null → null).
 *
 * Holds the LABEL in state rather than a clock, which is the whole efficiency of it. A
 * `mutableStateOf` write only invalidates readers when the value actually changes, so a tick
 * that recomputes "4h ago" and finds "4h ago" costs nothing at all. The previous version kept
 * `now` in state and returned a value derived from it, so every tick invalidated its caller
 * unconditionally -- for a car refreshed hours ago that was 120 recompositions an hour, each
 * producing a byte-identical string, at three or four call sites, times however many car pages
 * the pager holds live.
 *
 * The interval now matches the label's own resolution instead of being a flat 30s. Under a
 * minute the text really does change every few seconds, so tick at 10s; under an hour it can
 * only change once a minute; past that it cannot change more than every quarter of an hour.
 * Strictly more responsive at the fine end and ~30x less work at the coarse end.
 *
 * Also gone: `if (now >= 0)`, which was always true (it tested a wall-clock millis) and existed
 * only to make the composable read the state and thus subscribe to the timer. It worked, but a
 * condition that cannot be false is a trap for the next reader -- holding the label in state
 * makes the subscription honest and the guard unnecessary.
 *
 * The bucket thresholds themselves stay in shared/relativeLabel(), which owns them; this had
 * drifted from that once already ("d ago" here vs "day ago" there).
 */
@Composable
internal fun rememberRelativeTime(millis: Long?): String? {
    if (millis == null) return null
    var label by remember(millis) {
        mutableStateOf(com.bloo.bluelink.data.relativeLabel(millis))
    }
    LaunchedEffect(millis) {
        while (true) {
            val age = System.currentTimeMillis() - millis
            delay(
                when {
                    age < 60_000L -> 10_000L
                    age < 3_600_000L -> 60_000L
                    else -> 900_000L
                },
            )
            label = com.bloo.bluelink.data.relativeLabel(millis)
        }
    }
    return label
}

/**
 * Small pill-shaped fact badge -- [CarHeaderRow]'s own model/powertrain and
 * "updated x ago" facts, which used to be two stacked plain caption lines
 * with no container of their own, reading as an afterthought next to the
 * rest of the app's chip/pill chrome. A muted [surfaceContainerHigh] fill,
 * not the floating pills' glass treatment -- this sits on the app's own
 * ordinary surface, not over an unpredictable photo, so it doesn't need
 * that treatment's guaranteed contrast, just enough of a container to read
 * as a distinct fact rather than body text bleeding into the row beside it.
 */
@Composable
internal fun MetaChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
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

/** "Updated x ago" fact, as a [MetaChip]. Null (renders nothing) until a
 *  first fetch has actually landed for [v]. */
@Composable
internal fun LastUpdatedLabel(v: Vehicle, state: UiState, modifier: Modifier = Modifier) {
    val rel = rememberRelativeTime(state.fetchedAt(v)) ?: return
    MetaChip("Updated $rel", modifier, icon = Icons.Filled.Refresh)
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


/**
 * A clean, fully custom slider: a rounded track with an accent fill, subtle step
 * ticks, and a circular thumb that springs to the nearest step. Drawn entirely on
 * a Canvas (no Material Slider) so its look is consistent and theme-driven.
 */
@Composable
internal fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accent: Color = MaterialTheme.colorScheme.primary,
    // Fired once, with the final value, when the drag/tap settles — for callers
    // whose real commit is expensive (see the Vibrancy/UI-scale sliders, which
    // otherwise call onValueChange on every drag tick and each one recomposes
    // the whole app since they feed BlooTheme's colorScheme/LocalDensity). Those
    // should update local/visual state cheaply in onValueChange and do the
    // actual expensive write here instead, matching "sync on commit" everywhere
    // else in the app.
    onValueSettled: ((Float) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var latestValue by remember { mutableFloatStateOf(value) }
    com.bloo.uicommon.AnimatedSlider(
        value = value,
        onValueChange = { latestValue = it; onValueChange(it) },
        valueRange = valueRange,
        steps = steps,
        accent = accent,
        inactiveColor = scheme.surfaceContainerHighest,
        dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
        dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        reduceMotion = LocalReduceMotion.current,
        onStepTick = { haptics?.tick() },
        onSettle = { haptics?.click(); onValueSettled?.invoke(latestValue) },
    )
}

@Composable
internal fun WiggleText(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    // Memoize the style copy to avoid recreating it when color/fontWeight don't change.
    val resolvedStyle = remember(style, fontWeight, resolvedColor) {
        style.copy(fontWeight = fontWeight, color = resolvedColor)
    }
    com.bloo.uicommon.WiggleText(
        text = text,
        style = resolvedStyle,
        reduceMotion = LocalReduceMotion.current,
    )
}

internal fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float =
    com.bloo.uicommon.snapToStep(v, range, steps)

/**
 * Softly fades the top/bottom [length] of a vertically scrolling area instead of
 * hard-clipping it at the bounds. The fade only appears on an edge that has more
 * content past it, and eases in as you scroll toward it.
 */
internal fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val lenPx = length.toPx()
        val topAlpha = (scroll.value / lenPx).coerceIn(0f, 1f)
        val botAlpha = ((scroll.maxValue - scroll.value) / lenPx).coerceIn(0f, 1f)
        if (topAlpha > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = lenPx,
                ),
                blendMode = BlendMode.DstIn,
                alpha = topAlpha,
            )
        }
        if (botAlpha > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - lenPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
                alpha = botAlpha,
            )
        }
    }


/**
 * Shared state for dragging a pebble onto (or off) the dual-column hot spot. The
 * dragged section and the live finger position (window coords) are tracked here,
 * and the hot-spot slot publishes its window bounds so we can tell when a drag is
 * hovering it.
 */
internal class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
    val overSlot: Boolean
        get() = section != null && slotSize.width > 0 &&
            pointer.x in slotTopLeft.x..(slotTopLeft.x + slotSize.width) &&
            pointer.y in slotTopLeft.y..(slotTopLeft.y + slotSize.height)
}

internal val LocalHotSeatDrag = staticCompositionLocalOf<HotSeatDrag?> { null }

/** Trivial full-size [Box] wrapper; exists as a distinct composable purely so
 *  the hot-seat drag machinery has a single, stable, named host to reason
 *  about/hang [LocalHotSeatDrag] state around rather than an anonymous Box. */
@Composable
internal fun BackdropHost(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
