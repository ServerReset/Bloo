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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.abs

// Was 1000, "big enough that a user could never swipe to the edge in one
// sitting" -- which meant every DISTINCT virtual page a user ever swiped onto
// became its own permanent composition (a whole SettingsScreen, a whole
// VehicleDetailContent) with no explicit `key` to let Compose reuse one across
// wraps, retained for the life of the process. Reported directly as a real
// OOM (heap exhausted shortly after unlock, a trivial 32-byte allocation the
// one that finally failed). A first fix (`key = { page -> wrap.real(page) }`,
// letting every virtual copy of the same real item share one identity) was
// tried and reverted -- it collided ("Key \"1\" was already used") because
// TWO simultaneously-composed virtual pages can map to the SAME real index
// whenever `realCount` is small relative to how many pages a pager holds
// alive at once (beyondViewportPageCount, and perPage on the multi-column
// pager) -- not a HorizontalPager internals mystery, just this fix skipping
// that collision check the first time around. A follow-up mitigation just
// shrunk this constant to 30, capping the leak's ceiling instead of removing
// it -- still real growth, just bounded, and a real (if very unlikely) dead
// end once a user actually swiped that far. The key IS back now (see
// [WrapPagerState.keyFor]), this time gated on the exact inequality that
// makes it collision-free, with every call site falling back to the old
// unkeyed behaviour below that threshold.
//
// This is the actual fix: recenter ([WrapPagerState.recenterIfNearEdge],
// called after every settle) silently jumps back toward the middle once the
// pager drifts within [RECENTER_MARGIN_CYCLES] real-item-widths of either
// edge -- the destination page has the IDENTICAL real index, so the content
// shown doesn't change, and the pager keeps having room to go. This is what
// actually bounds the leak (the old huge-range trick just hoped nobody
// swiped far enough to need bounding at all) -- but recentering jumps to a
// DIFFERENT virtual page than the one just left, which USED TO BE a distinct
// Compose slot (fresh scroll position, fresh enter animations, whatever
// per-composition state that page holds), so a recenter was not perfectly
// invisible the way sliding to a genuinely neighbouring page is. See
// [WrapPagerState.keyFor]: every call site now keys its pages by REAL index
// (where it's provably safe to), so a recenter jump reuses the very same
// composition instead of creating a fresh one -- the jump is now actually
// invisible, not just rare, which is most of why this constant no longer
// needs to be huge to hide it.
//
// First shipped with this constant at 10, "so recentering can only ever
// retain a small number of compositions" -- but that shrank the SAFE ZONE
// (the number of one-directional swipes before recentering triggers) down
// with it, especially for the common case of a small `realCount` (one or two
// cars): with realCount=2 and this at 10, recentering could trigger after
// as few as 3-4 swipes in one direction, which is exactly the "swipe the
// same way repeatedly to see if it loops" motion anyone testing this feature
// would make -- reported directly as "very obvious when it doesn't infinite
// scroll and goes around."
//
// Raising it all the way to 1000 traded that bug for a real OutOfMemoryError
// (a heap-exhaustion crash on a live device, not a hypothetical): with
// realCount=2 that let the pager retain up to 1000x2 = 2000 distinct virtual
// pages before recentering ever became reachable, and each one is a whole
// VehicleDetailContent/SettingsScreen subtree, not a lightweight row --
// nowhere near the "bounded and self-correcting" ceiling that number was
// asserted to be when 1000 was chosen; it was never actually measured
// against a device heap. 80 is the compromise: still a wide safe zone
// (realCount x (40 - RECENTER_MARGIN_CYCLES) one-directional swipes before a
// recenter is reachable -- 60 for two cars, comfortably past any "does this
// loop" test), while capping the worst case at 80x[realCount] retained
// pages instead of 1000x -- an order of magnitude smaller ceiling for
// whatever in a page's subtree scales with distinct-pages-ever-visited.
// This is a bound on the ceiling, not a fix for a confirmed root cause: this
// sandbox can't run the app or take a heap dump, so the exact mechanism
// retaining those pages (composition-slot retention, an image cache, a
// per-page coroutine that's never cancelled) hasn't been isolated. If the
// OOM recurs, that measurement -- not another guess at this constant -- is
// the next step.
private const val WRAP_MULTIPLIER = 80
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
 * Wraps a [PagerState] whose page space is a small, FIXED virtual range,
 * exposing the real (modulo) index, a delta-jump that moves to a real index
 * without an animated fly-through across it, and [recenterIfNearEdge] --
 * called after every settle -- which is what actually makes the wrap feel
 * infinite (see [WRAP_MULTIPLIER]'s own doc for why this replaced a much
 * bigger fake range). [realCount] is the number of real items the pages
 * cycle through (cars, car-blocks, or tiles depending on the site); when it
 * is <= 1 there is no wrap and [real] is always 0.
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
    val currentReal: Int get() = real(pager.currentPage)

    /**
     * The [HorizontalPager]/[VerticalPager] `key` for [page], given that pager's own
     * [beyondViewportPageCount] and [perPage] (how many real items one page-width actually
     * shows at once -- 1 for every wrap-pager except the multi-column collapsed garage pager).
     * Keying by [real] index (not raw virtual page) lets Compose treat every virtual copy of
     * the same real item as ONE composition slot, so a [recenterIfNearEdge] jump reuses it
     * (its scroll position, its `remember` state, no fresh enter animation) instead of
     * composing a brand-new one -- the recenter becomes genuinely invisible instead of merely
     * bounded, and this pager stops accumulating a new distinct composition per virtual page
     * ever visited (the actual leak an earlier fix here mistook a bigger multiplier for
     * solving; see [WRAP_MULTIPLIER]'s own doc).
     *
     * Only safe once [realCount] is large enough that no two pages this pager can EVER hold
     * simultaneously composed map to the same real index. A contiguous run of `perPage +
     * 2 * beyondViewportPageCount` virtual pages is composed at once (the multi-column
     * pager's own `beyondViewportPageCount` was already sized by solving exactly this
     * inequality for a real, reported crash -- see its call site's own doc), and a contiguous
     * run of consecutive integers only visits every residue mod [realCount] at most once
     * while its length is <= [realCount] (pigeonhole). Below that threshold, falls back to
     * the raw page index -- the pre-existing, uncrashable behaviour -- since a small
     * [realCount] already keeps [WRAP_MULTIPLIER]'s own worst-case retained-page count small
     * regardless.
     *
     * STRICT `>`, confirmed necessary the hard way: loosening this to `>=` (briefly, in this
     * session) shipped a real, reported "Key already used" crash at the exact boundary
     * (composed count == realCount) a second time, on a DIFFERENT realCount than the first
     * one this file ever hit that crash on -- so the composed window really can exceed the
     * nominal `perPage + 2*beyondViewportPageCount` by one page in practice (almost certainly
     * Compose's Pager needing both the settling-from and settling-to page's own neighbourhoods
     * alive during a settle/recenter transition, not just whichever one is "current" at any
     * single instant) -- not a one-off arithmetic bug at a single call site fixable by tuning
     * that call site's own beyond formula. `>=` is not safe; do not loosen this again without
     * first eliminating whatever transiently composes that extra page, which nothing in this
     * codebase has actually done.
     *
     * The EARLIER loosening was chasing a real cost, though: requiring strict `>` while every
     * call site kept independently picking its own `beyondViewportPageCount` (typically capped
     * at 1 "for smoothness") meant [realCount] frequently landed EXACTLY on `perPage +
     * 2*beyond` for the everyday case of 2-3 cars -- permanently disabling real-index keying
     * there, so every [recenterIfNearEdge] jump (routine, recurring during ordinary swiping)
     * composed a brand-new page instead of reusing the existing one, visibly restarting every
     * pebble's own load/animate-in. The actual fix is [safeBeyond]: call sites now DERIVE their
     * beyondViewportPageCount from the margin this key needs, instead of picking one
     * independently and hoping it happens to fit -- so the two can never drift apart, and
     * real-index keying stays available for exactly the small-realCount cases that used to
     * lose it, at the cost of pre-warming one fewer neighbour only where [realCount] is too
     * small to afford it.
     *
     * [realCount] <= 1 is excluded explicitly: [real] collapses to the CONSTANT 0 for every
     * page whenever realCount <= 1, so keying by it would give literally every virtual page the
     * same key. [recenterIfNearEdge] itself already no-ops for realCount <= 1, so there is no
     * reuse to gain from real-keying there anyway -- the raw page index is both safe and
     * sufficient.
     */
    fun keyFor(page: Int, beyondViewportPageCount: Int, perPage: Int = 1): Any =
        if (realCount > 1 && realCount > perPage + 2 * beyondViewportPageCount) real(page) else page

    /**
     * The largest `beyondViewportPageCount` up to [desired] that still leaves [keyFor]'s own
     * strict margin satisfied for THIS pager's [realCount] and [perPage] -- i.e. the largest
     * `beyond` with `perPage + 2*beyond < realCount`. Callers should derive their actual
     * [HorizontalPager]/[VerticalPager] `beyondViewportPageCount` from this (passing the same
     * value to `keyFor`) instead of picking one independently: that guarantees the two can
     * never drift apart, which is what let real-index keying get permanently, silently
     * disabled for small-but-common [realCount] values before this existed (see [keyFor]'s own
     * doc). [desired] is still honoured whenever [realCount] is large enough to afford it (a
     * bigger garage keeps its full neighbour pre-warm); only a small [realCount] trades away
     * some of that pre-warming for real-index keying's own bigger win (invisible recentering,
     * no per-recenter recomposition) instead of losing the latter entirely.
     */
    fun safeBeyond(desired: Int, perPage: Int = 1): Int {
        if (realCount <= 1) return 0
        val maxSafe = (realCount - perPage - 1) / 2
        return desired.coerceIn(0, maxSafe.coerceAtLeast(0))
    }
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
     * `snapshotFlow { pager.settledPage }` collector): if the pager has
     * drifted within [RECENTER_MARGIN_CYCLES] real-item-widths of either edge
     * of the (small, fixed) virtual range, silently jump back to the page
     * nearest the middle that maps to the SAME real index -- so nothing
     * visibly changes, but the pager has room to keep going in either
     * direction. This -- not a huge fake page count -- is what makes the
     * wrap feel genuinely infinite while keeping the number of distinct
     * virtual pages this pager can ever compose small and constant, instead
     * of growing with how long or how far someone swipes. See
     * [WRAP_MULTIPLIER]'s own doc for the full reasoning.
     */
    suspend fun recenterIfNearEdge() {
        if (realCount <= 1) return
        val margin = realCount * RECENTER_MARGIN_CYCLES
        val page = pager.currentPage
        val count = pager.pageCount
        // Comfortably inside both edges already -- nothing to do. Note this
        // margin is measured off the pager's OWN current pageCount, not a
        // value cached at creation, so it stays correct even if realCount
        // (and therefore pageCount) changes later -- see rememberWrapPager's
        // `remember(pager, realCount)` for why a realCount change re-seeds
        // this whole wrapper anyway, landing back at a fresh center.
        if (page in margin..(count - 1 - margin)) return
        val center = count / 2
        val target = center - (center % realCount) + real(page)
        if (target != page) pager.scrollToPage(target)
    }
}

/**
 * Creates a [WrapPagerState] seeded at the middle of the virtual range plus
 * [initialRealIndex], so the pager opens on that real item and can wrap in
 * both directions. Falls back to a plain single-page state when [realCount]
 * <= 1. The underlying [PagerState] survives recomposition; the wrapper is
 * re-created only when [realCount] changes (it holds no scroll state itself).
 */
@Composable
internal fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0): WrapPagerState {
    val loop = realCount > 1
    val virtualCount = if (loop) realCount * WRAP_MULTIPLIER else realCount.coerceAtLeast(1)
    val start = (if (loop) virtualCount / 2 else 0) + initialRealIndex.coerceIn(0, (realCount - 1).coerceAtLeast(0))
    val pager = rememberPagerState(initialPage = start) { virtualCount }
    return remember(pager, realCount) { WrapPagerState(pager, realCount) }
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
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp < COVER_SCREEN_WIDTH_DP && cfg.screenHeightDp < COVER_SCREEN_HEIGHT_DP
}
