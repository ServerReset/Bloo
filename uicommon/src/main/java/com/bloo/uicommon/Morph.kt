package com.bloo.uicommon

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphButtonCore
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.standardButtonIcon
import com.bloo.uicommon.MorphButtonLabel
import com.bloo.uicommon.ButtonTargetHeight
import com.bloo.uicommon.ButtonLabelStyle
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.ButtonPaddingH
import com.bloo.uicommon.canBlurBackdrops
import com.bloo.uicommon.inertButtonFrost
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.SafeExpansiveButton

/**
 * The one button style used across the app: rests as a **pill**, becomes a **rounded rectangle**
 * while [active] or pressed, and fills with [activeContainerColor] when active. Width springs when
 * the content width changes.
 */
@Composable
fun MorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Whether a disabled button wears the inert frost. False for one that is busy (a pending command): it
     *  is disabled to block taps, not "off", and a blur under its spinner would re-render every frame. */
    frostWhenDisabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /**
     * Overrides the disabled content tone (default: content at 38% alpha, so only the label fades).
     * A caller that dims the whole pill itself passes a full-alpha tone.
     */
    disabledContentColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // Asymmetric shape replacing the plain pill-to-square morph (connected group segment, split
    // button half). Receives the raw morph progress (0 = pill, 1 = morphed) and the corner percent.
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** Hold-to-act action (the chevron's spin easter egg). Null = plain click-only. */
    onLongClick: (() -> Unit)? = null,
    /** Haptic for a plain click; null = the standard click() pulse. */
    onClickHaptic: (() -> Unit)? = null,
    /**
     * Corner percent when idle (50 = perfect pill) and when [active]/pressed (28 = standard rounded
     * square).
     */
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /**
     * The app's one button height ([ButtonTargetHeight]); pass 0.dp to let a short pill keep its
     * natural height.
     */
    minHeight: Dp = ButtonTargetHeight,
    /**
     * Inside a button group, this button's share of the row's leftover space (0 = natural width).
     * Replaces Modifier.weight, which cannot reach a group member.
     */
    groupWeight: Float = 0f,
    /**
     * Wraps the button in [SafeExpansiveButton] itself -- the press-grow feedback -- so a call site
     * sets a flag instead of hand-wiring a wrapper around a shared interaction source. Always on
     * inside an [ExpressiveButtonGroup], where the button joins on its own.
     */
    expressive: Boolean = false,
    /**
     * For a labelled button alone on its row: rests at natural width on the start edge and widens
     * to the row when pressed. Only takes effect together with [expressive].
     */
    fillOnPress: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val clickHaptic = onClickHaptic ?: { haptics?.click() }
    // The content tone content lambdas inherit, provided the way M3's Button provides it internally
    // (the shared core is foundation-only and cannot reach material3's LocalContentColor).
    val resolvedContent = if (active) activeContentColor else contentColor
    // The frost's animation state only exists for a button that has ever been inert (nearly all
    // never are).
    val inert = !enabled && frostWhenDisabled
    var everInert by remember { mutableStateOf(inert) }
    SideEffect { if (inert) everInert = true }
    val frost = if (everInert || inert) {
        Modifier.inertButtonFrost(inert)
    } else {
        Modifier
    }
    val providedContent = if (enabled) {
        resolvedContent
    } else {
        // A disabled button is one inert thing: the fill already goes to neutral glass (see
        // disabledContainerColor below), so the label goes muted too. Leaving it the vivid emphasis
        // colour read as a washed-out pill with a bright label -- worst on the Confirm/Deny/active
        // buttons that are actually coloured.
        disabledContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledContentAlpha)
    }
    val body: @Composable () -> Unit = {
        CompositionLocalProvider(LocalContentColor provides providedContent) {
            MorphButtonCore(
                onClick = { clickHaptic(); onClick() },
                modifier = modifier
                    // `active` is otherwise a colour-only change -- most call sites also swap their label text
                    // (Lock/Unlock, Start/Stop), which is why this mostly "worked" for TalkBack by accident.
                    .semantics { selected = active }
                    // Inert: the app's one disabled look (see [frosted]).
                    .then(frost)
                    // Skipped while SafeExpansiveButton is already smoothly driving this button's width on press
                    // (LocalExpressiveGrowth): animateContentSize is for a genuine content change, and left on it
                    // re-smoothed a width the wrapper was already animating, so a growing button wobbled.
                    .then(
                        if (LocalExpressiveGrowth.current) {
                            Modifier
                        } else {
                            Modifier.animateContentSize(
                                lowPowerAwareSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            )
                        },
                    )
                    // Never narrower than tall: the smallest a button gets (symbol only) is a circle.
                    .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight).widthIn(min = minHeight) else Modifier),
                enabled = enabled,
                active = active,
                containerColor = containerColor,
                activeContainerColor = activeContainerColor,
                contentPadding = contentPadding,
                border = if (active) null else border,
                // Disabled = the app's standard frosted glass, not a washed-out tonal pill: the shared glass
                // tint (translucent, blur-aware) with a matching rim, so a dimmed button reads as an inert
                // pane of glass in the layout rather than a broken button.
                disabledContainerColor = glassTint(canBlurBackdrops()),
                disabledBorder = BorderStroke(1.dp, hairlineColor()),
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                pillCornerPercent = pillCornerPercent,
                morphedCornerPercent = morphedCornerPercent,
                shapeForCorner = shapeForCorner,
                content = content,
            )
        }
    }
    // Join a group without the call site knowing: a button only takes part in the press
    // redistribution if it carries the group's parent data from SafeExpansiveButton. Wrapping is
    // idempotent.
    val standalone = groupWeight != 0f && !LocalExpressiveGrowth.current
    if (LocalExpressiveGroup.current || expressive || standalone) {
        SafeExpansiveButton(
            interactionSource = interactionSource,
            enabled = enabled,
            groupWeight = groupWeight,
            fillOnPress = fillOnPress || standalone,
        ) { body() }
    } else {
        body()
    }
}

/** How far a disabled button's label is muted; the fill is neutral glass of its own. */
private const val DisabledContentAlpha = 0.55f



/**
 * A text-only [MorphButton] used wherever a plain labelled button is needed (dialogs, settings).
 */
@Composable
fun MorphTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** The button's weight in the hierarchy; sets the colours unless one is passed explicitly. */
    emphasis: ButtonEmphasis = ButtonEmphasis.Tonal,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /**
     * The glyph to lead with. Null asks [standardButtonIcon] for the standard one for [text];
     * [showIcon] false suppresses it.
     */
    icon: ImageVector? = null,
    showIcon: Boolean = true,
    /** Swaps the glyph for the shared in-flight spinner; pass a real flag, not `true`. */
    pending: Boolean = false,
    /** See [MorphButton]'s own `groupWeight`; labelled buttons default to proportional-to-label. */
    groupWeight: Float = GroupWeightProportional,
    /** A toggle's "on" state: the primary fill. */
    active: Boolean = false,
    /** See [MorphButton]'s own `expressive`. On by default: every text button in the app presses the same way. */
    expressive: Boolean = true,
    /** See [MorphButton]'s own `fillOnPress`; false when the button shares its row. */
    fillOnPress: Boolean = true,
) {
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        // A pending action takes no taps, but is busy rather than "off": no frost over its spinner.
        enabled = enabled && !pending,
        frostWhenDisabled = !pending,
        active = active,
        groupWeight = groupWeight,
        expressive = expressive,
        fillOnPress = fillOnPress,
        interactionSource = interactionSource,
        containerColor = containerColor.takeOrElse { emphasis.container() },
        contentColor = contentColor.takeOrElse { emphasis.content() },
        contentPadding = StandardButtonPadding,
        minHeight = ButtonTargetHeight,
    ) {
        val glyph = (icon ?: standardButtonIcon(text)).takeIf { showIcon }
        if (glyph != null) {
            MorphButtonLabel(glyph, text, pending = pending)
        } else {
            ButtonLabelText(text)
        }
    }
}

/** The standard action button: a glyph, a label and a tonal fill. [MorphTextButton] with the glyph required. */
@Composable
fun MorphActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pending: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    groupWeight: Float = GroupWeightProportional,
    expressive: Boolean = false,
    fillOnPress: Boolean = true,
    emphasis: ButtonEmphasis = ButtonEmphasis.Tonal,
    active: Boolean = false,
) = MorphTextButton(
    text = label,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    emphasis = emphasis,
    interactionSource = interactionSource,
    icon = icon,
    pending = pending,
    groupWeight = groupWeight,
    active = active,
    expressive = expressive,
    fillOnPress = fillOnPress,
)

/** Every button in the app is this tall, so a row of them lines up whatever it contains. */

/**
 * A lone button never grows wider than this, even when pressed to fill its row: a single action
 * stretched across a whole phone (or tablet) row reads as a slab, not a button. (2x the previous cap.)
 */
val MaxSingleButtonWidth = 480.dp

/** The glyph beside a label. One size for push buttons, chips and icon buttons alike. */

/** The glyph in a label-less button (the expand chevron); larger because the icon IS the button. */

/**
 * The smallest a standalone glyph control may be, in both axes: the glyph plus a comfortable tap
 * target, and square, so it reads as a circle. A clustered button's own floor is the cluster's height
 * instead (see ButtonCluster), which keeps a collapsed pill a true circle.
 */
val IconButtonMinWidth = 48.dp

/** The standard horizontal padding inside a labelled button, and inside a chip or list button. */

/**
 * The one content padding for a labelled button, used by every labelled button in the app.
 *
 * A `@Composable` getter, not a plain `val`: its vertical padding is [GapRow], which is itself a
 * composable getter (it reads the user's spacing scale), so this can only be resolved from a
 * composable. Declared exactly like [ButtonLabelStyle] below it, for the same reason.
 */
val StandardButtonPadding: PaddingValues
    @Composable get() = PaddingValues(horizontal = ButtonPaddingH, vertical = GapRow)

/**
 * The one button-label [Text]: the shared [ButtonLabelStyle], semi-bold, single line, ellipsized.
 * Used wherever a button shows a bare label with no glyph ([MorphTextButton], [MorphChip]).
 */
@Composable
fun ButtonLabelText(text: String) {
    RevealText(
        text,
        style = ButtonLabelStyle,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The one label style every button uses. */
