package com.bloo.bluelink.ui

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
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.STALE_STATUS_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            vm.reportInfo("Tip: tune each car's seats, photo and pebble order in Settings")
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
    val pullFractionState = remember { mutableFloatStateOf(0f) }
    // Drives the expanded pager's programmatic car-switch (the hero card's own swipe --
    // see ExpandedCar's onSwipeCar).
    val garageScope = rememberCoroutineScope()
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
    val pulling by remember { derivedStateOf { pullFractionState.floatValue > 0.01f } }
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
    val large = widthDp >= TWO_COLUMN_MIN_DP.dp
    val chromeHidden = refreshing || pulling
    // SideEffect, not a bare assignment: these are snapshot writes, and writing state during
    // composition invalidates the composition that is running.
    //
    // The pull is published as a LAMBDA over the State, never as a value. Computing a Dp target
    // here meant reading pullFractionState in THIS composition -- and it changes on every pixel
    // of the gesture, so the garage, its pager and all three live car pages recomposed on every
    // drag frame to move some chrome. The modifier reads it in its offset lambda instead.
    SideEffect {
        floatingRegistry.chromePull = { pullFractionState.floatValue }
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
    val perPage = carColumnsFor(widthDp.value).coerceIn(1, slots)
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
                ExpandedGaragePage(vehicles, count, expandedIdx, state, vm, hazeState, appearance.columnsFlipped, garageScope)
            } else {
                CollapsedGaragePager(vehicles, count, slots, perPage, canExpand, currentIndex, windowInfo.containerSize.width, state, vm, hazeState)
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
                statusLine = mapStatusLine(state.value, expandedVehicle, appearance.metricDistance),
                deviceLocation = deviceLocation,
                mapState = expandedMap.mapStateFor(expandedVehicle.vin),
                hazeState = hazeState,
                onRefreshLocation = locateExpandedVehicle,
                deviceRequest = rememberDeviceLocationRequest(vm),
                // The real command-pending flag, not a guessed timer -- see
                // MapTopBar's own doc -- so the refresh icon's spin genuinely
                // tracks the in-flight fetch this same button just kicked off.
                refreshing = state.value.isPending(expandedVehicle.vin, "locate"),
                onDismiss = { expandedMap.vin = null },
            )
        }
    }
    }
}
