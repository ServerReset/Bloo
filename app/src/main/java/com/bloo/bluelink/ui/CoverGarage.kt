@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Cover.kt's flip-cover garage cluster, peeled out of Cover.kt (which kept the
 * tile/tile-face chrome): the settings gate CoverSettingsGate (a one-time "this
 * was built for a taller phone" nudge shown the first time this pager's own
 * folded-in Settings page appears) and the car pager CompactGarage -- its
 * per-car CompactCar page composable, a status card in place of a car when
 * there are none, and one embedded Settings page after everything else.
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlin.math.max

/**
 * Flip-cover Settings: the REAL scrollable SettingsScreen (the settings grid
 * scrolls exactly as it does on the phone -- the old "manage on your phone"
 * gate card locked the whole screen away behind itself, double-blocking
 * everything from the update card onward), introduced once by a polite
 * "this was built for a taller phone" prompt with a persistent "don't show
 * again". The prompt is a doorbell, not a bouncer: after it, settings just
 * scroll on the cover. Called from CompactGarage's own pager -- Settings has
 * no separate route to reach it through any more, on the cover or the phone.
 */
@Composable
internal fun CoverSettingsGate(vm: AppViewModel) {
    SettingsScreen(vm, compact = true)
}


/**
 * Cover-screen layout: swipe left/right for cars (and one page further, for
 * Settings), up/down for section tiles.
 *
 * Owns one [HorizontalPager] (`pager`) for switching between cars, using the
 * same "virtual page count = real count * 1000, start in the middle, map
 * back with modulo" trick as the other car pagers in this file to fake
 * infinite wrap-around. Each car's page then hosts its own vertical tile
 * pager/scrubber further down (not shown in this snippet) for swiping
 * between that car's pebbles. The page right after the last car is not a car
 * at all but an embedded [SettingsScreen] -- the cover has no gear button and
 * no modal Settings route, exactly like the phone garage.
 *
 * `scrubbing` is shared mutable state that, when
 * true, disables `userScrollEnabled` on this horizontal pager so a
 * long-press-drag scrub of the vertical tile indicator can't accidentally
 * also trigger a car-switch swipe underneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactGarage(state: State<UiState>, vm: AppViewModel, appearance: SettingsStore.Appearance, hazeState: HazeState = remember { HazeState() }) {
    // Derived reads, same reasoning as GarageScreen's own: a value parameter would force the
    // CALLER to read state.value in its body (subscribing it to every emission), and a body
    // read here would do the same for this composable -- and this is the whole cover on
    // phones. Only these three fields are ever drawn, so only these three can invalidate.
    val vehicles by remember { derivedStateOf { state.value.vehicles } }
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    val locked by remember { derivedStateOf { state.value.locked } }
    val count = vehicles.size
    // At least one non-Settings page even with zero cars: the "no connection"/
    // "not signed in"/"no vehicles" status card takes that one slot instead of
    // a car, so Settings is still just one swipe away on the cover exactly as
    // it is with cars -- see GarageScreen's matching `slots`/GarageStatusCard
    // for the full reasoning. Also what keeps `slots - 1` (used below) from
    // going negative: count - 1 does with zero cars, and coerceIn(0, -1) throws
    // (min > max).
    val slots = maxOf(count, 1)
    // Infinite wrap-around, matching every other car-switching pager in the
    // app (the expanded pager, the default grid) and the cover screen's own
    // tile pager, which already looped.
    // Same as GarageScreen: the index is its own flow, collected here.
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    // slots + 1, not count: Settings is a page in this pager, exactly as it is
    // in the phone garage's own pager -- the page right after the last car (or
    // the status card, with none), index `slots`. There is no gear button on
    // the cover and no modal route to it from here; swiping past the last real
    // page IS how you reach Settings, the same gesture that already switches cars.
    val total = slots + 1
    val wrap = rememberWrapPager(total, currentIndex.coerceIn(0, slots - 1))
    val pager = wrap.pager
    /** Real item index for a virtual page: a vehicle index, or [slots] for the
     *  folded-in Settings page. */
    fun realCar(virtualPage: Int) = wrap.real(virtualPage)
    LaunchedEffect(pager, count) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val real = realCar(page)
            // Guarded: settling on the Settings page (or the status card slot)
            // is not a car selection, so currentIndex keeps whatever car it had
            // and swiping back lands where you left off instead of snapping to
            // car 0.
            if (real < count) vm.selectIndex(real)
            // See UiState.onSettingsPageSlot's own doc -- the same signal
            // GarageScreen's pager publishes, so SearchLayer's bubble/pill morph
            // tracks the cover's embedded Settings page too.
            vm.setOnSettingsPageSlot(real == slots)
            wrap.recenterIfNearEdge()
        }
    }
    // Mirrors GarageScreen's own reset: nothing else clears this once this pager
    // leaves composition (unfolding, navigating away), so a stale `true` could
    // otherwise outlive the page it described.
    DisposableEffect(Unit) { onDispose { vm.setOnSettingsPageSlot(false) } }
    // Mirror of the default garage pager's own fix: react to currentIndex
    // changing out from under an already-composed pager (e.g. a shortcut tap
    // selecting a specific car while the cover screen was already showing a
    // different one) by snapping to it, instead of only ever pushing this
    // pager's own settles into currentIndex one-way.
    LaunchedEffect(currentIndex) {
        wrap.snapToReal(currentIndex.coerceIn(0, slots - 1))
    }
    // True while the page scrubber is active; suspends car-switching swipes so a
    // scrub gesture can't be hijacked into flipping to the next car.
    val scrubbing = remember { mutableStateOf(false) }
    // Fades the floating chrome while a refresh is in flight (pull-to-refresh /
    // manual refresh) so the loading indicator owns the screen.
    // Held as State, not read via `by` — see the same treatment in GarageScreen.
    // Read in composition scope this fade recomposed the whole cover pager (and,
    // as a plain Float parameter, every CompactCar page) once per animation frame.
    // Published to the shared floating registry rather than animated here. This was the THIRD
    // hand-rolled copy of "fade the chrome while a refresh runs" -- GarageScreen had two of its
    // own before they moved -- and keeping it local is what forced it to be threaded down into
    // CompactCar as a parameter just so one dot row could read it. Modifier.floatingOverlay owns
    // the spring now; this screen publishes the target and nothing recomposes per frame.
    val coverFloatingRegistry = LocalFloatingRegistry.current
    val coverChromeHidden = refreshing
    SideEffect { coverFloatingRegistry.chromeHidden = coverChromeHidden }
    // Same reason as the garage's: nothing else resets this, so a refresh in flight when the
    // cover goes away would leave every floating element faded out for whatever comes next.
    DisposableEffect(coverFloatingRegistry) {
        onDispose { coverFloatingRegistry.resetChrome() }
    }
    // The band chip (below) floats over whatever car page is currently showing --
    // this is what gives it a REAL blur of that content instead of just a flat tint,
    // the same hazeSource/hazeEffect pairing GarageScreen's own pagers already use.
    //
    // Uses the FUNCTION PARAMETER (see its own doc), not a fresh local instance: this
    // used to shadow it with `val hazeState = remember { HazeState() }` right here,
    // silently discarding whatever the caller passed in. GarageScreen's own call site
    // passes its own hazeState through -- ultimately Screens.kt's shared searchHazeState,
    // the SAME instance the floating search bar/results panel and the app-wide snackbar
    // blur whatever's currently on screen through (see GarageScreen's own hazeState doc).
    // With the shadowed local in place, blurs inside this screen worked fine against each
    // other, but nothing outside it -- a snackbar or the search overlay shown while the
    // cover screen was up -- ever saw this screen's content as a hazeSource at all, so
    // their glass silently fell back to a flat tint instead of a real blur.
    Box(Modifier.fillMaxSize()) {
        // Measured once and shared by every reader below: the tiles' car-name label, the band
        // itself, and the search dock. Hoisted ABOVE the pager because the tiles need to know
        // whether the band is already naming the car -- see LocalCoverCarName below.
        val band = coverCutoutBand()
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            userScrollEnabled = !scrubbing.value,
            // NOT a flat 1 -- see GarageScreen's own matching fix for the full
            // reasoning (a real, reported crash: ArrayIndexOutOfBoundsException
            // inside Compose's RememberEventDispatcher, surfacing through this
            // pager's own folded-in SettingsScreen's LazyVerticalStaggeredGrid).
            // This pager shows exactly one item per page (perPage is implicitly
            // 1 here, never narrower), so pre-warming 1 neighbour on EACH side
            // composes 3 virtual pages at once cycling through `total` real
            // items -- for a single-car cover user, total = slots+1 = 2, so a
            // flat beyond=1 guarantees two of those three pages resolve to the
            // SAME real item (the folded-in Settings page) and mount it TWICE
            // at once. (total - 1) / 2, capped at 1, is the largest beyond that
            // can never revisit an item within one full cycle. NOT tied to the key
            // below at all -- every page is keyed by its raw index regardless of this
            // value (see WrapPager.kt's own doc for why); this beyond still exists
            // purely to stop two DIFFERENT virtual pages from ever resolving to the
            // same real item while simultaneously composed.
            beyondViewportPageCount = ((total - 1) / 2).coerceIn(0, 1),
            // Raw page index as the key (never the modulo real item) -- see GarageScreen's
            // own key comment for the crash this avoids.
            key = { page -> page },
        ) { page ->
            val real = realCar(page)
            if (real == slots) {
                // The folded-in Settings page, always the last one -- via
                // CoverSettingsGate rather than SettingsScreen directly, so the
                // one-time "built for a taller phone" nudge shows here too, the
                // only place cover Settings is reached now.
                //
                // Always composed here, the same as any other page in this pager -- see
                // GarageScreen's matching site for why: deferring this build until the
                // pager settled (or later, until it merely reached `currentPage`) traded
                // one off-screen, one-time jank (paying for a full CoverSettingsGate/
                // SettingsScreen build while pre-warmed as a neighbour, before the user
                // could see it) for a reproducible ON-screen black void every real swipe
                // onto Settings -- directly reported. Composing it as a pre-warmed
                // neighbour, unconditionally, means that cost is paid before this page
                // is visible, exactly like every other page.
                Box(Modifier.fillMaxSize().pagerDepth(pager, page)) {
                    CoverSettingsGate(vm)
                }
                return@HorizontalPager
            }
            if (count == 0) {
                // No cars at all: the status card takes this pager's one other
                // slot instead of a car -- see GarageStatusCard's own doc.
                Box(Modifier.fillMaxSize().pagerDepth(pager, page)) {
                    GarageStatusCard(state, vm, hazeState = hazeState)
                }
                return@HorizontalPager
            }
            val v = vehicles[real]
            // No blur -- see the other two car pagers' history for why: a plain
            // Modifier.blur(x.dp) reconstructs and re-lays-out its own modifier
            // node on every drag frame (the jitter this exact pattern caused
            // elsewhere), and this cover-screen pager had never actually been
            // updated when that got fixed there. Just the cheap graphicsLayer
            // fade/scale transforms now, consistent with the other pagers.
            Box(Modifier.fillMaxSize().pagerDepth(pager, page)) {
                CompositionLocalProvider(
                    LocalCoverScrubbing provides scrubbing,
                    // The car's name is deliberately NOT put on the tile any more: a cover
                    // tile is one line tall, and the name ate a chunk of its header row. The
                    // name still shows on the camera band where one exists; the tiles get
                    // their full width back either way.
                    LocalCoverCarName provides null,
                ) {
                    CompactCar(v, state, vm, hazeState = hazeState)
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
        // Pager dots removed: user requested no page indicators at the top of the screen
        if (band != null) {
            // Search is available here whenever it's available on the cover
            // at all -- same gate BlooApp itself uses to decide whether to
            // show SearchLayer for the garage. When it holds, this band
            // reserves CoverBandSearchDock's worth of space at the end
            // nearest the camera; SearchLayer reads the same band and docks
            // its own bubble into exactly that reservation (see there) --
            // one tap target, not a second one duplicated here.
            val searchInBand = appearance.showSearch && !locked
            // Same glass chip every other floating chrome in the app wears
            // (the identity pill, FloatingIcon) -- bare text here used to sit
            // directly on whatever the tile underneath happened to be
            // showing, so legibility rode entirely on luck (readable over a
            // dark gauge, gone over a bright photo). A pill-shaped backdrop,
            // sized to the band's own height, gives it the same guaranteed
            // contrast every other piece of floating chrome already has, and
            // reads as one more piece of that chrome rather than loose text.
            // percent = 50, not a hand-derived half-height radius: RoundedCornerShape's percent
            // basis is already the shorter side of the actual box, the same "true pill" language
            // MorphButton's own pillCornerPercent speaks everywhere else in the app.
            val bandShape = RoundedCornerShape(percent = 50)
            // GlassSurface (GlassChrome.kt): the same shared fill/rim/shadow every
            // other floating chip in the app goes through now, instead of this one
            // hand-chaining ambientRing/dropShadow/frostedRim/background separately.
            // No HazeState in scope on this screen yet (a real blur here is still its
            // own future follow-up, not a regression) -- GlassSurface's own flat-tint
            // fallback is exactly the glassTint(blurred = false) this used to call
            // directly.
            GlassSurface(
                shape = bandShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = band.xDp.dp, y = band.yDp.dp)
                    .width(band.widthDp.dp)
                    .height(band.heightDp.dp)
                    .ambientRing(bandShape),
                hazeState = hazeState,
            ) {
                Row(
                    // fillMaxSize, not wrap-content: this Row used to BE the glass
                    // surface (sized to the full band directly), so its own
                    // horizontalArrangement could pack content flush against
                    // whichever end is near the camera across the FULL band width.
                    // Nested inside GlassSurface's content slot now, it needs its
                    // own explicit full size or GlassSurface's default centering
                    // would shrink-wrap and center this Row instead, losing that
                    // flush-to-one-end packing entirely.
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
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
}
