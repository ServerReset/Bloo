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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.bloo.uicommon.connectedGroupShape

/** One icon-only segment in a connected button group (see [connectedGroupShape]). */
internal data class GroupIconAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)


/**
 * The one button style used across the whole app. It rests as a **pill** and
 * becomes a **rounded rectangle** only while [active] (an on/toggled state) - or
 * momentarily while pressed. When [active], it fills with [activeContainerColor].
 * Its width springs (with a little overshoot) whenever the content width changes,
 * e.g. the label flips Start -> Stop.
 *
 * This IS the shared [MorphButtonCore] from :uicommon -- dressed in this
 * module's Material theme colours, haptics and M3 content padding, plus two
 * phone-wide conventions:
 *
 *  - [minHeight] of 48dp (the M3 touch target the old `Button` enforced
 *    implicitly) unless a caller opts out to keep a shorter pill
 *    (split-button halves, preset pills).
 *  - `selected = [active]` semantics, so TalkBack hears the state, not just
 *    the label ("Unlock" says what happens, not what is).
 *
 * Every other button-looking control in this app -- the split action+chevron
 * pills, the standalone chevron, the preset pills, the cover action bar --
 * is this same component; the ones that look different simply pass different
 * shapes (shapeForCorner) and colours. There are no separate button types.
 *
 * The default fill is the tonal-with-outline treatment (`secondaryContainer` +
 * a hairline `outline` rim) that used to live only on [MorphActionButton], the
 * map's own Expand/Open in Maps pair -- reported directly as the look wanted
 * everywhere ("more contrast, an outline"), and every idle button not already
 * carrying an explicit colour override (the header action+chevron pills, the
 * standalone chevron, dialog buttons that don't set their own tone) is exactly
 * such a case, so promoting it here is what actually makes it the standard
 * instead of one component quietly staying the odd one out. `active` still
 * fills solid `primary` regardless -- that's a state colour, not this idle
 * one, and stays exactly as loud as it always was.
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
    /** Overrides the disabled content tone (default: resolved content at 38%
     *  alpha -- "only the label fades"). The cover action button passes its
     *  own full-alpha tone because it dims the WHOLE pill itself. */
    disabledContentColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // An asymmetric shape to use instead of the plain pill<->square morph --
    // for a connected button-group segment (see StateControl), or a split
    // button half, whose inner (seam) corners stay small while the outer
    // corner is the one that morphs. Receives the raw morph progress
    // (0 = pill, 1 = fully morphed) and the animated corner percent, so the
    // shape can derive any corner geometry from the button's own spring.
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** Hold-to-act action (chevron easter egg, cover flash-lights). */
    onLongClick: (() -> Unit)? = null,
    /** Haptic for a plain click; null = the standard click() pulse. The lock
     *  button overrides with heavy(), the chevron with tick()/click() by
     *  direction. */
    onClickHaptic: (() -> Unit)? = null,
    /** The pill's corner-percent when idle (50 = perfect pill) and when
     *  [active]/pressed (default 28 = the app's standard rounded square).
     *  Overridable so a fixed-height square button (cover actions, chevron
     *  nub) can land on its own exact corner radius. */
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /** [ButtonTargetHeight], the app's one button height -- not a re-typed `48.dp`,
     *  which is what this default was even though [ButtonTargetHeight] is declared in
     *  this same file for exactly this, two call sites below already pass it back in
     *  explicitly ([MorphActionButton]'s own doc even claims "the shared 48dp
     *  ButtonTargetHeight" for a button that reaches this default), and it is also the
     *  minimum touch target M3 `Button` enforced implicitly. One name means a change to
     *  the app's button height cannot leave the buttons that rely on the default behind.
     *  Pass 0.dp to let a short pill keep its natural height. */
    minHeight: Dp = ButtonTargetHeight,
    /**
     * Inside a button group, this button's share of the row's leftover space (0 = keep its
     * natural width). This is the group's replacement for Modifier.weight, which cannot reach
     * a group member -- see ExpressiveGroupData.weight.
     */
    groupWeight: Float = 0f,
    /** Wraps the button in [SafeExpansiveButton] itself -- the press-grow feedback -- so a call
     *  site sets a flag instead of hand-wiring a wrapper around a shared interaction source.
     *  Always on inside an [ExpressiveButtonGroup], where the button joins on its own. */
    expressive: Boolean = false,
    /** For a labelled button alone on its row: rests at its natural width on the start edge and
     *  widens to the whole row when pressed. See [SafeExpansiveButton]'s own `fillOnPress`.
     *  Only takes effect together with [expressive]. */
    fillOnPress: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val clickHaptic = onClickHaptic ?: { haptics?.click() }
    // The content tone content lambdas inherit, provided the way M3's Button
    // provides it internally (the shared core is foundation-only and cannot
    // reach material3's LocalContentColor).
    val resolvedContent = if (active) activeContentColor else contentColor
    // What this button's label (if it has one) reports, for the long-press name hint.
    val hint = remember { LabelHintState() }
    // The frost's animation state only exists for a button that has ever been inert: nearly all never are,
    // and this runs for every button in the app.
    var everInert by remember { mutableStateOf(!enabled) }
    SideEffect { if (!enabled) everInert = true }
    val frost = if (everInert || !enabled) {
        Modifier.frosted(!enabled, RoundedCornerShape(pillCornerPercent.toInt()), blurRadius = 1.2.dp, rim = false, veil = false)
    } else {
        Modifier
    }
    // Press and hold a symbol-only button: after a short hold it EXPANDS IN PLACE to show its
    // name (hint.onHoldStart -> the label layout reports its full width -> the button's own
    // animateContentSize springs wider), and collapses back the moment the finger lifts. The
    // phone also builds a vibration during the hold so the expansion is felt coming. This
    // replaces the old glass BUBBLE that popped up above the button and auto-hid on a timer --
    // reported directly as wanting the control to grow where it is instead of a popup.
    if (enabled && onLongClick == null) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        if (!hint.collapsed) return@collectLatest
                        val built = launch {
                            delay(120)
                            var gap = 85L
                            repeat(6) { i ->
                                if (i < 3) haptics?.tick() else haptics?.click()
                                delay(gap)
                                gap = (gap * 0.8f).toLong().coerceAtLeast(30L)
                            }
                        }
                        delay(HOLD_TO_EXPAND_MS)
                        hint.onHoldStart()
                        built.join()
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> hint.onHoldEnd()
                }
            }
        }
    }
    val providedContent = if (enabled) {
        resolvedContent
    } else {
        // Keep the button's full background when disabled (only the label
        // fades) instead of M3's default onSurface@12%, which is invisible
        // against light cards and made disabled buttons look backgroundless.
        disabledContentColor ?: resolvedContent
    }
    val body: @Composable () -> Unit = {
        CompositionLocalProvider(LocalContentColor provides providedContent, LocalLabelHint provides hint) {
            MorphButtonCore(
                onClick = { clickHaptic(); onClick() },
                modifier = modifier
                    // `active` is otherwise a colour-only change -- most call sites also
                    // swap their label text (Lock/Unlock, Start/Stop), which is why this
                    // mostly "worked" for TalkBack by accident, but that's caller
                    // discipline, not something the shared button guarantees. Setting
                    // `selected` here makes every MorphButton correct by construction:
                    // the app's one button framework, so this is the single highest-
                    // leverage place to fix it.
                    .semantics { selected = active }
                    // Can't be pressed right now: iced out, the app's one disabled look (see [frosted]).
                    .then(frost)
                    // Skipped while SafeExpansiveButton is already smoothly driving this
                    // button's width on press (LocalExpressiveGrowth -- see its own doc):
                    // animateContentSize exists for a genuine content change (a label
                    // swapping to a longer one), but left unconditional it ALSO re-smoothed
                    // a width the wrapper was already animating frame by frame with its own,
                    // deliberately non-bouncy spring -- two springs chasing the same width at
                    // once, which is what made a growing button wobble on press instead of
                    // just growing. Still applies normally to a button with no such wrapper.
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
                enabled = enabled,
                active = active,
                containerColor = containerColor,
                activeContainerColor = activeContainerColor,
                contentPadding = contentPadding,
                border = if (active) null else border,
                // Disabled = the app's standard frosted glass, not a washed-out tonal
                // pill: the shared glass tint (translucent, blur-aware) with a matching
                // frosted rim, so a dimmed button reads as an inert pane of glass sitting
                // in the layout rather than a broken button.
                disabledContainerColor = glassTint(canBlurBackdrops()),
                disabledBorder = BorderStroke(1.dp, hairlineColor()),
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                pillCornerPercent = pillCornerPercent,
                morphedCornerPercent = morphedCornerPercent,
                shapeForCorner = shapeForCorner,
                content = {
                    content()
                },
            )
        }
    }
    // Join the group WITHOUT the call site having to know. A button in an ExpressiveButtonGroup
    // only takes part in the press redistribution if it carries the group's parent data, and
    // that data comes from SafeExpansiveButton -- which only MorphChip and the expand chevron
    // wrapped themselves in. Every other button dropped into a group was silently a non-member:
    // it kept its natural width, took no part, and its neighbours had nothing to give up. That
    // is the "it just pushes them and they don't shrink" behaviour, and it could reappear with
    // any new call site, so the button joins itself rather than relying on the call site to
    // remember. Wrapping is idempotent -- see SafeExpansiveButton's group branch.
    // A labelled button (one that declares a width weight) follows the standard press behaviour
    // wherever it sits: in a group it shares the line, alone it rests at its natural width on the
    // start edge and fills the row when pressed. [LocalExpressiveGrowth] is true inside a wrapper
    // that has already done this (SafeMorphTextButton, an explicit SafeExpansiveButton), so the
    // standard behaviour is never applied twice.
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


/**
 * How loudly a button speaks. One choice, resolved to colours in one place, so the same kind of
 * action looks the same on every screen instead of each call site hand-picking a container.
 *
 *  - [Tonal]: the standard button -- everything that is not the one thing you came to do.
 *  - [Primary]: the screen's or card's main action (Save, Sync now, Sign in).
 *  - [Destructive]: removes or signs out; red is information, not decoration.
 */
enum class ButtonEmphasis { Tonal, Primary, Destructive }


@Composable
internal fun ButtonEmphasis.container(): Color = when (this) {
    ButtonEmphasis.Tonal -> buttonContainer()
    ButtonEmphasis.Primary -> MaterialTheme.colorScheme.primary
    ButtonEmphasis.Destructive -> MaterialTheme.colorScheme.errorContainer
}


@Composable
internal fun ButtonEmphasis.content(): Color = when (this) {
    ButtonEmphasis.Tonal -> MaterialTheme.colorScheme.onSurface
    ButtonEmphasis.Primary -> MaterialTheme.colorScheme.onPrimary
    ButtonEmphasis.Destructive -> MaterialTheme.colorScheme.onErrorContainer
}


/**
 * A text-only [MorphButton] - the app's one button framework, used everywhere a
 * plain labelled button is needed (dialogs, settings, etc.) so they all share
 * the pill-morphs-to-rounded-square press feel.
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
     * The glyph to lead with. Null asks [standardButtonIcon] for the standard one for [text],
     * so an existing call site gets one without being touched; pass one to override, or set
     * [showIcon] false for the rare button that should not have any.
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
        // A pending action (a request in flight) is inert and iced out like any button that can't be pressed.
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


/**
 * [MorphTextButton] with its own press-feedback wrapper: owns the interaction source and wraps
 * itself in [SafeExpansiveButton], so a call site is one call instead of a hand-wired
 * source + wrapper + button trio.
 */
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
    /** False for the rare button that shares its row with other content (a field, a label): it
     *  keeps the small press push instead of widening over its neighbours. */
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
 * **The** standard action button: a glyph, a label, a tonal fill and a hairline rim.
 *
 * This is the one look for "tap this and something happens" -- Expand / Open in Maps on
 * the map, every owner-area destination on the car-info pebble, Set place / My location,
 * Change PIN, Add a tile, Reload. It was previously re-typed inline at each of those call
 * sites with a slightly different container, padding or border (or none), which is exactly
 * what made a screen full of buttons read as several unrelated button families.
 *
 * No longer overrides [MorphButton]'s container/content/border at all -- the tonal fill
 * and hairline rim this button made the reference for ("more contrast, an outline") are
 * now [MorphButton]'s own bare defaults, so every idle button in the app gets them, not
 * just this one. What's left here is purely the padding: 18dp/8dp on the shared 48dp
 * [ButtonTargetHeight], so a row mixing these with [MorphTextButton]s still lines up.
 *
 * Deliberately NOT the button for: a destructive action (keep `errorContainer` -- red is
 * information, not decoration), a screen's single primary CTA (`active = true` / an
 * explicit `primary` fill), a toggle showing state (StateControl), a picker
 * ([MorphSegmented]), or a low-emphasis tertiary link ([MorphTextButton]).
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
        // A pending action (a request in flight) is inert and iced out like any button that can't be pressed.
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
        // The shared label, so the glyph gets the standard gap and -- the part a
        // hand-assembled Icon+Spacer+Text cannot do -- the button can tell a cramped
        // button group how small it is willing to get.
        MorphButtonLabel(icon, label, pending = pending)
    }
}


/** Every button in the app is this tall, so a row of them lines up whatever it contains. */
val ButtonTargetHeight = 48.dp


/** The glyph beside a label. One size for push buttons, chips and cover actions alike. */
val ButtonIconSize = 18.dp


/** The glyph in a button that has NO label -- the expand chevron. Larger on purpose: with no
 *  word beside it the icon IS the button, and an 18dp glyph in a 48dp target reads as a speck. */
val ButtonIconOnlySize = 24.dp


/** The one label style every button uses. Bigger and heavier than the ambient body default the
 *  buttons used to inherit, which is what made them read as text that happened to be tappable. */
val ButtonLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleMedium
