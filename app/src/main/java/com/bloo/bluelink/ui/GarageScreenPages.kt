package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.bloo.bluelink.data.Vehicle
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/*
 * The garage's two layouts, peeled out of GarageScreen: ExpandedGaragePage is one car full screen,
 * CollapsedGaragePager is the pager of cars (and Settings as one more page) that cycles forever.
 */

/**
 * Resistance when paging: below [PageResistanceThreshold] of a page a drag moves the pages at
 * [PageResistanceGive] of the finger, then past it they follow 1:1. A quick flick crosses the
 * threshold in a single frame, so it just pages.
 */
private const val PageResistanceThreshold = 0.06f
private const val PageResistanceGive = 0.5f

/**
 * Trails the pages behind the finger at the start of a swipe, then lets them catch up. [perPage] is
 * how many pages sit side by side, so one page is a 1/[perPage] slice of the pager. The offset is
 * read in the draw phase, so a drag never recomposes.
 */
@Composable
private fun Modifier.pageResistance(pager: PagerState, perPage: Int): Modifier {
    // The finger's travel since the drag began, as a fraction of a page. The pager's own continuous
    // scroll (page + fraction, which never wraps) drives it rather than raw pointer coordinates:
    // this node is itself translated, so pointer coordinates would shift underneath it and feed
    // back on themselves.
    val trail = remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var catchUp by remember { mutableStateOf<Job?>(null) }
    return this
        .pointerInput(pager) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val start = pager.currentPage + pager.currentPageOffsetFraction
                catchUp?.cancel()
                trail.floatValue = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    val moved = (pager.currentPage + pager.currentPageOffsetFraction) - start
                    trail.floatValue = moved.coerceIn(-PageResistanceThreshold, PageResistanceThreshold)
                    if (!change.pressed) break
                }
                // Ease the trail (and so the offset) away during the pager's own settle, so the
                // hand-off does not pop.
                catchUp = scope.launch {
                    animate(trail.floatValue, 0f, animationSpec = tween(150)) { value, _ ->
                        trail.floatValue = value
                    }
                }
            }
        }
        .graphicsLayer {
            val pageW = size.width / perPage.coerceAtLeast(1)
            if (pageW > 0f) translationX = -PageResistanceGive * trail.floatValue * pageW
        }
}

@Composable
internal fun ExpandedGaragePage(
    vehicles: List<Vehicle>,
    count: Int,
    expandedIdx: Int?,
    state: State<UiState>,
    vm: AppViewModel,
    hazeState: HazeState,
    columnsFlipped: Boolean,
    garageScope: CoroutineScope,
) {
        // Full-screen car; infinite wrap-around via a huge virtual range mapped back with modulo.
        val exWrap = rememberWrapPager(count, (expandedIdx ?: 0).coerceIn(0, count - 1))
        val exPager = exWrap.pager
        LaunchedEffect(exPager) {
            snapshotFlow { exPager.settledPage }.collect {
                vm.expand(exWrap.real(it))
                exWrap.recenterIfNearEdge()
            }
        }
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = exPager,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    // Same resistance as the collapsed pager (see there): a slow drag trails, then
                    // follows 1:1 past the threshold; a quick flick just pages.
                    .pageResistance(exPager, 1),
                // One neighbour pre-warmed per side, never more pages than cars: two composed pages
                // resolving to one car crash. count + wrap means pages composed = 1 + 2 * beyond <=
                // count.
                userScrollEnabled = true,
                beyondViewportPageCount = ((count - 1) / 2).coerceIn(0, 1),
                pageSize = androidx.compose.foundation.pager.PageSize.Fill,
                key = { page -> page },
            ) { page ->
                // Pager offset is read only in graphicsLayer{} (draw phase) so drags never
                // recompose the page. Plain fade/scale only: no blur, tilt or secondary snap spring
                // (they read worse and lagged the drag).
                Box(Modifier.fillMaxSize()) {
                    val pv = vehicles[exWrap.real(page)]
                    ExpandedCar(
                        pv,
                        state,
                        vm,
                        flipped = columnsFlipped,
                        onCollapse = { vm.collapse() },
                        hazeState = hazeState,
                        // A horizontal swipe on the hero card steps to the neighbouring car too.
                        onSwipeCar = { dir ->
                            garageScope.launch {
                                exPager.animateScrollToPage(
                                    (exPager.currentPage + dir).coerceIn(0, exPager.pageCount - 1),
                                )
                            }
                        },
                    )
                }
            }
            // Stays active mid-drag so it never disappears while swiping.
            StatusBarScrim(hazeState = hazeState)
        }
}

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
        Box(Modifier.fillMaxSize()) {
            // The pixel width of ONE item's page -- viewport width divided by perPage, so `perPage`
            // of them sit side by side at rest. onSizeChanged, not BoxWithConstraints: this Box's
            // content recomposes on every drag frame's worth of state (the pager itself), and
            // BoxWithConstraints is a SubcomposeLayout with a real extra composition pass -- the
            // same "measured size via a plain layout callback instead" trade this codebase already
            // makes everywhere else performance-sensitive (CarMap's tile box, PebbleShell's own row
            // height).
            var boxWidthPx by remember { mutableIntStateOf(windowWidthPx) }
            val density = LocalDensity.current
            val pageWidth = with(density) { ((boxWidthPx + perPage - 1) / perPage).coerceAtLeast(1).toDp() }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    .onSizeChanged { boxWidthPx = it.width }
                    .pageResistance(pager, perPage),
                userScrollEnabled = true,
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
                            offset = { (pager.currentPage - page) + pager.currentPageOffsetFraction },
                            strength = 0.5f,
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
