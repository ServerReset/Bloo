@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine

/**
 * Shared animation tokens and helpers: springs, expand/collapse transitions, and the
 * per-row stagger system pebbles use when opening/closing. Split out of UiTokens.kt to
 * keep that file to plain design tokens (colour/sizing/shapes/icons) -- see its own doc.
 */

// ---- Motion -------------------------------------------------------------------

// Aliases onto :uicommon so the phone reads the shared motion tokens.
internal val SoftDamping get() = com.bloo.uicommon.SoftDamping


// The morph button's two corner states from the shared motion vocabulary. Aliased the same
// way SoftDamping above is.
internal val PillCornerPercent get() = com.bloo.uicommon.PillCornerPercent

internal val MorphedCornerPercent get() = com.bloo.uicommon.MorphedCornerPercent


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
 * The Settings screen's Simple/Advanced toggle used to be the one deliberate holdout here,
 * on its own calmer, now-deleted `AdvancedModeStiffness` spec, reasoned as "it reveals a lot
 * at once." It now uses these same tokens too -- what actually made revealing a lot at once
 * feel chaotic turned out to be several cards all overshooting on the same frame, not the
 * bounce itself, and `staggeredAdvancedVisible` (SettingsScreen.kt) fixes that directly by
 * giving each card a small index-based head start instead of avoiding the bounce altogether.
 * [SettingsCard] itself is gone too, as a bespoke `Card` + `animateContentSize` -- it's now a
 * thin wrapper around [PebbleShell], the exact same expandable-pebble system this token
 * pair drives, so there's no second "in-place resize" spec left needing its own constant.
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

// Slightly underdamped (0.95) instead of critically damped (1.0) for a smoother,
// more natural close transition that isn't stiff
internal val PebbleCloseDamping = 0.95f

internal val PebbleBounceStiffness = Spring.StiffnessLow


// The PillDock* spring tokens and pillDockSpring() that used to live here
// are gone with the flown-title system they served -- the floating name is
// a fixed-corner TitleDockBadge now (see Screens.kt), which animates with
// plain AnimatedVisibility springs and needs no bespoke tuning of its own.

/**
 * Standard enter animation for expanding surfaces: a gentle fade-in combined with a slide
 * offset so content appears to emerge from the direction of expansion. Used when pebbles,
 * tiles, or sections become visible. Matches the update pebble's animation language.
 */
internal fun expandEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    fadeIn(tween(180)) + slideInVertically {
        if (expandFrom == Alignment.Top) -it / 3 else it / 3
    }


/**
 * Standard exit animation for expanding surfaces: mirrors [expandEnter] but in reverse,
 * fading out while sliding back in the direction of collapse. Keeps the same 120ms timing
 * as the update pebble's exit for consistency.
 *
 * [fade] defaults true (standard behavior); set false for nested content with its own
 * per-row fades (e.g., StaggeredRevealColumn rows with individual fade animations).
 */
internal fun expandExit(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition =
    (if (fade) fadeOut(tween(120)) else ExitTransition.None) + slideOutVertically {
        if (shrinkTowards == Alignment.Top) -it / 3 else it / 3
    }


/**
 * [expandEnter] plus real container-height growth via [expandVertically]. Plain
 * `AnimatedVisibility` accordion/disclosure sites -- a pebble's body, a settings section, a
 * toggled color picker -- sit inline in a Column: when their content appears with only
 * [expandEnter]'s fade+slide and no actual size transition, the surrounding layout snaps to
 * the revealed height on the very first frame instead of growing into it, and on the way out
 * ([expandExitSized]) the container sits at full height for the ENTIRE fade+slide before
 * cutting to zero in one frame the instant the exit finishes -- reported as "the closing
 * animation is broken" (a pebble's body looked like it did nothing, then vanished).
 *
 * Deliberately NOT folded into [expandEnter]/[expandExit] themselves: [expandContentTransform]
 * (AnimatedContent) builds on those too, and AnimatedContent already owns its own size
 * interpolation via its `sizeTransform` -- adding a second, competing size animation there
 * would fight it rather than fix anything. Use this pair only where you're building
 * `AnimatedVisibility`'s `enter=`/`exit=` directly.
 */
internal fun expandEnterSized(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    expandEnter(expandFrom) + expandVertically(expandFrom = expandFrom)


/** Mirror of [expandEnterSized]; see there for why this exists separately from [expandExit]. */
internal fun expandExitSized(shrinkTowards: Alignment.Vertical = Alignment.Top, fade: Boolean = true): ExitTransition =
    expandExit(shrinkTowards, fade) + shrinkVertically(shrinkTowards = shrinkTowards)


/**
 * Transition spec for [AnimatedContent] that uses the standardized expand animation:
 * used by status updates and other inline content that changes state. Pairs [expandEnter]
 * and [expandExit] for a cohesive, snappy feel consistent across the app.
 */
internal fun expandContentTransform(): ContentTransform =
    expandEnter() togetherWith expandExit()


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
    /**
     * True also interpolates the container's HEIGHT (expandVertically/shrinkVertically), not
     * just fade+scale in place. Off by default to match how every existing call site already
     * looks -- a pop that pops, not a reveal that slides -- and most sit beside fixed content
     * where a height change wouldn't read as "pop" and isn't needed anyway.
     *
     * Turn it on for a call site whose CONTAINER reflows SIBLINGS when this content's height
     * changes -- e.g. a pebble inside the reorderable list, where the pebble below animates its
     * own position to make room via `Modifier.animatePlacement()` (ReorderColumn.kt) over ~300ms
     * of spring settle. Without this, the pebble's own height jumped to its new value on the
     * very next layout pass -- instant, not animated -- while the sibling below was still
     * mid-spring toward its new slot, so for that whole window the two visibly overlapped: this
     * pebble's newly-grown bottom edge was already there, but the sibling hadn't slid down out
     * of the way yet. Expanding/shrinking this content's own height in step keeps the two
     * in sync instead of one snapping and the other catching up.
     */
    sizeAnimated: Boolean = false,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleIn(
                lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
                initialScale = 0.8f,
            ) +
            (if (sizeAnimated) expandVertically(lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)) else EnterTransition.None),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec<Float>(), targetScale = 0.8f) +
            (if (sizeAnimated) shrinkVertically(lowPowerAwareSpring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)) else ExitTransition.None),
        content = content,
    )
}


/** Lets [StaggeredRevealColumn] accept a `content` lambda typed for `ColumnScope` --
 *  every pebble's body is already written against that receiver -- without actually being a
 *  Column. `weight`/`align`/`alignBy` all become no-ops, which is exactly what a REAL
 *  Column.weight already reduces to here: it only redistributes space in a height-bounded
 *  Column, and this container has always been wrap-content (see the note at the call site
 *  in [StaggeredRevealColumn]). */
object NoOpColumnScope : ColumnScope {
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
fun pebbleRowOvershoot(t: Float, overshoot: Float = 1.15f): Float {
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
const val PebbleStaggerSpan = 0.85f
