package com.bloo.bluelink.ui

import android.os.Build
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.platformOverridable
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class OnboardingStepKind {
    WELCOME, RESTORE, SETUP, LOOK, ALERTS, WATCH,
    /**
     * The three per-car questions. Each one blocks until it is answered: see [needsConfirmation].
     */
    CAR_POWERTRAIN, CAR_PLATFORM, CAR_CLIMATE,
    TIPS, FEATURES,
}

/** The trailing, information-only cards the deck lets you swipe through instead of tapping Next. */
internal fun OnboardingStepKind.isInfoSwipe(): Boolean =
    this == OnboardingStepKind.TIPS || this == OnboardingStepKind.FEATURES

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
 * (POST_NOTIFICATIONS exists) until the permission is granted. Pure so the gate can be pinned by a
 * plain JVM test without a running Activity or a permission dialog.
 */
internal fun notifRequiredOnSetup(
    onSetup: Boolean,
    notificationsSupported: Boolean,
    notifGranted: Boolean,
): Boolean = onSetup && notificationsSupported && !notifGranted

/**
 * Whether a lock blocks leaving the SETUP card. Exactly ONE mechanism is required: biometrics when
 * the device has them enrolled, otherwise a PIN. Pure, so the truth table is testable.
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
 * The cards in a deck, in order. - First run: welcome, restore-from-sync, setup (notifications,
 * lock, Drive), look and feel, which alerts to get, then three cards for each car that isn't
 * already configured (powertrain, head unit where it applies, seats and steering wheel), the watch,
 * tips and the features card. - New cars: just those three cards for each car in
 * [OnboardingMode.NewCars.vins]. - Replay: welcome, setup, look, alerts, every car's cards
 * (ungated, to revisit answers), watch, tips, features. [preConfiguredVins] skips a car's card on
 * first run: a backup restored on the RESTORE card can bring in real powertrain/seat config for a
 * car already set up on another device.
 */
internal fun buildOnboardingSteps(
    mode: OnboardingMode,
    vehicles: List<com.bloo.bluelink.data.Vehicle>,
    preConfiguredVins: Set<String> = emptySet(),
): List<OnboardingStep> = buildList {
    fun carCards(v: com.bloo.bluelink.data.Vehicle) {
        add(OnboardingStep(OnboardingStepKind.CAR_POWERTRAIN, v.vin))
        // Only Hyundai/Genesis US cars have a head-unit generation to confirm.
        if (v.platformOverridable) add(OnboardingStep(OnboardingStepKind.CAR_PLATFORM, v.vin))
        add(OnboardingStep(OnboardingStepKind.CAR_CLIMATE, v.vin))
    }
    when (mode) {
        is OnboardingMode.NewCars -> vehicles.filter { it.vin in mode.vins }.forEach(::carCards)
        OnboardingMode.Replay -> {
            add(OnboardingStep(OnboardingStepKind.WELCOME))
            add(OnboardingStep(OnboardingStepKind.SETUP))
            add(OnboardingStep(OnboardingStepKind.LOOK))
            add(OnboardingStep(OnboardingStepKind.ALERTS))
            vehicles.forEach(::carCards)
            add(OnboardingStep(OnboardingStepKind.WATCH))
            add(OnboardingStep(OnboardingStepKind.TIPS))
            add(OnboardingStep(OnboardingStepKind.FEATURES))
        }
        OnboardingMode.FirstRun -> {
            add(OnboardingStep(OnboardingStepKind.WELCOME))
            add(OnboardingStep(OnboardingStepKind.RESTORE))
            add(OnboardingStep(OnboardingStepKind.SETUP))
            add(OnboardingStep(OnboardingStepKind.LOOK))
            add(OnboardingStep(OnboardingStepKind.ALERTS))
            vehicles.filter { it.vin !in preConfiguredVins }.forEach(::carCards)
            add(OnboardingStep(OnboardingStepKind.WATCH))
            add(OnboardingStep(OnboardingStepKind.TIPS))
            add(OnboardingStep(OnboardingStepKind.FEATURES))
        }
    }
}
/**
 * Every setup flow in the app: first run, newly detected cars, and the welcome cards summoned again
 * from Settings.
 *
 * One standard card at a time, advanced only from the liquid-glass bar at the bottom: the deck never
 * swipes, so the flow reads the same for everyone and nothing depends on a gesture.
 */
@Composable
internal fun OnboardingScreen(vm: AppViewModel, mode: OnboardingMode = OnboardingMode.FirstRun) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val state by vm.state.collectAsStateWithLifecycle()
    val canBio = remember { vm.canUseBiometrics() }
    val scheme = MaterialTheme.colorScheme
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val notif by vm.notifications.collectAsStateWithLifecycle()
    val firstRun = mode == OnboardingMode.FirstRun
    // Notifications are REQUIRED on the setup card (API 33+), so the grant must be visible to the
    // Next gate here, not only to the card's own button. Re-checked on resume so returning from the
    // system permission screen updates it without a relaunch.
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

    // Which step is showing. Advanced only by the bottom bar's Next / Back, never a swipe.
    var pageIndex by remember { mutableIntStateOf(0) }

    // Cars a restored backup already configured, frozen (latched) once past the setup card so
    // configuring a car can't shrink the deck under the visible card.
    var preConfiguredVins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pastSetup by remember { mutableStateOf(false) }
    LaunchedEffect(state.powertrains.keys, pageIndex) {
        if (!firstRun) return@LaunchedEffect
        if (pageIndex > FIRST_RUN_SETUP_INDEX) pastSetup = true
        if (!pastSetup) preConfiguredVins = state.powertrains.keys.toSet()
    }
    val steps = remember(mode, state.vehicles, preConfiguredVins) { buildOnboardingSteps(mode, state.vehicles, preConfiguredVins) }
    // The list can shrink under the visible step (a restored backup configuring a car); keep the
    // index in range.
    LaunchedEffect(steps.size) { if (pageIndex > steps.lastIndex) pageIndex = steps.lastIndex.coerceAtLeast(0) }

    val lastIndex = steps.lastIndex
    val isLast = pageIndex == lastIndex
    // The trailing info-only cards are the swipe section; the bar fades out when it begins and the
    // deck becomes hand-scrollable (it stays scrollable afterwards so you can go back).
    val swipeStart = remember(steps) {
        steps.indexOfFirst { it.kind.isInfoSwipe() }.let { if (it < 0) steps.size else it }
    }
    val inSwipe = pageIndex >= swipeStart
    var swipeUnlocked by remember { mutableStateOf(false) }
    LaunchedEffect(inSwipe) { if (inSwipe) swipeUnlocked = true }

    // First run only: the setup card is BLOCKING. Notifications (API 33+) and a lock (biometrics
    // when the device has them, else a PIN) are both required before Next unlocks.
    val notificationsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    // Per-car cards apply their answer the moment you pick it, so there is nothing to confirm: once
    // the card shows what is right, Next just works.
    val setupUnmetNow = firstRun &&
        setupIsBlocked(true, notificationsSupported, notifGranted, canBio, appearance.biometricLock, state.appPinSet)
    fun blockReason(i: Int): String? {
        val step = steps.getOrNull(i) ?: return null
        return when {
            step.kind == OnboardingStepKind.SETUP && setupUnmetNow ->
                if (notifRequiredOnSetup(true, notificationsSupported, notifGranted)) "Turn on notifications above to continue."
                else "Set up the lock above to continue."
            else -> null
        }
    }
    val blockedHere = blockReason(pageIndex)

    fun finish() = when (mode) {
        OnboardingMode.FirstRun -> vm.finishOnboarding()
        is OnboardingMode.NewCars -> vm.finishCarSetup(mode.vins)
        OnboardingMode.Replay -> vm.dismissWelcomeCards()
    }
    // The exit: fanfare, then the whole deck eases away (shrinks toward the app, fades) before the
    // mode's finish runs, so ending never just snaps to the next screen.
    var leaving by remember { mutableStateOf(false) }
    // True when the deck is closed by the last swipe rather than the final button, so the hand-off
    // to the garage is quick instead of the button path's long firework finish.
    var swipeFinish by remember { mutableStateOf(false) }
    val exit by androidx.compose.animation.core.animateFloatAsState(
        if (leaving) 1f else 0f,
        androidx.compose.animation.core.tween(650, delayMillis = 350),
        label = "deckExit",
    )
    // The finish runs off a plain delay rather than the animation's end callback, so it fires even
    // if the animation is interrupted or the window is idle.
    LaunchedEffect(leaving) {
        if (leaving) {
            kotlinx.coroutines.delay(if (swipeFinish) 500 else 1100)
            finish()
        }
    }
    // The deck is one pager. The bar drives the first (button) section; the trailing info-only
    // section is hand-swipeable, and its final (hand-off) page lands on the garage.
    val deck = androidx.compose.foundation.pager.rememberPagerState(pageCount = { steps.size + 1 })
    val deckScope = rememberCoroutineScope()
    LaunchedEffect(deck) {
        snapshotFlow { deck.settledPage }.collect { p ->
            when {
                p >= steps.size ->
                    if (!leaving) { swipeFinish = true; leaving = true }
                pageIndex != p -> pageIndex = p
            }
        }
    }
    fun goTo(index: Int) {
        if (leaving) return
        val next = index.coerceIn(0, lastIndex)
        if (next == pageIndex) return
        haptics?.click()
        pageIndex = next
        deckScope.launch { deck.animateScrollToPage(next) }
    }
    fun goNext() {
        if (leaving || blockedHere != null) return
        if (!isLast) {
            goTo(pageIndex + 1)
        } else {
            leaving = true
            haptics?.fireworks()
            Fireworks.playSound(context)
        }
    }
    fun goBack() {
        if (pageIndex > 0) goTo(pageIndex - 1) else if (mode == OnboardingMode.Replay) vm.dismissWelcomeCards()
    }
    // Back steps back through the cards and never out of a setup the user still has to finish.
    BackHandler { goBack() }

    // --- Fun: a fireworks burst, a sound and a buzz for every little win -------------------
    // [burst] is re-keyed to replay the overlay; [bigBurst] picks the fanfare over the small ding.
    var burst by remember { mutableIntStateOf(0) }
    var burstBig by remember { mutableStateOf(false) }
    fun celebrate(big: Boolean) {
        burst++
        burstBig = big
        if (big) {
            Fireworks.playSound(context)
            haptics?.fireworks()
        } else {
            haptics?.heavy()
        }
    }
    // The finish: the closing card of a first run (and of a new car's setup) goes off like New
    // Year's.
    LaunchedEffect(isLast) {
        if (isLast && !replayMode(mode)) celebrate(big = true)
    }
    // A soft tick as each step lands, so moving through the deck has a feel.
    LaunchedEffect(pageIndex) {
        if (pageIndex > 0) haptics?.tick()
    }
    // Every setup item that flips to done: notifications, the lock, Drive sync.
    val setupDone = (if (notifGranted) 1 else 0) +
        (if (appearance.biometricLock || state.appPinSet) 1 else 0) +
        (if (state.syncUri != null) 1 else 0)
    var lastSetupDone by remember { mutableIntStateOf(-1) }
    LaunchedEffect(setupDone) {
        if (lastSetupDone >= 0 && setupDone > lastSetupDone) celebrate(big = false)
        lastSetupDone = setupDone
    }
    // Every car that gets its powertrain chosen.
    var lastCarsDone by remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.powertrains.size) {
        if (lastCarsDone >= 0 && state.powertrains.size > lastCarsDone) celebrate(big = false)
        lastCarsDone = state.powertrains.size
    }

    Box(
        Modifier
            .fillMaxSize()
            // Opaque, and a pointer target of its own so touches never fall through to what the
            // deck sits over; it takes none of them, so the cards and buttons above get every one.
            .graphicsLayer {
                val e = exit
                alpha = 1f - e
                scaleX = 1f + 0.12f * e; scaleY = scaleX
            }
            .background(scheme.background)
            .pointerInput(Unit) {},
    ) {
        // The glassy backdrop: the app's own aurora behind a wash of the colour of the step you are
        // on, easing from one accent to the next. This whole layer is the Haze source the cards and
        // the bottom bar blur.
        val backdropHaze = remember { HazeState() }
        val accent by androidx.compose.animation.animateColorAsState(
            onboardingAccent(steps.getOrNull(pageIndex)?.kind ?: OnboardingStepKind.WELCOME),
            androidx.compose.animation.core.tween(MotionLong),
            label = "deckAccent",
        )
        OnboardingAurora(backdropHaze, accent, Modifier.matchParentSize())
        if (burst > 0 || leaving) androidx.compose.runtime.key(burst, leaving) { FireworksOverlay(Modifier.fillMaxSize(), bursts = if (burstBig || leaving) 16 else 7) }

        CompositionLocalProvider(LocalBackdropHaze provides backdropHaze) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // One standard card at a time, in a pager. The button section does not scroll by
                // hand; the trailing information-only section does, and its final page hands off.
                androidx.compose.foundation.pager.HorizontalPager(
                    state = deck,
                    userScrollEnabled = swipeUnlocked,
                    modifier = Modifier.weight(1f).testTag(DECK_STEP_TAG),
                    key = { it },
                ) { idx ->
                    val step = steps.getOrNull(idx)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .pageTurn(
                                offset = { pageOffsetFraction(deck.currentPage, idx, deck.currentPageOffsetFraction) },
                                strength = 0.7f,
                            )
                            .padding(horizontal = GapPage, vertical = GapGroup),
                    ) {
                        if (step != null) {
                            val vehicle = step.vin?.let { vin -> state.vehicles.firstOrNull { it.vin == vin } }
                            OnboardingStepCard(
                                spec = onboardingCardSpec(step.kind, vehicle?.name, newCar = mode is OnboardingMode.NewCars),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
                                    when (step.kind) {
                                        OnboardingStepKind.WELCOME -> OnboardingWelcomePage()
                                        OnboardingStepKind.RESTORE -> OnboardingRestorePage(vm)
                                        OnboardingStepKind.SETUP -> OnboardingSetupPage(vm, state, context, canBio, appearance.biometricLock, notifGranted) { notifGranted = it }
                                        OnboardingStepKind.LOOK -> OnboardingLookPage(appearance, vm)
                                        OnboardingStepKind.ALERTS -> OnboardingAlertsPage(notif, vm)
                                        OnboardingStepKind.WATCH -> OnboardingWatchPage()
                                        OnboardingStepKind.CAR_POWERTRAIN -> vehicle?.let { OnboardingPowertrainPage(it, state, vm) }
                                        OnboardingStepKind.CAR_PLATFORM -> vehicle?.let { OnboardingPlatformPage(it, state, vm) }
                                        OnboardingStepKind.CAR_CLIMATE -> vehicle?.let { OnboardingClimatePage(it, state, vm) }
                                        OnboardingStepKind.TIPS -> OnboardingTipsPage()
                                        OnboardingStepKind.FEATURES -> OnboardingFeaturesPage(state)
                                    }
                                }
                            }
                            // The swipe section's own cue, on its first page only.
                            if (idx == swipeStart) {
                                androidx.compose.material3.Text(
                                    "Swipe to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = GapGroup, top = GapGroup),
                                )
                            }
                        }
                    }
                }

                // The one way forward through the button section: progress and Back/Next on a
                // liquid-glass bar, which fluidly animates out as the swipe section begins.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !inSwipe,
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(MotionShort)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(MotionShort)) { it },
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(MotionShort)) +
                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(MotionShort)) { it },
                ) {
                    OnboardingBottomBar(
                        current = pageIndex,
                        total = steps.size,
                        accent = accent,
                        onBack = if (pageIndex > 0 || mode == OnboardingMode.Replay) ({ goBack() }) else null,
                        onNext = { goNext() },
                        nextLabel = when {
                            isLast && mode == OnboardingMode.Replay -> "Dismiss"
                            isLast && mode is OnboardingMode.NewCars -> "Done"
                            isLast -> "Enter Bloo"
                            firstRun && pageIndex == 0 -> "Get started"
                            else -> "Next"
                        },
                        nextIcon = if (isLast) AppIcons.CheckCircle else AppIcons.Check,
                        nextEnabled = blockedHere == null,
                        hint = blockedHere,
                        hazeState = backdropHaze,
                    )
                }
            }
        }
    }
}

/** Lets a UI test find the deck's card area (the pager's page content). */
internal const val DECK_STEP_TAG = "onboardingDeckStep"

private fun replayMode(mode: OnboardingMode) = mode == OnboardingMode.Replay

/** Index of the SETUP card in a first-run deck (welcome, restore, setup). */
private const val FIRST_RUN_SETUP_INDEX = 2
