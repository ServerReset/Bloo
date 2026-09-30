@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

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
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi

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
 * [AnimatedContent] in [CarSetupWizardScreen], so a multi-car setup becomes
 * one continuous Back/Next sequence instead of nested per-car flows.
 */
internal fun buildSetupPages(vehicles: List<com.bloo.bluelink.data.Vehicle>): List<WizardPage> = buildList {
    vehicles.forEach { v ->
        add(WizardPage(WizardStepKind.POWERTRAIN, v.vin))
        if (v.platformOverridable) add(WizardPage(WizardStepKind.PLATFORM, v.vin))
        add(WizardPage(WizardStepKind.SEATS, v.vin))
        add(WizardPage(WizardStepKind.STEERING, v.vin))
    }
}

internal enum class OnboardingStepKind { INTRO, SETUP, CAR, CRASH_COURSE, FEATURES }

internal data class OnboardingStep(val kind: OnboardingStepKind, val vin: String? = null)

/**
 * Whether notifications block leaving the [OnboardingStepKind.SETUP] step: required on API 33+
 * (POST_NOTIFICATIONS exists) until the permission is granted. Pure so the gate can be pinned
 * by a plain JVM test without a running Activity or a permission dialog.
 */
internal fun notifRequiredOnSetup(
    onSetup: Boolean,
    notificationsSupported: Boolean,
    notifGranted: Boolean,
): Boolean = onSetup && notificationsSupported && !notifGranted

/**
 * Whether a lock blocks leaving the SETUP step. Exactly ONE mechanism is required: biometrics
 * when the device has them enrolled, otherwise a PIN. Pure, so the truth table is testable.
 */
internal fun lockRequiredOnSetup(
    onSetup: Boolean,
    canBio: Boolean,
    biometricLock: Boolean,
    appPinSet: Boolean,
): Boolean = onSetup && (if (canBio) !biometricLock else !appPinSet)

/** The SETUP step blocks Next while [notifRequiredOnSetup] or [lockRequiredOnSetup] is true. */
internal fun setupIsBlocked(
    onSetup: Boolean,
    notificationsSupported: Boolean,
    notifGranted: Boolean,
    canBio: Boolean,
    biometricLock: Boolean,
    appPinSet: Boolean,
): Boolean =
    notifRequiredOnSetup(onSetup, notificationsSupported, notifGranted) ||
        lockRequiredOnSetup(onSetup, canBio, biometricLock, appPinSet)

/**
 * Flattens first-run onboarding into one linear list of steps: a welcome
 * intro, a combined notifications+biometrics+sync setup step, one CAR step
 * per vehicle that isn't already configured (each vehicle gets its own
 * dedicated screen rather than being stacked in one scroll or split into
 * per-feature pages), a crash-course on the basic gestures (swipe, hold to
 * reorder, hold to refresh), and a closing FEATURES page -- the last thing
 * shown before "Enter Bloo" hands off to the real garage, so it is the one
 * screen every new user is guaranteed to see once, unlike a feature buried
 * in Settings they may never open. Drives the single [AnimatedContent] in
 * [OnboardingScreen] the same way [buildSetupPages] drives [CarFeatureWizard].
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
    add(OnboardingStep(OnboardingStepKind.FEATURES))
}

/**
 * First-run onboarding: a button-driven multi-screen wizard -- intro, then
 * notifications/biometrics, then one screen per car, then a crash course on
 * the app's gestures, then a features highlight reel -- capped off by
 * [AppViewModel.finishOnboarding]. Shares its shell shape
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
    val state by vm.state.collectAsStateWithLifecycle()
    val canBio = remember { vm.canUseBiometrics() }
    val scheme = MaterialTheme.colorScheme
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Notifications are REQUIRED on the setup step (API 33+), so the grant must be visible to
    // the Next gate here, not only to the setup card's own button. Re-checked on resume so
    // returning from the system permission screen updates it without a relaunch.
    var notifGranted by remember { mutableStateOf(com.bloo.bluelink.data.Notifications.hasPermission(context)) }
    val notifLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(notifLifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifGranted = com.bloo.bluelink.data.Notifications.hasPermission(context)
            }
        }
        notifLifecycle.lifecycle.addObserver(obs)
        onDispose { notifLifecycle.lifecycle.removeObserver(obs) }
    }

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

    // The setup step is BLOCKING: notifications (API 33+) and a lock (biometrics when the
    // device has them, else a PIN) are both required before Next unlocks. The lock card swaps
    // to whichever the device supports, so there is never a second mechanism to skip.
    val onSetup = steps.getOrNull(pageIndex)?.kind == OnboardingStepKind.SETUP
    val notificationsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notifRequired = notifRequiredOnSetup(onSetup, notificationsSupported, notifGranted)
    val setupBlocked = setupIsBlocked(
        onSetup = onSetup,
        notificationsSupported = notificationsSupported,
        notifGranted = notifGranted,
        canBio = canBio,
        biometricLock = appearance.biometricLock,
        appPinSet = state.appPinSet,
    )

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
            Spacer(Modifier.height(GapRow))

            // --- Progress: an animated bar plus a small step counter ---
            val progress = if (steps.size > 1) pageIndex.toFloat() / lastIndex.toFloat() else 1f
            val animatedProgress by animateFloatAsState(progress, tween(WizardProgressDurationMs), label = "onboardProgress")
            Row(
                Modifier.fillMaxWidth().paddingHorizontal24(),
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
                LabelText("${pageIndex + 1}/${steps.size}")
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
                        .paddingHorizontal24(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(GapSection),
                    ) {
                        when (step.kind) {
                            OnboardingStepKind.INTRO -> OnboardingIntroPage()
                            OnboardingStepKind.SETUP -> OnboardingSetupPage(vm, state, context, canBio, appearance.biometricLock, notifGranted) { notifGranted = it }
                            OnboardingStepKind.CAR -> {
                                val vehicle = step.vin?.let { vin -> state.vehicles.firstOrNull { it.vin == vin } }
                                val sc = vehicle?.let { state.seatConfigs[it.vin] } ?: com.bloo.bluelink.data.SeatConfig()
                                OnboardingCarPage(vehicle, state, sc, vm)
                            }
                            OnboardingStepKind.CRASH_COURSE -> OnboardingCrashCoursePage()
                            OnboardingStepKind.FEATURES -> OnboardingFeaturesPage(state)
                        }
                    }
                }
            }

            // --- Back / Next footer ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .paddingHorizontal24Vertical16(),
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
                    MorphButton(
                        onClick = ::goBack,
                        interactionSource = backSource,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = GapSection),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        expressive = true,
                    ) {
                        Text("Back", style = ButtonLabelStyle)
                    }
                }
                val nextSource = remember { MutableInteractionSource() }
                // The weight goes on the SafeExpansiveButton, which is the Row's actual child,
                // NOT on the MorphButton inside it -- whose parent is that wrapper's own layout
                // and never reads it. The same dead-weight mistake the cover action bar had:
                // this button was silently hugging its label instead of taking the 2:1 share
                // over Back that the expression asks for.
                MorphButton(
                    onClick = ::goNext,
                    active = true,
                    enabled = !setupBlocked,
                    interactionSource = nextSource,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = GapSection),
                    expressive = true,
                ) {
                    // MorphButtonLabel, not a hand-rolled Icon+Spacer+Text -- that Text used
                    // FontWeight.Bold, where every other button label in the app (including
                    // this one's own "Back" neighbour) uses SemiBold.
                    val nextIcon: ImageVector = if (isLast) AppIcons.CheckCircle else AppIcons.Check
                    val nextText: String = when {
                        isLast -> "Enter Bloo"
                        pageIndex == 0 -> "Get started"
                        else -> "Next"
                    }
                    MorphButtonLabel(
                        nextIcon,
                        nextText,
                        pending = false,
                    )
                }
                if (setupBlocked) {
                    Spacer(Modifier.height(GapHairline))
                    BodySmallText(
                        if (notifRequired) "Turn on notifications above to continue."
                        else "Set up the lock above to continue.",
                    )
                }
            }
        }
    }
}

/**
 * [Screen.SyncChoice]: the very first thing a first-run device shows once sign-in
 * resolves at least one vehicle -- before the welcome wizard gets a chance to
 * start. Two ways forward:
 *
 * - "Restore from sync" opens a document picker for an existing Drive-sync
 *   file, exactly the same join [OnboardingSetupPage]'s own buried "Sync
 *   across devices" card and Settings' "Backup & sync" card both use
 *   ([AppViewModel.importSettingsAndSync]/[AppViewModel.restoreFromSyncThenContinue]).
 *   That's deliberate reuse, not a new sync mechanism -- this screen only
 *   changes WHEN the option is offered, surfacing it before a single wizard
 *   page renders instead of requiring a click through to the SETUP step to
 *   discover it. Once the join finishes, [AppViewModel.restoreFromSyncThenContinue]
 *   re-resolves the destination screen from what actually got restored:
 *   straight to the garage if the import already answered everything
 *   onboarding would have asked, [Screen.CarSetup] for any car it didn't
 *   cover, or the normal wizard if the file didn't resolve first-run status
 *   at all.
 * - "Set up fresh" ([AppViewModel.declineSyncRestore]) proceeds into
 *   [OnboardingScreen] exactly as if this screen didn't exist.
 *
 * `restoring` only guards against double-tapping the restore button while its
 * (network-bound) join is in flight -- there's nothing to reset it back to
 * false on failure, because a failed join reports its own snackbar and this
 * whole screen stays composed either way, ready to be tapped again.
 */
@Composable
internal fun SyncChoiceScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var restoring by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            restoring = true
            vm.restoreFromSyncThenContinue(context, uri)
        }
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .paddingHorizontal24(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔄", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(GapGroup))
            Text(
                "Set up this device",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(GapHairline))
            BodyMediumText(
                "Already use Bloo elsewhere with sync? Bring that setup in.",
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            OnboardingSetupCard(
                icon = Icons.Filled.CloudSync,
                title = "Restore from sync",
                body = "Pick the sync file another device uses -- theme, layout, alerts, presets and car setup come with it.",
                done = false,
            ) {
                MorphButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                    enabled = !restoring,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = GapGroup),
                ) {
                    if (restoring) LoadingIndicator() else MorphButtonLabel(Icons.Filled.Cloud, "Choose sync file", pending = false)
                }
            }

            Spacer(Modifier.height(GapSection))

            MorphButton(
                onClick = vm::declineSyncRestore,
                enabled = !restoring,
                modifier = Modifier.fillMaxWidth(),
                containerColor = scheme.secondaryContainer,
                contentColor = scheme.onSecondaryContainer,
            ) {
                Text("Set up fresh", style = ButtonLabelStyle, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
