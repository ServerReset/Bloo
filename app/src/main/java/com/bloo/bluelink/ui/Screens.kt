package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.ClipData
import androidx.compose.runtime.withFrameNanos

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
    com.bloo.bluelink.data.StartupTrace.once("compose-root", "BlooApp: first composition")
    LaunchedEffect(Unit) {
        withFrameNanos { }
        com.bloo.bluelink.data.StartupTrace.once("compose-first-frame", "BlooApp: first frame callback")
    }
    val stateHolder = vm.state.collectAsStateWithLifecycle()
    val state by stateHolder
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Narrow derived reads, so unrelated UiState emissions don't recompose the whole root.
    val screen by remember { derivedStateOf { state.screen } }
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
    val accounts by remember { derivedStateOf { state.accounts } }
    val kiaOtp by remember { derivedStateOf { state.kiaOtp } }
    val canadaOtp by remember { derivedStateOf { state.canadaOtp } }
    val onSettingsPageSlot by remember { derivedStateOf { state.onSettingsPageSlot } }
    val mapExpanded by remember { derivedStateOf { state.mapExpanded } }
    // One blur source shared by GarageScreen, SettingsScreen and SearchLayer, which floats over
    // either.
    val searchHazeState = remember { HazeState() }
    val backdropHaze = remember { HazeState() }
    val toasts = remember { ToastState() }
    // The one pull-to-refresh indicator; every Refreshable feeds it.
    val refreshIndicator = remember { RefreshIndicatorState() }
    val scope = rememberCoroutineScope()
    // LocalClipboard's set is suspend, so copies hop through `scope`.
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    // One haptics engine; written in a SideEffect because composition-time writes can be discarded.
    val haptics = remember { Haptics(context.applicationContext) }
    SideEffect { haptics.enabled = appearance.hapticsEnabled }

    // Loops a soft sweep while work is in flight; gated on STARTED so a backgrounded app stays
    // quiet.
    val busy by remember { derivedStateOf { state.loading || state.pending.isNotEmpty() } }
    // The compose-ui LocalLifecycleOwner is deprecated.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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
            vm.clearMessage()
        }
    }

    // The one search anchor: the search element publishes its resting rect/dock here and the toasts
    // and page spacers read it. See SearchAnchor.kt.
    val searchAnchor = remember { SearchAnchor() }
    val dialogHost = remember { DialogHost(searchHazeState) }
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
        LocalHaptics provides haptics,
        // Provided once so pebbles read LocalAppearance.current instead of each collecting.
        LocalAppearance provides appearance,
    ) {
    // A soft full-bleed gradient behind the transparent system bars.
    val scheme = MaterialTheme.colorScheme
    // Biometric lock overlay: blur the whole app behind it and fade the blur away once unlocked.
    // Hoisted into their own small composables below so only those tiny scopes recompose per frame;
    // everything else just gets redrawn under the blurred/faded layer.
    Box(Modifier.fillMaxSize()) {
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
    // The app's backdrop: a Haze source every glass card blurs. A sibling under everything, since a
    // card can't blur its own parent.
    Box(
        Modifier
            .matchParentSize()
            .hazeSource(backdropHaze)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerHigh,
                        scheme.surface,
                        scheme.surfaceContainerLow,
                    ),
                ),
            )
            // Ambient colour under the glass: two soft glows of the theme's own colours, so cards
            // have something to blur and refract even when the aurora is off.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(scheme.primary.copy(alpha = 0.22f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.2f),
                        radius = size.width * 0.9f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(scheme.tertiary.copy(alpha = 0.18f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.75f),
                        radius = size.width * 0.9f,
                    ),
                )
            },
    )
    Scaffold(
        containerColor = Color.Transparent,
        // Toasts render in ToastHost below, above search, not in the Scaffold's snackbar.
        snackbarHost = {},
    ) { padding ->
        // Adding an account shows the login form even while signed in.
        val target = if (addingAccount) Screen.Login else screen
        // Hoisted above the screen switch: the aurora pauses while search is open.
        var searchOpen by remember { mutableStateOf(false) }
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
                val slideSpec = spring<IntOffset>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
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
                },
            ) {
            // The garage draws full-bleed; other screens are inset by the Scaffold.
            when (screen) {
                // Bootstrapping placeholder (see Screen.Loading): same aurora + wordmark as Login,
                // nothing interactive.
                Screen.Loading -> {
                    com.bloo.bluelink.data.StartupTrace.once("screen-loading", "screen: Loading composed")
                    LoadingScreen(Modifier.padding(padding))
                }
                Screen.Login -> Box(Modifier.padding(padding)) {
                    com.bloo.bluelink.data.StartupTrace.once("screen-login", "screen: Login composed")
                    val loginUpdateAvailable by remember { derivedStateOf { stateHolder.value.updateAvailable } }
                    val loginUpdateChecking by remember { derivedStateOf { stateHolder.value.updateChecking } }
                    LoginScreen(
                        loading = loading,
                        onLogin = vm::login,
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
                    com.bloo.bluelink.data.StartupTrace.once("screen-garage", "screen: Garage composed (first garage frame next)")
                    // Reuses the outer `appearance`.
                    Box(Modifier.fillMaxSize()) {
                        // `paused = searchOpen`: the search panel sits ABOVE this background, and
                        // while it's up (typing frames, panel scrolling) the ambient drift would
                        // otherwise keep redrawing the blurred backdrop underneath at ~12fps --
                        // real contention on exactly the frames search is using.
                        if (appearance.auroraBackground) AuroraBackground(Modifier.matchParentSize().hazeSource(backdropHaze), appearance, refreshing = refreshing, paused = searchOpen)
                        // Cold-start lock: don't compose (and blur) the full garage behind a lock
                        // the user hasn't passed; it composes once `locked` flips.
                        if (!(locked && !unlockedThisSession)) {
                            GarageScreen(stateHolder, vm, hazeState = searchHazeState)
                        }
                    }
                }
            }
            }
        }
        // Search lives above the AnimatedContent so one element survives the transition (corner
        // bubble morphing into the settings pill). Only the garage is searchable.
        val searchable = target == Screen.Garage
        val notifPrefs by vm.notifications.collectAsStateWithLifecycle()
        // On the Settings pager page search is always shown (how you find a setting);
        // onSettingsPageSlot tracks it.
        val effectivelyInSettings = onSettingsPageSlot
        // Hidden while the map overlay is expanded: its bottom action row occupies the same corner.
        if (searchable && !locked && !mapExpanded) {
            // No `.padding(padding)`: SearchLayer reads WindowInsets itself, and padding here would
            // subtract the nav bar twice.
            Box(Modifier.fillMaxSize()) {
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
    // Toasts sit inside the outer Box after SearchLayer so they stack above search; the lock
    // overlay and dialogs sit above them. The app-wide pull-to-refresh disc, drawn above content
    // and below toasts/dialogs; refracts searchHazeState.
    PullRefreshIndicatorHost(
        state = refreshIndicator,
        hazeState = searchHazeState,
    )
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
    }
    }
        // Biometric lock overlay, drawn over the blurred app; fades out on unlock.
        LockAlphaOverlay(locked = locked, vm = vm, opaqueBackdrop = !contentSettled)
    
    // The welcome cards, when summoned from Settings: over the app, dismissed back to it.
    if (state.welcomeCardsOpen) {
        Box(Modifier.fillMaxSize()) { OnboardingScreen(vm, OnboardingMode.Replay) }
    }
    // The pre-install guide: shown before the system installer runs when Shizuku is off, so the
    // Play-Protect steps are seen before the sheet appears rather than after it looks broken.
    if (state.showInstallGuide) {
        UpdateInstallGuideDialog(
            onDismiss = { vm.dismissInstallGuide() },
            onContinue = { vm.confirmInstallUpdate() },
        )
    }
    // Every open dialog, above everything else.
    DialogLayer(dialogHost)
    }
    }

}

// Owns the lock-blur animation in its own small recompose scope so animating it doesn't invalidate
// all of BlooApp (see BlooApp's call site comment).

// Owns the lock-overlay fade animation in its own small recompose scope, for the same reason as
// [LockBlurLayer].

internal val FieldShape: androidx.compose.foundation.shape.RoundedCornerShape
    get() = com.bloo.uicommon.FieldShape

// --- Garage (main) --------------------------------------------------------

/** Window width from which the garage shows two car columns side by side. */
internal const val TWO_COLUMN_MIN_DP = 600

/** Window width from which it shows three -- tablets, desktop windows; never more than this. */
internal const val THREE_COLUMN_MIN_DP = 900

/**
 * How many car columns a window [widthDp] wide gets: 1 on a phone, 2 on a foldable or small tablet,
 * 3 on a wide tablet or desktop. The caller still caps it at the number of cars.
 */
internal fun carColumnsFor(widthDp: Float): Int = when {
    widthDp >= THREE_COLUMN_MIN_DP -> 3
    widthDp >= TWO_COLUMN_MIN_DP -> 2
    else -> 1
}
