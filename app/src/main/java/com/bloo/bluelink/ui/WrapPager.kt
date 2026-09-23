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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.abs

// The long, twisty history of this constant (kept because the reasoning matters more
// than the number): it started at 1000, "big enough that a user could never swipe to
// the edge in one sitting" -- with every pager call site keying pages by the RAW
// virtual index, that meant every DISTINCT virtual page a user ever swiped onto became
// its own permanent entry in Compose's SaveableStateHolder (a whole SettingsScreen, a
// whole VehicleDetailContent's remembered state), never reused, retained for the life
// of the pager's composition. Reported directly as a real OOM (heap exhausted shortly
// after unlock, a trivial 32-byte allocation the one that finally failed).
//
// Real-index keying (`key = { page -> wrap.real(page) }`, letting every virtual copy of
// the same real item share ONE SaveableStateHolder entry -- the actual fix for that
// leak, not just a smaller ceiling on it) was tried twice and reverted twice, both times
// after crashing ("Key already used") on a real device rather than in this sandbox
// (which can't run the app or attach a debugger to see the live composition tree). Both
// attempts kept [WRAP_MULTIPLIER] small (30, then this constant at 80) in the belief
// that the virtual range itself had to stay small to bound the leak -- which was true
// for RAW keys, but is irrelevant for real-index ones: real-index keys bound the
// SaveableStateHolder to exactly [realCount] live entries, however large the virtual
// range is, because Compose only ever holds saved state per DISTINCT KEY, not per
// virtual page. That misdiagnosis is what actually caused both crashes: keeping the
// range small meant [WrapPagerState.recenterIfNearEdge] -- the jump back toward the
// middle once the pager nears either edge -- had to fire often (every realCount x
// (WRAP_MULTIPLIER - RECENTER_MARGIN_CYCLES) swipes), and that jump is the one moment
// real-index keying is NOT provably collision-free: it lands on a virtual page sharing
// its real index with the page just left, and for one transient frame Compose can still
// have the OUTGOING page's beyond-window neighbours (real indices matching the
// incoming page's own beyond-window, since realCount/beyond are unchanged by the jump)
// composed alongside the incoming ones -- an all-but-guaranteed collision on the one
// operation genuinely being exercised.
//
// The fix isn't a smarter jump; it's making the jump essentially never happen. With
// real-index keys the memory argument for a small range is gone, so this constant can
// go enormous instead: 1,000,000 makes the safe zone (one-directional swipes before a
// recenter is even reachable) realCount x ~1,000,000 -- for two cars, two million
// swipes in the same direction, i.e. not reachable by a human in a real session, while
// [Int] happily holds the resulting page count (realCount x this, comfortably under
// 2^31 for any realistic realCount) with no overflow risk. recenterIfNearEdge stays as
// a defence-in-depth backstop against a pathological/scripted case that swipes that far
// -- if it's ever actually reached, the same transient-collision risk described above
// is still technically live, but "after two million consecutive swipes" is a
// fundamentally different risk profile than "after 60."
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
 * fly-through across it, [keyFor] -- what actually makes the wrap feel infinite, by
 * letting every virtual copy of one real item reuse the same composition -- and
 * [recenterIfNearEdge], an edge-of-range backstop that in practice never fires. See
 * [WRAP_MULTIPLIER]'s own doc for the full reasoning. [realCount] is the number of real
 * items the pages cycle through (cars, car-blocks, or tiles depending on the site); when
 * it is <= 1 there is no wrap and [real] is always 0.
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
     * of building it from scratch every time, which is what made the wrap feel like a
     * loop of fresh pages rather than genuinely infinite scroll through a small, fixed
     * set of screens.
     *
     * Provably collision-free in ordinary use: every call site computes its own
     * `beyondViewportPageCount` to satisfy `perPage + 2*beyond <= realCount`, which is
     * exactly the condition under which no two SIMULTANEOUSLY composed virtual pages can
     * ever resolve to the same real index (see each call site's own derivation). The one
     * operation that isn't provably safe under this key is [recenterIfNearEdge]'s own
     * jump -- see [WRAP_MULTIPLIER]'s doc for why that's now made operationally
     * unreachable instead of guarded against directly.
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
    val windowInfo = LocalWindowInfo.current
    return with(LocalDensity.current) {
        windowInfo.containerSize.width.toDp() < COVER_SCREEN_WIDTH_DP.dp &&
            windowInfo.containerSize.height.toDp() < COVER_SCREEN_HEIGHT_DP.dp
    }
}
