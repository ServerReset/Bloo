@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.formatLockoutSeconds
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.PinLockout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.max

@Composable
internal fun LockBlurLayer(locked: Boolean, content: @Composable () -> Unit) {
    // Both directions SNAP, neither animates the radius. Animating a full-screen 22dp blur
    // radius means re-rasterizing the whole app tree at a different radius on EVERY frame of
    // the animation -- measured on the API 34 emulator as a 2.3s frame of which ~1s was this
    // blur -- and on real devices the unlock then still chugs for the whole 450ms. Locking
    // snaps in anyway (the lock's own UI covers the content), and on unlock the 450ms fade is
    // carried by LockAlphaOverlay's own alpha, so the blur snapping off behind it is not a
    // visible pop -- the lock UI is what is fading, not the blur.
    val lockBlur = remember { Animatable(0f) }
    LaunchedEffect(locked) {
        lockBlur.snapTo(if (locked) LOCK_BLUR_DP else 0f)
    }
    // The blur modifier is applied only while there IS one. Modifier.blur(0.dp) installs no
    // RenderEffect, but it still forces the whole app tree into its own graphicsLayer on every
    // frame -- for the entire life of the process, to serve a lock screen that is almost never
    // up. This wraps every screen in the app, so it was the most expensive no-op in the tree.
    Box(
        Modifier
            .fillMaxSize()
            .then(if (lockBlur.value > 0f) Modifier.blur(lockBlur.value.dp) else Modifier),
    ) {
        content()
    }
}


/** The locked blur radius (was an inline 22.dp at its one call site). */
private const val LOCK_BLUR_DP = 22f


@Composable
internal fun LockAlphaOverlay(locked: Boolean, vm: AppViewModel, opaqueBackdrop: Boolean = false) {
    val lockAlpha by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "lockAlpha",
    )
    if (lockAlpha > 0.01f) {
        Box(Modifier.fillMaxSize().alpha(lockAlpha)) {
            LockOverlay(vm, opaqueBackdrop)
        }
    }
}


/**
 * The app lock, drawn as an overlay on top of the blurred app. High-contrast
 * white-on-scrim text reads over any wallpaper of cars behind it; a floating
 * back arrow returns to the login screen. Centered + width-capped so it sits
 * well on phones, flip-phone cover screens and tablets alike.
 *
 * Two mechanisms, one overlay:
 *  - **Biometric/biometric** when the device has biometrics enrolled AND
 *    the biometric lock is on (the classic prompt, plus a "Use PIN" link);
 *  - **PIN** when a device PIN is installed -- which is always the case on
 *    the device when it has no biometrics at all (the onboarding flow
 *    requires one there, since otherwise the app could never lock) -- or
 *    when the user picks the PIN route from the biometric prompt.
 *
 * All controls are the app's standard components (MorphButton /
 * MorphTextButton / the FieldShape outline field), so the lock reads as part
 * of the same app, not a leftover scaffold screen.
 */
@Composable
internal fun LockOverlay(vm: AppViewModel, opaqueBackdrop: Boolean = false) {
    val context = LocalContext.current
    val compact = isCompactCoverScreen()
    val appState by vm.state.collectAsStateWithLifecycle()
    // The device-biometric gate is a binder call -- evaluate once per overlay
    // mount, not per recomposition of the (frequently updating) state below.
    val bioAvailable = remember { vm.canUseBiometrics() }
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Start on the biometric prompt when there's one to show; the user can
    // switch to PIN; devices without biometrics land straight on PIN.
    var usePinMode by remember { mutableStateOf(!bioAvailable) }
    var pin by remember { mutableStateOf("") }
    // A wall-clock ticker that only runs while a rejection window is open --
    // the countdown line needs a fresh "seconds left" each second, and
    // nothing else here wants a 1s recomposition loop.
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // The monotonic reading is ticked alongside the wall clock, so the countdown this screen
    // SHOWS agrees with the one verifyAppPin enforces. Reading only the wall clock here would
    // have the UI cheerfully offer a keypad while the attempt was still being rejected.
    var elapsedTick by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    val lockout = appState.pinLockout
    val rejected = lockout.isLocked(nowTick, elapsedTick)
    val remainingMs = lockout.remainingMs(nowTick, elapsedTick)

    fun authenticateBiometric() {
        context.findFragmentActivity()?.let { activity ->
            showBiometricPrompt(
                activity = activity,
                title = "Unlock Bloo",
                subtitle = "Confirm it's you to access your vehicles",
                onSuccess = { vm.unlocked() },
                onError = { },
            )
        }
    }
    fun attemptPin() {
        if (pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS && !rejected) {
            vm.verifyAppPin(pin)
            pin = ""
        }
    }
    LaunchedEffect(Unit) {
        // Fresh overlay (re-lock, cold start) → clear any stale rejection.
        vm.acknowledgePinRejection()
        if (!usePinMode) authenticateBiometric()
    }
    // Tick ONLY while a rejection window is actually counting down.
    //
    // This loop used to be `while (true)` inside the effect above -- two state writes every
    // 250ms for as long as the lock screen was composed, i.e. 4Hz forever, on a countdown that
    // is only ever on screen during a lockout. Its own comment already claimed the conditional
    // behaviour it did not have. Every tick invalidated every reader of `nowTick`/
    // `elapsedTick` (the countdown line and the keypad's own enablement), and the writes are
    // what made the whole lock screen -- and anything sharing its scope -- recompose four
    // times a second for no reason, which is exactly the shape of churn the heap profile
    // showed during a locked cold start.
    LaunchedEffect(rejected) {
        while (rejected) {
            delay(250)
            nowTick = System.currentTimeMillis()
            elapsedTick = android.os.SystemClock.elapsedRealtime()
        }
    }
    // Pattern for "pick the PIN route": tapping "Use PIN" once; a failed
    // biometric prompt stays on the biometric UI; PIN always returns here on
    // the next lock anyway (fresh overlay remounts at the default mode).
    val haptics = LocalHaptics.current
    val showBiometric = bioAvailable && !usePinMode
    // The backdrop animates between fully opaque (the first frames of a cold start, before
    // the content underneath has settled -- see LockBlurLayer's own note) and the 45% scrim.
    // It used to snap between the two the instant `opaqueBackdrop` flipped, which read as the
    // whole lock screen "going transparent" in one frame; a short cross-fade makes the
    // hand-off to the blurred garage imperceptible.
    val backdropAlpha by animateFloatAsState(
        targetValue = if (opaqueBackdrop) 1f else 0.45f,
        animationSpec = tween(320),
        label = "lockBackdropAlpha",
    )
    Box(
        Modifier
            .fillMaxSize()
            // Darken the blur for legibility, and swallow taps to the app behind. While the
            // backdrop is NOT blurred (the first frames of a cold start, before the app
            // underneath has finished composing) this is fully opaque instead: a 45% scrim
            // over a sharp garage would show car names, plates and status through the lock
            // screen, which is the one thing it exists to prevent.
            .background(Color.Black.copy(alpha = backdropAlpha))
            .noRippleClickable {},
    ) {
        // Floating back arrow -> login: the same FloatingIcon every other floating
        // circular button in the app uses, with the lock scrim's plain-white
        // override colours (this is what its old hand-rolled Surface now
        // passes in -- one circle button, one component).
        FloatingIcon(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Back to login",
            onClick = { haptics?.click(); vm.lockToLogin() },
            containerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding(),
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showBiometric) {
                // --- Classic biometric prompt ---------------------------------
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 44.dp else 72.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                Text(
                    "Bloo is locked",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(GapHairline))
                Text(
                    if (appState.appPinSet) "Confirm it's you, or use your PIN." else "Confirm it's you to reach your vehicles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                // White pill for maximum contrast over the dimmed blur.
                val unlockSource = remember { MutableInteractionSource() }
                MorphButton(
                    onClick = { authenticateBiometric() },
                    modifier = Modifier.height(if (compact) 56.dp else ControlHeight),
                    interactionSource = unlockSource,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
                    expressive = true,
                    fillOnPress = true,
                    groupWeight = GroupWeightProportional,
                ) {
                    // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- the icon stays
                    // larger (24dp) than the standard 18dp, an intentional emphasis for the
                    // screen's one primary CTA.
                    MorphButtonLabel(Icons.Filled.Fingerprint, "Unlock", pending = false, iconSize = 24.dp)
                }
                if (appState.appPinSet) {
                    Spacer(Modifier.height(GapGroup))
                    SafeMorphTextButton(
                        "Use PIN",
                        onClick = { haptics?.click(); usePinMode = true },
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White,
                    )
                }
            } else if (appState.appPinSet) {
                // --- PIN prompt (device has no biometrics, or user chose PIN) --
                // GlassSurface (GlassChrome.kt): the same fill/rim/shadow every other
                // floating surface in the app shares, replacing this card's own
                // one-off near-opaque fill and its own separately-hand-rolled flat
                // BorderStroke rim (a THIRD, divergent rim treatment next to
                // appGlassRim's shared gradient one and Ambient.kt's now-removed
                // dialog override) -- no more per-site exceptions.
                GlassSurface(
                    shape = RoundedCornerShape(if (compact) 20.dp else 28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconBadge(
                                icon = AppIcons.Lock,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                size = 46.dp,
                                iconSize = 24.dp,
                            )
                            Column {
                                Text(
                                    "Enter your PIN",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (bioAvailable) "Your biometrics or your PIN unlock Bloo."
                                    else "This device has no biometrics, so a PIN is required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(GapSection))
                        BlooTextField(
                            value = pin,
                            onValueChange = { pin = it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } },
                            placeholder = { Text("4–8 digit PIN") },
                            singleLine = true,
                            colors = borderlessFieldColors(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { attemptPin() }),
                            supportingText = {
                                when {
                                    rejected -> Text(
                                        "Too many attempts. Try again in ${formatLockoutSeconds(remainingMs)}",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> Text(
                                        lockout.attemptsRemainingInBatch(nowTick, elapsedTick)?.let { left ->
                                            if (left <= 2) "Careful. $left ${if (left == 1) "attempt" else "attempts"} before a lockout"
                                            else "${PinLockout.STRIKES_PER_BATCH} wrong attempts lock the app for 30 seconds. The wait doubles each time."
                                        } ?: "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(GapGroup))
                        val pinUnlockSource = remember { MutableInteractionSource() }
                        MorphButton(
                            onClick = { attemptPin() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            interactionSource = pinUnlockSource,
                            enabled = !rejected && pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS,
                            expressive = true,
                            fillOnPress = true,
                            groupWeight = GroupWeightProportional,
                        ) {
                            MorphButtonLabel(Icons.Filled.LockOpen, "Unlock", pending = false)
                        }
                        if (bioAvailable) {
                            Spacer(Modifier.height(GapRow))
                            SafeMorphTextButton(
                                "Use biometrics",
                                onClick = { haptics?.click(); usePinMode = false; authenticateBiometric() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                // No mechanism at all -- should not be reachable (the lock
                // gate refuses to engage without one); a calm fallback so the
                // overlay never dead-ends silently.
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 44.dp else 72.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.height(GapSection))
                Text(
                    "Bloo is locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(GapHairline))
                Text(
                    "Please try opening Bloo again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
