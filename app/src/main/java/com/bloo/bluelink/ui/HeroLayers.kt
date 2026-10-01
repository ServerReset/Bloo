@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
 * The hero card's two layers, peeled out of HeroHeader: the photo with the charge readout that
 * morphs over it, and the numbers that fly between their collapsed and expanded positions.
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
    // The card's own coordinate space, captured once. Both anchors
    // convert into this, so the overlay's lerp is between two points
    // in one space rather than a mix of window and local offsets --
    // which is the way this goes wrong silently, by landing the
    // numbers off the card entirely.
    Spacer(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { cardCoords.value = it },
    )
    // Captured here (composable context) rather than inside the slide
    // transitions' offset lambdas below, which run outside composition.
    val heroPhotoDensity = LocalDensity.current
    AnimatedVisibility(
        visible = photoExpanded,
        // The shared collapse spec (fade + the container's own height reveal)
        // PLUS a slide-and-settle for the photo itself. This used to be a
        // scaleIn/Out from 92%/94% on the same non-bouncy spec the container's
        // own height uses -- an 8% scale change finishing at the same rate as
        // the reveal it rides inside reads as the photo simply FILLING IN as
        // the card grows, not as an object arriving on its own. Reported as
        // "pops in" from a real device.
        //
        // The entrance spring is deliberately UNDER-damped
        // (Spring.DampingRatioLowBouncy < 1): it overshoots its target and
        // settles back, which is what makes this a bounce and not just a
        // faster ease. The exit stays on the non-bouncy default spec --
        // a bounce reads as arrival, not departure; overshooting on the
        // way OUT would look like the photo hesitating before it leaves.
        // slideInVertically travels a real distance (HeroPhotoSlideDistance)
        // rather than a subtle scale nudge, so the photo visibly arrives FROM
        // somewhere instead of blooming in place. Scale rides the same spring
        // as the slide on each side, so the two read as one physical motion
        // rather than two differently-timed effects layered on top of each
        // other.
        //
        // Only the hero does this. A pebble body sliding open is content
        // appearing; a photo is an object, and objects arrive and settle.
        //
        // slideInVertically's offset lambda runs outside composition (it's
        // called by the animation, not composed), so the px distance is
        // converted with a plain captured Density rather than
        // LocalDensity.current inside the lambda.
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
    // The expanded readout, at the BOTTOM of the card.
    //
    // A SIBLING of the photo, not a child of it. As a child it inherited the
    // photo's scaleIn/scaleOut settle, so the numbers and the bar zoomed with the
    // image -- wrong for text, which should arrive rather than being flown in.
    // Split, the photo settles as an object and the readout just closes with it.
    //
    // Aligned within the card's own Box rather than placed in the pebble body:
    // the body is top-aligned in its Column, so a bar there sits under the
    // header, and pushing it down would need the Column to fillMaxHeight inside a
    // Box whose own height comes from a sibling -- which in a scrollable parent
    // (maxHeight = Infinity) is exactly how you get a bad measure. Aligning has
    // no such dependency.
    // THE readout. One instance, both states, morphing between them.
    //
    // Bottom-anchored and deliberately NOT wrapped in an AnimatedVisibility,
    // because there is nothing to show or hide any more -- this node exists in
    // both states. That also retires the footprint bug this slot used to have: a
    // fade-only AnimatedVisibility held its full ~142dp for the whole fade and
    // then dropped it in one frame, which was the "hangs at the wrong size, then
    // snaps". A node that never leaves cannot strand a footprint.
    //
    // The TRAVEL is free. The photo above is already animating the card's height,
    // so anchoring here rides that change from the header down to the base of the
    // photo with no bounds animation at all. `heroT` drives only the SIZE morph.
    // That is what three attempts with `sharedBounds` were doing the hard way --
    // see HeroMorphReadout.
    //
    // The paddings lerp, which is what widens the bar: collapsed it stops short
    // of the chevron, expanded it runs the card's full width.
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            // These three insets are DERIVED from the header's own geometry, not
            // picked. Collapsed, this node has to land exactly in the slot the
            // header reserved for it, and my first numbers did not -- the
            // percentage sat on top of the car icon and clipped the title's
            // descenders, because the readout is positioned against the CARD while
            // the reserve lives inside the header's TEXT COLUMN. Two coordinate
            // systems, and I had not made them agree.
            //
            // The header (PebbleShell) is: padding(horizontal = 16, vertical = 6),
            // Icon(20), Spacer(10), then the weighted text column. So:
            //
            //  start  16 + 20 + 10 = 46dp -- the text column's left edge, so the
            //         percentage lines up under the car NAME instead of over the
            //         icon. Expanded there is no icon to clear, so 16dp.
            //  end    the chevron is ~48dp inside the row's own 16dp padding, so
            //         76dp leaves it clear with a small optical gap. This was 64dp,
            //         which is why the bar ran under the chevron.
            //  bottom  Derived, not tuned. The readout is bottom-anchored in the
            //          card's Box, and the header reserves
            //          collapsedReadoutHeight + HeroReadoutBottomInset for it, so
            //          the two line up by construction rather than by a pixel budget
            //          that has to be re-checked whenever the type changes.
            //
            //          The comment removed from here did a hand arithmetic proof
            //          ("title occupies y 6..30 and the reserve y 30..70, this node
            //          is 40dp tall") against a 40dp reserve. The code beside it
            //          reserved 4.dp + ChargeBarHeight = 22dp. Whichever was once
            //          true, they had stopped agreeing, which is exactly the failure
            //          a derived value removes.
            .padding(
                // Clears the car icon, and NOTHING more. Putting the name's width in
                // here pushed the whole Column across -- including the BAR, which
                // then started under the percentage instead of spanning the card.
                // The name-clearing offset belongs to the numbers Row alone; it is
                // passed to HeroMorphReadout as `numbersStart` below.
                start = lerp(46.dp, 16.dp, heroT),
                // The readout clears whatever sits at the END of the header row. On a
                // plain page that is just the collapse chevron (76dp); in the
                // dual-column/expanded view the header ALSO carries the expandAction
                // ("Back to all cars"), so the readout (and the charge bar under it)
                // stops a full button short of that second control instead of running
                // underneath it -- plus one more gap, at the user's request that the
                // bar read narrower in the dual-column view specifically.
                end = lerp(if (hasExpandAction) 76.dp + HeaderButtonSize + GapRow else 76.dp, 16.dp, heroT),
                bottom = lerp(HeroReadoutBottomInset, 16.dp, heroT),
            ),
    ) {
        // Same travel as the title: this readout sits ON the photo once the
        // card is open, and it reads LocalContentColor, so without this the
        // percentage, range and state line were near-black on a dark image
        // exactly as the name was. One provider covers all three.
        CompositionLocalProvider(
            LocalContentColor provides
                lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
        ) {
            HeroMorphReadout(
                readout,
                heroT,
                onNumbersPositioned = reportNumbers,
                numbersHoisted = hoisted,
                statusAlpha = statusAlpha,
                // Collapsed, the numbers start after the name; expanded, they own the
                // left edge. Only this Row shifts -- the bar underneath does not.
                // Zero: this copy only ever shows EXPANDED, where it owns the card's
                // lower-left. The collapsed numbers are the header's, so nothing here has
                // to be offset past the car name any more -- which also retires the
                // measured-title-width plumbing that offset needed.
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
                    lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
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
