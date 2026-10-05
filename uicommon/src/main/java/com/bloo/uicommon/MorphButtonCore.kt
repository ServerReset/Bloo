package com.bloo.uicommon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The button machinery behind every filled pill/rounded-square button.
 *
 * - Morph: a pill at rest, a rounded rectangle while [active] or pressed. [active] also drives the
 *   highlight colour ([activeContainerColor], theme primary by default): one state, one colour.
 * - Press and click: pressed is read from [interactionSource]; [onLongClick] rides `combinedClickable`.
 * - Container colours only: stays painted when disabled (only content dims). Content colour is the
 *   wrapper's job (this is foundation-only, no LocalContentColor).
 *
 * Corners animate as a PERCENT of the short side, [pillCornerPercent] (50 = pill) to
 * [morphedCornerPercent] (28), exact on every height. Asymmetric corners come from [shapeForCorner],
 * which receives the raw [morph] progress (0 = pill, 1 = morphed) and the animated [cornerPercent].
 *
 * Not a `Button`: M3's has no long-click slot and cannot animate corner percent per frame, so this
 * draws a flat filled shape with `combinedClickable`, clipped so the ripple follows the pill.
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
    /** When null, the disabled container keeps the idle/active fill (only the label fades). */
    disabledContainerColor: Color? = null,
    /** Border used while disabled (overrides [border]). */
    disabledBorder: BorderStroke? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /** Hold-to-act; null means click-only. */
    onLongClick: (() -> Unit)? = null,
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /** Asymmetric shapes get the raw morph progress and animated corner percent; null is the plain pill. */
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** Spring for the morph; default is gentle (StiffnessLow). */
    morphSpring: SpringSpec<Float> = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
    colorSpring: FiniteAnimationSpec<Color> = spring(stiffness = Spring.StiffnessMediumLow),
    content: @Composable RowScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()

    // The clickable animated half lives in `MorphChrome` (it recomposes every morph frame); the CONTENT
    // is a stable sibling that never recomposes for the animation.
    // The content Row wraps and the chrome uses matchParentSize(), so CONTENT decides the button's size
    // and `modifier` stretches it. fillMaxSize() children would leave the Box with no intrinsic size
    // (growing to full width, or zero height in unbounded parents).
    Box(modifier = modifier.blockPageSwipe(), contentAlignment = Alignment.Center) {
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
            morphSpring = morphSpring,
            colorSpring = colorSpring,
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
 *  scale, click and long-click. The ONLY composable that recomposes per animation frame. */
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
    morphSpring: SpringSpec<Float>,
    colorSpring: FiniteAnimationSpec<Color>,
    enabled: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    // `active` drives the morph only for a STANDALONE button (shapeForCorner == null). Connected halves
    // (split pair, segmented group) morph independently, so a standing `active` on one would warp its
    // outer cap and seam corners out of agreement with its neighbour; they react to PRESS only.
    val morph by animateFloatAsState(
        targetValue = if ((active && shapeForCorner == null) || pressed) 1f else 0f,
        animationSpec = morphSpring,
        label = "morphProgress",
    )
    val cornerPercent = (pillCornerPercent + (morphedCornerPercent - pillCornerPercent) * morph).roundToInt()
    val shape = shapeForCorner?.invoke(morph, cornerPercent) ?: RoundedCornerShape(percent = cornerPercent)
    val bg by animateColorAsState(
        targetValue = if (active) activeContainerColor else containerColor,
        animationSpec = colorSpring,
        label = "morphBg",
    )
    val fill = if (!enabled && disabledContainerColor != null) disabledContainerColor else bg
    val resolvedBorder = if (!enabled) (disabledBorder ?: border) else border
    // The caller's BorderStroke is read here every morph frame (cheap).
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
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    )
}

