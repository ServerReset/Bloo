package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/*
 * The garage's two layouts, peeled out of GarageScreen: ExpandedGaragePage is one car full screen,
 * CollapsedGaragePager is the pager of cars (and Settings as one more page) that cycles forever.
 */

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
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
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
        // Settings is one more item (index `slots`) in the cycling sequence, one car-column wide.
        val total = slots + 1
        // Opens on the car (or status card) already selected, never on Settings.
        val initialItem = currentIndex.coerceIn(0, slots - 1)
        // One virtual page per real item; the real item for a page is wrap.real(page).
        val wrap = rememberWrapPager(total, initialItem)
        val pager = wrap.pager
        fun realItem(virtualPage: Int) = wrap.real(virtualPage)
        LaunchedEffect(pager, total, perPage) {
            snapshotFlow { pager.settledPage }.collect { page ->
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
        // Clears the flag when the pager leaves composition so it can't go stale.
        DisposableEffect(Unit) { onDispose { vm.setOnSettingsPageSlot(false) } }
        // Jump (no fly-through) when currentIndex changes externally, e.g. a shortcut tap. This and
        // the `total` effect skip their first firing so they don't overwrite initialItem's seed.
        val skipFirstIndexSnap = remember { mutableStateOf(true) }
        LaunchedEffect(currentIndex) {
            if (skipFirstIndexSnap.value) { skipFirstIndexSnap.value = false; return@LaunchedEffect }
            // Skip while exiting the Garage: `state` keeps updating under the stale exit animation.
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
            // Pixel width of one item's page: viewport / perPage. onSizeChanged, not
            // BoxWithConstraints (no extra subcomposition per drag frame).
            var boxWidthPx by remember { mutableIntStateOf(windowWidthPx) }
            val density = LocalDensity.current
            val pageWidth = with(density) { ((boxWidthPx + perPage - 1) / perPage).coerceAtLeast(1).toDp() }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    .onSizeChanged { boxWidthPx = it.width },
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
                Box(Modifier.fillMaxSize()) {
                    if (real == slots) {
                        // Folded-in Settings page, one car-column wide. Always composed (pre-warmed
                        // as a neighbour) so a swipe onto it never shows an empty page.
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
