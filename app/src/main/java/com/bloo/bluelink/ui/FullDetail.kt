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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlin.math.max

// --- Full detail ----------------------------------------------------------


/**
 * Single-column car view (phones, and each column of the grid). Everything
 * scrolls together in one [Column] inside [Refreshable] (header row, then
 * the reorderable [PebbleList]).
 *
 * The car's name is real, visible content inside the hero card's own title slot -- there is no
 * floating corner badge that takes over once it scrolls out of view. There used to be (and,
 * before that, a floating "Settings" badge too) -- both removed as unwanted UI, along with the
 * whole TitleFlight/FloatingTitlePill/dock-on-scroll system that existed only to drive them.
 */
@Composable
internal fun VehicleDetailContent(
    v: Vehicle,
    /**
     * State SOURCE (vm.state.value from a collectAsStateWithLifecycle()), consistent with
     * PebbleList/SinglePebble:
     * per-use state.value reads keep this page and its pebble rows stable against
     * emissions that don't touch the values they actually read.
     */
    state: State<UiState>,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    // reserveTopForDots and reserveHeaderEnd were removed the same way: each reserved room for
    // a floating button that no longer exists (the pager dots, then CarHeaderRow's own
    // Fullscreen icon -- see its doc), each reduced to "no longer used but kept for API
    // compatibility. Always false/[value]" once its button went, and there is no API to be
    // compatible with -- this is an `internal` composable with exactly one caller in the same
    // module, which passed the literal constant either time.
    /** See [CarHeaderRow]'s own doc -- forwarded through so its chips can blur. */
    hazeState: HazeState? = null,
) {
    com.bloo.bluelink.data.StartupTrace.once("vehicle-detail-${v.vin}", "VehicleDetailContent composing for ${v.name}")
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    // Narrowed, not `state.value.refreshing`: a bare read here would subscribe this whole page
    // -- all three of them live at once in the pager -- to every UiState emission.
    val refreshing by remember { derivedStateOf { state.value.refreshing } }
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }, hazeState = hazeState) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Inset spacer (not padding) so content scrolls *behind* the bars --
            // topInset alone, no extra breathing room, so the name sits right at
            // the status bar's own edge instead of noticeably below it.
            Spacer(Modifier.height(topInset))
            CarHeaderRow(v, state, hazeState = hazeState)
            // Single column has no separate "hero info column" to pin anything into --
            // that's a wide/dual-column-only concept (ExpandedCar's own HotspotSlot,
            // with its drag-to-pin secondary slot and "Add a pebble" call to action).
            // pinHotspot = false here lets "controls" (lock/unlock/lights/horn) --
            // and any pebble the user pinned to the secondary slot -- render as
            // ordinary members of this reorderable list instead, landing in their
            // normal DEFAULT_SECTIONS position (summary, then update, then controls,
            // ...) -- i.e. right below the hero card, with no separate pin/CTA UI of
            // any kind. Previously this called HotspotSlot directly, same as
            // ExpandedCar -- which did put "controls" back on screen, but ALSO
            // surfaced the wide-layout-only "Add a pebble" drag target above the hero
            // card, on every phone, reported directly as the layout looking wrong.
            PebbleList(v, state, vm, pinHotspot = false, onExpand = onExpand)
            // Reserves exactly as much room as the floating search bubble (SearchLayer, mounted
            // globally for Screen.Garage -- see Screens.kt's `searchable` gate) actually needs,
            // read live off its own reported bounds -- not a flat guessed height. A guess here
            // (the old +132dp) is exactly what let the last pebble's own trailing chevron sit
            // directly under the bubble, visibly cut off by it, once confirmed from a real
            // screenshot; see searchBarClearance's own doc for why a fixed constant can't stay
            // right. bottomInset itself is folded into the live value (the bar sits above it),
            // so it is not added again here.
            Spacer(Modifier.height(searchBarClearance(fallback = bottomInset + 132.dp)))
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
 * No floating corner name badge here any more -- removed as unwanted UI, along with the whole
 * floating-title system. The car's name is real, visible content on CriticalContent's own
 * HeroHeader, same as [VehicleDetailContent]; it simply scrolls off with the rest of that column
 * once it's flipped out of view, like any other content.
 */
@Composable
internal fun ExpandedCar(
    v: Vehicle,
    /** See VehicleDetailContent's `state` doc -- same source plumbing. */
    state: State<UiState>,
    vm: AppViewModel,
    flipped: Boolean,
    /** Collapse back to the multi-column grid -- surfaced as the hero card's own header
     *  action (see [HeroHeader]'s `expandAction`) instead of a separate floating back
     *  button, now that this view has one already for the opposite direction. */
    onCollapse: () -> Unit,
    /** See [CarHeaderRow]'s own doc -- forwarded through so its chips can blur. */
    hazeState: HazeState? = null,
) {
    // All derived, so this view recomposes when the hotspot sections or the refresh flag
    // actually changes -- not on every UiState emission for every car.
    val hotspots by remember(v) {
        derivedStateOf {
            state.value.hotspotFor(v.vin).filter {
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
    // CriticalContent's own HeroHeader is the real hero photo card here --
    // this view was NOT missing one the way the doc above used to claim;
    // CarHeaderRow's plain-text name and HeroHeader's own (on the photo)
    // were simply both visible at once, the exact duplicate-name bug fixed
    // everywhere else in the app. hideName = true here, matching
    // VehicleDetailContent's own CarHeaderRow call exactly.
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state, hazeState = hazeState)
        CriticalContent(v, state, vm, onCollapse = onCollapse)
        HotspotSlot(v, hotspots, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        // Pinned pebbles in the hotspot are excluded from the reorderable list
        PebbleList(v, state, vm, exclude = setOf("summary"))
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag) {
    Refreshable(refreshing, onRefresh = { vm.refreshStatus(v) }, hazeState = hazeState) {
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
            val topSpacerHeight = topInset + HeaderCornerGap + HeaderButtonSize + HeaderContentClearance
            val bottomSpacerHeight = searchBarClearance(fallback = bottomInset + 132.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    // Swipe left/right to flip which column ("controls" vs "pebbles") renders on
                    // which side -- replaces the screen-level "Flip columns" floating icon that
                    // used to be the only way to do this. Keyed on `flipped` so a drag that
                    // crosses the threshold and flips doesn't immediately re-trigger off the
                    // rest of the SAME continuous gesture (isFlipped's own AnimatedContent swap
                    // restarts pointerInput's coroutine, zeroing the accumulator).
                    .pointerInput(isFlipped) {
                        var dragTotal = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = { dragTotal = 0f },
                            onDragCancel = { dragTotal = 0f },
                        ) { change, dragAmount ->
                            change.consume()
                            dragTotal += dragAmount
                            if (kotlin.math.abs(dragTotal) > size.width / 4f) {
                                vm.setColumnsFlipped(!isFlipped)
                                dragTotal = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
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
                    ) {
                        Spacer(Modifier.height(topSpacerHeight))
                        leftCol()
                        Spacer(Modifier.height(bottomSpacerHeight))
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rightScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Spacer(Modifier.height(topSpacerHeight))
                        rightCol()
                        Spacer(Modifier.height(bottomSpacerHeight))
                    }
                }
            }
        }
    }
    }
}


/**
 * A row of small fact chips (model/powertrain, "updated x ago"). The car's name
 * is only ever drawn ONCE, live, on the hero photo card, so a second copy here
 * would be the same name twice on screen at once -- this row's entire content
 * is the chips, not a name plus a caption underneath it.
 *
 * The "expand to full screen" button that used to float here (and "back to
 * all cars"/"flip columns" as separate screen-level floating icons) all moved
 * onto the hero card's own header instead -- see [HeroHeader]'s `expandAction`
 * -- so this row is chips only now, CenterVertically since there is no longer
 * a taller icon beside them to align against.
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
    /** The screen's own [HazeState] (see GarageScreen's own `hazeSource` doc) -- threaded
     *  through so these chips get a real backdrop blur instead of the flat-tint fallback
     *  every [GlassSurface] uses with no hazeState in scope. */
    hazeState: HazeState? = null,
) {
    val meta by remember(v) { derivedStateOf { "${v.model} · ${state.value.powertrainLabel(v)}" } }
    val fetchedAt by remember(v) { derivedStateOf { state.value.fetchedAt(v) } }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetaChip(meta, hazeState = hazeState)
                LastUpdatedLabel(fetchedAt, hazeState = hazeState)
            }
        }
    }
}
