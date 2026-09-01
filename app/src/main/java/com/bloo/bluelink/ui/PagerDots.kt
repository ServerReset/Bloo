@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.bloo.uicommon.PagerDotColors

/** Vertical sibling of [PagerDots] for the cover-screen tile stack.
 *
 * Long-pressing the indicator expands it into a scrubber: slide finger up/down
 * to jump between pages quickly. Each 14 dp of drag moves one page.
 */
@Composable
internal fun VerticalPagerDots(
    current: Int,
    count: Int,
    tiles: List<String>,
    onPageJump: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubStartPage by remember { mutableIntStateOf(0) }
    var scrubAccumY by remember { mutableFloatStateOf(0f) }
    // `current` closed over by a long-lived gesture coroutine, not read fresh each
    // gesture: Modifier.pointerInput(count) below only cancels-and-relaunches its
    // block when `count` (tiles.size) changes, not when `current` does -- ordinary
    // tile swipes change neither, and this composable's own car page survives many
    // of them (the outer car HorizontalPager keeps a neighbour alive via
    // beyondViewportPageCount). So the coroutine launched once, captured whatever
    // `current` was at that moment, and every long-press afterward -- regardless
    // of which tile was actually showing by then -- started the scrub from that
    // one frozen value. Reported as "hold the rail and it resets you to [a fixed]
    // page". rememberUpdatedState is the standard fix for exactly this: the
    // coroutine still only restarts on `count` changing, but reads .value fresh
    // on every gesture instead of the parameter it closed over at launch.
    val currentState = rememberUpdatedState(current)
    val density = LocalDensity.current
    val jumpScope = rememberCoroutineScope()
    // Shorter travel per page = a more sensitive scrub.
    val pxPerPage = with(density) { 14.dp.toPx() }
    // Shared flag so the parent HorizontalPager can lock car-switching swipes.
    val coverScrubbing = LocalCoverScrubbing.current
    val haptics = LocalHaptics.current

    // Drag down → higher page index (later tiles); drag up → lower index (earlier tiles).
    val scrubTargetPage by remember {
        derivedStateOf {
            (scrubStartPage + (scrubAccumY / pxPerPage).roundToInt()).coerceIn(0, count - 1)
        }
    }
    // Same tick-per-step convention as AnimatedSlider/MorphSegmented; the first
    // firing (right as scrubbing starts) doubles as a "scrub mode entered" tick,
    // matching ReorderColumn's onDragStart tick for the analogous pebble-drag gesture.
    LaunchedEffect(scrubTargetPage, scrubbing) {
        if (scrubbing) { haptics?.tick(); onPageJump(scrubTargetPage) }
    }

    fun tileName(t: String) = when (t) {
        "main" -> "Car"
        else -> t.replaceFirstChar { it.uppercase() }
    }

    // Resting paddings/spacing bumped up (was 6/10/6) so the rail is a more
    // comfortable thumb target on the cover — the previous ~19dp-wide sliver was a
    // fifth of the app's 48dp min target. The invisible gesture Box already spans
    // 48dp wide (below); this widens the VISIBLE rail so it reads as tappable too.
    val hPad by animateDpAsState(if (scrubbing) 18.dp else 9.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubHPad")
    val vPad by animateDpAsState(if (scrubbing) 18.dp else 12.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubVPad")
    val itemSpacing by animateDpAsState(if (scrubbing) 14.dp else 8.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubSpacing")
    val cornerRadius by animateDpAsState(if (scrubbing) 20.dp else 100.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubCorner")
    val surfaceAlpha by animateFloatAsState(if (scrubbing) 0.92f else 0.7f, label = "scrubAlpha")

    Box(
        // The resting pill is only as wide as its 7dp dot column plus 6dp
        // padding on each side (~19dp) -- a fifth of the app's own 48dp
        // minimum touch target (FloatingIcon, standard IconButtons), on this
        // screen's most cramped device widths, for the only way to enter the
        // scrub gesture. The gesture/semantics live on this wider invisible
        // Box; the Surface below stays visually as narrow as before.
        modifier = modifier
            .widthIn(min = 48.dp)
            .pointerInput(count) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    longPress.consume()
                    scrubbing = true
                    coverScrubbing?.value = true
                    scrubStartPage = currentState.value
                    scrubAccumY = 0f
                    try {
                        verticalDrag(longPress.id) { change ->
                            change.consume()
                            scrubAccumY += (change.position - change.previousPosition).y
                        }
                    } finally {
                        // Always clear, even if the gesture is cancelled, so the
                        // parent never gets stuck with car-switching disabled.
                        scrubbing = false
                        coverScrubbing?.value = false
                    }
                }
            }
            // Entirely gesture-driven (long-press-then-drag-to-scrub) with no
            // semantics at all -- with TalkBack's touch exploration
            // intercepting single-finger gestures, this was both unreachable
            // as its own focus stop and the scrub gesture itself couldn't be
            // performed. contentDescription announces which tile is showing;
            // customActions exposes a direct "go to this tile" action per
            // tile (onPageJump is the same suspend jump function the scrub
            // gesture already calls, so this is the exact same code path, not
            // a parallel one that could drift out of sync).
            .semantics {
                contentDescription = "Showing ${tileName(tiles.getOrElse(current) { "" })} tile, ${current + 1} of $count"
                customActions = tiles.mapIndexedNotNull { i, t ->
                    if (i == current) return@mapIndexedNotNull null
                    CustomAccessibilityAction("Go to ${tileName(t)}") {
                        jumpScope.launch { onPageJump(i) }
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                // Same gap as PagerDots below -- only ever had Material's own weak
                // tonal shadowElevation, no real shadow or rim, on a pill that
                // floats over the same unpredictable car-photo backgrounds.
                .ambientRing(RoundedCornerShape(cornerRadius))
                .dropShadow(RoundedCornerShape(cornerRadius))
                .frostedRim(RoundedCornerShape(cornerRadius)),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = surfaceAlpha),
        ) {
            Column(
                Modifier.padding(horizontal = hPad, vertical = vPad),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                horizontalAlignment = Alignment.End,
            ) {
                repeat(count) { i ->
                    val selected = i == current
                    val scrubSelected = scrubbing && i == scrubTargetPage
                    val highlight = selected || scrubSelected
                    val dotH by animateDpAsState(
                        if (highlight) 28.dp else 9.dp,
                        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                        label = "vdotH",
                    )
                    val dotW by animateDpAsState(
                        if (scrubbing) 10.dp else 9.dp,
                        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                        label = "vdotW",
                    )
                    val color by androidx.compose.animation.animateColorAsState(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            scrubSelected -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        label = "vdotC",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (scrubbing) {
                            tiles.getOrNull(i)?.let { tileName ->
                                Text(
                                    tileName(tileName),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                                    color = color,
                                )
                            }
                        }
                        Box(Modifier.width(dotW).height(dotH).clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

/** Page indicator dots, optionally with long-press-to-refresh — holding the
 *  indicator for one second triggers [onRefresh] (mirrors the watch's
 *  CarNameOverlay pattern). Passing null drops the whole gesture (and its
 *  fill-ring) entirely instead of just disarming the action -- the cover
 *  screen's own edge-trace gesture already owns refresh there, and even a
 *  quick tap-through on the dots (e.g. brushing them mid-swipe) started that
 *  ring filling for a frame, which read as a spurious "refresh" flicker on
 *  every plain press. */
/**
 * Reads `pager.currentPage` INSIDE its own restartable composable scope.
 *
 * This exists for one reason, and it was the single worst frame-stall in the
 * app. Every call site put `PagerDots(current = real(pager.currentPage))` in a
 * `Box` — and `Box` is an INLINE composable, so it is not its own recomposition
 * scope. The nearest restartable scope was the one that also contains the
 * sibling `HorizontalPager` call. `currentPage` flips the instant a drag crosses
 * the halfway point — i.e. at peak finger velocity — so that flip invalidated
 * the whole scope, re-invoked HorizontalPager with a freshly allocated content
 * lambda, and recomposed EVERY live page: three cars' full pebble columns, ~30
 * pebbles, in one frame, in the middle of every single swipe.
 *
 * Reading it one level down confines the invalidation to the dots. Keep the read
 * in here — hoisting it back to the call site silently restores the stall.
 */
/** How much extra top clearance a car needs to reserve when [PagerDots] is
 *  showing (see [VehicleDetailContent]'s own `reserveTopForDots`) -- the
 *  dots' own `top = 10.dp` position plus roughly their own control height
 *  (7dp dots + 6dp vertical padding each side, plus the glass rim/shadow's
 *  own visual bulk), rounded up with a little breathing room rather than
 *  measured exactly. Generous on purpose: a few dp of unclaimed space above
 *  the chips is invisible; a few dp of real overlap is a toggle switch
 *  sitting behind the "Updated x ago" text. */
internal val PagerDotClearance = 40.dp

@Composable
internal fun PagerDotsFor(
    pager: PagerState,
    count: Int,
    real: (Int) -> Int,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
) {
    // The collision dodge goes through the floating registry rather than a State hand-threaded
    // down from GarageScreen (which had to be forwarded through TitleFlightOverlay ->
    // FloatingNamePill -> FullDetail to get there in the first place): .dodgeFloating fades
    // these out whenever anything else floating -- today the flying car name -- is in that spot.
    //
    // Publishing the bounds is the CALLER's job, via Modifier.floatingOverlay on the `modifier`
    // passed in, and deliberately not done here as well: that modifier carries the status-bar
    // inset and the pull-to-refresh shift, so the caller's node is the one whose rect is where
    // the dots really are. Registering here too would just have two writers racing on one id.
    // Theme-only choices stay in the app: this wrapper is the one place that
    // translates Material colors + app chrome into the uicommon core's
    // parameterized [PagerDotColors], so the core never imports material3.
    val haptics = LocalHaptics.current
    val colors = PagerDotColors(
        active = MaterialTheme.colorScheme.primary,
        inactive = MaterialTheme.colorScheme.outlineVariant,
        ringTrack = MaterialTheme.colorScheme.surfaceVariant,
        ringFill = MaterialTheme.colorScheme.primary,
        pill = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
    )
    com.bloo.uicommon.PagerDots(
        current = real(pager.currentPage),
        count = count,
        modifier = modifier.dodgeFloating(FloatingIds.PagerDots),
        onRefresh = onRefresh,
        haptics = haptics?.let { { it.tick() } },
        colors = colors,
    )
}

