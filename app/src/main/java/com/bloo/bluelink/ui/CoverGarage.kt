@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Cover.kt's flip-cover garage cluster, peeled out of Cover.kt (which kept the
 * tile/tile-face chrome): the settings gate CoverSettingsGate and the two-page
 * car pager CompactGarage with its per-car CompactCar page composable.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.isGen5W
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.max
import com.bloo.uicommon.LocalReorderActive

/**
 * Flip-cover Settings: the REAL scrollable SettingsScreen (the settings grid
 * scrolls exactly as it does on the phone -- the old "manage on your phone"
 * gate card locked the whole screen away behind itself, double-blocking
 * everything from the update card onward), introduced once by a polite
 * "this was built for a taller phone" prompt with a persistent "don't show
 * again". The prompt is a doorbell, not a bouncer: after it, settings just
 * scroll on the cover.
 */
@Composable
internal fun CoverSettingsGate(vm: AppViewModel) {
    val appearance = LocalAppearance.current
    var promptOpen by remember { mutableStateOf(true) }
    SettingsScreen(vm, compact = true)
    if (promptOpen && !appearance.coverSettingsHintDismissed) {
        GlassAlertDialog(
            onDismissRequest = { promptOpen = false },
            title = "Settings on the cover",
            icon = Icons.Filled.Smartphone,
            text = {
                Text("This screen is designed for a taller phone display. You can scroll through everything here, but unfolding the phone makes settings much easier to read and tweak.")
            },
            buttons = {
                val continueSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = continueSource,
                    enabled = true,
                ) {
                    MorphButton(
                        onClick = { promptOpen = false },
                        interactionSource = continueSource,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue on the cover", fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(8.dp))
                val dismissSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = dismissSource,
                    enabled = true,
                ) {
                    MorphTextButton(
                        "Don't show this again",
                        onClick = {
                            promptOpen = false
                            vm.setCoverSettingsHintDismissed(true)
                        },
                        interactionSource = dismissSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

/**
 * Cover-screen layout: swipe left/right for cars, up/down for section tiles.
 *
 * Owns one [HorizontalPager] (`pager`) for switching between cars, using the
 * same "virtual page count = real count * 1000, start in the middle, map
 * back with modulo" trick as the other car pagers in this file to fake
 * infinite wrap-around. Each car's page then hosts its own vertical tile
 * pager/scrubber further down (not shown in this snippet) for swiping
 * between that car's pebbles; `scrubbing` is shared mutable state that, when
 * true, disables `userScrollEnabled` on this horizontal pager so a
 * long-press-drag scrub of the vertical tile indicator can't accidentally
 * also trigger a car-switch swipe underneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactGarage(state: UiState, vm: AppViewModel, appearance: SettingsStore.Appearance) {
    val vehicles = state.vehicles
    val count = vehicles.size
    // count - 1 goes negative with zero cars, and coerceIn(0, -1) throws
    // (min > max) before the pager below ever gets a chance to handle an empty
    // list gracefully. Kept as a crash guard rather than an expected state, and
    // labelled that way on purpose: it is currently UNREACHABLE from the one
    // caller -- GarageScreen returns on its first line when vehicles is empty,
    // and a zero-car app routes to Screen.Empty long before Screen.Garage. This
    // comment used to cite a `compact && vehicles.isEmpty()` branch in that caller
    // as proof the state was real; that branch was itself dead for the same
    // reason, and has been deleted. Two lines of guard against a throwing
    // coerceIn is still worth keeping; the claim that something reaches it wasn't.
    if (count == 0) {
        EmptyScreen(vm)
        return
    }
    // Infinite wrap-around, matching every other car-switching pager in the
    // app (the expanded pager, the default grid) and the cover screen's own
    // tile pager, which already looped.
    // Same as GarageScreen: the index is its own flow, collected here.
    val currentIndex by vm.currentIndex.collectAsState()
    val wrap = rememberWrapPager(count, currentIndex.coerceIn(0, count - 1))
    val pager = wrap.pager
    fun realCar(virtualPage: Int) = wrap.real(virtualPage)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.selectIndex(realCar(it)) }
    }
    // Mirror of the default garage pager's own fix: react to currentIndex
    // changing out from under an already-composed pager (e.g. a widget tap
    // selecting a specific car while the cover screen was already showing a
    // different one) by snapping to it, instead of only ever pushing this
    // pager's own settles into currentIndex one-way.
    LaunchedEffect(currentIndex) {
        wrap.snapToReal(currentIndex.coerceIn(0, count - 1))
    }
    // True while the page scrubber is active; suspends car-switching swipes so a
    // scrub gesture can't be hijacked into flipping to the next car.
    val scrubbing = remember { mutableStateOf(false) }
    // Hide the page indicators while a refresh is in flight (pull-to-refresh /
    // manual refresh) so the loading indicator owns the screen. Shared by both
    // dot rows below (car-switch AND per-car tile) instead of each keeping its
    // own separate Animatable of the exact same value.
    // Held as State, not read via `by` — see the same treatment in GarageScreen.
    // Read in composition scope this fade recomposed the whole cover pager (and,
    // as a plain Float parameter, every CompactCar page) once per animation frame.
    // Published to the shared floating registry rather than animated here. This was the THIRD
    // hand-rolled copy of "fade the chrome while a refresh runs" -- GarageScreen had two of its
    // own before they moved -- and keeping it local is what forced it to be threaded down into
    // CompactCar as a parameter just so one dot row could read it. Modifier.floatingOverlay owns
    // the spring now; this screen publishes the target and nothing recomposes per frame.
    val coverFloatingRegistry = LocalFloatingRegistry.current
    val coverChromeHidden = state.refreshing
    SideEffect { coverFloatingRegistry.chromeHidden = coverChromeHidden }
    // Same reason as the garage's: nothing else resets this, so a refresh in flight when the
    // cover goes away would leave every floating element faded out for whatever comes next.
    DisposableEffect(coverFloatingRegistry) {
        onDispose { coverFloatingRegistry.resetChrome() }
    }
    Box(Modifier.fillMaxSize()) {
        // Measured once and shared by every reader below: the tiles' car-name label, the band
        // itself, and the search dock. Hoisted ABOVE the pager because the tiles need to know
        // whether the band is already naming the car -- see LocalCoverCarName below.
        val band = coverCutoutBand()
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !scrubbing.value,
            beyondViewportPageCount = 1,
        ) { page ->
            val v = vehicles[realCar(page)]
            // No blur -- see the other two car pagers' history for why: a plain
            // Modifier.blur(x.dp) reconstructs and re-lays-out its own modifier
            // node on every drag frame (the jitter this exact pattern caused
            // elsewhere), and this cover-screen pager had never actually been
            // updated when that got fixed there. Just the cheap graphicsLayer
            // fade/scale transforms now, consistent with the other pagers.
            Box(Modifier.fillMaxSize().pagerDepth(pager, page)) {
                CarThemeOverride(
                    paletteId = appearance.carCustomPaletteIds[v.vin],
                    customPalettes = appearance.customPalettes,
                    themeMode = appearance.themeMode,
                    vibrancy = appearance.vibrancy,
                ) {
                    CompositionLocalProvider(
                        LocalCoverScrubbing provides scrubbing,
                        // So each section tile can put the car's name on its own title row --
                        // see CoverTile.trailingLabel. Provided here, where the page's vehicle
                        // is known, rather than threaded through every tile composable.
                        // Null when the camera band is already showing the name. The band is
                        // free real estate beside the island; the tile's trailing label costs a
                        // share of its header row. So where a band exists it owns the name and
                        // the tiles get their full width back -- and the name is never drawn
                        // twice, which it was.
                        LocalCoverCarName provides v.name.takeIf { band == null },
                    ) {
                        CompactCar(v, state, vm)
                    }
                }
            }
        }
        // Measured once and shared by both readers below (the top-overlay name
        // and the band itself), so they can never disagree about whether the
        // band exists and end up showing the name twice or not at all.
        // (band is computed above the pager -- see its hoist there.)
        // Car-switching dots, hoisted out of CompactCar (a per-page composable)
        // and up to here -- a sibling of the whole pager, not inside any one
        // page's fade/scale graphicsLayer -- so it doesn't itself fade and
        // shrink along with the outgoing/incoming car during a swipe, exactly
        // like every other car pager's PagerDots already stays put outside
        // the per-page transform.
        // The car-switching dots.
        //
        // This overlay used to carry the car's NAME above the dots as well, because cover
        // pebbles render header-less and nothing on a section tile said which car you were
        // looking at. That was true when a pebble page was a bare body; it stopped being true
        // once every page went through CoverTile, which draws its own title at the top of the
        // tile -- in exactly the band this overlay occupies. Two titles, one band, and this one
        // reserves no space because it is a sibling drawn OVER the pager.
        //
        // The name now rides each tile's own title row instead (CoverTile.trailingLabel), where
        // it costs no height and cannot collide with anything. What is left here is the dots,
        // which are chrome about the pager rather than about the page.
        val dotsShowing = count > 1 && !LocalReorderActive.current
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = HeaderCornerGap, start = HeaderCornerGap, end = HeaderCornerGap)
                // PagerDots, not Title: with the name gone this overlay IS the dots, and this
                // is the node that carries their real position (status-bar inset and corner
                // gap), so it publishes and PagerDotsFor below opts out.
                // shift = false: the cover has no pull-to-refresh shift of its own -- its
                // refresh is the edge-trace gesture -- so only the fade applies here.
                //
                // active mirrors the dots' OWN condition below. This Column publishes the dots'
                // bounds, but its child is conditional, so with a single car -- or while
                // reordering -- it was publishing a padding-sized rectangle at top-centre for
                // dots that were not there. Nothing reads PagerDots except the dots themselves
                // today, so it was invisible; it is still a lie in the registry, and the whole
                // point of the registry is that other elements can trust what is in it.
                .floatingOverlay(FloatingIds.PagerDots, active = dotsShowing, shift = false),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dotsShowing) {
                PagerDotsFor(
                    pager = pager,
                    real = { realCar(it) },
                    count = count,
                    // No hold-to-refresh here -- the cover screen's own
                    // edge-trace gesture (drag down from the top edge) is
                    // already the refresh affordance in this mode; the dots
                    // are display-only.
                    onRefresh = null,
                    registerBounds = false,
                )
            }
        }
        if (band != null) {
            // Search is available here whenever it's available on the cover
            // at all -- same gate BlooApp itself uses to decide whether to
            // show SearchLayer for the garage. When it holds, this band
            // reserves CoverBandSearchDock's worth of space at the end
            // nearest the camera; SearchLayer reads the same band and docks
            // its own bubble into exactly that reservation (see there) --
            // one tap target, not a second one duplicated here.
            val searchInBand = appearance.showSearch && !state.locked
            // Same glass chip every other floating chrome in the app wears
            // (the identity pill, FloatingIcon) -- bare text here used to sit
            // directly on whatever the tile underneath happened to be
            // showing, so legibility rode entirely on luck (readable over a
            // dark gauge, gone over a bright photo). A pill-shaped backdrop,
            // sized to the band's own height, gives it the same guaranteed
            // contrast every other piece of floating chrome already has, and
            // reads as one more piece of that chrome rather than loose text.
            val bandShape = RoundedCornerShape((band.heightDp / 2f).dp)
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = band.xDp.dp, y = band.yDp.dp)
                    .width(band.widthDp.dp)
                    .height(band.heightDp.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()), bandShape)
                    .ambientRing(bandShape)
                    .dropShadow(bandShape)
                    .frostedRim(bandShape)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Grouped flush against whichever end is next to the camera,
                // not spread across the whole band -- a short name spread
                // full-width by weight(1f) used to leave a dead gap between
                // the text and the island it should read as belonging next
                // to. weight(1f, fill = false) still bounds FittedText enough
                // to shrink-fit inside a narrow band, it just no longer
                // forces the box to fill space the text isn't using.
                horizontalArrangement = Arrangement.spacedBy(
                    4.dp,
                    if (band.nearCameraAtEnd) Alignment.End else Alignment.Start,
                ),
            ) {
                val current = vehicles.getOrNull(currentIndex.coerceIn(0, count - 1))
                // Order follows which end is near the camera, so the dock
                // reservation always lands flush against it regardless of
                // which side of the island this band happens to be on.
                if (!band.nearCameraAtEnd && searchInBand) {
                    Spacer(Modifier.width(CoverBandSearchDock))
                }
                if (current != null) {
                    com.bloo.uicommon.FittedText(
                        text = current.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (band.nearCameraAtEnd && searchInBand) {
                    Spacer(Modifier.width(CoverBandSearchDock))
                }
            }
        }
    }
}

/**
 * One car's page inside [CompactGarage]'s pager: a vertical stack of pebble
 * "tiles" (main summary, climate, charge, location, ...), one per screen,
 * navigated with the same infinite-wrap virtual-page trick as the car
 * pager itself. Also owns three independent, cover-screen-only concerns
 * layered into the same [Box]:
 *  - Camera-cutout avoidance: content is padded via native
 *    WindowInsets.displayCutout (corner-safe, recomposition-aware) so it clears
 *    a punch-hole on whichever edge(s) it touches; a decorative ring is drawn
 *    around the hole so it reads as intentional.
 *  - The edge-trace refresh gesture: a long-press-and-hold that fills an
 *    animated ring around the screen edge over 1.2s; completing the hold
 *    (without releasing or moving past touch slop) triggers a refresh. Its
 *    pointerInput lives on the outer parent [Box], deliberately relying on
 *    Compose's leaf-to-root gesture dispatch so [VerticalPager]'s own drag
 *    recognizer (a child, and therefore evaluated first) gets first claim on
 *    any real vertical drag before this handler ever sees it.
 *  - Per-tile scroll position (`tileScrollStates`), keyed by tile name so a
 *    tall tile's scroll offset survives being paged away from and back to,
 *    and survives the user reordering pebbles (unlike keying by index).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactCar(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
) {
    // Live source passed to SinglePebble (which takes State<UiState> now).
    val stateSource = rememberUpdatedState(state)
    val status = state.statusFor(v)
    val isGen5W = remember(v.brand, v.generation, state.platforms[v.vin]) { state.isGen5WEffective(v) }
    // Cover-screen tiles follow the same order the user arranged the pebbles in
    // (state.sectionsFor). "summary" maps to the always-present "main" tile;
    // "controls" has no cover tile so it falls away. If summary was somehow
    // dropped, "main" is prepended so the cover screen always has a home tile.
    // Memoized on exactly the state slices the predicate reads, so this mapNotNull +
    // list concat doesn't re-run on every unrelated state emission (CompactCar takes
    // the whole UiState, so it recomposes on any change worth reflecting on one
    // car page (status ticks, pending flags, messages) -- the per-tile memo
    // below keeps that cost proportional to what changed.
    val hasBattery = state.hasBattery(v)
    // updateAvailable and updateTileDismissed are in the key because isSectionAvailable reads
    // them for the "update" tile. Without them this memo kept a stale tile list: the update tile
    // could arrive late, or survive being dismissed, until some unrelated key happened to change.
    // The phone's equivalent memo in PebbleList already lists both for exactly this reason -- the
    // cover's copy had drifted from it.
    val tiles = remember(
        state.sectionOrders[v.vin], hasBattery, state.aiEnabled, isGen5W, state.hiddenPebbles,
        state.updateAvailable, state.updateTileDismissed,
    ) {
        state.sectionsFor(v).mapNotNull { section ->
            when (section) {
                "summary" -> "main"
                else -> section.takeIf {
                    it in CompactKnownTiles &&
                        // Cover-screen-only gate, and the reason isSectionAvailable
                        // does not carry it: everywhere else SinglePebble falls back to
                        // a FuelPebble for a car with no battery, so "charge" still has
                        // something to render. The cover has no such fallback tile.
                        (it != "charge" || hasBattery) &&
                        state.isSectionAvailable(v, it)
                }
            }
        }.let { ordered -> if ("main" in ordered) ordered else listOf("main") + ordered }
    }
    // Infinite wrap-around: start in the middle of a huge virtual range and map
    // each virtual page back onto a real tile with modulo. FLAT tiles -- unlike the three
    // horizontal car pagers this VerticalPager gets NO pagerDepth: a full-bleed page that
    // shrinks as it leaves reads as depth between CARS, but between sections of one car it just
    // makes the panel feel like it is wobbling.
    val vWrap = rememberWrapPager(tiles.size)
    val vPager = vWrap.pager
    // NOT read here. Passing the live index as a lambda keeps the pager's mid-swipe position
    // out of CompactCar's own scope -- reading it here recomposed the VerticalPager and every
    // composed tile on the drag's critical path, to move a dot on the right edge.
    val currentTile = { vWrap.currentReal }
    // Per-tile scroll states, keyed by tile name so position persists across
    // pager recycling AND reordering. Tall tiles scroll their own content; the
    // VerticalPager then nested-scrolls to the next/previous tile once a tile is
    // scrolled to its edge.
    val tileScrollStates = remember { mutableMapOf<String, ScrollState>() }
    // Suspend native tile paging while the right-rail scrubber is driving the
    // pager, so a scrub drag can't also be read as a page swipe.
    val coverScrubbing = LocalCoverScrubbing.current

    val density = LocalDensity.current
    // NOTE: nothing here reads the display cutout's boundingRects any more, which
    // is what this note is actually about -- it used to say "nothing here reads the
    // display cutout", flatly, which is not true and sends anyone chasing a
    // cover-screen bump problem to the wrong place. The hand-rolled per-edge
    // CLEARANCE math went first, and the decorative ring that was the last
    // remaining rects reader has now gone too (see where it used to be drawn).
    // Cutout avoidance is still very much present, just native and declarative:
    // the tile Box below takes the scaffold's merged nav-bar-union-cutout inset,
    // and the scrubber rail takes WindowInsets.displayCutout on its End side only.
    // Both are corner-safe and recomposition-aware, which the rects math was not.

    // ---- Edge-trace refresh gesture ----
    // Long-press anywhere on the cover screen to trace a line around the edge.
    // When the line completes its full circuit, trigger a refresh. This is a
    // cover-screen-only interaction (the normal phone layout doesn't use it).
    val edgeTraceProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var edgeTraceHolding by remember { mutableStateOf(false) }
    // The tile-scrubber dots (VerticalPagerDots) are a sibling inside this same
    // Box, so a press over them still reaches this pointerInput during the
    // normal ancestor dispatch -- without carving out their bounds, holding
    // the dots to scrub also started the edge-trace refresh ring underneath,
    // since edge-trace begins timing on raw down regardless of what else the
    // touch lands on. Populated by the dots' own onGloballyPositioned below.
    var dotsBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    LaunchedEffect(edgeTraceHolding) {
        if (edgeTraceHolding) {
            edgeTraceProgress.snapTo(0f)
            edgeTraceProgress.animateTo(
                1f,
                animationSpec = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
            if (edgeTraceHolding) {
                // Only refresh if the user is still holding (didn't release early).
                vm.refreshStatus(v)
            }
            edgeTraceHolding = false
        } else if (edgeTraceProgress.value > 0f) {
            // Released (or cancelled into a swipe) before completing the hold --
            // ease the partial ring back to nothing instead of leaving it frozen
            // at whatever progress it had reached.
            edgeTraceProgress.animateTo(0f, androidx.compose.animation.core.tween(200))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Edge-trace refresh gesture lives here, on the actual PARENT of
            // VerticalPager below, not on a separate sibling Box overlapping
            // it -- sibling dispatch order between two unrelated composables
            // is ambiguous and kept letting this steal the vertical swipe
            // despite two earlier attempts (never consuming; then watching on
            // the Final pass). Parent/child order is NOT ambiguous: the
            // default Main pass runs leaf-to-root, so VerticalPager's own
            // drag recognizer (the child) always gets first crack at a given
            // event, and by the time it bubbles up to this parent's handler,
            // change.isConsumed already reflects whether the pager claimed
            // it. This is the actual textbook nested-gesture-priority
            // pattern, not another guess at pass ordering between siblings.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A press starting inside the tile-scrubber dots' own hit
                    // area belongs entirely to their long-press-to-scrub
                    // gesture -- don't also start timing an edge-trace hold
                    // for it (see dotsBounds' declaration above).
                    if (dotsBounds?.contains(down.position) == true) return@awaitEachGesture
                    // Only arm the edge-trace when the press starts near a screen
                    // EDGE — that's the whole metaphor ("trace around the rim"). It
                    // used to arm on ANY press anywhere, so a slow/stationary press on
                    // a center control (the DC-limit slider, a climate button) both
                    // flickered the ring on and, if held >1.2s, fired an unintended
                    // vm.refreshStatus. Requiring an edge start makes it intentional
                    // and stops it stealing center interactions.
                    val edgeMarginPx = with(density) { 40.dp.toPx() }
                    val nearEdge = down.position.x <= edgeMarginPx ||
                        down.position.x >= size.width - edgeMarginPx ||
                        down.position.y <= edgeMarginPx ||
                        down.position.y >= size.height - edgeMarginPx
                    if (!nearEdge) return@awaitEachGesture
                    edgeTraceHolding = true
                    val slop = viewConfiguration.touchSlop
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || change.isConsumed) break
                            val dx = abs(change.position.x - down.position.x)
                            val dy = abs(change.position.y - down.position.y)
                            if (dx > slop || dy > slop) break
                        }
                    } finally { if (edgeTraceHolding) edgeTraceHolding = false }
                }
            },
    ) {
        // Native vertical paging. The pager owns the swipe gesture and pages on
        // any vertical drag; tall tiles scroll their own content first and the
        // pager nested-scrolls to the next/previous tile once a tile is at its
        // edge. The car-switching HorizontalPager is orthogonal, so left/right
        // swipes go to it and up/down swipes go here without any custom gesture
        // arbitration. Paging is suspended while the right-rail scrubber is active.
      CoverScaffold(reserveRailGutter = tiles.size > 1) { metrics ->
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = coverScrubbing?.value != true,
            // Pre-compose the neighbouring tile. This is the gesture people actually make on a
            // cover screen -- you flip the phone to check ONE car and then swipe through its
            // sections -- and it was the only pager in the app composing its incoming page
            // mid-swipe, which is exactly when there is no frame budget to spare. The car pager
            // beside it has had this since the beginning.
            //
            // The cost is fine here in a way it would not be on the watch: a flip phone's cover
            // is a small DISPLAY, not a small device. It runs the same flagship SoC as the main
            // screen. And a tile is cheaper than it was -- no per-tile SubcomposeLayout and no
            // hero block any more.
            beyondViewportPageCount = 1,
        ) { page ->
            val i = vWrap.real(page)
            val tileScroll = tileScrollStates.getOrPut(tiles[i]) { ScrollState(0) }
            CompositionLocalProvider(
                LocalForceExpanded provides true,
                LocalPebbleFillHeight provides true,
                LocalCoverScrollState provides tileScroll,
            ) {
                // ONE merged inset from the scaffold (nav bar ∪ cutout ∪ corner-safe
                // camera-bump ∪ base gutter, max()'d per edge) — replaces the old
                // three-layer additive stack that double-reserved the bump and
                // crammed content into the left half.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(metrics.contentPadding),
                ) {
                    // The cover reuses the phone's pebble CARDS, rendered under the
                    // LocalForceExpanded/PebbleFillHeight/CoverScrollState providers so
                    // each pebble draws as an always-expanded, header-less, height-
                    // filling scrolling card (its cover glance-hero branch). The tile
                    // list renames "summary" -> "main", so map it back for SinglePebble.
                    // The home tile is the cover's own combined layout, not
                    // the phone's photo-first HeroHeader -- see CoverMainTile.
                    if (tiles[i] == "main") {
                        // Narrowed the same way every SinglePebble branch already is:
                        // CoverMainTile and CoverActionBar (called from inside its
                        // `actions` lambda) together only ever read this fixed set of
                        // UiState fields, but both took the whole UiState directly, so
                        // any unrelated emission (a location update on another car, a
                        // log line) recomposed this tile the whole time the cover
                        // screen was showing. remember(keys) { state } is the same
                        // "same reference back, skip if the keys didn't move" trick
                        // used at every other pebble call site.
                        val mainState = remember(
                            state.statusFor(v), state.imageUrls[v.vin], state.hasBattery(v),
                            state.hasFuel(v), state.drivingLabel(v), state.loading,
                            state.isPending(v.vin, "doors"), state.isPending(v.vin, "climate"),
                            state.isPending(v.vin, "charge"), state.isPending(v.vin, "hornLights"),
                        ) { state }
                        CoverMainTile(v, mainState, vm)
                    } else {
                        SinglePebble(tiles[i], v, stateSource, vm, Modifier)
                    }
                }
            }
        }
      }
        // The decorative camera ring that used to be drawn here has been
        // removed. It assumed the display cutout was a small circular
        // punch-hole and derived its radius from `cutout.width() / 2`, but a
        // flip cover screen reports the whole camera ISLAND as one bounding
        // rect -- so instead of tracing a lens it swept an enormous faint
        // circle across the panel, well outside the cameras it was meant to
        // acknowledge. Reported from a real device.
        //
        // Not re-fitted to the island shape: the rect is a bounding box, not
        // the real outline, so anything drawn from it is a guess at hardware
        // geometry that varies per device. It was purely cosmetic and load-
        // bearing for nothing (content padding comes from the native
        // WindowInsets.displayCutout on the tile Box above), so the honest
        // fix is to stop drawing it rather than to keep guessing.
        // Edge-trace ring: when holding (gesture handler lives on the outer
        // Box now, see above), a line traces the screen edge clockwise from
        // the top-left. Full circuit = refresh. Purely decorative here --
        // this Box has no pointerInput of its own to conflict with anything.
        Box(Modifier.fillMaxSize()) {
            // A derived BOOLEAN, not the animation value. edgeTraceProgress runs a 1200ms tween,
            // and reading it here put roughly 72 frames of full CompactCar recomposition -- the
            // VerticalPager and every composed tile -- inside the gesture that starts it. The
            // Canvas below already reads .value in its draw lambda; only this gate was wrong,
            // and derivedStateOf means it notifies twice per gesture rather than 72 times.
            val tracing by remember { derivedStateOf { edgeTraceProgress.value > 0.001f } }
            if (tracing) {
                val accent = MaterialTheme.colorScheme.primary
                // The rounded-rect perimeter Path + PathMeasure only depend on the
                // Canvas size/density (constant while this composable is on screen),
                // not on edgeTraceProgress -- so they're built once per size/density
                // and cached here instead of being reallocated on every animation
                // frame. Only measure.getSegment(...) needs to re-run per frame, and
                // `traced` is rewound and reused rather than reallocated each time.
                val perimeterCache = remember { EdgeTracePerimeterCache() }
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = with(density) { 3.dp.toPx() }
                    val inset = stroke / 2f
                    if (perimeterCache.size != size) {
                        val rect = androidx.compose.ui.geometry.Rect(
                            inset, inset, size.width - inset, size.height - inset
                        )
                        // Trace the actual RECTANGULAR (rounded) screen perimeter, not an
                        // ellipse. The old code called drawArc on this full-screen rect,
                        // which draws an arc of the ELLIPSE inscribed in it — a huge oval
                        // bulging far past the visible edges (the "giant blue circle" in
                        // the screenshots). Instead, build the rounded-rect perimeter as a
                        // Path and take the first `progress` fraction of its length via
                        // PathMeasure.getSegment, so a thin stroke grows clockwise hugging
                        // the real edge.
                        val corner = with(density) { 28.dp.toPx() }
                        val perimeter = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    rect,
                                    androidx.compose.ui.geometry.CornerRadius(corner, corner),
                                )
                            )
                        }
                        perimeterCache.measure.setPath(perimeter, false)
                        perimeterCache.size = size
                    }
                    val measure = perimeterCache.measure
                    val traced = perimeterCache.traced
                    traced.rewind()
                    measure.getSegment(
                        0f,
                        measure.length * edgeTraceProgress.value.coerceIn(0f, 1f),
                        traced,
                        true,
                    )
                    drawPath(
                        path = traced,
                        color = accent.copy(alpha = edgeTraceProgress.value.coerceIn(0f, 1f) * 0.85f),
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        // Vertical page dots on the right edge - show which pebble tile is visible.
        // (Car-switching dots are hoisted up to CompactGarage -- see there.)
        if (tiles.size > 1 && !LocalReorderActive.current) {
            VerticalPagerDots(
                current = currentTile,
                count = tiles.size,
                tiles = tiles,
                onPageJump = { targetTile ->
                    vWrap.snapToReal(targetTile)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Clear a right-edge / bottom-right-corner camera bump: the
                    // scrubber sits flush to the physical right edge, so on a device
                    // whose cutout intrudes from the right it used to sit under the
                    // bump. Native displayCutout (End side only) floats it inboard;
                    // it's a no-op when the cutout doesn't touch the right edge.
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                    .padding(end = 4.dp)
                    .floatingOverlay(FloatingIds.TileRail, shift = false)
                    .onGloballyPositioned { dotsBounds = it.boundsInParent() },
            )
        }
    }
}
