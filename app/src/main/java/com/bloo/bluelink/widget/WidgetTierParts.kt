package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width

/**
 * The two decisions and one arrangement every tier layout shares.
 *
 * [controlsPriority] answers "did the user ask for buttons instead of info on
 * a small tile"; [controlsMiniStatusEdge] sizes the small status mark that
 * still appears alongside those buttons; [RingWithContent] is the ring-beside-
 * content-or-stacked-above-it pairing that most tiers are built from.
 *
 * These stayed together, and stayed out of both tier files, precisely because
 * both files use them: putting them with either half would have made one
 * group of tiers reach across into the other's file for a shared primitive.
 */

/** True when this widget should show controls instead of info/ring at the
 *  MICRO/COMPACT_WIDE/COMPACT_TALL tiers -- see [WidgetConfig.priority]'s
 *  doc comment for why this only applies below MEDIUM. */
internal fun controlsPriority(render: Render) = render.config.priority == WidgetConfig.PRIORITY_CONTROLS

/**
 * Budget-fraction status-ring/glyph edge for a controls-priority layout.
 *
 * Every controls-priority tier used to replace its ENTIRE content with
 * ActionButtons and return -- so "small sizes: Controls" meant a tile
 * showing literally nothing about the car itself: no charge, no lock
 * state, not even which car it was. Reported from a batch of real
 * device screenshots across every controls-priority tier.
 *
 * A small fraction of whatever budget the caller has, capped low: the
 * buttons stay the point of a controls-priority tile, this is a glance
 * at the car's own state alongside them, not a second ring module
 * competing for the same room. Returns 0 (via [Scale.ring]'s own floor)
 * when even that fraction can't read.
 */
internal fun controlsMiniStatusEdge(size: DpSize, budget: Dp, fraction: Float = 0.32f, cap: Dp = 40.dp): Dp =
    Scale.ring(size, minOf(budget * fraction, cap))

/**
 * A status ring/glyph beside a block of other content when there's
 * width to spare, or the ring stacked on top of that content when
 * there isn't -- the shared "should this go vertical instead" decision
 * for every tier that pairs the ring with something else (an info
 * stack, a header + buttons column, ...), so it's one reusable building
 * block instead of a hand-picked Row hard-coded per tier. [minRowWidth]
 * is how much width THIS specific pairing needs to read comfortably
 * side by side; below it, a vertical stack keeps both pieces legible
 * instead of squeezing them into a cramped row.
 *
 * [content] is handed the width its own slot ACTUALLY gets -- which in
 * the side-by-side case is only what's left after the ring, not the
 * whole tile. Everything inside it (info rows, a header, buttons) needs
 * that real number to decide whether its own text/buttons fit, because
 * measuring against the full tile width would let a label sail past
 * the wrap threshold and get clipped in a column half that wide.
 */
@Composable
internal fun RingWithContent(
    modifier: GlanceModifier,
    minRowWidth: Dp,
    ringWidth: Dp,
    /** Needed for [Scale.innerWidth] -- the padded content box, which is what `content`
     *  actually gets. Cannot be derived from LocalSize alone: rootPadding depends on the
     *  frame's pill corner. */
    frame: Scale.Frame,
    ring: @Composable () -> Unit,
    content: @Composable (Dp) -> Unit,
) {
    // TWO widths, deliberately, and they are not interchangeable.
    //
    // `tileWidth` is the raw tile and stays the BRANCH test: minRowWidth was tuned against
    // the raw width, so testing the padded width instead would silently flip the row/column
    // choice on every tile sitting near the threshold -- a layout change dressed up as a bug
    // fix.
    //
    // `inner` is the padded content box, and it is what `content` is handed. That was the
    // bug: content received the raw width, so a label was judged against space the root
    // padding had already taken. This function's own KDoc states the requirement -- "needs
    // that real number to decide whether its own text/buttons fit, because measuring against
    // the full tile width would let a label sail past the wrap threshold and get clipped in a
    // column half that wide" -- and the code handed over the full tile width.
    val tileWidth = LocalSize.current.width
    val inner = Scale.innerWidth(frame)
    if (tileWidth >= minRowWidth) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) { ring() }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                content((inner - ringWidth - 12.dp).coerceAtLeast(24.dp))
            }
        }
    } else {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            ring()
            Spacer(GlanceModifier.height(8.dp))
            content(inner)
        }
    }
}
