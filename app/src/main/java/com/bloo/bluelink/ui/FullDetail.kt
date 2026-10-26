package com.bloo.bluelink.ui

/**
 * The car's detail view: [ExpandedCar] -- one column, or two side by side when its page is wide
 * enough -- and the shared [CarHeaderRow] fact-chip row.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import com.bloo.uicommon.GapHairline
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.GapPage
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity

// --- Full detail ----------------------------------------------------------

/** Wide expanded view: critical info in one column, pebbles in the other. */
@Composable
internal fun ExpandedCar(
    v: Vehicle,
    state: State<UiState>,
    vm: AppViewModel,
    /**
     * Width of ONE column, as a supplier read in the measure phase. The reflow holds it constant while the page grows
     * from one column to two, so the blocks do not re-wrap; when the page itself is widening (a car going full screen)
     * it eases with the page, frame by frame, so the blocks grow with it instead of jumping to the new width.
     */
    pageColumnWidth: () -> Dp,
    /**
     * Switch to the neighbouring car, -1 = previous / +1 = next -- two-finger-free car swapping
     * driven by a horizontal swipe on the hero card itself (see [CriticalContent]'s own
     * `onSwipeCar`).
     */
    onSwipeCar: (Int) -> Unit = {},
    /**
     * 0 = the pebbles sit UNDER the hero (one column), 1 = BESIDE it (two columns). Driven by the
     * device width now, not a manual expand. A STATE, not a plain float: read only where it flips
     * ([expanded]) and in the reflow's measure phase, so a running animation never recomposes the
     * car every frame.
     */
    expandedT: State<Float> = remember { mutableFloatStateOf(1f) },
    /**
     * The hero's trailing header control. Null on a narrow single column (the hero keeps its own
     * expand chevron); in the grid it is the full-screen button, which replaces that chevron.
     */
    heroAction: PebbleHeaderAction? = null,
    /** See [CarHeaderRow]'s own doc -- forwarded through so its chips can blur. */
    hazeState: HazeState? = null,
) {
    // All derived, so this view recomposes when the hotspot sections or the refresh flag actually
    // changes -- not on every UiState emission for every car.
    val hotspots by remember(v) {
        derivedStateOf {
            state.value.hotspotFor(v.vin).filter {
                it in state.value.sectionsFor(v) && state.value.isSectionAvailable(v, it)
            }
        }
    }
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    val hotDrag = remember { HotSeatDrag() }
    // Hoisted (not recreated on flip) so each column keeps its own scroll position when the columns
    // swap sides. controlsScroll always belongs to whichever COLUMN currently renders `controls`
    // (and therefore CriticalContent's own HeroHeader), regardless of which physical side
    // (left/right) that currently is: the leftScroll/rightScroll pairing below always keeps this
    // same ScrollState paired with the same content across a flip -- which is what makes it the
    // right thing for the badge's own tap-to-scroll-to-top.
    val pebblesScroll = rememberScrollState()
    // The aurora leans as the page scrolls; see ExpandedCar.
    val aurora = LocalAuroraReactions.current
    LaunchedEffect(pebblesScroll, aurora) {
        if (aurora == null) return@LaunchedEffect
        // Quantized like the page lean (AuroraLeanStep): every write repaints the whole backdrop, and
        // a scroll would otherwise write it on every scrolling frame.
        var last = 0f
        snapshotFlow { pebblesScroll.value to pebblesScroll.maxValue }.collect { (v, m) ->
            val lean = if (m > 0) (v.toFloat() / m) * 2f - 1f else 0f
            if (kotlin.math.abs(lean - last) >= AuroraLeanStep) {
                last = lean
                aurora.leanY.floatValue = lean
            }
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Full screen (two columns): the hot seat is shown and its pinned pebbles are pulled out of the
    // stack. Collapsed (one column): it is hidden and they go back into the stack. Derived, so this
    // recomposes only when the boolean flips (at the midpoint), not on every animation frame.
    val expanded by remember { derivedStateOf { expandedT.value >= 0.5f } }
    // Composed the moment the expand STARTS, not at the 0.5 flip: the seat (a full controls pebble)
    // is the heaviest thing that flip used to add, and composing it mid-animation was a visible
    // hitch. It stays collapsed and transparent until the midpoint (see [hotSeatReveal]), so the
    // visual timing is unchanged -- only the composition moves off the flip.
    val hotSeatPresent by remember { derivedStateOf { expandedT.value > 0.001f } }
    // One column wide, held constant through the animation (see ReflowPair's own doc): the page
    // grows to two columns but each block stays a single column's width, so nothing re-wraps.
    val blockWidth: () -> Dp = { (minOf(pageColumnWidth(), 960.dp) - ScreenGutter * 2).coerceAtLeast(1.dp) }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag) {
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }) {
        // One scrolling page whose two blocks reflow by [expandedT]: the pebble list sits UNDER the
        // hero while collapsed and slides up BESIDE it while expanded. Same content either way, so
        // expanding/collapsing is an in-place animation, not a screen swap.
        // Read only in the hot seat's offset (layout phase): as composition state it recomposed this whole
        // page on every swipe frame, since the window position moves with the pager.
        // The page's scrolling edges, for the liquid-glass distortion of anything sliding under the status bar
        // or the bottom bar: the viewport is the page less the two insets, in window coordinates.
        val edgeViewportPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val edgeWindowTop = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val edgeScroll = remember(pebblesScroll) { EdgeScroll(pebblesScroll, edgeViewportPx, edgeWindowTop) }
        val edgeDensity = LocalDensity.current
        val edgeTopPx = with(edgeDensity) { topInset.toPx() }
        val edgeBottomPx = with(edgeDensity) { bottomInset.toPx() }
        val pagerIdle = LocalPagerIdle.current
        CompositionLocalProvider(LocalEdgeScroll provides edgeScroll) {
        Box(
            Modifier.fillMaxSize().onGloballyPositioned {
                val origin = it.localToWindow(Offset.Zero)
                // The pin target reaches from the slot to the bottom of the page.
                hotDrag.zoneBottom = origin.y + it.size.height
                // Not mid-swipe: the page's window position moves with the swipe (see glassEdgeFocus).
                if (pagerIdle?.value != false) {
                    edgeWindowTop.floatValue = origin.y + edgeTopPx
                    edgeViewportPx.intValue = (it.size.height - edgeTopPx - edgeBottomPx).toInt().coerceAtLeast(0)
                }
            },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .verticalScroll(pebblesScroll)
                    .padding(horizontal = ScreenGutter),
            ) {
                Spacer(Modifier.height(topInset))
                ReflowPair(
                    expandedT = { expandedT.value },
                    blockWidth = blockWidth,
                    // A little air above the pebble column: beside the hero it started flush with the page top, close under the status bar.
                    trailInset = GapPage,
                    lead = {
                        Column(Modifier.zIndex(if (hotDrag.pinnedActive) 1f else 0f), verticalArrangement = Arrangement.spacedBy(GapGroup)) {
                            // No bespoke swipe handler here any more: the car pager one level up owns
                            // every horizontal swipe (the columns scroll vertically only, so a
                            // horizontal drag falls through to it).
                            CarHeaderRow(v, state, hazeState = hazeState)
                            Box(Modifier.glassEdgeFocus()) { CriticalContent(v, state, vm, heroAction = heroAction) }
                            // The hot seat lives only in the full-screen two-column view; collapsed,
                            // its pinned pebbles are ordinary members of the stack below. Its reveal
                            // rides the reflow fraction itself (measure/draw only, never recomposed).
                            if (hotSeatPresent) {
                                Box(Modifier.hotSeatReveal { expandedT.value }) {
                                    HotspotSlot(v, hotspots, state, vm, expanded)
                                }
                            }
                        }
                    },
                    trail = {
                        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
                            PebbleList(v, state, vm, exclude = setOf("summary"), pinHotspot = expanded)
                        }
                    },
                )
                Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
            }
        }
        }
    }
    }
}

/**
 * Reveals the hot seat as the reflow proceeds: it stays collapsed (zero height, transparent) until
 * the reflow's midpoint, then grows and fades in. The fraction is read only in the measure and draw
 * phases, so an expand never recomposes the seat. Because its HEIGHT ramps with the same fraction,
 * the lead block's height (and so where the trail stacks under it) changes smoothly too, instead of
 * jumping the moment the seat appears.
 */
private fun Modifier.hotSeatReveal(t: () -> Float): Modifier = this
    .graphicsLayer {
        val f = hotSeatFraction(t())
        alpha = f
        // Clipped only while it is still arriving: once settled nothing is cut, so the pinned pebble's shadow is whole and a
        // pebble dragged out of the seat is not cropped at its edge.
        clip = f < 1f
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val f = hotSeatFraction(t())
        layout(placeable.width, (placeable.height * f).roundToInt().coerceAtLeast(0)) {
            placeable.place(0, 0)
        }
    }

/** 0 until the reflow's midpoint, then a smoothstep up to 1 at the end. */
private fun hotSeatFraction(t: Float): Float {
    val x = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * The car's name is only ever drawn ONCE, live, on the hero photo card, so a second copy here would
 * be the same name twice on screen at once -- this row's entire content is the chips, not a name
 * plus a caption underneath it.
 */
@Composable
internal fun CarHeaderRow(
    v: Vehicle,
    /** State SOURCE, not a snapshot -- see ExpandedCar's own `state` doc. */
    state: State<UiState>,
    hazeState: HazeState? = null,
) {
    val meta by remember(v) { derivedStateOf { "${v.model} · ${state.value.powertrainLabel(v)}" } }
    val fetchedAt by remember(v) { derivedStateOf { state.value.fetchedAt(v) } }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(GapRow),
                verticalArrangement = Arrangement.spacedBy(GapHairline),
            ) {
                MetaChip(meta, hazeState = hazeState)
                LastUpdatedLabel(fetchedAt, hazeState = hazeState)
            }
        }
    }
}
