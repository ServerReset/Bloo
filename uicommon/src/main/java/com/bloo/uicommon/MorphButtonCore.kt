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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * THE button machinery behind every filled pill↔rounded-square button in the
 * app, shared byte-for-byte by the phone and the watch.
 *
 * What it owns, and the answer to "why is this one component instead of N":
 *
 *  - **The morph.** A pill at rest, a rounded rectangle while [active] (the
 *    on/toggled/highlighted state) or pressed. [active] follows the same rule
 *    everywhere because the highlight colour IS the active state: the lock/
 *    unlock button, the chevron on an expanded pebble and the climate/charge
 *    toggles all activate the exact same way, so their highlight also comes
 *    from the same default ([activeContainerColor] = theme primary /
 *    [activeContentColor] = onPrimary). There is no "green when expanded" or
 *    "gray when selected" special case left anywhere -- one state, one colour.
 *  - **The press state and the click/long-click surface.** Pressed is read
 *    from [interactionSource] (so callers can share one source for their own
 *    pressed-driven extras), and the click surface is a standard
 *    `combinedClickable` -- [onLongClick] is how the chevron's hold-to-spin
 *    easter egg and the cover button's hold-for-lights arrive without any
 *    second gesture system.
 *  - **The colours.** Idle vs [active], plus the disabled pair: the container
 *    stays fully painted when disabled (M3's default onSurface@12% wash is
 *    invisible against these light cards) and only the content dims.
 *
 * Fundamentally foundation-only (no Material dependency): the phone and the
 * watch supply their own platform colours (via the wrappers in each module)
 * and their own interaction/haptic conventions, but the drawing, the motion
 * and the shape logic here are one copy.
 *
 * Geometry: corners are animated as a PERCENT of the short side, [pillCornerPercent]
 * (50 = a perfect pill) smoothing to [morphedCornerPercent] (28 = the app's
 * standard rounded square). That is why there is no `fullyRound = height/2 + 2dp`
 * in this codebase any more -- percentage corners are exact by construction on
 * every height, where fixed-dp radii on a measured height needed a buffer and
 * still hit rounded-rect drawing's undefined behaviour when corner radii summed
 * past an edge. Callers that need asymmetric (split-button/connected-group)
 * corners supply [shapeForCorner], which receives the raw [morph] progress
 * (0 = pill, 1 = morphed) and the animated [cornerPercent] so every corner can
 * be derived from the same single spring.
 *
 * Not a `Button`: M3's `Button` has no long-click slot and its corner percent
 * can't be animated per-frame; the standard long-click API is
 * `combinedClickable`, which lives on a modifier. So this draws a flat,
 * filled shape (the same `background`+`border` primitives M3's `Surface`
 * wraps, so nothing about the look differs from a `Button`) whose modifier
 * carries a `combinedClickable`, clipped to the animated shape so the ripple
 * follows the pill. Behaviour -- container/content/disabled colours, haptics
 * on the caller side, centre-aligned content with [contentPadding] -- mirrors
 * what `Button` provided, because there is only one button here either way.
 */
@Composable
fun MorphButtonCore(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color,
    contentColor: Color,
    activeContainerColor: Color,
    activeContentColor: Color,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    border: BorderStroke? = null,
    /** When null, the disabled container keeps the (animated) idle/active fill
     *  instead of washing out -- matching the app's "only the label fades"
     *  disabled treatment everywhere. */
    disabledContainerColor: Color? = null,
    /** When null, disabled content dims the resolved content colour to 38%
     *  alpha. The watch overrides with its own 55% version. */
    disabledContentColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /** Hold-to-act variety: the chevron easter egg, the cover screen's
     *  flash-lights. Null means plain click-only, exactly like M3 `Button`. */
    onLongClick: (() -> Unit)? = null,
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /** Asymmetric shapes (split-pill halves, connected-group segments) get the
     *  raw morph progress and the animated corner percent to build from; null
     *  gets the plain single-corner-percent pill. */
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** The phone uses StiffnessLow for its gentle morph; the watch uses
     *  StiffnessMedium because the slower corner change alone didn't register
     *  on a small round face. Deliberately parameterised so the two can differ
     *  without the comment history this file's predecessors needed. */
    morphSpring: SpringSpec<Float> = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
    colorSpring: FiniteAnimationSpec<Color> = spring(stiffness = Spring.StiffnessMediumLow),
    /** Non-null scales the whole button down to this factor while pressed --
     *  the watch's press-punch, which the phone doesn't use. */
    pressScale: Float? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val resolvedContent = if (active) activeContentColor else contentColor
    val disabledContent = disabledContentColor ?: resolvedContent.copy(alpha = 0.38f)

    // The split: the clickable ANIMATED half lives in `MorphChrome` (child
    // scope -- it recomposes every morph frame), the CONTENT is a stable
    // sibling that never recomposes for the animation. Previously all three
    // animations (corner shape, background, press scale) were read in THIS
    // scope, so every frame of any morph/active/pressed state re-ran the
    // entire button including the icon/label Texts -- a dozen buttons on a
    // page re-running all their labels for every frame of a single morph.
    // Now the per-frame work is the tiny chrome Box; the Texts sit above it,
    // composed once.
    Box(modifier = modifier) {
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
            border = border,
            morphSpring = morphSpring,
            colorSpring = colorSpring,
            pressScale = pressScale,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
        // Stable content -- drawn OVER the chrome; taps reach the chrome
        // below (nothing here consumes them).
        Row(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

/** The clickable animated half: morphing shape + clip (so the ripple exactly
 *  tracks it), sprung background, press scale, clickable + long-click. The
 *  ONLY composable that recomposes per animation frame. */
@Composable
private fun MorphChrome(
    pressed: Boolean,
    active: Boolean,
    interactionSource: MutableInteractionSource,
    pillCornerPercent: Float,
    morphedCornerPercent: Float,
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)?,
    activeContainerColor: Color,
    containerColor: Color,
    disabledContainerColor: Color?,
    border: BorderStroke?,
    morphSpring: SpringSpec<Float>,
    colorSpring: FiniteAnimationSpec<Color>,
    pressScale: Float?,
    enabled: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val morph by animateFloatAsState(
        targetValue = if (active || pressed) 1f else 0f,
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
    val scale by animateFloatAsState(
        targetValue = if (pressed && pressScale != null) pressScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "morphPressScale",
    )
    val fill = disabledContainerColor ?: bg
    // The BorderStroke passed in by the caller is the same mutable object in
    // the parent scope; Modifier.border reads it here on every morph frame --
    // cheap, and it keeps the reference semantics identical to before.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .then(
                if (border != null) {
                    Modifier.background(color = fill, shape = shape).border(border, shape)
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

