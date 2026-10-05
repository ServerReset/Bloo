package com.bloo.bluelink.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bloo.uicommon.MorphButtonCore

/** One icon-only segment in a connected button group (see [ButtonCluster]). */
internal data class GroupIconAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

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
    active: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    /**
     * Overrides the disabled content tone (default: content at 38% alpha, so only the label fades).
     * The cover action button passes a full-alpha tone because it dims the whole pill itself.
     */
    disabledContentColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // Asymmetric shape replacing the plain pill-to-square morph (connected group segment, split
    // button half). Receives the raw morph progress (0 = pill, 1 = morphed) and the corner percent.
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** Hold-to-act action (chevron easter egg, cover flash-lights). */
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
    // What this button's label reports, for the long-press name hint.
    val hint = remember { LabelHintState() }
    // The frost's animation state only exists for a button that has ever been inert (nearly all
    // never are).
    var everInert by remember { mutableStateOf(!enabled) }
    SideEffect { if (!enabled) everInert = true }
    val frost = if (everInert || !enabled) {
        Modifier.frosted(!enabled, RoundedCornerShape(pillCornerPercent.toInt()), blurRadius = 1.2.dp, rim = false, veil = false)
    } else {
        Modifier
    }
    // Press and hold a symbol-only button: haptic ticks build, the button shakes, a heavy pulse lands, and then
    // the button itself lifts out of its row above the finger and widens to show its name (see LabelHint).
    // Letting go drops it back, and the hold never presses it.
    if (enabled && onLongClick == null) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        hint.suppressClick = false
                        if (!hint.collapsed) return@collectLatest
                        val build = launch {
                            delay(120)
                            var gap = 70L
                            repeat(3) {
                                haptics?.tick()
                                delay(gap)
                                gap = (gap * 0.8f).toLong().coerceAtLeast(30L)
                            }
                        }
                        delay(HOLD_TO_EXPAND_MS)
                        build.cancel()
                        haptics?.heavy()
                        hint.shake()
                        haptics?.heavy()
                        hint.beginLift()
                        hint.raise()
                        awaitCancellation()
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> hint.endLift()
                }
            }
        }
    }
    val providedContent = if (enabled) {
        resolvedContent
    } else {
        // Keep the button's full background when disabled (only the label fades) instead of M3's
        // default onSurface@12%, which is invisible against light cards and made disabled buttons
        // look backgroundless.
        disabledContentColor ?: resolvedContent
    }
    // The button itself, drawn once in its slot and once more, as the lifted copy, in the hold-to-explain layer.
    @Composable
    fun core(coreModifier: Modifier, onCoreClick: () -> Unit, source: MutableInteractionSource, state: LabelHintState, withLayer: Boolean) {
        CompositionLocalProvider(LocalContentColor provides providedContent, LocalLabelHint provides state) {
            MorphButtonCore(
                onClick = onCoreClick,
                modifier = coreModifier,
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
                interactionSource = source,
                onLongClick = onLongClick,
                pillCornerPercent = pillCornerPercent,
                morphedCornerPercent = morphedCornerPercent,
                shapeForCorner = shapeForCorner,
                content = {
                    content()
                    if (withLayer) {
                        LabelHintLayer(state) { copy -> core(Modifier, {}, remember { MutableInteractionSource() }, copy, false) }
                    }
                },
            )
        }
    }
    val body: @Composable () -> Unit = {
        core(
            modifier
                // `active` is otherwise a colour-only change -- most call sites also swap their label text
                // (Lock/Unlock, Start/Stop), which is why this mostly "worked" for TalkBack by accident.
                .semantics { selected = active }
                .onPlaced { hint.coordinates = it }
                // Out of the layout while its lifted copy is up, and out of sight.
                .liftCollapse(hint)
                .graphicsLayer { rotationZ = hint.shakeDegrees; alpha = if (hint.lifted) 0f else 1f }
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
                .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier),
            // A hold that lifted the button is not a press: its click is swallowed.
            {
                if (hint.suppressClick) {
                    hint.suppressClick = false
                } else {
                    clickHaptic()
                    onClick()
                }
            },
            interactionSource,
            hint,
            true,
        )
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
            suppressPress = { hint.lifted },
        ) { body() }
    } else {
        body()
    }
}

/**
 * How a button speaks, resolved to colours in one place. [Tonal]: the standard button. [Confirm]: go ahead
 * (green). [Deny]: remove, sign out, stop (red).
 */
enum class ButtonEmphasis { Tonal, Confirm, Deny }

@Composable
internal fun ButtonEmphasis.container(): Color = when (this) {
    ButtonEmphasis.Tonal -> buttonContainer()
    ButtonEmphasis.Confirm -> confirmTone().container
    ButtonEmphasis.Deny -> denyTone().container
}

@Composable
internal fun ButtonEmphasis.content(): Color = when (this) {
    ButtonEmphasis.Tonal -> MaterialTheme.colorScheme.onSurface
    ButtonEmphasis.Confirm -> confirmTone().content
    ButtonEmphasis.Deny -> denyTone().content
}

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
) {
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        // A pending action is inert and iced out.
        enabled = enabled && !pending,
        groupWeight = groupWeight,
        interactionSource = interactionSource,
        containerColor = containerColor.takeOrElse { emphasis.container() },
        contentColor = contentColor.takeOrElse { emphasis.content() },
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = GapRow),
        minHeight = ButtonTargetHeight,
    ) {
        val glyph = (icon ?: standardButtonIcon(text)).takeIf { showIcon }
        if (glyph != null) {
            MorphButtonLabel(glyph, text, pending = pending)
        } else {
            Text(
                text,
                style = ButtonLabelStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** [MorphTextButton] that owns its interaction source and wraps itself in [SafeExpansiveButton]. */
@Composable
fun SafeMorphTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasis: ButtonEmphasis = ButtonEmphasis.Tonal,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    icon: ImageVector? = null,
    showIcon: Boolean = true,
    pending: Boolean = false,
    groupWeight: Float = GroupWeightProportional,
    /**
     * False for a button sharing its row with other content: it keeps the small press push instead
     * of widening.
     */
    fillOnPress: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    SafeExpansiveButton(
        interactionSource = source,
        enabled = enabled,
        // The wrapper is what the group reads, so the weight has to be declared HERE -- passed only
        // to the inner button it was silently dropped and every labelled button sat at width zero
        // weight, never filling its line.
        groupWeight = groupWeight,
        fillOnPress = fillOnPress,
    ) {
        MorphTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            emphasis = emphasis,
            containerColor = containerColor,
            contentColor = contentColor,
            interactionSource = source,
            icon = icon,
            showIcon = showIcon,
            pending = pending,
            groupWeight = groupWeight,
        )
    }
}

/**
 * **The** standard action button: a glyph, a label, a tonal fill and a hairline rim. Padding is
 * 18dp/8dp on the shared [ButtonTargetHeight].
 */
@Composable
fun MorphActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Swaps the glyph for the shared in-flight spinner; pass a real flag, not `true`. */
    pending: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /** See [MorphButton]'s own `groupWeight`; labelled buttons default to proportional-to-label. */
    groupWeight: Float = GroupWeightProportional,
    /** See [MorphButton]'s own `expressive`. */
    expressive: Boolean = false,
    /** See [MorphButton]'s own `fillOnPress`; false when the button shares its row. */
    fillOnPress: Boolean = true,
    /** The button's weight in the hierarchy, same as [MorphTextButton]'s. */
    emphasis: ButtonEmphasis = ButtonEmphasis.Tonal,
    /** A toggle's "on" state: the primary fill, same as [MorphButton]'s own `active`. */
    active: Boolean = false,
) {
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        // A pending action is inert and iced out.
        enabled = enabled && !pending,
        active = active,
        interactionSource = interactionSource,
        groupWeight = groupWeight,
        expressive = expressive,
        fillOnPress = fillOnPress,
        // Tonal keeps MorphButton's own default fill; the others take their emphasis colours.
        containerColor = if (emphasis == ButtonEmphasis.Tonal) MaterialTheme.colorScheme.secondaryContainer else emphasis.container(),
        contentColor = if (emphasis == ButtonEmphasis.Tonal) MaterialTheme.colorScheme.onSecondaryContainer else emphasis.content(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = GapRow),
    ) {
        // The shared label gives the glyph its standard gap and lets a cramped group know how small
        // it can get.
        MorphButtonLabel(icon, label, pending = pending)
    }
}

/** Every button in the app is this tall, so a row of them lines up whatever it contains. */
val ButtonTargetHeight = 48.dp

/** The glyph beside a label. One size for push buttons, chips and cover actions alike. */
val ButtonIconSize = 18.dp

/** The glyph in a label-less button (the expand chevron); larger because the icon IS the button. */
val ButtonIconOnlySize = 24.dp

/** The one label style every button uses. */
val ButtonLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium
