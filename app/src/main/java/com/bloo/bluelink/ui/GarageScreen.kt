@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.STALE_STATUS_MS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.bloo.uicommon.LocalReorderActive

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
internal fun GarageScreen(state: State<UiState>, vm: AppViewModel) {
    val vehicles = state.value.vehicles
    if (vehicles.isEmpty()) return
    val appearance = LocalAppearance.current

    // Collected here rather than read off UiState: the pager's position is its
    // own flow now precisely so that finishing a swipe does not invalidate the
    // car pages. Reading it in THIS composable is fine and intended -- this is
    // one of the few places that genuinely needs it, and it is above the pages.
    val currentIndex by vm.currentIndex.collectAsState()
    val currentVehicle = vehicles.getOrNull(currentIndex.coerceIn(0, vehicles.lastIndex))
    val currentFetchedAt = currentVehicle?.let { state.value.fetchedAt(it) }
    val sessionStartMs = remember { System.currentTimeMillis() }
    LaunchedEffect(currentVehicle?.vin, currentFetchedAt) {
        if (currentFetchedAt != null &&
            currentFetchedAt < sessionStartMs &&
            System.currentTimeMillis() - currentFetchedAt > STALE_STATUS_MS) {
            // Give the automatic background fetch time to land. If it returns fresh
            // data, currentFetchedAt changes → this effect restarts → delay is
            // cancelled → user never sees a spurious "stale" toast.
            delay(25_000)
            vm.reportInfo("Data is over 15 min old. Pull down to refresh")
        }
    }

    // Gentle one-time nudge after onboarding, encouraging a Settings visit.
    LaunchedEffect(state.value.showSettingsHint) {
        if (state.value.showSettingsHint) {
            vm.reportInfo("Tip: fine-tune each car's seats, photo and pebble order in Settings")
            vm.dismissSettingsHint()
        }
    }

    // Settle haptic when a refresh lands.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.value.refreshing) {
        if (wasRefreshing && !state.value.refreshing) haptics?.slotSettle()
        wasRefreshing = state.value.refreshing
    }
    // Live pull distance reported by Refreshable, so the overlays react the moment
    // the user starts pulling - not only once a refresh is in flight.
    val pullFractionState = remember { mutableStateOf(0f) }
    // Hide the page indicator as soon as the pull begins (and through the refresh),
    // so the squiggly indicator has the stage to itself; fade it back in when done.
    // NOT read via `by` here: this is GarageScreen scope, the car pager's parent.
    // A composition-scope read meant all ~12 frames of this 200ms fade recomposed
    // GarageScreen and, through it, every live pager page. Held as State and read
    // inside graphicsLayer{} at the use sites instead, so the fade is draw-phase
    // only and never invalidates composition.
    // Narrowed to the boolean flip rather than reading the continuous fraction
    // directly: pullFractionState changes on every pixel of a pull gesture, and a
    // composition-scope read of it here would recompose GarageScreen (the car
    // pager's parent -- see PagerDotsFor's doc comment for why that's expensive)
    // on every one of those pixels, for a target value that's already saturated
    // the moment the pull passes 1%.
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
    val cfg = LocalConfiguration.current
    val widthDp = cfg.screenWidthDp
    val large = widthDp >= COVER_SCREEN_WIDTH_DP
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
    //
    // The `vehicles.isEmpty()` variant that used to pick a "setup experience"
    // wording is gone with it -- GarageScreen returns on the first line when
    // vehicles is empty, so that branch was unreachable and the string it chose
    // could never appear.
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
        CompactGarage(state.value, vm, appearance)
        return
    }
    val chromeHidden = state.value.refreshing || pulling
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
        floatingRegistry.chromeHolding = state.value.refreshing
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
    // How many full-height cards fit side by side; pages advance by this many.
    val perPage = (widthDp / MIN_CARD_DP).coerceIn(1, count)
    // Expanding to the dual-column view only makes sense on a wide screen.
    val canExpand = large && count > 1
    val singleLarge = large && count == 1
    // A car expanded by the user (multi-car), or the lone car on a big screen.
    val expandedByUser = state.value.expandedIndex?.takeIf { it in vehicles.indices && canExpand }
    val expandedIdx = if (singleLarge) 0 else expandedByUser

    BackHandler(enabled = expandedByUser != null) { vm.collapse() }

    CompositionLocalProvider(LocalPullFraction provides pullFractionState) {
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
                    snapshotFlow { exPager.settledPage }.collect { vm.expand(exWrap.real(it)) }
                }
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = exPager,
                        modifier = Modifier.fillMaxSize(),
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
                    ) { page ->
                        // Read the continuous pager offset ONLY inside graphicsLayer{}
                        // below (draw-phase, never triggers recomposition) -- reading
                        // it as a plain val in this composable scope used to subscribe
                        // the WHOLE page composable (CarThemeOverride, VehicleDetailContent,
                        // every pebble in it) to recompose on literally every drag frame,
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
                            CarThemeOverride(
                                paletteId = appearance.carCustomPaletteIds[pv.vin],
                                customPalettes = appearance.customPalettes,
                                themeMode = appearance.themeMode,
                                vibrancy = appearance.vibrancy,
                            ) {
                                ExpandedCar(
                                    pv,
                                    state,
                                    vm,
                                    flipped = appearance.columnsFlipped,
                                    // Feeds this same PagerDotsFor's collision dodge
                                    // below. Wired unconditionally per page rather than
                                    // gated to "only the settled page" -- doing that
                                    // gate here would mean reading exPager.currentPage
                                    // in this scope, which is exactly the per-frame,
                                    // whole-pager-invalidating read this file's own
                                    // PagerDotsFor doc above warns against. Harmless
                                    // either way: only one page is ever actually
                                    // composed here (beyondViewportPageCount = 0), so
                                    // there's no simultaneous writer to race against.
                                )
                            }
                        }
                    }
                    StatusBarScrim()
                    if (count > 1 && !LocalReorderActive.current) {
                        PagerDotsFor(
                            pager = exPager,
                            real = { exWrap.real(it) },
                            count = count,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = HeaderCornerGap)
                                .floatingOverlay(FloatingIds.PagerDots),
                            // Published by the modifier above -- the node that carries the inset and the
                            // refresh shift -- so the dots must not also publish under the same id.
                            registerBounds = false,
                            onRefresh = { vm.refreshStatus(vehicles[exWrap.settledReal]) },
                        )
                    }
                }
            } else {
                val pageCount = (count + perPage - 1) / perPage
                // One extra real "block" tacked onto the end for Settings, when the
                // user has opted into reaching it by swiping instead of the gear
                // button (Appearance.settingsAsPage) -- WrapPagerState.realCount is
                // already just "how many real things this cycles through," so it
                // costs nothing else here to hand it one more than the car-block
                // count and treat that extra index specially below and in the page
                // renderer. block == pageCount (never a valid car block index, which
                // only ever run 0 until pageCount-1) is what marks it as the
                // Settings slot rather than a car.
                // appearance.settingsAsPage || state.value.landOnSettingsPage, not
                // appearance.settingsAsPage alone: the preference write behind that
                // flag goes through DataStore, and DataStore is genuinely async --
                // the frame right after closeSettings(landOnSettingsPage = true)
                // (itself a synchronous UiState update) can still read the OLD
                // value here for a beat, before the write finishes its round trip
                // back through the Flow. Without the OR, totalBlocks below would
                // stay at the OLD (no-Settings-slot) count on that first frame,
                // making initialBlock's `pageCount` seed a few lines down point at
                // a block that doesn't exist yet -- landOnSettingsPage is
                // unambiguous proof the slot is about to exist regardless of
                // which frame the DataStore write actually lands on.
                val settingsAsPage = appearance.settingsAsPage || state.value.landOnSettingsPage
                val totalBlocks = if (settingsAsPage) pageCount + 1 else pageCount
                // Normally the car currentIndex was already parked on. The one
                // exception is state.value.landOnSettingsPage (see its own doc): Settings
                // itself just switched settingsAsPage on and asked to be followed,
                // so this fresh mount seeds straight onto the just-created Settings
                // slot instead of whichever car was selected before Settings was
                // ever opened -- otherwise the user would land on a car for one
                // frame before having to go find the page themselves.
                val initialBlock = if (state.value.landOnSettingsPage && settingsAsPage) {
                    pageCount
                } else {
                    (currentIndex.coerceIn(0, count - 1)) / perPage
                }
                // Infinite wrap-around: WrapPagerState.realCount is the BLOCK count
                // here (ceil(count / perPage), plus the Settings slot if enabled), and
                // the real vehicle index for a page is realBlock(page) * perPage.
                val wrap = rememberWrapPager(totalBlocks, initialBlock)
                val pager = wrap.pager
                fun realBlock(virtualPage: Int) = wrap.real(virtualPage)
                // Authoritative, not just a fire-and-hope seed: initialBlock above
                // already gets this right on the fast path (a genuinely fresh mount,
                // which returning from Screen.Settings normally is), but that value
                // is only ever honoured the very FIRST time rememberWrapPager builds
                // its underlying pager for this composable instance -- if anything
                // about Compose's own state retention across the AnimatedContent
                // screen transition ever meant this pager wasn't quite as fresh as
                // assumed, a seed-only fix would silently do nothing and Settings
                // would look like it was never actually followed. This actively
                // MOVES the pager there instead of hoping the seed took, which costs
                // nothing extra in the common case (wrap.snapToReal no-ops when
                // already there) and is the actual fix in the uncommon one.
                LaunchedEffect(state.value.landOnSettingsPage) {
                    if (state.value.landOnSettingsPage) {
                        if (settingsAsPage) wrap.snapToReal(pageCount)
                        vm.consumeLandOnSettingsPage()
                    }
                }
                // Keyed on totalBlocks too, not just pager/perPage: this effect's
                // own collect{} closes over realBlock/pageCount/settingsAsPage as
                // they were the moment it (re)started. Toggling Appearance.
                // settingsAsPage from a search result while GarageScreen stays
                // mounted the whole time (see that toggle's own comment -- it
                // deliberately doesn't require a fresh mount) changes totalBlocks
                // without touching pager's identity or perPage, so without this
                // key the running coroutine kept using a stale, pre-toggle
                // settingsAsPage (permanently false, so onSettingsPageSlot could
                // never become true and the search bubble never morphed to a
                // pill) AND a stale pageCount/realBlock pairing that no longer
                // matched the pager's own (live) virtual page count -- a
                // mismatched modulus that could resolve `block` to an unrelated
                // number and fire selectIndex with a bogus index. Restarting here
                // rebinds the closure to the current values the instant the slot
                // count changes.
                LaunchedEffect(pager, perPage, totalBlocks) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        // Guarded: the Settings slot isn't a car block, and
                        // selectIndex/currentIndex only ever mean "which car" --
                        // settling there should leave whatever car was last
                        // selected exactly as it was, so swiping back to a car
                        // lands where you left it instead of snapping to car 0.
                        val block = realBlock(page)
                        if (block < pageCount) vm.selectIndex((block * perPage).coerceIn(0, count - 1))
                        // See UiState.onSettingsPageSlot's own doc -- this is what
                        // lets SearchLayer's floating bubble/pill morph track the
                        // embedded Settings page the same way it already tracks
                        // the standalone route, instead of staying a garage
                        // "bubble" the whole time it's on screen.
                        vm.setOnSettingsPageSlot(settingsAsPage && block == pageCount)
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
                // external change (a widget/shortcut tap selecting a specific
                // car while this pager was already composed on a different
                // one) updated currentIndex, and the floating name pill below
                // read it correctly, but the pager itself just sat there on
                // whatever car it last settled on. A widget tap always means
                // "look at this car now," so jump (no animated fly-through
                // across a potentially large virtual-page delta) the instant
                // currentIndex moves out from under the page actually shown.
                //
                // Both this and the totalBlocks effect below skip their own very
                // first firing (each with its own remember'd flag -- two
                // independent flags rather than one shared one, so there is no
                // ordering to get right between separate LaunchedEffects racing to
                // set it). LaunchedEffect always runs its body once on first
                // composition regardless of whether its key "changed" from
                // anything, and initialBlock above has ALREADY seeded the correct
                // starting page for every case, landOnSettingsPage included -- so
                // an unguarded first firing here did not correct drift, it
                // OVERWROTE that seed, unconditionally snapping back to
                // currentIndex's own block the instant the pager mounted. That
                // silently defeated landOnSettingsPage every time (Settings looked
                // like it never actually got followed) and, worse, chained into
                // the settle-observer above calling selectIndex for that block --
                // which on a multi-car-per-page grid is not always literally
                // currentIndex when the two don't share a block boundary, so the
                // "correction" could self-report as a genuine, uninitiated car
                // change on the very frame the screen appeared.
                val skipFirstIndexSnap = remember { mutableStateOf(true) }
                LaunchedEffect(currentIndex) {
                    if (skipFirstIndexSnap.value) { skipFirstIndexSnap.value = false; return@LaunchedEffect }
                    // Not on Garage any more (mid exit-transition to another screen,
                    // Settings included): this composition's `state` param keeps
                    // updating live even while AnimatedContent slides its ALREADY-
                    // STALE content off screen, so without this guard a snap here
                    // still visibly moves the pager underneath its own exit
                    // animation -- see the totalBlocks effect below for the exact
                    // trigger (settingsAsPage flipping mid-exit) and why it read as
                    // jank rather than a clean transition.
                    if (state.value.screen != Screen.Garage) return@LaunchedEffect
                    val targetBlock = currentIndex.coerceIn(0, count - 1) / perPage
                    wrap.snapToReal(targetBlock)
                }
                // Toggling Appearance.settingsAsPage changes totalBlocks -- and
                // therefore `wrap`'s realCount, the modulo divisor real() uses --
                // out from under the pager's raw (unmoved) virtual position. That
                // divisor changing while the position doesn't is exactly what a
                // "seam" is: real(pager.currentPage) resolves to a DIFFERENT block
                // than the one on screen a moment ago, so flipping the switch
                // could silently reshuffle which car you land on, or -- toggling
                // off while parked on the Settings slot itself, which no longer
                // exists under the new count -- strand the pager on an arbitrary
                // block instead of the last real car you were actually on. Same
                // fix as the currentIndex effect above and for the same reason:
                // snap (not fly-through) back to the block currentIndex actually
                // means, which is exactly "stay on the same car" when a car was
                // showing, and "return to the last car you had" when Settings was.
                // Skips its own first firing too -- see the currentIndex effect's
                // comment just above for why. Also skips once this screen is on
                // its way out (same reason, same fix): toggling the switch OFF
                // from the embedded page calls vm.openSettings() immediately, which
                // starts the OUTER Garage -> Settings slide the instant it runs --
                // but appearance.settingsAsPage's own DataStore write can still
                // land a beat or two INTO that slide, while this now-exiting
                // composition is still live and still reacting to real state
                // changes. totalBlocks changing at that exact moment used to fire
                // this effect and snap the pager to a different page while its
                // (already stale, already animating off screen) content was
                // visibly sliding away -- the reported "janky" transition.
                val skipFirstBlocksSnap = remember { mutableStateOf(true) }
                LaunchedEffect(totalBlocks) {
                    if (skipFirstBlocksSnap.value) { skipFirstBlocksSnap.value = false; return@LaunchedEffect }
                    if (state.value.screen != Screen.Garage) return@LaunchedEffect
                    // Toggling settingsAsPage ON from the standalone route
                    // (SettingsScreen's own toggle) changes totalBlocks AND
                    // sets state.value.landOnSettingsPage in the very same
                    // transition -- this effect exists to snap back to
                    // currentIndex's own block when totalBlocks changes for
                    // its OWN reasons (see the doc above), but that's
                    // exactly wrong here: it raced the landOnSettingsPage
                    // effect above (both fire off the same totalBlocks
                    // change, in the same frame) and could snap to the
                    // CURRENT CAR right after that effect had already landed
                    // on the new Settings slot, silently undoing it --
                    // reported as toggling the switch not actually taking
                    // you to the embedded page. That effect's own
                    // consumeLandOnSettingsPage() call clears this flag once
                    // it's genuinely done, so deferring to it here is safe
                    // even if this effect happens to run first.
                    if (state.value.landOnSettingsPage) return@LaunchedEffect
                    val targetBlock = currentIndex.coerceIn(0, count - 1) / perPage
                    wrap.snapToReal(targetBlock)
                }
                // Hoisted identity badge state for the single-car-per-page
                // pager (perPage == 1) -- one shared TitleFlightOverlay, driven
                // by whichever page is currently SETTLED (car or the
                // embedded Settings slot), rather than each page keeping its
                // own. See HoistedIdentityFlight's own doc. The position is
                // deliberately NOT reset on page switches: a stale value
                // holds the badge steady for the single frame it takes the
                // newly settled page's own report to arrive (guaranteed
                // next layout pass -- onPositioned only ever fires from the
                // page currently holding the hoisted flight), and if the new
                // page's answer differs the badge just fades -- its only
                // move -- rather than flashing through a wrong state.value.
                val density = LocalDensity.current
                val hoistedTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val hoistedTopInsetPx = with(density) { hoistedTopInset.toPx() }
                val hoistedScrollToTop = remember { mutableStateOf<(suspend () -> Unit)?>(null) }
                val pillScope = rememberCoroutineScope()
                // remember(Unit), not remember(hoistedTopInsetPx): the old
                // keyed remember threw this whole object away -- accumulated
                // dock state, colour, titleScale, everything -- and rebuilt
                // it from scratch every time the status-bar inset itself
                // changed (rotation, fold/unfold, multi-window resize), even
                // though nothing about WHICH page is hoisted or what it's
                // doing changed at all. The freshly-built replacement then
                // had to re-earn its position from a genuine sentinel-free
                // "nothing reported yet" state (see HeroTitleFlight's own
                // doc) before the badge was visible again. topInsetPx is
                // pushed into the persistent object via a plain field write
                // below instead -- see HeroTitleFlight.topInsetPx's own doc.
                val hoistedFlight = remember {
                    HoistedIdentityFlight(
                        flight = HeroTitleFlight(hoistedTopInsetPx, with(density) { TitleDockHysteresis.toPx() }),
                        scrollToTop = hoistedScrollToTop,
                    )
                }
                SideEffect { hoistedFlight.flight.topInsetPx = hoistedTopInsetPx }
                // Lets the page dots (and anything else that dodges the Title id) hide the
                // instant a name is docked, without needing their bounds to actually overlap --
                // see FloatingRegistry.nameDocked's own doc for why real overlap is the wrong
                // test here. `hoistedFlight` is remember{}'d once for this whole screen's life
                // (see its own doc just above), so this lambda's captured reference never goes
                // stale; the SideEffect just needs to run once; re-running it every recomposition
                // is a harmless identical assignment.
                SideEffect { floatingRegistry.nameDocked = { hoistedFlight.flight.docked.value } }
                // Per-page (keyed by pager page index, which every block --
                // car or the embedded Settings slot -- has exactly one of)
                // live "is THIS page's own title currently docked" flag,
                // reported up by whichever page is currently live (settled
                // or not -- see VehicleDetailContent/SettingsScreen's own
                // `onDockedChanged`). This used to be inferred purely from
                // `page == pager.settledPage`, which conflated two different
                // questions: "which page is settled" and "should the shared
                // corner badge take over from this page's own inline title".
                // A page can be settled for a long time while fully
                // undocked (the ordinary hero-card state) -- hoisting it
                // regardless meant its name was ALWAYS routed through the
                // shared floating overlay instead of ordinary page content,
                // even mid-drag, which is what let the badge visibly detach
                // from the card it names. Gating `hoisted` on this map
                // instead means only a page that has ACTUALLY scrolled its
                // title past the status bar ever claims the shared flight;
                // every other page (settled-but-undocked, or the
                // pre-composed neighbour) renders its own name as plain
                // page content that moves 1:1 with the pager's own drag.
                // Keyed by stable identity (a VIN, or "settings"), NOT by raw
                // page index -- an index's real-world meaning isn't stable:
                // deleting a car shifts every later one down a slot,
                // resizing a foldable/tablet window changes perPage and so
                // pageCount, and reordering cars (drag-to-reorder, reachable
                // from the embedded Settings page without ever leaving this
                // same composition) reassigns which car sits at which index
                // directly. An earlier, index-keyed version of this exact
                // idea (lastKnownDocked, since removed) had precisely this
                // bug: after a reorder, a page's dockedPages entry could
                // describe a DIFFERENT car than the one now sitting at that
                // index, hoisting the wrong page's badge or leaving the
                // right one stuck un-hoisted.
                val dockedPages = remember { mutableStateMapOf<Any, Boolean>() }
                // Cleared the instant `perPage` is observed to have actually
                // changed (grid <-> single-car, a live foldable/multi-window
                // resize) -- synchronously, during composition, NOT via a
                // LaunchedEffect(perPage): a coroutine-based clear only runs
                // after THIS recomposition (the one that first sees the new
                // perPage value, and that ALSO swaps in the freshly-built
                // single-car pager composables below) has already committed,
                // leaving those fresh composables' own first `hoisted`/
                // `hoistedVisible` reads (further down) still seeing whatever
                // stale `true` a car left behind before the resize -- one
                // whole recomposition too late to prevent hoisting a
                // genuinely-undocked, just-recomposed page for a frame.
                // dockedPages only ever gets written `if (perPage == 1)` (see
                // onDockedChanged's own gate below), so any entries left
                // over from a prior perPage==1 stint are unconditionally
                // stale once perPage has changed at all -- clearing the
                // whole map, not just one key, is correct here.
                var lastPerPage by remember { mutableStateOf(perPage) }
                if (lastPerPage != perPage) {
                    dockedPages.clear()
                    lastPerPage = perPage
                }
                fun dockedPageKey(page: Int): Any =
                    if (settingsAsPage && page == pageCount) "settings" else vehicles.getOrNull(page)?.vin ?: page
                // Whether the shared hoisted badge SHOULD be showing right
                // now, and which page it's showing/fading for -- computed
                // HERE (not beside the AnimatedVisibility call site that
                // actually renders it, further below) so the per-page pager
                // content below can also read them; see `isSettledAndDocked`
                // and `hoistedFullyGone`'s own docs for why both matter.
                val hoistedVisible = perPage == 1 && dockedPages[dockedPageKey(pager.settledPage)] == true
                // Frozen at the last page seen while `hoistedVisible` was
                // actually true. AnimatedVisibility (further below) keeps
                // its content composed for the duration of its own exit
                // fade, and `pager.settledPage` may have ALREADY moved on to
                // a DIFFERENT, never-docked page by the time that fade
                // starts (a fast swipe straight off a still-docked car) --
                // reading `pager.settledPage` straight, at either use site,
                // would relabel the still-fading badge with the new page's
                // identity, or (see `isSettledAndDocked` below) incorrectly
                // extend the new page's own hoisted grace period using the
                // OLD page's fade state.value. Written plainly here in
                // composition, not inside an effect/coroutine -- every
                // reader in this same pass sees the just-written value
                // immediately.
                var frozenBlock by remember { mutableStateOf(realBlock(pager.settledPage)) }
                if (hoistedVisible) frozenBlock = realBlock(pager.settledPage)
                // Backs the AnimatedVisibility call site further below
                // instead of a bare Boolean, so its own idle/target
                // bookkeeping can answer "has the exit fade actually
                // FINISHED", not just "has the dock flag flipped false" --
                // see `hoistedFullyGone`'s own doc for why the two are
                // different questions.
                val hoistedVisibleState = remember { MutableTransitionState(false) }
                // True only once the shared badge's own exit fade has
                // genuinely finished playing (isIdle) settled on "gone"
                // (!targetState) -- NOT the instant dockedPages flips false,
                // which only means the SPRING settled, one phase before the
                // 160ms crossfade even starts. Read synchronously here (a
                // plain property read on a Compose-owned object, not a
                // remembered duration or a coroutine delay), so using it
                // below to gate the undock hand-off can't race the fade the
                // way a `LaunchedEffect(...) { delay(160) }` guess could.
                val hoistedFullyGone = hoistedVisibleState.isIdle && !hoistedVisibleState.targetState
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // Finger swipe between cars is ON. Every page renders its FULL
                        // pebble column (VehicleDetailContent → PebbleList) — there is no
                        // in-transit skeleton. Swipe smoothness comes from two places:
                        // PebbleList's own one-frame lazy-fill (only the first EAGER_PEBBLES
                        // sections compose their bodies immediately; the rest fill one frame
                        // later) and beyondViewportPageCount=1 pre-composing the neighbour
                        // while idle, off the drag critical path.
                        userScrollEnabled = true,
                        // beyondViewportPageCount = 1 (was unset → default 0): the
                        // default meant the (heavy) neighbour car page only started
                        // composing the instant it peeked in — i.e. on the FIRST frames
                        // of the drag — so swiping between cars hitched right as it
                        // began. Pre-composing one neighbour while idle moves that work
                        // off the drag critical path. This matches the expanded pager
                        // (which already sets 1 with the same VehicleDetailContent
                        // pages) and the cover-screen pager, so it's consistent with
                        // proven-safe siblings. (The remaining ceiling is that each
                        // page composes a whole car's pebble list; making that lazy is
                        // a bigger, reorder-model-sensitive change left for a device.)
                        //
                        // KEEP THIS AT 1 — do NOT raise it. 1→2 holds two more live
                        // compositions and widens any state emission that DOES change
                        // UiState from ~3 pages to ~5.
                        //
                        // This used to say "because UiState is unstable". It is not: it is
                        // @Immutable, as are Appearance/NotificationPrefs, AppViewModel is
                        // @Stable, and compose-stability.conf covers the `data` package.
                        //
                        // The REAL remaining cost is the opposite of instability. Because
                        // UiState is @Immutable it is diffed with its generated equals(),
                        // which compares every field -- so any one changed field makes the
                        // whole object unequal and every pebble taking it whole recomposes.
                        // Do NOT "fix" that by dropping @Immutable: an unstable object is
                        // compared by reference instead, which is strictly less permissive.
                        //
                        // Fixed: SinglePebble now wraps the `state` it hands each pebble in
                        // remember(<that pebble's own catalogued fields>) { state.value }, so an
                        // unrelated field changing (another car's weather, an AI/update
                        // probe, a status fetch for a page that isn't even visible) no
                        // longer forces every pebble on every in-composition page to
                        // recompose -- only the ones whose own dependencies actually
                        // changed. currentIndex already lives outside UiState, so a plain
                        // car-switch settle changes nothing any pebble reads at all, and now
                        // that holds for pebble recomposition too, not just for triggering a
                        // new UiState emission in the first place. Reported as real,
                        // measurable cold-start/car-switch lag on a real device; see
                        // SinglePebble's own doc for the full reasoning and the per-pebble
                        // dependency lists.
                        beyondViewportPageCount = 1,
                    ) { page ->
                        // Same fade/scale transition the expanded single-car pager
                        // above uses (see its own comment for why: the continuous
                        // offset is read only inside graphicsLayer{} below, draw-phase
                        // only, and the secondary "snap bounce" spring this used to
                        // multiply in is gone -- it lagged the visual response behind
                        // the actual drag for the whole gesture, which is what made
                        // this pager's swipe read as less smooth than the cover
                        // screen's equivalent). This, the default view most people
                        // see swiping between cars day to day, previously had no
                        // per-page transform at all, just a plain flat scroll.
                        val block = realBlock(page)
                        val start = block * perPage
                        val end = minOf(start + perPage, count)
                        // Removes this page's own dockedPages entry the instant its
                        // key changes identity OR it leaves composition -- covers
                        // both real disposal (scrolled past beyondViewportPageCount,
                        // so a fresh instance later reusing this key starts from "not
                        // reported docked yet" instead of inheriting a stale `true`)
                        // and a reorder reassigning this pager slot to a different
                        // car mid-life (DisposableEffect re-keys on dockedPageKey(page)
                        // changing, cleaning up the OLD vin's entry as part of the
                        // same recomposition instead of leaving it orphaned). Nothing
                        // in this file previously cleared dockedPages at all, so a
                        // stale `true` could hoist a freshly-recomposed, genuinely
                        // undocked page for one or more frames -- exactly the class
                        // of "flash" this whole audit was looking for. perPage > 1
                        // never writes dockedPages (see onDockedChanged's own gate
                        // below), so this is a no-op there.
                        if (perPage == 1) {
                            val dpKey = dockedPageKey(page)
                            DisposableEffect(dpKey) { onDispose { dockedPages.remove(dpKey) } }
                        }
                        // The "is this the settled page" test used to live here, as
                        // `page == pager.settledPage`. Discrete, yes -- but it still
                        // subscribed this page's composition to settledPage, so every
                        // in-composition page (three, with beyondViewportPageCount=1)
                        // recomposed its ENTIRE pebble column the moment a swipe
                        // settled. That landed on the same frames as the settle
                        // animation's tail and as selectIndex's own state emission,
                        // which recomposes those same three pages again: two full
                        // rebuilds of three car pages, back to back, exactly at the
                        // end of the gesture. That is the switch-pages hitch.
                        //
                        // It gates one callback, so it moved INTO that callback --
                        // read at invoke time, off the composition path entirely.
                        // No blur, no rotationZ tilt -- see the expanded pager above.
                        // NO pagerDepth here. Reported from a real device: this
                        // swipe was smooth when it was a plain flat scroll, and
                        // went juttery once the shrink was added. A graphicsLayer
                        // scale is cheap on a simple layer, but this page is a
                        // full pebble column and every pebble draws an elevation
                        // shadow -- shadows are rasterized from the layer's
                        // resolved size, so a scale that changes every frame
                        // re-renders all of them every frame, on the drag's
                        // critical path. The cover-screen pager keeps its shrink
                        // because its pages are small and shadow-light.
                        //
                        // The transition this was meant to improve is not worth
                        // the gesture it happens during: a swipe that tracks the
                        // finger exactly IS the effect.
                        // Computed once per page body and reused below, instead of
                        // repeating the full expression (and its dockedPageKey()
                        // call + map lookup) at each call-site argument -- the
                        // "resolve once, don't re-derive per argument" rule this
                        // file already applies elsewhere (see TitleFlightOverlay's
                        // own textColorOverride doc).
                        //
                        // `|| (!hoistedFullyGone && page == frozenBlock)`, not
                        // just the raw dockedPages flag: dockedPages flips
                        // false the instant the shared badge's own SPRING
                        // settles back undocked, one phase before its 160ms
                        // crossfade (AnimatedVisibility, further above/below)
                        // even starts. Handing `hoisted` back to null the
                        // instant the flag flips used to switch this page's
                        // ambient LocalHeroTitleFlight back to its own local
                        // flight immediately -- cutting the shared flight off
                        // from any further live position reports while it was
                        // STILL VISIBLE, fading out for another 160ms. If the
                        // user was still actively scrolling during that window
                        // (a slow, deliberate scroll past the undock threshold,
                        // as opposed to a fling that's already stopped by the
                        // time the spring settles), the exiting badge kept
                        // animating toward a now-frozen stale target while the
                        // freshly-live local badge tracked real, still-moving
                        // coordinates -- the two visibly diverging, reading as
                        // the name flickering/partly vanishing rather than
                        // gliding. Keeping `hoisted` (and therefore the shared
                        // flight's own live position feed) alive for the FULL
                        // fade, not just the spring phase, closes that gap.
                        // Gated on `page == frozenBlock`, not just "any
                        // currently-settled page": without it, swiping straight
                        // from a still-docked car to a DIFFERENT, never-docked
                        // one would incorrectly extend the NEW page's own
                        // hoisted grace period off the OLD page's still-fading
                        // badge -- frozenBlock is specifically which page that
                        // badge belongs to.
                        val isSettledAndDocked = perPage == 1 && page == pager.settledPage &&
                            (dockedPages[dockedPageKey(page)] == true || (!hoistedFullyGone && page == frozenBlock))
                        // remember(page), not a fresh lambda literal per
                        // recomposition -- this whole per-page content block
                        // recomposes for reasons unrelated to docking (any
                        // UiState field this page's own descendants read), and
                        // `page` alone is enough to make this a stable function
                        // of "which page", the only thing the callback's own
                        // closure actually depends on.
                        val onPageDockedChanged: ((Boolean) -> Unit)? = remember(page) {
                            if (perPage == 1) ({ d: Boolean -> dockedPages[dockedPageKey(page)] = d }) else null
                        }
                        if (settingsAsPage && block == pageCount) {
                            // The extra slot: Settings itself, embedded rather than
                            // navigated to -- see SettingsScreen's own `embedded` doc.
                            // hoisted only for the SETTLED page, AND only once that
                            // page's own title has actually scrolled into the docked
                            // (pill) state -- see dockedPages' own doc above for why
                            // "settled" alone isn't the right gate any more.
                            SettingsScreen(
                                vm, embedded = true,
                                hoisted = if (isSettledAndDocked) hoistedFlight else null,
                                onDockedChanged = onPageDockedChanged,
                                // See VehicleDetailContent's identical `pageLabel`
                                // doc -- matches the shared hoisted badge's own
                                // label so the hand-off between the two instances
                                // has no width to pop.
                                pageLabel = if (perPage == 1 && totalBlocks > 1) "${block + 1} / $totalBlocks" else null,
                            )
                        } else {
                        Row(Modifier.fillMaxSize()) {
                            for (i in start until end) {
                                val gv = vehicles[i]
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    CarThemeOverride(
                                        paletteId = appearance.carCustomPaletteIds[gv.vin],
                                        customPalettes = appearance.customPalettes,
                                        themeMode = appearance.themeMode,
                                        vibrancy = appearance.vibrancy,
                                    ) {
                                        VehicleDetailContent(
                                            gv, state, vm,
                                            onExpand = if (canExpand) ({ vm.expand(i) }) else null,
                                            // Dynamic, not a flat "last car always leaves
                                            // room": the persistent gear button this is
                                            // dodging is itself hidden right here, in the
                                            // collapsed grid, whenever settingsAsPage is on
                                            // (see that button's own condition below --
                                            // expandedIdx is always null in this branch, so
                                            // its "|| expandedIdx != null" half never
                                            // applies). Reserving the gap for a button
                                            // that isn't there just left the last car's own
                                            // expand button sitting noticeably further from
                                            // the true corner than every other car's, for
                                            // no reason once nothing was actually competing
                                            // with it.
                                            reserveHeaderEnd = canExpand && i == end - 1 && !appearance.settingsAsPage,
                                            // Same condition PagerDotsFor itself uses to
                                            // decide whether it's showing at all -- see
                                            // reserveTopForDots's own doc.
                                            reserveTopForDots = totalBlocks > 1,
                                            // Only hide the per-car pull indicator in the
                                            // multi-car grid (perPage > 1) -- a prior fix
                                            // meant for the grid only ended up applying here
                                            // unconditionally, silently killing the single-
                                            // car view's refresh feedback too.
                                            hideIndicator = perPage > 1,
                                            // hoisted only for the SETTLED page in
                                            // single-car-per-page mode, AND only once
                                            // that page's own title has actually
                                            // scrolled into the docked (pill) state --
                                            // see dockedPages' own doc above. perPage >
                                            // 1 shows several cars at once, so there is
                                            // no single "the settled car" to hoist.
                                            hoisted = if (isSettledAndDocked) hoistedFlight else null,
                                            // Every page in the single-car-per-page
                                            // pager (settled or the pre-composed
                                            // neighbour alike) reports its own live
                                            // docked state up into dockedPages -- see
                                            // that map's own doc for why this can no
                                            // longer be conditioned on being settled.
                                            onDockedChanged = onPageDockedChanged,
                                            // Feeds PagerDotsFor's own collision
                                            // dodge -- was missing from this call
                                            // site entirely, which is why the dots
                                            // never actually dodged: this page's
                                            // own (non-hoisted) badge is the one
                                            // that's live and flying near the top
                                            // for the whole undocked/pre-dock
                                            // phase, in BOTH single-car and grid
                                            // mode, and nothing here was reporting
                                            // its bounds at all.
                                                    // Only a grid column's container is
                                            // genuinely offset from the
                                            // composition root -- see
                                            // TitleFlightOverlay's own
                                            // `containerRelative` doc for why this
                                            // must stay scoped to exactly that
                                            // case.
                                            gridColumn = perPage > 1,
                                            // See VehicleDetailContent's own
                                            // `pageLabel` doc -- matches the
                                            // shared hoisted badge's own label so
                                            // the hand-off between the two
                                            // instances has no width to pop.
                                            // Only meaningful for the pager this
                                            // page's badge can actually hand off
                                            // into (perPage == 1); grid columns
                                            // never hoist at all.
                                            pageLabel = if (perPage == 1 && totalBlocks > 1) "${block + 1} / $totalBlocks" else null,
                                        )
                                    }
                                }
                            }
                            repeat(perPage - (end - start)) { Spacer(Modifier.weight(1f)) }
                        }
                        }
                    }
                    StatusBarScrim()
                    // Floating animated page indicator (no thin top bar). totalBlocks,
                    // not pageCount -- the dots include the Settings slot (one more,
                    // trailing dot) when settingsAsPage is on, same as any other page.
                    if (totalBlocks > 1 && !LocalReorderActive.current) {
                        PagerDotsFor(
                            pager = pager,
                            real = { realBlock(it) },
                            count = totalBlocks,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = HeaderCornerGap)
                                .floatingOverlay(FloatingIds.PagerDots),
                            // Published by the modifier above -- the node that carries the inset and the
                            // refresh shift -- so the dots must not also publish under the same id.
                            registerBounds = false,
                            // Guarded like every other currentIndex read in this
                            // function (currentVehicle above, etc.) -- currentIndex
                            // is its own StateFlow, independent of `vehicles`, so a
                            // resync/removal shrinking the list can leave it briefly
                            // out of range; an unguarded vehicles[currentIndex] here
                            // would crash the screen on a mistimed pull-to-refresh.
                            onRefresh = { vehicles.getOrNull(currentIndex)?.let { vm.refreshStatus(it) } },
                        )
                    }
                    // Grid mode (perPage > 1, wide/large screens) hides each
                    // card's own pull-to-refresh indicator above -- state.value.refreshing
                    // is one app-wide flag, not per-car, so leaving them unhidden
                    // would light up every visible card's spinner for a refresh
                    // that only touched one of them. But that left a real gap:
                    // pageCount == 1 (every car already fits on one page, common
                    // on tablets) meant PagerDots above never renders either, so
                    // pulling to refresh in the grid had *zero* visual feedback of
                    // any kind. One shared, real M3 Expressive indicator here
                    // covers every grid case, page dots or not.
                    if (perPage > 1) {
                        AnimatedVisibility(
                            visible = state.value.refreshing,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(200)),
                            // fade = false: this is the one piece of chrome that must stay visible exactly
                            // when the rest of it fades out -- it IS the refresh.
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = HeaderCornerGap)
                                .floatingOverlay(FloatingIds.RefreshIndicator, fade = false),
                        ) {
                            LoadingIndicator()
                        }
                    }
                    // Hoisted identity badge -- one shared TitleFlightOverlay for
                    // single-car-per-page mode, following whichever page is
                    // currently SETTLED (car or the embedded Settings slot).
                    // See hoistedFlight's own doc above.
                    // Mounted ONLY once the settled page has actually reported
                    // itself docked (dockedPages, above) -- NOT unconditionally
                    // for every perPage==1 frame the way this used to read. Two
                    // reasons this can no longer stay unconditional now that
                    // `hoisted` itself is gated the same way (see the call
                    // sites' own doc): first, an undocked settled page renders
                    // its OWN plain title as ordinary content now (VehicleDetail
                    // Content/SettingsScreen's own `local` path), so an always-
                    // mounted copy here would draw a SECOND, stale copy of
                    // whatever name this shared flight last carried right on
                    // top of it. Second, nothing is writing fresh reports into
                    // hoistedFlight.flight while no page currently owns it, so
                    // that stale copy wouldn't even be showing the RIGHT car --
                    // exactly last fix's bug 2 (wrong name at a stale position),
                    // just relocated here instead of at the settle boundary.
                    // The one tradeoff: mounting/unmounting this composable
                    // resets TitleFlightOverlay's own internal dock/undock
                    // spring each time, instead of that spring free-running
                    // continuously the way a truly permanent instance would --
                    // acceptable because by the time this mounts, the page's
                    // own `local` flight has already been reporting the exact
                    // corner-adjacent position for a while (see
                    // VehicleDetailContent's `onDockedChanged`/hoisted hand-off
                    // doc), so there's no visible snap.
                    //
                    // Wrapped in AnimatedVisibility, not a plain `if`, so
                    // mounting/unmounting fades rather than pops -- a bare
                    // `if` used to tear this composable down (and stand it
                    // back up) INSTANTLY the moment the settled page's own
                    // docked state differs from the page swiped away from
                    // (e.g. settling on an undocked car right after a docked
                    // one), which read as the corner pill just vanishing/
                    // appearing with no transition at all.
                    //
                    // `hoistedVisible`/`frozenBlock` are computed once,
                    // higher up (right after `dockedPageKey`), not here --
                    // the per-page pager content above needs to read them
                    // too (see `isSettledAndDocked`'s own doc). Backed by
                    // `hoistedVisibleState`, a MutableTransitionState, not a
                    // bare `visible: Boolean` -- see `hoistedFullyGone`'s own
                    // doc for why knowing exactly when this fade FINISHES,
                    // not just when it starts, matters.
                    hoistedVisibleState.targetState = hoistedVisible
                    AnimatedVisibility(
                        visibleState = hoistedVisibleState,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                    ) {
                        // Settled, not current: matches every other "which page is
                        // this" read in this pager (the settle effect above), so
                        // the badge's own identity only updates mid-swipe once a
                        // page actually wins, not on every frame of the drag.
                        val settledBlock = frozenBlock
                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                        val onSettingsSlot = settingsAsPage && settledBlock == pageCount
                        val title = if (onSettingsSlot) "Settings" else vehicles.getOrNull(settledBlock)?.name ?: ""
                        // ONE persistent TitleFlightOverlay, bound directly to the
                        // single shared hoistedFlight.flight -- never torn down and
                        // rebuilt per page (an AnimatedContent-per-block design used
                        // to live here; see git history for the full saga of bugs
                        // that came from swapping the underlying flight object on
                        // every switch: stale-geometry windows, readiness races, a
                        // duplicate badge on the pre-composed neighbour, and a
                        // dock-state cache that could itself go stale). Since the
                        // object is never swapped, TitleFlightOverlay's OWN existing
                        // spring (mounted/LaunchedEffect(docked, flight), already
                        // used and proven for a real SCROLL-driven dock/undock
                        // crossing) is what carries a page switch that changes dock
                        // state too -- no separate "hop" machinery needed, because
                        // it is not a structurally different event to this function
                        // any more. inlinePos/dockedAnchor are also structurally the
                        // SAME position for every car (same corner offsets, same
                        // hero-card layout), so hero-hero and pill-pill switches
                        // don't visibly move at all -- only the TEXT changes, via
                        // the inner AnimatedContent in `content` below.
                        TitleFlightOverlay(
                            flight = hoistedFlight.flight,
                            cornerX = 16.dp,
                            cornerY = hoistedTopInset + HeaderCornerGap,
                            // Car slots clear the top-right gear/expand chrome, same
                            // as VehicleDetailContent's own badge (72dp). The embedded
                            // Settings slot instead needs to clear the always-visible
                            // 172dp Simple/Advanced toggle in the corner (192dp, same
                            // value SettingsScreen's own standalone route already
                            // reserves for it -- see SettingsHeaderRow) -- without
                            // this, a docked "Settings" pill could grow wide enough to
                            // run under that toggle, something only the standalone
                            // route was guarding against.
                            reserveEnd = if (onSettingsSlot) 192.dp else 72.dp,
                            maxWidth = screenWidth - 16.dp - (if (onSettingsSlot) 192.dp else 72.dp) - 32.dp,
                            // The Settings slot has no hero photo to morph its own
                            // colour against, so it's forced to plain onSurface;
                            // every car slot instead reads its own flight's live
                            // colour, resolved INSIDE TitleFlightOverlay (see
                            // textColorOverride's own doc for why reading it
                            // there instead of here as a call-site argument
                            // matters).
                            textColorOverride = if (onSettingsSlot) MaterialTheme.colorScheme.onSurface else null,
                            onClick = { pillScope.launch { hoistedScrollToTop.value?.invoke() } },
                            // Keeps dockedPages in sync with THIS shared
                            // badge's own resting state, in both directions
                            // -- see onSettledChanged's own doc for why
                            // undocking used to be reported off the raw
                            // scroll-threshold flag instead (from
                            // VehicleDetailContent/SettingsScreen), which cut
                            // this exact instance off from further position
                            // updates while its own exit spring was often
                            // still mid-flight, reading as a stutter back
                            // toward the pebble. Keyed off `frozenBlock`, not
                            // `pager.settledPage` -- this can still fire
                            // during the AnimatedVisibility exit fade, by
                            // which point the pager may have already settled
                            // onto a different page; `frozenBlock` is the
                            // page this instance was actually mounted for.
                            onSettledChanged = { atRest -> dockedPages[dockedPageKey(frozenBlock)] = atRest },
                            measureContent = {
                                Text(
                                    title,
                                    // headlineSmall, not titleLarge -- matches the
                                    // base PebbleShell actually scales its own
                                    // (invisible) title anchor from (see that
                                    // Text's own `titleStyle` comment). The flying
                                    // Text used to be styled a whole different type
                                    // step (titleLarge, 22sp default) than the base
                                    // its shared titleScale ratio was computed
                                    // against (titleMedium/headlineSmall, 16/24sp) --
                                    // so even when titleScale genuinely varied with
                                    // the hero photo pebble's own expand/collapse, the
                                    // rendered size never actually reached either of
                                    // the two type steps it was supposed to land on.
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            extraContent = {
                                // totalBlocks, not vehicles.size -- the Settings slot is
                                // one more page in the same sequence, so it counts too
                                // (see PagerDotsFor above, which already does the same
                                // swap).
                                if (totalBlocks > 1) {
                                    Text(
                                        "${settledBlock + 1} / $totalBlocks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        ) {
                            // A LITERAL masked wipe, not an approximation: `content`
                            // is invoked from inside TitleFlightOverlay's own visible
                            // Text Box, which is already positioned exactly where the
                            // pill/hero-card sits -- so this AnimatedContent's local
                            // bounds genuinely ARE the text's on-screen bounds, and
                            // its default (clipping) SizeTransform genuinely clips to
                            // them, unlike the old outer-AnimatedContent attempt whose
                            // content was offset far outside its own measured box.
                            //
                            // docked read HERE, not hoisted out as a val above --
                            // same reasoning as flight.color's own doc: keeps the
                            // recompose scope this causes down to just this small
                            // inner composable, not the whole hoisted-badge block.
                            val docked by hoistedFlight.flight.docked
                            AnimatedContent(
                                targetState = title,
                                transitionSpec = {
                                    if (docked) {
                                        // A real, local wipe: the outgoing name
                                        // slides out one side while the incoming
                                        // one slides in from the other, both
                                        // clipped to their own (here, genuinely
                                        // local) bounds -- the "morph and change
                                        // the text with a wipe" this whole
                                        // redesign exists for.
                                        (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)))
                                            .togetherWith(
                                                slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180)),
                                            )
                                    } else {
                                        // Plain hero-card text -- already moving
                                        // with the pager's own drag underneath it;
                                        // no separate transition of its own.
                                        EnterTransition.None togetherWith ExitTransition.None
                                    }
                                },
                                label = "hoistedTitleWipe",
                            ) { t ->
                                Text(
                                    t,
                                    // headlineSmall -- see measureContent's
                                    // identical fix just above for why.
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Back/flip ride the refresh shift with the page content during a pull-
        // to-refresh; Settings stays put -- it's a persistent nav target, not
        // page-local chrome, so it shouldn't wander while pulling to refresh.
        if (expandedByUser != null) {
            FloatingIcon(
                icon = Icons.Filled.ArrowBack,
                description = "Back to all cars",
                onClick = { vm.collapse() },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    // No fade: a nav affordance that vanishes mid-refresh is a trap, and unlike
                    // the dots it is not re-drawn by anything else while it is gone.
                    .floatingOverlay(FloatingIds.BackIcon, fade = false),
            )
        }
        if (expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.SwapHoriz,
                description = "Flip columns",
                onClick = { vm.setColumnsFlipped(!appearance.columnsFlipped) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 52.dp)
                    .floatingOverlay(FloatingIds.FlipIcon, fade = false),
            )
        }
        // Hidden when Settings is reached by swiping instead (Appearance.settingsAsPage)
        // -- the pager's own extra page is the discovery mechanism in that mode, so a
        // second, redundant entry point here would contradict the "either/or" the
        // setting itself offers. BUT kept while a car is expanded (expandedIdx != null):
        // that pager has finger-swipe disabled entirely (see its own comment above), so
        // there is no swipe alternative there at all -- hiding this unconditionally
        // would make Settings genuinely unreachable from the expanded view.
        if (!appearance.settingsAsPage || expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.Settings,
                description = "Settings",
                onClick = { vm.openSettings() },
                // shift = false: the cog is a persistent nav target anchored to the SCREEN, not
                // page-local chrome, so it should not wander while the page is pulled. That was
                // already the intent at this site; it is now stated rather than implied by the
                // absence of a modifier.
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                    .floatingOverlay(FloatingIds.SettingsIcon, fade = false, shift = false),
            )
        }
    }
    }
}
