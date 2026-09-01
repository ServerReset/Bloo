@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.MorphButtonCore
import com.bloo.uicommon.connectedGroupShape
import kotlinx.coroutines.flow.first

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
 * This IS the shared [MorphButtonCore] from :uicommon -- the same machinery the
 * watch's MorphButton uses -- dressed in this module's Material theme colours,
 * haptics and M3 content padding, plus two phone-wide conventions:
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
 */
@Composable
fun MorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color = buttonContainer(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
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
    /** 48dp is the minimum touch-target height M3 `Button` enforced implicitly;
     *  pass 0.dp to let a short pill keep its natural height. */
    minHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val clickHaptic = onClickHaptic ?: { haptics?.click() }
    // The content tone content lambdas inherit, provided the way M3's Button
    // provides it internally (the shared core is foundation-only and cannot
    // reach material3's LocalContentColor).
    val resolvedContent = if (active) activeContentColor else contentColor
    val providedContent = if (enabled) {
        resolvedContent
    } else {
        // Keep the button's full background when disabled (only the label
        // fades) instead of M3's default onSurface@12%, which is invisible
        // against light cards and made disabled buttons look backgroundless.
        disabledContentColor ?: resolvedContent.copy(alpha = 0.38f)
    }
    val body: @Composable () -> Unit = {
        CompositionLocalProvider(LocalContentColor provides providedContent) {
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
                    .animateContentSize(
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    )
                    .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier),
                enabled = enabled,
                active = active,
                containerColor = containerColor,
                contentColor = contentColor,
                activeContainerColor = activeContainerColor,
                activeContentColor = activeContentColor,
                contentPadding = contentPadding,
                border = if (active) null else border,
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                pillCornerPercent = pillCornerPercent,
                morphedCornerPercent = morphedCornerPercent,
                shapeForCorner = shapeForCorner,
                content = content,
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
    if (LocalExpressiveGroup.current) {
        SafeExpansiveButton(interactionSource = interactionSource, enabled = enabled) { body() }
    } else {
        body()
    }
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
    containerColor: Color = buttonContainer(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /**
     * The glyph to lead with. Null asks [standardButtonIcon] for the standard one for [text],
     * so an existing call site gets one without being touched; pass one to override, or set
     * [showIcon] false for the rare button that should not have any.
     */
    icon: ImageVector? = null,
    showIcon: Boolean = true,
) {
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        containerColor = containerColor,
        contentColor = contentColor,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        minHeight = ButtonTargetHeight,
    ) {
        val glyph = (icon ?: standardButtonIcon(text)).takeIf { showIcon }
        if (glyph != null) {
            MorphButtonLabel(glyph, text, pending = false)
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

/**
 * The standard glyph for a button label.
 *
 * Keyed on the user-facing string, which is unusual and worth justifying: the alternative was
 * editing 45 call sites across a dozen files and adding an icon import to each, for a decision
 * ("Copy takes the copy glyph") that is the same everywhere and belongs in one place. Keeping it
 * central means the app cannot drift into three different icons for Clear, and a new button gets
 * the right one for free.
 *
 * It fails safe: an unrecognised label simply has no icon, and any call site can pass its own.
 * The cost is that renaming a label silently drops its glyph, which is why the map is grouped by
 * meaning rather than alphabetised -- a rename lands next to its neighbours.
 */
fun standardButtonIcon(label: String): ImageVector? = when (label) {
    // Dismissive
    "Cancel", "Not now", "Dismiss" -> Icons.Filled.Close
    // Destructive. Close, not a bin: this app's vocabulary has no delete glyph, and every icon
    // here is one the codebase already uses -- see the note above on why that constraint exists.
    "Clear", "Remove", "Delete" -> Icons.Filled.Close
    "Sign out" -> Icons.AutoMirrored.Filled.Logout
    "Unpin" -> Icons.Filled.PushPin
    // Confirmation -- the second tap of a two-step action, so it takes the affirmative glyph
    // rather than the destructive one it is confirming.
    "Tap again to confirm", "Tap again to reset", "Keep it", "Keep PIN" -> Icons.Filled.Check
    // Content
    "Copy" -> Icons.Filled.ContentCopy
    "Export" -> Icons.Filled.Download
    "Restore" -> Icons.Filled.CloudDone
    "Save as preset" -> Icons.Filled.Star
    // Disclosure. KeyboardArrowDown for both directions: the chevron this app already uses for
    // every expand control, rather than a second pair of glyphs meaning the same thing.
    "Show", "Full notes", "Trouble installing?" -> Icons.Filled.KeyboardArrowDown
    "Hide", "Hide install help", "Hide diagnostics" -> Icons.Filled.KeyboardArrowDown
    // Navigation / external
    "GitHub", "Open release page" -> Icons.AutoMirrored.Filled.OpenInNew
    // Sync / run
    "Test sync", "Pull from primary" -> Icons.Filled.CloudSync
    "Run it", "Working…" -> Icons.Filled.Bolt
    "Remind me" -> Icons.Filled.Refresh
    // Security
    "Use fingerprint" -> Icons.Filled.Fingerprint
    "Use PIN", "Update PIN", "Change PIN", "Set up PIN" -> Icons.Filled.Pin
    // Places / appearance
    "Set place" -> Icons.Filled.Place
    "Reset appearance" -> Icons.Filled.Palette
    else -> null
}


/**
 * The morph family's icon-only member.
 *
 * [MorphButton] is the wrong tool for a bare icon affordance -- a snackbar
 * action, a text-field's clear button, a 28dp edit glyph in a swatch grid -- since
 * it would wrap each one in a filled pill and change the design rather than unify
 * it. So this keeps [IconButton]'s containerless chrome and 40dp target exactly,
 * and adds the two things every other member of the family provides and these
 * were missing:
 *
 *  - **The click haptic.** Of the six bare IconButtons in this file, exactly ONE
 *    remembered to call `haptics?.click()` itself. Every Morph* control fires one;
 *    a containerless icon is no less of a button to the finger.
 *  - **A press response.** With no container there is no corner to morph, so the
 *    equivalent is a scale dip, on the family's own [SoftDamping] spring.
 *
 * Same parameter shape as [IconButton] so converting a call site is mechanical.
 * If you are converting one that already called the haptic by hand, delete that
 * call -- it fires here now, and two in a row is a stutter, not emphasis.
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    val pressed by interactionSource.collectIsPressedAsState()
    // Already optimal, and worth a note so nobody "fixes" it: `by` here costs nothing, because
    // what decides the phase of a snapshot read is WHERE the getter runs, not whether the
    // property is delegated. `scale` is referenced only inside the graphicsLayer BLOCK below,
    // so the read happens when Compose invokes that block -- composition and layout are
    // skipped. Google's own guidance shows exactly this shape (`val color by animateColorBetween(...)`
    // read inside `drawBehind { }`).
    //
    // I briefly rewrote this to `val scale = animateFloatAsState(...)` plus `scale.value`,
    // believing the delegated form forced a composition read. It does not; the two are
    // identical here. Reverted, because a comment asserting a difference that does not exist
    // teaches the next reader a false rule.
    //
    // The real audit question for the ~61 `by animate*AsState` sites in this project is not
    // `by` vs `=`. It is whether the value is read in the composable BODY (recomposes every
    // frame -- e.g. passed to `Modifier.padding(...)`, a `TextStyle`, or a size) or inside a
    // lambda modifier like `graphicsLayer {}` / `offset {}` / `drawBehind {}` (already
    // deferred, nothing to do). This site is the second kind.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "morphIconPress",
    )
    val body: @Composable () -> Unit = {
        IconButton(
            onClick = { haptics?.click(); onClick() },
            // On the button, not the icon: scaling the icon alone shrinks the glyph
            // inside a target that stays put, which reads as a glitch rather than a
            // press.
            modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            enabled = enabled,
            interactionSource = interactionSource,
            content = content,
        )
    }
    // Joins a group when it is in one -- see MorphButton's own note. An icon button is just as
    // likely to sit in a row of peers (the snackbar's copy/dismiss pair) as a labelled one.
    if (LocalExpressiveGroup.current) {
        SafeExpansiveButton(interactionSource = interactionSource, enabled = enabled) { body() }
    } else {
        body()
    }
}

/**
 * Standard leading slot for a [MorphButton]: shows the [icon], or a same-sized
 * spinner while [pending], so the button width never changes just from loading.
 */
@Composable
fun MorphButtonLabel(
    icon: ImageVector,
    label: String,
    pending: Boolean,
    iconSize: Dp = ButtonIconSize,
    spinning: Boolean = false,
) {
    if (pending) {
        LoadingIndicator(Modifier.size(iconSize))
    } else {
        // Always-composed Animatable, but it only runs while spinning - so idle
        // buttons don't each hold a live infinite animation, and we avoid calling
        // remember conditionally.
        val angle = remember { Animatable(0f) }
        LaunchedEffect(spinning) {
            if (spinning) {
                // Ramp up: the first revolution accelerates from rest...
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutLinearInEasing),
                )
                // ...then hold a steady, fast linear spin.
                while (true) {
                    angle.animateTo(
                        targetValue = angle.value + 360f,
                        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                    )
                }
            } else if (angle.value != 0f) {
                // Ramp down: decelerate to the next full turn, then reset.
                val target = kotlin.math.ceil(angle.value / 360f) * 360f
                angle.animateTo(target, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
                angle.snapTo(0f)
            }
        }
        Icon(
            icon,
            contentDescription = null,
            // Draw-phase read. This one matters most of the three: `angle` is a
            // continuously-running spin (the pending/refresh indicator loops
            // `while (true)`), so reading it through Modifier.rotate()'s argument
            // recomposed this Icon on EVERY FRAME for as long as the spinner ran.
            modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = angle.value },
        )
    }
    Spacer(Modifier.width(8.dp))
    // The one button label style, shared with MorphTextButton -- this pair is the reference the
    // rest of the app standardises on, so the size lives in a token rather than being whatever
    // each button happened to inherit.
    //
    // softWrap = false is a guarantee, not a nicety: these buttons are squeezed by their
    // neighbours in a group, and a label that wraps turns a one-line button into a two-line one
    // mid-press, which shoves the whole row's height around. The group will not squeeze below a
    // child's intrinsic width, so this should never trigger -- it is the backstop for the case
    // where a button is narrow for some other reason.
    Text(
        label,
        style = ButtonLabelStyle,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A unified selectable chip: a **pill** when unselected, morphing smoothly into a
 * filled **rounded box** when selected. Replaces ad-hoc FilterChips so selection
 * feels the same everywhere.
 */
@Composable
fun MorphChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val haptics = LocalHaptics.current
    val chipSelected = selected
    // Wrapped like every other button, so a chip presses the same way: inside an
    // ExpressiveButtonRow it takes width from its neighbours, and on its own it grows. Chips
    // were the one tappable control with no press wrapper at all, which is why a row of them
    // sat still while the buttons above them moved.
    val chipSource = remember { MutableInteractionSource() }
    SafeExpansiveButton(interactionSource = chipSource, enabled = true) {
    // The same MorphButton as everywhere: pill when idle, primary fill +
    // rounded box when selected, standard corner-percent animation. The chip's
    // historic 22dp/12dp corners on its ~40dp height are just under the
    // framework's 50/28 defaults, so it uses the shared defaults verbatim.
    MorphButton(
        onClick = { onClick() },
        onClickHaptic = { haptics?.tick() },
        interactionSource = chipSource,
        active = selected,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        // Same target height as every other button. A chip was 0-min and so ended up shorter
        // than the buttons beside it, which is most of why the seat-heat row read as a
        // different family from the rest of a card.
        minHeight = ButtonTargetHeight,
        // Same gap MorphSegmented had: a selectable pill with no `selected`
        // semantics reaching TalkBack, which announced every chip identically
        // regardless of which one was actually active. Captured into a
        // differently-named local first -- inside semantics{}, `selected` on
        // its own resolves to the SemanticsPropertyReceiver's own property,
        // not this composable's `selected` parameter of the same name.
        modifier = modifier.semantics { this.selected = chipSelected },
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Text(
            label,
            style = ButtonLabelStyle,
            fontWeight = if (chipSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
    }
}


// --- Pebble (expandable, reorderable section) -----------------------------


/**
 * Right-side expand control for pebbles with no action button — the whole
 * right handle is a pill that morphs to a rounded-square when the section is
 * open, giving a clear visual indicator of state.
 */
@Composable
internal fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphChevron",
    )

    // Easter egg: same hold as SplitExpandButton — long-press spins + vibrates
    var easterEggTriggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (easterEggTriggered) 360f else 0f,
        animationSpec = if (easterEggTriggered) spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow) else snap(),
        label = "easterEggMorphSpin",
        finishedListener = { if (easterEggTriggered) easterEggTriggered = false },
    )
    // This button is a FIXED 50dp square, so a 50% corner is a true circle and
    // 10dp is exactly 20%. The default 28 (the app's standard rounded square)
    // is deliberately overridden to keep this control's 10dp corners, which
    // the shared percent model expresses cleanly for a fixed-size button.
    // With expansion animation.
    val chevronSource = remember { MutableInteractionSource() }
    SafeExpansiveButton(
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
            // Expanded highlight = the SAME active state as lock/unlock: primary
            // fill, onPrimary content, straight from MorphButton's defaults.
            active = expanded,
            interactionSource = chevronSource,
            contentPadding = PaddingValues(0.dp),
            pillCornerPercent = 50f,
            morphedCornerPercent = 20f,
            minHeight = 0.dp,
            // Same as SplitExpandButton's chevron: the icon's contentDescription is
            // the next action, this is the current state -- both together instead
            // of only announcing what tapping does. Tap toggles; holding spins the
            // chevron (easter egg) without toggling.
            // ButtonTargetHeight, like everything else tappable -- it was a lone 50dp so it
            // stood 2dp proud of every button beside it for no reason anyone chose.
            modifier = Modifier
                .size(ButtonTargetHeight)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                // Larger chevron icon (24dp to match action button icon size), with
                // easter egg spin animation when the chevron is held.
                // Draw-phase read -- see PebbleShell's identical chevron for why
                // Modifier.rotate() (which takes the angle as an argument, and so reads
                // the spring in composition) is the wrong tool here.
                modifier = Modifier.size(ButtonIconOnlySize).graphicsLayer {
                    rotationZ = rotation + easterEggSpin
                },
            )
        }
    }
}
