@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Full-detail (single-car) views of the garage: [VehicleDetailContent] (the
 * collapsed single-column car), [ExpandedCar] (the wide dual-column detail),
 * and their shared [CarHeaderRow] fact-chip row. Peeled out of GarageScreen.kt;
 * they keep their original `internal` visibility.
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Horizontal-swipe-to-switch-cars: once the drag passes a sixth of the node's own width it
 * fires [onSwipeCar] (+1 for a left drag = next car, -1 for a right drag = previous) and
 * re-arms. [ExpandedCar] hangs this on the two non-column surfaces of its page -- the hero
 * card and the header chips row -- so a swipe on the card or on the background changes car,
 * exactly as asked, while every other horizontal drag still belongs to the column pager.
 */
internal fun Modifier.carSwipe(onSwipeCar: (Int) -> Unit): Modifier = pointerInput(onSwipeCar) {
    var accum = 0f
    detectHorizontalDragGestures(
        onDragEnd = { accum = 0f },
        onDragCancel = { accum = 0f },
    ) { change, dragAmount ->
        change.consume()
        accum += dragAmount
        if (abs(accum) > size.width / 6f) {
            onSwipeCar(if (accum < 0f) 1 else -1)
            accum = 0f
        }
    }
}

/**
 * Swallows horizontal drags so they never reach an ancestor horizontal pager. Put on each pebble
 * card in a car's page so a sideways drag on a pebble does NOT flip to the next car, while the
 * gaps BETWEEN pebbles and the hero card (neither of which carries this) still do -- requested
 * directly: "you can only change the pages of cars when you're swiping between pebbles or on the
 * hero card." Vertical drags pass through untouched, so the list still scrolls, and a child that
 * owns the gesture (a temperature slider) still consumes it before this sees it.
 */
internal fun Modifier.consumeHorizontalDrags(): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures { change, _ -> change.consume() }
}

// --- Full detail ----------------------------------------------------------

/**
 * Single-column car view (phones, and each column of the grid). Everything
 * scrolls together in one [Column] inside [Refreshable] (header row, then
 * the reorderable [PebbleList]).
 *
 * The car's name is real, visible content inside the hero card's own title slot -- there is no
 * floating corner badge that takes over once it scrolls out of view. There used to be (and,
 * before that, a floating "Settings" badge too) -- both removed as unwanted UI, along with the
 * whole TitleFlight/FloatingTitlePill/dock-on-scroll system that existed only to drive them.
 */
@Composable
internal fun VehicleDetailContent(
    v: Vehicle,
    /**
     * State SOURCE (vm.state.value from a collectAsStateWithLifecycle()), consistent with
     * PebbleList/SinglePebble:
     * per-use state.value reads keep this page and its pebble rows stable against
     * emissions that don't touch the values they actually read.
     */
    state: State<UiState>,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    /** See [CarHeaderRow]'s own doc -- forwarded through so its chips can blur. */
    hazeState: HazeState? = null,
) {
    com.bloo.bluelink.data.StartupTrace.once("vehicle-detail-${v.vin}", "VehicleDetailContent composing for ${v.name}")
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    // Narrowed, not `state.value.refreshing`: a bare read here would subscribe this whole page
    // -- all three of them live at once in the pager -- to every UiState emission.
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }, hazeState = hazeState) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            // Inset spacer (not padding) so content scrolls *behind* the bars --
            // topInset alone, no extra breathing room, so the name sits right at
            // the status bar's own edge instead of noticeably below it.
            Spacer(Modifier.height(topInset))
            CarHeaderRow(v, state, hazeState = hazeState)
            // Single column has no separate "hero info column" to pin anything into --
            // that's a wide/dual-column-only concept (ExpandedCar's own HotspotSlot,
            // with its drag-to-pin secondary slot and "Add a pebble" call to action).
            // pinHotspot = false here lets "controls" (lock/unlock/lights/horn) --
            // and any pebble the user pinned to the secondary slot -- render as
            // ordinary members of this reorderable list instead, landing in their
            // normal DEFAULT_SECTIONS position (summary, then update, then controls,
            // ...) -- i.e. right below the hero card, with no separate pin/CTA UI of
            // any kind. Previously this called HotspotSlot directly, same as
            // ExpandedCar -- which did put "controls" back on screen, but ALSO
            // surfaced the wide-layout-only "Add a pebble" drag target above the hero
            // card, on every phone, reported directly as the layout looking wrong.
            PebbleList(v, state, vm, pinHotspot = false, onExpand = onExpand, blockHorizontalDrags = true)
            // Reserves exactly as much room as the floating search bubble (SearchLayer, mounted
            // globally for Screen.Garage -- see Screens.kt's `searchable` gate) actually needs,
            // read live off its own reported bounds -- not a flat guessed height. A guess here
            // (the old +132dp) is exactly what let the last pebble's own trailing chevron sit
            // directly under the bubble, visibly cut off by it, once confirmed from a real
            // screenshot; see searchBarClearance's own doc for why a fixed constant can't stay
            // right. bottomInset itself is folded into the live value (the bar sits above it),
            // so it is not added again here.
            Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
        }
    }
}

/**
 * Wide expanded view: critical info in one column, pebbles in the other.
 *
 * `controls` and `pebbles` are held as `@Composable` lambdas (not directly
 * inlined) so [flipped] can freely swap which one renders in the left vs.
 * right [Column] without re-creating either column's content -- each
 * column's own [rememberScrollState] (`controlsScroll`/`pebblesScroll`) is
 * hoisted here rather than created inside `controls`/`pebbles` themselves,
 * so a scroll position sticks with its *content* across a flip rather than
 * with whichever physical column (left/right) currently renders it.
 * [HotspotSlot] lets one pebble be pinned into the info column permanently
 * (excluded from the normal reorderable pebble list via `exclude` above);
 * [HotSeatDrag] (provided via [LocalHotSeatDrag]) is the cross-column drag
 * state that lets a pebble be dragged from the scrolling list directly onto
 * that slot to pin it.
 *
 * No floating corner name badge here any more -- removed as unwanted UI, along with the whole
 * floating-title system. The car's name is real, visible content on CriticalContent's own
 * HeroHeader, same as [VehicleDetailContent]; it simply scrolls off with the rest of that column
 * once it's flipped out of view, like any other content.
 */
@Composable
internal fun ExpandedCar(
    v: Vehicle,
    /** See VehicleDetailContent's `state` doc -- same source plumbing. */
    state: State<UiState>,
    vm: AppViewModel,
    flipped: Boolean,
    /** Collapse back to the multi-column grid -- surfaced as the hero card's own header
     *  action (see [HeroHeader]'s `expandAction`) instead of a separate floating back
     *  button, now that this view has one already for the opposite direction. */
    onCollapse: () -> Unit,
    /** Switch to the neighbouring car, -1 = previous / +1 = next -- two-finger-free car
     *  swapping driven by a horizontal swipe on the hero card itself (see
     *  [CriticalContent]'s own `onSwipeCar`). The column pager owns every other horizontal
     *  drag on this screen, so the hero is the deliberate, discoverable seam where a swipe
     *  means "different car" instead of "different column". */
    onSwipeCar: (Int) -> Unit = {},
    /** See [CarHeaderRow]'s own doc -- forwarded through so its chips can blur. */
    hazeState: HazeState? = null,
) {
    // All derived, so this view recomposes when the hotspot sections or the refresh flag
    // actually changes -- not on every UiState emission for every car.
    val hotspots by remember(v) {
        derivedStateOf {
            state.value.hotspotFor(v.vin).filter {
                it in state.value.sectionsFor(v) && state.value.isSectionAvailable(v, it)
            }
        }
    }
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    val hotDrag = remember { HotSeatDrag() }
    // Hoisted (not recreated on flip) so each column keeps its own scroll
    // position when the columns swap sides. controlsScroll always belongs
    // to whichever COLUMN currently renders `controls` (and therefore
    // CriticalContent's own HeroHeader), regardless of which physical side
    // (left/right) that currently is: the leftScroll/rightScroll pairing
    // below always keeps this same ScrollState paired with the same content
    // across a flip -- which is what makes it the right thing for the
    // badge's own tap-to-scroll-to-top.
    val controlsScroll = rememberScrollState()
    val pebblesScroll = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // CriticalContent's own HeroHeader is the real hero photo card here --
    // this view was NOT missing one the way the doc above used to claim;
    // CarHeaderRow's plain-text name and HeroHeader's own (on the photo)
    // were simply both visible at once, the exact duplicate-name bug fixed
    // everywhere else in the app. hideName = true here, matching
    // VehicleDetailContent's own CarHeaderRow call exactly.
    val controls: @Composable ColumnScope.() -> Unit = {
        // The header chips row is chrome, not a column card, so it reads as "background" --
        // swiping it switches cars (same modifier the hero card itself carries), rather than
        // scrolling the column pager like any card below it would.
        Box(Modifier.carSwipe(onSwipeCar)) {
            CarHeaderRow(v, state, hazeState = hazeState)
        }
        CriticalContent(v, state, vm, onCollapse = onCollapse, swipeModifier = Modifier.carSwipe(onSwipeCar))
        HotspotSlot(v, hotspots, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        // Pinned pebbles in the hotspot are excluded from the reorderable list
        PebbleList(v, state, vm, exclude = setOf("summary"))
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag) {
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }, hazeState = hazeState) {
        // The two columns live in a genuinely infinite horizontal pager now: real page 0 is
        // [controls | pebbles], real page 1 is [pebbles | controls], and the wrap pager loops
        // them without end -- dragging left/right slides the pair around forever instead of the
        // old discrete flip (AnimatedContent's one-shot swap), which is what "scroll between the
        // two columns, infinitely, moving them left and right" asked for. At rest the two
        // visible pages are exactly the same pair the old Row drew, so the LAYOUT is unchanged;
        // only the gesture became a real, continuous, unbounded scroll. Each column keeps its
        // own hoisted scroll state, so a scroll sticks with its CONTENT across a swap rather
        // than with whichever physical side it currently renders on.
        //
        // `flipped` (the persisted setting) stays the single source of truth: the pager drives
        // it on settle, and an external change snaps the pager back -- the same two-way sync
        // the collapsed car pager in GarageScreen uses.
        val flipWrap = rememberWrapPager(2, if (flipped) 1 else 0)
        val flipPager = flipWrap.pager
        LaunchedEffect(flipPager) {
            snapshotFlow { flipPager.settledPage }.collect { page ->
                flipWrap.recenterIfNearEdge()
                val nowFlipped = flipWrap.real(page) == 1
                if (nowFlipped != flipped) vm.setColumnsFlipped(nowFlipped)
            }
        }
        val skipFirstFlipSnap = remember { mutableStateOf(true) }
        LaunchedEffect(flipped) {
            if (skipFirstFlipSnap.value) { skipFirstFlipSnap.value = false; return@LaunchedEffect }
            flipWrap.snapToReal(if (flipped) 1 else 0)
        }
        HorizontalPager(
            state = flipPager,
            modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fill,
            beyondViewportPageCount = 0,
            key = { page -> page },
        ) { page ->
            val isFlipped = flipWrap.real(page) == 1
            val leftCol = if (isFlipped) pebbles else controls
            val rightCol = if (isFlipped) controls else pebbles
            val leftScroll = if (isFlipped) pebblesScroll else controlsScroll
            val rightScroll = if (isFlipped) controlsScroll else pebblesScroll
            val topSpacerHeight = topInset + HeaderCornerGap + HeaderButtonSize + HeaderContentClearance
            val bottomSpacerHeight = searchBarClearance(fallback = bottomInset + 132.dp)
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 960.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(leftScroll),
                        verticalArrangement = Arrangement.spacedBy(GapGroup),
                    ) {
                        Spacer(Modifier.height(topSpacerHeight))
                        leftCol()
                        Spacer(Modifier.height(bottomSpacerHeight))
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rightScroll),
                        verticalArrangement = Arrangement.spacedBy(GapGroup),
                    ) {
                        Spacer(Modifier.height(topSpacerHeight))
                        rightCol()
                        Spacer(Modifier.height(bottomSpacerHeight))
                    }
                }
            }
        }
    }
    }
}

/**
 * A row of small fact chips (model/powertrain, "updated x ago"). The car's name
 * is only ever drawn ONCE, live, on the hero photo card, so a second copy here
 * would be the same name twice on screen at once -- this row's entire content
 * is the chips, not a name plus a caption underneath it.
 *
 * The "expand to full screen" button that used to float here (and "back to
 * all cars"/"flip columns" as separate screen-level floating icons) all moved
 * onto the hero card's own header instead -- see [HeroHeader]'s `expandAction`
 * -- so this row is chips only now, CenterVertically since there is no longer
 * a taller icon beside them to align against.
 */
@Composable
internal fun CarHeaderRow(
    v: Vehicle,
    /**
     * State SOURCE, not a snapshot -- see VehicleDetailContent's own `state` doc. Taking a
     * plain UiState here made this row's two callers (a page body, and the expanded view)
     * subscribe to EVERY UiState emission just to render a model name and a relative
     * timestamp. Both slices below are derived, so this row recomposes when the two strings
     * it actually shows change, and not when some other car's weather ticks.
     */
    state: State<UiState>,
    /** The screen's own [HazeState] (see GarageScreen's own `hazeSource` doc) -- threaded
     *  through so these chips get a real backdrop blur instead of the flat-tint fallback
     *  every [GlassSurface] uses with no hazeState in scope. */
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(GapHairline),
            ) {
                MetaChip(meta, hazeState = hazeState)
                LastUpdatedLabel(fetchedAt, hazeState = hazeState)
            }
        }
    }
}
