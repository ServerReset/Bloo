package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.flow.first

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
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "morphIconPress",
    )
    var everInert by remember { mutableStateOf(!enabled) }
    SideEffect { if (!enabled) everInert = true }
    val frost = if (everInert || !enabled) {
        Modifier.frosted(!enabled, CircleShape, blurRadius = 1.2.dp, rim = false, veil = false)
    } else {
        Modifier
    }
    val body: @Composable () -> Unit = {
        IconButton(
            onClick = { haptics?.click(); onClick() },
            // On the button, not the icon: scaling the icon alone shrinks the glyph
            // inside a target that stays put, which reads as a glitch rather than a
            // press.
            modifier = modifier.graphicsLayer {
                // The family's squash, tuned down three times now (0.5/0.9 -> 0.3/0.5 ->
                // 0.25/0.5 -> here) and the dip softened to 0.97: at this point it is a
                // hint of give under the finger, not a visible squash.
                val dip = 1f - scale
                scaleX = 1f + dip * 0.15f
                scaleY = 1f - dip * 0.3f
            }
                .then(frost),
            enabled = enabled,
            interactionSource = interactionSource,
            content = content,
        )
    }
    // Joins a group when it is in one -- see MorphButton's own note. An icon button is just as
    // likely to sit in a row of peers (the snackbar's copy/dismiss pair) as a labelled one.
    if (LocalExpressiveGroup.current) {
        SafeExpansiveButton(
            interactionSource = interactionSource,
            enabled = enabled,
        ) { body() }
    } else {
        body()
    }
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = GapRow),
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
        // The shared label, like every other button, rather than a hand-assembled icon and
        // text. Two things follow from that: the glyph gets the standard gap beside it (these
        // two were emitted with NO spacer between them at all), and a row of chips can compact
        // to glyphs when it runs out of room, which a hand-assembled pair cannot.
        //
        // A selected chip is SemiBold like every other button label now, not Bold. Its
        // selection already reads from the filled container and the rounded-square morph; a
        // third signal was the sort of per-control exception this pass exists to remove.
        if (icon != null) {
            MorphButtonLabel(icon, label, pending = false)
        } else {
            Text(
                label,
                style = ButtonLabelStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    }
}

/**
 * Right-side expand control for pebbles with no action button — the whole
 * right handle is a pill that morphs to a rounded-square when the section is
 * open, giving a clear visual indicator of state.
 */
@Composable
internal fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
    /** Reports this button's own pressed (held-down) state to the caller, live --
     *  so the pebble card this chevron belongs to can square its own outer shape
     *  off together with the chevron's, instead of only the small chevron itself
     *  reacting to the hold. Null (the default) for every caller that doesn't
     *  care. */
    onPressChange: ((Boolean) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphChevron",
    )

    // Easter egg: same hold as SplitExpandButton — long-press spins + vibrates
    var easterEggTriggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (easterEggTriggered) 360f else 0f,
        animationSpec = if (easterEggTriggered) lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow) else snap(),
        label = "easterEggMorphSpin",
        finishedListener = { if (easterEggTriggered) easterEggTriggered = false },
    )
    // This button is a FIXED 50dp square, so a 50% corner is a true circle and
    // 10dp is exactly 20%. The default 28 (the app's standard rounded square)
    // is deliberately overridden to keep this control's 10dp corners, which
    // the shared percent model expresses cleanly for a fixed-size button.
    // With expansion animation.
    val chevronSource = remember { MutableInteractionSource() }
    if (onPressChange != null) {
        val pressed by chevronSource.collectIsPressedAsState()
        LaunchedEffect(pressed) { onPressChange(pressed) }
        // Whatever was true when this leaves the composition (a collapse that
        // unmounts this button mid-press, say) shouldn't leave the pebble
        // permanently squared -- the effect above only reacts to CHANGES, not
        // to being torn down.
        DisposableEffect(Unit) { onDispose { onPressChange(false) } }
    }
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
