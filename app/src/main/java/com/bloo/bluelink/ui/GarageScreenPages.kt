package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * True while the car pager is at rest (not being dragged or flung). Page content that is expensive to
 * compose, and not needed to start a swipe, waits for this before it builds -- so its cost lands after
 * the motion, not inside it. Null outside a pager (previews, tests): such content builds at once.
 */
internal val LocalPagerIdle = androidx.compose.runtime.staticCompositionLocalOf<State<Boolean>?> { null }

/*
 * The garage's layout, peeled out of GarageScreen: CollapsedGaragePager is the pager of cars (and
 * Settings as one more page) that cycles forever. Each car's own layout scales with the device width
 * (one column, or two when its page is wide enough), so the pager itself never changes size.
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
    currentIndex: Int,
    windowWidthPx: Int,
    state: State<UiState>,
    vm: AppViewModel,
    hazeState: HazeState,
    /** 0 = one column, 1 = two columns: the car's own reflow, sized from the device width. */
    expandedT: State<Float>,
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
        // The aurora leans with the page as it turns, and springs once each time a page settles.
        val aurora = LocalAuroraReactions.current
        LaunchedEffect(pager, aurora) {
            if (aurora == null) return@LaunchedEffect
            snapshotFlow { pager.currentPageOffsetFraction }.collect { aurora.leanX.floatValue = it }
        }
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
                aurora?.let { it.kick.intValue += 1; it.leanX.floatValue = 0f }
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
        val pagerIdle = remember(pager) { androidx.compose.runtime.derivedStateOf { !pager.isScrollInProgress } }
        val blurAmount = remember(pager) { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(pager) {
            // Only a real page swipe drops the glass blur. Any touch-slop wobble while scrolling a car up and
            // down starts the pager for a moment too; dropping and re-raising the blur for that was the
            // aurora "disappearing and reappearing" the instant the finger lifted.
            snapshotFlow { pager.isScrollInProgress && kotlin.math.abs(pager.currentPageOffsetFraction) > 0.05f }
                .distinctUntilChanged()
                .collect { moving ->
                    if (moving) blurAmount.snapTo(0f) else blurAmount.animateTo(1f, androidx.compose.animation.core.tween(MotionMedium))
                }
        }
        val pagerBlur = remember(blurAmount) { { blurAmount.value } }
        androidx.compose.runtime.CompositionLocalProvider(LocalPagerIdle provides pagerIdle, LocalPagerBlur provides pagerBlur) {
        Box(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val columnWidth = with(density) { ((boxWidthPx + perPage - 1) / perPage).coerceAtLeast(1).toDp() }
            // One column per car, `perPage` of them side by side: a car never grows to swallow the
            // pager any more -- the layout INSIDE the page scales (one column or two) with the
            // device width instead.
            val columnPageSize = remember(perPage) {
                object : androidx.compose.foundation.pager.PageSize {
                    override fun androidx.compose.ui.unit.Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int =
                        ((availableSpace + perPage - 1) / perPage).coerceAtLeast(1)
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    .onSizeChanged { boxWidthPx = it.width },
                userScrollEnabled = true,
                pageSize = columnPageSize,
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
                    } else {
                        // One car experience, whatever the device: it reflows to two columns (hero
                        // beside its pebbles, hot seat and all) when its page is wide enough, and to
                        // one column when it is not. No expand button, no second screen.
                        ExpandedCar(
                            vehicles[real], state, vm,
                            pageColumnWidth = columnWidth,
                            expandedT = expandedT,
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
}
