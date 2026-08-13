@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
// No `motionScheme` import: it is a member of the MaterialTheme object (verified as
// MaterialTheme.getMotionScheme in the resolved material3 AAR), as are defaultEffectsSpec
// and defaultSpatialSpec on MotionScheme. Screens.kt imports none of them either.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
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
 * The CLOSE half deliberately does NOT reuse that same bouncy spring. shrinkVertically drives
 * an IntSize animating towards zero; an underdamped spring overshoots its target in both
 * directions, and undershooting a zero height is a negative size a layout node cannot report,
 * so the frames near the end of a bouncy collapse would clamp to zero early and then sit
 * there while the spring's math thinks it's still moving -- a stutter, not a bounce. Closing
 * keeps the calmer default spec; the bounce reads on the way open, where there's headroom
 * past the target for the overshoot to actually be visible.
 */
private const val PebbleBounceDamping = 0.6f
private val PebbleBounceStiffness = Spring.StiffnessMediumLow

@Composable
internal fun collapseEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
        expandVertically(
            spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
            expandFrom = expandFrom,
        )

/** Mirror of [collapseEnter]; see there for why both halves are springs, and for why only
 *  the OPEN direction bounces. */
@Composable
internal fun collapseExit(shrinkTowards: Alignment.Vertical = Alignment.Top): ExitTransition =
    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
        shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>(), shrinkTowards = shrinkTowards)

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
