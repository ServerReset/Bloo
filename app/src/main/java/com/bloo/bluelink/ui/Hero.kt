package com.bloo.bluelink.ui

import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.uicommon.AppIcons

/**
 * The car's hero card: photo/visual on top, [ChargeFuelBar] below. Corner radius eases 24dp-40dp
 * with `charging`; fades and slides up on first composition.
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
    /**
     * Whether the photo box is showing. Passed in (not collected here) so the hero doesn't
     * recompose on unrelated state changes.
     */
    photoExpanded: Boolean = true,
    /**
     * The hero's trailing header control. Null keeps the hero's own expand/collapse chevron; set (the
     * grid's full-screen button) it SWAPS that chevron out, since the photo toggle has nowhere to go
     * once the control is taken.
     */
    headerAction: PebbleHeaderAction? = null,
) {
    // On the phone the hero is a pebble on PebbleShell; its body collapses with the shared
    // collapseEnter/collapseExit transition, so no photo collapse logic is needed here.
    val readout = chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric)
    // 0 collapsed, 1 expanded: the one value the readout morph (type sizes, gaps, paddings, header
    // reservation) lerps on. Critically damped: this drives a SIZE, and an under-damped spatial
    // spring would overshoot type size.
    // The hero's body is open when the user's own choice says so.
    val heroShown = photoExpanded
    val heroTState = animateFloatAsState(
        targetValue = if (heroShown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "heroMorph",
    )
    // NOT read in this body: every per-frame use reads heroTState inside its own slot (background,
    // foreground, trailing), so a frame of the morph recomposes only those slots, never the whole card.
    // Read as thresholds, so the slots that only switch at a point in the morph change once, not per frame.
    val trailingGone by remember { androidx.compose.runtime.derivedStateOf { heroTState.value > 0.35f } }

    // Delayed only going IN (photoExpanded true); collapsing fades it out immediately, so the card
    // doesn't look like it's still finishing an entrance while it closes. This is a real wall-clock
    // delay (tween + delayMillis), not a fraction of heroT, because heroT is spring-driven with no
    // fixed duration to carve a fraction out of.
    fun statusAlpha(heroT: Float): Float {
        val t = ((heroT - 0.15f) / 0.5f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
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

    // Follows the morph: the name travels from the surface colour to the light one the scrim is
    // built for; snapping would flash a white name on a white card.
    // In eighths: the card itself recomposes eight times over the morph for its title colour, not every frame.
    val titleStep by remember { androidx.compose.runtime.derivedStateOf { (heroTState.value * 8f).roundToInt() / 8f } }
    // What the photo looks like behind the title and the readout decides their colours (dark text on a light photo, light on a dark
    // one), and a gradient or a photo that changes behind the text is covered by judging each band's worst part.
    val photoTone by rememberHeroPhotoTone(imageUrl)
    // A screen reader's actions menu on the readout: the three things most wanted of a car, without hunting for the buttons.
    val doorLocked = status?.doorLock
    val spokenActions = remember(v.vin, v.name, doorLocked) {
        listOf(
            CustomAccessibilityAction("Refresh ${v.name}") { vm.refreshStatus(v); true },
            CustomAccessibilityAction(if (doorLocked == true) "Unlock ${v.name}" else "Lock ${v.name}") {
                if (doorLocked == true) vm.unlock(v) else vm.lock(v)
                true
            },
        )
    }
    val heroTitleColorNow = heroTitleColor(photoTone, titleStep)
    CompositionLocalProvider(LocalHeroTone provides photoTone) {
        PebbleShell(
            expanded = heroShown,
            onToggle = { vm.togglePebble(v, com.bloo.bluelink.data.HERO_PHOTO_SECTION) },
            icon = AppIcons.DirectionsCar,
            title = v.name,
            modifier = modifier,
            titleColor = heroTitleColorNow,
            // The hero's own expand/collapse chevron, unless a header control was supplied (the grid's
            // full-screen button), which takes that slot: one trailing control, never two.
            headerAction = headerAction,
            canToggle = headerAction == null,
            // The ONLY pebble that grows its title. Here the title is the car's NAME and the card
            // becomes a photo of that car, so the name scaling up reads as the card taking over. On
            // "Location" or "Diagnostics" it is a heading resizing for no reason.
            growTitleOnExpand = true,
            // No `summary`: the bar is the summary. The photo is the card's BACKGROUND, running behind
            // the header row, with the title and chevron overlaid.
            background = {
                val t = heroTState.value
                HeroBackground(cardCoords, v, imageUrl, heroShown, height, t, false, readout, hoisted, statusAlpha(t), report(expandedNumbers))
            },
            // The travelling numbers: a plain lerp between the two anchors in this Box's space.
            // Rendered as `foreground` so they cross the header row above its buttons, not under.
            foreground = {
                val t = heroTState.value
                HeroForeground(collapsedNumbers, expandedNumbers, hoisted, t, readout, statusAlpha(t), actions = spokenActions)
            },
            // Collapsed: name, percentage and range on one row, with the bar directly under it. No
            // shared element: SharedTransitionLayout is a LookaheadScope, which adds a lookahead pass
            // per placement and costs car-swipe frames.
            titleTrailing = if (trailingGone && !hoisted) null else {
                {
                    HeroCollapsedNumbers(
                        readout, heroTState.value,
                        onPositioned = report(collapsedNumbers),
                        hoisted = hoisted,
                    )
                }
            },
            summary = null,
            headerContent = {
                // A reservation, not content: stops the header text from sitting on the readout while
                // the card is short. Derived from the readout's tokens (pct line + gap + bar), not a
                // constant.
                val collapsedReadoutHeight = 2.dp + ChargeBarHeight
                // + the readout's own bottom inset. The readout occupies collapsedReadoutHeight of
                // CONTENT and then sits HeroReadoutBottomInset above the card's edge, so reserving only
                // the content left the reservation one gap short and the readout's top edge crossed
                // into the title's row.
                val full = collapsedReadoutHeight + HeroReadoutBottomInset
                // The reservation shrinks in the LAYOUT phase (the morph is read there, not in this slot's
                // composition), so the header content is re-measured per frame but never recomposed. No
                // graphicsLayer: an alpha on an empty Box is a layer allocation per frame for no pixels.
                Spacer(
                    Modifier.fillMaxWidth().layout { measurable, constraints ->
                        val h = lerp(full, 0.dp, heroTState.value).roundToPx().coerceAtLeast(0)
                        val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(p.width, h) { p.place(0, 0) }
                    },
                )
            },
        ) {
            // Empty by design. Everything the expanded state adds -- the photo and the readout over its
            // lower edge -- is in `background`, because both need to be positioned against the IMAGE
            // rather than stacked under the header.
            Spacer(Modifier.height(0.dp))
        }
    }
}
