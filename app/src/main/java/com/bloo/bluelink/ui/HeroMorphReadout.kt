package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

@Composable
internal fun HeroMorphReadout(
    data: ChargeReadout,
    t: Float,
    modifier: Modifier = Modifier,
    /** Start inset for the NUMBERS row only, so it can sit after the car name while the bar
     *  below still spans the card. Zero for [ChargeFuelBar], which has no name beside it. */
    numbersStart: Dp = 0.dp,
    /** Reports where the NUMBERS row landed, for the travelling overlay. See
     *  [HeroCollapsedNumbers.onPositioned] -- same idea at the other end. */
    onNumbersPositioned: (LayoutCoordinates) -> Unit = {},
    /** True once the overlay draws the real numbers. This row keeps measuring and
     *  positioning so it stays a valid anchor, and stops painting. */
    numbersHoisted: Boolean = false,
    /** The status line's own fade, on its own delayed clock -- see the caller's
     *  `statusAlpha` for why it isn't just `t`. */
    statusAlpha: Float = t,
) {
    val type = MaterialTheme.typography
    // Real type steps, lerped -- not a graphicsLayer scale -- and the reason is DEPENDENT
    // LAYOUT, not glyph quality. This Column's height must genuinely grow as the numbers do:
    // the state line below has to be pushed down and the card's content has to reserve the
    // space. `graphicsLayer` explicitly does not affect that -- it "does not change the
    // measured size or placement", so siblings would stay put and the scaled digits would
    // draw OVER them. [HeroCollapsedStats] (now deleted) could scale precisely because nothing depended on its
    // size; this cannot.
    //
    // So this pays a real cost, knowingly: a `Text` measures through the SINGLE-SLOT
    // ParagraphLayoutCache, so a per-frame font size misses it every frame. Bounded to two
    // Text nodes in one layout pass, with no lookahead pass doubling it.
    //
    // (The previous claim here -- that "a scaled 45sp glyph is soft at every intermediate
    // frame" -- was not a verified mechanism, and it contradicted the since-deleted
    // HeroCollapsedStats' comment
    // arguing the reverse. If this ever needs to become free, the move is Compose's own:
    // sharedBounds with scaleToBounds + skipToLookaheadSize, which scales a layout measured
    // once. That was tried and reverted for a different reason -- the lookahead cost on every
    // pager page -- documented in this function's KDoc above.)
    // The type scale for the numbers lives in [HeroNumbers] now, with the numbers
    // themselves. It was duplicated here, and a second copy of a lerped type scale
    // is exactly the drift this rework exists to remove -- the two would have had to
    // be kept in step by hand for the anchor to keep describing what the overlay
    // draws.
    Column(
        // NO alpha ramp. This node is present and fully visible in BOTH states, which is the
        // whole point: one bar and one pair of numbers that move and change shape, rather than
        // two copies crossfading. The `t * t` fade that was here existed only to hide this copy
        // while a second one was drawn in the header.
        modifier,
        verticalArrangement = Arrangement.spacedBy(lerp(2.dp, 6.dp, t)),
    ) {
        // Fades IN on the back half only. The collapsed numbers are drawn by the header's own
        // title Row (see HeroCollapsedNumbers), because that is the only way to guarantee they sit
        // on the name's line -- so this copy must be invisible until that one has gone, or both
        // are on screen at once and the morph reads as a double image.
        //
        // A plain Row, not BoxWithConstraints -- that was tried (to hand HeroNumbers its own
        // measured width so this anchor doesn't render left-packed for however many frames it
        // takes the travelling overlay to hoist) and reverted for the same reason ChargeBar's
        // own KDoc already warns about a few hundred lines down: BoxWithConstraints is
        // SUBCOMPOSITION, found there once already "while chasing dropped frames in the hero's
        // collapse". This Row is present and re-measured on every frame of the whole heroT
        // transition (only its alpha changes, never its existence), so a subcomposition here
        // paid that cost every frame the card was opening or closing, times however many pager
        // pages keep this composed at once -- reported as the animation "dropping frames" after
        // that change landed.
        //
        // fillMaxWidth achieves the same thing for free: this Row already stretches to the
        // readout's full available width, and HeroNumbers' own inner Row can be told to do the
        // same (fillWidth = true) rather than being handed a measured Dp -- both end up
        // constrained to the identical width, but the fillMaxWidth version costs one ordinary
        // layout pass instead of a second, nested composition pass.
        Row(
            Modifier
                .padding(start = numbersStart)
                // fillMaxWidth so this anchor reports the readout's real span rather
                // than its own wrapped content width. The overlay lerps to that
                // width, and it is what puts the range against the right edge; a
                // wrapped anchor would have left it packed beside the percentage.
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = if (numbersHoisted) 0f
                    else ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                }
                .onGloballyPositioned(onNumbersPositioned),
            verticalAlignment = Alignment.Bottom,
        ) {
            // ONE definition of the numbers, shared with the collapsed anchor and the
            // travelling overlay -- see [HeroNumbers]. This row's job is now only to
            // be MEASURED: it lays the numbers out where the expanded card wants them
            // and reports that, and the overlay draws the copy anyone actually sees.
            //
            // Rendering the same composable here rather than a hand-kept twin is what
            // makes the anchor trustworthy: if this drew a different size from the
            // overlay, the interpolation would be between two points that describe
            // different things, and the numbers would drift as the card opened.
            //
            // fillWidth = true, not a measured `width`: this Row is already fillMaxWidth,
            // so HeroNumbers' own inner SpaceBetween Row just needs to be told to match it
            // (see [HeroNumbers]'s own `fillWidth` param) rather than being handed the number
            // back through a subcomposition.
            HeroNumbers(data, t, fillWidth = true, statusAlpha = statusAlpha)
        }
        // Plug-in hybrid's fuel tank: expanded only, same reasoning as the state line. Fades
        // in over the back half of the morph so it does not compete with the numbers growing.
        //
        // The pump icon is here because dropping it was a second regression in my first pass
        // at this morph -- ChargeFuelBar has always drawn one, and "Fuel 40%" on its own reads
        // as another battery figure in a card that is otherwise all battery.
        data.fuelPct?.takeIf { statusAlpha > 0.01f }?.let { fuelPct ->
            // LocalContentColor, not colorScheme.onSurfaceVariant -- the SAME fault the
            // "Parked"/"Battery"/"Fuel" state line above was just fixed for (see
            // statusColor's own doc), and the comment right above here already said
            // "same reasoning as the state line" while the code did the opposite.
            // This row is expanded-only, which means it is always over the hero's car
            // photo + scrim, and onSurfaceVariant is a
            // SURFACE role: on a light-themed app it drew "Fuel 40%" and its pump glyph
            // in near-black over a dark photo, invisible, while the percentage and range
            // beside it -- both painted from the hero's LocalContentColor provider, which
            // travels onSurface -> heroOnPhoto (theme-inverted) as the card opens -- were
            // near-white in light mode. Under
            // a custom palette active it also picked up a slice of the seed
            // colour, so it came out a tinted grey belonging to no backdrop at all.
            // MutedContentAlpha keeps it subordinate to the numbers the way the muted
            // variant role used to, and statusAlpha still carries the morph fade-in.
            val fuelColor = LocalContentColor.current
                .copy(alpha = statusAlpha * MutedContentAlpha)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonIconSize),
                    tint = fuelColor,
                )
                Spacer(Modifier.width(ButtonIconGap))
                Text("Fuel $fuelPct%", style = type.bodyMedium, color = fuelColor, maxLines = 1)
            }
        }
        ChargeSegmentBar(
            frac = animatedChargeFrac(data.frac),
            limitPct = data.limitPct,
            stuckAtLimit = data.stuckAtLimit,
            charging = data.charging,
            // Darken the remaining track while the card is collapsed (the compact bar),
            // so the coloured fill reads against it; expanded keeps the lighter track.
            collapsed = t < 0.5f,
        )
    }
}
