package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.uicommon.coldStartIntroPlayed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier,
    drivingLabel: String? = null,
    height: Dp = 150.dp,
    metric: Boolean = false,
    /** Whether the photo box is showing. Passed IN rather than collected from the
     *  view model here: this composable already has `vm`, but subscribing to state
     *  inside it would recompose the whole hero on every unrelated state change, and
     *  both call sites already hold the UiState they would read it from. */
    photoExpanded: Boolean = true,
    /**
     * The multi-column grid's own "expand to full screen"/expanded-view's "back to all
     * cars" toggle, rendered as this pebble's [PebbleShell] header action (next to the
     * existing collapse/expand chevron) instead of a separate floating icon elsewhere
     * on screen -- [SinglePebble]'s "summary" case supplies the fullscreen version,
     * [CriticalContent] the back version; null (the phone's single-column view, which
     * has neither concept) renders no action at all.
     */
    expandAction: PebbleHeaderAction? = null,
) {
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
        launch { heroAlpha.animateTo(1f, tween(MotionLong)) }
        launch { heroOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }
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
        modifier = modifier,
        titleColor = heroTitleColorNow,
        headerAction = expandAction,
        // The ONLY pebble that grows its title. Here the title is the car's NAME and the
        // card becomes a photo of that car, so the name scaling up reads as the card taking
        // over. On "Location" or "Diagnostics" it is a heading resizing for no reason.
        growTitleOnExpand = true,
        // No `summary` string. The bar below IS the summary now, and it is the real
        // one -- restating "82% - 241 mi" as header text beside a bar showing the same
        // thing is how the same numbers get rendered twice and then drift.
        // The photo is the card's BACKGROUND now, not a body child, so it runs up
        // behind the header row and the title and chevron overlay its top. Collapsing
        // it is the same shared transition as before -- the only change is which layer
        // it lives on.
        background = {
            HeroBackground(cardCoords, v, imageUrl, photoExpanded, height, heroT, expandAction != null, readout, hoisted, statusAlpha, report(expandedNumbers))
        },
        // THE numbers. One instance, travelling between the two anchors -- and the travel
        // is a plain lerp because both anchors are points in this same Box's space.
        //
        // Rendered as `foreground` (ON TOP of the header and body), NOT inside `background`:
        // the numbers cross the header row on the way between the two anchors, and drawn
        // behind it they passed UNDER the header's own buttons and read as a clipped glitch.
        // Reported directly: the range text "glitches when it goes below the buttons during
        // the collapse". Same card coordinate space either way, so the lerp is unchanged;
        // only the z-order moved.
        //
        // A two-phase easing was tried here and reverted: width is what HeroNumbers uses to
        // size its Row, but the type size scales with heroT directly, so holding width at the
        // collapsed value while heroT advanced made the range outgrow its own width mid-morph
        // ("mi" -> "m..."). Plain lerp keeps width and type in lockstep.
        //
        // NOT alpha = statusAlpha: that is the STATUS LINE's own delayed fade, wrong for the
        // percentage/range, which this Box is the only thing that still paints once a card has
        // been expanded (both anchors stop painting the moment `hoisted` turns true). Wrapping
        // it in statusAlpha made the numbers vanish whenever heroT sat near 0.
        foreground = {
            HeroForeground(collapsedNumbers, expandedNumbers, hoisted, heroT, readout, statusAlpha)
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
}

