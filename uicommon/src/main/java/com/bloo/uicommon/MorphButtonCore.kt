package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The button machinery behind every filled pill/rounded-square button.
 *
 * - Morph: a pill at rest, a rounded rectangle while [active] or pressed. [active] also drives the
 *   highlight colour ([activeContainerColor], theme primary by default): one state, one colour.
 * - Press and click: pressed is read from [interactionSource]; the click and long-press ride `combinedClickable`.
 * - Container colours only: stays painted when disabled (only content dims). Content colour is the
 *   wrapper's job (this is foundation-only, no LocalContentColor).
 *
 * Corners animate as a PERCENT of the short side, [pillCornerPercent] (50 = pill) to
 * [morphedCornerPercent] (28), exact on every height. Asymmetric corners come from [shapeForCorner],
 * which receives the raw [morph] progress (0 = pill, 1 = morphed) and the animated [cornerPercent].
 *
 * Not a `Button`: M3's has no per-frame corner-percent morph and cannot animate corner percent per
 * frame, so this draws a flat filled shape with `combinedClickable`, clipped so the ripple follows the pill.
 */
@Composable
fun MorphButtonCore(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color,
    activeContainerColor: Color,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    border: BorderStroke? = null,
    /**
     * When null, the disabled container keeps the (animated) idle/active fill instead of washing
     * out -- matching the app's "only the label fades" disabled treatment everywhere.
     */
    disabledContainerColor: Color? = null,
    /** Border used while disabled (overrides [border]). */
    disabledBorder: BorderStroke? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /**
     * Hold-to-act variety (the chevron's spin easter egg). Null means plain click-only, exactly
     * like M3 `Button`.
     */
    onLongClick: (() -> Unit)? = null,
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /** Asymmetric shapes get the raw morph progress and animated corner percent; null is the plain pill. */
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()

    // The clickable animated half lives in `MorphChrome` (it recomposes every morph frame); the CONTENT
    // is a stable sibling that never recomposes for the animation.
    // The content Row wraps and the chrome uses matchParentSize(), so CONTENT decides the button's size
    // and `modifier` stretches it. fillMaxSize() children would leave the Box with no intrinsic size
    // (growing to full width, or zero height in unbounded parents).
    // One node for a screen reader: the label (the content) and the click (the chrome beneath it) are siblings, so left apart
    // it would meet an unlabelled clickable and, separately, a bit of text that does nothing.
    Box(modifier = modifier.semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {
        MorphChrome(
            pressed = pressed,
            active = active,
            interactionSource = interactionSource,
            pillCornerPercent = pillCornerPercent,
            morphedCornerPercent = morphedCornerPercent,
            shapeForCorner = shapeForCorner,
            activeContainerColor = activeContainerColor,
            containerColor = containerColor,
            disabledContainerColor = disabledContainerColor,
            disabledBorder = disabledBorder,
            border = border,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        // Stable content drawn over the chrome; taps reach the chrome below. Wrap-content: gives the button its size.
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/** The clickable animated half: morphing shape + clip (ripple tracks it), sprung background, press
 *  scale and click. The ONLY composable that recomposes per animation frame. */
@Composable
private fun BoxScope.MorphChrome(
    pressed: Boolean,
    active: Boolean,
    interactionSource: MutableInteractionSource,
    pillCornerPercent: Float,
    morphedCornerPercent: Float,
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)?,
    activeContainerColor: Color,
    containerColor: Color,
    disabledContainerColor: Color?,
    disabledBorder: BorderStroke?,
    border: BorderStroke?,
    enabled: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    // `active` drives the morph only for a STANDALONE button (shapeForCorner == null). Connected halves
    // (split pair, segmented group) morph independently, so a standing `active` on one would pull its
    // outer cap and seam corners out of agreement with its neighbour; they react to PRESS only.
    val morph by animateFloatAsState(
        targetValue = if ((active && shapeForCorner == null) || pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphProgress",
    )
    val cornerPercent = (pillCornerPercent + (morphedCornerPercent - pillCornerPercent) * morph).roundToInt()
    val shape = shapeForCorner?.invoke(morph, cornerPercent) ?: RoundedCornerShape(percent = cornerPercent)
    // One animated fill for every state, disabled included: a button that is disabled while a command runs,
    // or re-enabled when it finishes, eases to the new colour instead of snapping.
    val fill by animateColorAsState(
        targetValue = when {
            !enabled && disabledContainerColor != null -> disabledContainerColor
            active -> activeContainerColor
            else -> containerColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "morphBg",
    )
    val resolvedBorder = if (!enabled) (disabledBorder ?: border) else border
    // The BorderStroke passed in by the caller is the same mutable object in the parent scope;
    // Modifier.border reads it here on every morph frame -- cheap, and it keeps the reference
    // semantics identical to before.
    Box(
        Modifier
            // matchParentSize(), NOT fillMaxSize(): it is measured after the parent's real size and never
            // influences it. fillMaxSize() follows incoming constraints, so it would inflate un-weighted buttons
            // in a Row and collapse to zero height under an unbounded (scrollable) height.
            .matchParentSize()
            .clip(shape)
            .then(
                if (resolvedBorder != null) {
                    Modifier.background(color = fill, shape = shape).border(resolvedBorder, shape)
                } else {
                    Modifier.background(color = fill, shape = shape)
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    )
}
