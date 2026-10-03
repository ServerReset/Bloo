
package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
// No `motionScheme` import: it is a member of the MaterialTheme object (verified as
// MaterialTheme.getMotionScheme in the resolved material3 AAR), as are defaultEffectsSpec
// and defaultSpatialSpec on MotionScheme. Screens.kt imports none of them either.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
// State<T>'s `by` delegate isn't a member -- it resolves to this file-scope operator
// extension, which the compiler will not find without an explicit import (unlike most of
// this file's other extension functions, which show up as unresolved-reference errors
// instead of this one's more oblique "has no method getValue... cannot serve as a delegate").
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Drop-in replacement for [PebbleShell]'s plain `Column` of body rows: gives every DIRECT
 * CHILD its own independent pop-in/pop-out as the pebble opens and closes, cascading
 * top-to-bottom, without any of PebbleShell's ~15 callers having to change a single row of
 * their own content -- that is the whole point of putting this here rather than asking
 * every pebble to wrap its own rows in [PopVisible]. "No animation on the text and UI
 * elements in the pebbles as they're revealed or hidden" was reported after [PopVisible]
 * only covered the handful of call sites that had been individually converted; this instead
 * makes EVERY row of EVERY pebble cascade, for free, by changing the one shared container.
 *
 * ONE animated value drives every child, rather than each row owning an [AnimatedVisibility]
 * of its own -- a pebble can have a dozen rows, and a dozen independent animation tickets is
 * a dozen times the per-frame cost of one shared progress value that every child's
 * [Placeable.PlacementScope.placeWithLayer] block reads and remaps into its own little window
 * (see [PebbleStaggerSpan]). That remap is what turns one linear 0..1 value into a cascade,
 * ON THE WAY IN: row *i* of *n* doesn't start moving until progress passes
 * `i/n * PebbleStaggerSpan`, and is fully settled by the time progress reaches
 * `i/n * PebbleStaggerSpan + (1 - PebbleStaggerSpan)`. On the way OUT every row instead reads
 * the SAME un-windowed progress directly -- see the `closing` local in the function body for
 * why staggering the close the same way actively made it worse, not just less staggered.
 *
 * That progress comes from [transition] -- the SAME `Transition<EnterExitState>` the caller's
 * own [AnimatedVisibility] is already running for its height/fade, passed in from inside that
 * call's content lambda (where it's available as `AnimatedVisibilityScope.transition`) -- NOT
 * a private [Animatable] driven by its own `LaunchedEffect`, which is what the first version
 * of this did and which is why "the collapse effect doesn't work" was a real bug, not a tuning
 * problem: `AnimatedVisibility` only waits for animations that live in its OWN `Transition`
 * before removing its content from composition (this file's own note on [collapseEnter] had
 * already flagged this exact trap). A private `Animatable` is invisible to that -- the card
 * would finish shrinking and get torn out of composition on whatever its OWN schedule was,
 * mid-fade, regardless of how long the row animation asked for. Registering this progress
 * with `transition.animateFloat` instead means it graduates into a first-class participant in
 * that same `Transition`: `AnimatedVisibility` cannot consider the exit "finished" -- and so
 * cannot remove the content -- until THIS animation reports finished too. No duration to
 * guess, no race to lose.
 *
 * A custom [Layout] rather than a real `Column`, because the per-child transform has to be
 * applied at PLACEMENT (`placeWithLayer`'s `layerBlock`), and that API belongs to
 * `Placeable.PlacementScope` -- there is no way to reach it by composing ordinary children
 * with a `Modifier` the way every other row-level effect in this file works. The measure
 * policy below deliberately mirrors what a loose (non-`fillMaxHeight`) `Column` with
 * `Arrangement.spacedBy(verticalGap)` already does for [PebbleShell]'s body -- same width
 * behaviour (children get the incoming max width, nothing stretched), same wrap-content
 * height -- so swapping it in changes nothing about layout, only about how each child draws
 * in on its way in.
 *
 * Scale-and-fade per child, same as [PopVisible] and for the same reason: the outer
 * `AnimatedVisibility` is ALREADY animating the container's height, and a per-child height
 * change here would be a second party fighting that same dimension.
 */
@Composable
internal fun StaggeredRevealColumn(
    transition: Transition<EnterExitState>,
    modifier: Modifier = Modifier,
    verticalGap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // targetValueByState, not a plain visible/1f-0f Animatable -- transition.animateFloat
    // seeds and drives this off transition.currentState, which for a freshly-ENTERING pebble
    // is PreEnter (mapped to 0f here), NOT Visible -- so the seeding bug the old Animatable-
    // based version needed a hand-written workaround for ("must seed at 0f, never
    // conditionally") simply doesn't exist with this API: the library already gets the first
    // frame right.
    val progress by transition.animateFloat(
        label = "pebbleRowCascade",
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                // A short head start for the CARD, not the rows -- asked for explicitly: the
                // pop should read as arriving just after the pebble has started opening, not
                // racing it from the same frame. collapseEnter's own bounce has no fixed
                // duration (it's a spring, not a tween), so this can't be timed to "wait until
                // the card is exactly this far open" -- a flat delay is what's available,
                // short enough that the rows are still clearly popping in DURING the open
                // rather than only once it's fully settled.
                //
                // LinearEasing, deliberately, even though the result should still look eased
                // -- each row's own `local` below is a REMAP of a narrow slice of this value
                // into its own 0..1, and remapping a slice of an already-eased curve gives
                // that slice a distorted, not-actually-eased shape (steep in some windows,
                // flat in others, depending on where in the source curve the slice happened
                // to land). A linear source makes every row's slice equally linear, so
                // applying ONE consistent ease per row (the smoothstep in the placement block
                // below) gives every row's own pop the identical shape -- which is what makes
                // them read as repeated, distinct STEPS rather than one blurry wave.
                tween(durationMillis = 480, delayMillis = 90, easing = LinearEasing)
            } else {
                // No delay on the way out, but NOT a short duration either -- 220ms first
                // shipped on the (now outdated) assumption that shorter was safer against
                // being torn out of composition early. That race is gone (this progress lives
                // in the SAME Transition as the card's own height/fade now), but 220ms turned
                // out to have created a NEW problem: divided across PebbleStaggerSpan's narrow
                // per-row windows, each row got roughly 220ms * (1 - 0.85) =~ 33ms to fade in
                // -- too fast to read as a step at all, so the whole cascade looked like one
                // instant cut rather than a hide animation. 400ms gives each row a ~60ms
                // window instead, close to the ~similar per-row math the OPEN side's 480ms
                // already uses (480ms * 0.15 =~ 72ms) rather than a fraction of it.
                tween(durationMillis = 400, easing = LinearEasing)
            }
        },
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // Windowing (see [PebbleStaggerSpan]) only applies on the way IN. On the way out it was
    // making things WORSE, not just less staggered: a row whose window starts late (`start`
    // close to 0.85) has `raw` pinned at its clamped max of 1 for almost the entire close --
    // progress has to fall below that row's own `start` before `raw` even begins dropping --
    // so most rows sat fully opaque for most of the collapse and then cut to invisible in the
    // last sliver of it, which reads as "nothing is happening, then it's just gone," not a
    // fade. Closing instead maps every row to the SAME un-windowed value: `progress` itself,
    // smoothly 1 -> 0 across the whole close duration, so every row visibly fades together for
    // the entire collapse rather than a handful of them cutting out unnoticed near the end.
    val closing = transition.targetState != EnterExitState.Visible
    val gapPx = with(LocalDensity.current) { verticalGap.roundToPx() }
    // Takes `content` with the same ColumnScope receiver PebbleShell's own body always has
    // (every pebble's content lambda is already typed that way), via NoOpColumnScope below --
    // real Column.weight is a no-op here regardless of that shim, because it only redistributes
    // space in a HEIGHT-BOUNDED Column, and this container has always been wrap-content. The
    // shim exists purely so `content` type-checks against
    // callers written for `ColumnScope`, not to add real weight/align support.
    Layout(content = { NoOpColumnScope.content() }, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtMost(constraints.maxWidth)
        val gaps = gapPx * (placeables.size - 1).coerceAtLeast(0)
        val height = (placeables.sumOf { it.height } + gaps).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            val n = placeables.size
            var y = 0
            placeables.forEachIndexed { i, p ->
                val start = if (n <= 1) 0f else (i.toFloat() / n) * PebbleStaggerSpan
                // `progress` is read INSIDE the layerBlock, not out here in the placement body
                // -- layerBlock is deferred to the draw phase, so reading it there means only
                // drawing re-runs as the transition ticks. Reading it out here instead (where
                // `start`/`y` are computed) would make the STATE read part of layout, and every
                // one of the transition's frames would re-trigger a full remeasure of every
                // child in this pebble to move a value that only ever changes how they're drawn.
                p.placeWithLayer(0, y) {
                    // No windowing on the way out (see the `closing` comment above) --
                    // `progress` itself, un-remapped, is every row's shared raw value.
                    val raw = if (closing) {
                        progress.coerceIn(0f, 1f)
                    } else {
                        ((progress - start) / (1f - PebbleStaggerSpan)).coerceIn(0f, 1f)
                    }
                    // Smoothstep for ALPHA specifically -- opacity has nowhere to overshoot TO
                    // (a value past 1 just clips back to fully opaque), so a plain ease with no
                    // overshoot is the right shape for it either way.
                    val local = raw * raw * (3f - 2f * raw)
                    alpha = local
                    // SCALE gets its own [pebbleRowOvershoot] shape instead of reusing `local`
                    // -- asked for explicitly ("the bounce on it feel like different systems"):
                    // the card itself overshoots and settles (PebbleBounceDamping), but a plain
                    // smoothstep glides straight to its target with no overshoot at all, so the
                    // rows and the card read as two different kinds of motion even when they're
                    // triggered by the same open/close. A small scale overshoot gives every row
                    // the same overshoot-then-settle CHARACTER as the card, not just the same
                    // rough timing -- which is what actually reads as "one system," on both the
                    // way in and the way out (this shape is symmetric in `raw`, so closing pops
                    // each row very slightly larger before it shrinks away, mirroring how it
                    // grew slightly larger than its target on the way in).
                    val scaleT = pebbleRowOvershoot(raw)
                    // 0.7 -> 1.0, not 0.85 -> 1.0: a 15%-of-size scale change is easy to miss
                    // next to the alpha fade doing most of the visible work: wider so the pop
                    // reads as its own distinct motion rather than a fade with a barely-there
                    // size wobble riding along.
                    scaleX = 0.7f + 0.3f * scaleT
                    scaleY = 0.7f + 0.3f * scaleT
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                y += p.height + gapPx
            }
        }
    }
}
