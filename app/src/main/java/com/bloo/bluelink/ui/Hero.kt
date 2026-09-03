@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.uicommon.coldStartIntroPlayed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The car's hero card: photo/visual on top, [ChargeFuelBar] below. Corner
 * radius eases between 24dp and 40dp (animateDpAsState) when `charging`
 * flips, as a subtle "something is happening" cue distinct from any text or
 * icon change. Fades and slides up 16dp on first composition
 * (`heroAlpha`/`heroOffset`, both [Animatable]s driven once in
 * `LaunchedEffect(Unit)`) so it enters in step with the rest of the
 * per-car stack rather than popping in instantly.
 */
@Composable
internal fun HeroHeader(
    v: Vehicle,
    status: VehicleStatus?,
    imageUrl: String?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    vm: AppViewModel,
    drivingLabel: String? = null,
    dragHandle: Modifier = Modifier,
    height: Dp = 150.dp,
    metric: Boolean = false,
    /** Whether the photo box is showing. Passed IN rather than collected from the
     *  view model here: this composable already has `vm`, but subscribing to state
     *  inside it would recompose the whole hero on every unrelated state change, and
     *  both call sites already hold the UiState they would read it from. */
    photoExpanded: Boolean = true,
) {
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    // Play the fade/slide-up entrance only ONCE per car per session, gated on the
    // same coldStartIntroPlayed set the pebble stagger uses. Previously this was an
    // unconditional LaunchedEffect(Unit) that replayed on EVERY (re)composition of
    // this hero — including when a swiped-away page is disposed and later recomposed
    // (or, now that the car pager pre-composes a neighbour via
    // beyondViewportPageCount=1, when that neighbour composes off-screen). Replaying
    // the fade on each enter added animation frames on top of the page's compose
    // burst mid-swipe. Once-per-VIN means a page that re-enters snaps straight to
    // rest instead of re-animating.
    val playIntro = remember(v.vin) { coldStartIntroPlayed.add("hero:${v.vin}") }
    val heroAlpha = remember { Animatable(if (playIntro) 0f else 1f) }
    val heroOffset = remember { Animatable(if (playIntro) 16f else 0f) }
    LaunchedEffect(v.vin) {
        if (!playIntro) return@LaunchedEffect
        launch { heroAlpha.animateTo(1f, tween(400)) }
        launch { heroOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }
    val corner by animateDpAsState(
        targetValue = if (charging) 40.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = SoftDamping,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "heroCorner",
    )
    // On the PHONE, match the EXPANDED pebble corner (PebbleCornerExpanded = 20dp) —
    // the hero reads as an always-expanded card, so it should share the tighter
    // expanded radius, not the rounder collapsed one. The old hardcoded charging 40dp /
    // idle 24dp both mismatched. The COVER keeps its own animated corner (full-height tile).
    val heroShape = RoundedCornerShape(if (LocalForceExpanded.current) corner else PebbleCornerExpanded)
    val heroOutline = LocalAppearance.current
    // On the flip cover this hero is one full-screen tile. Unlike every other
    // pebble it rolls its own Card and never went through PebbleShell, so it never
    // got the cover's fill-height treatment — it wrapped its content and left a
    // dead gradient box (no photo) plus a black void below. When on the cover, fill
    // the tile height, centre the content, and drop the empty photo box entirely.
    val cover = LocalForceExpanded.current
    if (!cover) {
        // On the phone the hero IS a pebble now, built on the same PebbleShell as every
        // other one: header with icon, title, summary and the standard chevron, and a body
        // that collapses with the shared collapseEnter/collapseExit transition.
        //
        // This replaces a bespoke Card with a MorphExpandButton bolted beside the charge
        // bar. That version worked, but it was a card that looked like a pebble and
        // collapsed like a pebble while sharing none of the mechanism -- so it
        // re-implemented the shadow, outline, corner, drag-handle plumbing and toggle
        // placement, and would have drifted from the real pebbles the first time any of
        // those changed.
        //
        // The photo needs no collapse logic of its own any more either: PebbleShell hides
        // the whole body when collapsed, so "no image when collapsed" falls out of the
        // shared component instead of being a rule this function enforces.
        //
        // Derived ONCE, here, and handed to both densities. The collapsed line and the
        // expanded block used to work the percentage, the range and the charging state out
        // separately -- same inputs, two derivations, and therefore two things to keep in
        // step by hand.
        val readout = chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric)
        // 0 collapsed, 1 expanded. The ONE value the readout's morph runs on: type sizes,
        // gaps, paddings and the header's reservation all lerp on it, so they cannot get out
        // of step with each other the way separate transitions did.
        //
        // Critically damped and terminating on a real threshold, for the same reason the
        // discarded bounds spring needed it: this drives a SIZE, and the theme's spatial
        // spring is under-damped by design, so type would overshoot past its target size and
        // spring back. Text that overshoots reads as a wobble, not as liveliness.
        val heroT by animateFloatAsState(
            targetValue = if (photoExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            label = "heroMorph",
        )

        // The status line's ("Parked"/"Charging...") own fade, on its OWN clock rather than
        // heroT: originally delayed a full 500ms (requested as "half a second longer
        // before it fades in" -- the line was reading as arriving too eagerly, at the
        // same moment the card itself starts opening) and then reported as too long a
        // wait once that shipped, so trimmed to 250ms -- still a real, deliberate beat
        // after the card starts opening rather than simultaneous with it, just not a
        // hang. Delayed only going IN (photoExpanded true); collapsing fades it out
        // immediately, so the card doesn't look like it's still finishing an entrance
        // while it closes. This is a real wall-clock delay (tween + delayMillis), not a
        // fraction of heroT, because heroT is spring-driven with no fixed duration to
        // carve a fraction out of. By the time it starts, the height reveal (still on
        // heroT) has long since finished even at this shorter delay, so there's no
        // repeat of the clip-vs-alpha mismatch fixed just before this -- the slot is
        // already fully sized and the text just fades into it cleanly.
        // durationMillis raised from 200 to 350: reported as not fading in at all after
        // the delay was trimmed, and 200ms is short enough on a real device's frame
        // pacing to read as a snap rather than a fade, especially right after a 250ms
        // wait primes the eye to expect a discrete change. 350ms is closer to what the
        // original 500ms-delay version's own fadeIn spec would have taken to settle,
        // just without the long wait in front of it.
        // ONE reveal curve for the whole expanded readout -- the travelling
        // numbers, the status line and the fuel row all fade in AROUND THE
        // SAME WINDOW (a single smoothstep on heroT, with a soft head start
        // so the card's own open bounce has begun before the content lands),
        // instead of three pieces each skipping in on their own threshold.
        // Same clock = no "one part pops in, the rest follows later" stagger
        // (reported: fade in gracefully, and in lockstep). Pieces that ride
        // the card's photo (numbers/status/fuel) share this alpha; the bar
        // itself stays persistent because it is the "what the card shows you"
        // element, not a detail of it.
        val statusAlpha = run {
            val t = ((heroT - 0.15f) / 0.5f).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }

        // ---- The travelling numbers -------------------------------------------
        //
        // ONE instance of the percentage and range, drawn by the overlay below and
        // positioned by MEASUREMENT rather than arithmetic.
        //
        // The two ends are laid out by the things that already know where they go:
        // the title Row puts the collapsed numbers beside the car name, and the
        // readout puts the expanded ones at the card's lower-left. Both keep doing
        // exactly that -- they simply stop painting and report their position
        // instead. Six earlier attempts computed the collapsed position by hand (a
        // bottom anchor, a derived lift, the measured title width, a type-step
        // ratio) and each landed slightly off, the last of them printing the
        // numbers above the name. A Row places its children correctly by
        // construction; the trick is to read that placement rather than reproduce it.
        //
        // Both anchors report in the CARD's coordinate space, so the overlay's
        // offset is a plain lerp between two points in the same space.
        val cardCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
        // Position AND width: the overlay needs the width to know how far apart to
        // push the percentage and the range. Collapsed that width is the natural
        // content width, so nothing moves; expanded it is the readout's full span,
        // which is what puts the range on the right.
        val collapsedNumbers = remember { mutableStateOf<Rect?>(null) }
        val expandedNumbers = remember { mutableStateOf<Rect?>(null) }
        // Until BOTH ends have been measured there is nothing to interpolate
        // between, so the two anchors paint themselves and the card looks exactly
        // as it did before. That makes the first frame correct rather than blank,
        // and a measurement that never arrives degrade to the old crossfade instead
        // of losing the numbers entirely.
        val hoisted = cardCoords.value != null &&
            collapsedNumbers.value != null && expandedNumbers.value != null
        fun report(into: androidx.compose.runtime.MutableState<Rect?>) =
            { coords: LayoutCoordinates ->
                val card = cardCoords.value
                if (card != null && coords.isAttached) {
                    val origin = card.localPositionOf(coords, Offset.Zero)
                    into.value = Rect(
                        origin,
                        androidx.compose.ui.geometry.Size(
                            coords.size.width.toFloat(),
                            coords.size.height.toFloat(),
                        ),
                    )
                }
            }

        // Follows the morph rather than switching: the photo fades in over the same
        // t, so the name has to travel from the surface's own colour to the light one
        // the scrim is built for. Snapping at a threshold would flash a white name
        // onto a still-white card for the frames before the photo arrives.
        val heroTitleColorNow = lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT)
        PebbleShell(
            expanded = photoExpanded,
            onToggle = { vm.togglePebble(v, com.bloo.bluelink.data.HERO_PHOTO_SECTION) },
            icon = Icons.Filled.DirectionsCar,
            title = v.name,
            vm = vm,
            dragHandle = dragHandle,
            titleColor = heroTitleColorNow,
            // The ONLY pebble that grows its title. Here the title is the car's NAME and the
            // card becomes a photo of that car, so the name scaling up reads as the card taking
            // over. On "Location" or "Diagnostics" it is a heading resizing for no reason.
            growTitleOnExpand = true,
            // No `summary` string. The bar below IS the summary now, and it is the real
            // one -- restating "82% - 241 mi" as header text beside a bar showing the same
            // thing is how the same numbers get rendered twice and then drift, which is a
            // bug I already had to fix on the widget's MEDIUM tiers.
            // The photo is the card's BACKGROUND now, not a body child, so it runs up
            // behind the header row and the title and chevron overlay its top. Collapsing
            // it is the same shared transition as before -- the only change is which layer
            // it lives on.
            background = {
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
                    enter = collapseEnter() +
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
                    exit = collapseExit() +
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
                            end = lerp(76.dp, 16.dp, heroT),
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
                            onNumbersPositioned = report(expandedNumbers),
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
                // THE numbers. One instance, travelling between the two anchors --
                // and the travel is a plain lerp because both anchors are points in
                // this same Box's space.
                //
                // A two-phase easing (height drops first, width/x held then released on
                // a curve that overshoots past its target) was tried here and reverted:
                // width is what HeroNumbers uses to size its Row, but the ROW's own text
                // size scales with heroT directly, not with width's easing. Holding width
                // at the narrow collapsed value while heroT (and so the type size) kept
                // advancing meant the range text grew past what its still-small width
                // could fit for part of the transition -- "mi" briefly shrank to "m..."
                // before width caught up and it reflowed back. A plain lerp keeps width
                // and type size moving together in lockstep, which is what avoids that.
                // Each anchor falls back to the other. Requiring BOTH meant that on a card
                // which has never been expanded -- the normal state of every card in the garage
                // -- the expanded anchor had never composed, so `to` was null, this whole block
                // was skipped, and the in-card copy is alpha 0 while hoisted. The percentage and
                // range simply were not drawn anywhere. With one anchor known the lerp is
                // between a point and itself, which is exactly right: hold at the collapsed
                // position until the expanded one reports.
                val from = collapsedNumbers.value ?: expandedNumbers.value
                val to = expandedNumbers.value ?: collapsedNumbers.value
                if (hoisted && from != null && to != null) {
                    val x = androidx.compose.ui.util.lerp(from.left, to.left, heroT)
                    val y = androidx.compose.ui.util.lerp(from.top, to.top, heroT)
                    val w = androidx.compose.ui.util.lerp(from.width, to.width, heroT)
                    Box(
                        // NOT alpha = statusAlpha. That is the STATUS LINE's own delayed fade
                        // (0 until heroT passes 0.15, ramping in over the following 0.5) --
                        // right for the "Charging..." text HeroNumbers gates internally with
                        // this same value, wrong for the percentage/range themselves, which
                        // this Box is the ONLY thing that still paints once a card has been
                        // expanded even once (both anchors go permanently invisible the moment
                        // `hoisted` turns true -- see their own alpha`s doc). Wrapping the whole
                        // overlay in statusAlpha meant the numbers vanished for the entire time
                        // heroT sat at or near 0 -- i.e. whenever that card was COLLAPSED. That
                        // is the reported "the collapsed hero pebble doesn't have the charge
                        // percent any more" -- it did, right up until the card was expanded
                        // once, and then never again while collapsed.
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
            },
            // Collapsed: name, percentage and range on ONE row, with the bar directly under
            // it. Two rows reads as a status line with a gauge under it, which is what it is.
            //
            // Collapsed: name, percentage and range on ONE row, with the bar under it.
            //
            // NOT a shared element, and this time the reason is measured rather than
            // guessed. `sharedBounds` requires a `SharedTransitionLayout`, which is a
            // LookaheadScope, so the hero's subtree runs an extra lookahead measure/place
            // pass every time it is placed -- and a pager drag re-places every page on every
            // frame, on all three pages beyondViewportPageCount keeps live. (Verified in the
            // resolved artifact: SharedBoundsNode implements ApproachLayoutModifierNode.)
            //
            // That is exactly why the flip cover's swipe was smooth while the phone's was
            // not: PebbleShell returns through CoverTile BEFORE it ever creates the scope, so
            // the cover path never pays for this at all. A travelling charge bar is not worth
            // the one gesture the user makes most.
            //
            // The original ask -- the percentage and range rendered twice -- stays fixed, and
            // at the level that actually mattered: ONE [ChargeReadout] derivation feeds both
            // densities, so they cannot drift and only one is ever on screen.
            // Both collapsed slots stay NON-NULL and gate with AnimatedVisibility inside.
            // `if (photoExpanded) null else { … }` deletes the node on the frame the pebble
            // opens, so there is nothing left to play an exit -- which is why these two
            // popped in and out with no animation at all after the shared element came out.
            //
            // No shared element here, deliberately. The travel needed a
            // SharedTransitionLayout, which is a LookaheadScope, and that is what cost the
            // car-swipe frames (see 3cc327a). An animated collapse does not need one: these
            // are ordinary enter/exit transitions on the two nodes, which participate in
            // layout exactly once per frame like everything else.
            // ROW 1 of the collapsed card: the percentage and range, on the car name's own line.
            //
            // In the header's own Row rather than positioned by me. Six attempts to compute this
            // inset -- a bottom anchor, a derived lift, the measured title width, the type-step
            // ratio -- each landed slightly off, the last of them printing the numbers ABOVE the
            // name. A Row aligns its children by construction, which is the whole reason this slot
            // exists; its KDoc named the hero as the user while nothing used it.
            //
            // Yes, this means the NUMBERS have two instances (this one and the expanded copy in
            // [HeroMorphReadout]) -- the honest cost of layout-instead-of-arithmetic. The charge
            // BAR is still a single instance, which was the part worth protecting. And the
            // roughness the two-copy version originally had came from both being visible at
            // similar opacity: this one is gone by t = 0.35 and the expanded copy starts appearing
            // there, so they never overlap.
            // Kept alive past 0.35 once hoisted: it is the collapsed ANCHOR then, and
            // an anchor that is removed stops reporting, which would strand the
            // overlay at its last known point.
            titleTrailing = if (heroT > 0.35f && !hoisted) null else {
                {
                    HeroCollapsedNumbers(
                        readout, heroT,
                        onPositioned = report(collapsedNumbers),
                        hoisted = hoisted,
                    )
                }
            },
            summary = null,
            headerContent = {
                // A RESERVATION, not content. The bar itself lives in the one readout at the
                // bottom of the card; this only stops the header's text column from sitting on
                // top of it while the card is short.
                //
                // Derived from the same tokens the readout composes with, not picked: its
                // collapsed height is the pct line (titleMedium) plus the inter-row gap plus
                // the bar. Choosing a number here instead of deriving it is how this slot
                // produced a mismatch every time it was a constant -- the deleted
                // heroReadoutReserve() was exactly that, and the tombstone above says so.
                // TextUnit.toDp() THROWS on an Unspecified or Em value, so this depends on
                // titleMedium keeping an sp lineHeight. It does: expressiveTypography() builds
                // from Typography() and `.copy(fontFamily, fontWeight)` only, so the default
                // 24.sp survives. Checked rather than assumed, because the failure would be a
                // crash in the hero rather than a layout being a few dp out. If a future
                // typography ever sets lineHeight = TextUnit.Unspecified, guard this.
                // The BAR only -- deliberately NOT the numbers row above it.
                //
                // Reserving the readout's whole height pushed it clear of the title and the
                // collapsed pill became THREE rows: name / numbers / bar. It must be two: name
                // and numbers sharing one row, bar underneath. The numbers row is the same
                // height as the title (both titleMedium), so reserving only what sits BELOW it
                // lets the bottom-anchored readout land its numbers on the title's own row.
                val collapsedReadoutHeight = 2.dp + ChargeBarHeight
                // + the readout's own bottom inset. The readout occupies
                // collapsedReadoutHeight of CONTENT and then sits HeroReadoutBottomInset above
                // the card's edge, so reserving only the content left the reservation one gap
                // short and the readout's top edge crossed into the title's row.
                val h = lerp(collapsedReadoutHeight + HeroReadoutBottomInset, 0.dp, heroT)
                // No graphicsLayer: there is nothing here to fade any more. An alpha on an
                // empty Box is a layer allocation per frame for no pixels.
                Spacer(Modifier.fillMaxWidth().height(h))
            },
        ) {
            // Empty by design. Everything the expanded state adds -- the photo and the
            // readout over its lower edge -- is in `background`, because both need to be
            // positioned against the IMAGE rather than stacked under the header.
            Spacer(Modifier.height(0.dp))
        }
        return
    }
    // Unreachable from here down: `cover` (LocalForceExpanded) is true only on the
    // flip cover, and the cover's home page no longer calls HeroHeader at all --
    // CoverMainTile replaced it, including this branch's own photo-as-background
    // treatment (see CoverMainTile's own doc for where that logic lives now). Left
    // as a `return` above rather than restructuring this already-long function to
    // drop the `if`, so the diff that orphaned this branch stays easy to find in
    // history if that's ever in question.
}

/** The tonal primary→tertiary→secondary gradient used as the fallback fill
 *  behind car photos across the garage/settings surfaces. Callers apply their
 *  own `.alpha(...)` where they want it dimmed -- this returns only the brush. */
internal fun carTonalBrush(scheme: ColorScheme): Brush =
    Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))

/** The clipped square thumbnail used for a car: the set photo if there is one,
 *  else the [carTonalBrush] fallback with a centered car icon. [cornerRadius]
 *  and [iconSize] vary per caller (the settings card vs. the tiles header). */
@Composable
internal fun CarThumb(img: String?, size: Dp, cornerRadius: Dp, iconSize: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (!img.isNullOrBlank()) {
            AsyncImage(
                model = rememberPhotoModel(img),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(carTonalBrush(scheme)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** The Coil model for a stored car photo: a [java.io.File] for a locally-cropped
 *  absolute path, or the raw URL string for a pasted one. */
@Composable
internal fun rememberPhotoModel(url: String): Any =
    remember(url) { if (url.startsWith("/")) java.io.File(url) else url }

// collapseEnter / collapseExit -- the app's one collapse spec -- now live in UiTokens.kt,
// with the reasoning that goes with them. 14 call sites in this file still use them.

/**
 * The car photo plus the contrast scrim that makes text on top of it legible. ONE
 * definition, used by the phone hero's expanded background and by the flip cover's tile.
 *
 * Contrast, not decoration. Every element overlaid on the hero -- title, chevron, the whole
 * charge readout -- sits on an arbitrary car photo, and against a light car they all
 * disappear. The widget hit the same problem and solved it with a luminance check on the
 * resolved accent; a scrim is the cheap version and is what the hero does.
 *
 * The gradient covers the FULL height and never reaches transparent. An earlier version
 * scrimmed only the top strip and faded to clear by 45%, on the assumption that only the
 * header row was overlaid -- it is not, the readout is over the image too. Heaviest at the
 * top and bottom because those are the two bands that carry content (title and chevron up
 * top, the charge readout along the bottom); the middle can afford to be clear because
 * nothing sits there, which is what lets the photo still read as a photo.
 *
 * remember-ed: Brush.verticalGradient allocates a stop list, and this sits inside a card
 * that recomposes on every status change.
 *
 * [aspectRatio] null means size by [height] -- the flip cover, whose tile height is given.
 */
@Composable
internal fun HeroPhotoBackdrop(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    aspectRatio: Float? = null,
    corner: Dp = PebbleCornerExpanded,
    /** See [HeroVisual.fill] -- the flip cover fills its tile. */
    fill: Boolean = false,
) {
    Box(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        HeroVisual(v, imageUrl, height, corner, aspectRatio = aspectRatio, fill = fill)
        val scrim = remember {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.30f to Color.Black.copy(alpha = 0.22f),
                0.62f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Black.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.matchParentSize().background(scrim))
    }
}

/** Default = a clean brand gradient. If the user set a photo, show that instead. */
@Composable
internal fun HeroVisual(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    corner: Dp = 18.dp,
    /** When set, size by aspect ratio instead of [height] -- 16:9 for the phone hero, so
     *  the image keeps its shape at any screen width instead of being letterboxed or
     *  cropped by a fixed dp height. */
    aspectRatio: Float? = null,
    /** Fill the parent in BOTH axes, ignoring [height] and [aspectRatio] -- the flip cover,
     *  whose tile height is the frame, so cropping to fill it is what a full-screen glance
     *  wants. Requires a bounded parent, which the cover tile is (its Card fills height). */
    fill: Boolean = false,
) {
    val sizeModifier = when {
        fill -> Modifier.fillMaxSize()
        aspectRatio != null -> Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        else -> Modifier.fillMaxWidth().height(height)
    }
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            sizeModifier
                .clip(RoundedCornerShape(corner))
                .background(carTonalBrush(scheme)),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = rememberPhotoModel(imageUrl)
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends
        // seamlessly into the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        // crossfade, so the car photo ARRIVES instead of popping. This is the one hero
        // element that had no animation of any kind: the pebble's collapse animates, the
        // readout's numbers roll, the bar's fill springs -- and then the photo itself
        // appeared between two frames. The map tiles below already did this; the hero,
        // the largest image in the app and the one the eye lands on first, did not.
        //
        // Coil skips the fade for memory-cache hits by design, which is exactly right
        // here: a first load fades in, but scrolling back to an already-decoded photo
        // does not re-fade, so this cannot turn into a flicker on the car pager.
        // Memoized like the map tiles: creating a fresh ImageRequest every recomposition
        // would trigger unnecessary reloads and cause visible flicker/jank.
        val context = LocalContext.current
        val imageRequest = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = v.model,
            contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
            modifier = sizeModifier
                .then(if (transparent) Modifier else Modifier.clip(RoundedCornerShape(corner))),
        )
    }
}

/**
 * The battery/fuel percentage readout: headline percent + range, a status
 * line beneath (charging details > driving/parked > plain "Battery"/"Fuel"
 * label, in that priority order), and a gradient progress bar. The bar's
 * fill animates via a spring (`animatedFrac`) rather than snapping to the
 * new percentage, and -- when plugged in -- a small dot marks the
 * charge-limit target percentage on the track so the user can see at a
 * glance how much further it'll charge.
 */
@Composable
internal fun ChargeFuelBar(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String? = null,
    metric: Boolean = false,
) {
    // Now literally [HeroMorphReadout] held at its expanded end. There is ONE readout
    // implementation in the app, and every surface that shows this -- the hero on the phone,
    // the flip cover's tile, the EV Charge pebble -- renders that same one.
    //
    // This function had grown a near-duplicate of it: a ChargeStatsBlock with the same Row,
    // the same weighted spacer, the same two RollingNumbers at the same two type steps, then
    // the same fuel row and the same bar. Two implementations of one readout is how the
    // collapsed bar ended up silently dropping the charge-limit marker the expanded one drew,
    // and how the morph pass dropped the fuel icon this file had always had. `t = 1f` is a
    // constant, so nothing here animates -- the morph is inert at its endpoint.
    HeroMorphReadout(chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric), t = 1f)
}

/**
 * Everything the charge/fuel readout says, derived ONCE.
 *
 * The hero renders this readout at two densities — one line in the collapsed header,
 * the full block at the bottom of the expanded card — and until now those were two
 * independent derivations of the same numbers: two answers to "battery percentage or
 * fuel percentage", two copies of the charging > driving > plain priority order for
 * the state line, two charging-colour rules. That is this codebase's recurring class
 * of bug (a rule that exists in one place and is re-typed in another), and here it
 * had already produced a visible one — both copies on screen simultaneously,
 * disagreeing about whether to mention charging.
 *
 * Now both densities render from one of these, and only the LAYOUT differs.
 */

// Colours, sizes and motion specs shared across screens live in UiTokens.kt.

/**
 * The shared floating/card edge: the app's default frosted rim ([frostedRim]).
 * Call sites keep their normal [glassContainerAlpha] frosted fill. The [tint]
 * param is retained for call-site compatibility but is no longer used.
 */
@Composable
internal fun Modifier.appGlassRim(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") tint: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier = this.frostedRim(shape)

