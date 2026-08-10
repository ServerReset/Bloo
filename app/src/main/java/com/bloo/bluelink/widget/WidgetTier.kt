package com.bloo.bluelink.widget

import androidx.compose.ui.unit.DpSize

/**
 * The layout tiers, smallest to largest, one per layout composable in
 * [CarWidget] -- 16 in total, most of them (the 7 below MEDIUM) further
 * doubled by [WidgetConfig.priority] into a genuinely distinct
 * info-vs-controls layout, so a widget dropped small has real variety to
 * grow into rather than one shape stretched to fit every size.
 *
 * Every tier reuses the same shared modules (HeaderRow, RingImage,
 * InfoStack, ActionButtons, ...) -- what changes tier to tier is
 * composition and proportion, not reinvented logic, which is what keeps 16
 * layouts maintainable as one set of building blocks rather than 16
 * independent implementations.
 *
 * This lives outside [CarWidget] deliberately: picking a tier is a pure
 * function of the measured size with no Glance or Android dependency, so
 * keeping it separate lets WidgetTierTest verify the whole size space
 * directly. That test exists because this map has had real holes in it --
 * see [tierFor].
 */
internal enum class WidgetTier {
    MICRO_TINY, MICRO,
    /** Extreme aspect ratios -- a long thin strip along one axis. See [tierFor]. */
    BANNER, RAIL,
    COMPACT_SQUARE,
    COMPACT_WIDE_NARROW, COMPACT_WIDE,
    COMPACT_TALL_NARROW, COMPACT_TALL,
    MEDIUM_SQUARE, MEDIUM_WIDE, MEDIUM_TALL,
    LARGE_SQUARE, LARGE_WIDE, LARGE_TALL,
    XL_WIDE, XL_TALL, XL_SQUARE,
}

/**
 * Picks the layout tier for an exact measured widget size.
 *
 * Ordered largest-first so the first match wins. The size gates are the
 * ones the original 6-tier system used (roughly: XL >= 5x5, LARGE >= 4-wide,
 * MEDIUM >= 2x2, the two COMPACT strips catching lopsided small sizes before
 * the tiny floor); each band then splits again by aspect ratio, so a wide
 * 5x5 and a tall 5x5 get differently proportioned layouts instead of the
 * same one letterboxed.
 *
 * The one rule worth stating outright, because breaking it is what caused
 * this function's last two bugs: every size must land on a tier with enough
 * room for what that tier draws. Falling through to [WidgetTier.MICRO] --
 * which is glyph-only, no text at all -- is correct ONLY for tiles barely
 * past the manifest's 40dp floor. WidgetTierTest enforces exactly that.
 *
 * ## The BANNER gate is GRID-driven, not aspect-driven
 *
 * Every other band above is still keyed on raw dp thresholds tuned against
 * screenshots, but BANNER exists for one specific, nameable shape: a widget
 * placed at [WidgetGrid.MIN_ROWS] -- ONE cell tall, [WidgetGrid.MIN_COLS] to
 * [WidgetGrid.MAX_COLS] cells wide (a "2x1" through "7x1" strip, in the
 * terms a user resizing the widget actually thinks in -- see [WidgetGrid]).
 * The old gate (`w >= 220 && h < 110 && w >= h * 3`) approximated that shape
 * with an aspect-ratio test, and the approximation had a real hole: it also
 * required `w >= 220`, so a 2- or 3-column-wide strip (nominally 110dp/180dp
 * wide) never matched it at all. `110x40` -- literally [WidgetGrid.MIN_COLS]
 * columns by one row -- fell through every other band and landed on
 * [WidgetTier.MICRO_TINY], the icon-only floor meant for tiles barely past
 * the manifest's absolute minimum, not a widget with 110dp of real width to
 * work with. `180x40` (3 columns) fared little better, landing on
 * [WidgetTier.COMPACT_WIDE_NARROW] -- a layout built around ~100-150dp of
 * vertical room for a ring beside a name column, not a 40dp-tall strip.
 * Both were reported as "looks broken/cut off at small widths" from real
 * device screenshots.
 *
 * The fix states the actual rule directly instead of approximating it:
 * ANY tile shorter than one compact cell ([WidgetTier.COMPACT_SQUARE]'s own
 * `short >= 80` floor, so the handoff between the two is gapless) is a
 * one-row strip and gets [WidgetTier.BANNER] -- which already scales its
 * content continuously by the REAL measured width (see [CarWidget.BannerLayout]),
 * so a 2-column-wide strip and a 7-column-wide one both render cleanly from
 * the same composable, just with a different button/info capacity. The
 * `w >= 80` floor keeps the true bottom corner (a tile close to the
 * manifest's 40dp absolute floor on both axes) on [WidgetTier.MICRO_TINY]
 * where it belongs -- swept in WidgetTierTest, which is what caught the
 * off-by-nothing gap an EARLIER version of this exact fix left between "no
 * longer counts as one row" and "wide enough for [WidgetTier.COMPACT_SQUARE]":
 * a `70..79`dp-wide sliver at `h` just above the row-1 ceiling matched
 * neither BANNER nor COMPACT_SQUARE and regressed to MICRO on a 2dp grow,
 * exactly the "growing must never demote" invariant this file exists to hold.
 */
internal fun tierFor(size: DpSize): WidgetTier {
    val w = size.width.value
    val h = size.height.value
    val short = minOf(w, h)
    val aspect = w / h
    return when {
        w >= 300f && h >= 300f -> when {
            aspect > 1.35f -> WidgetTier.XL_WIDE
            aspect < 0.74f -> WidgetTier.XL_TALL
            else -> WidgetTier.XL_SQUARE
        }
        w >= 240f && h >= 170f -> when {
            aspect > 1.2f -> WidgetTier.LARGE_WIDE
            aspect < 0.83f -> WidgetTier.LARGE_TALL
            else -> WidgetTier.LARGE_SQUARE
        }
        w >= 150f && h >= 150f -> when {
            aspect > 1.25f -> WidgetTier.MEDIUM_WIDE
            aspect < 0.8f -> WidgetTier.MEDIUM_TALL
            else -> WidgetTier.MEDIUM_SQUARE
        }
        // See the "BANNER gate is GRID-driven" doc above: any tile shorter
        // than one compact cell is a one-row strip, at ANY column count from
        // WidgetGrid.MIN_COLS up -- not just the wide ones the old aspect
        // gate happened to catch.
        h < 80f && w >= 80f -> WidgetTier.BANNER
        // RAIL is the narrow-tall mirror of BANNER, but is NOT grid-driven
        // the same way: WidgetGrid's own declared floor is 2 COLUMNS wide
        // (110dp nominal), which already lands cleanly on COMPACT_SQUARE /
        // COMPACT_TALL_NARROW / COMPACT_TALL (see WidgetTierTest's grid
        // sweep) -- there is no equivalent hole to close on this axis within
        // the 2..7 column, 1..7 row grid the widget actually offers. RAIL
        // stays a safety net for a genuinely narrower real launcher
        // configuration (sub-110dp) outside that declared range.
        h >= 220f && w < 110f && h >= w * 3f -> WidgetTier.RAIL
        // Loosened from the old w/h >= 150 gate -- a tile like 145x100 is
        // genuinely wide and had real unused room, but missed every band
        // above and fell all the way through to MICRO's icon-only
        // treatment. 120dp plus a slightly softer aspect gate catches it
        // without overlapping MEDIUM's own floor (MEDIUM requires BOTH
        // sides >= 150, so nothing here steals from it).
        w >= 120f && h < 150f && w >= h * 1.4f ->
            if (w >= 220f) WidgetTier.COMPACT_WIDE else WidgetTier.COMPACT_WIDE_NARROW
        h >= 120f && w < 150f && h >= w * 1.3f ->
            if (h >= 220f) WidgetTier.COMPACT_TALL else WidgetTier.COMPACT_TALL_NARROW
        // The catch-all for anything with real room that no band above
        // claimed, sitting between MICRO and the compact/medium bands: 80dp
        // on the short side is enough for a proper mini layout, not just an
        // icon.
        //
        // Deliberately NOT gated on aspect ratio. It used to be, and the
        // ceiling (1.33) didn't meet COMPACT_WIDE's floor (w >= 1.4h), so
        // every tile in between -- 190x140, 135x100, 20 such sizes on a 5dp
        // grid -- matched nothing and fell through to a lone icon despite
        // having room for the full layout. No aspect test is needed here
        // anyway: the wide and tall bands above have already claimed every
        // lopsided shape, so whatever reaches this line is square-ish by
        // construction (1.44 at the very worst).
        short >= 80f -> WidgetTier.COMPACT_SQUARE
        else -> if (short < 60f) WidgetTier.MICRO_TINY else WidgetTier.MICRO
    }
}
