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
 * The car's hero card: photo/visual on top, [ChargeFuelBar] below. Corner radius eases
 * 24dp-40dp with `charging`; fades and slides up on first composition.
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
    /** Whether the photo box is showing. Passed in (not collected here) so the hero doesn't
     *  recompose on unrelated state changes. */
    photoExpanded: Boolean = true,
    /**
     * Grid "expand to full screen" / expanded-view "back to all cars" toggle, shown as this
     * pebble's [PebbleShell] header action. Null (phone single-column) renders none.
     */
    expandAction: PebbleHeaderAction? = null,
) {
    // Entrance plays once per car per session (coldStartIntroPlayed); replaying on re-composition
    // adds frames to the page's compose burst mid-swipe.
    val playIntro = remember(v.vin) { coldStartIntroPlayed.add("hero:${v.vin}") }
    val heroAlpha = remember { Animatable(if (playIntro) 0f else 1f) }
    val heroOffset = remember { Animatable(if (playIntro) 16f else 0f) }
    LaunchedEffect(v.vin) {
        if (!playIntro) return@LaunchedEffect
        launch { heroAlpha.animateTo(1f, tween(MotionLong)) }
        launch { heroOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }
    // On the phone the hero is a pebble on PebbleShell; its body collapses with the shared
    // collapseEnter/collapseExit transition, so no photo collapse logic is needed here.
    // Percentage, range and charging state are derived once and shared by both densities.
    val readout = chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric)
    // 0 collapsed, 1 expanded: the one value the readout morph (type sizes, gaps, paddings,
    // header reservation) lerps on.
    // Critically damped: this drives a SIZE, and an under-damped spatial spring would overshoot type size.
    val heroT by animateFloatAsState(
        targetValue = if (photoExpanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        label = "heroMorph",
    )

    // The status line fades on its own clock, not heroT: heroT is spring-driven with no fixed
    // duration to carve a delay from. Delayed only going in (photoExpanded true); collapsing
    // fades it out immediately. 350ms avoids reading as a snap.
    // One reveal curve (smoothstep on heroT with a head start) for numbers, status line and
    // fuel row so they fade in together. The bar stays persistent.
    val statusAlpha = run {
        val t = ((heroT - 0.15f) / 0.5f).coerceIn(0f, 1f)
        t * t * (3f - 2f * t)
    }

    // ---- The travelling numbers -------------------------------------------
    // One instance of the percentage and range, drawn by the overlay and positioned by
    // MEASURING the two anchors (collapsed title row, expanded readout) rather than by arithmetic.
    // Both anchors report in the CARD's coordinate space, so the offset is a plain lerp.
    val cardCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Position AND width: the overlay needs the width to space percentage and range apart.
    val collapsedNumbers = remember { mutableStateOf<Rect?>(null) }
    val expandedNumbers = remember { mutableStateOf<Rect?>(null) }
    // Until both ends are measured the anchors paint themselves, so the first frame is correct.
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

    // Follows the morph: the name travels from the surface colour to the light one the scrim
    // is built for; snapping would flash a white name on a white card.
    val heroTitleColorNow = lerp(MaterialTheme.colorScheme.onSurface, heroOnPhoto(), heroT)
    PebbleShell(
        expanded = photoExpanded,
        onToggle = { vm.togglePebble(v, com.bloo.bluelink.data.HERO_PHOTO_SECTION) },
        icon = Icons.Filled.DirectionsCar,
        title = v.name,
        modifier = modifier,
        titleColor = heroTitleColorNow,
        headerAction = expandAction,
        // The only pebble that grows its title: the name scaling up reads as the card taking over.
        growTitleOnExpand = true,
        // No `summary`: the bar is the summary. The photo is the card's BACKGROUND, running behind
        // the header row, with the title and chevron overlaid.
        background = {
            HeroBackground(cardCoords, v, imageUrl, photoExpanded, height, heroT, expandAction != null, readout, hoisted, statusAlpha, report(expandedNumbers))
        },
        // The travelling numbers: a plain lerp between the two anchors in this Box's space.
        // Rendered as `foreground` so they cross the header row above its buttons, not under.
        // Plain lerp (no two-phase easing) keeps width and type size in lockstep.
        // Not alpha = statusAlpha: that is the status line's delayed fade.
        foreground = {
            HeroForeground(collapsedNumbers, expandedNumbers, hoisted, heroT, readout, statusAlpha)
        },
        // Collapsed: name, percentage and range on one row, with the bar directly under it.
        // No shared element: SharedTransitionLayout is a LookaheadScope, which adds a lookahead
        // pass per placement and costs car-swipe frames. One [ChargeReadout] derivation feeds
        // both densities.
        // Both collapsed slots stay non-null and gate with AnimatedVisibility inside, so the
        // exit can play (a null slot deletes the node on the frame the pebble opens).
        // Row 1 of the collapsed card: percentage and range on the car name's own line, laid out in
        // the header's Row rather than positioned by hand. The numbers have two instances (this and
        // [HeroMorphReadout]); this one is gone by t = 0.35, when the expanded copy appears.
        // Kept alive past 0.35 once hoisted: it is the collapsed anchor, and a removed anchor
        // stops reporting.
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
            // A reservation, not content: stops the header text from sitting on the readout while the
            // card is short. Derived from the readout's tokens (pct line + gap + bar), not a constant.
            // TextUnit.toDp() throws on Unspecified/Em, so this relies on titleMedium keeping an sp
            // lineHeight; guard it if typography ever sets TextUnit.Unspecified.
            // The bar only, not the numbers row: the numbers share the title's row, so reserving
            // only what sits below it keeps the collapsed pill at two rows.
            val collapsedReadoutHeight = 2.dp + ChargeBarHeight
            // Plus the readout's bottom inset (HeroReadoutBottomInset above the card edge).
            val h = lerp(collapsedReadoutHeight + HeroReadoutBottomInset, 0.dp, heroT)
            // No graphicsLayer: an alpha on an empty Box is a per-frame layer allocation.
            Spacer(Modifier.fillMaxWidth().height(h))
        },
    ) {
        // Empty by design: the photo and readout live in `background`, positioned against the image.
        Spacer(Modifier.height(0.dp))
    }
}

