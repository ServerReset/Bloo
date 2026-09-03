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
import androidx.compose.runtime.LaunchedEffect
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
 * The car's name is real, visible content inside the hero card's own title slot -- it just
 * fades itself out as it scrolls above the status bar, handing off to [FloatingTitlePill]'s own
 * corner badge. See that function's own doc.
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
    // Reports this page's own live docked state (with hysteresis -- see
    // FloatingTitle.docked's own doc) up to the caller on every change. GarageScreen uses this
    // to know which page's pill is currently docked, for the page-dots collision check -- see
    // that call site's own doc.
    onDockedChanged: ((Boolean) -> Unit)? = null,
    // True unless this page is a merely pre-composed pager neighbour, not the currently settled
    // one -- see FloatingTitlePill's own `settled` doc (including why this is a `State<Boolean>`,
    // not a plain `Boolean`). GarageScreen is the only caller that ever passes a non-default one.
    settled: State<Boolean> = AlwaysSettled,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val topInsetPx = with(density) { topInset.toPx() }
    // remember(Unit) + SideEffect, not remember(topInsetPx) -- a keyed remember here would
    // discard all accumulated docked state on every inset change instead of just picking up the
    // new inset value.
    val floatingTitle = remember { FloatingTitle(with(density) { TitleDockHysteresis.toPx() }) }
    SideEffect { floatingTitle.topInsetPx = topInsetPx }
    LaunchedEffect(floatingTitle.docked.value) { onDockedChanged?.invoke(floatingTitle.docked.value) }
    // Narrowed, not `state.value.refreshing`: a bare read here would subscribe this whole page
    // -- all three of them live at once in the pager -- to every UiState emission.
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    Refreshable(v, refreshing, vm, hideIndicator = hideIndicator) {
        CompositionLocalProvider(LocalFloatingTitle provides floatingTitle) {
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
        // Every page now owns its own docked pill -- no more shared/hoisted badge handed
        // between pages (see TitleFlight.kt's own doc on the redesign). This is safe to leave
        // composed for every page, settled or pre-composed neighbour alike: FloatingTitlePill's
        // own AnimatedVisibility only pays for the glass chrome while actually docked or
        // transitioning.
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        FloatingTitlePill(
            title = floatingTitle,
            cornerX = 16.dp,
            cornerY = topInset + HeaderCornerGap,
            // Clears the top-right FloatingIcon slot (the expand button in grid columns, the
            // gear on the standalone route) -- 12dp outer padding + 48dp icon + a little air.
            reserveEnd = 72.dp,
            maxWidth = screenWidth - 16.dp - 72.dp - 32.dp,
            onClick = { scope.launch { scroll.animateScrollTo(0) } },
            settled = settled,
        ) {
            Text(
                v.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
 * A [FloatingTitlePill] (built inline near the bottom, alongside the
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
    // remember(Unit) + SideEffect, not remember(topInsetPx) -- an inset change alone shouldn't
    // discard this title's accumulated docked state.
    val titleFlight = remember { FloatingTitle(with(density) { TitleDockHysteresis.toPx() }) }
    SideEffect { titleFlight.topInsetPx = topInsetPx }
    // CriticalContent's own HeroHeader is the real hero photo card here --
    // this view was NOT missing one the way the doc above used to claim;
    // CarHeaderRow's plain-text name and HeroHeader's own (on the photo)
    // were simply both visible at once, the exact duplicate-name bug fixed
    // everywhere else in the app. hideName = true here, matching
    // VehicleDetailContent's own CarHeaderRow call exactly: the floating
    // name is sourced from HeroHeader (via the ambient LocalFloatingTitle
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
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag, LocalFloatingTitle provides titleFlight) {
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
        // via the same ambient LocalFloatingTitle above).
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        FloatingTitlePill(
            title = titleFlight,
            // Clears GarageScreen's own back arrow (top-left, 12dp/48dp) --
            // it's always present whenever ExpandedCar is on screen.
            cornerX = 60.dp,
            cornerY = topInset + HeaderCornerGap,
            // Clears the flip-columns + gear buttons in the top-right.
            reserveEnd = 120.dp,
            maxWidth = screenWidth - 60.dp - 120.dp - 32.dp,
            onClick = { scope.launch { controlsScroll.animateScrollTo(0) } },
        ) {
            Text(
                v.name,
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
