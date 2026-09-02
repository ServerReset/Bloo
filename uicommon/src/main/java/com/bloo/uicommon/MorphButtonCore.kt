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
    // contentAlignment = Center, and the content Row below WRAPS instead of
    // filling: between them they reproduce exactly what the single-Row version
    // of this component did before the chrome/content split, which is that the
    // CONTENT decides the button's size and `modifier` (weight/fillMaxWidth/
    // heightIn from the callers) is what stretches it.
    //
    // Both children used to be fillMaxSize(), which left this Box with NO
    // wrap-content child at all -- so the button had no intrinsic size of its
    // own and simply took whatever the incoming constraints allowed. That is
    // one bug with two very different-looking symptoms, both reported from
    // real screenshots:
    //
    //  - Width is bounded almost everywhere, so every button GREW to the full
    //    width available. In a pebble header that meant the un-weighted
    //    SplitExpandButton ate the whole row, leaving nothing for the weighted
    //    title Column (titles vanished) and pushing the chevron half out past
    //    the clip (chevrons vanished) -- "the main buttons are just totally
    //    fucked, no chevrons, big and the sizes are all weird".
    //  - Height inside a vertically-unbounded parent (a settings card body, an
    //    info pebble's rows -- any Column in a scrollable) has maxHeight
    //    Infinity, where fillMaxSize() cannot apply a height at all. The chrome
    //    Box holds no content, so it wrapped to ZERO height and its background
    //    drew nothing, while the content Row still wrapped its label normally
    //    and stayed visible -- "the smaller buttons don't have a shape or
    //    background anymore", text with no pill behind it.
    //
    // matchParentSize() (not fillMaxSize) is the fix on the chrome side: it is
    // measured AFTER, and against, the size this Box got from its real content,
    // so the chrome always exactly covers the button without ever contributing
    // to -- or inflating -- its size.
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
        // below (nothing here consumes them). Wrap-content (see the Box's own
        // comment): this is the child that gives the button its size.
        Row(
            modifier = Modifier.padding(contentPadding),
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
    border: BorderStroke?,
    morphSpring: SpringSpec<Float>,
    colorSpring: FiniteAnimationSpec<Color>,
    pressScale: Float?,
    enabled: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    // `active` drives the morph for a STANDALONE button (shapeForCorner == null) -- the
    // lock/unlock body, the lone expand chevron -- where "pill calm, rounded-square
    // highlighted" is the whole point (see StateControl's own doc). It must NOT also drive
    // the morph for a connected pair (a split action+chevron, a segmented group): those
    // halves each run their OWN independent morph, so the moment one half sits in a
    // standing `active` state (Charge's "Stop" while charging, an expanded pebble's own
    // chevron) while its neighbour does not, the two only ever agree by coincidence. Two
    // symptoms, same cause, both reported from real screenshots: the active half's OUTER
    // corner -- the free end that is supposed to stay a stable pill cap matching its
    // neighbour's -- morphed toward the squarish shape instead, and the two halves' SEAM
    // corners (10dp idle -> 16dp "morphed") drifted apart because only the active half's
    // seam had opened, leaving a visible step where the pill was supposed to read as one
    // connected piece. Gating on shapeForCorner == null keeps a connected half's shape
    // reacting to a real PRESS (still tactile, still transient, still per-segment) while
    // leaving its standing active/expanded state to do what it already does elsewhere --
    // change the FILL colour -- without also warping the one corner that has to keep
    // matching its neighbour's.
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
            // === matchParentSize() CRITICAL: NOT fillMaxSize() ===
            // This is the animated background layer. It MUST match the parent
            // Box's actual size without influencing that size.
            //
            // WHY NOT fillMaxSize()?
            // fillMaxSize() measures against incoming layout CONSTRAINTS, not
            // against the parent Box's actual content size. This caused:
            //   - Buttons in bounded containers (Row): parent Box gets real size
            //     from content → fillMaxSize() also gets that constraint →
            //     MorphChrome fills it → but ALSO influences sizing → in an
            //     un-weighted SplitExpandButton, this made it eat the entire row
            //   - Buttons in unbounded containers (Column in scrollable, height
            //     constraint = Infinity): parent Box measures content size (e.g.,
            //     48dp for label + padding) → fillMaxSize() sees maxHeight=Infinity
            //     → cannot apply that to the chrome → chrome becomes 0-height →
            //     background disappears even though content Row is visible
            //
            // WHY matchParentSize()?
            // matchParentSize() is measured AFTER the parent Box has its real
            // size. It then exactly matches that size. This means:
            //   - Chrome always exactly covers what the Button became
            //   - Chrome never adds to the button's size (even when scaled in
            //     press animation, the scale happens around the chrome's own
            //     measured bounds, not against incoming constraints)
            //   - Caller's modifier (weight, fillMaxWidth, heightIn, etc.) can
            //     still stretch the Button, and chrome adapts to match
            //   - In unbounded heights, chrome measures the Row's actual height
            //     (not Infinity) and draws the background correctly
            .matchParentSize()
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

