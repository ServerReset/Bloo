@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.STALE_STATUS_MS
import kotlinx.coroutines.delay

/**
 * Top-level garage screen: picks between three fundamentally different
 * layouts based on screen size/shape and dispatches to the right one, then
 * (for the "normal phone" case) owns the pager(s) that let the user swipe
 * between cars.
 *
 * Layout selection:
 *  - `compact` (a folding phone's small cover screen, see
 *    [isCompactCoverScreen]) short-circuits straight to [CompactGarage] and
 *    returns early -- none of the pager/expand logic below applies there.
 *  - `large` (wide enough for [perPage] > 1 car side by side) enables the
 *    dual/multi-column view and "expand one car to fill the screen" gesture.
 *  - Otherwise, the default single-column swipe-between-cars view.
 *
 * State plumbing specific to this screen:
 *  - `pullFractionState` plus the floating registry's chrome targets drive how the
 *    floating page-indicator dots and other overlays react live as the user
 *    pulls to refresh -- fading/sliding out of the way during the pull and
 *    springing back once it resolves -- rather than only reacting once
 *    `state.refreshing` flips.
 *  - The expanded ([HorizontalPager] over `exPager`) and collapsed
 *    (multi-car-per-page `pager`) pagers both use the "start in the middle
 *    of a huge virtual page range, map back to a real index with modulo"
 *    trick to fake infinite wrap-around swiping in both directions.
 *  - A `LaunchedEffect(currentVehicle?.vin, currentFetchedAt)` watches for
 *    stale data and only warns the user if a fresh background refresh
 *    doesn't land within 25s (see the inline comment below for why the
 *    delay is cancellable).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun GarageScreen(
    state: State<UiState>,
    vm: AppViewModel,
    /** Defaults to a fresh one for every existing caller's exact prior behavior.
     *  Screens.kt passes its own shared instance instead, so the floating search
     *  bar/results panel it hosts ABOVE this screen (see SearchLayer's own doc for
     *  why it lives there) can mark ITS glass fill as a real blur of whichever car
     *  page is actually showing, instead of a flat tint with nothing to blur. */
    hazeState: HazeState = remember { HazeState() },
) {
    // DERIVED reads, not body reads of `state.value`.
    //
    // A bare `state.value.<field>` in this body subscribes the whole garage -- the pager, its
    // item lambda, every live car page -- to EVERY UiState emission: a status poll for one car,
    // a weather refresh, a device-location tick, any command anywhere. The values here change
    // far less often than the state object does, and a derived state only invalidates its
    // reader when the value it exposes actually changes, so unrelated emissions stop reaching
    // the pager entirely.
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    val expandedIndex by remember { derivedStateOf { state.value.expandedIndex } }
    val deviceLocation by remember { derivedStateOf { state.value.deviceLocation } }
    val chargersVisible by remember { derivedStateOf { state.value.chargersVisible } }
    val chargersLoading by remember { derivedStateOf { state.value.chargersLoading } }
    val chargersError by remember { derivedStateOf { state.value.chargersError } }
    val chargers by remember { derivedStateOf { state.value.chargers } }
    val chargerFilters by remember { derivedStateOf { state.value.chargerFilters } }
    val showSettingsHint by remember { derivedStateOf { state.value.showSettingsHint } }
    // No more early return on an empty garage: a zero-vehicle account is now
    // just another state of this SAME screen (see `slots`/GarageStatusCard
    // below), not a separate standalone Screen.Empty route -- reported
    // directly as wanting it to "just be another card like the rest of them",
    // swipeable to Settings rather than a whole different screen with its own
    // header/back-navigation chrome.
    val appearance = LocalAppearance.current

    // Collected here rather than read off UiState: the pager's position is its
    // own flow now precisely so that finishing a swipe does not invalidate the
    // car pages. Reading it in THIS composable is fine and intended -- this is
    // one of the few places that genuinely needs it, and it is above the pages.
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    // lastIndex.coerceAtLeast(0): lastIndex is -1 on an empty list, and
    // coerceIn(0, -1) throws (min > max) before getOrNull ever gets a chance
    // to just return null for it.
    val currentVehicle = vehicles.getOrNull(currentIndex.coerceIn(0, vehicles.lastIndex.coerceAtLeast(0)))
    val currentFetchedAt by remember(currentVehicle?.vin) {
        derivedStateOf { currentVehicle?.let { state.value.fetchedAt(it) } }
    }
    val sessionStartMs = remember { System.currentTimeMillis() }
    LaunchedEffect(currentVehicle?.vin, currentFetchedAt) {
        val fetchedAt = currentFetchedAt
        if (fetchedAt != null &&
            fetchedAt < sessionStartMs &&
            System.currentTimeMillis() - fetchedAt > STALE_STATUS_MS) {
            // Give the automatic background fetch time to land. If it returns fresh
            // data, currentFetchedAt changes → this effect restarts → delay is
            // cancelled → user never sees a spurious "stale" toast.
            delay(25_000)
            vm.reportInfo("Data is over 15 min old. Pull down to refresh")
        }
    }

    // Gentle one-time nudge after onboarding, encouraging a Settings visit.
    LaunchedEffect(showSettingsHint) {
        if (showSettingsHint) {
            vm.reportInfo("Tip: fine-tune each car's seats, photo and pebble order in Settings")
            vm.dismissSettingsHint()
        }
    }

    // Settle haptic when a refresh lands.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (wasRefreshing && !refreshing) haptics?.slotSettle()
        wasRefreshing = refreshing
    }
    // Live pull distance reported by Refreshable, so the overlays react the moment
    // the user starts pulling - not only once a refresh is in flight.
    val pullFractionState = remember { mutableStateOf(0f) }
    // Hide the floating chrome as soon as the pull begins (and through the refresh),
    // so the squiggly indicator has the stage to itself; fade it back in when done.
    // NOT read via `by` here: this is GarageScreen scope, the car pager's parent.
    // A composition-scope read meant all ~12 frames of this 200ms fade recomposed
    // GarageScreen and, through it, every live pager page. Held as State and read
    // inside graphicsLayer{} at the use sites instead, so the fade is draw-phase
    // only and never invalidates composition.
    // Narrowed to the boolean flip rather than reading the continuous fraction
    // directly: pullFractionState changes on every pixel of a pull gesture, and a
    // composition-scope read of it here would recompose GarageScreen (the car
    // pager's parent) on every one of those pixels, which is expensive. Use boolean
    // derivedStateOf instead for a stable result that only changes at threshold.
    val pulling by remember { derivedStateOf { pullFractionState.value > 0.01f } }
    // Published to the floating registry instead of animated here. The fade and the pull shift
    // are behaviours of floating CHROME, not of the dots or the corner buttons individually --
    // holding them per-site is what let them disagree (dots faded but never shifted; the corner
    // icons shifted but never faded). Modifier.floatingOverlay owns both springs now, so this
    // screen publishes targets and never recomposes on their frames.
    val floatingRegistry = LocalFloatingRegistry.current
    // Slide the floating overlays (dots, settings, back/flip) down: in real time as
    // the user pulls, then settle/spring back up once the refresh completes.
    // overlayShiftTarget genuinely needs the continuous fraction (the shift is
    // proportional to how far the user has pulled, not just on/off), so this read
    // can't be narrowed the same way -- it recomposes GarageScreen during an
    // active pull, same as before. What CAN be (and is, below) fixed is the
    // spring's OWN settling frames: `refreshShift` used to be read via `by`,
    // which meant every one of the ~12 frames it takes to spring back up also
    // recomposed GarageScreen, for a value only ever consumed inside an
    // offset { } at its two use sites.
    val count = vehicles.size
    // At least one non-Settings page even with zero cars: the "no connection"/
    // "not signed in"/"no vehicles" status card (GarageStatusCard, Guard.kt)
    // takes that one slot instead of a car, so Settings is still just one
    // swipe away rather than a whole separate unreachable-by-swipe screen.
    // Everywhere below that used to divide/coerce against `count` for pager
    // math (which throws or misbehaves at zero -- see each use site's own
    // comment) uses this instead; `count` itself stays the real vehicle
    // count for anything that indexes into `vehicles`.
    val slots = maxOf(count, 1)
    val windowInfo = LocalWindowInfo.current
    val widthDp = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }
    val large = widthDp >= COVER_SCREEN_WIDTH_DP.dp
    val compact = isCompactCoverScreen()
    // Only show cover-screen hints once per session.
    var coverHintShown by rememberSaveable { mutableStateOf(false) }
    // Detect a device that likely has a cover screen: look for a camera cutout
    // (punch-hole) on a short screen, indicating a flip/fold cover display.
    val view = LocalView.current
    val hasCameraCutout = remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            view.rootWindowInsets?.displayCutout?.boundingRects?.isNotEmpty() == true
        else false
    }
    // `compact` is part of the CONDITION, not just of the message choice. It used
    // to set coverHintShown before testing it, so the once-per-session latch was
    // spent by any device with a punch-hole -- which is essentially every modern
    // phone -- while unfolded, showing nothing. Fold/unfold is a configuration
    // change, and coverHintShown is rememberSaveable precisely to survive one, so
    // a user who opened the app unfolded and then closed the phone reached the
    // cover screen with the hint already marked shown and never saw it: the hint
    // was reliably consumed everywhere except the one screen it exists for.
    LaunchedEffect(compact, hasCameraCutout) {
        if (compact && hasCameraCutout && !coverHintShown) {
            coverHintShown = true
            vm.reportInfo("Open your phone for the full Bloo experience")
        }
    }
    if (compact) {
        // Returns BEFORE publishing the chrome targets below. CompactGarage is a child of this
        // composable and publishes its own, so leaving this screen's SideEffect above the
        // short-circuit meant two writers on one shared pair of fields in a single composition.
        // They agreed only by accident -- the compact path returns before LocalPullFraction is
        // provided, so `pulling` was always false here and both reduced to `refreshing`. One
        // change to either expression and it becomes last-writer-wins flicker.
        CompactGarage(state, vm, appearance, hazeState = hazeState)
        return
    }
    val chromeHidden = refreshing || pulling
    // SideEffect, not a bare assignment: these are snapshot writes, and writing state during
    // composition invalidates the composition that is running.
    //
    // The pull is published as a LAMBDA over the State, never as a value. Computing a Dp target
    // here meant reading pullFractionState in THIS composition -- and it changes on every pixel
    // of the gesture, so the garage, its pager and all three live car pages recomposed on every
    // drag frame to move some chrome. The modifier reads it in its offset lambda instead.
    SideEffect {
        floatingRegistry.chromePull = { pullFractionState.value }
        // Two separate flags on purpose -- see chromeHolding's own doc. The HOLD is only while
        // a refresh is in flight; the FADE covers the pull as well.
        floatingRegistry.chromeHolding = refreshing
        floatingRegistry.chromeHidden = chromeHidden
    }
    // Cleared when this screen goes away. Nothing else resets these, so leaving mid-pull or
    // mid-refresh -- opening Settings, locking the phone -- left the registry asserting
    // "hidden, shifted 96dp" for as long as no GarageScreen was around to say otherwise. The
    // outgoing screen's own overlays are still composed during the crossfade, so they held that
    // offset and alpha 0 all the way through the transition.
    DisposableEffect(floatingRegistry) {
        onDispose { floatingRegistry.resetChrome() }
    }
    // hazeState is now a parameter (see this function's own doc) -- backs both
    // StatusBarScrim calls below (expanded and collapsed pager alike -- only one is
    // ever composed at a time, so one shared instance is enough) with a REAL
    // backdrop blur: Modifier.hazeSource on whichever pager is actually visible
    // marks it as the content to blur, StatusBarScrim's own hazeState param reads it
    // back. See StatusBarScrim's doc for why plain Modifier.blur never worked here.
    // Backs every Location pebble's map-expand button on this screen -- see its own
    // doc for why a shared, hoisted instance (not local state inside the pebble
    // itself) is what lets the expanded view be "literally that same" map/component
    // rather than a copy fading in. One instance for the whole screen: only one
    // car's map can be expanded at a time regardless of which page it's on.
    val expandedMap = remember { ExpandedMapState() }
    // Mirrors expandedMap.vin into shared UiState -- see UiState.mapExpanded's own doc --
    // so Screens.kt (a sibling of this screen, not a descendant, and so unable to read
    // expandedMap/LocalExpandedMap directly) can hide the floating search bubble while
    // the map sheet covers the same corner its own bottom action row occupies. Reset on
    // dispose for the same reason setOnSettingsPageSlot's matching effect is: leaving a
    // stale `true` behind with nothing left to correct it once this screen itself goes
    // away would otherwise hide search permanently.
    LaunchedEffect(expandedMap.vin) { vm.setMapExpanded(expandedMap.vin != null) }
    DisposableEffect(Unit) { onDispose { vm.setMapExpanded(false) } }
    // How many full-height cards fit side by side; pages advance by this many.
    // `slots`, not `count`: coerceIn(1, 0) throws (min > max) with zero cars,
    // and there is exactly one non-Settings page to show anyway in that case
    // (the status card), so multi-column grid mode never applies to it.
    //
    // Derived from `widthDp` (LocalWindowInfo.containerSize), NOT the collapsed pager's
    // own measured `boxWidthPx` (see that Box's own `pageWidth`, below): widthDp is available
    // synchronously from the very first frame, while boxWidthPx starts at 0 and only catches
    // up once that Box's first onSizeChanged fires a layout pass later. This briefly WAS
    // sourced from boxWidthPx instead, to fix a real "Key already used" pager crash caused by
    // perPage/pageWidth disagreeing for one frame after a rotation -- but the pager below no
    // longer keys pages by real index at all (key = { page -> page }, unconditionally), so
    // that mismatch can no longer cause a crash regardless of which value perPage comes from.
    // Sourcing it from
    // boxWidthPx anyway meant every cold start on a wide/multi-column screen (a large
    // foldable, unfolded) rendered ONE frame as a single column before reflowing into its
    // real column count the instant boxWidthPx caught up -- a visible hitch on every single
    // launch, reported directly. widthDp being correct from frame one is what avoids it.
    val perPage = (widthDp / MIN_CARD_DP.dp).toInt().coerceIn(1, slots)
    // Expanding to the dual-column view only makes sense on a wide screen.
    val canExpand = large && count > 1
    // Expanded means exactly one thing now: the user tapped the fullscreen icon
    // on a car. There is deliberately no automatic "a lone car on a wide screen
    // fills the screen by itself" case -- that shortcut bypassed the block pager
    // below, and with Settings folded into that pager as its own page it would
    // have made Settings unreachable by swipe for exactly that one combination
    // (a single car on a wide screen). A lone car renders through the same
    // block/window pager as any other count instead.
    val expandedIdx = expandedIndex?.takeIf { it in vehicles.indices && canExpand }

    BackHandler(enabled = expandedIdx != null) { vm.collapse() }

    CompositionLocalProvider(LocalPullFraction provides pullFractionState, LocalExpandedMap provides expandedMap) {
    BackdropHost {
        AnimatedContent(
            targetState = expandedIdx != null,
            transitionSpec = {
                val spec = spring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                (fadeIn(spec) + scaleIn(spec, initialScale = 0.94f)) togetherWith
                    (fadeOut(spec) + scaleOut(spec, targetScale = 0.94f))
            },
            label = "expand",
        ) { isExpanded ->
            if (isExpanded) {
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
                                flipped = appearance.columnsFlipped,
                                // Wired unconditionally per page. Pager dots (which previously
                                // needed per-page notification) were removed, but the structure
                                // remains for consistency. Only one page is ever actually
                                // composed here (beyondViewportPageCount = 0) anyway.
                                onCollapse = { vm.collapse() },
                                hazeState = hazeState,
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
            } else {
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
                LaunchedEffect(pager, total) {
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
                        vm.setOnSettingsPageSlot(real == slots)
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
                    // Seeded from windowInfo.containerSize.width (the same source `widthDp`
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
                    var boxWidthPx by remember { mutableIntStateOf(windowInfo.containerSize.width) }
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
                                    // Only hide the per-car pull indicator in the
                                    // multi-car grid (perPage > 1) -- state.refreshing
                                    // is one app-wide flag, not per-car, so leaving
                                    // it unhidden would light up every visible car's
                                    // spinner for a refresh that only touched one.
                                    hideIndicator = perPage > 1,
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
                    // Grid mode (perPage > 1, wide/large screens) hides each
                    // card's own pull-to-refresh indicator above -- state.value.refreshing
                    // is one app-wide flag, not per-car, so leaving them unhidden
                    // would light up every visible card's spinner for a refresh
                    // that only touched one of them. But that left a real gap:
                    // count <= perPage (every car already fits on one page, common
                    // on tablets) meant pulling to refresh in the grid had *zero*
                    // visual feedback of any kind. RefreshIndicatorBadge (Pebbles.kt)
                    // -- the one shared refresh badge every pull-to-refresh surface in
                    // the app now uses -- covers every grid case. There's no drag
                    // gesture to follow here, so its progress just animates 0->1 off
                    // the plain `refreshing` boolean instead of a live pull distance.
                    if (perPage > 1) {
                        val gridRefreshProgress by animateFloatAsState(
                            targetValue = if (refreshing) 1f else 0f,
                            animationSpec = tween(if (refreshing) 150 else 200),
                            label = "gridRefreshProgress",
                        )
                        RefreshIndicatorBadge(
                            hazeState = hazeState,
                            // fade = false: this is the one piece of chrome that must stay
                            // visible exactly when the rest of it fades out -- it IS the refresh.
                            modifier = Modifier.align(Alignment.TopCenter)
                                .floatingOverlay(FloatingIds.RefreshIndicator, fade = false),
                        ) { gridRefreshProgress }
                    }
                    // No floating name badge here at all any more -- removed as unwanted UI.
                }
            }
        }
        // The floating "Back to all cars" and "Flip columns" icons that used to live here
        // are gone: back is now the hero card's own header action (see HeroHeader's
        // expandAction, wired via ExpandedCar's onCollapse above), and flipping columns is
        // now a swipe gesture on ExpandedCar itself rather than a button anyone had to find.
        // THE SINGLE CarMap instance, positioned at the screen level.
        // It animates from the pebble location (collapsed) to full-screen (expanded).
        // Same instance, literally growing -- not two separate maps or morphing.
        val expandedVin = expandedMap.vin
        val expandedVehicle = if (expandedVin != null) vehicles.firstOrNull { it.vin == expandedVin } else null
        val expandedLocation = expandedVehicle?.let { state.value.locations[it.vin] }
        val originBounds = expandedVehicle?.vin?.let { expandedMap.originBoundsFor(it).value }

        if (expandedVehicle != null && expandedLocation != null && originBounds != null) {
            // rememberLocateAction, not a bare vm.locate(expandedVehicle) call -- see its own
            // doc for why: this exact call site used to skip the ACCESS_FINE_LOCATION request
            // entirely, so anyone who only ever used the expanded map's own refresh icon (never
            // the compact pebble's matching button) could never grant it and never saw the
            // device's own location dot, reported directly a second time against this screen.
            val locateExpandedVehicle = rememberLocateAction(vm, expandedVehicle)
            ExpandableMapLayer(
                isExpanded = true,
                originBounds = originBounds,
                location = expandedLocation,
                vehicleName = expandedVehicle.name,
                deviceLocation = deviceLocation,
                mapState = expandedMap.mapStateFor(expandedVehicle.vin),
                hazeState = hazeState,
                onRefreshLocation = locateExpandedVehicle,
                // The real command-pending flag, not a guessed timer -- see
                // MapTopBar's own doc -- so the refresh icon's spin genuinely
                // tracks the in-flight fetch this same button just kicked off.
                refreshing = state.value.isPending(expandedVehicle.vin, "locate"),
                onDismiss = { expandedMap.vin = null },
                chargersVisible = chargersVisible,
                chargersLoading = chargersLoading,
                chargersError = chargersError,
                chargers = chargers,
                chargerFilters = chargerFilters,
                onToggleChargersVisible = { vm.toggleChargersVisible(expandedLocation) },
                onRetryChargers = { vm.loadNearbyChargers(expandedLocation) },
                onSetChargerMinKw = { vm.setChargerMinKw(it) },
                onToggleChargerNetwork = { vm.toggleChargerNetwork(it) },
                onSetChargerApiKey = { vm.setChargerApiKey(it, expandedLocation) },
            )
        }
    }
    }
}
