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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.isGen5W
import kotlin.math.abs
import kotlin.math.max

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
    state: State<UiState>,
    vm: AppViewModel,
    /** Shared hazeState instance for glass effects. Must match the pager's hazeSource
     *  so floating elements overlay this screen with a real blur of the tile content
     *  behind them, not a flat-tint fallback. */
    hazeState: HazeState,
) {
    // The State itself now, not rememberUpdatedState(value): the caller no longer reads
    // state.value to call this, so this composable is out of every emission's invalidation
    // set except where one of the derived reads below actually changes.
    val stateSource = state
    val isGen5W by remember(v.vin) { derivedStateOf { state.value.isGen5WEffective(v) } }
    // Cover-screen tiles follow the same order the user arranged the pebbles in
    // (state.sectionsFor). "summary" maps to the always-present "main" tile;
    // "controls" has no cover tile so it falls away. If summary was somehow
    // dropped, "main" is prepended so the cover screen always has a home tile.
    // NOTE: "charge" is deliberately NOT gated on the car having a battery any more. A gas
    // car renders the fuel pebble under that same "charge" section (SinglePebble routes
    // "charge" to FuelPebble when hasBattery is false), so gating it on hasBattery left a gas
    // car's flip cover with NO energy tile at all -- no fuel level or range -- while the phone
    // showed one. Reported as the kind of odd cover/phone divergence this pass is for.
    // A derived computation, not remember(keys): the keys used to be exactly the state slices
    // this predicate reads, but reading them in the composition body to form the key still
    // subscribed CompactCar to every emission. As a derived state it only invalidates when the
    // resulting tile LIST differs, which is what the key list was really asking for.
    val tiles by remember(v.vin) {
        derivedStateOf {
            val sel = state.value
            sel.sectionsFor(v).mapNotNull { section ->
                when (section) {
                    "summary" -> "main"
                    else -> section.takeIf { it in CompactKnownTiles && sel.isSectionAvailable(v, it) }
                }
            }.let { ordered -> if ("main" in ordered) ordered else listOf("main") + ordered }
        }
    }
    // Infinite wrap-around: start in the middle of a huge virtual range and map
    // each virtual page back onto a real tile with modulo. FLAT tiles -- unlike the three
    // horizontal car pagers this VerticalPager gets NO pagerDepth: a full-bleed page that
    // shrinks as it leaves reads as depth between CARS, but between sections of one car it just
    // makes the panel feel like it is wobbling.
    val vWrap = rememberWrapPager(tiles.size)
    val vPager = vWrap.pager
    // See the car pager's own matching effect above for why: recentering after
    // every settle is what makes this wrap feel genuinely infinite (never a real
    // dead end) while keeping the virtual page range small and bounded forever.
    LaunchedEffect(vPager, tiles.size) {
        snapshotFlow { vPager.settledPage }.collect { vWrap.recenterIfNearEdge() }
    }
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
                    } finally { edgeTraceHolding = false }
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
            // The cost is fine here: a flip phone's cover
            // is a small DISPLAY, not a small device. It runs the same flagship SoC as the main
            // screen. And a tile is cheaper than it was -- no per-tile SubcomposeLayout and no
            // hero block any more.
            //
            // Capped the same way the car pager beside this one is (see its own doc): a flat
            // beyond=1 pre-warms a neighbour on EACH side, so a car with very few visible
            // sections (tiles.size <= 2, e.g. most sections hidden) would compose the same
            // tile twice at once through the exact wrap-collision this whole fix targets.
            // Most cars have well more than 2 tiles, so this is a rare-but-real edge rather
            // than the everyday case the horizontal car pager's own fix is.
            beyondViewportPageCount = ((tiles.size - 1) / 2).coerceIn(0, 1),
            // Raw page index as the key (never the modulo real item) -- see GarageScreen's
            // own key comment for the crash this avoids.
            key = { page -> page },
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
                        // CoverMainTile and CoverActionBar now take the State and do their
                        // own derived reads, so this call site no longer reads state.value
                        // (and no longer needs the remember(keys) { state } slice that used
                        // to keep an unrelated emission -- a location update on another car,
                        // a log line -- from recomposing this tile).
                        CoverMainTile(v, state, vm)
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
        // Vertical page dots removed: user requested no page indicators
        // No refresh indicator badge here any more -- removed app-wide after several
        // rounds of real, reported visual bugs. The edge-trace ring above still tracks
        // the pull gesture itself.
    }
}
