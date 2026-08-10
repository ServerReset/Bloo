package com.bloo.bluelink.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The widget's pure layout-decision layer: given a tier, a size and the text scale, it decides
 * how a tall tier divides its column between the button stack, the hero (ring/glyph), the info
 * rows and the optional map. No Glance imports, so it is unit-testable directly.
 *
 * WHY THIS EXISTS. The three tall tiers (RAIL, COMPACT_TALL_NARROW, COMPACT_TALL) run the
 * identical reservation sequence -- [Scale.tallColumn] for the name + button reservation, then
 * [Scale.tallSplit] over what's left -- differing ONLY in a handful of per-tier constants
 * (whether there's a name, the button overhead/trailing-gap, the button cap, the info-row cap).
 * Those constants were hardcoded in each composable AND copied again into WidgetScaleTest's
 * sweep; the test file's own comments record that this mirror had already gone stale once
 * (COMPACT_TALL's overhead modelled as 12dp while the composable used 20dp). Moving the
 * per-tier numbers here, behind one [tallPlan] call that both the composables and the sweep
 * invoke, is what finally makes them impossible to drift: there is one definition of what each
 * tier reserves, and the test asserts on the same object the widget renders from.
 *
 * The composables keep OWNING the render tree (which slots, in what order, with which spacers);
 * this only owns the space budget they render into. That division is deliberate -- see the
 * package's layout notes.
 */
internal object WidgetLayout {

    /** The per-tier constants the tall-column reservation differs on. Everything else about
     *  the three tall tiers is identical, which is the whole reason they can share [tallPlan]. */
    private data class TallSpec(
        /** Whether the tier draws a car name above the hero (RAIL does not). */
        val hasName: Boolean,
        /** Extra height reserved under the name beyond the text line itself (the narrow tier's
         *  4dp gap; 0 where the name line is the whole reservation). */
        val nameExtra: Dp,
        /** What [Scale.maxStackedButtons] must keep free: the trailing gap after the last
         *  button PLUS any forced spacer the tier always emits before the button row. */
        val buttonOverhead: Dp,
        /** The gap after the last button, counted inside the reserved button zone. */
        val buttonTrailingGap: Dp,
        /** Upper bound on stacked buttons (RAIL/COMPACT_TALL: all configured; the narrow
         *  tier caps at 4 so a tall-but-narrow tile doesn't become a button ladder). */
        val buttonCapCeiling: Int,
        /** Info-row ceiling handed to [Scale.tallSplit] (0 = the tier shows no rows). */
        val capRows: Int,
    )

    private val RAIL = TallSpec(
        hasName = false, nameExtra = 0.dp,
        buttonOverhead = 16.dp, buttonTrailingGap = 8.dp,
        buttonCapCeiling = Int.MAX_VALUE, capRows = 0,
    )
    private val COMPACT_TALL_NARROW = TallSpec(
        hasName = true, nameExtra = 4.dp,
        buttonOverhead = 8.dp, buttonTrailingGap = 4.dp,
        buttonCapCeiling = 4, capRows = 1,
    )
    private val COMPACT_TALL = TallSpec(
        hasName = true, nameExtra = 0.dp,
        buttonOverhead = 20.dp, buttonTrailingGap = 12.dp,
        buttonCapCeiling = Int.MAX_VALUE, capRows = 4,
    )

    /** The spec for a tall [tier], or null if [tier] is not one of the three tall tiers. */
    private fun specFor(tier: WidgetTier): TallSpec? = when (tier) {
        WidgetTier.RAIL -> RAIL
        WidgetTier.COMPACT_TALL_NARROW -> COMPACT_TALL_NARROW
        WidgetTier.COMPACT_TALL -> COMPACT_TALL
        else -> null
    }

    /** The resolved budget for a tall tier: the name-line height it reserved (0 when the tier
     *  shows no name), the button stack it decided on, and the [Scale.TallSplit] over what was
     *  left. The composable renders exactly these numbers; the sweep asserts on them. */
    data class TallPlan(
        val nameHeight: Dp,
        val column: Scale.TallColumn,
        val split: Scale.TallSplit,
    ) {
        val buttonCount: Int get() = column.buttonCount
        val buttonZone: Dp get() = column.buttonZone
    }

    /**
     * Plan a tall tier's column. [tier] must be RAIL, COMPACT_TALL_NARROW or COMPACT_TALL
     * (throws otherwise -- callers dispatch on the tier, so a wrong one is a programming error).
     *
     * @param actionCount how many actions the car/config actually resolved to; the button cap
     *   is `min(actionCount, tier ceiling)`, so a tier never reserves for buttons it won't draw.
     * @param wantMap whether a map is eligible. The three tall tiers pass false today; the
     *   parameter keeps the split honest if that ever changes.
     */
    fun tallPlan(
        tier: WidgetTier,
        size: DpSize,
        textScale: Float,
        actionCount: Int,
        wantMap: Boolean = false,
    ): TallPlan {
        val spec = specFor(tier)
            ?: error("WidgetLayout.tallPlan called with non-tall tier $tier")
        val nameHeight = if (spec.hasName) {
            Scale.lineHeight(Scale.titleSp(size).value, textScale) + spec.nameExtra
        } else {
            0.dp
        }
        val column = Scale.tallColumn(
            size,
            nameHeight = nameHeight,
            buttonOverhead = spec.buttonOverhead,
            buttonTrailingGap = spec.buttonTrailingGap,
            buttonCap = minOf(actionCount, spec.buttonCapCeiling),
        )
        val split = Scale.tallSplit(size, column.heroRoom, capRows = spec.capRows, textScale = textScale, wantMap = wantMap)
        return TallPlan(nameHeight, column, split)
    }

    // ---- Square tiers (MEDIUM_SQUARE / LARGE_SQUARE / XL_SQUARE) ----------------
    //
    // These share the ringRoom -> squareSplit sequence, differing only in the row-width
    // threshold (below which rows stack under the ring instead of beside it), the spacer
    // allowance handed to ringRoom, and the info-row cap. Unlike the tall tiers they also
    // depend on RUNTIME config (showHeader/showFooter) and whether a map bitmap exists, so
    // those are parameters rather than part of the static spec.

    /** Per-tier square constants. `hasFooter` is whether the tier draws a footer AT ALL
     *  (MEDIUM_SQUARE never does); the actual footer visibility still ANDs the config flag. */
    private data class SquareSpec(
        val rowWidth: Dp,
        val spacerAllowance: Dp,
        val capRows: Int,
        val hasFooter: Boolean,
    )

    private val MEDIUM_SQUARE = SquareSpec(rowWidth = 140.dp, spacerAllowance = 16.dp, capRows = 3, hasFooter = false)
    private val LARGE_SQUARE = SquareSpec(rowWidth = 220.dp, spacerAllowance = 20.dp, capRows = 4, hasFooter = true)
    private val XL_SQUARE = SquareSpec(rowWidth = 260.dp, spacerAllowance = 24.dp, capRows = 4, hasFooter = true)

    private fun squareSpecFor(tier: WidgetTier): SquareSpec? = when (tier) {
        WidgetTier.MEDIUM_SQUARE -> MEDIUM_SQUARE
        WidgetTier.LARGE_SQUARE -> LARGE_SQUARE
        WidgetTier.XL_SQUARE -> XL_SQUARE
        else -> null
    }

    /** The row-width threshold at/above which a square tier lays its info rows BESIDE the ring
     *  (RingWithContent's `minRowWidth`), exposed so the composable's RingWithContent call and
     *  [squarePlan]'s sideBySide decision read the same number — they must agree or the rows are
     *  budgeted for a band they don't actually share. */
    fun squareRowWidth(tier: WidgetTier): Dp =
        (squareSpecFor(tier) ?: error("WidgetLayout.squareRowWidth called with non-square tier $tier")).rowWidth

    /** The resolved square-tier budget: the [Scale.SquareSplit] over the tier's ringRoom. The
     *  composable renders `split.ring`/`split.rows`/`split.map`/`split.ringRoom`; the sweep
     *  asserts the assembled column fits. */
    data class SquarePlan(val split: Scale.SquareSplit)

    /**
     * Plan a square tier's ring/rows/map split. [tier] must be MEDIUM/LARGE/XL_SQUARE.
     *
     * @param showHeader/showFooter the live config flags — ringRoom subtracts the header/footer
     *   only when shown. (MEDIUM_SQUARE has no footer at all, so its showFooter is forced false.)
     * @param wantMap whether a map bitmap exists to place (MEDIUM_SQUARE never shows one).
     */
    fun squarePlan(
        tier: WidgetTier,
        frame: Scale.Frame,
        showHeader: Boolean,
        showFooter: Boolean,
        wantMap: Boolean,
    ): SquarePlan {
        val spec = squareSpecFor(tier)
            ?: error("WidgetLayout.squarePlan called with non-square tier $tier")
        val room = Scale.ringRoom(frame, showHeader, spec.hasFooter && showFooter, spec.spacerAllowance)
        val split = Scale.squareSplit(
            frame.size,
            room = room,
            capRows = spec.capRows,
            textScale = frame.textScale,
            wantMap = wantMap,
            sideBySide = frame.size.width >= spec.rowWidth,
        )
        return SquarePlan(split)
    }

    // ---- Wide bar-hero tiers (LARGE_WIDE / XL_WIDE) -----------------------------
    //
    // Both run a header + footer + full-width BarHero + info rows + map + button row down one
    // column, sized by ringRoom(spacers) -> tallSplit. They differ only in the spacer allowance
    // (three explicit inter-slot Spacers: 3x10dp on LARGE, 3x14dp on XL) and the info-row cap.
    // MEDIUM_WIDE is deliberately NOT here -- its bar branch has a different sequence (a
    // restAfterBar split at capRows 2), so folding it in would obscure rather than share.

    private data class WideSpec(val spacers: Dp, val capRows: Int)

    private val LARGE_WIDE = WideSpec(spacers = 30.dp, capRows = 4)
    private val XL_WIDE = WideSpec(spacers = 42.dp, capRows = WidgetInfoField.ALL.size)

    private fun wideSpecFor(tier: WidgetTier): WideSpec? = when (tier) {
        WidgetTier.LARGE_WIDE -> LARGE_WIDE
        WidgetTier.XL_WIDE -> XL_WIDE
        else -> null
    }

    /** The resolved wide bar-hero budget: the [Scale.TallSplit] over the tier's ringRoom (the
     *  hero size is `split.ring`, fed to BarHero; rows/map come from the same split). */
    data class WidePlan(val split: Scale.TallSplit)

    /** Plan a wide bar-hero tier (LARGE_WIDE / XL_WIDE). ringRoom subtracts header/footer per the
     *  live config flags; capRows and the spacer allowance are the per-tier spec. */
    fun wideBarPlan(
        tier: WidgetTier,
        frame: Scale.Frame,
        showHeader: Boolean,
        showFooter: Boolean,
        wantMap: Boolean,
    ): WidePlan {
        val spec = wideSpecFor(tier)
            ?: error("WidgetLayout.wideBarPlan called with non-wide-bar tier $tier")
        val ringRoom = Scale.ringRoom(frame, showHeader, showFooter, spec.spacers)
        val split = Scale.tallSplit(frame.size, ringRoom, capRows = spec.capRows, textScale = frame.textScale, wantMap = wantMap)
        return WidePlan(split)
    }
}
