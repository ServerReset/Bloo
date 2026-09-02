@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow

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
 * This version has ONE number that has to be right: is the inline title's top edge above the
 * dock line. Everything else is two ordinary, independent pieces of UI:
 *  - The INLINE title is the real, visible title -- not a hidden clone something else flies
 *    from -- and just fades itself out as it docks.
 *  - The DOCKED PILL is a plain corner badge that fades/slides itself in when docked, with its
 *    own fixed position, its own fixed (non-photo-adaptive) colour, and its own copy of the name.
 * Two ordinary AnimatedVisibility-driven pieces of UI, each right by construction, instead of one
 * continuously-recomputed position shared between two renderers.
 *
 * The trade made for that simplicity: no more literal "flies from exactly here to exactly there"
 * motion, and no more shared badge hoisted across pager pages (each page owns its own, cheap
 * enough now that the expensive glass chrome only ever composes while that page's OWN pill is
 * docked or transitioning -- see [FloatingTitlePill]). What's kept: the same corner, the same
 * hysteresis so scroll jitter at the threshold can't flicker it, the same page-dots collision
 * hookup, and the same one-line-per-surface integration shape.
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

    /** Called from the inline title's own `onGloballyPositioned` every layout pass. */
    fun onPositioned(rootPosition: Offset) {
        dockedState.value = com.bloo.uicommon.shouldDock(
            rootPosition.y,
            topInsetPx,
            dockedState.value,
            hysteresisPx,
        )
    }
}

/** Null everywhere except inside a screen that hosts a floating title -- the garage screen's
 *  car pages, the Settings screen, and ExpandedCar. Each of those constructs its own
 *  [FloatingTitle] (no more shared/hoisted instance handed between pages -- see this file's own
 *  doc) and provides it here so the inline title composable, several layers down, can report its
 *  position without threading a parameter through every intermediate composable. */
internal val LocalFloatingTitle = compositionLocalOf<FloatingTitle?> { null }

/**
 * Modifier for the INLINE title: reports its own position to [title] every layout pass, and
 * fades itself out as it docks (the corner pill is fading in over the same window -- see
 * [FloatingTitlePill]'s own animation spec, matched here so the hand-off reads as one continuous
 * cross-fade rather than two independently-timed ones). The fade itself is read in a
 * `graphicsLayer` lambda, draw-phase only, so the docked spring running doesn't recompose the
 * whole title -- only redraws this one node.
 */
@Composable
internal fun Modifier.reportsToFloatingTitle(title: FloatingTitle?): Modifier {
    if (title == null) return this
    val docked by title.docked
    val alpha by animateFloatAsState(
        if (docked) 0f else 1f,
        animationSpec = tween(if (docked) 160 else 220),
        label = "inlineTitleFade",
    )
    return this
        .onGloballyPositioned { title.onPositioned(it.positionInRoot()) }
        .graphicsLayer { this.alpha = alpha }
}

/**
 * The corner pill: fades and slides into place the instant [title] docks, and back out the
 * instant it undocks. A plain [AnimatedVisibility], so the (real) cost -- the glass chrome's
 * shadow/ring/frosted background -- only ever composes while actually visible or mid-transition,
 * the same guarantee the old hoisted-badge machinery existed to provide, without needing a shared
 * instance handed between pages to get it: an undocked page's own pill costs nothing at rest
 * regardless of how many OTHER pages are simultaneously visible.
 */
@Composable
internal fun BoxScope.FloatingTitlePill(
    title: FloatingTitle,
    cornerX: Dp,
    cornerY: Dp,
    reserveEnd: Dp,
    maxWidth: Dp,
    onClick: () -> Unit,
    extraContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val docked by title.docked
    val haptics = LocalHaptics.current
    val shape = remember { RoundedCornerShape(50) }
    AnimatedVisibility(
        visible = docked,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = cornerX, top = cornerY, end = reserveEnd),
        // Grown from the leading edge, matching the inline title's own left-anchored grow/shrink
        // (see PebbleShell's identical transformOrigin) so nothing appears to drift sideways.
        enter = fadeIn(tween(220)) +
            scaleIn(0.7f, transformOrigin = TransformOrigin(0f, 0.5f)) +
            slideInVertically(tween(220)) { -it / 4 },
        exit = fadeOut(tween(160)) + scaleOut(0.85f, transformOrigin = TransformOrigin(0f, 0.5f)),
    ) {
        Row(
            Modifier
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
                // `active = true` here: this Row is only ever composed while AnimatedVisibility
                // has it showing or animating in the first place.
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
            extraContent?.invoke(this)
        }
    }
}
