@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Cover.kt's camera-bump-beside cluster, peeled out of Cover.kt (which kept the
 * tile/tile-face chrome): MAX_CUTOUT_FRACTION, EdgeDp and cutoutClearanceDp, the
 * CoverBand data class with COVER_BAND_MIN_W/H and coverCutoutBand, the
 * CoverBandSearchDock footprint, and the adaptive CoverScaffold itself.
 */

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Most of one dimension a display cutout may ever claim as clearance. A real
 *  notch or lens is a few percent; anything demanding more than this is a
 *  camera island being measured as though it were a punch-hole, and honouring
 *  it costs more screen than it protects. See cutoutClearanceDp. */
internal const val MAX_CUTOUT_FRACTION = 0.22f

/**
 * Per-edge camera-bump clearance in dp, computed from the display cutout rects for
 * ANY bump position. Returns how much each edge must be reserved so content flows
 * AROUND the punch-hole/bump instead of under it: (start, top, end, bottom) in dp,
 * zeros pre-API-28 or with no cutout.
 *
 * Why this exists alongside the native WindowInsets.displayCutout padding: on
 * Samsung flip COVER displays the OS frequently reports the front camera via
 * displayCutout.boundingRects (which is why the decorative ring positions
 * correctly) but exposes ZERO safeInset/displayCutout WINDOW insets for it — so
 * windowInsetsPadding(displayCutout) alone reserves nothing and content sits under
 * the bump (observed on the user's device). This reads the rects directly (each
 * call, not a remember(view) snapshot, so it reflects insets once dispatched).
 *
 * CRITICAL for a CORNER bump: PaddingValues insets a WHOLE edge, so reserving both
 * edges a corner bump touches removes an L-shaped chunk from two full sides — for a
 * bottom-right bump that's a full-HEIGHT right strip ~45% of the width, which
 * crushed every tile's content into the left half (observed: values wrapping
 * "Locke/d"/"Runnin/g", range clipped to "26…"). A corner bump only occludes its
 * corner, so this reserves only the edge with the SMALLER intrusion — for a
 * bottom-right bump that's the bump's HEIGHT (small), pushing content up just
 * enough to clear it while reclaiming the full width. A true single-edge cutout
 * still pads that one edge. Only a bump within [edgeBandPx] of an edge counts.
 */
internal data class EdgeDp(val start: Float, val top: Float, val end: Float, val bottom: Float)

@Composable
internal fun cutoutClearanceDp(): EdgeDp {
    val view = LocalView.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return EdgeDp(0f, 0f, 0f, 0f)
    val insets = view.rootWindowInsets
    val vw = view.width
    val vh = view.height
    // Remembered on everything it reads. This walks the cutout's bounding rects and does float
    // work per rect, and it used to do all of it on EVERY recomposition of whoever called it --
    // for a value that changes only when the window's insets or size actually change. The insets
    // object is the key rather than a flag, so a genuine change (rotation, cutout mode, a fold)
    // still recomputes, while the far commoner "something else recomposed" costs a map lookup.
    return remember(insets, vw, vh, density.density) {
        cutoutClearance(insets?.displayCutout, vw, vh, density)
    }
}

private fun cutoutClearance(
    cutout: android.view.DisplayCutout?,
    vw: Int,
    vh: Int,
    density: Density,
): EdgeDp {
    if (cutout == null) return EdgeDp(0f, 0f, 0f, 0f)
    if (vw <= 0 || vh <= 0) return EdgeDp(0f, 0f, 0f, 0f)
    val edgeBandPx = with(density) { 24.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    for (r in cutout.boundingRects) {
        val hIntr: Float? = when {
            r.left <= edgeBandPx -> r.right + margin
            vw - r.right <= edgeBandPx -> (vw - r.left) + margin
            else -> null
        }
        val vIntr: Float? = when {
            r.top <= edgeBandPx -> r.bottom + margin
            vh - r.bottom <= edgeBandPx -> (vh - r.top) + margin
            else -> null
        }
        // Corner bump: reserve only the smaller intrusion so the opposite full
        // dimension is reclaimed. Edge notch: reserve that one edge.
        val hOnly = hIntr != null && (vIntr == null || hIntr <= vIntr)
        val vOnly = vIntr != null && (hIntr == null || vIntr < hIntr)
        if (hOnly && r.left <= edgeBandPx) left = maxOf(left, hIntr!!)
        if (hOnly && vw - r.right <= edgeBandPx) right = maxOf(right, hIntr!!)
        if (vOnly && r.top <= edgeBandPx) top = maxOf(top, vIntr!!)
        if (vOnly && vh - r.bottom <= edgeBandPx) bottom = maxOf(bottom, vIntr!!)
    }
    // Clamp each edge to a fraction of its own dimension.
    //
    // The arithmetic above assumes the cutout is a small punch-hole, so the
    // clearance is measured from the FAR side of the rect: `r.right + margin`,
    // or `(vw - r.left) + margin`. That's right for a lens and catastrophic
    // for a flip cover screen, which reports its whole camera ISLAND as one
    // bounding rect -- an island starting halfway across yields a clearance of
    // half the display, and the content gets squeezed into the strip that's
    // left with the rest sitting empty. Reported from a real device.
    //
    // Past this cap the rect isn't a notch to dodge, it's the panel's shape,
    // and the honest response is to use the space rather than surrender it:
    // anything the hardware genuinely occludes is already excluded from the
    // window the app was given.
    val maxH = vw * MAX_CUTOUT_FRACTION
    val maxV = vh * MAX_CUTOUT_FRACTION
    return with(density) {
        EdgeDp(
            left.coerceAtMost(maxH).toDp().value,
            top.coerceAtMost(maxV).toDp().value,
            right.coerceAtMost(maxH).toDp().value,
            bottom.coerceAtMost(maxV).toDp().value,
        )
    }
}

/**
 * The strip of screen BESIDE the camera island, when there is one worth using.
 *
 * A flip cover reports its whole camera island as one display-cutout rect
 * hugging an edge, and every layout here so far has responded by reserving
 * that entire edge -- the island's height across the full width. But the
 * island only occupies part of that band; the rest of it is real, lit,
 * unoccluded screen that nothing was allowed to use. On a screen this small
 * that is a meaningful fraction of it.
 *
 * Returns the larger of the two free segments (left of the island or right of
 * it) as an absolute rect in dp from the window's top-left, or null when there
 * is no cutout, when the cutout doesn't hug a horizontal edge, or when what's
 * beside it is too small to hold anything worth putting there. Null is the
 * normal answer on a phone; this is a cover-screen affordance.
 */
/** @property nearCameraAtEnd true when this band's END edge (right, in LTR) is
 *  the one touching the camera island -- the island sits at the OTHER end of
 *  the row from this band's own start, so grouping content flush against the
 *  band's end is what actually reads as "next to the camera". False means the
 *  island touches the band's START edge instead, so content should group
 *  there. Threaded through explicitly rather than re-derived by every caller,
 *  since it depends on which of the two free segments (left/right of the
 *  island) [coverCutoutBand] picked. */
internal data class CoverBand(
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val nearCameraAtEnd: Boolean,
)

/** Below these a band is a sliver: too short for a legible line of text, or
 *  too narrow for a name plus a tap target. */
internal const val COVER_BAND_MIN_W = 84f
internal const val COVER_BAND_MIN_H = 26f

@Composable
internal fun coverCutoutBand(): CoverBand? {
    val view = LocalView.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val insets = view.rootWindowInsets
    val vw = view.width
    val vh = view.height
    // Remembered for the same reason as cutoutClearanceDp above, and it matters more here: one
    // of this function's callers is the search layer, which recomposes on every keystroke and
    // on every frame of a bubble drag.
    return remember(insets, vw, vh, density.density) {
        coverBand(insets?.displayCutout, vw, vh, density)
    }
}

private fun coverBand(
    cutout: android.view.DisplayCutout?,
    vw: Int,
    vh: Int,
    density: Density,
): CoverBand? {
    if (cutout == null) return null
    if (vw <= 0 || vh <= 0) return null
    val edgeBandPx = with(density) { 24.dp.toPx() }
    var best: CoverBand? = null
    for (r in cutout.boundingRects) {
        // Only a rect hugging the TOP or BOTTOM edge leaves a band beside it
        // that runs the other way. One hugging a side edge leaves a tall thin
        // column, which is not a place to put a name and a button.
        val hugsTop = r.top <= edgeBandPx
        val hugsBottom = vh - r.bottom <= edgeBandPx
        if (!hugsTop && !hugsBottom) continue
        val leftFree = r.left.toFloat()
        val rightFree = (vw - r.right).toFloat()
        val useLeft = leftFree >= rightFree
        val widthPx = if (useLeft) leftFree else rightFree
        val xPx = if (useLeft) 0f else r.right.toFloat()
        val band = with(density) {
            CoverBand(
                xDp = xPx.toDp().value,
                yDp = r.top.toFloat().toDp().value,
                widthDp = widthPx.toDp().value,
                heightDp = (r.bottom - r.top).toFloat().toDp().value,
                // useLeft picked the segment left of the island, so the
                // island -- and therefore "near camera" -- is at this band's
                // END (right); otherwise the island sits at its START.
                nearCameraAtEnd = useLeft,
            )
        }
        if (band.widthDp < COVER_BAND_MIN_W || band.heightDp < COVER_BAND_MIN_H) continue
        // Widest wins, on the theory that whatever we put there wants room.
        if (best == null || band.widthDp > best!!.widthDp) best = band
    }
    return best
}

/** Fixed footprint of the search dock this band reserves next to the camera
 *  island (see [CompactGarage]'s band Row and [SearchLayer]'s band-docked
 *  bubble) -- shared between the two files so the space one reserves is
 *  exactly the space the other draws into. */
internal val CoverBandSearchDock = 30.dp

/**
 * The adaptive cover-screen scaffold. Measures the REAL available space with
 * BoxWithConstraints and merges every inset source (nav bar, display cutout,
 * corner-safe camera-bump clearance, a small base gutter) into ONE contentPadding
 * per edge via max() — never additively — so a device that reports the bump both
 * as a window inset AND a boundingRect reserves it exactly once (this was the
 * "crammed into the left half" bug). Exposes [CoverMetrics] via [LocalCoverMetrics]
 * and clamps the subtree font scale so a huge system font can't overflow the tiny
 * face. The scaffold itself does NOT apply the padding — the tile region reads
 * metrics.contentPadding — so full-bleed siblings (rings, rail) stay full-bleed.
 */
@Composable
internal fun CoverScaffold(
    reserveRailGutter: Boolean,
    content: @Composable BoxWithConstraintsScope.(CoverMetrics) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutDir = LocalLayoutDirection.current
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        // Gentle base gutter off the shorter side so a small cover doesn't lose a
        // fixed chunk; extra end gutter when the tile-scrubber rail is shown.
        val gutterScale = (minOf(wDp, hDp) / 300f).coerceIn(0.8f, 1.2f)
        val baseSide = 10f * gutterScale
        val baseEnd = (if (reserveRailGutter) 22f else 10f) * gutterScale
        val cut = cutoutClearanceDp()
        val sys = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
        val sysStart = sys.calculateStartPadding(layoutDir).value
        val sysTop = sys.calculateTopPadding().value
        val sysEnd = sys.calculateEndPadding(layoutDir).value
        val sysBottom = sys.calculateBottomPadding().value
        // Single merged inset per edge — the whole point: max(), not sum.
        val padStart = maxOf(baseSide, cut.start, sysStart)
        val padTop = maxOf(baseSide, cut.top, sysTop)
        val padEnd = maxOf(baseEnd, cut.end, sysEnd)
        val padBottom = maxOf(12f * gutterScale, cut.bottom, sysBottom)
        val usableW = (wDp - padStart - padEnd).coerceAtLeast(0f)
        val usableH = (hDp - padTop - padBottom).coerceAtLeast(0f)
        val isTiny = minOf(usableW, usableH) < COVER_TINY_DP
        val metrics = CoverMetrics(
            widthDp = usableW,
            heightDp = usableH,
            isTiny = isTiny,
            contentPadding = PaddingValues(start = padStart.dp, top = padTop.dp, end = padEnd.dp, bottom = padBottom.dp),
        )
        // Coarse font-scale clamp for the whole cover subtree so a large system font
        // can't blow past the measured region (FittedText is the fine guard on top).
        val cappedFont = density.fontScale.coerceAtMost(if (isTiny) 1.15f else 1.3f)
        CompositionLocalProvider(
            LocalCoverMetrics provides metrics,
            LocalDensity provides Density(density.density, cappedFont),
        ) {
            content(metrics)
        }
    }
}
