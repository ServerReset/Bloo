@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Full-detail (single-car) views of the garage: [VehicleDetailContent] (the
 * collapsed single-column car), [ExpandedCar] (the wide dual-column detail),
 * and their shared [CarHeaderRow] fact-chip row. Peeled out of GarageScreen.kt;
 * they keep their original `internal` visibility.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

// --- Full detail ----------------------------------------------------------


/**
 * Single-column car view (phones, and each column of the grid). Everything
 * scrolls together in one [Column] inside [Refreshable] (header row, then
 * the reorderable [PebbleList]).
 *
 * The car's name is drawn exactly ONCE, but not here -- the hero card's own
 * title slot is permanently invisible (space/position only); the one Text
 * that actually paints the name lives in [TitleFlightOverlay], which flies
 * it between that slot and the corner pill. See that function's own doc.
 */
@Composable
internal fun VehicleDetailContent(
    v: Vehicle,
    /**
     * State SOURCE (vm.state.value.collectAsState()), consistent with PebbleList/SinglePebble:
     * per-use state.value reads keep this page and its pebble rows stable against
     * emissions that don't touch the values they actually read.
     */
    state: State<UiState>,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    reserveHeaderEnd: Boolean = false,
    hideIndicator: Boolean = false,
    // True whenever GarageScreen's own PagerDotsFor is showing (totalBlocks
    // > 1 there) -- that indicator floats fixed at TopCenter, independent of
    // this car's own scroll position, so it can sit directly over this
    // car's fact-chip row the instant the car is scrolled to its own top.
    // Reported from a real device: with exactly two cars the dots -- one
    // small circle, one elongated into a bar -- read as a toggle switch
    // sitting half behind the chips. Same idea as reserveHeaderEnd already
    // dodging the Settings gear; this reserves the analogous clearance at
    // the top instead of the end.
    reserveTopForDots: Boolean = false,
    // Non-null ONLY for GarageScreen's single-car-per-page pager's currently
    // SETTLED page, AND only once that page has reported itself DOCKED (see
    // `onDockedChanged` below and the call site's `dockedPages` doc) -- see
    // HoistedIdentityFlight's own doc. A settled-but-undocked page (the
    // ordinary hero-card state, and the common case for a plain hero-to-hero
    // swipe) now gets `hoisted == null` here just like the pre-composed
    // neighbour does, and renders its OWN name as ordinary page content
    // (`local`, below) instead -- see this param's git history for the two
    // bugs that came from routing that case through the shared flight
    // anyway: the badge visibly detaching from its card mid-drag (only the
    // ORIGIN page owned the shared flight for the whole gesture, since
    // `pager.settledPage` doesn't change until the drag fully settles), and
    // a one-frame flash of the new car's name at the old car's stale
    // position right at the settle boundary. When `hoisted` IS non-null,
    // this page's own scroll-to-top is reported into the CALLER's shared
    // flight, and this composable renders NO badge of its own at all -- the
    // caller renders ONE shared badge instead, covering every page
    // including this one.
    hoisted: HoistedIdentityFlight? = null,
    // Reports this page's own live docked state (with hysteresis -- see
    // HeroTitleFlight.docked's own doc) up to the caller on every change,
    // regardless of whether `hoisted` is currently null or not. Called for
    // EVERY page in the single-car-per-page pager -- settled or the
    // pre-composed neighbour alike -- because the caller needs to know the
    // instant a settled-but-undocked page BECOMES docked in order to start
    // passing `hoisted` for it; there's no other signal it could use. Null
    // for every page outside that pager (perPage > 1 grid mode), which has
    // no single "the settled car" for a caller-level flag to mean anything.
    onDockedChanged: ((Boolean) -> Unit)? = null,
    // Forwarded to this page's own (non-hoisted) TitleFlightOverlay call
    // below -- see that parameter's own doc (Screens.kt). Was missing
    // True ONLY when this is a perPage>1 grid column -- forwarded to this
    // page's own TitleFlightOverlay call as `containerRelative`. See that
    // parameter's own doc for why this must stay opt-in and default false.
    gridColumn: Boolean = false,
    // "N / M" page-count label, non-null under the exact same condition the
    // shared hoisted badge shows one (perPage == 1, more than one page) --
    // see this page's own TitleFlightOverlay call below for why passing it
    // here too, not just on the hoisted badge, is load-bearing rather than
    // decorative: without it, the local badge's chrome Row measures
    // narrower (name only) than the hoisted badge's chrome Row (name +
    // label) it gets swapped for the instant this page finishes docking,
    // and Modifier.size(dockedSize...) has no width animation of its own --
    // so the pill visibly popped wider the moment the hand-off happened.
    // Passing the identical label here means both instances measure to the
    // same width, so there's nothing left to jump.
    pageLabel: String? = null,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Built UNCONDITIONALLY now, for every page -- settled, pre-composed
    // neighbour, or a plain standalone grid column alike. This used to be
    // skipped entirely whenever `hoisted != null` or `hoistedPending`, on
    // the theory that a page in either state reports into (or will report
    // into) the caller's shared flight instead and so has no use for one of
    // its own. That left the pre-composed neighbour with NOTHING tracking
    // its own dock state or position until the moment it became settled --
    // exactly the gap that let a stale, previous-page position leak through
    // for a frame right at the settle boundary (see `hoisted`'s own doc).
    // Building this always means every page has a live, continuously
    // up-to-date local flight from the moment it exists, so there's always
    // something real to read from (`onDockedChanged`, below) and always
    // something real to hand off FROM the instant this page's own name
    // needs to take over the shared corner badge.
    val topInsetPx = with(density) { topInset.toPx() }
    // remember(Unit) + SideEffect, not remember(topInsetPx) -- see the
    // hoisted flight's identical construction in GarageScreen for why a
    // keyed remember here silently discarded all accumulated dock/
    // position state on every inset change instead of just picking up
    // the new inset value.
    val heroFlight = remember { HeroTitleFlight(topInsetPx, with(density) { TitleDockHysteresis.toPx() }) }
    SideEffect { heroFlight.topInsetPx = topInsetPx }
    // Same live-scroll-correction wiring as topInsetPx just above -- see
    // `HeroTitleFlight.inlinePos`'s own doc for why this closes the
    // "name feels a frame behind the card" gap. `scroll` is the exact
    // ScrollState the real hero card's own Column (below) places itself
    // against, so both read the identical, same-frame-fresh offset.
    SideEffect { heroFlight.scrollValuePx = { scroll.value.toFloat() } }
    val local = LocalNamePillState(flight = heroFlight)
    // Whichever flight is actually LIVE for this page right now: the
    // caller's shared one while genuinely hoisted, this page's own
    // otherwise. Both `docked` reporting and the ambient
    // LocalHeroTitleFlight below key off this SAME value, so there is never
    // a moment where the two disagree about which object HeroHeader should
    // be writing its real position/colour/scale into.
    val liveFlight = hoisted?.flight ?: local.flight
    // dockedPages is now driven ENTIRELY by whichever TitleFlightOverlay is
    // actually live's own `onSettledChanged` -- this page's own local badge
    // below while `hoisted == null`, or GarageScreen's shared hoisted badge
    // (its own call site) while `hoisted != null`. Used to also report the
    // undocking direction immediately off the raw `liveFlight.docked` flag
    // here -- reasoned at the time to have "no hand-off-timing hazard on the
    // way out", which was wrong: the instant that raw report flipped
    // `dockedPages` false, the shared hoisted badge got torn down as the
    // live one (this page's local flight took back over), which cut the
    // SHARED flight off from any further position updates while its own
    // exit spring was often still mid-flight -- it kept animating toward a
    // now-frozen stale target while the freshly-visible local badge tracked
    // live coordinates, the two visibly diverging for the crossfade window.
    // onSettledChanged (fired only once a spring genuinely finishes, see its
    // own doc) doesn't have that hazard in either direction.
    if (hoisted != null) {
        // Register this page as the one actually driving the hoisted badge.
        // Idempotent, so re-running it every recomposition while hoisted is
        // harmless -- the caller only ever passes non-null here for the
        // currently SETTLED, currently DOCKED page, so there's no risk of
        // two pages fighting over the same hoisted state.
        hoisted.scrollToTop.value = { scroll.animateScrollTo(0) }
    }
    // Narrowed, not `state.value.refreshing`: a bare read here would subscribe this whole page
    // -- all three of them live at once in the pager -- to every UiState emission.
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    Refreshable(v, refreshing, vm, hideIndicator = hideIndicator) {
        CompositionLocalProvider(LocalHeroTitleFlight provides liveFlight) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Inset spacer (not padding) so content scrolls *behind* the bars --
                // topInset alone, no extra breathing room, so the name sits right at
                // the status bar's own edge instead of noticeably below it. Plus
                // PagerDotClearance when the dots are showing -- see
                // reserveTopForDots's own doc.
                Spacer(Modifier.height(topInset + if (reserveTopForDots) PagerDotClearance else 0.dp))
                CarHeaderRow(v, state, onExpand, reserveHeaderEnd, hideName = true)
                // summary (image+gauge) and controls are reorderable pebbles too. The full
                // pebble column always renders while swiping; smoothness comes from
                // PebbleList's own one-frame lazy-fill (filled/EAGER_PEBBLES) + the pager's
                // beyondViewportPageCount=1 pre-compose, not from an in-transit skeleton.
                PebbleList(v, state, vm)
                // bottomInset + 132.dp, not +16.dp: this pager's own floating search
                // bubble (SearchLayer, mounted globally for Screen.Garage -- see
                // Screens.kt's `searchable` gate) sits fixed to the screen's bottom
                // edge over this content, exactly like SettingsScreen's own trailing
                // spacer already accounts for. The old +16dp only cleared the system
                // nav bar, not the search bubble on top of it -- the last pebble's own
                // trailing chevron sat directly under the bubble, visibly cut off by
                // it (confirmed from a real screenshot: the "Diagnostics" row's own
                // expand chevron overlapped by the floating search icon).
                Spacer(Modifier.height(bottomInset + 132.dp))
            }
        }
        // Hoisted mode (this page is settled AND docked) renders NO badge of
        // its own here at all -- the caller (GarageScreen) renders ONE
        // shared badge covering every page, including this one. Every OTHER
        // state -- perPage > 1 grid mode, this pager's pre-composed
        // neighbour, or this same page before it's scrolled into the docked
        // state -- renders its own name here, as ordinary page content that
        // simply moves with the pager/scroll like everything else on the
        // page. See `hoisted`'s own doc.
        // AnimatedVisibility, not a bare `if`, with the SAME fade duration
        // GarageScreen's shared hoisted badge uses for its own enter/exit
        // (see that AnimatedVisibility's own doc) -- a real, reproducible
        // bug traced one side of this hand-off cutting out/in instantly
        // while the other ramped over 160ms, leaving a ~160ms window where
        // the name was dimmer than it should be (or, on the reverse
        // direction, two overlapping copies at mismatched alphas). Fading
        // both sides in lockstep removes that dip entirely.
        AnimatedVisibility(
            visible = hoisted == null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            TitleFlightOverlay(
                flight = local.flight,
                cornerX = 16.dp,
                cornerY = topInset + HeaderCornerGap,
                // Clears the top-right FloatingIcon slot (the expand button
                // in grid columns, the gear on the standalone route) -- 12dp
                // outer padding + 48dp icon + a little air.
                reserveEnd = 72.dp,
                maxWidth = screenWidth - 16.dp - 72.dp - 32.dp,
                // Falls back to onSurface before HeroHeader has reported a real
                // colour yet (this composable's own first frame) -- Unspecified would
                // otherwise resolve through LocalContentColor's own default instead.
                // textColorOverride omitted: `flight` here IS local.flight, so
                // TitleFlightOverlay reads its live colour itself -- see that
                // parameter's own doc for why resolving it there instead of
                // here is load-bearing, not stylistic.
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                containerRelative = gridColumn,
                // See `liveFlight`'s own doc just above -- dockedPages is
                // driven entirely off this, in both directions, rather than
                // the raw scroll-threshold flag.
                onSettledChanged = { atRest -> onDockedChanged?.invoke(atRest) },
                // See `pageLabel`'s own doc -- matches the shared hoisted
                // badge's own extraContent (Screens.kt, GarageScreen) so
                // the two instances' chrome measures to the same width and
                // the hand-off between them has nothing left to pop.
                extraContent = pageLabel?.let { label ->
                    {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) {
                Text(
                    v.name,
                    // headlineSmall -- matches PebbleShell's own real title
                    // base; see the hoisted badge's identical fix for the
                    // full reasoning.
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Wide expanded view: critical info in one column, pebbles in the other.
 *
 * `controls` and `pebbles` are held as `@Composable` lambdas (not directly
 * inlined) so [flipped] can freely swap which one renders in the left vs.
 * right [Column] without re-creating either column's content -- each
 * column's own [rememberScrollState] (`controlsScroll`/`pebblesScroll`) is
 * hoisted here rather than created inside `controls`/`pebbles` themselves,
 * so a scroll position sticks with its *content* across a flip rather than
 * with whichever physical column (left/right) currently renders it.
 * [HotspotSlot] lets one pebble be pinned into the info column permanently
 * (excluded from the normal reorderable pebble list via `exclude` above);
 * [HotSeatDrag] (provided via [LocalHotSeatDrag]) is the cross-column drag
 * state that lets a pebble be dragged from the scrolling list directly onto
 * that slot to pin it.
 *
 * A [TitleFlightOverlay] (built inline near the bottom, alongside the
 * flip-columns transition) fades in once CriticalContent's own HeroHeader --
 * the real hero photo card, shared with [VehicleDetailContent] -- has
 * scrolled out of view, same as every other surface. Tapping it scrolls
 * `controlsScroll` back to top: whichever column currently renders
 * `controls` (and therefore HeroHeader), regardless of which physical side
 * that is after a flip.
 */
@Composable
internal fun ExpandedCar(
    v: Vehicle,
    /** See VehicleDetailContent's `state` doc -- same source plumbing. */
    state: State<UiState>,
    vm: AppViewModel,
    flipped: Boolean,
) {
    // All derived, so this view recomposes when the hotspot section or the refresh flag
    // actually changes -- not on every UiState emission for every car.
    val hotspot by remember(v) {
        derivedStateOf {
            state.value.hotspotFor(v.vin)
                ?.takeIf {
                    it in state.value.sectionsFor(v) && state.value.isSectionAvailable(v, it)
                }
        }
    }
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    val hotDrag = remember { HotSeatDrag() }
    // Hoisted (not recreated on flip) so each column keeps its own scroll
    // position when the columns swap sides. controlsScroll always belongs
    // to whichever COLUMN currently renders `controls` (and therefore
    // CriticalContent's own HeroHeader), regardless of which physical side
    // (left/right) that currently is: the leftScroll/rightScroll pairing
    // below always keeps this same ScrollState paired with the same content
    // across a flip -- which is what makes it the right thing for the
    // badge's own tap-to-scroll-to-top.
    val controlsScroll = rememberScrollState()
    val pebblesScroll = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInsetPx = with(density) { topInset.toPx() }
    // remember(Unit) + SideEffect, not remember(topInsetPx) -- same
    // reasoning as VehicleDetailContent's and GarageScreen's identical
    // construction sites: an inset change alone shouldn't discard this
    // flight's accumulated dock/position state.
    val titleFlight = remember { HeroTitleFlight(topInsetPx, with(density) { TitleDockHysteresis.toPx() }) }
    SideEffect { titleFlight.topInsetPx = topInsetPx }
    // Same live-scroll-correction wiring as VehicleDetailContent's identical
    // construction site -- see HeroTitleFlight.inlinePos's own doc.
    // `controlsScroll`, not `pebblesScroll`: per this composable's own doc
    // just above, controlsScroll is always the ScrollState paired with
    // whichever column currently hosts CriticalContent's HeroHeader,
    // regardless of which physical side a flip has it on.
    SideEffect { titleFlight.scrollValuePx = { controlsScroll.value.toFloat() } }
    // CriticalContent's own HeroHeader is the real hero photo card here --
    // this view was NOT missing one the way the doc above used to claim;
    // CarHeaderRow's plain-text name and HeroHeader's own (on the photo)
    // were simply both visible at once, the exact duplicate-name bug fixed
    // everywhere else in the app. hideName = true here, matching
    // VehicleDetailContent's own CarHeaderRow call exactly: the floating
    // name is sourced from HeroHeader (via the ambient LocalHeroTitleFlight
    // below, which HeroHeader already knows how to use -- no changes needed
    // there), not from this plain header.
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state, onExpand = null, reserveEnd = false, hideName = true)
        CriticalContent(v, state, vm)
        HotspotSlot(v, hotspot, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        PebbleList(v, state, vm, exclude = setOfNotNull("summary", "controls", hotspot))
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag, LocalHeroTitleFlight provides titleFlight) {
    // Was hardcoded hideIndicator = true -- the same "grid-only" flag that
    // hid the pull-to-refresh spinner in the single-car view (fixed in
    // a944a91) also hid it here, in the expanded/wide dual-column detail
    // view, unconditionally. This is a single car's own detail screen, not
    // the multi-car grid the flag was meant for, so the real M3 Expressive
    // indicator should show here too.
    Refreshable(v, refreshing, vm) {
        Box(Modifier.fillMaxSize()) {
        // Animate the swap when the columns are flipped. Same spring the
        // expand/collapse transition (GarageScreen) and the collapsed
        // pager's own settle both use -- this was the one transition left
        // running on AnimatedContent's plain default spec instead of the
        // app's own spring language, and read noticeably flatter/more
        // mechanical next to those two right beside it.
        AnimatedContent(
            targetState = flipped,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                val floatSpec = spring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                val offsetSpec = spring<IntOffset>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                (slideInHorizontally(offsetSpec) { w -> dir * w / 4 } + fadeIn(floatSpec)) togetherWith
                    (slideOutHorizontally(offsetSpec) { w -> -dir * w / 4 } + fadeOut(floatSpec))
            },
            label = "flipColumns",
        ) { isFlipped ->
            val leftCol = if (isFlipped) pebbles else controls
            val rightCol = if (isFlipped) controls else pebbles
            val leftScroll = if (isFlipped) pebblesScroll else controlsScroll
            val rightScroll = if (isFlipped) controlsScroll else pebblesScroll
            // Inset spacers (not padding) so content scrolls *behind* the bars;
            // the leading spacer also clears the floating overlay buttons --
            // HeaderCornerGap + HeaderButtonSize (their real combined
            // footprint, 60dp), not the bare 52.dp this used to hardcode,
            // which let content peek up 8dp under the buttons' own bottom
            // edge. HeaderContentClearance adds real buffer on top of that
            // bare footprint -- without it, the column's own 12dp
            // `spacedBy` was the only thing standing between the button's
            // ambient glow/shadow halo and the first pebble/control's own
            // card shadow, and the two could visibly touch (e.g. the AI
            // summary pebble sitting right under the gear/flip buttons).
            val lead: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(topInset + HeaderCornerGap + HeaderButtonSize + HeaderContentClearance)) }
            // bottomInset + 132.dp, not +16.dp: same fix as VehicleDetailContent's
            // identical trailing spacer just above -- this dual-column view sits
            // under the same globally-floating search bubble (Screen.Garage), which
            // the old +16dp never accounted for.
            val trail: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(bottomInset + 132.dp)) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 960.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(leftScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { lead(); leftCol(); trail() }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rightScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { lead(); rightCol(); trail() }
                }
            }
        }
        // The floating name badge, once CriticalContent's own HeroHeader has
        // scrolled out of view (same hero-card source as VehicleDetailContent,
        // via the same ambient LocalHeroTitleFlight above).
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        TitleFlightOverlay(
            flight = titleFlight,
            // Clears GarageScreen's own back arrow (top-left, 12dp/48dp) --
            // it's always present whenever ExpandedCar is on screen.
            cornerX = 60.dp,
            cornerY = topInset + HeaderCornerGap,
            // Clears the flip-columns + gear buttons in the top-right.
            reserveEnd = 120.dp,
            maxWidth = screenWidth - 60.dp - 120.dp - 32.dp,
            // Same Unspecified-before-first-report fallback as VehicleDetailContent's own call site.
            // textColorOverride omitted -- same reasoning as VehicleDetailContent's
            // own call site: flight IS titleFlight, so TitleFlightOverlay reads
            // its live colour itself.
            onClick = { scope.launch { controlsScroll.animateScrollTo(0) } },
        ) {
            Text(
                v.name,
                // headlineSmall -- matches PebbleShell's own real title
                // base; see the hoisted badge's identical fix for the full
                // reasoning.
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


/**
 * A row of small fact chips (model/powertrain, "updated x ago"), with an
 * optional expand button -- [hideName] is true from every real caller now
 * ([VehicleDetailContent] and [ExpandedCar] both pass it, the car's name is
 * only ever drawn ONCE, live, on the hero photo card, so a second copy here
 * would be the same name twice on screen at once), which makes this row's
 * ENTIRE content the chips, not a name plus a caption underneath it.
 *
 * CenterVertically, not Top: with no name line above them any more, the
 * chips are the row's only content, sitting noticeably shorter than
 * [FloatingIcon]'s fixed 48dp -- top-aligning them against it left the icon
 * looming taller beside a strip of chips hugging the top edge, reading as
 * mismatched pieces rather than one row. Centering both against each other
 * is what makes it read as one consistent band, the same alignment this
 * exact icon-beside-content pairing uses everywhere else it isn't paired
 * with a taller title line of its own.
 *
 * [hideName] itself (and the name [Text] it would draw) stays as an escape
 * hatch rather than being deleted outright -- nothing currently calls it
 * false, but the option to draw a title-sized line above the chips again
 * (with its own top-aligned pairing) is cheap to keep and expensive to
 * reconstruct if a future caller needs it.
 */
@Composable
internal fun CarHeaderRow(
    v: Vehicle,
    /**
     * State SOURCE, not a snapshot -- see VehicleDetailContent's own `state` doc. Taking a
     * plain UiState here made this row's two callers (a page body, and the expanded view)
     * subscribe to EVERY UiState emission just to render a model name and a relative
     * timestamp. Both slices below are derived, so this row recomposes when the two strings
     * it actually shows change, and not when some other car's weather ticks.
     */
    state: State<UiState>,
    onExpand: (() -> Unit)?,
    reserveEnd: Boolean,
    hideName: Boolean = false,
) {
    val meta by remember(v) { derivedStateOf { "${v.model} · ${state.value.powertrainLabel(v)}" } }
    val fetchedAt by remember(v) { derivedStateOf { state.value.fetchedAt(v) } }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (reserveEnd) Modifier.padding(end = 52.dp) else Modifier),
        verticalAlignment = if (hideName) Alignment.CenterVertically else Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (!hideName) {
                Text(
                    v.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = if (hideName) 0.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetaChip(meta)
                LastUpdatedLabel(fetchedAt)
            }
        }
        if (onExpand != null) {
            // A proper floating chip (was a hard-to-see bare icon).
            FloatingIcon(Icons.Filled.Fullscreen, "Expand to full screen", onExpand)
        }
    }
}
