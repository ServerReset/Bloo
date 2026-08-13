@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The phone UI's shared design vocabulary: the sizes, colours and motion that more than one
 * screen has to agree on.
 *
 * Extracted from Screens.kt, which is 14.6k lines and 28% of this codebase. That extraction
 * is worth being honest about: the empirical literature does NOT support splitting a large
 * file to reduce defects (every study that controls for size either reverses the effect or
 * dissolves it, and none performs a refactoring intervention at all), and the build-speed
 * argument does not survive verification either. What a split does buy is navigability, and
 * it is the prerequisite for any further split of that file, because Kotlin's top-level
 * `private` is FILE-scoped -- so shared helpers have to become `internal` and live somewhere
 * common BEFORE any screen can move out from under them.
 *
 * This file is therefore deliberately the least interesting one: no logic, no layout, just
 * the values that would silently diverge if each screen kept its own copy. That is not
 * hypothetical here -- `AdvancedModeStiffness` below already drifted from the shared collapse
 * spec while it was buried at line ~10,700 of the monolith.
 *
 * `internal`, not `public`: it is the narrowest visibility that survives a file boundary.
 */

// ---- Colour -------------------------------------------------------------------

// Was a phone-only re-declaration of the same hex values shared/BlooColors.kt already
// centralizes (bit-identical today, one edit away from silently diverging like
// chargerLabel's text had).
internal val ChargeGreen = Color(com.bloo.bluelink.data.BlooColors.chargeGreen)
internal val ChargeGreenDark = Color(com.bloo.bluelink.data.BlooColors.chargeGreenDark)

/** The charge bar's "topped up" state: the pack has reached its own configured limit,
 *  so the fill reads as done rather than still climbing. See ChargeSegmentBar. */
internal val ChargeBlue = Color(com.bloo.bluelink.data.BlooColors.chargeBlue)
internal val ChargeBlueDark = Color(com.bloo.bluelink.data.BlooColors.chargeBlueDark)

/** The app's muted/secondary-text alpha, applied over LocalContentColor. */
internal const val MutedContentAlpha = 0.7f

/**
 * Text and icons drawn ON the hero's car photo.
 *
 * Fixed rather than theme-derived, because what sits behind it is a photograph and
 * a dark scrim, not a themed surface -- so it is the same in light and dark, and the
 * colour scheme's own `onSurface` is the wrong answer in both. It was what the
 * expanded hero used, which rendered the car's name in near-black on a dark photo.
 *
 * Slightly off pure white: at full white the name reads as harsher than the photo
 * behind it, and every other light-on-dark surface in the app lands here too.
 */
internal val HeroOnPhoto = Color(0xFFF2F2F5)

// ---- Sizing -------------------------------------------------------------------

/** Shared control height: a collapsed pebble matches the lock/unlock button. */
internal val ControlHeight = 76.dp

/** Uniform collapsed-header height so every pebble lines up at the same size. */
internal val PebbleHeaderHeight = ControlHeight
internal val PebbleCornerCollapsed = 38.dp
internal val PebbleCornerExpanded = 20.dp

/** The charge bar's height, shared by every surface that draws this bar so the
 *  proportions read as one component rather than five near-misses. */
internal val ChargeBarHeight = 18.dp

/** The gap reserved on both sides of every internal boundary in the charge bar, so
 *  each segment (fill, track-to-limit, dim-track-past-it) is its own visibly
 *  separate, independently-rounded piece rather than any two reading as one shape. */
internal val ChargeSegmentGap = 5.dp

/**
 * Gap between the hero's collapsed readout and the bottom edge of its card.
 *
 * Named because it is needed in TWO places that must agree: the readout's own bottom padding,
 * and the height the header reserves so its title does not sit on top of the readout. When it
 * was a bare `6.dp` at the padding site only, the reservation accounted for the readout's
 * content but not for this inset, so the reserved space was one gap short of what the node
 * actually occupies. Two copies of a spatial constant is how this slot has gone wrong every
 * previous time; one name means the reservation cannot drift from the thing it reserves for.
 */
internal val HeroReadoutBottomInset = 14.dp

/** Gap between settings cards. Lives inside SettingsCard as bottom padding rather than in
 *  the parent's arrangement, so a card collapsing to zero height takes its gap with it --
 *  see the comment at that padding for what went wrong when the parent owned it. */
internal val SettingsCardGap = 10.dp

// ---- Motion -------------------------------------------------------------------

// Aliases onto :uicommon so the phone and the watch read as the same controls.
internal val SoftDamping get() = com.bloo.uicommon.SoftDamping

// The morph button's two corner states, shared with the watch so both surfaces read as the
// same control. Aliased the same way SoftDamping above is.
internal val PillCornerPercent get() = com.bloo.uicommon.PillCornerPercent
internal val MorphedCornerPercent get() = com.bloo.uicommon.MorphedCornerPercent

/** Shared spring stiffness for the Simple/Advanced mode switch's card expand/collapse (the
 *  outer settings column, each card's own animateContentSize, and the advanced-only cards'
 *  enter/exit) -- slower than Spring.StiffnessLow for a slightly longer, calmer settle,
 *  paired with [SoftDamping] for minimal bounce. All of these must share one spec or the
 *  pieces visibly settle at different times/feels. */
internal const val AdvancedModeStiffness = 130f

/**
 * The app's collapse/expand transition, supplied by the Material theme.
 *
 * M3 Expressive delivers motion as a theme subsystem: MaterialTheme.motionScheme exposes six
 * spec factories, a 2x3 matrix of SPATIAL (bounds, size, scale, shape -- allowed to
 * overshoot) against EFFECTS (colour, alpha -- must not) crossed with fast/default/slow.
 * This app already opts into MaterialExpressiveTheme, so those resolve to
 * MotionScheme.expressive() and were sitting unused.
 *
 * Two things that makes correct, beyond removing hardcoded numbers:
 *
 *  - The height is spatial and the fade is effects, which is the split the spec draws.
 *    Material's stated rule for choosing is interruption: a spring preserves velocity
 *    continuity when the target changes mid-flight, a tween is for preset choreography. A
 *    collapse toggle is re-tappable by definition, so its fade wanted a spring too.
 *  - The durations stop being invented. Every hand-rolled copy has been migrated here (14
 *    call sites), and between them they had fade tweens of 120, 130, 150, 160, 180, 180,
 *    200, 220, 220, 240 and 300ms with no comment anywhere explaining why any of them
 *    differed. Four also SPRANG open and TWEENED shut, which is what made closing feel like
 *    a snap next to a smooth open; six tweened both halves.
 *
 * The one deliberate holdout is `advancedEnter`/`advancedExit` in the settings screen, which
 * keeps [AdvancedModeStiffness] for the height because it reveals a lot at once and wants a
 * calmer settle. It takes its FADE from the same effects spec as this.
 *
 * [expandFrom] is a parameter and not a constant because it is a layout fact, not a timing
 * one: a body under a header should grow downward from its top, while a bubble anchored
 * above the bottom bar should reveal from its bottom. Sites that were on
 * `expandVertically`'s own default pass [Alignment.Bottom] explicitly so this migration
 * changed how things MOVE without changing which way they open.
 *
 * NOT using Modifier.animateContentSize for the height, deliberately -- but not for the
 * reason this comment used to give. It claimed animateContentSize "animates clip bounds, so
 * collapses commonly snap": that mechanism is wrong. Clipping is a DRAW-phase operation and
 * cannot drive layout; animateContentSize animates the size the node REPORTS, and the same
 * "clip bounds" phrasing in the official docs is descriptive shorthand. (With
 * `clip = false` on shrinkVertically the footprint still shrinks, which is the proof.)
 *
 * The real reasons to prefer AnimatedVisibility + shrinkVertically here:
 *  - it is the documented approach for this, and shrinkVertically animates the reported
 *    layout size while measuring the child ONCE at unchanged incoming constraints -- so a
 *    large image and any text inside it are progressively sliced rather than reflowed;
 *  - it REMOVES the node from composition at the end, instead of leaving a fully transparent
 *    one occupying space and reachable by TalkBack.
 *
 * Worth knowing if either token is ever "simplified" to fade-only: expand/shrinkVertically
 * are ALREADY AnimatedVisibility's defaults, so passing fade alone is an explicit opt-OUT of
 * continuous sizing. fadeOut builds a config whose changeSize is null, which leaves
 * sizeAnimation null and makes the measure path report the child's FULL measuredSize on every
 * frame -- then AnimatedVisibility drops it in a single frame once every animation in its
 * Transition finishes. That is precisely the "hangs at the wrong size, then snaps" this
 * project already paid for once. scaleIn/scaleOut do NOT help: they keep the full footprint.
 *
 * Related trap, since heroT is an animate*AsState living OUTSIDE these transitions:
 * AnimatedVisibility can only wait for animations in its OWN Transition, so an independent
 * animation is invisible to it and its content can be removed before that one finishes.
 *
 * A "content should fade in as the pebble uncovers it" step-in effect was tried here twice
 * (first `slowEffectsSpec`, then an explicit 500ms tween) and asked to be removed after
 * neither read as the effect wanted -- a single shared alpha for the whole revealed block
 * can only ever make the WHOLE block dimmer-then-brighter together, never give newly
 * uncovered content its own independent fade-in distinct from what's already visible above
 * it, which is what "steps in as it's uncovered" actually needs. Back to the plain
 * `defaultEffectsSpec` this token had before either attempt.
 *
 * The OPEN half of the height, though, DOES use a dedicated bounce spring rather than
 * `defaultSpatialSpec` -- asked for explicitly ("an animation on the box that like
 * bounces"), and picked over layering a second scale-pulse on top of the resize for the
 * same reason a second animateContentSize was rejected two comments up: two independently
 * sprung animations chasing the same box fight each other every frame. One spring, tuned to
 * actually overshoot, both delivers the bounce and stays the single source of truth for the
 * card's bounds.
 *
 * The CLOSE half went through its own arc: plain (the original), then given its own,
 * more-damped bounce on explicit request, tuned twice (0.85, then 0.72) trying to make that
 * bounce read as connected to the card rather than tacked on -- and it never did. The report
 * that settled it: "the spring on it collapsing feels disjoined from the closing itself."
 * Bouncing on the way IN reads as arrival -- there's real headroom past the target for an
 * overshoot to land in. Bouncing on the way OUT, toward a target of zero, doesn't have an
 * equivalent physical read: there's nothing past "gone" for an overshoot to mean, so however
 * it was damped it kept reading as an effect layered onto the collapse rather than something
 * that WAS the collapse. Back to [PebbleCloseDamping] at 1.0 (critically damped, no
 * overshoot) -- the calm collapse this had before any of that, now sharing [PebbleBounceStiffness]
 * with the open spring (and with the corner morph, still) purely so the TIMING still feels
 * like one card, even though the SHAPE of the motion is deliberately different in each
 * direction now.
 *
 * Overshoot fraction is set by damping ratio alone (stiffness only changes how FAST the
 * spring gets there, not how far past the target it swings). History on [PebbleBounceDamping]:
 * 0.6 + StiffnessMediumLow first shipped and read as too subtle to register as a bounce at
 * all. 0.5 (MediumBouncy) + StiffnessLow went the other way and read as too MUCH -- StiffnessLow
 * gave the swing enough travel time to be seen, but also stretched out how long the overshoot
 * lingers. 0.75 + StiffnessMediumLow overcorrected into invisible again. 0.6 + StiffnessLow
 * landed as visible bounce, but with the corners on a different spring (fixed since -- see
 * PebbleShell) it read as disconnected from the card rather than too big; asked to be "a bit
 * less drastic" once that was fixed, so damping nudged up once more, to 0.68.
 */
// internal, not private: PebbleShell's own corner-radius morph (animateDpAsState, Screens.kt)
// shares these exact springs too -- see that call site for why. Two different physics
// animating the height and the corners of the SAME card at once is what read as "the bounce
// doesn't feel connected to the pebble actually opening" rather than one coherent motion.
internal val PebbleBounceDamping = 0.68f
internal val PebbleCloseDamping = Spring.DampingRatioNoBouncy
internal val PebbleBounceStiffness = Spring.StiffnessLow

@Composable
internal fun collapseEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
        expandVertically(
            spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
            expandFrom = expandFrom,
        )

/**
 * Mirror of [collapseEnter]; see there for why the CLOSE direction settles calmly
 * ([PebbleCloseDamping]) rather than bouncing like the open one does.
 *
 * [fade] defaults true (every other caller of this wants the whole block to fade as it
 * leaves, same as always), but [PebbleShell] passes false for its own body: that body's rows
 * now own their OWN fade individually (see [StaggeredRevealColumn]), and running the
 * block-level fadeOut here AT THE SAME TIME as that per-row fade meant two overlapping
 * opacity animations landing on the same pixels at once -- the coarser, whole-block one
 * dominated what was actually visible, and the finer per-row cascade underneath it was
 * indistinguishable from noise. That is what "closing has no animation on the content" was:
 * a real animation, rendered invisible by a redundant one sitting on top of it. With [fade]
 * off here, the per-row fade is the ONLY thing animating opacity, so it's what's actually seen.
 */
@Composable
internal fun collapseExit(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition {
    val shrink = shrinkVertically(
        spring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness),
        shrinkTowards = shrinkTowards,
    )
    return if (fade) fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) + shrink else shrink
}

/**
 * Independent pop-in/pop-out for ONE row-level element that appears or disappears while its
 * pebble is already open -- a sync badge landing, a preset chip becoming available, a
 * conditional row switching on. Deliberately separate from [collapseEnter]/[collapseExit],
 * which animate a pebble's body as a single block and, per the doc there, cannot stagger: two
 * prior attempts tried to fake a "steps in as it's uncovered" effect with one shared alpha
 * over the whole revealed block and both were reverted, because a shared alpha can only dim
 * the WHOLE block together. This is the different, viable version of that ask -- every call
 * site gets its OWN [AnimatedVisibility] and its own transition state, so row B popping in
 * does not wait on row A's animation or share its alpha with it.
 *
 * Scale-and-fade, not height-based. A pebble body already animates ITS size via
 * `animateContentSize` (see the comment on that modifier in PebbleShell) whenever content
 * inside it changes -- adding expandVertically/shrinkVertically on a row nested inside that
 * would be a second, independently-sprung party changing the same height at the same time,
 * which is exactly the double-animation stutter documented at [collapseEnter]. A pop that
 * changes how the row DRAWS (scale, alpha) rather than the space it occupies rides on top of
 * that outer size animation instead of contending with it.
 *
 * [PebbleBounceDamping] again for the scale half, so a popped-in row overshoots slightly and
 * settles -- matching the card's own open bounce rather than introducing a second, unrelated
 * feel for "things arriving."
 */
@Composable
internal fun PopVisible(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleIn(
                spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
                initialScale = 0.8f,
            ),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec<Float>(), targetScale = 0.8f),
        content = content,
    )
}

/** Lets [StaggeredRevealColumn] accept a `content` lambda typed for `ColumnScope` --
 *  every pebble's body is already written against that receiver -- without actually being a
 *  Column. `weight`/`align`/`alignBy` all become no-ops, which is exactly what a REAL
 *  Column.weight already reduces to here: it only redistributes space in a height-bounded
 *  Column, and this container has always been wrap-content (see the note at the call site
 *  in [StaggeredRevealColumn]). */
private object NoOpColumnScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    // VerticalAlignmentLine, not Horizontal -- Column stacks children top-to-bottom, so the
    // alignment line it aligns children BY is one that carries an X offset (a "vertical"
    // line, in Compose's naming: the line runs vertically, at some horizontal position).
    // HorizontalAlignmentLine (a Y offset, the FirstBaseline/LastBaseline shape) is what
    // RowScope aligns by instead -- confirmed by CI, which also rejected the alignByBaseline()
    // override right below the line this replaced: ColumnScope has no such shortcut, since
    // "align by baseline" is specifically a Row concept.
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

/**
 * A gentle "back ease": rises past 1.0 near the end before settling back down to it, the same
 * overshoot-then-settle shape a spring has, without needing an actual spring (a real spring
 * driving SCALE here would be a third independently-timed animation on top of the shared
 * [Transition] progress each row already reads -- exactly the "two systems" problem this
 * exists to avoid, just moved one layer down). Standard cubic back-ease formula, [overshoot]
 * kept small (Compose's canonical constant is ~1.70158, which reads as a much showier pop
 * than a row-sized element wants) so the effect stays a subtle "settle," not a wobble.
 */
private fun pebbleRowOvershoot(t: Float, overshoot: Float = 1.15f): Float {
    val c3 = overshoot + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + overshoot * x * x
}

/** How much of the shared progress each row's own stagger window is offset by, end to end --
 *  see [StaggeredRevealColumn]. 0.85, not 0.6: at 0.6 every row's own 40%-wide window
 *  overlapped its neighbours' (row *i+1* was already moving before row *i* finished), which is
 *  what read as "one big block" fading rather than distinct steps -- reported after the first
 *  version shipped. At 0.85 each row gets a narrow 15%-wide window instead: for up to 5 rows
 *  (most pebbles) consecutive windows don't overlap at all, so row *i* is fully settled before
 *  row *i+1* even starts; past 5 they overlap only slightly, and the window is still narrow
 *  enough to read as its own quick step rather than blending into a wave. */
private const val PebbleStaggerSpan = 0.85f

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
    // space in a HEIGHT-BOUNDED Column, and this container has always been wrap-content (the
    // one place PebbleShell bounds its height, fillHeight+expanded, returns through CoverTile
    // before it ever reaches this code). The shim exists purely so `content` type-checks against
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
