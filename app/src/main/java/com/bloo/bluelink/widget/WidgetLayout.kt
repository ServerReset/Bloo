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
}
