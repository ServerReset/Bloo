@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
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
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
internal fun LockBlurLayer(locked: Boolean, content: @Composable () -> Unit) {
    val lockBlur by animateDpAsState(
        targetValue = if (locked) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 450),
        label = "lockBlur",
    )
    // The blur modifier is applied only while there IS one. Modifier.blur(0.dp) installs no
    // RenderEffect, but it still forces the whole app tree into its own graphicsLayer on every
    // frame -- for the entire life of the process, to serve a lock screen that is almost never
    // up. This wraps every screen in the app, so it was the most expensive no-op in the tree.
    Box(
        Modifier
            .fillMaxSize()
            .then(if (lockBlur > 0.dp) Modifier.blur(lockBlur) else Modifier),
    ) {
        content()
    }
}

@Composable
internal fun LockAlphaOverlay(locked: Boolean, vm: AppViewModel) {
    val lockAlpha by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "lockAlpha",
    )
    if (lockAlpha > 0.01f) {
        Box(Modifier.fillMaxSize().alpha(lockAlpha)) {
            LockOverlay(vm)
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
 *  - **Fingerprint/biometric** when the device has biometrics enrolled AND
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
internal fun LockOverlay(vm: AppViewModel) {
    val context = LocalContext.current
    val compact = isCompactCoverScreen()
    val appState by vm.state.collectAsState()
    // The device-biometric gate is a binder call -- evaluate once per overlay
    // mount, not per recomposition of the (frequently updating) state below.
    val bioAvailable = remember { vm.canUseBiometrics() }
    val appearance by vm.appearance.collectAsState()
    // Start on the biometric prompt when there's one to show; the user can
    // switch to PIN; devices without biometrics land straight on PIN.
    var usePinMode by remember { mutableStateOf(!bioAvailable) }
    var pin by remember { mutableStateOf("") }
    // A wall-clock ticker that only runs while a rejection window is open --
    // the countdown line needs a fresh "seconds left" each second, and
    // nothing else here wants a 1s recomposition loop.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    // The monotonic reading is ticked alongside the wall clock, so the countdown this screen
    // SHOWS agrees with the one verifyAppPin enforces. Reading only the wall clock here would
    // have the UI cheerfully offer a keypad while the attempt was still being rejected.
    var elapsedTick by remember { mutableStateOf(android.os.SystemClock.elapsedRealtime()) }
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
        while (true) {
            delay(250)
            nowTick = System.currentTimeMillis()
            elapsedTick = android.os.SystemClock.elapsedRealtime()
        }
    }
    // Pattern for "pick the PIN route": tapping "Use PIN" once; a failed
    // biometric prompt stays on the biometric UI; PIN always returns here on
    // the next lock anyway (fresh overlay remounts at the default mode).
    val haptics = LocalHaptics.current
    val noRipple = remember { MutableInteractionSource() }
    val showBiometric = bioAvailable && !usePinMode
    Box(
        Modifier
            .fillMaxSize()
            // Darken the blur for legibility, and swallow taps to the app behind.
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = noRipple, indication = null) {},
    ) {
        // Floating back arrow -> login: the same FloatingIcon every other floating
        // circular button in the app uses, with the lock scrim's plain-white
        // override colours (this is what its old hand-rolled Surface now
        // passes in -- one circle button, one component).
        FloatingIcon(
            icon = Icons.Filled.ArrowBack,
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
                Spacer(Modifier.height(4.dp))
                Text(
                    if (appState.appPinSet) "Confirm it's you, or use your PIN." else "Confirm it's you to reach your vehicles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                // White pill for maximum contrast over the dimmed blur.
                //
                // Plain MorphButton, deliberately NOT wrapped in SafeExpansiveButton -- this
                // screen's one primary CTA was the reported "janky on press" button, and the
                // cause was two independent press animations fighting each other:
                // SafeExpansiveButton REALLY re-measures the button wider on press (a genuine
                // layout size change, meant for buttons sitting in a row that need to shove
                // their neighbours aside), while MorphButtonCore separately, always, applies its
                // OWN press feedback as a graphicsLayer scale on its content. Layered together,
                // one press produced the box growing outward at the same time its own content
                // was scaling inward (or vice versa) -- two different mechanisms disagreeing
                // about what "pressed" looks like on the same button. This button is alone, not
                // in a row with siblings to make room for, so it never needed the width-growth
                // in the first place: MorphButton's own built-in scale is the entire, coherent
                // press feedback here.
                val unlockSource = remember { MutableInteractionSource() }
                MorphButton(
                    onClick = { authenticateBiometric() },
                    modifier = Modifier.height(if (compact) 56.dp else ControlHeight),
                    interactionSource = unlockSource,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
                ) {
                    // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- the icon stays
                    // larger (24dp) than the standard 18dp, an intentional emphasis for the
                    // screen's one primary CTA.
                    MorphButtonLabel(Icons.Filled.Fingerprint, "Unlock", pending = false, iconSize = 24.dp)
                }
                if (appState.appPinSet) {
                    Spacer(Modifier.height(12.dp))
                    val pinSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = pinSource,
                        enabled = true,
                    ) {
                        MorphTextButton(
                            "Use PIN",
                            onClick = { haptics?.click(); usePinMode = true },
                            interactionSource = pinSource,
                            containerColor = Color.White.copy(alpha = 0.10f),
                            contentColor = Color.White,
                        )
                    }
                }
            } else if (appState.appPinSet) {
                // --- PIN prompt (device has no biometrics, or user chose PIN) --
                Surface(
                    shape = RoundedCornerShape(if (compact) 20.dp else 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha(0.97f)),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column {
                                Text(
                                    "Enter your PIN",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (bioAvailable) "Your fingerprint or your PIN unlocks Bloo."
                                    else "This device has no fingerprint sensor, so a PIN is required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } },
                            placeholder = { Text("4–8 digit PIN") },
                            singleLine = true,
                            shape = FieldShape,
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
                                        "Too many attempts — try again in ${formatLockoutSeconds(remainingMs)}",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> Text(
                                        lockout.attemptsRemainingInBatch(nowTick, elapsedTick)?.let { left ->
                                            if (left <= 2) "Careful — $left ${if (left == 1) "attempt" else "attempts"} before a lockout"
                                            else "${PinLockout.STRIKES_PER_BATCH} wrong attempts lock the app for 30 seconds — the wait doubles each time"
                                        } ?: "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        val pinUnlockSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = pinUnlockSource,
                            enabled = !rejected && pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS,
                        ) {
                            MorphButton(
                                onClick = { attemptPin() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                interactionSource = pinUnlockSource,
                                enabled = !rejected && pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS,
                            ) {
                                MorphButtonLabel(Icons.Filled.LockOpen, "Unlock", pending = false)
                            }
                        }
                        if (bioAvailable) {
                            Spacer(Modifier.height(8.dp))
                            val bioSource = remember { MutableInteractionSource() }
                            SafeExpansiveButton(
                                interactionSource = bioSource,
                                enabled = true,
                            ) {
                                MorphTextButton(
                                    "Use fingerprint",
                                    onClick = { haptics?.click(); usePinMode = false; authenticateBiometric() },
                                    interactionSource = bioSource,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
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
                Spacer(Modifier.height(18.dp))
                Text(
                    "Bloo is locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Please try opening Bloo again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}


// --- Empty ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmptyScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    // Note: pull-to-refresh feature temporarily disabled due to Material 3 version compatibility

    // Three distinct causes used to collapse into the same "No vehicles
    // found" / "Not signed in" copy -- including a real network/API
    // failure, which then looked exactly like the app had silently
    // signed the user out. Each now gets its own icon, headline, and
    // primary action so the actual cause is always clear.
    val loadFailed = state.accounts.isNotEmpty() && state.garageLoadError != null
    val (icon, headline, body) = when {
        state.accounts.isEmpty() -> Triple(
            Icons.Filled.CloudOff,
            "Not signed in",
            "Sign in to your Hyundai, Kia, or Genesis account in Settings to get started.",
        )
        loadFailed -> Triple(
            Icons.Filled.WifiOff,
            "Couldn't load your vehicles",
            "${state.garageLoadError}\n\nCheck your connection and try again.",
        )
        else -> Triple(
            Icons.Filled.DirectionsCar,
            "No vehicles found",
            "No enrolled vehicles were found on this account.\n\nMake sure your car is registered in the BlueLink / UVO app, then tap Reload.",
        )
    }

    // Fade + slide up on first composition, matching HeroHeader and every
    // other first-paint card elsewhere in the app -- this screen used to pop
    // in instantly, one more thing that made it read as a leftover plain
    // Material screen rather than part of the same app.
    val contentAlpha = remember { Animatable(0f) }
    val contentOffset = remember { Animatable(16f) }
    LaunchedEffect(Unit) {
        launch { contentAlpha.animateTo(1f, tween(400)) }
        launch { contentOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }

    Box(
        Modifier
            .fillMaxSize(),
    ) {
        // The rest of the app never sits on a flat black/theme-background
        // screen with a stock opaque TopAppBar -- Garage, Settings, and
        // Onboarding all float their header over an animated Aurora backdrop
        // with a blurred status-bar scrim and translucent circular icon
        // buttons. This was the one screen still doing it the plain way.
        AuroraBackground(Modifier.matchParentSize())
        StatusBarScrim()
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Bloo",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                FloatingIcon(Icons.Filled.Refresh, "Reload", { vm.loadGarage() })
                FloatingIcon(Icons.Filled.Settings, "Settings", { vm.openSettings() })
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .graphicsLayer {
                            alpha = contentAlpha.value
                            // .dp.toPx(), not the raw Animatable value:
                            // translationY is in PIXELS, so feeding it 16f slid
                            // this 16px -- about 5dp on a 3x-density phone, and a
                            // different distance on every device. GraphicsLayerScope
                            // is a Density, so the conversion is free right here
                            // (same idiom ReorderColumn's intro slide already uses).
                            translationY = contentOffset.value.dp.toPx()
                        },
                ) {
                    // A soft glow behind the icon instead of a bare, flat glyph
                    // floating on empty space -- the same halo technique the
                    // search bar uses for its own icon treatment.
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(scheme.primary.copy(alpha = 0.16f), Color.Transparent),
                                    ),
                                    CircleShape,
                                ),
                        )
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = if (loadFailed) scheme.error.copy(alpha = 0.85f) else scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.accounts.isEmpty()) {
                        val settingsSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = settingsSource,
                            enabled = true,
                        ) {
                            MorphButton(
                                onClick = { vm.openSettings() },
                                interactionSource = settingsSource,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MorphButtonLabel(Icons.Filled.Settings, "Open Settings", pending = false)
                            }
                        }
                    } else {
                        val reloadSource = remember { MutableInteractionSource() }
                        SafeExpansiveButton(
                            interactionSource = reloadSource,
                            enabled = true,
                        ) {
                            MorphButton(
                                onClick = { vm.loadGarage() },
                                interactionSource = reloadSource,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                MorphButtonLabel(Icons.Filled.Refresh, if (loadFailed) "Try again" else "Reload", pending = false)
                            }
                        }
                    }
                    val accountSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = accountSource,
                        enabled = true,
                    ) {
                        MorphTextButton(
                            "Account Settings",
                            onClick = { vm.openSettings() },
                            interactionSource = accountSource,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
