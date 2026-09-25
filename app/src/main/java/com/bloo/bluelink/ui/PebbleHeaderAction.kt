@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * The collapsible "pebble" shell family, peeled out of Pebbles.kt (which keeps
 * the per-section pebble composites and list plumbing). This file owns the
 * generic [Pebble] wrapper, the [PebbleShell] expand/collapse card, its
 * [PebbleHeaderAction] action model, and the split [SplitExpandButton] control
 * (action + chevron nub). Same package, so Pebbles.kt's call sites stay
 * internal-visible and verbatim.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.seamCorner
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pebble header action model and its split-button control. Split out of
 * PebbleShell.kt -- Pebble/PebbleShell (staying there) reference PebbleHeaderAction
 * only as a parameter type, needing no import for the same-package reference.
 */

internal class PebbleHeaderAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val pending: Boolean = false,
    val active: Boolean = false,
    val spinning: Boolean = false,
    val bounceIcon: Boolean = false,
    val activeContainer: Color? = null,
    val activeContent: Color? = null,
    val isWarning: Boolean = false,
    /** Explicit TalkBack label for icon-only actions (empty [label]) -- without
     *  it, an empty-label button inside a Surface (which doesn't merge
     *  descendant semantics) announces only "Button" with no indication of
     *  what it does. Only needed when [label] is blank. */
    val contentDescription: String? = null,
)

/**
 * Right-side expand control for pebbles that also have an action button.
 * Left half: the action (label + icon); right half: chevron nub. Together
 * they form a connected split pill, identical in style to [PresetPill].
 */
@Composable
internal fun SplitExpandButton(
    action: PebbleHeaderAction,
    expanded: Boolean,
    onToggle: () -> Unit,
    canToggle: Boolean = true,
    /** Caps how much of the header row this whole control may claim -- see the call
     *  site's own doc (PebbleShell's header Row) for why this exists: without it, this
     *  being a plain (non-weighted) Row sibling meant it was always measured against
     *  the row's FULL width, so its own already-existing compact-to-icon fit rule never
     *  had a reason to fire even when the weighted title beside it was starved for room
     *  and had to ellipsize instead. */
    modifier: Modifier = Modifier,
    /** Reports the CHEVRON half's own pressed (held-down) state, live -- see
     *  [MorphExpandButton]'s identical parameter for why. The action half
     *  (the label button) does not report through this; only the chevron is
     *  the pebble's own expand/collapse control. */
    onChevronPressChange: ((Boolean) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitChevron",
    )

    // Easter egg: HOLD the chevron (long-press) to trigger a one-shot spin
    // animation with a vibration. A long press does NOT toggle the pebble --
    // only a plain tap does. After the spin completes the chevron returns to
    // normal operation and can be held again.
    var easterEggTriggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (easterEggTriggered) 360f else 0f,
        animationSpec = if (easterEggTriggered) lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow) else snap(),
        label = "easterEggSpin",
        finishedListener = { if (easterEggTriggered) easterEggTriggered = false },
    )

    // The row's own real, measured height. The halves' corners are expressed
    // as a PERCENT of the short side (the shared MorphButton model -- exact
    // pills by construction, no fixed-dp radius that could exceed an edge),
    // and the chevron's FULLY MORPHED corner needs to land on the exact same
    // absolute radius seamCorner's own morphed end does (16dp -- see its
    // default params) so the two corners of the SAME half converge to one
    // consistent curve once fully pressed, instead of the seam opening to
    // 16dp while the outer corner (percent-based, so its absolute radius
    // depends on THIS row's own height) settled on a different number. This
    // used to be based on 10dp -- seamCorner's IDLE value, not its morphed
    // one -- which is why holding the chevron (reported from a real
    // screenshot: "the shape is not consistent on the corners") showed two
    // visibly different radii on the one shape at the exact moment (fully
    // pressed) they should have matched.
    var rowHeightDp by remember { mutableStateOf(52.dp) }
    val density = LocalDensity.current
    val morphedPercent = 100f * 16.dp.value / rowHeightDp.value
    // Expansion pushes the CHEVRON's own outer corner toward the morphed (squarer)
    // shape -- reported directly: the header's chevron nub stayed a full pill even
    // once its own card had squared off on expand (PebbleShell's own `corner` a few
    // hundred lines up), reading as a mismatched leftover shape on an otherwise-square
    // card. The ACTION half (Start/Summarize/Locate/...) deliberately does NOT square
    // the same way -- a follow-up report ("the other buttons should still stay
    // rounded") after a first attempt did exactly that to both halves. Only the
    // chevron's own free corner is the "right side" either report was ever about.
    //
    // This is NOT the same thing as passing `active = expanded` to the chevron's own
    // MorphButton -- that is deliberately excluded (see MorphButtonCore's own doc: a
    // connected half's outer corner must not react to a STANDING active state, because
    // two independent `active` flags -- one per half -- can disagree and leave the
    // pair PERMANENTLY mismatched, e.g. Charge's "Stop" squared while its neighbour
    // chevron stayed round). `expandedMorph` sidesteps that differently: it only ever
    // feeds the SEAM (both halves' shared inner edge, so it still converges the way it
    // always did) and the chevron's own OUTER corner -- never the action half's own
    // outer corner, which stays exactly the press-only shape it always was.
    val expandedMorph by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitExpandCorner",
    )
    // Each half gets its own shape: the OUTER corner morphs (pill when idle, rounded square
    // when that half's own state says morphed), the INNER corner is the shared seamCorner() --
    // the SAME idle/morphed nub the lock/horn/lights connected group and the split pills draw,
    // rather than a bespoke static 6dp this header was still carrying on its own. That
    // mismatch was real: every OTHER seamed pair in the app opens up as it presses (10dp ->
    // 16dp), and this one -- used by literally every card's own header -- did not, which is
    // what made a card's action+chevron read as a slightly different kind of control from the
    // connected lock group right below it on the same screen. Both halves are the same
    // MorphButton component; each one's own active/pressed state drives its OWN morph, forwarded
    // here as `morph`.
    //
    // That seam nub is wrong when canToggle is false: the chevron half is never
    // rendered then (see the `if (canToggle)` below), so the action button sits
    // alone with nothing to seam against -- a small fixed corner on a side with
    // no neighbor just reads as a broken/half-finished pill (reported from a
    // real screenshot: the Summarize action, whose pebble is permanently
    // expanded in simple mode and so never shows a chevron). Full pill on both
    // sides in that case instead.
    val leftShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        // `cp` (MorphButtonCore's own resolved percent) is press-only, unmodified by
        // expandedMorph -- the action half's outer corner stays a pill regardless of
        // the pebble's expanded state; see this val's own doc above.
        val seamMorph = maxOf(morph, expandedMorph)
        val end = if (canToggle) seamCorner(seamMorph) else CornerSize(percent = cp)
        RoundedCornerShape(
            topStart = CornerSize(percent = cp), bottomStart = CornerSize(percent = cp),
            topEnd = end, bottomEnd = end,
        )
    }
    val rightShapeForCorner: (Float, Int) -> Shape = { morph, _ ->
        // Unlike the left half, this free corner (top/bottom-end) IS meant to square
        // off on expand, so it can't just reuse the passed-in `cp` (press-only, per
        // MorphButtonCore's shapeForCorner-gated exclusion of `active`) -- recomputed
        // here from the same combined value the seam uses, off this half's own
        // pillCornerPercent/morphedCornerPercent (50f / morphedPercent, passed to its
        // MorphButton call below).
        val combined = maxOf(morph, expandedMorph)
        val cp = (50f + (morphedPercent - 50f) * combined).roundToInt()
        val start = seamCorner(combined)
        RoundedCornerShape(
            topStart = start, bottomStart = start,
            topEnd = CornerSize(percent = cp), bottomEnd = CornerSize(percent = cp),
        )
    }

    // secondaryContainer/onSecondaryContainer, not buttonContainer()/onSurface: this header
    // action (Locate, Summarize, Start, Stop...) is the app's other big case of an idle
    // button not already carrying its own colour override, exactly the case MorphButton's
    // own bare defaults now cover -- matching it here explicitly (rather than just omitting
    // containerColor/contentColor below and letting them fall through) because this `when`
    // also needs its warning/active branches, which a bare default can't express.
    val defaultContainer = MaterialTheme.colorScheme.secondaryContainer
    val leftContainer = if (action.isWarning) MaterialTheme.colorScheme.errorContainer else defaultContainer
    val leftFg = when {
        action.isWarning -> MaterialTheme.colorScheme.onErrorContainer
        action.active -> (action.activeContent ?: MaterialTheme.colorScheme.onPrimary)
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    // Bounce animation for the location button's icon.
    val bounceY = remember { Animatable(0f) }
    val bounceScope = rememberCoroutineScope()
    // Read here, at composable scope, not inline inside bounceScope.launch{} below --
    // lowPowerAwareSpring is itself @Composable, so it can't be called from a suspend lambda.
    val bounceUpSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    val bounceDownSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    var bouncing by remember { mutableStateOf(false) }

    // The climate icon's own spin now comes from MorphButtonLabel's `spinning` param below --
    // it already carries this exact ramp-up/hold/decelerate shape internally, so a second,
    // external copy of the same animation had nothing left to drive.

    // ExpressiveButtonGroup, not a plain Row: these two halves are the app's clearest case of
    // buttons that should physically shove each other on press (they are a single connected
    // pill), and the group is what makes that safe -- it redistributes width BETWEEN the halves
    // so its own outer size never changes, which matters because this header also renders inside
    // Settings' LazyColumn items, where a size change during scroll crashes a lazy layout.
    // See ExpressiveButtons.kt for the full why.
    ExpressiveButtonGroup(
        modifier = modifier
            // A fixed 52dp target (the old content-driven ~40dp pill read as
            // undersized next to the 76dp header it sits in -- reported from
            // a real screenshot). IntrinsicSize.Min still reconciles the two
            // halves to the SAME height; heightIn supplies the floor.
            .height(IntrinsicSize.Min)
            .heightIn(min = rowHeightDp)
            // Real measured height, so the 10dp corner percent above lands on
            // the right radius -- see that val's own doc.
            .onSizeChanged { rowHeightDp = with(density) { it.height.toDp() } },
        spacing = 3.dp,
        verticalAlignment = Alignment.CenterVertically,
        // An action half joined to a chevron half is one control with a seam, so it must not
        // break onto two lines however narrow the header gets -- see `wrap`. It compacts to
        // glyphs instead, which is exactly right for a header running out of room.
        wrap = false,
    ) {
        // Left half — the action (label + icon) button with expansion animation.
        val actionSource = remember { MutableInteractionSource() }
        GroupButton(
            interactionSource = actionSource,
            enabled = action.enabled && !action.pending,
        ) {
            MorphButton(
                onClick = {
                    if (action.bounceIcon) bounceScope.launch {
                        bouncing = true
                        bounceY.animateTo(-9f, bounceUpSpring)
                        bounceY.animateTo(0f, bounceDownSpring)
                        bouncing = false
                    }
                    action.onClick()
                },
                enabled = action.enabled && !action.pending,
                active = action.active,
                interactionSource = actionSource,
                containerColor = leftContainer,
                contentColor = leftFg,
                activeContainerColor = action.activeContainer ?: MaterialTheme.colorScheme.primary,
                activeContentColor = action.activeContent ?: MaterialTheme.colorScheme.onPrimary,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                shapeForCorner = leftShapeForCorner,
                morphedCornerPercent = morphedPercent,
                pillCornerPercent = 50f,
                // The halves keep their measured ~42-46dp height; the standard
                // 48dp touch floor would inflate the whole pebble header.
                minHeight = 0.dp,
                modifier = Modifier.fillMaxHeight().then(
                    run {
                        // Local copy so the smart cast works inside the semantics
                        // lambda (class properties are not stable for smart-cast
                        // capture; the !! form read fine but would crash if the
                        // guard and the call ever drifted apart).
                        val desc = action.contentDescription
                        if (action.label.isEmpty() && desc != null) {
                            Modifier.semantics { contentDescription = desc }
                        } else Modifier
                    },
                ),
            ) {
                // The shared label -- same icon size, gap and type as every other button in
                // the app, including PrimaryActions' lock/horn/lights row, which this one
                // still drew nothing like: a bespoke 16dp icon, labelLarge text and a 6dp gap
                // that predated MorphButtonLabel entirely. Every card's own header action goes
                // through this one call site, so that mismatch was "the control pebble looks
                // different from the rest of the app" for literally every OTHER pebble at once.
                //
                // `spinning` replaces the hand-rolled `spinAngle` Animatable above -- the exact
                // same ramp-up/hold/decelerate shape already lives inside MorphButtonLabel's own
                // glyph, so driving a second, external copy of it here was duplicated animation
                // state for the same visual effect.
                //
                // widthIn still caps the label so a long action ("Downloading…") at a large font
                // size cannot grow this button unbounded and squeeze the pebble title -- but past
                // the cap this now compacts to the icon alone (the app's one fit rule) instead of
                // ellipsizing a half-cut word.
                //
                // 132dp, not 110: at 110 a perfectly ordinary short label ("Summarize", nine
                // letters and two wide 'm's at SemiBold) already landed past the cap and compacted
                // to the bare glyph -- a WIDE dark pill with a lone sparkle floating in it, reported
                // from a real screenshot. The group had already reserved this button its full,
                // uncapped intrinsic width (it measures the same subtree, cap included, so the
                // reservation and this box's own ceiling should describe the same content but did
                // not once the label was long enough to graze the old cap) -- so the pill stayed
                // wide while its content silently gave up the word inside it. 132dp comfortably
                // clears every short action label in the app ("Summarize", "Lock", "Stop", "Start",
                // "Install now") while still catching the genuinely long ones ("Downloading…",
                // "Installing…"), which is what this cap exists for.
                Box(Modifier.widthIn(max = 132.dp).graphicsLayer { translationY = bounceY.value }) {
                    MorphButtonLabel(
                        action.icon,
                        action.label,
                        pending = action.pending && !bouncing,
                        spinning = action.spinning,
                    )
                }
            }
        }
        // Right half — chevron nub with expansion animation.
        // Hidden if canToggle is false (single-setting pebbles in simple mode).
        if (canToggle) {
            val chevronSource = remember { MutableInteractionSource() }
            if (onChevronPressChange != null) {
                val pressed by chevronSource.collectIsPressedAsState()
                LaunchedEffect(pressed) { onChevronPressChange(pressed) }
                // See MorphExpandButton's identical DisposableEffect for why: a
                // collapse that unmounts this half mid-press must not leave the
                // pebble permanently squared behind it.
                DisposableEffect(Unit) { onDispose { onChevronPressChange(false) } }
            }
            GroupButton(
                interactionSource = chevronSource,
                enabled = true,
            ) {
                MorphButton(
                    onClick = { onToggle() },
                    onClickHaptic = { if (expanded) haptics?.tick() else haptics?.click() },
                    onLongClick = {
                        // Easter egg: hold the chevron to spin it + vibrate.
                        if (!easterEggTriggered) {
                            easterEggTriggered = true
                            haptics?.heavy()
                        }
                    },
                    active = expanded,
                    interactionSource = chevronSource,
                    contentPadding = PaddingValues(start = 13.dp, end = 12.dp),
                    shapeForCorner = rightShapeForCorner,
                    morphedCornerPercent = morphedPercent,
                    pillCornerPercent = 50f,
                    minHeight = 0.dp,
                    // The icon's own contentDescription below is the NEXT action
                    // ("Expand"/"Collapse"); this is the CURRENT state -- without it
                    // TalkBack only ever hears what tapping will do, never whether the
                    // pebble is presently open, so distinguishing the two took a
                    // double-tap-and-listen-again instead of being announced on focus.
                    // widthIn(min = rowHeightDp) keeps the nub a square at the row's
                    // fixed height so its pill end is a true semicircle by percent.
                    modifier = Modifier.fillMaxHeight().widthIn(min = rowHeightDp)
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        // Larger chevron icon (24dp to match action button icon size), with
                        // easter egg spin animation when the chevron is held.
                        // rotationZ in a graphicsLayer LAMBDA, not Modifier.rotate(): rotate()
                        // takes the angle as an argument, so the spring is read in COMPOSITION
                        // and this Icon recomposes on every frame of every expand/collapse, on
                        // every pebble header in the app. Read in the lambda it is draw-phase.
                        modifier = Modifier.size(ButtonIconOnlySize).graphicsLayer {
                            rotationZ = rotation + easterEggSpin
                        },
                    )
                }
            }
        }
    }
}
