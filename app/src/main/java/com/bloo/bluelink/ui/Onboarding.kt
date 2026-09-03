@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.max

internal enum class WizardStepKind { POWERTRAIN, PLATFORM, SEATS, STEERING }

internal data class WizardPage(
    val kind: WizardStepKind,
    val vin: String? = null,
)

/**
 * Flattens the per-vehicle setup wizard into one linear list of pages: for
 * each vehicle, a POWERTRAIN page, a PLATFORM page (only for a vehicle where
 * [com.bloo.bluelink.data.platformOverridable] is true -- see that
 * property's own doc; there's nothing to confirm for the rest), then SEATS,
 * then STEERING, in that order. The resulting list drives a single
 * [HorizontalPager] in [CarSetupWizardScreen], so a multi-car setup becomes
 * one continuous swipe sequence instead of nested per-car flows.
 */
internal fun buildSetupPages(vehicles: List<com.bloo.bluelink.data.Vehicle>): List<WizardPage> = buildList {
    vehicles.forEach { v ->
        add(WizardPage(WizardStepKind.POWERTRAIN, v.vin))
        if (v.platformOverridable) add(WizardPage(WizardStepKind.PLATFORM, v.vin))
        add(WizardPage(WizardStepKind.SEATS, v.vin))
        add(WizardPage(WizardStepKind.STEERING, v.vin))
    }
}

internal enum class OnboardingStepKind { INTRO, SETUP, CAR, CRASH_COURSE }

internal data class OnboardingStep(val kind: OnboardingStepKind, val vin: String? = null)

/**
 * Flattens first-run onboarding into one linear list of steps: a welcome
 * intro, a combined notifications+biometrics+sync setup step, one CAR step
 * per vehicle that isn't already configured (each vehicle gets its own
 * dedicated screen rather than being stacked in one scroll or split into
 * per-feature pages), and a closing crash-course. Drives the single
 * [AnimatedContent] in [OnboardingScreen] the same way [buildSetupPages]
 * drives [CarFeatureWizard].
 *
 * [preConfiguredVins] skips a car's whole CAR step -- restoring a Drive/
 * manual backup on the SETUP step (which always comes before any CAR step)
 * can bring in real powertrain/seat config for a car that already had it set
 * up on another device, and there's no reason to ask again for something the
 * backup already answered. Empty by default: normal first-run onboarding
 * with nothing to restore still gets one CAR step per vehicle as before.
 */
internal fun buildOnboardingSteps(
    vehicles: List<com.bloo.bluelink.data.Vehicle>,
    preConfiguredVins: Set<String> = emptySet(),
): List<OnboardingStep> = buildList {
    add(OnboardingStep(OnboardingStepKind.INTRO))
    add(OnboardingStep(OnboardingStepKind.SETUP))
    vehicles.forEach { if (it.vin !in preConfiguredVins) add(OnboardingStep(OnboardingStepKind.CAR, it.vin)) }
    add(OnboardingStep(OnboardingStepKind.CRASH_COURSE))
}

/**
 * First-run onboarding: a button-driven multi-screen wizard -- intro, then
 * notifications/biometrics, then one screen per car, then a crash course --
 * capped off by [AppViewModel.finishOnboarding]. Shares its shell shape
 * (animated top progress bar, [AnimatedContent] slide/fade transitions,
 * Back/Next footer) with [CarFeatureWizard] but keeps its own copy since
 * this flow's steps are heterogeneous (intro/setup/crash-course pages
 * alongside per-car pages) rather than the uniform per-feature pages
 * [CarFeatureWizard] flips through. The system back gesture steps back one
 * page instead of exiting outright, and only bottoms out (does nothing) on
 * the very first page, so the user can never back out of onboarding
 * entirely before finishing setup.
 */
@Composable
internal fun OnboardingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val state by vm.state.collectAsState()
    val canBio = remember { vm.canUseBiometrics() }
    val scheme = MaterialTheme.colorScheme

    // Snapshot of vehicles a restored backup already configured, frozen once
    // the user moves past the SETUP step (always index 1 -- INTRO then SETUP
    // always come first, see buildOnboardingSteps) so a live edit on a CAR
    // page later (which also updates state.powertrains) can't retroactively
    // shrink the step list out from under the page the user is looking at.
    var preConfiguredVins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pageIndex by remember { mutableIntStateOf(0) }
    // The freeze has to LATCH. Keying the update on `pageIndex <= 1` alone read as
    // "only while still on INTRO/SETUP", but that condition becomes true again
    // every time the user navigates BACK to those pages -- and BackHandler makes
    // going back the normal way to move around this wizard, not an edge case. So
    // the snapshot re-took itself from a state.powertrains that now included cars
    // the user had configured on a CAR page in between, and those cars' steps
    // vanished from the list: with three unconfigured cars, configuring the first
    // and then backing up to SETUP dropped its page, so walking forward again went
    // straight to the second car with no way to reach the first. pageIndex isn't
    // remapped when the list shrinks either, so the skip was silent.
    //
    // Exactly the retroactive shrink the comment above says this is here to
    // prevent -- the freeze was just never closed.
    var pastSetup by remember { mutableStateOf(false) }
    LaunchedEffect(state.powertrains.keys, pageIndex) {
        if (pageIndex > 1) pastSetup = true
        if (!pastSetup) preConfiguredVins = state.powertrains.keys.toSet()
    }
    val steps = remember(state.vehicles, preConfiguredVins) { buildOnboardingSteps(state.vehicles, preConfiguredVins) }
    LaunchedEffect(steps) { if (pageIndex > steps.lastIndex) pageIndex = steps.lastIndex }

    val lastIndex = steps.lastIndex
    val isLast = pageIndex == lastIndex

    // Devices without biometrics MUST finish the PIN step before leaving it
    // -- without a PIN there is no lock mechanism for this device at all.
    // The CTA below is disabled (with a hint) until the PIN lands.
    val pinRequired = !canBio && steps.getOrNull(pageIndex)?.kind == OnboardingStepKind.SETUP && !state.appPinSet

    fun goNext() {
        if (pageIndex < lastIndex) {
            haptics?.click()
            pageIndex++
        } else {
            vm.finishOnboarding()
        }
    }
    fun goBack() {
        if (pageIndex > 0) {
            haptics?.click()
            pageIndex--
        }
    }
    BackHandler { goBack() }

    LaunchedEffect(isLast) {
        if (isLast) {
            Fireworks.playSound(context)
            haptics?.fireworks()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        if (isLast) FireworksOverlay(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(8.dp))

            // --- Progress: an animated bar plus a small step counter ---
            val progress = if (steps.size > 1) pageIndex.toFloat() / lastIndex.toFloat() else 1f
            val animatedProgress by animateFloatAsState(progress, tween(WizardProgressDurationMs), label = "onboardProgress")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedProgress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary))),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${pageIndex + 1}/${steps.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            // --- Slide/fade animated step content ---
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir } + fadeIn(tween(WizardStepFadeInDurationMs))) togetherWith
                        (slideOutHorizontally { -it * dir } + fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "onboardStep",
            ) { idx ->
                val step = steps.getOrNull(idx) ?: return@AnimatedContent
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (step.kind) {
                            OnboardingStepKind.INTRO -> OnboardingIntroPage()
                            OnboardingStepKind.SETUP -> OnboardingSetupPage(vm, state, context, canBio)
                            OnboardingStepKind.CAR -> {
                                val vehicle = step.vin?.let { vin -> state.vehicles.firstOrNull { it.vin == vin } }
                                val sc = vehicle?.let { state.seatConfigs[it.vin] } ?: com.bloo.bluelink.data.SeatConfig()
                                OnboardingCarPage(vehicle, state, sc, vm)
                            }
                            OnboardingStepKind.CRASH_COURSE -> OnboardingCrashCoursePage()
                        }
                    }
                }
            }

            // --- Back / Next footer ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = pageIndex > 0,
                    modifier = Modifier.weight(1f),
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
                ) {
                    // MorphButton, not a plain OutlinedCard -- this was the one
                    // button in the entire app still built on stock Material
                    // chrome instead of the shared pill<->rounded-square press
                    // feel (haptic click, corner morph, press-scale) every other
                    // button gets, onboarding included right next to it.
                    // active=false gives it MorphButton's own secondary/outline
                    // treatment, matching how every other Back/secondary action
                    // in the app already reaches for the same component rather
                    // than a bespoke look-alike for "the quieter one."
                    // With expansion animation.
                    val backSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = backSource,
                        enabled = true,
                    ) {
                        MorphButton(
                            onClick = ::goBack,
                            interactionSource = backSource,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                        ) {
                            Text("Back", style = ButtonLabelStyle)
                        }
                    }
                }
                val nextSource = remember { MutableInteractionSource() }
                // The weight goes on the SafeExpansiveButton, which is the Row's actual child,
                // NOT on the MorphButton inside it -- whose parent is that wrapper's own layout
                // and never reads it. The same dead-weight mistake the cover action bar had:
                // this button was silently hugging its label instead of taking the 2:1 share
                // over Back that the expression asks for.
                SafeExpansiveButton(
                    interactionSource = nextSource,
                    enabled = !pinRequired,
                    modifier = Modifier.weight(if (pageIndex > 0) 2f else 1f),
                ) {
                    MorphButton(
                        onClick = ::goNext,
                        active = true,
                        enabled = !pinRequired,
                        interactionSource = nextSource,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- that Text used
                        // FontWeight.Bold, where every other button label in the app (including
                        // this one's own "Back" neighbour) uses SemiBold.
                        MorphButtonLabel(
                            if (isLast) Icons.Filled.CheckCircle else Icons.Filled.Check,
                            when {
                                isLast -> "Enter Bloo"
                                pageIndex == 0 -> "Get started"
                                else -> "Next"
                            },
                            pending = false,
                        )
                    }
                }
                if (pinRequired) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Set your PIN above to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Step 1: a short welcome + feature highlights. No per-item entrance
 * animation here -- [AnimatedContent]'s own slide/fade in [OnboardingScreen]
 * already animates the whole page in, and layering a second, blur-based
 * entrance on top of every single line/card (as this page used to) fought
 * with that slide and read as jittery rather than smooth.
 */
@Composable
internal fun OnboardingIntroPage() {
    val scheme = MaterialTheme.colorScheme
    Text("👋", style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Welcome to Bloo",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black,
        color = scheme.onSurface,
    )
    Text(
        "Control your Hyundai, Genesis, or Kia from your phone -- lock, climate, " +
            "charge status, and more. Let's get your car set up.",
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    val highlights = listOf(
        Triple(Icons.Filled.Bolt, "Live status", "Battery, fuel, and lock state at a glance"),
        Triple(Icons.Filled.Thermostat, "Remote climate", "Warm it up or cool it down before you get in"),
        Triple(Icons.Filled.SwapHoriz, "Multiple cars", "Swipe between every car on your account"),
    )
    highlights.forEach { (icon, title, body) ->
        OnboardingTipCard(icon, title, body)
    }
}

/**
 * Step 2: notifications, biometrics, and Drive/manual sync -- all optional,
 * Next always works regardless. Each gets its own solid card (icon + title +
 * body + action) instead of a bare full-width button floating directly on
 * the animated Aurora background -- a moving, colourful backdrop is a poor
 * contrast surface for plain text, and three thin buttons with nothing else
 * around them read as an empty step. Syncing here (not just notifications +
 * biometrics) also means a restored backup can skip the per-car setup
 * screens later in this same flow for any car it already configured -- see
 * [buildOnboardingSteps]' `preConfiguredVins`.
 */
@Composable
internal fun OnboardingSetupPage(vm: AppViewModel, state: UiState, context: android.content.Context, canBio: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(
        "Quick setup",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = scheme.onSurface,
    )
    Text(
        if (canBio)
            "All optional -- skip anything here and turn it on later in Settings."
        else
            "Everything here is optional -- except one thing: this device has no fingerprint sensor, so a PIN is required to lock the app.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        var notifGranted by remember {
            mutableStateOf(com.bloo.bluelink.data.Notifications.hasPermission(context))
        }
        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> notifGranted = granted }
        OnboardingSetupCard(
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            body = "Get notified about charge status, alerts, and app updates.",
            done = notifGranted,
        ) {
            MorphButton(
                onClick = { if (!notifGranted) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                active = notifGranted,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                MorphButtonLabel(
                    if (notifGranted) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
                    if (notifGranted) "Enabled" else "Enable notifications",
                    pending = false,
                )
            }
        }
    }

    if (canBio) {
        var bioEnabled by remember { mutableStateOf(false) }
        OnboardingSetupCard(
            icon = Icons.Filled.Fingerprint,
            title = "Fingerprint lock",
            body = "Require your fingerprint to open Bloo.",
            done = bioEnabled,
        ) {
            MorphButton(
                onClick = {
                    if (!bioEnabled) {
                        context.findFragmentActivity()?.let { activity ->
                            showBiometricPrompt(
                                activity = activity,
                                title = "Enable fingerprint lock",
                                subtitle = "Confirm to require it when opening Bloo",
                                onSuccess = { vm.setBiometricLock(true); bioEnabled = true },
                                onError = {},
                            )
                        }
                    }
                },
                active = bioEnabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                MorphButtonLabel(
                    if (bioEnabled) Icons.Filled.CheckCircle else Icons.Filled.Fingerprint,
                    if (bioEnabled) "Enabled" else "Enable fingerprint lock",
                    pending = false,
                )
            }
        }
    }

    // --- App PIN ---
    // Required (this exact card, not a skipped option) on devices with no
    // biometrics: without either mechanism the app could never lock at all.
    // On biometric devices it's the optional backup PIN.
    if (!canBio || !state.appPinSet) {
        OnboardingSetupCard(
            icon = Icons.Filled.Lock,
            title = if (canBio) "Backup PIN" else "PIN lock",
            body = if (canBio)
                "Add a 4-8 digit PIN as a backup for days fingerprint sensors act up."
            else
                "This device can't read fingerprints, so Bloo needs a 4-8 digit PIN to lock itself with.",
            done = state.appPinSet,
        ) {
            OnboardingPinForm(
                existing = state.appPinSet,
                onSet = { pin -> vm.setAppPin(pin) },
            )
        }
    }

    // --- Sync across devices (Google Drive or a plain file) ---
    var showDriveDialog by remember { mutableStateOf(false) }
    val driveSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { vm.setSyncUri(it) } }
    val driveOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }
    if (showDriveDialog) {
        DriveSyncSetupDialog(
            onDismissRequest = { showDriveDialog = false },
            onSaveToDrive = { showDriveDialog = false; driveSaveLauncher.launch("bloo_settings.json") },
            onOpenFromDrive = { showDriveDialog = false; driveOpenLauncher.launch(arrayOf("application/json")) },
        )
    }
    val syncEnabled = state.syncUri != null
    OnboardingSetupCard(
        icon = Icons.Filled.CloudSync,
        title = "Sync across devices",
        body = if (syncEnabled) {
            "Your settings and car photos back up to Google Drive automatically."
        } else {
            "Join an existing backup to bring in your car photos and setup automatically, or start a fresh one."
        },
        done = syncEnabled,
    ) {
        // AnimatedContent, not a bare if/else -- this used to snap straight
        // from the "Set up Drive sync" button to the "enabled" row the instant
        // the dialog finished, the one un-animated content swap left in a step
        // whose sibling cards (notifications, fingerprint) at least keep the
        // same MorphButton in place and only recolor it.
        AnimatedContent(
            targetState = syncEnabled,
            // Explicit, not the implicit default -- every other AnimatedContent
            // in this file specifies its own transitionSpec; this one didn't,
            // which meant a real height difference between the two states (the
            // MorphButton's Material3 minimum touch target vs. the plain
            // "enabled" row) snapped instantly under the fade instead of
            // animating, a small but visible pop right when Drive sync
            // finishes setting up.
            transitionSpec = {
                (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "onboardingSyncDone",
        ) { enabled ->
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Drive sync enabled", fontWeight = FontWeight.SemiBold, color = scheme.primary)
                }
            } else {
                MorphButton(
                    onClick = { showDriveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    MorphButtonLabel(Icons.Filled.Cloud, "Set up Drive sync", pending = false)
                }
            }
        }
    }
}

/** One card in the onboarding Setup step: icon + title + body on a solid
 *  surface -- not directly on the animated Aurora background, which made
 *  plain text here hard to read against a busy, colourful, moving backdrop
 *  -- with [content] (a MorphButton or a "done" status row) below. [done]
 *  tints the icon chip to the primary color as a lightweight "this one's
 *  handled" cue, matching the checkmark treatment MorphButton itself already
 *  uses for its own active state. */
/**
 * The create-a-PIN mini form used by onboarding (and, in a slimmer re-use,
 * the building block of the Settings set/change/remove dialogs): two
 * matching 4-8 digit fields, a haptic'd Save only once valid. [existing]
 * true just swaps the call to "Replace PIN" semantics -- the caller handles
 * what that means; this form only ever validates and reports a valid new
 * PIN.
 */
@Composable
internal fun OnboardingPinForm(
    existing: Boolean,
    onSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val valid = pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS &&
        pin == confirm
    val sanitize: (String) -> String = { it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = sanitize(it); attempted = false },
            placeholder = { Text("4–8 digit PIN") },
            singleLine = true,
            shape = FieldShape,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && pin.isNotEmpty() && pin.length < PinCrypto.PIN_MIN_DIGITS,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = sanitize(it); attempted = false },
            placeholder = { Text("Confirm PIN") },
            singleLine = true,
            shape = FieldShape,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && confirm.isNotEmpty() && pin != confirm,
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && (pin.length < PinCrypto.PIN_MIN_DIGITS || pin != confirm)) {
            Text(
                "PINs must be 4-8 digits and match.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
        MorphButton(
            onClick = {
                if (valid) {
                    haptics?.click()
                    onSet(pin)
                    pin = ""
                    confirm = ""
                } else {
                    attempted = true
                    haptics?.tick()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            enabled = pin.isNotEmpty() && confirm.isNotEmpty(),
        ) {
            MorphButtonLabel(Icons.Filled.Lock, if (existing) "Replace PIN" else "Save PIN", pending = false)
        }
    }
}

@Composable
internal fun OnboardingSetupCard(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (done) scheme.primaryContainer else scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (done) scheme.onPrimaryContainer else scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** One step per car: powertrain, seats, and steering-wheel heat together on a
 *  single dedicated screen -- reuses the exact same persisted-flag wiring as
 *  [CarFeatureWizard]'s per-feature pages, just consolidated into one page
 *  per vehicle instead of three. */
/**
 * A single tinted "tip" card: a rounded [surfaceContainerHigh] surface holding a
 * primary-tinted icon beside a bold title and a muted one-line body. The onboarding
 * intro and crash-course pages each render a list of these; the card chrome was
 * copied verbatim between them, so it lives here and each page just maps its own
 * `Triple(icon, title, body)` list onto it.
 */
@Composable
internal fun OnboardingTipCard(icon: ImageVector, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The three-line header every setup-wizard page opens with: a primary-coloured
 * eyebrow, a large title, and a supporting paragraph. Emitted as bare siblings (NOT
 * wrapped in a Column) because the callers place them as direct children of a Column
 * with its own `Arrangement.spacedBy`, which spaces the header lines and the gap to
 * the page content below -- an inner Column would collapse that spacing. The title is
 * pinned to `onSurface` (== `onBackground` in every scheme this app produces), so all
 * four pages render pixel-identically to how they did when hand-rolled.
 */
@Composable
internal fun WizardPageHeader(eyebrow: String, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Bold)
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = scheme.onSurface)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
}

@Composable
internal fun OnboardingCarPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    sc: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Set up",
        vehicle.name,
        "Bloo cannot read powertrain or feature info from the API. Set them once here so the right controls appear.",
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Powertrain", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        val currentPt = state.powertrainOf(vehicle)
        PowertrainPicker(current = currentPt) { pt -> vm.setPowertrain(vehicle, pt) }
    }

    // Only Hyundai/Genesis US vehicles have a real head-unit generation to
    // confirm -- see platformOverridable's own doc.
    if (vehicle.platformOverridable) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Head-unit generation", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
            PlatformPicker(current = state.platformOf(vehicle)) { pt -> vm.setPlatform(vehicle, pt) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Seats", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            SeatPositions.forEachIndexed { i, pos ->
                if (i > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                WizardSeatRow(pos.label, pos.heat(sc), pos.cool(sc),
                    { vm.setSeatFlag(vehicle, pos.heatKey, it) }, { vm.setSeatFlag(vehicle, pos.coolKey, it) })
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Extras", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        val extrasHaptics = LocalHaptics.current
        // Same MorphButton as the rest of the app: a filled pill that lights up
        // secondaryContainer while the feature is on (for cars with it).
        MorphButton(
            onClick = { vm.setSeatFlag(vehicle, "sw", !sc.steeringWheel) },
            active = sc.steeringWheel,
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            activeContainerColor = scheme.secondaryContainer,
            activeContentColor = scheme.onSecondaryContainer,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            minHeight = 0.dp,
        ) {
            if (sc.steeringWheel) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text("Steering wheel heat", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Final step: a quick tip list covering the app's core gestures. */
@Composable
internal fun OnboardingCrashCoursePage() {
    val scheme = MaterialTheme.colorScheme
    Text("🎉", style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "You're all set",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = scheme.onSurface,
    )
    Text(
        "A few things that make Bloo quick to use:",
        style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    val tips = listOf(
        Triple(Icons.Filled.SwapHoriz, "Swipe between cars", "If you have more than one, swipe left or right on the garage screen"),
        Triple(Icons.Filled.DragHandle, "Tap to expand, hold to reorder", "Tap any pebble for details, or hold and drag to rearrange them"),
        Triple(Icons.Filled.Refresh, "Hold to refresh", "Press and hold the refresh control to pull the latest status from your car"),
        Triple(Icons.Filled.Settings, "Tune it anytime", "Powertrain, seats, and lock settings all live in Settings if things change"),
    )
    tips.forEach { (icon, title, body) ->
        OnboardingTipCard(icon, title, body)
    }
}

/**
 * Standalone wizard shown when a new car is detected after first-run onboarding.
 * Mandatory — navigates to the garage only when every car in [vins] is configured.
 */
@Composable
internal fun CarSetupWizardScreen(vm: AppViewModel, vins: List<String>) {
    val state by vm.state.collectAsState()
    BackHandler {}
    val vehicles = remember(state.vehicles, vins) { state.vehicles.filter { it.vin in vins } }
    val pages = remember(vehicles) { buildSetupPages(vehicles) }
    CarFeatureWizard(
        vm = vm,
        pages = pages,
        onComplete = { vm.finishCarSetup(vins) },
    )
}

/**
 * Renders a linear, swipe-free (button-driven) wizard over [pages]: a top
 * progress bar, the current page's content cross-faded/slid in via
 * [AnimatedContent] keyed on [pageIndex], and a Back/Next footer. Only
 * [pageIndex] is local state -- advancing or retreating just mutates that
 * int, which drives both the progress bar's target and which page content
 * is shown. Reaching "Next" on the last page calls [onComplete] instead of
 * advancing further.
 */
@Composable
internal fun CarFeatureWizard(
    vm: AppViewModel,
    pages: List<WizardPage>,
    onComplete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val state by vm.state.collectAsState()

    var pageIndex by remember { mutableIntStateOf(0) }

    if (pages.isEmpty()) return
    fun goNext() {
        if (pageIndex < pages.lastIndex) {
            pageIndex++
        } else {
            onComplete()
        }
    }
    fun goBack() {
        if (pageIndex > 0) pageIndex--
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surface))),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // Progress bar across the top.
            val progress = if (pages.size > 1) pageIndex.toFloat() / (pages.lastIndex.toFloat()) else 1f
            val animatedProgress by animateFloatAsState(progress, tween(WizardProgressDurationMs), label = "wizProgress")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(scheme.primary, scheme.tertiary),
                            ),
                        ),
                )
            }

            // Slide-animated page content.
            AnimatedContent(
                targetState = pageIndex,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally { it * dir } + fadeIn(tween(WizardStepFadeInDurationMs))) togetherWith
                        (slideOutHorizontally { -it * dir } + fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "wizPage",
            ) { idx ->
                val pg = pages.getOrNull(idx) ?: return@AnimatedContent
                val veh = pg.vin?.let { vin -> state.vehicles.firstOrNull { it.vin == vin } }
                val sc = veh?.let { state.seatConfigs[it.vin] } ?: com.bloo.bluelink.data.SeatConfig()
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        when (pg.kind) {
                            WizardStepKind.POWERTRAIN -> WizardPowertrainPage(veh, state, vm)
                            WizardStepKind.PLATFORM -> WizardPlatformPage(veh, state, vm)
                            WizardStepKind.SEATS -> WizardSeatsPage(veh, sc, vm)
                            WizardStepKind.STEERING -> WizardSteeringPage(veh, sc, vm)
                        }
                    }
                }
            }

            // Back / Next navigation strip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = pageIndex > 0,
                    modifier = Modifier.weight(1f),
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
                ) {
                    // MorphButton, not OutlinedCard -- same fix as the main
                    // OnboardingScreen's own Back button (this wizard is the
                    // near-identical "a car showed up after first run" cousin
                    // of that flow, and had copied the same stock-chrome
                    // button along with everything else). Picks up MorphButton's
                    // own haptic click for free too, which this Back button
                    // was missing outright -- unlike goNext below, whose
                    // MorphButton already had it.
                    //
                    // Wrapped in SafeExpansiveButton, matching goNext beside it and the main
                    // OnboardingScreen's own Back/Next -- it was missing the press-growth
                    // affordance every other button pair in the app gets.
                    val backSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(interactionSource = backSource, enabled = true) {
                        MorphButton(
                            onClick = ::goBack,
                            interactionSource = backSource,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                        ) {
                            Text("Back", style = ButtonLabelStyle)
                        }
                    }
                }
                val nextSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = nextSource,
                    enabled = true,
                    modifier = Modifier.weight(if (pageIndex > 0) 2f else 1f),
                ) {
                    MorphButton(
                        onClick = ::goNext,
                        active = true,
                        interactionSource = nextSource,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        val isLast = pageIndex == pages.lastIndex
                        // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text.
                        MorphButtonLabel(
                            if (isLast) Icons.Filled.CheckCircle else Icons.Filled.Check,
                            if (isLast) "Done" else "Next",
                            pending = false,
                        )
                    }
                }
            }
        }

    }
}

/**
 * One wizard page: a static list of the four [Powertrain] options, each a
 * selectable [Surface] row. Selecting a row calls [AppViewModel.setPowertrain]
 * directly (there's no local "pending" selection) -- the row's highlighted
 * state is driven straight off `state.powertrainOf(vehicle)`, so the whole
 * row list recomposes the instant the view model's state updates.
 */
@Composable
internal fun WizardPowertrainPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Powertrain",
        "What powers the ${vehicle.name}?",
        "Bloo uses this to show the right status tiles: battery percentage for EVs, " +
            "fuel level for gas, or both for plug-in hybrids.",
    )
    val current = state.powertrainOf(vehicle)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.bloo.bluelink.data.Powertrain.entries.forEach { pt ->
            val selected = current == pt
            val (icon, label, desc) = when (pt) {
                com.bloo.bluelink.data.Powertrain.GAS -> Triple("⛽", "Gasoline", "Combustion engine only")
                com.bloo.bluelink.data.Powertrain.HYBRID -> Triple("🔋", "Hybrid", "Gas + small electric motor (no plug)")
                com.bloo.bluelink.data.Powertrain.PHEV -> Triple("🔌", "Plug-in Hybrid", "Gas + large battery you can charge")
                com.bloo.bluelink.data.Powertrain.EV -> Triple("", "Electric", "Battery-only, no fuel tank")
            }
            // Same MorphButton as every other selector: pill at rest, fills
            // primaryContainer as a rounded square once chosen.
            MorphButton(
                onClick = { vm.setPowertrain(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                active = selected,
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                activeContainerColor = scheme.primaryContainer,
                activeContentColor = scheme.onPrimaryContainer,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                contentPadding = PaddingValues(16.dp),
                minHeight = 0.dp,
            ) {
                Text(icon, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                }
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

/**
 * One wizard page: which head-unit generation this Hyundai/Genesis US car
 * has, Gen5W or ccNC -- only ever reached for a vehicle where
 * [platformOverridable] is true (see [buildSetupPages]), same two-option
 * shape as [WizardPowertrainPage] otherwise: a selectable [Surface] row per
 * option, driven straight off `state.platformOf(vehicle)`, no local
 * "pending" selection.
 */
@Composable
internal fun WizardPlatformPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Head-unit generation",
        "Which generation is the ${vehicle.name}?",
        "Bloo can't always tell these apart from the API alone. Confirm it here " +
            "so features like Trips only show up when they're actually available.",
    )
    val current = state.platformOf(vehicle)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VehiclePlatform.entries.forEach { pt ->
            val selected = current == pt
            val (label, desc) = when (pt) {
                VehiclePlatform.GEN5W -> "Gen5W" to "Older head unit -- no Trips, no connected-car store"
                VehiclePlatform.CCNC -> "ccNC" to "Newer head unit -- Trips and the connected-car store, where the backend supports them"
            }
            // Same MorphButton as the powertrain page: pill at rest, fills
            // primaryContainer as a rounded square once chosen.
            MorphButton(
                onClick = { vm.setPlatform(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                active = selected,
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                activeContainerColor = scheme.primaryContainer,
                activeContentColor = scheme.onPrimaryContainer,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                contentPadding = PaddingValues(16.dp),
                minHeight = 0.dp,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                }
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * One wizard page: renders a heat/cool toggle-chip row per [SeatPositions]
 * entry, each row wired straight to its own persisted flag via
 * [AppViewModel.setSeatFlag] -- no local staging state, so a tap is reflected
 * immediately once the view model emits the updated [SeatConfig].
 */
@Composable
internal fun WizardSeatsPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    seats: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Seat comfort",
        "What does the ${vehicle.name} have?",
        "Bloo shows only the controls your car actually supports. Skip any seats you don't have.",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SeatPositions.forEachIndexed { i, pos ->
            if (i > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
            WizardSeatRow(pos.label, pos.heat(seats), pos.cool(seats),
                { vm.setSeatFlag(vehicle, pos.heatKey, it) }, { vm.setSeatFlag(vehicle, pos.coolKey, it) })
        }
    }
    Text(
                "You can change these any time in Settings under your car card.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )
}

/** The four seat positions, each pairing its persisted heat/cool flag keys with
 *  the matching [SeatConfig] fields — the seat matrix lives here once instead of
 *  being hand-written at each of the three places seats are configured. */
internal data class SeatPosition(
    val label: String,
    val heatKey: String,
    val coolKey: String,
    val heat: (SeatConfig) -> Boolean,
    val cool: (SeatConfig) -> Boolean,
)

internal val SeatPositions = listOf(
    SeatPosition("Driver", "dh", "dc", { it.driverHeat }, { it.driverCool }),
    SeatPosition("Front passenger", "ph", "pc", { it.passHeat }, { it.passCool }),
    SeatPosition("Rear left", "rlh", "rlc", { it.rearLeftHeat }, { it.rearLeftCool }),
    SeatPosition("Rear right", "rrh", "rrc", { it.rearRightHeat }, { it.rearRightCool }),
)

@Composable
internal fun WizardSeatRow(
    label: String,
    heat: Boolean,
    cool: Boolean,
    onHeat: (Boolean) -> Unit,
    onCool: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WizardToggleChip(label = "Heat", selected = heat, onClick = { onHeat(!heat) })
            WizardToggleChip(label = "Cool ❄️", selected = cool, onClick = { onCool(!cool) })
        }
    }
}

@Composable
internal fun WizardToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // The same MorphButton as everywhere: filled pill, secondaryContainer when
    // selected, outline border only while unselected (the wrapper clears it on
    // active). No second chip implementation left.
    MorphButton(
        onClick = { onClick() },
        active = selected,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        minHeight = 0.dp,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** One wizard page for the single "heated steering wheel" flag; same
 *  direct-to-view-model wiring as the other wizard pages, just one row. */
@Composable
internal fun WizardSteeringPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    seats: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Climate features",
        "Any extras on the ${vehicle.name}?",
        "Enable what the car actually has. These control which options appear in the climate command.",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(vertical = 8.dp),
    ) {
        WizardFeatureToggle(
            title = "Heated steering wheel",
            body = "Warm the steering wheel via the remote climate command",
            checked = seats.steeringWheel,
            onChecked = { vm.setSeatFlag(vehicle, "sw", it) },
        )
    }
    Text(
        "That's it for ${vehicle.name}. Tap Next to continue.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )
}

@Composable
internal fun WizardFeatureToggle(
    title: String,
    body: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            // Same fix as ToggleRow: toggleable + Role.Switch on the row, with the
            // inner track's own semantics node cleared, so TalkBack sees one
            // correctly-announced toggle instead of two focus stops.
            .toggleable(value = checked, role = Role.Switch) { next ->
                // ToggleRow fires these; this row did not, so the one toggle a new
                // user meets during onboarding was also the only silent one.
                if (next) haptics?.toggleOn() else haptics?.toggleOff()
                onChecked(next)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        // MorphToggleTrack, not a stock Switch. ToggleRow's docstring calls itself
        // "the app's one toggle control for boolean settings", built specifically so
        // there is no "default-Material holdout in an otherwise fully custom UI" --
        // and this row was that holdout. It clears its own semantics, so the
        // clearAndSetSemantics the Switch needed here is gone with it.
        MorphToggleTrack(checked)
    }
}
internal class Burst(val x: Float, val y: Float, val start: Float, val life: Float, val hue: Float, val count: Int, val maxR: Float)
/**
 * A short, lightweight particle-burst fireworks animation drawn on a Canvas.
 *
 * Seven [Burst]s are generated once (`remember`) with randomized position,
 * start-delay, lifetime, hue, particle count, and max radius. A single
 * [Animatable] `t` is driven from 0 to 1 over 2.6s and is the *only* thing
 * that changes over time; each burst reads its own local progress
 * `(t - start) / life` from that shared clock and is invisible outside
 * [0, 1]. For a visible burst, particles are placed evenly around a circle
 * of growing radius `local * maxR`, faded out via `alpha = 1 - local`, and
 * given a small downward drift (`local² * height * 0.06`) to mimic gravity.
 * Nothing here loops -- once `t` reaches 1 all bursts are permanently done.
 */
@Composable
internal fun FireworksOverlay(modifier: Modifier = Modifier) {
    val bursts = remember {
        val r = kotlin.random.Random(System.nanoTime())
        List(7) {
            Burst(
                x = r.nextFloat() * 0.8f + 0.1f,
                y = r.nextFloat() * 0.5f + 0.12f,
                start = r.nextFloat() * 0.55f,
                life = r.nextFloat() * 0.25f + 0.35f,
                hue = r.nextFloat() * 360f,
                count = 18 + r.nextInt(14),
                maxR = r.nextFloat() * 0.12f + 0.14f,
            )
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2600)) }
    Canvas(modifier) {
        bursts.forEach { b ->
            val local = ((t.value - b.start) / b.life)
            if (local <= 0f || local >= 1f) return@forEach
            val cx = b.x * size.width
            val cy = b.y * size.height
            val r = local * b.maxR * size.height
            val alpha = (1f - local).coerceIn(0f, 1f)
            val color = Color.hsv(b.hue, 0.85f, 1f).copy(alpha = alpha)
            for (k in 0 until b.count) {
                val ang = (k.toFloat() / b.count) * (2f * Math.PI.toFloat())
                val px = cx + kotlin.math.cos(ang) * r
                val py = cy + kotlin.math.sin(ang) * r + local * local * size.height * 0.06f
                drawCircle(color, radius = 5f * alpha + 1.5f, center = Offset(px, py))
            }
        }
    }
}
