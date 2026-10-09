package com.bloo.bluelink.ui

/**
 * Full-detail (single-car) views of the garage: [VehicleDetailContent] (the collapsed single-column
 * car), [ExpandedCar] (the wide dual-column detail), and their shared [CarHeaderRow] fact-chip row.
 */

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

/**
 * Horizontal-swipe-to-switch-cars: once the drag passes a sixth of the node's own width it fires
 * [onSwipeCar] (+1 for a left drag = next car, -1 for a right drag = previous) and re-arms.
 * [ExpandedCar] hangs this on the two non-column surfaces of its page -- the hero card and the
 * header chips row -- so a swipe on the card or on the background changes car, exactly as asked,
 * while every other horizontal drag still belongs to the column pager.
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

// --- Full detail ----------------------------------------------------------

/**
 * Single-column car view (phones, and each column of the grid). Everything scrolls together in one
 * [Column] inside [Refreshable] (header row, then the reorderable [PebbleList]).
 */
@Composable
internal fun VehicleDetailContent(
    v: Vehicle,
    /**
     * State SOURCE (vm.state.value from a collectAsStateWithLifecycle()), consistent with
     * PebbleList/SinglePebble: per-use state.value reads keep this page and its pebble rows stable
     * against emissions that don't touch the values they actually read.
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
    // Narrowed, not `state.value.refreshing`: a bare read here would subscribe this whole page --
    // all three of them live at once in the pager -- to every UiState emission.
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = ScreenGutter),
            verticalArrangement = Arrangement.spacedBy(GapGroup),
        ) {
            // Inset spacer (not padding) so content scrolls *behind* the bars -- topInset alone, no
            // extra breathing room, so the name sits right at the status bar's own edge instead of
            // noticeably below it.
            Spacer(Modifier.height(topInset))
            CarHeaderRow(v, state, hazeState = hazeState)
            // Single column has no separate "hero info column" to pin anything into -- that's a
            // wide/dual-column-only concept (ExpandedCar's own HotspotSlot, with its drag-to-pin
            // secondary slot and "Add a pebble" call to action). pinHotspot = false here lets
            // "controls" (lock/unlock/lights/horn) -- and any pebble the user pinned to the
            // secondary slot -- render as ordinary members of this reorderable list instead,
            // landing in their normal DEFAULT_SECTIONS position (summary, then update, then
            // controls, ...) -- i.e. right below the hero card, with no separate pin/CTA UI of any
            // kind.
            PebbleList(v, state, vm, pinHotspot = false, onExpand = onExpand)
            Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
        }
    }
}

/** Wide expanded view: critical info in one column, pebbles in the other. */
@Composable
internal fun ExpandedCar(
    v: Vehicle,
    /** See VehicleDetailContent's `state` doc -- same source plumbing. */
    state: State<UiState>,
    vm: AppViewModel,
    flipped: Boolean,
    onCollapse: () -> Unit,
    /**
     * Switch to the neighbouring car, -1 = previous / +1 = next -- two-finger-free car swapping
     * driven by a horizontal swipe on the hero card itself (see [CriticalContent]'s own
     * `onSwipeCar`).
     */
    onSwipeCar: (Int) -> Unit = {},
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
    val controlsScroll = rememberScrollState()
    val pebblesScroll = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val controls: @Composable ColumnScope.() -> Unit = {
        // The header chips row is chrome, not a column card, so it reads as "background" -- swiping
        // it switches cars (same modifier the hero card itself carries), rather than scrolling the
        // column pager like any card below it would.
        Box(Modifier.carSwipe(onSwipeCar)) {
            CarHeaderRow(v, state, hazeState = hazeState)
        }
        CriticalContent(v, state, vm, onCollapse = onCollapse, modifier = Modifier.carSwipe(onSwipeCar))
        HotspotSlot(v, hotspots, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        // Pinned pebbles in the hotspot are excluded from the reorderable list
        PebbleList(v, state, vm, exclude = setOf("summary"))
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag) {
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }) {
        // Plain, centred two columns: the hero + controls column on the START side (left in LTR)
        // always, and the standard pebble cards on the other. No flip pager any more -- the columns
        // never swap sides. Horizontal swipes belong to the car pager one level up (and the hero and
        // chip row carry carSwipe), so paging back and forth changes car from anywhere in the view.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter),
                horizontalArrangement = Arrangement.spacedBy(GapSection),
            ) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(controlsScroll),
                    verticalArrangement = Arrangement.spacedBy(GapGroup),
                ) {
                    Spacer(Modifier.height(topInset))
                    controls()
                    Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(pebblesScroll),
                    verticalArrangement = Arrangement.spacedBy(GapGroup),
                ) {
                    Spacer(Modifier.height(topInset))
                    pebbles()
                    Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
                }
            }
        }
    }
    }
}

/**
 * The car's name is only ever drawn ONCE, live, on the hero photo card, so a second copy here would
 * be the same name twice on screen at once -- this row's entire content is the chips, not a name
 * plus a caption underneath it.
 */
@Composable
internal fun CarHeaderRow(
    v: Vehicle,
    /** State SOURCE, not a snapshot -- see VehicleDetailContent's own `state` doc. */
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
