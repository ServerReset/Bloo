@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.SideEffect
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

internal enum class OnboardingStepKind { WELCOME, RESTORE, SETUP, LOOK, CAR, TIPS, FEATURES }

internal data class OnboardingStep(val kind: OnboardingStepKind, val vin: String? = null)

/** Which deck of cards is showing. One component runs all three. */
internal sealed interface OnboardingMode {
    /** A device's first run: everything, ending in the app. */
    data object FirstRun : OnboardingMode

    /** Cars detected after first run, each needing its own setup card; nothing else. */
    data class NewCars(val vins: List<String>) : OnboardingMode

    /** Summoned again from Settings: the general cards, minus restore and the per-car questions. */
    data object Replay : OnboardingMode
}

/**
 * Whether notifications block leaving the [OnboardingStepKind.SETUP] card: required on API 33+
 * (POST_NOTIFICATIONS exists) until the permission is granted. Pure so the gate can be pinned
 * by a plain JVM test without a running Activity or a permission dialog.
 */
internal fun notifRequiredOnSetup(
    onSetup: Boolean,
    notificationsSupported: Boolean,
    notifGranted: Boolean,
): Boolean = onSetup && notificationsSupported && !notifGranted

/**
 * Whether a lock blocks leaving the SETUP card. Exactly ONE mechanism is required: biometrics
 * when the device has them enrolled, otherwise a PIN. Pure, so the truth table is testable.
 */
internal fun lockRequiredOnSetup(
    onSetup: Boolean,
    canBio: Boolean,
    biometricLock: Boolean,
    appPinSet: Boolean,
): Boolean = onSetup && (if (canBio) !biometricLock else !appPinSet)

/** The SETUP card blocks Next while [notifRequiredOnSetup] or [lockRequiredOnSetup] is true. */
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
 * The cards in a deck, in order.
 *
 *  - First run: welcome, restore-from-sync, setup (notifications, lock, Drive), look and feel,
 *    then one card per car that isn't already configured, tips, and the closing features card.
 *  - New cars: just a card per car in [OnboardingMode.NewCars.vins].
 *  - Replay: welcome, setup, look, tips, features -- the answers already given are not asked again.
 *
 * [preConfiguredVins] skips a car's card on first run: a backup restored on the RESTORE card can
 * bring in real powertrain/seat config for a car already set up on another device.
 */
internal fun buildOnboardingSteps(
    mode: OnboardingMode,
    vehicles: List<com.bloo.bluelink.data.Vehicle>,
    preConfiguredVins: Set<String> = emptySet(),
): List<OnboardingStep> = buildList {
    when (mode) {
        is OnboardingMode.NewCars ->
            vehicles.filter { it.vin in mode.vins }.forEach { add(OnboardingStep(OnboardingStepKind.CAR, it.vin)) }
        OnboardingMode.Replay -> {
            add(OnboardingStep(OnboardingStepKind.WELCOME))
            add(OnboardingStep(OnboardingStepKind.SETUP))
            add(OnboardingStep(OnboardingStepKind.LOOK))
            add(OnboardingStep(OnboardingStepKind.TIPS))
            add(OnboardingStep(OnboardingStepKind.FEATURES))
        }
        OnboardingMode.FirstRun -> {
            add(OnboardingStep(OnboardingStepKind.WELCOME))
            add(OnboardingStep(OnboardingStepKind.RESTORE))
            add(OnboardingStep(OnboardingStepKind.SETUP))
            add(OnboardingStep(OnboardingStepKind.LOOK))
            vehicles.forEach { if (it.vin !in preConfiguredVins) add(OnboardingStep(OnboardingStepKind.CAR, it.vin)) }
            add(OnboardingStep(OnboardingStepKind.TIPS))
            add(OnboardingStep(OnboardingStepKind.FEATURES))
        }
    }
}

/**
 * Every setup flow in the app: first run, newly detected cars, and the welcome cards summoned
 * again from Settings. A deck of pebble cards swiped left and right like the garage's own; the
 * cards differ by [mode], the chrome never does. The setup card gates leaving it on first run
 * (notifications and a lock), and the last card's button finishes the deck for its mode.
 */
@Composable
internal fun OnboardingScreen(vm: AppViewModel, mode: OnboardingMode = OnboardingMode.FirstRun) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val state by vm.state.collectAsStateWithLifecycle()
    val canBio = remember { vm.canUseBiometrics() }
    val scheme = MaterialTheme.colorScheme
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val firstRun = mode == OnboardingMode.FirstRun
    // Notifications are REQUIRED on the setup card (API 33+), so the grant must be visible to
    // the Next gate here, not only to the card's own button. Re-checked on resume so returning
    // from the system permission screen updates it without a relaunch.
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

    // The deck is a pager, like the app's own. Its length is held here because the step list is
    // computed from the card the user has reached.
    var pageCount by remember { mutableIntStateOf(1) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pageCount })
    val pageIndex = pagerState.currentPage
    val pageScope = androidx.compose.runtime.rememberCoroutineScope()

    // Cars a restored backup already configured, frozen once the user is past the restore and
    // setup cards, so configuring a car on its card can't retroactively shrink the deck out from
    // under the card being looked at. The freeze LATCHES: going back to an earlier card must not
    // re-take the snapshot, or the cars configured in between would vanish from the deck.
    var preConfiguredVins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pastSetup by remember { mutableStateOf(false) }
    LaunchedEffect(state.powertrains.keys, pageIndex) {
        if (!firstRun) return@LaunchedEffect
        if (pageIndex > FIRST_RUN_SETUP_INDEX) pastSetup = true
        if (!pastSetup) preConfiguredVins = state.powertrains.keys.toSet()
    }
    val steps = remember(mode, state.vehicles, preConfiguredVins) { buildOnboardingSteps(mode, state.vehicles, preConfiguredVins) }
    SideEffect { pageCount = steps.size.coerceAtLeast(1) }

    val lastIndex = steps.lastIndex
    val isLast = pageIndex == lastIndex

    // First run only: the setup card is BLOCKING. Notifications (API 33+) and a lock (biometrics
    // when the device has them, else a PIN) are both required before Next unlocks.
    val notificationsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val onSetup = firstRun && steps.getOrNull(pageIndex)?.kind == OnboardingStepKind.SETUP
    val notifRequired = notifRequiredOnSetup(onSetup, notificationsSupported, notifGranted)
    val setupBlocked = setupIsBlocked(onSetup, notificationsSupported, notifGranted, canBio, appearance.biometricLock, state.appPinSet)
    val setupIndex = steps.indexOfFirst { it.kind == OnboardingStepKind.SETUP }
    val setupUnmet = firstRun &&
        setupIsBlocked(true, notificationsSupported, notifGranted, canBio, appearance.biometricLock, state.appPinSet)
    // Swiping is free, but not past the setup card while what it requires is undone: the deck
    // settles back onto it, the same gate the Next button enforces.
    LaunchedEffect(pagerState.settledPage, setupUnmet) {
        if (setupUnmet && setupIndex >= 0 && pagerState.settledPage > setupIndex) pagerState.animateScrollToPage(setupIndex)
    }

    fun finish() = when (mode) {
        OnboardingMode.FirstRun -> vm.finishOnboarding()
        is OnboardingMode.NewCars -> vm.finishCarSetup(mode.vins)
        OnboardingMode.Replay -> vm.dismissWelcomeCards()
    }
    fun goNext() {
        if (!isLast) {
            haptics?.click()
            pageScope.launch { pagerState.animateScrollToPage(pageIndex + 1) }
        } else {
            finish()
        }
    }
    fun goBack() {
        if (pageIndex > 0) {
            haptics?.click()
            pageScope.launch { pagerState.animateScrollToPage(pageIndex - 1) }
        } else if (mode == OnboardingMode.Replay) {
            vm.dismissWelcomeCards()
        }
    }
    // Back steps back through the cards and never out of a setup the user still has to finish.
    BackHandler { goBack() }

    LaunchedEffect(isLast) {
        if (isLast && firstRun) {
            Fireworks.playSound(context)
            haptics?.fireworks()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Opaque, and a pointer target of its own so touches never fall through to what the
            // deck sits over; it takes none of them, so the cards and buttons above get every one.
            .background(scheme.background)
            .pointerInput(Unit) {},
    ) {
        AuroraBackground(Modifier.matchParentSize())
        if (isLast && firstRun) FireworksOverlay(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Spacer(Modifier.height(GapSection))
            OnboardingDots(count = steps.size, current = pageIndex, modifier = Modifier.fillMaxWidth())

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 14.dp,
                beyondViewportPageCount = 1,
            ) { idx ->
                val step = steps.getOrNull(idx) ?: return@HorizontalPager
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 24.dp),
                ) {
                    val vehicle = step.vin?.let { vin -> state.vehicles.firstOrNull { it.vin == vin } }
                    val spec = onboardingCardSpec(step.kind, vehicle?.name)
                    PebbleShell(
                        expanded = true,
                        onToggle = {},
                        icon = spec.icon,
                        title = spec.title,
                        summary = spec.summary,
                        canToggle = false,
                        forceExpanded = true,
                        // A darker card, so the panels inside it step UP from it instead of sinking into it.
                        containerColor = scheme.surfaceContainer,
                    ) {
                        when (step.kind) {
                            OnboardingStepKind.WELCOME -> OnboardingWelcomePage()
                            OnboardingStepKind.RESTORE -> OnboardingRestorePage(vm)
                            OnboardingStepKind.SETUP -> OnboardingSetupPage(vm, state, context, canBio, appearance.biometricLock, notifGranted) { notifGranted = it }
                            OnboardingStepKind.LOOK -> OnboardingLookPage(appearance, vm)
                            OnboardingStepKind.CAR -> {
                                val sc = vehicle?.let { state.seatConfigs[it.vin] } ?: com.bloo.bluelink.data.SeatConfig()
                                OnboardingCarPage(vehicle, state, sc, vm)
                            }
                            OnboardingStepKind.TIPS -> OnboardingTipsPage()
                            OnboardingStepKind.FEATURES -> OnboardingFeaturesPage(state)
                        }
                    }
                }
            }

            // Back / Next: the deck's buttons, for anyone who would rather tap than swipe.
            Column(Modifier.fillMaxWidth().paddingHorizontal24Vertical16(), verticalArrangement = Arrangement.spacedBy(GapHairline)) {
                ExpressiveButtonRow(modifier = Modifier.fillMaxWidth(), spacing = 12.dp) {
                    if (pageIndex > 0) {
                        SafeMorphTextButton(text = "Back", onClick = ::goBack)
                    }
                    MorphActionButton(
                        label = when {
                            isLast && mode == OnboardingMode.Replay -> "Dismiss"
                            isLast && mode is OnboardingMode.NewCars -> "Done"
                            isLast -> "Enter Bloo"
                            firstRun && pageIndex == 0 -> "Get started"
                            else -> "Next"
                        },
                        icon = if (isLast) AppIcons.CheckCircle else AppIcons.Check,
                        onClick = ::goNext,
                        enabled = !setupBlocked,
                        active = true,
                    )
                }
                if (setupBlocked) {
                    BodySmallText(
                        if (notifRequired) "Turn on notifications above to continue." else "Set up the lock above to continue.",
                    )
                }
            }
        }
    }
}

/** Index of the SETUP card in a first-run deck (welcome, restore, setup). */
private const val FIRST_RUN_SETUP_INDEX = 2
