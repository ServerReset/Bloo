@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.abs

// The long history of this constant and the keying scheme it goes with (kept because
// the reasoning matters more than the number, and because this has genuinely gone wrong
// in more than one distinct way):
//
// v1: virtual range = realCount x 1000, raw-index keys. Every DISTINCT virtual page a
// user ever swiped onto became its own permanent SaveableStateHolder entry, never
// reused, retained for the life of the pager's composition -- a real OOM (heap
// exhausted shortly after unlock) on a live device.
//
// v2: real-index keys (`key = { page -> wrap.real(page) }`), so every virtual copy of
// one real item shares ONE entry -- the actual fix for v1's leak. Reverted after
// crashing ("Key already used") on a real device: two simultaneously-composed virtual
// pages could map to the SAME real index whenever `realCount` was small relative to how
// many pages a pager holds alive at once (beyondViewportPageCount, perPage on the
// multi-column pager).
//
// v3: real-index keys again, this time with beyondViewportPageCount at every call site
// solving `perPage + 2*beyond <= realCount` -- provably collision-free in steady state.
// STILL crashed on a real device, immediately at cold start rather than after any
// swiping, which ruled out the steady-state formula itself. The actual cause: this
// composable let the underlying PagerState survive a `realCount` change (only the
// WrapPagerState wrapper was rebuilt), which is harmless for raw keys but not for
// real-index ones -- the pager's own `currentPage` kept whatever raw value it had under
// the OLD realCount, and reinterpreting that value's real()/beyond-window under a
// DIFFERENT realCount has no reason to land on a collision-free arrangement. realCount
// changes exactly at cold start (0 vehicles while nothing has loaded yet, then however
// many actually exist) -- reachable within the first couple of frames, hence no swiping
// needed. Reverted rather than patched further given two real-device failures already.
//
// v4 (current): real-index keys, PLUS reseeding the PagerState (via [key], not just
// rebuilding the wrapper) whenever [rememberWrapPager]'s own `resizeKey` changes -- not
// just `realCount`. v3's own fix only reseeded on a `realCount` change; it missed that
// GarageScreen's collapsed pager also recomputes `beyondViewportPageCount` from
// `perPage`, which changes on a fold/unfold or rotation WITHOUT `realCount` (the car
// count) changing at all -- the exact gap a foldable owner would hit on every fold,
// independent of and in addition to the cold-start case v3 fixed. Every call site now
// passes whatever of its OWN beyond-window inputs can change independently of realCount
// as `resizeKey`, so ANY of them reseeds instead of reinterpreting stale state.
//
// This still could not be verified against a real device in this sandbox. If "Key
// already used" recurs a fourth time, the next candidate is [recenterIfNearEdge]'s own
// jump (see its updated doc) -- the one operation real-index keying was never able to
// prove collision-free even in v3, just made astronomically rare by the multiplier
// below.
private const val WRAP_MULTIPLIER = 1_000_000
/** Recenter once the pager drifts within this many real-item-widths of
 *  either edge of the virtual range -- see [WRAP_MULTIPLIER]'s own doc. This
 *  only needs to be big enough that a single fling can't overshoot past it in
 *  one frame (it does not need to scale with [WRAP_MULTIPLIER] -- that
 *  constant is what controls how RARELY this triggers, this one just needs
 *  enough runway once it does). */
private const val RECENTER_MARGIN_CYCLES = 10
/** Max per-page scale shrink at full off-screen offset (floor 0.94). */
private const val PAGER_SHRINK = 0.06f

/**
 * Wraps a [PagerState] whose page space is a huge, FIXED virtual range, exposing the
 * real (modulo) index, a delta-jump that moves to a real index without an animated
 * fly-through across it, [WrapPagerState.keyFor] -- what actually makes the wrap feel
 * infinite, by letting every virtual copy of one real item reuse the same composition --
 * and [WrapPagerState.recenterIfNearEdge], an edge-of-range backstop that in practice
 * never fires. See [WRAP_MULTIPLIER]'s own doc for the full reasoning. [realCount] is
 * the number of real items the pages cycle through (cars, car-blocks, or tiles depending
 * on the site); when it is <= 1 there is no wrap and [WrapPagerState.real] is always 0.
 */
/**
 * Pure wrap arithmetic: the real item index a virtual page maps to.
 *
 * Extracted as an `internal` top-level function (not a member) so the modulo
 * trick is pinnable from a plain JVM test -- WrapPagerState's own member
 * forwards here, so the pin lives where the bugs would be. The double-modulo
 * form is deliberate: Kotlin's `%` keeps the NEGATIVE operand's sign, so a
 * page ever so slightly below the virtual midpoint (page 0 vs realCount 1m?)
 * -- i.e. a wrap pushed a page below zero by a snapshot/offset quirk -- would
 * map to a NEGATIVE real index and silently read a non-existent item. Adding
 * realCount once and modding again pulls every result into [0, realCount).
 */
internal fun wrapRealIndex(page: Int, realCount: Int): Int =
    if (realCount <= 1) 0 else ((page % realCount) + realCount) % realCount

/**
 * Pure wrap arithmetic: the nearest virtual page whose real index is [target],
 * without animating a long fly-through across the virtual range.
 *
 * [currentPage] is the page the pager is on, [pageCount] is the pager's total
 * virtual page count, [realCount] is the number of real items. When
 * [realCount] <= 1 there is only ever one item and [currentPage] is returned
 * unchanged. `delta` is computed FROM the real index of the CURRENT page (not
 * from [currentPage] itself), so jumping from any virtual copy of an item to
 * another item always takes the shortest real step; the result is clamped to
 * the pager's own [pageCount] - 1 because the virtual range is finite.
 */
internal fun wrapPageToward(currentPage: Int, pageCount: Int, realCount: Int, target: Int): Int {
    if (realCount <= 1) return currentPage
    val t = target.coerceIn(0, realCount - 1)
    val delta = t - wrapRealIndex(currentPage, realCount)
    return (currentPage + delta).coerceIn(0, pageCount - 1)
}

@Stable
internal class WrapPagerState(val pager: PagerState, val realCount: Int) {
    fun real(page: Int): Int = wrapRealIndex(page, realCount)

    /**
     * The key every pager call site's `key = { page -> wrap.keyFor(page) }` uses: the
     * REAL item index, not the raw virtual page. This is what makes a swipe back to a
     * car (or the Settings page) you've already visited reuse its existing composition
     * -- its scroll position, its expanded pebbles, its in-flight image loads -- instead
     * of building it from scratch every time.
     *
     * Provably collision-free in ordinary use: every call site computes its own
     * `beyondViewportPageCount` to satisfy `perPage + 2*beyond <= realCount`, which is
     * exactly the condition under which no two SIMULTANEOUSLY composed virtual pages can
     * ever resolve to the same real index. That invariant depends on `realCount` AND
     * whatever else feeds a call site's own `beyond` formula staying stable across the
     * composed window -- see [rememberWrapPager]'s `resizeKey` for why a call site whose
     * `perPage` can change independently (a fold/unfold, a rotation) must pass it there
     * too, not just rely on `realCount`.
     */
    fun keyFor(page: Int): Int = real(page)

    // settledReal was removed: zero readers. Both places that care about a SETTLE go through
    // `snapshotFlow { pager.settledPage }.collect { real(it) }` instead (GarageScreen's and
    // CompactGarage's pager-settle effects), because they need the settle as an EVENT, not as
    // a value to read during composition -- and reading a settled page in composition scope is
    // the exact subscription those pagers are built to avoid, so a convenience accessor for it
    // was never going to be the right shape.
    /** Jump so the currently-shown page maps to [target], picking the nearest
     *  virtual page in the current direction (no long fly-through). */
    suspend fun snapToReal(target: Int) {
        if (realCount <= 1) return
        val page = wrapPageToward(pager.currentPage, pager.pageCount, realCount, target)
        if (page != pager.currentPage) pager.scrollToPage(page)
    }

    /**
     * Call after every settle (never mid-drag -- see each call site's own
     * `snapshotFlow { pager.settledPage }` collector): if the pager has drifted within
     * [RECENTER_MARGIN_CYCLES] real-item-widths of either edge of the virtual range,
     * silently jump back to the page nearest the middle that maps to the SAME real
     * index -- so nothing visibly changes, but the pager has room to keep going in
     * either direction.
     *
     * With [keyFor] keying by real index, this is no longer what makes the wrap feel
     * infinite (ordinary swiping already reuses each real item's one composition,
     * unconditionally) or what bounds memory (a real-index key costs the same whether
     * the virtual range is 100 pages or 100 million). Its only remaining job is a
     * backstop against [WRAP_MULTIPLIER] eventually running out -- reachable only after
     * an amount of one-directional swiping no real session produces. See
     * [WRAP_MULTIPLIER]'s own doc for why that makes this call, when it does fire, a
     * different and far smaller risk than it used to be.
     */
    suspend fun recenterIfNearEdge() {
        if (realCount <= 1) return
        val margin = realCount * RECENTER_MARGIN_CYCLES
        val page = pager.currentPage
        val count = pager.pageCount
        // Comfortably inside both edges already -- nothing to do. Note this margin is
        // measured off the pager's OWN current pageCount, not a value cached at
        // creation -- though in practice a realCount/resizeKey change gets an entirely
        // fresh, freshly-seeded PagerState now (see rememberWrapPager's own doc), so
        // this method only ever runs against a pageCount that matches the CURRENT inputs.
        if (page in margin..(count - 1 - margin)) return
        val center = count / 2
        val target = center - (center % realCount) + real(page)
        if (target != page) pager.scrollToPage(target)
    }
}

/**
 * Creates a [WrapPagerState] seeded at the middle of the virtual range plus
 * [initialRealIndex], so the pager opens on that real item and can wrap in both
 * directions. Falls back to a plain single-page state when [realCount] <= 1.
 *
 * The underlying [PagerState] is recreated (via [key], not just re-wrapped) whenever
 * [realCount] OR [resizeKey] changes, landing back on a freshly-seeded, correctly
 * mid-range position -- the fix for a real "Key 0 was already used" crash that
 * real-index keying ([WrapPagerState.keyFor]) hit twice: letting the SAME PagerState
 * instance survive such a change (only the wrapper was rebuilt) is harmless under
 * raw-index keys (any page index is valid for any realCount) but breaks the real-index
 * invariant outright -- `currentPage` keeps whatever raw value it had under the OLD
 * inputs, and re-interpreting that value's `real()`/beyond-window under DIFFERENT ones
 * has no reason to land on a collision-free arrangement.
 *
 * [resizeKey] exists because `realCount` alone doesn't cover every input a call site's
 * own `beyondViewportPageCount` formula depends on: GarageScreen's collapsed pager also
 * derives it from `perPage` (how many cars fit side by side), which changes on a
 * fold/unfold or rotation WITHOUT `realCount` (the car count) changing at all -- a gap
 * the first version of this reseed fix missed entirely, since it only keyed on
 * `realCount` and that specific crash reproduced at cold start, not on a fold. A call
 * site whose own beyond-window math can shift independently of `realCount` MUST pass
 * that value here too; one that can't (a flat `beyondViewportPageCount = 0`, or a
 * `perPage` that's always exactly 1) can safely leave it null.
 *
 * The cost: a genuine realCount or resizeKey change now visibly resets scroll position
 * to the freshly-seeded middle instead of preserving mid-swipe state across it --
 * correct trade for turning a crash into, at most, a snap back to center on the rare
 * frame this actually happens.
 */
@Composable
internal fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0, resizeKey: Any? = null): WrapPagerState {
    val loop = realCount > 1
    val virtualCount = if (loop) realCount * WRAP_MULTIPLIER else realCount.coerceAtLeast(1)
    val start = (if (loop) virtualCount / 2 else 0) + initialRealIndex.coerceIn(0, (realCount - 1).coerceAtLeast(0))
    val pager = key(realCount, resizeKey) { rememberPagerState(initialPage = start) { virtualCount } }
    return remember(pager) { WrapPagerState(pager, realCount) }
}

/**
 * The shared per-page depth transform for the horizontal car pagers: a subtle
 * shrink proportional to how far this [page] is from the settled one, read ONLY
 * in the draw phase (via [graphicsLayer]) so a drag never triggers recomposition
 * of the page content. NOT applied to the vertical tile pager, which stays flat
 * by design.
 *
 * Scale only — no alpha, no translation. The matching fade this used to apply
 * was removed for a real
 * frame-rate reason, not a taste one. A graphicsLayer with alpha < 1 over content
 * that overlaps (a full car page: cards, their drop shadows, the aurora behind
 * them) makes Compose's default compositing strategy allocate a FULL-SCREEN
 * offscreen buffer and composite through it every frame. During a drag two pages
 * are live, so that's two full-screen buffers per frame purely to tint pages 20%
 * darker in transit. Transforms need no such buffer: scale is applied by the
 * RenderNode directly. Dropping the fade keeps the depth read and removes the
 * per-frame allocation entirely. (CompositingStrategy.ModulateAlpha would also
 * avoid the buffer, but it applies alpha per drawing op, so each pebble's own
 * drop shadow would show THROUGH the semi-transparent card above it — a grey
 * wash under every card mid-swipe. Not worth it for a 0.2 fade.)
 */
internal fun Modifier.pagerDepth(pager: PagerState, page: Int): Modifier = graphicsLayer {
    // NO translationX. A parallax drift was tried here and reverted from a
    // device screenshot: a pager page is full-bleed and its neighbours are
    // composed (beyondViewportPageCount = 1), so ANY translation toward the
    // viewport pulls the next car's card into the edge of the screen and
    // leaves it there AT REST -- a sliver of another car down both sides,
    // which is also live to touch. Depth on a full-bleed pager can only come
    // from transforms that shrink or push AWAY, never pull in.
    //
    // Offset formula matches the Compose Pager docs' own sample --
    // (currentPage - page) + currentPageOffsetFraction. This file previously
    // had (page - currentPage) + offset, which negates the fraction's
    // contribution and made the shrink slightly asymmetric mid-drag: one
    // neighbour shrank a touch more than the other for the same finger
    // position.
    val off = abs((pager.currentPage - page).toFloat() + pager.currentPageOffsetFraction)
        .coerceIn(0f, 1f)
    scaleX = 1f - off * PAGER_SHRINK
    scaleY = 1f - off * PAGER_SHRINK
}

/** Screen height (dp) below which the phone gets the compact cover-screen
 *  layout -- a folding phone's small outer display (Galaxy Z Flip's ~260-280dp
 *  square cover, for instance), not a full unfolded/candybar phone screen.
 *  GarageScreen and LockOverlay used to each pick their own cutoff (570 vs
 *  440), so a screen sized between them got the compact UI on one but the
 *  full-size one on the other for the exact same physical device -- one
 *  shared threshold instead. Width is checked separately (see isCompactCoverScreen)
 *  so a wide-but-short screen (a tablet in landscape) doesn't false-positive. */
const val COVER_SCREEN_HEIGHT_DP = 570
const val COVER_SCREEN_WIDTH_DP = 600

/** True on a folding phone's compact cover screen; false on a full phone,
 *  foldable-unfolded, or tablet screen. See [COVER_SCREEN_HEIGHT_DP]. */
@Composable
internal fun isCompactCoverScreen(): Boolean {
    val windowInfo = LocalWindowInfo.current
    return with(LocalDensity.current) {
        windowInfo.containerSize.width.toDp() < COVER_SCREEN_WIDTH_DP.dp &&
            windowInfo.containerSize.height.toDp() < COVER_SCREEN_HEIGHT_DP.dp
    }
}
