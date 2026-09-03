@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The floating car/screen name, REBUILT from scratch (see git history for the three earlier
 * designs this replaces).
 *
 * Every earlier version tried to make ONE Text object fly between a measured inline position and
 * a measured corner position, springing along a live-interpolated path -- which meant carrying
 * two separate measured anchors in sync, correcting for a frame of scroll lag between them,
 * subtracting a container's own root offset for the one caller whose container wasn't
 * composition root, and a whole separate "shared badge handed off between pages" identity dance
 * to avoid paying for that machinery once per visible card. Every one of those pieces was a real,
 * independently-reported bug at some point (see the old file's own doc comments) because every
 * one of them was a place two numbers computed two different ways had to agree, every frame.
 *
 * This version still has ONE number that has to be right: is the inline title's top edge above
 * the dock line ([FloatingTitle.docked], with hysteresis). But unlike the very first rebuild
 * of this file, it does NOT give up the literal flight -- it gets it back without the old bugs,
 * because the two things that made it fragile before are gone: there is no longer a single Text
 * object trying to serve both roles (inline AND docked), and there is no longer a shared badge
 * whose identity has to hand off between pager pages. Instead:
 *  - The INLINE title is the real, visible title -- not a hidden clone something else flies
 *    from -- and reports its own live root position on every layout pass
 *    ([FloatingTitle.onPositioned]) while doing nothing but a plain fade of its own as it docks.
 *    Deliberately no motion here: see [reportsToFloatingTitle]'s own doc for why splitting the
 *    motion across both elements made the position this reports drift from what the eye actually
 *    saw, which was itself a seam.
 *  - The DOCKED PILL ([FloatingTitlePill]) is its own separate element with its own fixed
 *    position, its own fixed (non-photo-adaptive) colour, and its own copy of the name -- and it
 *    carries the ENTIRE visible motion: it flies in from that live reported position to its own
 *    resting corner spot (and back out to it on undock), growing from a fraction of its own size
 *    up to full size along the way, a real `Animatable<Offset>` + scale, not a generic slide/scale
 *    guess. One pixel value (the inline title's last known position) is the only thing that has to
 *    be right for this to read as one continuous move; everything past that is an ordinary tween.
 * Each page still owns its own [FloatingTitle] instance -- no shared/hoisted identity handed
 * between pager pages -- so the expensive glass chrome still only ever composes while that page's
 * own pill is actually visible or mid-flight. What differs from a plain "swipe past it" pager,
 * though: while a merely pre-composed neighbour page is still mid-swipe-in, its own pill stays
 * suppressed even if docked (see `settled` on [FloatingTitlePill]) so swiping between two already-
 * docked pages reads as one badge staying at the corner, crossfading, rather than two independent
 * pills sliding past each other.
 */

/** How far past the dock line the title has to scroll back before [FloatingTitle.docked]
 *  releases again -- stops a scroll position resting exactly on the line from flickering the
 *  pill in and out on sub-pixel jitter. Shared by every construction site so the debounce feels
 *  the same everywhere. */
internal val TitleDockHysteresis = 8.dp

/**
 * Tracks whether ONE scrolling title has crossed the dock line, with hysteresis. That is the
 * entire state this system needs to share between the inline title (which reports its own
 * position) and the corner pill (which reads whether to show itself) -- no position, no colour,
 * no font-scale plumbed through here any more; the inline title already knows all of that about
 * itself and draws itself directly.
 *
 * `topInsetPx` is a plain mutable field, not a constructor value, so a caller can push a changed
 * status-bar inset (rotation, fold/unfold) in with a `SideEffect` without discarding the docked
 * state that goes with it.
 */
@Stable
internal class FloatingTitle(private val hysteresisPx: Float) {
    var topInsetPx: Float = 0f
    private val dockedState = mutableStateOf(false)
    val docked: State<Boolean> = dockedState

    // Where the inline title last actually was, in root (window) coordinates -- kept live even
    // while it's faded to invisible (the modifier that reports this never leaves layout, only
    // fades -- see `reportsToFloatingTitle`), so [FloatingTitlePill] always has a real, current
    // start point to fly from/to instead of a guessed direction. This is the one piece of state
    // that makes the dock/undock hand-off read as one continuous move rather than two
    // independently-timed animations that merely overlap.
    private val lastInlinePositionState = mutableStateOf(Offset.Zero)
    val lastInlineRootPosition: State<Offset> = lastInlinePositionState

    /** Called from the inline title's own `onGloballyPositioned` every layout pass. */
    fun onPositioned(rootPosition: Offset) {
        lastInlinePositionState.value = rootPosition
        dockedState.value = com.bloo.uicommon.shouldDock(
            rootPosition.y,
            topInsetPx,
            dockedState.value,
            hysteresisPx,
        )
    }
}

/** The default for [FloatingTitlePill]'s `settled` parameter -- a `State<Boolean>` that's always
 *  true, for every caller outside GarageScreen's own collapsed pager, without needing a `remember`
 *  or any snapshot machinery just to say "there's no paging concept here." */
internal val AlwaysSettled: State<Boolean> = object : State<Boolean> {
    override val value: Boolean get() = true
}

/** Null everywhere except inside a screen that hosts a floating title -- the garage screen's
 *  car pages, the Settings screen, and ExpandedCar. Each of those constructs its own
 *  [FloatingTitle] (no more shared/hoisted instance handed between pages -- see this file's own
 *  doc) and provides it here so the inline title composable, several layers down, can report its
 *  position without threading a parameter through every intermediate composable. */
internal val LocalFloatingTitle = compositionLocalOf<FloatingTitle?> { null }

/** Shared duration for the dock/undock hand-off, used by both the inline title's own motion
 *  and [FloatingTitlePill]'s enter/exit so the two independently-animated pieces read as one
 *  continuous event instead of two out-of-step fades. */
private const val TitleDockMillis = 220

/**
 * Modifier for the INLINE title: reports its own live position to [title] every layout pass (the
 * one number [FloatingTitlePill] flies from/to) and fades itself out as it docks -- a PLAIN fade,
 * deliberately no motion of its own. [FloatingTitlePill] is the thing that moves and grows now
 * (see its own doc): giving this element its own independent rise/shrink used to fight that,
 * two things visibly moving in similar-but-not-quite-matching ways. It also would have made the
 * position this reports (the plain, untransformed layout position `onGloballyPositioned` sees --
 * graphicsLayer transforms never affect it) drift away from where the eye actually saw the text,
 * a real source of the seams this file exists to remove. One element fades in place; the other
 * carries the entire visible motion; nothing here has to line up with the pill's own timing frame
 * by frame, because there's nothing here to keep in sync in the first place. Entirely draw-phase
 * (`graphicsLayer` only) so the docked spring running doesn't recompose the whole title -- only
 * redraws this node.
 */
@Composable
internal fun Modifier.reportsToFloatingTitle(title: FloatingTitle?): Modifier {
    if (title == null) return this
    val docked by title.docked
    val alpha by animateFloatAsState(
        if (docked) 0f else 1f,
        animationSpec = tween(TitleDockMillis),
        label = "inlineTitleFade",
    )
    return this
        .onGloballyPositioned { title.onPositioned(it.positionInRoot()) }
        .graphicsLayer { this.alpha = alpha }
}

/** How small the pill starts (and shrinks back to on undock) relative to its own full size --
 *  small enough that its chrome (the rounded glass background, not just its text) reads as
 *  emerging from roughly the inline title's own footprint instead of popping in at full size
 *  and then sliding, which is what a flight with no scale at all looked like. */
private const val PillFlightScale = 0.55f

/**
 * The corner pill: flies into place the instant [title] docks, and back out the instant it
 * undocks -- a REAL position AND size flight, from wherever the inline title last reported itself
 * to this pill's own resting corner spot, not a generic slide/scale guess. [FloatingTitle] already
 * tracks that live position (see its own doc); this is the only place that reads it. Composed only
 * while actually visible or mid-flight (`composed`, below) -- an undocked page's own pill still
 * costs nothing at rest, the same guarantee the old hoisted-badge machinery existed to provide,
 * without needing a shared instance handed between pages to get it.
 */
@Composable
internal fun BoxScope.FloatingTitlePill(
    title: FloatingTitle,
    cornerX: Dp,
    cornerY: Dp,
    reserveEnd: Dp,
    maxWidth: Dp,
    onClick: () -> Unit,
    // True when this is the pager's currently SETTLED page, or there's no paging concept at all
    // (every caller other than GarageScreen's own collapsed pager leaves this at its default).
    // While a merely pre-composed neighbour page is mid-swipe-in, its own pill stays suppressed
    // here even if THAT page's own scroll position is independently docked -- otherwise swiping
    // between two already-docked pages showed two separate pills sliding past each other instead
    // of one badge staying put at the corner while the name underneath it crossfades. See
    // GarageScreen's own call site for how `settled` is computed.
    //
    // A `State<Boolean>`, not a plain `Boolean` -- GarageScreen's own `settled` is derived from
    // `pager.settledPage`, and reading THAT as a bare value anywhere above this function would
    // subscribe that whole caller (VehicleDetailContent/SettingsScreen, everything they render)
    // to recompose on every settle, the exact per-page full-recompose bug this file's sibling
    // pager already had to fix once (see GarageScreen's own `onPageDockedChanged` doc). Passing
    // the `State` object itself down unread, and reading `.value` only here, keeps that
    // recomposition scoped to just this pill.
    settled: State<Boolean> = AlwaysSettled,
    content: @Composable () -> Unit,
) {
    val docked by title.docked
    val wantsVisible = docked && settled.value
    val haptics = LocalHaptics.current
    val shape = remember { RoundedCornerShape(50) }
    val density = LocalDensity.current
    // This pill's own resting position, in the same root coordinate space `title` measures the
    // inline title in -- cornerX/cornerY are already root-relative padding on a TopStart-aligned
    // Box that (at every real call site) sits at the screen's own root origin, so no separate
    // measurement is needed for this half of the flight.
    val restPx = remember(cornerX, cornerY, density) {
        with(density) { Offset(cornerX.toPx(), cornerY.toPx()) }
    }
    val flightOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val flightAlpha = remember { Animatable(0f) }
    val flightScale = remember { Animatable(PillFlightScale) }
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(wantsVisible) {
        if (wantsVisible) {
            composed = true
            flightOffset.snapTo(title.lastInlineRootPosition.value - restPx)
            flightScale.snapTo(PillFlightScale)
            // Position and scale spring, like every other piece of motion in this app --
            // SoftDamping is high enough that neither visibly overshoots, but a spring still
            // settles with the same weight/acceleration curve as the button-press growth, the
            // coach-mark entrance, every other bit of chrome that moves in Bloo. A flat tween
            // here was correct in duration but a different MOTION LANGUAGE from everything
            // around it -- part of why the flight still didn't read as native to the app.
            // Alpha stays a tween: a springing alpha can dip visibly below 0/above 1 well before
            // settling, unlike position/scale where that's just a normal, expected overshoot.
            launch { flightOffset.animateTo(Offset.Zero, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)) }
            launch { flightScale.animateTo(1f, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)) }
            flightAlpha.animateTo(1f, tween(TitleDockMillis))
        } else if (composed) {
            launch { flightOffset.animateTo(title.lastInlineRootPosition.value - restPx, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)) }
            launch { flightScale.animateTo(PillFlightScale, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)) }
            flightAlpha.animateTo(0f, tween(TitleDockMillis))
            composed = false
        }
    }
    if (composed || wantsVisible) {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = cornerX, top = cornerY, end = reserveEnd)
                .offset { IntOffset(flightOffset.value.x.roundToInt(), flightOffset.value.y.roundToInt()) }
                .graphicsLayer {
                    alpha = flightAlpha.value
                    // Top-start origin -- matches what the offset above means (a translation of
                    // this element's own top-left corner), so scaling and translating together
                    // read as one continuous grow-and-move instead of the scale visibly fighting
                    // the position for a moment right at the start.
                    scaleX = flightScale.value
                    scaleY = flightScale.value
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .heightIn(min = ButtonTargetHeight)
                .widthIn(max = maxWidth)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()), shape)
                .ambientRing(shape)
                .dropShadow(shape)
                // Chrome before clip -- dropShadow's blur bleeds outside the shape by design;
                // clip only bounds the ripple below.
                .frostedRim(shape)
                .clip(shape)
                .clickable { haptics?.click(); onClick() }
                // Reports this pill's real bounds to the floating registry, so the page dots can
                // get out of its way -- see FloatingSystem.kt's own doc on `nameDocked`. Always
                // `active = true` here: this Row is only ever composed while actually showing or
                // mid-flight in the first place.
                .floatingElement(FloatingIds.Title, active = true)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Fixed colour, not a live photo-adaptive morph: once docked, this pill sits over the
            // app's own chrome (the status bar area), never over the car photo the inline title's
            // own colour is tuned to stay legible against -- so unlike the old shared-Text design,
            // there is nothing here that needs to track the hero's photo-expand colour spring at
            // all. One less piece of cross-composable state.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        }
    }
}
