package com.bloo.wear.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearViewModel

private const val PIN_LENGTH = 4

/** Small dots showing how many of [PIN_LENGTH] digits are entered, filling
 *  with a size+colour spring (same "fill" feel as ChargeRing's progress
 *  animation) instead of teleporting, and briefly tinting error on a wrong PIN. */
@Composable
private fun PinDots(filled: Int, showError: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(PIN_LENGTH) { i ->
            val isFilled = i < filled
            val color by animateColorAsState(
                targetValue = when {
                    showError -> MaterialTheme.colorScheme.error
                    isFilled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = tween(150),
                label = "pinDotColor",
            )
            val size by animateDpAsState(
                targetValue = if (isFilled) 11.dp else 9.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pinDotSize",
            )
            Box(Modifier.size(size).clip(CircleShape).background(color))
        }
    }
}

/** A single round keypad button. Tracks its own pressed state via a
 *  [MutableInteractionSource] and drives three independent animated values off
 *  it (scale, background colour, border colour) so a tap reads as a soft
 *  "press in, fill with the primary colour, border disappears" rather than a
 *  flat Material ripple -- consistent with the app's other custom-animated
 *  controls (MorphButton et al.) rather than a stock clickable. */
@Composable
private fun PinKey(label: String, keySize: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pinKeyScale",
    )
    val bg by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(120),
        label = "pinKeyBg",
    )
    val content = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val border by animateColorAsState(
        targetValue = if (pressed) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        animationSpec = tween(120),
        label = "pinKeyBorder",
    )
    Box(
        Modifier
            .size(keySize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            // MorphButton (the app's other primary control) is bordered at
            // rest; the PIN pad -- arguably the most-tapped control in the
            // app -- was the one flat-surface-with-no-rim outlier.
            .border(BorderStroke(1.dp, border), CircleShape)
            // The backspace key's visible/spoken content was the raw "⌫"
            // glyph -- unlike the digit keys, TTS engines don't reliably
            // pronounce it, so this was the one key most likely to be
            // silent or wrong for a TalkBack user.
            .then(if (label == "⌫") Modifier.semantics { contentDescription = "Backspace" } else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = content)
    }
}

/** A standard 3x4 numeric keypad layout (1-9, then a blank/0/backspace row).
 *  The blank bottom-left cell is rendered as an equally-sized invisible
 *  [Spacer] rather than simply omitted, so the "0" and "⌫" keys below/above it
 *  stay aligned in the same grid column as the digits above -- an empty
 *  string in the `rows` data is the marker for "render a spacer here, not a
 *  key". Tapping "⌫" routes to [onBackspace]; every other key routes to
 *  [onDigit] with that key's own label as the digit typed. */
@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    // Size the keys to the screen so the whole 4-row pad + the title/dots stack
    // above it always fits inside a round face without the outer rows being
    // pushed under the bezel. 48dp (Wear's recommended touch target) is kept on
    // normal/large round watches (>=225dp tall); the smallest ~192dp faces
    // shrink toward 40dp, which is the only way the pad fits there at all.
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val gap = 4.dp
    val keySize = when {
        screenH >= 225 -> 48.dp
        screenH >= 205 -> 44.dp
        else -> 40.dp
    }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(keySize))
                    } else {
                        PinKey(key, keySize, onClick = { if (key == "⌫") onBackspace() else onDigit(key) })
                    }
                }
            }
        }
    }
}

/**
 * A self-contained 4-digit PIN entry pad: collects exactly [PIN_LENGTH]
 * digits, then calls [onSubmit] and clears itself. [error], when non-null, is
 * shown above the dots and triggers a reject haptic + a brief error tint/shake
 * on the dots + clears any partial entry (used for "wrong PIN" after a failed
 * [onSubmit]).
 *
 * [errorNonce] must be incremented by the caller on every failed attempt, and
 * is what actually drives that reject/clear. Keying it off [error] alone was a
 * real lockout: `submitPin` reports the identical "Wrong PIN" string for every
 * attempt before the lockout threshold, Compose's MutableState uses structural
 * equality, so re-assigning an equal value is not a state change -- the effect
 * never restarted, [buffer] stayed at PIN_LENGTH, and `onDigit`'s
 * `buffer.length < PIN_LENGTH` guard then swallowed every subsequent tap. The
 * first wrong PIN cleared and shook; the second through fourth left the pad
 * frozen with four filled dots until the user manually backspaced four times.
 */
@Composable
fun PinEntryScreen(
    title: String,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    error: String? = null,
    errorNonce: Int = 0,
    onCancel: (() -> Unit)? = null,
) {
    var buffer by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    var showErrorTint by remember { mutableStateOf(false) }
    val shakeX = remember { Animatable(0f) }
    // Keyed on the attempt COUNTER, not just the message -- see the KDoc: two
    // consecutive wrong PINs produce the same string, so an `error`-only key
    // never re-ran and the pad stayed stuck with a full buffer. `error` is kept
    // in the key list as well so a caller that sets a message without bumping
    // the counter still gets the reject (the body no-ops when it's null).
    LaunchedEffect(errorNonce, error) {
        if (error != null) {
            haptics.reject()
            buffer = ""
            showErrorTint = true
            // A quick left-right-left shake reads as "rejected" the same way
            // MessageSnackbar's errorContainer tint reads as "bad state"
            // elsewhere in the app -- the red text alone was easy to miss.
            listOf(-8f, 8f, -5f, 5f, 0f).forEach { x ->
                shakeX.animateTo(x, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh))
            }
            showErrorTint = false
        }
    }
    val entranceAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entranceAlpha.animateTo(1f, tween(220)) }
    Column(
        modifier
            .fillMaxSize()
            // Safety net for the smallest round faces: with an error line AND a
            // Cancel row present, the title + dots + 4-row pad stack can exceed a
            // ~192dp face, and Arrangement.Center then splits the overflow so the
            // title (top) and Cancel (bottom) clip off-screen. verticalScroll lets
            // that rare tall case scroll instead of clip; when the stack fits (the
            // common case) it stays centered and scroll never engages. Touch-target
            // key sizes are intentionally kept full (48/44/40dp) — shrinking a PIN
            // pad to fit is worse than letting it scroll.
            .verticalScroll(rememberScrollState())
            .graphicsLayer { alpha = entranceAlpha.value }
            // A small fixed horizontal inset, NOT roundSafeHorizontalPadding:
            // that helper widens the inset to 22dp on round for scrolling LIST
            // content (whose corners are cut along the vertical span), but the
            // PIN pad is vertically centered at the circle's WIDEST point where
            // no corner cut applies -- a 22dp inset there only steals width and
            // pushes the outer 1/4/7 & 3/9/⌫ columns toward the bezel. 8dp keeps
            // the pad centered with room to spare on every round size.
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (error != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        // offset { } (layout phase), not offset(x = ...dp) (composition phase). The dp overload
        // reads shakeX in COMPOSITION, and this read sits in PinLockScreen's own body -- so every
        // frame of the reject shake recomposed the entire screen, twelve keypad buttons included,
        // to move one row of dots a few pixels. entranceAlpha thirty lines up already avoids
        // exactly this with a graphicsLayer lambda; this was the one that got missed, on the
        // slowest CPU in the project, during the one animation the user is watching closely.
        Box(Modifier.offset { IntOffset(shakeX.value.dp.roundToPx(), 0) }) {
            PinDots(buffer.length, showErrorTint)
        }
        Spacer(Modifier.height(4.dp))
        PinKeypad(
            onDigit = { d ->
                if (buffer.length < PIN_LENGTH) {
                    haptics.tick()
                    val next = buffer + d
                    buffer = next
                    if (next.length == PIN_LENGTH) {
                        // Used to clear immediately, so a verification that
                        // takes a moment (a cold DataStore read on first
                        // unlock after reboot) briefly showed 4 empty dots
                        // with zero PIN-related motion before either the
                        // unlock happened or the error-shake fired -- a dead
                        // beat with no "checking…" affordance at all. Leaving
                        // the dots filled reads as "still verifying" instead;
                        // a wrong PIN already clears + shakes via the `error`
                        // effect above, and a correct one unmounts this
                        // screen entirely, so there's no stale-filled state
                        // to worry about either way.
                        onSubmit(next)
                    }
                }
            },
            onBackspace = { if (buffer.isNotEmpty()) { haptics.tick(); buffer = buffer.dropLast(1) } },
        )
        if (onCancel != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClickLabel = "Cancel", role = Role.Button, onClick = onCancel),
            )
        }
    }
}

/**
 * The full-screen gate shown while [WearViewModel]'s pinLocked state is true.
 * Mechanism: this composable itself holds no PIN logic at all -- it's purely
 * a thin UI wrapper around [PinEntryScreen] that forwards whatever 4 digits
 * were typed to [WearViewModel.submitPin], which does the actual verification
 * (delegating to [com.bloo.wear.WearLocalStore.verifyPin]'s salted-hash
 * comparison) and flips `pinLocked` back to false on success. `error` here is
 * purely local transient UI state -- set to "Wrong PIN" on a failed attempt
 * (which also triggers [PinEntryScreen]'s shake/haptic-reject via its own
 * `error` param) and cleared on success. [WearViewModel.submitPin] itself now
 * enforces a consecutive-failure lockout (see its own doc comment); when
 * active, its `lockoutMessage` callback param is shown here in place of the
 * generic "Wrong PIN" text instead.
 */
@Composable
fun PinLockScreen(vm: WearViewModel) {
    var error by remember { mutableStateOf<String?>(null) }
    // Bumped on every failed attempt so PinEntryScreen's reject/clear fires even
    // when the message is byte-identical to the last one (it is, for attempts 1-4
    // -- submitPin only supplies a distinct lockout string on the 5th).
    var errorNonce by remember { mutableStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PinEntryScreen(
            title = "Enter PIN",
            error = error,
            errorNonce = errorNonce,
            onSubmit = { pin ->
                vm.submitPin(pin) { ok, lockoutMessage ->
                    if (ok) {
                        error = null
                    } else {
                        error = lockoutMessage ?: "Wrong PIN"
                        errorNonce++
                    }
                }
            },
        )
    }
}

/** Which PIN-management flow [PinManagementOverlay] is running. */
enum class PinFlowMode { SET, CHANGE, REMOVE, DISABLE }

private enum class PinFlowStep { CONFIRM_CURRENT, ENTER_NEW, CONFIRM_NEW, REMOVING, DISABLING }

/**
 * A full-screen overlay driving the settings screen's "Set/Change/Remove PIN"
 * flows: SET goes straight to entering a new PIN (twice, to confirm); CHANGE
 * and REMOVE both require the current PIN first. Calls [onDone] when finished
 * or cancelled -- the caller (SettingsScreen) is responsible for clearing
 * whatever state triggered showing this.
 */
@Composable
fun PinManagementOverlay(vm: WearViewModel, mode: PinFlowMode, onDone: () -> Unit) {
    var step by remember { mutableStateOf(if (mode == PinFlowMode.SET) PinFlowStep.ENTER_NEW else PinFlowStep.CONFIRM_CURRENT) }
    var firstEntry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    // Same reason as PinLockScreen's: CONFIRM_CURRENT sets the identical
    // "Wrong PIN" on every failed attempt, so the reject/clear needs a changing
    // key. ENTER_NEW/CONFIRM_NEW don't need it -- their errors always arrive
    // together with a `step` change, and key(step) rebuilds the pad anyway.
    var errorNonce by remember { mutableStateOf(0) }

    // Wrapped in a ScreenScaffold with timeText = {} so the inherited AppScaffold
    // clock is suppressed here — this overlay is drawn on top of Settings (inside
    // AppScaffold), and without this the curved clock painted over the centered
    // "Confirm PIN" / "Enter current PIN" title. The non-scrolling base overload
    // (scrollInfoProvider defaults to null → no scroll indicator) is selected by
    // passing only timeText; its content lambda hands back a PaddingValues we don't
    // need since the PIN pad is vertically centered.
    ScreenScaffold(timeText = {}) { _ ->
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Keyed on `step` so each step gets its own PinEntryScreen instance --
        // its internal entrance-fade LaunchedEffect(Unit) then replays on every
        // step change instead of only once for the whole overlay, giving the
        // CONFIRM_CURRENT -> ENTER_NEW -> CONFIRM_NEW sequence a soft crossfade
        // between steps instead of a hard pop.
        androidx.compose.runtime.key(step) {
            when (step) {
                PinFlowStep.CONFIRM_CURRENT -> PinEntryScreen(
                    title = "Enter current PIN",
                    error = error,
                    errorNonce = errorNonce,
                    onCancel = onDone,
                    onSubmit = { pin ->
                        vm.verifyPinForManagement(pin) { ok ->
                            if (ok) {
                                error = null
                                step = when (mode) {
                                    PinFlowMode.REMOVE -> PinFlowStep.REMOVING
                                    PinFlowMode.DISABLE -> PinFlowStep.DISABLING
                                    else -> PinFlowStep.ENTER_NEW
                                }
                            } else {
                                error = "Wrong PIN"
                                errorNonce++
                            }
                        }
                    },
                )
                PinFlowStep.ENTER_NEW -> PinEntryScreen(
                    title = "Set a new PIN",
                    error = error,
                    onCancel = onDone,
                    onSubmit = { pin -> error = null; firstEntry = pin; step = PinFlowStep.CONFIRM_NEW },
                )
                PinFlowStep.CONFIRM_NEW -> PinEntryScreen(
                    // No `error` param here on purpose (unlike the other two
                    // steps): a mismatch sets `error` and switches `step` to
                    // ENTER_NEW in the same event, so Compose recomposes
                    // straight to ENTER_NEW's screen -- this step's own
                    // PinEntryScreen instance never gets a frame to show the
                    // error on. The "Didn't match" message intentionally
                    // surfaces on the screen the user bounces back to instead.
                    title = "Confirm PIN",
                    onCancel = onDone,
                    onSubmit = { pin ->
                        if (pin == firstEntry) {
                            vm.setPin(pin) { onDone() }
                        } else {
                            error = "Didn't match, try again"
                            step = PinFlowStep.ENTER_NEW
                        }
                    },
                )
                // clearPin()/setPinLockEnabled() are asynchronous DataStore
                // writes -- calling onDone() right after firing them (rather
                // than from their own completion callback) used to close this
                // overlay and return to SettingsScreen before the write had
                // actually landed, so the screen's toggle could very briefly
                // flash its old "Lock: On" state until the settings flow
                // caught up a moment later.
                PinFlowStep.REMOVING -> LaunchedEffect(Unit) { vm.clearPin(onDone) }
                // Turning "Lock: On" off is functionally identical to removing
                // the PIN entirely (the watch will never lock again) but used
                // to require no PIN at all -- anyone who picked up the watch
                // during the exact window the lock is meant to protect could
                // permanently disable it in two taps. Requiring the same
                // CONFIRM_CURRENT step as REMOVE closes that gap without
                // clearing the stored PIN, so re-enabling doesn't need a reset.
                PinFlowStep.DISABLING -> LaunchedEffect(Unit) { vm.setPinLockEnabled(false, onDone) }
            }
        }
    }
    }
}
