package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.bloo.bluelink.data.Vehicle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch

/*
 * The garage's layout, peeled out of GarageScreen: CollapsedGaragePager is the pager of cars (and
 * Settings as one more page) that cycles forever, and it also carries the in-place expand of one car
 * to full width.
 */

/**
 * The cycling pager of cars (a status card when there are none) with Settings as the page after the
 * last.
 */
@Composable
internal fun CollapsedGaragePager(
    vehicles: List<Vehicle>,
    count: Int,
    slots: Int,
    perPage: Int,
    canExpand: Boolean,
    currentIndex: Int,
    windowWidthPx: Int,
    state: State<UiState>,
    vm: AppViewModel,
    hazeState: HazeState,
    /** The car currently expanded to full width, or null. */
    expandedIdx: Int? = null,
    /** 0 collapsed, 1 expanded: drives the focused page's own reflow. */
    expandedT: Float = 0f,
) {
        // Settings is appended as one more ITEM to the sequence this pager cycles through (index
        // `slots`, right after the last real page), always rendered at exactly one car-column's
        // width (see `pageWidth` below) -- so on a wide/multi-car screen Settings is never wider
        // than one car's column, however many share the screen with it, and SettingsScreen's own
        // LazyColumn is a single column at any width to begin with.
        val total = slots + 1
        // Always the car (or status card) currentIndex was already parked on: this pager opens
        // there, never on the Settings page at the end of it.
        val initialItem = currentIndex.coerceIn(0, slots - 1)
        // One virtual page per real item; the real item for a page is wrap.real(page).
        val wrap = rememberWrapPager(total, initialItem)
        val pager = wrap.pager
        fun realItem(virtualPage: Int) = wrap.real(virtualPage)
        LaunchedEffect(pager, total, perPage) {
            snapshotFlow { pager.settledPage }.collect { page ->
                // Same fade/scale transition the expanded single-car pager above uses (see its own
                // comment for why: the continuous offset is read only inside graphicsLayer{} below,
                // draw-phase only).
                val real = realItem(page)
                // Settings or the zero-car status slot is not a car selection; currentIndex keeps
                // its car.
                if (real < count) vm.selectIndex(real)
                // Settings counts as "on screen" if it sits in ANY of the visible columns, not just
                // the first: on a multi-column layout the search bar expands whenever Settings is
                // showing.
                vm.setOnSettingsPageSlot((0 until perPage.coerceAtLeast(1)).any { realItem(page + it) == slots })
                wrap.recenterIfNearEdge()
            }
        }
        // Resets the flag above the moment this pager itself leaves composition (navigating away
        // from the garage entirely) -- without it, closing Settings-as-embedded by navigating to
        // some OTHER screen (not a car, not standalone Settings) could leave a stale `true` behind
        // with nothing left to correct it, since the collect{} above stops running once this
        // composable is gone.
        DisposableEffect(Unit) { onDispose { vm.setOnSettingsPageSlot(false) } }
        // The above only pushes the pager's own settles into currentIndex, never the other
        // direction -- so an external change (a shortcut tap selecting a specific car while this
        // pager was already composed on a different one) updated currentIndex, but the pager itself
        // just sat there on whatever car it last settled on.
        val skipFirstIndexSnap = remember { mutableStateOf(true) }
        LaunchedEffect(currentIndex) {
            if (skipFirstIndexSnap.value) { skipFirstIndexSnap.value = false; return@LaunchedEffect }
            // Not on Garage any more (mid exit-transition to another screen, Settings included):
            // this composition's `state` param keeps updating live even while AnimatedContent
            // slides its ALREADY- STALE content off screen, so without this guard a snap here still
            // visibly moves the pager underneath its own exit animation -- see the `total` effect
            // below, which skips itself on the way out for the same reason.
            if (state.value.screen != Screen.Garage) return@LaunchedEffect
            wrap.snapToReal(currentIndex.coerceIn(0, slots - 1))
        }
        // A car-count change shifts wrap's modulo divisor and could reshuffle the shown car; snap
        // back to currentIndex. Skips its first firing and the exit transition, like the
        // currentIndex effect.
        val skipFirstTotalSnap = remember { mutableStateOf(true) }
        LaunchedEffect(total) {
            if (skipFirstTotalSnap.value) { skipFirstTotalSnap.value = false; return@LaunchedEffect }
            if (state.value.screen != Screen.Garage) return@LaunchedEffect
            wrap.snapToReal(currentIndex.coerceIn(0, slots - 1))
        }
        // The pixel width of ONE item's page -- viewport width divided by perPage, so `perPage`
        // of them sit side by side at rest.
        var boxWidthPx by remember { mutableIntStateOf(windowWidthPx) }
        Box(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val columnWidth = with(density) { ((boxWidthPx + perPage - 1) / perPage).coerceAtLeast(1).toDp() }
            val fullWidth = with(density) { boxWidthPx.coerceAtLeast(1).toDp() }
            // The focused page grows from one column to the whole width as it expands; every other
            // column is pushed off the side by the same amount.
            val pageWidth = androidx.compose.ui.unit.lerp(columnWidth, fullWidth, expandedT)
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    .onSizeChanged { boxWidthPx = it.width },
                userScrollEnabled = expandedIdx == null,
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed(pageWidth),
                // Never a flat 1: perPage + 2*beyond pages are composed at once, and if that
                // exceeds `total` two virtual pages resolve to the same real item (e.g. Settings
                // mounted twice) and corrupt its state, crashing.
                beyondViewportPageCount = ((total - perPage) / 2).coerceIn(0, 1),
                // Raw page index as key, never the modulo item: duplicate keys crash ("Key already
                // used").
                key = { page -> page },
            ) { page ->
                val real = realItem(page)
                Box(
                    Modifier
                        .fillMaxSize()
                        // The app's page-turn, gentler than the deck's: a car leans away and fades
                        // as it leaves the centre. Read in the draw phase, so a drag never
                        // recomposes the page.
                        .pageTurn(
                            offset = { pageOffsetFraction(pager.currentPage, page, pager.currentPageOffsetFraction) },
                            // Full strength for every layout now: the offset is the FRACTIONAL
                            // distance, so a fully visible neighbour rests flat instead of leaning.
                            strength = 0.75f,
                        ),
                ) {
                    if (real == slots) {
                        // The folded-in Settings item, always the last one -- it's a page in this
                        // pager, not a route, so its own LazyColumn is already a single column at
                        // this page's width (one car-column) regardless of how wide the screen
                        // sharing it is.
                        SettingsScreen(vm)
                    } else if (count == 0) {
                        // No cars: the status card takes the one other slot (Guard.kt).
                        GarageStatusCard(state, vm, hazeState = hazeState)
                    } else if (real == expandedIdx) {
                        // The expanded car: the SAME page, reflowing its pebbles from under the hero
                        // to beside it as expandedT runs, while it grows to take the whole width.
                        ExpandedCar(
                            vehicles[real], state, vm,
                            flipped = false,
                            onCollapse = { vm.collapse() },
                            expandedT = expandedT,
                            hazeState = hazeState,
                        )
                    } else {
                        val gv = vehicles[real]
                        VehicleDetailContent(
                            gv, state, vm,
                            onExpand = if (canExpand) ({ vm.expand(real) }) else null,
                            hazeState = hazeState,
                        )
                    }
                }
            }
            // Stays active mid-drag so it never disappears while swiping.
            StatusBarScrim(hazeState = hazeState)
            // No refresh indicator is drawn; the pull gesture still refreshes.
        }
}
