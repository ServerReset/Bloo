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
 * The CLOSE half now bounces too, on explicit request, but on its OWN, more heavily damped
 * spring ([PebbleCloseBounceDamping]) rather than reusing the open one. shrinkVertically
 * drives an IntSize animating towards zero, and an underdamped spring overshoots its target
 * in BOTH directions -- so undershooting near zero height would mean asking for a genuinely
 * negative size, which a layout node cannot report. A layout engine clamps that to zero
 * instead, which is a stutter (the spring's own math thinks it's still below zero and moving,
 * but what's on screen just sits at zero until the spring catches back up), not a bounce.
 * [PebbleCloseBounceDamping] is a LITTLE closer to critically damped than the open spring --
 * enough that the undershoot toward zero stays small -- but not by much: 0.85 was the first
 * value tried here specifically to be safe against the clamp/stutter above, and it erred so
 * far toward safe that the close bounce was reported as not there at all. 0.72 is the
 * smaller, one-step correction back toward the open spring's own 0.68.
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
internal val PebbleCloseBounceDamping = 0.72f
internal val PebbleBounceStiffness = Spring.StiffnessLow

@Composable
internal fun collapseEnter(expandFrom: Alignment.Vertical = Alignment.Top): EnterTransition =
    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
        expandVertically(
            spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness),
            expandFrom = expandFrom,
        )

/** Mirror of [collapseEnter]; see there for why both halves are springs, and for why the
 *  CLOSE direction bounces on its own, more heavily damped spring rather than reusing the
 *  open one. */
@Composable
internal fun collapseExit(shrinkTowards: Alignment.Vertical = Alignment.Top): ExitTransition =
    fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec<Float>()) +
        shrinkVertically(
            spring(dampingRatio = PebbleCloseBounceDamping, stiffness = PebbleBounceStiffness),
            shrinkTowards = shrinkTowards,
        )

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
 * (see [PebbleStaggerSpan]). That remap is what turns one linear 0..1 value into a cascade:
 * row *i* of *n* doesn't start moving until progress passes `i/n * PebbleStaggerSpan`, and is
 * fully settled by the time progress reaches `i/n * PebbleStaggerSpan + (1 - PebbleStaggerSpan)`.
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
                // Closing has no delay and a shorter duration than opening -- now a stylistic
                // choice (a snappier close reads right next to a slightly more deliberate
                // open), not a race against being torn out of composition early: since this
                // progress lives in the SAME Transition as the card's own height/fade, the
                // card physically cannot finish exiting before this does.
                tween(durationMillis = 220, easing = LinearEasing)
            }
        },
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
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
                    val raw = ((progress - start) / (1f - PebbleStaggerSpan)).coerceIn(0f, 1f)
                    // Smoothstep, applied per row rather than trusting the outer tween's own
                    // easing -- see the LinearEasing comment above for why: this is what gives
                    // every row's individual pop the same eased shape regardless of where its
                    // narrow window happens to land in the overall sequence.
                    val local = raw * raw * (3f - 2f * raw)
                    alpha = local
                    // 0.7 -> 1.0, not 0.85 -> 1.0: a 15%-of-size scale change is easy to miss
                    // next to the alpha fade doing most of the visible work: wider so the pop
                    // reads as its own distinct motion rather than a fade with a barely-there
                    // size wobble riding along.
                    scaleX = 0.7f + 0.3f * local
                    scaleY = 0.7f + 0.3f * local
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                y += p.height + gapPx
            }
        }
    }
}
