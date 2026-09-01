@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import kotlin.math.max



/** One option in a [MorphSegmented] control; re-exported from :uicommon. */
typealias SegmentOption = com.bloo.uicommon.SegmentOption

/**
 * A full-width segmented selector built from the app's button vocabulary: a
 * tonal track whose active segment fills with the primary accent and morphs to a
 * rounded-square, the rest staying pill-calm. Thin wrapper over the shared
 * :uicommon [com.bloo.uicommon.MorphSegmented], supplying the phone's Material 3
 * colours, label typography and haptics.
 */
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    trackHeight: Dp? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    com.bloo.uicommon.MorphSegmented(
        options = options,
        selectedKey = selectedKey,
        onSelect = onSelect,
        containerColor = containerColor ?: buttonContainer(),
        indicatorColor = scheme.primary,
        selectedTextColor = scheme.onPrimary,
        unselectedTextColor = scheme.onSurfaceVariant,
        // The one button label style. A segmented picker is a row of tappable labels, so it sat
        // a size below every button beside it -- which is exactly the "different family on the
        // same card" the standardisation is for.
        textStyle = ButtonLabelStyle,
        onTick = { haptics?.tick() },
        modifier = modifier,
        trackHeight = trackHeight ?: (if (options.any { it.icon != null }) 48.dp else 44.dp),
        // Every other interactive surface (Pebble, floating pills, dialogs)
        // got a hairline rim once real glass blur stopped giving flat
        // surfaces a second depth cue; this control was the one left out.
        borderColor = scheme.outline.copy(alpha = 0.18f),
    )
}


/** A car's powertrain (Gas/Hybrid/PHEV/EV) is a fixed 4-way choice between
 *  equal alternatives — one shared MorphSegmented instead of the MorphChip
 *  row this was duplicated as in both CarSettingsCard and its settings-search
 *  mirror. */
@Composable
internal fun PowertrainPicker(current: com.bloo.bluelink.data.Powertrain, onSelect: (com.bloo.bluelink.data.Powertrain) -> Unit) {
    // An icon per option (Gas/Hybrid/PHEV/EV) instead of text-only segments --
    // a quick visual "shape" for each choice, not just a label to read.
    MorphSegmented(
        options = listOf(
            SegmentOption(com.bloo.bluelink.data.Powertrain.GAS.name, "Gas", Icons.Filled.LocalGasStation),
            SegmentOption(com.bloo.bluelink.data.Powertrain.HYBRID.name, "Hybrid", Icons.Filled.Bolt),
            SegmentOption(com.bloo.bluelink.data.Powertrain.PHEV.name, "PHEV", Icons.Filled.Power),
            SegmentOption(com.bloo.bluelink.data.Powertrain.EV.name, "EV", Icons.Filled.FlashOn),
        ),
        selectedKey = current.name,
        onSelect = { key -> onSelect(com.bloo.bluelink.data.Powertrain.valueOf(key)) },
    )
}

/** A car's confirmed head-unit generation (Gen5W / ccNC) -- the same shape
 *  [PowertrainPicker] is, a fixed choice between equal alternatives on one
 *  [MorphSegmented]. Only ever shown for a vehicle where
 *  [com.bloo.bluelink.data.platformOverridable] is true -- see that
 *  property's own doc for why every other vehicle has nothing here to
 *  confirm. */
@Composable
internal fun PlatformPicker(current: com.bloo.bluelink.data.VehiclePlatform, onSelect: (com.bloo.bluelink.data.VehiclePlatform) -> Unit) {
    MorphSegmented(
        options = listOf(
            SegmentOption(com.bloo.bluelink.data.VehiclePlatform.GEN5W.name, "Gen5W", null),
            SegmentOption(com.bloo.bluelink.data.VehiclePlatform.CCNC.name, "ccNC", null),
        ),
        selectedKey = current.name,
        onSelect = { key -> onSelect(com.bloo.bluelink.data.VehiclePlatform.valueOf(key)) },
    )
}

/**
 * A labelled [MorphSegmented]: a small caption above a full-width segmented
 * control. The expressive replacement for a switch when the setting is really a
 * choice between two equal alternatives (°C/°F, in-app/browser) rather than on/off.
 */
@Composable
fun SettingsSegmentedRow(
    label: String,
    options: List<SegmentOption>,
    selectedKey: String,
    /** See ToggleRow's own `description` doc -- same caption, same rhythm. */
    description: String? = null,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SettingsGapRow))
        MorphSegmented(options = options, selectedKey = selectedKey, onSelect = onSelect)
        if (description != null) SettingsCaption(description)
    }
}

/**
 * A [MorphSegmented] with a fixed-width caption to its left and an explanatory line
 * beneath -- the layout the Quick-tiles card uses for its "On tap" and "Refresh"
 * choices. Distinct from [SettingsSegmentedRow], which stacks its label above the
 * control and carries no sub-caption; this one keeps the label inline (a 60dp column,
 * so the two rows' controls line up) and always has a hint below.
 */
@Composable
internal fun InlineSegmentedRow(
    label: String,
    caption: String,
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
        Spacer(Modifier.width(8.dp))
        MorphSegmented(
            modifier = Modifier.weight(1f),
            options = options,
            selectedKey = selectedKey,
            onSelect = onSelect,
        )
    }
    Spacer(Modifier.height(SettingsGapHairline))
    Text(
        caption,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}








/**
 * Re-architected onto [PebbleShell] -- the exact expandable-card system every garage
 * pebble uses (bounce-open / calm-close springs, the staggered per-row reveal via
 * [StaggeredRevealColumn], the tonal `surfaceVariant` fill, the morphing pill<->square
 * corner radius) -- instead of the bespoke always-expanded `Card` + `animateContentSize`
 * this used to be. Settings was otherwise the one screen in the app whose collapsible
 * surfaces didn't actually collapse and ran on their own separate motion spec (the
 * now-deleted `AdvancedModeStiffness`/[SoftDamping]) rather than the shared bounce
 * tokens ([PebbleBounceDamping]/[PebbleCloseDamping]) every other expandable surface
 * in the app converged on this session.
 *
 * Every card starts EXPANDED (`rememberSaveable` keyed on its own [title], so a
 * rotation or a process restore puts it back where the user left it) -- nothing that
 * was visible before this change is hidden by default. The only real behaviour change
 * is that a card's header is now a genuine toggle: tapping it collapses the card, the
 * same as every pebble in the garage, instead of Settings being the one screen where
 * every section stayed permanently open whether you cared about it or not.
 *
 * [vm] is threaded through purely because [PebbleShell] requires it in its own
 * signature (unused in that function's body today, kept for signature parity with
 * [Pebble]) -- every call site already has it in scope, since every one of them runs
 * inside `SettingsScreen(vm: AppViewModel)`.
 *
 * [icon] stays nullable at the call-site API (unchanged from before) but PebbleShell's
 * own `icon` parameter is not, so a null here falls back to a generic settings glyph --
 * in practice this only ever fires for the single "Car"/"Cars" card, which had no icon
 * of its own to begin with.
 */

@Composable
internal fun SettingsCard(
    title: String,
    icon: ImageVector? = null,
    vm: AppViewModel,
    /**
     * For a card whose entire body is ONE control: render that control on the title row and drop
     * the expand/collapse entirely. A card holding a single switch had nothing worth disclosing
     * -- you tapped a chevron to reveal one toggle, then tapped it again to put it away.
     *
     * Unconditional, not gated on simple mode (unlike Pebble's own
     * `inlineSettingInSimpleMode`, which exists because a car pebble has genuinely more to show
     * in advanced): a card that declares an inline setting has nothing else to show in ANY mode,
     * so a chevron there is never right.
     */
    inlineSetting: (@Composable () -> Unit)? = null,
    /**
     * A short piece of state for the title row -- "2 accounts", "On · auto", "Atkinson".
     *
     * This is the pebble treatment brought across: a car pebble tells you what it knows while
     * collapsed, and a Settings card had no way to, so the only method of finding out what a
     * setting was currently set to was to open the card and read the control. Ignored when the
     * card has an [inlineSetting], because then the control itself is already on that row and
     * says it better than a word could.
     */
    status: String? = null,
    content: @Composable () -> Unit,
) {
    // Open/closed now lives where a car pebble's does -- the same collapse set, persisted to the
    // same store under a reserved pseudo-VIN (see AppViewModel.toggleSettingsCard). This used to
    // be a local `rememberSaveable`, which is why a Settings card forgot whether it was open
    // whenever the process was killed while a car's pebble two screens away remembered.
    val collapsed by vm.collapsedSections.collectAsState()
    val inline = inlineSetting != null
    val expanded = !inline && "$SETTINGS_CARD_VIN:$title" !in collapsed
    // A soft lift while the card is OPEN: the expanded card scales up ~1.5%
    // and settles back -- the same "the thing that changed just came
    // forward" language the pebble cards' own open bounce already speaks,
    // so an expansion reads as the card arriving rather than the neighbours
    // merely moving out of its way. Pure draw-phase (graphicsLayer), so it
    // never re-measures the grid.
    val lift by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "settingsCardLift",
    )
    // heading() on the outer wrapper, not inside PebbleShell's own header Text -- PebbleShell
    // doesn't expose a hook into its title's own Modifier, so this is applied one level up
    // instead. PebbleShell's header row is already ONE merged TalkBack stop (tap-to-toggle),
    // so marking that whole stop as a heading preserves the "headings" navigation shortcut
    // across Settings' ~15 cards that the old Card-based header set up explicitly for.
    Box(
        Modifier
            .fillMaxWidth()
            // The inter-card gap lives HERE, inside this wrapper, and not as the parent
            // Column's `Arrangement.spacedBy`. That is not a style preference, it is the
            // fix for the Advanced->Simple collapse leaving gaps behind: `spacedBy` inserts
            // its spacing between EVERY pair of children regardless of their height, so an
            // advanced-only card shrunk to zero by its own outer AnimatedVisibility still
            // contributed a full gap that `spacedBy` held open on its own schedule and then
            // dropped in one frame once the node left composition -- "extra space between
            // the cards, then it snaps". Living on this wrapper instead means the gap sits
            // INSIDE that same outer AnimatedVisibility and shrinks away with the card.
            .padding(bottom = SettingsCardGap)
            .graphicsLayer {
                val s = 1f + 0.015f * lift
                scaleX = s
                scaleY = s
            }
            .semantics { heading() },
    ) {
        PebbleShell(
            expanded = expanded,
            onToggle = { if (!inline) vm.toggleSettingsCard(title) },
            icon = icon ?: Icons.Filled.Settings,
            title = title,
            vm = vm,
            canToggle = !inline,
            titleTrailing = inlineSetting ?: status?.takeIf { it.isNotBlank() }?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            // Hard right, like every other settings control -- see titleTrailingAtEnd. Without
            // it the switch sat flush against the end of the label text, mid-row, which is what
            // made the single-setting cards read as broken rather than compact.
            titleTrailingAtEnd = true,
            content = { content() },
        )
    }
}

@Composable
internal fun SecretRow(label: String, value: String) {
    var show by remember { mutableStateOf(false) }
    // A clean three-part row: label hugs the left, value hugs the button on
    // the right, no midpoint reservation. The old value used
    // `weight(1f, fill = false)`, which still RESERVED half the width and
    // parked the value mid-row with a void behind it -- the "weirdly
    // indented password" report. SpaceBetween on the row does the exact
    // thing that existed for nothing.
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            if (show) value else "•".repeat(value.length.coerceIn(4, 10)),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp),
        )
        Spacer(Modifier.width(10.dp))
        val toggleSource = remember { MutableInteractionSource() }
        SafeExpansiveButton(
            interactionSource = toggleSource,
            enabled = true,
        ) {
            MorphTextButton(
                if (show) "Hide" else "Show",
                onClick = { show = !show },
                interactionSource = toggleSource,
            )
        }
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    // The same MorphButton every selectable option uses: pill at rest,
    // primaryContainer rounded square once chosen, pressed-state included.
    // With expansion animation.
    val choiceSource = remember { MutableInteractionSource() }
    SafeExpansiveButton(
        interactionSource = choiceSource,
        enabled = true,
    ) {
        MorphButton(
            onClick = { onSelect() },
            active = selected,
            interactionSource = choiceSource,
            containerColor = buttonContainer(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            minHeight = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    }
}


// --- App PIN dialogs ------------------------------------------------------

/**
 * The Security card's PIN dialogs, one [GlassAlertDialog] shell with three
 * stages: verify the CURRENT PIN, then either enter a new one (set/change)
 * or confirm removal. Everything is the standard component set -- the shared
 * pin form, Morph buttons, the glass dialog shell -- so the PIN flow's UI
 * is no more bespoke than any other dialog in the app.
 *
 * The current-PIN stage routes through [AppViewModel.verifyAppPin] so it
 * enjoys the SAME lockout policy the lock screen has (and is not an
 * unguarded oracle for it): wrong PINs here count toward the app's own
 * rejection windows. A rejected attempt lands in [UiState.pinAttemptRejected]
 * (cleared via acknowledgePinRejection once this dialog has shown it); a
 * successful verify bumps [UiState.pinAcceptedTick], which advances the
 * stage.
 */
@Composable
internal fun PinDialogs(
    mode: String?,
    onDismiss: () -> Unit,
    vm: AppViewModel,
    state: UiState,
    canBio: Boolean,
) {
    if (mode == null) return
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val title = when (mode) {
        "set" -> if (state.appPinSet) "Change PIN" else "Set up PIN"
        else -> "Remove PIN"
    }

    // current-PIN gate -> (new PIN entry | remove confirm)
    // A fresh setup (no PIN installed yet) has no "current PIN" to prove --
    // jump straight to choosing the new one. Change and Remove always gate
    // on knowing the existing PIN first.
    var stage by remember(mode) {
        mutableStateOf(if (mode == "set" && !state.appPinSet) "finish" else "current")
    }
    var currentPin by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    // Baselines, so the effects below react to CHANGES only -- the initial
    // composition must not treat a stale flag (left by an earlier lock
    // screen session, say) as a fresh event.
    var seenRejected by remember(mode) { mutableStateOf(state.pinAttemptRejected) }
    var seenTick by remember(mode) { mutableStateOf(state.pinAcceptedTick) }
    // Watch the verify outcome: a wrong PIN flags pinAttemptRejected (shown
    // as an inline error here, then acknowledged), a right one advances.
    LaunchedEffect(state.pinAttemptRejected) {
        if (state.pinAttemptRejected && !seenRejected) {
            seenRejected = true
            rejected = true
            currentPin = ""
            vm.acknowledgePinRejection()
        }
    }
    LaunchedEffect(state.pinAcceptedTick) {
        if (state.pinAcceptedTick != seenTick) {
            seenTick = state.pinAcceptedTick
            if (stage == "current") stage = "finish"
        }
    }
    val sanitize: (String) -> String = { it.take(8).filter { ch -> ch.isDigit() } }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Filled.Lock,
        text = {
            when (stage) {
                "current" -> {
                    Text(
                        "Enter your current PIN to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(SettingsGapGroup))
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = sanitize(it); rejected = false },
                        placeholder = { Text("Current PIN") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (currentPin.length >= 4) {
                                haptics?.click()
                                vm.verifyAppPin(currentPin)
                            }
                        }),
                        isError = rejected,
                        supportingText = if (rejected) {
                            {
                                Text(
                                    "Incorrect PIN. Wrong attempts count toward a lockout.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error,
                                )
                            }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                "finish" -> {
                    when (mode) {
                        "set" -> {
                            Text(
                                "Choose a new 4-8 digit PIN.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(SettingsGapGroup))
                            OnboardingPinForm(
                                existing = state.appPinSet,
                                onSet = { pin -> vm.setAppPin(pin) },
                            )
                        }
                        else -> {
                            Text(
                                if (canBio)
                                    "Removing the PIN leaves fingerprints as the only way to lock the app."
                                else
                                    "This device has no fingerprints, so removing the PIN means the app can never lock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(SettingsGapSection))
                        }
                    }
                }
            }
        },
        buttons = {
            when (stage) {
                "current" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphTextButton("Cancel", onDismiss, modifier = Modifier.weight(1f))
                    MorphButton(
                        onClick = {
                            haptics?.click()
                            vm.verifyAppPin(currentPin)
                        },
                        enabled = currentPin.length >= 4,
                        modifier = Modifier.weight(1f),
                    ) { Text("Continue", fontWeight = FontWeight.SemiBold) }
                }
                "finish" -> {
                    if (mode == "remove") {
                        // equalWidths on the group, NOT Modifier.weight on each child.
                        // RowScope.weight is parent data read by a Row's own measure policy;
                        // inside this group's policy it is not read at all, so it would have
                        // silently done nothing while the two halves went back to hugging
                        // their labels. See ExpressiveButtons.kt.
                        ExpressiveButtonRow(
                            modifier = Modifier.fillMaxWidth(),
                            spacing = 8.dp,
                            equalWidths = true,
                        ) {
                            MorphTextButton("Keep PIN", onDismiss)
                            MorphButton(
                                onClick = { haptics?.heavy(); vm.removeAppPin(); onDismiss() },
                                containerColor = scheme.error,
                                contentColor = scheme.onError,
                            ) { Text("Remove PIN", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Spacer(Modifier.height(SettingsGapRow))
                    MorphTextButton(
                        "Done",
                        onClick = { haptics?.click(); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}
