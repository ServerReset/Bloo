package com.bloo.bluelink.ui

import com.bloo.uicommon.softSpring
import com.bloo.uicommon.lowPowerAwareSpring
import androidx.compose.foundation.layout.offset
import com.bloo.bluelink.data.StartupLabels
import com.bloo.bluelink.data.StartupTrace
import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bloo.uicommon.lowPowerBounce
import com.bloo.uicommon.lowPowerCalm
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.MotionShort
import com.bloo.uicommon.MotionMedium
import com.bloo.uicommon.LocalHaptics
import com.bloo.uicommon.FieldShape
import com.bloo.uicommon.Haptics
import com.bloo.uicommon.SoftDamping
import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Root composable for the phone app: a gradient backdrop, a [Scaffold] that switches top-level
 * screens via [AnimatedContent], and a biometric [LockOverlay] drawn last. State lives in [vm].
 */
@Composable
fun BlooApp(vm: AppViewModel) {
    // Cold-start instrumentation: "first composition" is the moment Compose starts running this
    // root, and the withFrameNanos below is the moment a frame is actually produced -- the pair
    // separates "compose is slow" from "the frame clock is late", which the frame monitor's own
    // numbers alone cannot distinguish.
    StartupTrace.once("compose-root", StartupLabels.BLOO_APP_FIRST_COMPOSITION)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        StartupTrace.once("compose-first-frame", StartupLabels.BLOO_APP_FIRST_FRAME_CALLBACK)
    }
    com.bloo.bluelink.perf.TrackRecompositions("BlooApp")
    val stateHolder = vm.state.collectAsStateWithLifecycle()
    val state by stateHolder
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val powerPolicy by com.bloo.bluelink.data.DeviceStatus.policy.collectAsStateWithLifecycle()
    // Narrow derived reads, so unrelated UiState emissions don't recompose the whole root.
    val screen by remember { derivedStateOf { state.screen } }
    // Frames are filed under the screen that is showing, so a log says "Garage" or "Settings", not just "slow".
    LaunchedEffect(screen) { com.bloo.bluelink.perf.PerfMonitor.screen(screen::class.simpleName ?: screen.toString()) }
    val locked by remember { derivedStateOf { state.locked } }
    // Latches once the app has been unlocked; only the cold-start lock defers composing the garage.
    var unlockedThisSession by remember { mutableStateOf(false) }
    // SideEffect, not a bare body write: this codebase's own convention (see BlooApp's haptics
    // write) -- mutating state during composition can be discarded or re-ordered, so it runs after
    // a successful (re)composition instead. Latching the instant a non-locked frame is composed is
    // what lets the garage start building on that very frame.
    SideEffect { if (!locked) unlockedThisSession = true }
    val loading by remember { derivedStateOf { state.loading } }
    val refreshing by remember { derivedStateOf { state.refreshing } }
    val message by remember { derivedStateOf { state.message } }
    val messageType by remember { derivedStateOf { state.messageType } }
    val addingAccount by remember { derivedStateOf { state.addingAccount } }
    // Adding an account shows the login form even while signed in. Above the Scaffold so the map overlay, drawn outside it, can read it too.
    val target = if (addingAccount) Screen.Login else screen
    val accounts by remember { derivedStateOf { state.accounts } }
    val kiaOtp by remember { derivedStateOf { state.kiaOtp } }
    val canadaOtp by remember { derivedStateOf { state.canadaOtp } }
    val onSettingsPageSlot by vm.onSettingsPageSlot.collectAsStateWithLifecycle()
    val mapExpanded by remember { derivedStateOf { state.mapExpanded } }
    // The search bar comes up from below the bottom edge, as it does when the app opens, whenever nothing hides it, and drops out
    // the same way when the lock or the full-screen map takes it away. It stays composed, parked off screen while hidden, so the rise
    // never pays for composing it.
    val searchHidden = locked || mapExpanded
    val searchRise = remember { Animatable(if (searchHidden) 1f else 0f) }
    // Built here, in composition: lowPowerAwareSpring is @Composable, so it cannot be read inside the coroutine below.
    // Calm rather than bouncy: the bar glides up and settles, instead of snapping up with an overshoot.
    val searchRiseSpec = lowPowerCalm<Float>(stiffness = Spring.StiffnessLow)
    LaunchedEffect(searchHidden) {
        // A lock hides the bar at once, under the blur. Everything else animates it.
        if (locked) {
            searchRise.snapTo(1f)
        } else {
            val target = if (searchHidden) 1f else 0f
            searchRise.animateTo(target, searchRiseSpec)
            // The spring stops a hair short of its target, and the offset below is that fraction of the whole screen: land on the
            // target exactly, so the bar rests where it belongs and not a pixel off it.
            searchRise.snapTo(target)
        }
    }
    val screenHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    // Derived too, so only a photo pick / welcome-card / install-guide change recomposes the root:
    // reading `state.x` directly here subscribed BlooApp to EVERY UiState emission.
    val photoPickRequest by remember { derivedStateOf { state.photoPickRequest } }
    val welcomeCardsOpen by remember { derivedStateOf { state.welcomeCardsOpen } }
    val showInstallGuide by remember { derivedStateOf { state.showInstallGuide } }
    // One blur source shared by GarageScreen, SettingsScreen and SearchLayer, which floats over
    // either.
    val searchHazeState = remember { HazeState() }
    // The expanded car's map. Held here, not in the garage, so the map can be drawn above the status bar's blur (see GarageMapOverlay).
    val expandedMap = remember { ExpandedMapState() }
    val backdropHaze = remember { HazeState() }
    // The one "is the user touching anything" clock the aurora reads; observed at the root below.
    val ambientActivity = remember { AmbientActivity() }
    val toasts = remember { ToastState() }
    // The one pull-to-refresh indicator; every Refreshable feeds it.
    val refreshIndicator = remember { RefreshIndicatorState() }
    val scope = rememberCoroutineScope()
    // LocalClipboard's set is suspend, so copies hop through `scope`.
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    // The phone's tilt, read once for the aurora and the pebbles' glass edges. Silent under battery saver, with animations off in
    // Accessibility, or once the app has sat untouched (see rememberTilt).
    val tilt = rememberTilt(active = powerPolicy.ambientMotion && !com.bloo.uicommon.reduceMotionCached(context), ambient = ambientActivity)

    // One haptics engine; written in a SideEffect because composition-time writes can be discarded.
    val haptics = remember { Haptics(context.applicationContext) }
    SideEffect { haptics.enabled = appearance.hapticsEnabled }

    // Loops a soft sweep while work is in flight; gated on STARTED so a backgrounded app stays
    // quiet.
    val busy by remember { derivedStateOf { state.loading || state.pending.isNotEmpty() } }
    // The compose-ui LocalLifecycleOwner is deprecated.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                haptics.loadingSweep()
                delay(560)
            }
        }
    }

    // Every message the ViewModels raise lands in the toast stack, carrying its own type so a later
    // message can never repaint an earlier one. clearMessage() right after keeps the single UiState
    // slot free for the next one, which stacks below instead of queueing behind.
    LaunchedEffect(message) {
        message?.let { msg ->
            toasts.show(msg, messageType)
            // The one arrival haptic: every message the app raises is felt, at the lightest weight.
            haptics.arrive()
            vm.clearMessage()
        }
    }

    // The one search anchor: the search element publishes its resting rect/dock here and the toasts
    // and page spacers read it. See SearchAnchor.kt.
    val searchAnchor = remember { SearchAnchor() }
    val dialogHost = remember { DialogHost(searchHazeState) }
    // Hardware-keyboard shortcuts (see KeyboardShortcuts.kt): the ones the root owns are heard here.
    var showShortcuts by remember { mutableStateOf(false) }
    val goBack = rememberBackAction()
    LaunchedEffect(vm) {
        vm.shortcutEvents.collect { event ->
            when (event) {
                Shortcut.Help -> showShortcuts = !showShortcuts
                Shortcut.Back -> if (showShortcuts) showShortcuts = false else goBack()
                else -> Unit
            }
        }
    }
    // The accessibility suite, resolved once here; everything below reads LocalAccessibility, not the stored switches.
    val a11y = rememberAccessibility(appearance, powerPolicy)
    CompositionLocalProvider(
        // Removes the default ripple app-wide (see NoTapHighlight). Material 3 components ignore
        // LocalIndication, so LocalRippleConfiguration = null turns off theirs.
        LocalIndication provides NoTapHighlight,
        LocalRippleConfiguration provides null,
        LocalSearchAnchor provides searchAnchor,
        LocalDialogHost provides dialogHost,
        LocalToasts provides toasts,
        LocalRefreshIndicator provides refreshIndicator,
        LocalBackdropHaze provides backdropHaze,
        LocalAmbientActivity provides ambientActivity,
        LocalPowerPolicy provides powerPolicy,
        LocalAccessibility provides a11y,
        LocalReduceMotion provides a11y.reduceMotion.value,
        LocalTilt provides tilt,
        LocalHaptics provides haptics,
        // Provided once so pebbles read LocalAppearance.current instead of each collecting.
        LocalAppearance provides appearance,
    ) {
    // Biometric lock overlay: blur the whole app behind it and fade the blur away once unlocked.
    // Hoisted into their own small composables below so only those tiny scopes recompose per frame;
    // everything else just gets redrawn under the blurred/faded layer.
    Box(
        Modifier.fillMaxSize().observeUserActivity(ambientActivity).appShortcuts(enabled = !locked && a11y.keyboardShortcuts.value, onShortcut = vm::onShortcut),
    ) {
    // "Content settled": the garage's first composition has finished. Gates two full-screen blurs
    // (lock blur, Aurora backdrop blur) that dominate cold-start frame cost.
    var contentSettled by remember { mutableStateOf(screen == Screen.Garage) }
    LaunchedEffect(screen) {
        if (screen == Screen.Garage) {
            // Two frames, not a wall-clock delay: on hardware the garage's first frame is a few
            // milliseconds, so the blur is back essentially immediately, while a device that needs
            // 2.5s for that frame (the software-rendered emulator) keeps the cheaper opaque
            // backdrop for exactly as long as it is struggling.
            withFrameNanos { }
            withFrameNanos { }
            contentSettled = true
        }
    }
    LockBlurLayer(locked = locked && contentSettled) {
    Box(Modifier.fillMaxSize()) {
    // The app's backdrop: the Haze source every glass card blurs (a sibling under everything).
    AppBackdrop(backdropHaze, Modifier.matchParentSize(), alsoInto = searchHazeState)
    Scaffold(
        containerColor = Color.Transparent,
        // Toasts render in ToastHost below, above search, not in the Scaffold's snackbar.
        snackbarHost = {},
    ) { padding ->
        // Hoisted above the screen switch: the aurora pauses while search is open.
        var searchOpen by remember { mutableStateOf(false) }
        // The search panel's IME animation freezes the tilt, sparing the redraws it would cause under the blur.
        SideEffect { tilt.paused = searchOpen }
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                // A real spring (this app's own SoftDamping/StiffnessMediumLow, the same feel the
                // garage's own expand/collapse AnimatedContent uses a few screens down) rather than
                // AnimatedContent's bare default -- the default spec is tuned for a small content
                // swap settling quickly, and on a screen-sized slide that read as slightly
                // clipped/mechanical next to every other full-screen motion in the app.
                // fadeIn/fadeOut keep their own (fast, linear-feeling) defaults on purpose: only
                // the SLIDE -- the part that actually travels screen-sized distance -- needed the
                // softer landing.
                val slideSpec = softSpring<IntOffset>(Spring.StiffnessMediumLow)
                (slideInHorizontally(slideSpec) { w -> -w } + fadeIn()) togetherWith
                    (slideOutHorizontally(slideSpec) { w -> w } + fadeOut())
            },
            label = "screen",
        ) { screen ->
            // The same page-turn, on whole-screen changes too: the leaving screen leans away and
            // dims, the arriving one leans back in. Read in the draw phase.
            val turn by transition.animateFloat(
                transitionSpec = { tween(MotionMedium) },
                label = "screenTurn",
            ) { state -> if (state == EnterExitState.Visible) 0f else 1f }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val away = turn
                    rotationY = away * 14f
                    val s = 1f - 0.05f * away
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.4f * away
                    translationY = away * 8.dp.toPx()
                    cameraDistance = 420.dp.toPx()
                }
            ) {
            // The garage draws full-bleed; other screens are inset by the Scaffold.
            when (screen) {
                // Bootstrapping placeholder (see Screen.Loading): same aurora + wordmark as Login,
                // nothing interactive.
                Screen.Loading -> {
                    StartupTrace.once("screen-loading", StartupLabels.SCREEN_LOADING_COMPOSED)
                    LoadingScreen(Modifier.padding(padding))
                }
                Screen.Login -> Box(Modifier.padding(padding)) {
                    StartupTrace.once("screen-login", StartupLabels.SCREEN_LOGIN_COMPOSED)
                    val loginUpdateAvailable by remember { derivedStateOf { stateHolder.value.updateAvailable } }
                    val loginUpdateChecking by remember { derivedStateOf { stateHolder.value.updateChecking } }
                    val bootReauth by remember { derivedStateOf { stateHolder.value.bootReauth } }
                    LoginScreen(
                        loading = loading,
                        onLogin = if (bootReauth) vm::reauthAfterRestart else vm::login,
                        onCancel = if (accounts.isNotEmpty()) ({ vm.cancelAddAccount() }) else null,
                        // Logged-out users have no Settings, so Login offers its own forced update
                        // check.
                        onCheckForUpdates = { vm.checkForUpdate(force = true, surfaceResult = true) },
                        updateChecking = loginUpdateChecking,
                        updateAvailableUrl = loginUpdateAvailable?.run?.htmlUrl,
                    )
                    kiaOtp?.let { otp -> KiaOtpDialog(otp, loading = loading, vm = vm) }
                    canadaOtp?.let { otp -> CanadaOtpDialog(otp, loading = loading, vm = vm) }
                }
                // One deck of cards for every setup flow: first run (restore, setup, look, cars),
                // and the cars detected later.
                Screen.Onboarding -> OnboardingScreen(vm)
                is Screen.CarSetup -> OnboardingScreen(vm, OnboardingMode.NewCars(screen.vins))
                Screen.Garage -> {
                    StartupTrace.once("screen-garage", StartupLabels.SCREEN_GARAGE_COMPOSED)
                    // Reuses the outer `appearance`.
                    val auroraTouch = remember { AuroraTouch() }
                    val auroraReactions = remember { AuroraReactions() }
                    Box(
                        Modifier.fillMaxSize().pointerInput(auroraTouch) {
                            // Watches the finger for the aurora without taking it: the Initial pass sees every touch
                            // first and nothing is consumed, so scrolling, swiping and taps behave exactly as before.
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = event.changes.firstOrNull { it.pressed }
                                    if (pressed != null) {
                                        // Only a real move is published: every write restarts the glow's follow spring,
                                        // and a panel delivers 120-240 move events a second.
                                        val last = auroraTouch.pos.value
                                        if (last == null || (pressed.position - last).getDistance() >= AuroraTouchStepPx) {
                                            auroraTouch.pos.value = pressed.position
                                        }
                                    } else {
                                        auroraTouch.pos.value = null
                                    }
                                }
                            }
                        },
                    ) {
                        // `paused = searchOpen`: the search panel sits ABOVE this background, and
                        // while it's up (typing frames, panel scrolling) the ambient drift would
                        // otherwise keep redrawing the blurred backdrop underneath at ~12fps --
                        // real contention on exactly the frames search is using.
                        // The aurora leans with the pager/scroll and springs on a page settle; both
                        // screens publish into this holder, read in the aurora's draw phase.
                        CompositionLocalProvider(LocalAuroraReactions provides auroraReactions) {
                        if (appearance.auroraBackground) CompositionLocalProvider(LocalAuroraTouch provides auroraTouch) {
                            AuroraBackground(Modifier.matchParentSize().hazeSource(backdropHaze).hazeSource(searchHazeState, zIndex = -1f), appearance, refreshing = refreshing, paused = searchOpen)
                        }
                        // Cold-start lock: don't compose (and blur) the full garage behind a lock
                        // the user hasn't passed; it composes once `locked` flips.
                        if (!(locked && !unlockedThisSession)) GarageScreen(stateHolder, vm, expandedMap, hazeState = searchHazeState)
                        }
                    }
                }
            }
            }
        }
        // Toasts pop up from BEHIND the search element, so they are drawn before it (and after the page).
        Box(Modifier.fillMaxSize()) {
            ToastHost(
                state = toasts,
                hazeState = searchHazeState,
                onCopy = { text ->
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("bloo", text))) }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        // Search lives above the AnimatedContent so one element survives the transition (corner
        // bubble morphing into the settings pill). Only the garage is searchable.
        val searchable = target == Screen.Garage
        val notifPrefs by vm.notifications.collectAsStateWithLifecycle()
        // On the Settings pager page search is always shown (how you find a setting);
        // onSettingsPageSlot tracks it.
        val effectivelyInSettings = onSettingsPageSlot
        // Hidden while the map overlay is expanded: its bottom action row occupies the same corner.
        if (searchable) {
            // No `.padding(padding)`: SearchLayer reads WindowInsets itself, and padding here would
            // subtract the nav bar twice. The rise is a layout offset, not a graphicsLayer translation: the glass samples the
            // backdrop by layout position, so a translated layer drew its blur a few pixels off from the content it sits behind.
            Box(Modifier.fillMaxSize().offset { IntOffset(0, (searchRise.value * screenHeightPx).roundToInt()) }) {
                SearchLayer(
                    vm = vm,
                    state = stateHolder,
                    appearance = appearance,
                    notif = notifPrefs,
                    onSettings = effectivelyInSettings,
                    onOpenChanged = { searchOpen = it },
                    hazeState = searchHazeState,
                )
            }
        }
    }
    // The expanded car's map sits over the app and under the status bar's blur, so the part of it that goes behind the status bar is
    // blurred rather than cut off at the content's edge. The map is drawn here, at screen level, for that reason.
    if (target == Screen.Garage && !(locked && !unlockedThisSession)) GarageMapOverlay(stateHolder, vm, expandedMap, searchHazeState)
    // The status bar's own strip: a blur of what is behind it, strongest at the top and nearly gone
    // by the bottom of the strip. It is the ONLY edge treatment in the app.
    StatusBarScrim(searchHazeState)
    // The app-wide pull-to-refresh disc, drawn above content; refracts searchHazeState. The lock overlay
    // and dialogs sit above it.
    PullRefreshIndicatorHost(state = refreshIndicator, hazeState = searchHazeState)
    }
    }
        // Biometric lock overlay, drawn over the blurred app; fades out on unlock.
        LockAlphaOverlay(locked = locked, vm = vm, opaqueBackdrop = !contentSettled)

    // Choosing and cropping a car photo, for whichever page (or request) asked for one.
    CarPhotoPickerHost(vm, photoPickRequest)

    // A picture or a settings file shared into the app from the share sheet.
    ShareIntakeHost(vm, stateHolder)

    // The welcome cards, when summoned from Settings: over the app, dismissed back to it.
    androidx.compose.animation.AnimatedVisibility(
        visible = welcomeCardsOpen,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(MotionMedium)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(MotionShort)),
    ) {
        Box(Modifier.fillMaxSize()) { OnboardingScreen(vm, OnboardingMode.Replay) }
    }
    // The pre-install guide: shown before the system installer runs when Shizuku is off, so the
    // Play-Protect steps are seen before the sheet appears rather than after it looks broken.
    if (showInstallGuide) {
        UpdateInstallGuideDialog(
            onDismiss = { vm.dismissInstallGuide() },
            onContinue = { vm.confirmInstallUpdate() },
        )
    }
    // The migration bridge's notice used to live here. It, and the whole dev-key signing path that
    // produced the build it shipped in, is gone -- that build is published and immutable, and no
    // build from this repo may be signed with the development key again. What reaches installs on
    // the old key now is the release text itself: see the title and changelog steps in
    // .github/workflows/android.yml.
    if (showShortcuts) ShortcutsDialog(onDismiss = { showShortcuts = false })
    // Every open dialog, above everything else.
    DialogLayer(dialogHost)
    }
    }
}

// Owns the lock-blur animation in its own small recompose scope so animating it doesn't invalidate
// all of BlooApp (see BlooApp's call site comment).

// Owns the lock-overlay fade animation in its own small recompose scope, for the same reason as
// [LockBlurLayer].

internal val FieldShape: RoundedCornerShape
    get() = com.bloo.uicommon.FieldShape

// --- Garage (main) --------------------------------------------------------

/** Window width from which a car reflows to its OWN two columns (hero + pebbles side by side). */
internal const val TWO_COLUMN_MIN_DP = 600

/** Window width from which the garage shows two car columns side by side. */
internal const val THREE_COLUMN_MIN_DP = 900

/** Window width from which the garage shows four car columns side by side: a desktop or ChromeOS window, an unfolded tablet in landscape. */
internal const val FOUR_COLUMN_MIN_DP = 1500

/**
 * How many car columns a window [widthDp] wide gets side by side: 1 on a phone, 2 on a foldable or
 * small tablet, 3 on a wide tablet or desktop. This is the GRID -- multiple cars visible at once.
 * Separately, each individual car reflows to its own two columns only when its column is wide enough
 * (GarageScreen's `twoColumn`), so a lone car on a wide screen gets the hero-beside-pebbles layout.
 * (An earlier take collapsed this to `width / 600`, which showed one wide car instead of the grid --
 * wrong: a large screen wants MORE columns, not fewer.)
 */

internal fun carColumnsFor(widthDp: Float): Int = when {
    widthDp >= FOUR_COLUMN_MIN_DP -> 4
    widthDp >= THREE_COLUMN_MIN_DP -> 3
    widthDp >= TWO_COLUMN_MIN_DP -> 2
    else -> 1
}

/** How far (px) a finger must move before the aurora's glow follows it again. */
private const val AuroraTouchStepPx = 18f
