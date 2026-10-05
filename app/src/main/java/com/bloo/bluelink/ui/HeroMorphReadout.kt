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
    /** Start inset for the numbers row only, so it can sit after the car name while the bar spans the card. Zero for [ChargeFuelBar]. */
    numbersStart: Dp = 0.dp,
    /** Reports where the numbers row landed, for the travelling overlay (see [HeroCollapsedNumbers.onPositioned]). */
    onNumbersPositioned: (LayoutCoordinates) -> Unit = {},
    /** True once the overlay draws the real numbers; this row keeps measuring as an anchor but stops painting. */
    numbersHoisted: Boolean = false,
    /** The status line's own fade on its delayed clock (see the caller's `statusAlpha`). */
    statusAlpha: Float = t,
) {
    val type = MaterialTheme.typography
    // Real type steps are lerped, not graphicsLayer-scaled: this Column's height must grow so the state line
    // below is pushed down (graphicsLayer does not change measured size). The cost is a per-frame font size
    // missing Text's single-slot ParagraphLayoutCache, bounded to two Text nodes in one layout pass.
    // The type scale lives in [HeroNumbers] so the anchor and overlay cannot drift.
    Column(
        // No alpha ramp: this node is fully visible in both states; one bar and one pair of numbers change shape.
        modifier,
        verticalArrangement = Arrangement.spacedBy(lerp(2.dp, 6.dp, t)),
    ) {
        // Fades in on the back half only: the collapsed numbers are drawn by the header's title Row (see
        // HeroCollapsedNumbers), so this copy must stay invisible until that one is gone.
        // A plain Row with fillMaxWidth, not BoxWithConstraints: subcomposition on every frame of the heroT transition
        // dropped frames. HeroNumbers gets fillWidth = true instead of a measured Dp.
        Row(
            Modifier
                .padding(start = numbersStart)
                // fillMaxWidth so the anchor reports the readout's real span; the overlay lerps to that width (range at the right edge).
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = if (numbersHoisted) 0f
                    else ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                }
                .onGloballyPositioned(onNumbersPositioned),
            verticalAlignment = Alignment.Bottom,
        ) {
            // One definition of the numbers shared with the collapsed anchor and overlay (see [HeroNumbers]); this row only
            // needs to be measured, and the overlay draws the visible copy. fillWidth = true matches the Row's width.
            HeroNumbers(data, t, fillWidth = true, statusAlpha = statusAlpha)
        }
        // Plug-in hybrid's fuel tank: expanded only; fades in over the back half of the morph. The pump icon
        // distinguishes it from the battery figure.
        data.fuelPct?.takeIf { statusAlpha > 0.01f }?.let { fuelPct ->
            // LocalContentColor, not onSurfaceVariant: this row is always over the hero's photo + scrim, and a surface role
            // would be near-black on a dark photo in light theme. MutedContentAlpha keeps it subordinate; statusAlpha fades it in.
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
            // Darken the remaining track while collapsed so the fill reads against it.
            collapsed = t < 0.5f,
        )
    }
}
