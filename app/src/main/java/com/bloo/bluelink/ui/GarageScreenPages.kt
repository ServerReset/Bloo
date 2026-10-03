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
 * The garage's two layouts, peeled out of GarageScreen: ExpandedGaragePage is one car full
 * screen, CollapsedGaragePager is the pager of cars (and Settings as one more page) that cycles
 * forever. GarageScreen keeps the state they both read and decides which one shows.
 */

/** One car full screen, with the car pager that re-seeds on whichever car was expanded. */
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
        // Full-screen car; swipe left/right to switch cars. Infinite
        // wrap-around: start in the middle of a huge virtual range and
        // map each virtual page back onto a real car with modulo --
        // same technique the cover screen's tile pager already uses.
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
                // Finger swipe between cars is disabled per user request (the
                // page-to-page swipe felt bad). To view a different car
                // full-screen the user collapses back to the grid (the "Back to
                // all cars" button / system back) and expands another car, which
                // re-seeds this pager on that car via rememberWrapPager above.
                userScrollEnabled = false,
                // Paired with userScrollEnabled=false above: the expanded pager
                // has NO finger swipe, so its neighbour pages can never be shown
                // or scrolled to — pre-warming them is pure dead weight. Worse,
                // ExpandedCar is heavier than a collapsed page (dual column, two
                // scrolls, force-expanded hotspot) and UiState is unstable, so
                // every state emission (poll/refresh tick/command) recomposes
                // EVERY in-composition page. beyondViewportPageCount=1 keeps 3
                // ExpandedCars in composition (current + 2 unreachable neighbours);
                // 0 keeps just the visible one → the per-emission recompose cost
                // (and the expand-entry burst under the fade/scale) drops ~3x.
                // Dots/settle read pure PagerState, so nothing visible changes.
                // If finger-swipe is ever re-enabled here, restore this to 1 — a
                // live swipe needs the neighbour pre-warmed (see collapsed pager
                // below for why).
                beyondViewportPageCount = 0,
                pageSize = androidx.compose.foundation.pager.PageSize.Fill,
                key = { page -> page },
            ) { page ->
                // Read the continuous pager offset ONLY inside graphicsLayer{}
                // below (draw-phase, never triggers recomposition) -- reading
                // it as a plain val in this composable scope used to subscribe
                // the WHOLE page composable (VehicleDetailContent, every pebble in
                // it) to recompose on literally every drag frame,
                // the real remaining cause of swipe jank after the blur/tilt
                // removal below. A secondary "snap bounce" spring driven off a
                // discretized settled/unsettled boolean used to multiply into
                // this too, on the theory that it'd add a subtle overshoot on
                // release -- in practice it lagged the scale/alpha response
                // behind the actual continuous drag position for the whole
                // gesture (the spring has to visibly catch up to "unsettled"
                // right as the drag starts), which is what made this pager's
                // swipe read as less smooth than the cover screen's equivalent
                // (CompactGarage), which never had that extra layer. Matching
                // it here: the raw continuous offset drives the transform
                // directly, no secondary spring in between.
                // No blur, no rotationZ tilt -- tried both a position-driven
                // and later a velocity-driven blur here, and the tilt on top
                // of the fade/scale, and all of it together read as worse
                // than the plain fade/scale alone. Just that now.
                // Flat, for the same reason as the garage pager below:
                // these pages are the same shadow-heavy pebble columns.
                Box(Modifier.fillMaxSize()) {
                    val pv = vehicles[exWrap.real(page)]
                    ExpandedCar(
                        pv,
                        state,
                        vm,
                        flipped = columnsFlipped,
                        onCollapse = { vm.collapse() },
                        hazeState = hazeState,
                        // A horizontal swipe on the hero card moves to the neighbouring
                        // car. The expanded pager itself has no finger swipe
                        // (userScrollEnabled = false below), so this programmatic page
                        // step is the one car-switch gesture the expanded view offers.
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
            // Always active, even mid-drag -- disabling it during a scroll (the
            // previous behaviour, a perf optimization) made it disappear while
            // swiping between cars, reported directly as wanting it to always be
            // there. Accepted tradeoff: a live drag now pays Haze's per-frame
            // recomposite cost the same as an idle frame does.
            StatusBarScrim(hazeState = hazeState)
            // Pager dots removed: user requested no page indicators at the top of the screen
        }
}

/** The cycling pager of cars (a status card when there are none) with Settings as the page after the last. */
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
        // Settings is appended as one more ITEM to the sequence this pager
        // cycles through (index `slots`, right after the last real page),
        // always rendered at exactly one car-column's width (see
        // `pageWidth` below) -- so on a wide/multi-car screen Settings is
        // never wider than one car's column, however many share the
        // screen with it, and SettingsScreen's own LazyColumn is a single
        // column at any width to begin with.
        //
        // ONE ITEM PER PAGE -- a car, the status card with none, or (index
        // `slots`) the folded-in Settings page -- exactly the model a phone
        // (perPage == 1) already used. On a wide/multi-car screen (perPage > 1) this is
        // now the ONLY model: `perPage` of these single-item pages are
        // simply narrow enough (see `pageWidth` below, applied via
        // `PageSize.Fixed`) to sit side by side in the viewport at once,
        // the same way a "peeking carousel" shows neighbours without
        // bundling them into one page.
        //
        // This replaces an earlier "sliding window" design that WAS
        // one-item-per-swipe in its real-index math, but still bundled
        // `perPage` items into one wide page under the hood -- so the
        // pager's own settle animation moved that whole bundle as a
        // single rigid unit, and a swipe from [car1,car2] to [car2,car3]
        // read as "the page changed" (both cars sliding together) rather
        // than "car1 left, car2 shifted left, car3 arrived" (each car an
        // independent, continuously-sliding thing) -- reported directly
        // as not feeling like the same seamless swipe a phone gets.
        // Giving each item its OWN page and just narrowing the page
        // instead is what makes HorizontalPager's native per-page drag/
        // fling physics apply per CAR again, matching the phone exactly,
        // instead of reimplementing "shift by one" on top of wide pages.
        // slots + 1, not count + 1: coerceIn(0, -1) below throws (min > max)
        // with zero cars, and there's still exactly one non-Settings page to
        // open on either way -- the status card (GarageStatusCard) takes the
        // one real-car slot that a zero-vehicle account would otherwise leave
        // empty. See `slots`' own doc above.
        val total = slots + 1
        // Always the car (or status card) currentIndex was already parked on:
        // this pager opens there, never on the Settings page at the end of it.
        val initialItem = currentIndex.coerceIn(0, slots - 1)
        // Infinite wrap-around: WrapPagerState.realCount is `total` --
        // one virtual page per real item, no window multiplier of any
        // kind -- and the real item for a page is realItem(page) itself.
        val wrap = rememberWrapPager(total, initialItem)
        val pager = wrap.pager
        fun realItem(virtualPage: Int) = wrap.real(virtualPage)
        // Keyed on total, not perPage: perPage only ever affected the old
        // window math (window width), which no longer exists -- the only
        // thing that can invalidate this effect's closure now is `total`
        // itself changing (a car added/removed).
        LaunchedEffect(pager, total, perPage) {
            snapshotFlow { pager.settledPage }.collect { page ->
                val real = realItem(page)
                // Guarded: settling on the Settings page (real == slots), or
                // the status card slot with zero cars, is not a car selection
                // -- currentIndex keeps whatever car it had, so swiping back
                // lands where you left off instead of snapping to car 0.
                if (real < count) vm.selectIndex(real)
                // See UiState.onSettingsPageSlot's own doc -- it's the one
                // "are we on Settings" signal now, there's no standalone route
                // left to also track.
                // Settings counts as "on screen" if it sits in ANY of the visible columns, not just the
                // first: on a multi-column layout the search bar expands whenever Settings is showing.
                vm.setOnSettingsPageSlot((0 until perPage.coerceAtLeast(1)).any { realItem(page + it) == slots })
                wrap.recenterIfNearEdge()
            }
        }
        // Resets the flag above the moment this pager itself leaves
        // composition (navigating away from the garage entirely) --
        // without it, closing Settings-as-embedded by navigating to some
        // OTHER screen (not a car, not standalone Settings) could leave
        // a stale `true` behind with nothing left to correct it, since
        // the collect{} above stops running once this composable is gone.
        DisposableEffect(Unit) { onDispose { vm.setOnSettingsPageSlot(false) } }
        // The above only pushes the pager's own settles into
        // currentIndex, never the other direction -- so an
        // external change (a shortcut tap selecting a specific car while
        // this pager was already composed on a different one) updated
        // currentIndex, but the pager itself just sat there on whatever
        // car it last settled on. A shortcut tap always means
        // "look at this car now," so jump (no animated fly-through
        // across a potentially large virtual-page delta) the instant
        // currentIndex moves out from under the page actually shown.
        //
        // Both this and the `total` effect below skip their own very
        // first firing (each with its own remember'd flag -- two
        // independent flags rather than one shared one, so there is no
        // ordering to get right between separate LaunchedEffects racing to
        // set it). LaunchedEffect always runs its body once on first
        // composition regardless of whether its key "changed" from
        // anything, and initialItem above has ALREADY seeded the correct
        // starting page -- so an unguarded first firing here did not
        // correct drift, it OVERWROTE that seed, unconditionally snapping
        // back to currentIndex the instant the pager mounted.
        val skipFirstIndexSnap = remember { mutableStateOf(true) }
        LaunchedEffect(currentIndex) {
            if (skipFirstIndexSnap.value) { skipFirstIndexSnap.value = false; return@LaunchedEffect }
            // Not on Garage any more (mid exit-transition to another screen,
            // Settings included): this composition's `state` param keeps
            // updating live even while AnimatedContent slides its ALREADY-
            // STALE content off screen, so without this guard a snap here
            // still visibly moves the pager underneath its own exit
            // animation -- see the `total` effect below, which skips
            // itself on the way out for the same reason.
            if (state.value.screen != Screen.Garage) return@LaunchedEffect
            wrap.snapToReal(currentIndex.coerceIn(0, slots - 1))
        }
        // A car being added or removed changes `total` -- and therefore
        // `wrap`'s realCount, the modulo divisor real() uses -- out from
        // under the pager's raw (unmoved) virtual position. That divisor
        // changing while the position doesn't is exactly what a "seam"
        // is: real(pager.currentPage) resolves to a DIFFERENT item than
        // the one on screen a moment ago, so the count changing could
        // silently reshuffle which car you land on. Same fix as the
        // currentIndex effect above and for the same reason: snap (not
        // fly-through) back to currentIndex.
        // Skips its own first firing too -- see the currentIndex effect's
        // comment just above for why. Also skips once this screen is on
        // its way out (same reason, same fix).
        val skipFirstTotalSnap = remember { mutableStateOf(true) }
        LaunchedEffect(total) {
            if (skipFirstTotalSnap.value) { skipFirstTotalSnap.value = false; return@LaunchedEffect }
            if (state.value.screen != Screen.Garage) return@LaunchedEffect
            wrap.snapToReal(currentIndex.coerceIn(0, slots - 1))
        }
        Box(Modifier.fillMaxSize()) {
            // The pixel width of ONE item's page -- viewport width divided
            // by perPage, so `perPage` of them sit side by side at rest.
            // onSizeChanged, not BoxWithConstraints: this Box's content
            // recomposes on every drag frame's worth of state (the pager
            // itself), and BoxWithConstraints is a SubcomposeLayout with a
            // real extra composition pass -- the same "measured size via a
            // plain layout callback instead" trade this codebase already
            // makes everywhere else performance-sensitive (CarMap's tile
            // box, PebbleShell's own row height).
            //
            // Seeded from windowWidthPx (the same source `widthDp`
            // above already uses, available synchronously on the very first frame),
            // NOT 0 -- a real device log caught what 0 actually does here: with
            // pageSize = PageSize.Fixed(0.dp), HorizontalPager doesn't just "render
            // nothing that first frame" as this comment used to claim -- it tries to
            // fill the viewport with zero-width pages, which never satisfies "enough
            // width composed yet", so it keeps composing pages until it runs out of
            // them: the entire virtual range (WRAP_MULTIPLIER x realCount, up to 160
            // pages), each one a full VehicleDetailContent/HeroVisual, alternating
            // real items by the wrap modulo -- exactly the "recompose #1, never
            // incrementing, alternating VINs" burst a real cold-start log showed,
            // and very plausibly the true source of this app's whole OOM history
            // (every one of those pages allocates real heap; onSizeChanged's first
            // real measurement only arrives and corrects pageWidth AFTER that burst
            // already ran). Seeding from the window's own size instead means pageWidth
            // is already correct (or within a sub-pixel inset difference, fixed the
            // instant onSizeChanged fires) on the very first frame -- `pageWidth`'s own
            // coerceAtLeast(1) below is what makes Fixed(0.dp) structurally unreachable
            // regardless, rather than resting solely on this seed being non-zero.
            var boxWidthPx by remember { mutableIntStateOf(windowWidthPx) }
            val density = LocalDensity.current
            // Ceiling division, not floor: `perPage` pages of a FLOORED width sum to
            // LESS than boxWidthPx (a leftover gap up to perPage-1 px at the right
            // edge), which forces the viewport to need a (perPage+1)-th page composed
            // to cover that gap during a scroll -- silently breaking the
            // beyondViewportPageCount formula below, which assumes
            // exactly perPage pages are ever on-screen at once. Rounding up instead
            // means perPage pages together are always >= boxWidthPx (they may overhang
            // the edge by a sub-pixel amount instead), which is what actually keeps
            // that assumption true.
            // coerceAtLeast(1), not just a better seed above: a zero (or degenerate)
            // width here is what actually lets PageSize.Fixed(0.dp) reach
            // HorizontalPager, which doesn't render nothing for that frame as this
            // comment used to claim -- with no width to satisfy "have I composed
            // enough to fill the viewport", it keeps composing pages until it runs
            // out, up to this pager's entire virtual range (see WrapPager.kt's
            // WRAP_MULTIPLIER doc), each one a full car page. Seeding boxWidthPx from
            // windowInfo above makes that statistically rare, not impossible -- this
            // floor is what makes it structurally unreachable regardless of what
            // seeds or later updates boxWidthPx.
            val pageWidth = with(density) { ((boxWidthPx + perPage - 1) / perPage).coerceAtLeast(1).toDp() }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    .onSizeChanged { boxWidthPx = it.width },
                userScrollEnabled = true,
                // ONE real item (a car, or Settings) per page, at a FIXED
                // width of exactly one car-column -- see this whole
                // section's own doc above for why this replaced a "wide
                // page holding perPage items" design. PageSize.Fill on a
                // single-car phone screen (perPage == 1, pageWidth ==
                // the whole viewport) and this Fixed width on a wide
                // screen are the SAME resulting page size; Fixed always
                // handles both uniformly rather than branching.
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed(pageWidth),
                // NOT a flat 1. This is the actual cause of a real, reported crash
                // (ArrayIndexOutOfBoundsException inside Compose's own
                // RememberEventDispatcher/MutableScatterSet, surfacing through a
                // LazyStaggeredGrid measure pass -- i.e. SettingsScreen's own grid).
                //
                // The wrap pager maps each VIRTUAL page to a real item cyclically
                // (real = page mod total), and `perPage` visible pages plus `beyond`
                // pre-warmed on EACH side means perPage + 2*beyond virtual pages are
                // composed AT ONCE. A contiguous run of consecutive integers mod
                // `total` only visits every residue at most once while its length is
                // <= total -- once it's longer, the pigeonhole principle guarantees
                // two different virtual pages resolve to the SAME real item, so the
                // SAME stateful composable (most often the folded-in SettingsScreen,
                // since it's the one item every car-count sequence cycles back to)
                // gets mounted TWICE at once, each with its own remember/BackHandler/
                // LazyVerticalStaggeredGrid state racing the other's -- exactly the
                // shape of corruption that crash is.
                //
                // A single-car account is the sharpest case: total = slots+1 = 2,
                // perPage is forced to 1 (coerceIn(1, slots) above), so a flat
                // beyond=1 composes 1+2*1 = 3 virtual pages cycling through only 2
                // real items -- guaranteed collision (the SAME real item, e.g. the
                // folded-in SettingsScreen, mounted twice at once, each with its own
                // racing remember/BackHandler/LazyVerticalStaggeredGrid state), every
                // single time that user opens the app. Solving `perPage + 2*beyond <=
                // total` for the largest safe integer beyond (capped at 1, since 1
                // pre-warmed neighbour is already all PebbleList's own lazy-fill needs
                // to hide, per this parameter's own history) gives the formula below.
                // NOT tied to the key below at all -- every page is keyed by its raw
                // index regardless of this value (see WrapPager.kt's own doc for why);
                // this beyond still exists purely to stop two DIFFERENT virtual pages
                // from ever resolving to the same real item while simultaneously
                // composed, independent of what key either of them is given.
                beyondViewportPageCount = ((total - perPage) / 2).coerceIn(0, 1),
                // Raw page index as the key, NEVER the real (modulo) item: keying by
                // real index made two virtual copies of one item share a composition
                // slot and crashed ("Key already used"). The raw page is unique by
                // construction. (beyondViewportPageCount above is what keeps two pages
                // from resolving to the same real item while composed, not the key.)
                key = { page -> page },
            ) { page ->
                // Same fade/scale transition the expanded single-car pager
                // above uses (see its own comment for why: the continuous
                // offset is read only inside graphicsLayer{} below, draw-phase
                // only). Applies identically now regardless of perPage, since
                // every page is exactly one item -- there is no more separate
                // "flat scroll on a wide screen" case.
                val real = realItem(page)
                Box(Modifier.fillMaxSize()) {
                    if (real == slots) {
                        // The folded-in Settings item, always the last one --
                        // it's a page in this pager, not a route, so its own
                        // LazyColumn is already a single column at this page's
                        // width (one car-column) regardless of how wide the
                        // screen sharing it is.
                        //
                        // Always composed here, the same as any other page in this
                        // pager (a car's VehicleDetailContent is never deferred either)
                        // -- this used to be gated behind onSettingsPageSlot/currentPage
                        // specifically to avoid paying for a full SettingsScreen build
                        // on the drag frame the FIRST time it's pre-warmed as a neighbour
                        // near cold start. That deferral traded one *off-screen*, one-time
                        // jank for a reproducible *on-screen* one: every real swipe onto
                        // Settings now showed a black void (just the app's own background)
                        // for as long as that same build took, however that build was
                        // gated -- moving the gate earlier (currentPage instead of
                        // settledPage) shrank the window but could not make the build
                        // itself faster, so the void was still directly reported. Building
                        // it as a pre-warmed neighbour instead means that cost is paid
                        // while the page isn't visible yet, exactly like every other page.
                        SettingsScreen(vm)
                    } else if (count == 0) {
                        // No cars at all: the status card takes this pager's
                        // one other slot instead of a car -- see
                        // GarageStatusCard's own doc (Guard.kt).
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
            // Always active, even during this pager's real finger-swipe drags --
            // disabling it mid-scroll (the previous behaviour, a perf optimization)
            // made it disappear while swiping between cars, reported directly as
            // wanting it to always be there.
            StatusBarScrim(hazeState = hazeState)
            // Pager dots removed: user requested no page indicators at the top of the screen
            // No global refresh indicator here for now -- a floating button standing in
            // for the multi-car grid's own perPage>1-only RefreshIndicatorBadge caused a
            // string of real, reported visual bugs (a stuck "blob" look from a busy-state
            // binding mismatch, then bleeding through underneath the expanded map's own
            // translucent top bar even after that was fixed) -- pulled out entirely rather
            // than keep patching a design that kept finding new ways to look broken. The
            // per-car pull-to-refresh (Refreshable, on each car's own page) is untouched.
            // No floating name badge here at all any more -- removed as unwanted UI.
        }
}
