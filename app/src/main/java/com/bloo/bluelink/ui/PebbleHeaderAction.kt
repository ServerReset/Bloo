package com.bloo.bluelink.ui

/**
 * The collapsible "pebble" shell family: [Pebble], [PebbleShell], [PebbleHeaderAction] and
 * [SplitExpandButton].
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.seamCorner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pebble header action model and its split-button control. Split out of PebbleShell.kt --
 * Pebble/PebbleShell (staying there) reference PebbleHeaderAction only as a parameter type, needing
 * no import for the same-package reference.
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
    /**
     * Explicit TalkBack label for icon-only actions (empty [label]) -- without it, an empty-label
     * button inside a Surface (which doesn't merge descendant semantics) announces only "Button"
     * with no indication of what it does. Only needed when [label] is blank.
     */
    val contentDescription: String? = null,
)

/**
 * Right-side expand control: action half plus chevron nub, a connected split pill like
 * [PresetPill].
 */
@Composable
internal fun SplitExpandButton(
    action: PebbleHeaderAction,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    canToggle: Boolean = true,
    /**
     * Caps how much of the header row this whole control may claim -- see the call site's own doc
     * (PebbleShell's header Row) for why this exists: without it, this being a plain (non-weighted)
     * Row sibling meant it was always measured against the row's FULL width, so its own
     * already-existing compact-to-icon fit rule never had a reason to fire even when the weighted
     * title beside it was starved for room and had to ellipsize instead.
     */
    onChevronPressChange: ((Boolean) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    // Shared with MorphExpandButton's chevron ([rememberChevronSpin]). A long press does not
    // toggle; only a tap does.
    val chevron = rememberChevronSpin(expanded, label = "splitChevron")

    // Measured row height: corner percents are relative to the short side, and the chevron's fully
    // morphed corner must land on seamCorner's morphed radius (16dp) so both corners of the half
    // converge.
    var rowHeightDp by remember { mutableStateOf(52.dp) }
    val density = LocalDensity.current
    val morphedPercent = 100f * 16.dp.value / rowHeightDp.value
    // The ACTION half (Start/Summarize/Locate/...) deliberately does NOT square the same way -- a
    // follow-up report ("the other buttons should still stay rounded") after a first attempt did
    // exactly that to both halves. Only the chevron's own free corner is the "right side" either
    // report was ever about.
    val expandedMorph by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitExpandCorner",
    )
    // Each half gets its own shape: the OUTER corner morphs (pill when idle, rounded square when
    // that half's own state says morphed), the INNER corner is the shared seamCorner() -- the SAME
    // idle/morphed nub the lock/horn/lights connected group and the split pills draw, rather than a
    // bespoke static 6dp this header was still carrying on its own.
    val leftShapeForCorner: (Float, Int) -> Shape = { morph, cp ->
        // `cp` (MorphButtonCore's own resolved percent) is press-only, unmodified by expandedMorph
        // -- the action half's outer corner stays a pill regardless of the pebble's expanded state;
        // see this val's own doc above.
        val seamMorph = maxOf(morph, expandedMorph)
        val end = if (canToggle) seamCorner(seamMorph) else CornerSize(percent = cp)
        RoundedCornerShape(
            topStart = CornerSize(percent = cp), bottomStart = CornerSize(percent = cp),
            topEnd = end, bottomEnd = end,
        )
    }
    val rightShapeForCorner: (Float, Int) -> Shape = { morph, _ ->
        // Unlike the left half, this free corner (top/bottom-end) IS meant to square off on expand,
        // so it can't just reuse the passed-in `cp` (press-only, per MorphButtonCore's
        // shapeForCorner-gated exclusion of `active`) -- recomputed here from the same combined
        // value the seam uses, off this half's own pillCornerPercent/morphedCornerPercent (50f /
        // morphedPercent, passed to its MorphButton call below).
        val combined = maxOf(morph, expandedMorph)
        val cp = (50f + (morphedPercent - 50f) * combined).roundToInt()
        val start = seamCorner(combined)
        RoundedCornerShape(
            topStart = start, bottomStart = start,
            topEnd = CornerSize(percent = cp), bottomEnd = CornerSize(percent = cp),
        )
    }

    // secondaryContainer matches MorphButton's bare idle defaults; set explicitly because
    // warning/active need overrides.
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
    // Read at composable scope: lowPowerAwareSpring is @Composable, so it can't be called inside
    // launch{}.
    val bounceUpSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
    val bounceDownSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    var bouncing by remember { mutableStateOf(false) }

    // ExpressiveButtonGroup: the halves shove each other on press while its outer size stays
    // constant (a size change during scroll crashes Settings' lazy items). See
    // ExpressiveButtons.kt.
    ExpressiveButtonGroup(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = rowHeightDp)
            // Real measured height, feeding the corner percent above.
            .onSizeChanged { rowHeightDp = with(density) { it.height.toDp() } },
        spacing = SplitSeam,
        verticalAlignment = Alignment.CenterVertically,
        // An action half joined to a chevron half is one control with a seam, so it must not break
        // onto two lines however narrow the header gets -- see `wrap`. It compacts to glyphs
        // instead, which is exactly right for a header running out of room.
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = GapRow),
                shapeForCorner = leftShapeForCorner,
                morphedCornerPercent = morphedPercent,
                pillCornerPercent = 50f,
                // Keeps the measured ~42-46dp height; the 48dp touch floor would inflate the
                // header.
                minHeight = 0.dp,
                modifier = Modifier.fillMaxHeight().then(
                    run {
                        // Local copy so the smart cast works inside the semantics lambda (class
                        // properties are not stable for smart-cast capture; the !! form read fine
                        // but would crash if the guard and the call ever drifted apart).
                        val desc = action.contentDescription
                        if (action.label.isEmpty() && desc != null) {
                            Modifier.semantics { contentDescription = desc }
                        } else Modifier
                    },
                ),
            ) {
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
        // Right half — chevron nub with expansion animation. Hidden if canToggle is false
        // (single-setting pebbles in simple mode).
        if (canToggle) {
            val chevronSource = remember { MutableInteractionSource() }
            if (onChevronPressChange != null) {
                val pressed by chevronSource.collectIsPressedAsState()
                LaunchedEffect(pressed) { onChevronPressChange(pressed) }
                // As in MorphExpandButton: a collapse unmounting this half mid-press must not leave
                // the pebble squared.
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
                        chevron.spin()
                        haptics?.heavy()
                    },
                    active = expanded,
                    interactionSource = chevronSource,
                    contentPadding = PaddingValues(start = 13.dp, end = 12.dp),
                    shapeForCorner = rightShapeForCorner,
                    morphedCornerPercent = morphedPercent,
                    pillCornerPercent = 50f,
                    minHeight = 0.dp,
                    // The icon's own contentDescription below is the NEXT action
                    // ("Expand"/"Collapse"); this is the CURRENT state -- without it TalkBack only
                    // ever hears what tapping will do, never whether the pebble is presently open,
                    // so distinguishing the two took a double-tap-and-listen-again instead of being
                    // announced on focus. widthIn(min = rowHeightDp) keeps the nub a square at the
                    // row's fixed height so its pill end is a true semicircle by percent.
                    modifier = Modifier.fillMaxHeight().widthIn(min = rowHeightDp)
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                ) {
                    ExpandChevronIcon(expanded, chevron)
                }
            }
        }
    }
}
