package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.compose.runtime.State

/*
 * The hero card's two layers: the photo with the charge readout over it, and the numbers that
 * fly between their collapsed and expanded positions.
 */

/** The hero's backdrop: the car photo (sliding and scaling in) and the readout sitting over it. */
@Composable
internal fun BoxScope.HeroBackground(
    cardCoords: MutableState<LayoutCoordinates?>,
    v: Vehicle,
    imageUrl: String?,
    photoExpanded: Boolean,
    height: Dp,
    heroT: Float,
    hasExpandAction: Boolean,
    readout: ChargeReadout,
    hoisted: Boolean,
    statusAlpha: Float,
    reportNumbers: (LayoutCoordinates) -> Unit,
) {
    // The card's own coordinate space; both anchors convert into it so the overlay lerps within one space.
    Spacer(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { cardCoords.value = it },
    )
    // Captured here: the slide transitions' offset lambdas run outside composition.
    val heroPhotoDensity = LocalDensity.current
    AnimatedVisibility(
        visible = photoExpanded,
        // Shared collapse spec (fade + height reveal) plus a slide-and-settle for the photo, which
        // reads as an object arriving rather than filling in. The entrance spring is underdamped
        // (DampingRatioLowBouncy) to bounce; the exit stays non-bouncy since overshoot reads as hesitation.
        // slideInVertically travels HeroPhotoSlideDistance, and scale rides the same spring.
        // The offset lambda runs outside composition, so the px distance uses a captured Density.
        enter = expandEnterSized() +
            slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { with(heroPhotoDensity) { -HeroPhotoSlideDistance.roundToPx() } } +
            scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        exit = expandExitSized() +
            slideOutVertically(
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ) { with(heroPhotoDensity) { -HeroPhotoSlideDistance.roundToPx() } } +
            scaleOut(
                targetScale = 0.9f,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
    ) {
        HeroPhotoBackdrop(v, imageUrl, height, aspectRatio = 16f / 9f)
    }
    // The expanded readout at the BOTTOM of the card: a sibling of the photo, not a child, so it
    // does not inherit the photo's scale settle (text should not zoom).
    // Aligned within the card's Box rather than the pebble body: filling height there would depend
    // on a sibling's height, which breaks measure in a scrollable parent.
    // THE readout: one instance, both states, never wrapped in AnimatedVisibility (so it cannot
    // strand a footprint). Bottom-anchoring rides the photo's height animation for free; `heroT`
    // drives only the size morph (see HeroMorphReadout). The paddings lerp to widen the bar.
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            // These insets derive from the header's geometry (PebbleShell: padding(h=16, v=6),
            // Icon(20), Spacer(10), then the weighted text column), not from tuning.
            //  start  16 + 20 + 10 = 46dp, the text column's left edge; expanded has no icon, so 16dp.
            //  end    the chevron is ~48dp inside 16dp padding, so 76dp clears it with an optical gap.
            //  bottom the readout is bottom-anchored and the header reserves
            //         collapsedReadoutHeight + HeroReadoutBottomInset, so the two line up by construction.
            .padding(
                // Clears the car icon only; the name-clearing offset belongs to the numbers Row
                // (`numbersStart`), or the bar would start under the percentage.
                start = lerp(46.dp, 16.dp, heroT),
                // Clears whatever sits at the END of the header row: the chevron (76dp), plus the
                // expandAction in the dual-column view, plus one more gap to keep the bar narrower there.
                end = lerp(if (hasExpandAction) 76.dp + HeaderButtonSize + GapRow else 76.dp, 16.dp, heroT),
                bottom = lerp(HeroReadoutBottomInset, 16.dp, heroT),
            ),
    ) {
        // The readout sits on the photo once the card is open and reads LocalContentColor, so it needs this provider.
        CompositionLocalProvider(
            LocalContentColor provides
                lerp(MaterialTheme.colorScheme.onSurface, heroOnPhoto(), heroT),
        ) {
            HeroMorphReadout(
                readout,
                heroT,
                onNumbersPositioned = reportNumbers,
                numbersHoisted = hoisted,
                statusAlpha = statusAlpha,
                // Zero: this copy only shows expanded and owns the lower-left; the collapsed numbers are the header's.
                numbersStart = 0.dp,
            )
        }
    }
}

/** The readout's numbers, hoisted out above the card and flown between the two layouts. */
@Composable
internal fun HeroForeground(
    collapsedNumbers: State<Rect?>,
    expandedNumbers: State<Rect?>,
    hoisted: Boolean,
    heroT: Float,
    readout: ChargeReadout,
    statusAlpha: Float,
) {
    val from = collapsedNumbers.value ?: expandedNumbers.value
    val to = expandedNumbers.value ?: collapsedNumbers.value
    if (hoisted && from != null && to != null) {
        val x = androidx.compose.ui.util.lerp(from.left, to.left, heroT)
        val y = androidx.compose.ui.util.lerp(from.top, to.top, heroT)
        val w = androidx.compose.ui.util.lerp(from.width, to.width, heroT)
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
        ) {
            CompositionLocalProvider(
                LocalContentColor provides
                    lerp(MaterialTheme.colorScheme.onSurface, heroOnPhoto(), heroT),
            ) {
                HeroNumbers(
                    readout, heroT,
                    width = with(LocalDensity.current) { w.toDp() },
                    statusAlpha = statusAlpha,
                )
            }
        }
    }
}
