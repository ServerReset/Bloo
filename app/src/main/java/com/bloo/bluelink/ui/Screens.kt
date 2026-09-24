@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import android.content.ClipData
import androidx.compose.runtime.withFrameNanos


/**
 * Root composable for the whole phone app. Owns nothing itself beyond a
 * snackbar host and a haptics engine -- all real state lives in [vm] and is
 * collected here as Compose state so this function (and everything below it)
 * recomposes whenever [AppViewModel.state] or [AppViewModel.appearance] emits.
 *
 * Structure, outside-in:
 *  - A [CompositionLocalProvider] makes the shared [Haptics] instance
 *    available to every descendant via [LocalHaptics].
 *  - A full-bleed vertical gradient paints behind the transparent system
 *    bars (edge-to-edge), inside a [Box] that can be blurred as a unit.
 *  - A [Scaffold] hosts the snackbar and, via [AnimatedContent] keyed on the
 *    current [Screen], cross-fades/slides between the Login, Empty,
 *    Onboarding, CarSetup, Garage, and Settings top-level screens.
 *  - A biometric lock overlay ([LockOverlay]) is drawn last, on top of
 *    everything, and blurs+dims the content behind it while [state.locked]
 *    is true.
 */
@Composable
fun BlooApp(vm: AppViewModel) {
    // Cold-start instrumentation: "first composition" is the moment Compose starts
    // running this root, and the withFrameNanos below is the moment a frame is actually
    // produced -- the pair separates "compose is slow" from "the frame clock is late",
    // which the frame monitor's own numbers alone cannot distinguish.
    com.bloo.bluelink.data.StartupTrace.once("compose-root", "BlooApp: first composition")
    LaunchedEffect(Unit) {
        withFrameNanos { }
        com.bloo.bluelink.data.StartupTrace.once("compose-first-frame", "BlooApp: first frame callback")
    }
    val stateHolder = vm.state.collectAsStateWithLifecycle()
    val state by stateHolder
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    // Narrow derived reads of the collected state.
    //
    // Reading these fields straight off `state` in this body subscribed the WHOLE app root --
    // the Scaffold, the screen dispatch, every child -- to every UiState emission: a status
    // poll for one car, a location tick, an AI probe, a weather refresh. Each of these values
    // changes far less often than the state object does, and a derived state only invalidates
    // its reader when the value really changed, so an unrelated emission no longer recomposes
    // the root (and, through it, the screen it is showing).
    val screen by remember { derivedStateOf { state.screen } }
    val locked by remember { derivedStateOf { state.locked } }
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
    // Shared by GarageScreen, SettingsScreen and SearchLayer below -- see either
    // screen's own `hazeState` parameter doc for why: SearchLayer floats above
    // whichever of the two is actually showing, so its own glass fill needs ONE
    // real blur source that works no matter which screen that turns out to be,
    // instead of each screen's own previously-private, unshared HazeState leaving
    // search with nothing to blur regardless of which one was on screen.
    val searchHazeState = remember { HazeState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // LocalClipboard (the non-deprecated spelling): its set API is SUSPEND, so
    // the copy call below hops through this screen's existing `scope` rather
    // than the old manager's synchronous setText.
    val clipboard = LocalClipboard.current
    val context = LocalContext.current

    // One haptics engine for the whole app; its enabled flag tracks the setting.
    // Written in a SideEffect{} rather than inline: mutating shared state during
    // composition is a Compose anti-pattern (the write can be discarded if the
    // composition is abandoned, and it isn't ordered relative to effects) --
    // SideEffect runs it after every successful (re)composition.
    val haptics = remember { Haptics(context.applicationContext) }
    SideEffect { haptics.enabled = appearance.hapticsEnabled }

    // While a command is in flight (or the garage is loading), loop a soft
    // left-to-right sweep so progress is felt until it completes. The effect is
    // keyed on `busy`, so it cancels as soon as work finishes. Gated on the
    // STARTED lifecycle state: a backgrounded Activity keeps its composition
    // (and its LaunchedEffects) alive, so without the gate a slow command kept
    // vibrating the phone in the user's pocket after they switched apps.
    val busy by remember { derivedStateOf { state.loading || state.pending.isNotEmpty() } }
    // androidx.lifecycle.compose.LocalLifecycleOwner -- the compose-ui platform
    // spelling is deprecated and slated for removal.
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

    // The snackbar's colour is driven by the message TYPE, but clearMessage()
    // (called right after showing) resets messageType back to "error" for the
    // next message, so the host can't read state.messageType at render time —
    // it would always paint red.
    //
    // A single captured `shownMessageType` variable didn't work either:
    // showSnackbar serialises on an internal mutex, so a second message queues
    // behind the first for up to ~4s while a shared variable is overwritten the
    // moment it's queued — the first snackbar recomposed into the SECOND one's
    // colour while still on screen (a failed refresh turning blue mid-display as
    // the update check's info message queued behind it). The type has to travel
    // WITH its own message, so it rides in custom visuals the host reads back.
    LaunchedEffect(message) {
        message?.let { msg ->
            val visuals = BlooSnackbarVisuals(msg, messageType)
            scope.launch { snackbar.showSnackbar(visuals) }
            vm.clearMessage()
        }
    }

    // One floating registry for the whole app: every floating element (corner chrome, search,
    // the refresh indicator) publishes its bounds here and asks here who else is in the way,
    // instead of screens hand-threading each other's positions around. See FloatingSystem.kt.
    val floatingRegistry = remember { FloatingRegistry() }
    CompositionLocalProvider(
        // Kills Android's default ripple app-wide -- see NoTapHighlight for why this app
        // answers a press with its own motion instead of a borrowed grey fill.
        //
        // Both halves are needed. LocalIndication covers everything that resolves indication
        // the ordinary way: every plain Modifier.clickable, and MorphButtonCore (the component
        // behind essentially every button here), which takes indication = LocalIndication.current.
        // Material 3's own components do NOT read it -- Surface(onClick), IconButton, Switch and
        // Card each construct their own ripple internally -- and LocalRippleConfiguration set to
        // null is the supported way to turn those off, without rewriting each component and
        // inheriting its layout quirks.
        LocalIndication provides NoTapHighlight,
        LocalRippleConfiguration provides null,
        LocalFloatingRegistry provides floatingRegistry,
        LocalHaptics provides haptics,
        // Provided once here (the app root already collects `appearance` above) so
        // every pebble/tile reads LocalAppearance.current instead of opening its own
        // collectAsStateWithLifecycle() collector — see LocalAppearance.
        LocalAppearance provides appearance,
    ) {
    // Edge-to-edge: a soft full-bleed gradient paints behind the transparent
    // status/navigation bars; screen content draws on top of it.
    val scheme = MaterialTheme.colorScheme
    // Biometric lock overlay: blur the whole app behind it and fade the blur
    // away once unlocked. Both animated values used to be read directly here
    // in BlooApp's own body (`by animateDpAsState`/`animateFloatAsState`),
    // which subscribed BlooApp's entire recompose scope -- the Scaffold, the
    // whole NavHost of every screen, the SearchLayer -- to every one of the
    // ~27 frames of each 450ms lock/unlock transition. Hoisted into their own
    // small composables below so only those tiny scopes recompose per frame;
    // everything else just gets redrawn under the blurred/faded layer.
    Box(Modifier.fillMaxSize()) {
    // "Content settled": true once the screen has actually become the garage AND its first
    // composition has had a moment to finish. Two full-screen blurs are gated on it, because
    // both re-rasterize the whole view tree and both were measured as the dominant cost of
    // the cold-start frames on the API 34 emulator:
    //   - the lock screen's 22dp blur over the entire app (2.3s frame with it, 1.33s
    //     without), and
    //   - Aurora's own 44dp backdrop blur, which the drift redraws every 80ms.
    // Neither is visible as a change: the lock screen paints its own scrim, and a frozen
    // backdrop is indistinguishable for the ~1s this window lasts on hardware (~0 on a fast
    // device, where the garage's first frame is a few ms).
    var contentSettled by remember { mutableStateOf(screen == Screen.Garage) }
    LaunchedEffect(screen) {
        if (screen == Screen.Garage) {
            // Two frames, not a wall-clock delay: on hardware the garage's first frame is a
            // few milliseconds, so the blur is back essentially immediately, while a device
            // that needs 2.5s for that frame (the software-rendered emulator) keeps the
            // cheaper opaque backdrop for exactly as long as it is struggling.
            withFrameNanos { }
            withFrameNanos { }
            contentSettled = true
        }
    }
    LockBlurLayer(locked = locked && contentSettled) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerHigh,
                        scheme.surface,
                        scheme.surfaceContainerLow,
                    ),
                ),
            ),
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = {
            SnackbarHost(snackbar, modifier = Modifier.imePadding()) { data ->
                // Two-part state, not one Animatable driven by snapTo: a live drag used to
                // launch a brand-new coroutine PER drag delta (`swipeScope.launch { offsetX.snapTo(...) }`),
                // each one entering Animatable's MutatorMutex separately -- at a touch-move's
                // frame rate that's dozens of allocated coroutines a second fighting the same
                // mutex, which is exactly the kind of stutter "swipe feels jittery" describes.
                // `dragOffsetPx` is a plain float written directly and synchronously from the
                // gesture callback -- follows the finger 1:1 with zero coroutine overhead. The
                // Animatable is reserved for what actually needs animating: springing back to 0
                // or flying off-screen once the finger lifts, started with exactly one launch per
                // gesture instead of one per pixel.
                var isDragging by remember(data) { mutableStateOf(false) }
                val dragOffsetPx = remember(data) { mutableFloatStateOf(0f) }
                val settleOffsetX = remember(data) { Animatable(0f) }
                val swipeScope = rememberCoroutineScope()
                val dismissPx = with(LocalDensity.current) { 110.dp.toPx() }
                // Read off THIS snackbar's own visuals, so a message queued behind
                // it can't repaint it — see the LaunchedEffect that shows them.
                val snackColors = when ((data.visuals as? BlooSnackbarVisuals)?.type) {
                    "success" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    "info" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                }
                val snackShape = RoundedCornerShape(24.dp)
                // GlassSurface, not a plain solid Surface -- this was the one floating
                // element in the app still using a flat opaque fill instead of the shared
                // blur/tint every other piece of chrome (dialogs, the search bar, floating
                // buttons) uses. searchHazeState is the same HazeState the screen behind
                // this snackbar already renders into (see this file's own `hazeSource`
                // wiring), so the blur is real, not a guess at a color. Alpha stays fairly
                // high even with real blur behind it -- unlike ambient chrome, a toast is
                // reporting something that just happened and needs to read clearly the
                // instant it appears, not fade into whatever's behind it.
                GlassSurface(
                    shape = snackShape,
                    hazeState = searchHazeState,
                    tint = snackColors.first.copy(alpha = if (canBlurBackdrops()) 0.75f else 0.94f),
                    contentColor = snackColors.second,
                    modifier = Modifier
                        .padding(16.dp)
                        // This is a hand-rolled Surface, not M3's own Snackbar()
                        // composable (which sets live-region semantics
                        // internally) -- without this, a command result / sync
                        // completion / error appears visually but TalkBack
                        // never proactively announces it; a screen-reader user
                        // has to blindly swipe around after every action to
                        // discover whether it worked.
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        // Read inside the placement/draw-phase lambdas, not hoisted to a val above
                        // -- that keeps a live drag to a layout/draw re-run per frame instead of a
                        // full recomposition of this snackbar (same convention as GarageScreen's
                        // pull-to-refresh offsets; see its own doc on why the hoisted read is the
                        // expensive version).
                        .offset {
                            val x = if (isDragging) dragOffsetPx.floatValue else settleOffsetX.value
                            IntOffset(x.roundToInt(), 0)
                        }
                        .graphicsLayer {
                            val x = if (isDragging) dragOffsetPx.floatValue else settleOffsetX.value
                            alpha = (1f - abs(x) / (dismissPx * 2.2f)).coerceIn(0f, 1f)
                        }
                        .pointerInput(data) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragOffsetPx.floatValue = settleOffsetX.value
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx.floatValue += dragAmount
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val released = dragOffsetPx.floatValue
                                    swipeScope.launch {
                                        settleOffsetX.snapTo(released)
                                        if (abs(released) > dismissPx) {
                                            val target = if (released > 0) dismissPx * 4 else -dismissPx * 4
                                            settleOffsetX.animateTo(target)
                                            data.dismiss()
                                        } else {
                                            settleOffsetX.animateTo(0f)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    val released = dragOffsetPx.floatValue
                                    swipeScope.launch {
                                        settleOffsetX.snapTo(released)
                                        settleOffsetX.animateTo(0f)
                                    }
                                },
                            )
                        },
                ) {
                    Row(
                        Modifier.padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        SelectionContainer(Modifier.weight(1f)) {
                            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        // NOT a group, deliberately. Two icon buttons cannot donate width to
                        // each other: a donor's floor is its own minIntrinsicWidth, and an
                        // IconButton's intrinsic width IS its fixed 48dp target, so capacity is
                        // zero and a group here would add layout nodes for no motion. (Joining
                        // a group is still right for an icon button sitting among LABELLED
                        // ones, where it can grow and they can give -- which is why
                        // MorphIconButton does it automatically.) These two keep the plain Row
                        // and the press scale MorphButtonCore already draws.
                        MorphIconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("bloo", data.visuals.message)))
                            }
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }
                        // Swipe-to-dismiss is a raw drag gesture with no
                        // TalkBack equivalent (a single-finger swipe here is
                        // captured by TalkBack's own navigation instead), so a
                        // screen-reader user previously had no way to dismiss
                        // early and had to wait out the auto-hide timeout.
                        MorphIconButton(onClick = { data.dismiss() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Adding an account shows the login form even while already signed in.
        val target = if (addingAccount) Screen.Login else screen
        // Shared with the search layer and the garage aurora: while the search
        // panel is open the blurred aurora beneath it pauses (see
        // AuroraBackground's `paused`), so typing/panel frames don't contend
        // with a full-screen blur redraw. Hoisted here, above the screen
        // switch, because BOTH the per-screen background and the search layer
        // (which sits above every screen) read it.
        var searchOpen by remember { mutableStateOf(false) }
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                // A real spring (this app's own SoftDamping/StiffnessMediumLow, the
                // same feel the garage's own expand/collapse AnimatedContent uses a
                // few screens down) rather than AnimatedContent's bare default -- the
                // default spec is tuned for a small content swap settling quickly,
                // and on a screen-sized slide that read as slightly clipped/mechanical
                // next to every other full-screen motion in the app. fadeIn/fadeOut
                // keep their own (fast, linear-feeling) defaults on purpose: only the
                // SLIDE -- the part that actually travels screen-sized distance --
                // needed the softer landing. Settings used to be one of these targets
                // (reached only from the no-vehicles screen) with its own slide
                // direction; it's a page in the garage's own pager now, for every
                // vehicle count, so there's nothing left here to give a special sign.
                val slideSpec = spring<IntOffset>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                (slideInHorizontally(slideSpec) { w -> -w } + fadeIn()) togetherWith
                    (slideOutHorizontally(slideSpec) { w -> w } + fadeOut())
            },
            label = "screen",
        ) { screen ->
            // The garage draws full-bleed (content scrolls behind the bars and
            // handles its own insets); other screens stay inset by the Scaffold.
            when (screen) {
                // Bootstrapping only -- see Screen.Loading's own doc
                // (AppViewModel.kt) for why this exists at all. The SAME
                // AuroraBackground + wordmark LoginScreen opens with (so
                // there's nothing to visually reconcile if this resolves to
                // Login next -- same background, same brand mark, already
                // mid-fade), but with no form, no fields, nothing interactive
                // -- this is a "we haven't decided what screen you need yet"
                // placeholder, not a real destination, and it has to stay
                // cheap: AuroraBackground is already exactly what the FIRST
                // frame of a cold start painted before this screen existed
                // (LoginScreen used it too), so this is strictly less work
                // than before, not more.
                Screen.Loading -> {
                    com.bloo.bluelink.data.StartupTrace.once("screen-loading", "screen: Loading composed")
                    LoadingScreen(Modifier.padding(padding))
                }
                Screen.Login -> Box(Modifier.padding(padding)) {
                    com.bloo.bluelink.data.StartupTrace.once("screen-login", "screen: Login composed")
                    LoginScreen(
                        loading = loading,
                        onLogin = vm::login,
                        onCancel = if (accounts.isNotEmpty()) ({ vm.cancelAddAccount() }) else null,
                    )
                    kiaOtp?.let { otp -> KiaOtpDialog(otp, loading = loading, vm = vm) }
                    canadaOtp?.let { otp -> CanadaOtpDialog(otp, loading = loading, vm = vm) }
                }
                // Shown once, right after sign-in resolves at least one vehicle for
                // a first-run device -- before Onboarding -- so a second phone (or
                // reinstall) never has to click through the whole welcome wizard
                // just to find the "Sync across devices" card buried in its SETUP
                // step. See Screen.SyncChoice's own doc for exactly what each choice
                // leads to.
                Screen.SyncChoice -> SyncChoiceScreen(vm)
                Screen.Onboarding -> OnboardingScreen(vm)
                is Screen.CarSetup -> CarSetupWizardScreen(vm, screen.vins)
                Screen.Garage -> {
                    com.bloo.bluelink.data.StartupTrace.once("screen-garage", "screen: Garage composed (first garage frame next)")
                    // Reuses the outer `appearance` (already collected once above
                    // for the CompositionLocalProvider) instead of re-subscribing
                    // to the same StateFlow a second time here.
                    Box(Modifier.fillMaxSize()) {
                        // `paused = searchOpen`: the search panel sits ABOVE this
                        // background, and while it's up (typing frames, panel
                        // scrolling) the ambient drift would otherwise keep
                        // redrawing the blurred backdrop underneath at ~12fps --
                        // real contention on exactly the frames search is using.
                        if (appearance.auroraBackground) AuroraBackground(Modifier.matchParentSize(), appearance, refreshing = refreshing, paused = searchOpen)
                        GarageScreen(stateHolder, vm, hazeState = searchHazeState)
                    }
                }
            }
        }
        // Search lives HERE, above the screen-switching AnimatedContent and
        // outside it, which is the whole point: one element that survives the
        // transition, so garage -> Settings genuinely morphs a corner bubble
        // into the bottom bar instead of cross-fading two different objects
        // that happen to look alike. Only the one screen that has anything to
        // search; login, onboarding and the setup wizard don't. Settings is
        // always a page inside THIS screen's own pager now (no standalone
        // route any more, for any vehicle count), so it doesn't need a
        // separate entry here.
        val searchable = target == Screen.Garage
        val cover = isCompactCoverScreen()
        val notifPrefs by vm.notifications.collectAsStateWithLifecycle()
        // On the garage (and the cover) it is the user's switch. On the Settings
        // PAGE of that same pager it is always there -- that is how you find a
        // setting. state.onSettingsPageSlot (kept in sync by the pager's own
        // settle effect) is the one signal for that now: without it, swiping to
        // the Settings page fell back to the ordinary garage-screen showSearch
        // preference (search could disappear entirely there for anyone with
        // that off) and the search element itself stayed shaped like a garage
        // "bubble" instead of morphing into the settings "pill".
        val effectivelyInSettings = onSettingsPageSlot
        // !mapExpanded: a car's full-screen map overlay has its own bottom action row
        // (Recentre/Open in Maps) sitting in the same corner the floating search bubble
        // does -- the two overlapped and clipped into each other, reported directly from
        // a screenshot. See UiState.mapExpanded's own doc.
        if (searchable && !locked && !mapExpanded && (appearance.showSearch || effectivelyInSettings)) {
            // fillMaxSize() alone, no `.padding(padding)` -- SearchLayer already
            // reads WindowInsets itself for every edge it cares about (its own
            // `bottomInset`, `insetTopDp` for the compact docked band), the same
            // "edge-to-edge, self-managed insets" pattern the Garage/Settings
            // screens right above already use with no `.padding(padding)` of
            // their own either. Applying the Scaffold's own default
            // `contentWindowInsets` (WindowInsets.systemBars) HERE as well meant
            // this Box's own measured height was already shrunk by the
            // navigation bar before SearchLayer's BoxWithConstraints ever saw
            // it, and SearchLayer's own `bottomInset` then subtracted that same
            // navigation-bar height a SECOND time computing where "the bottom"
            // is -- reported directly as the search bubble sitting noticeably
            // higher than its own bottom-anchored formula should ever place it.
            Box(Modifier.fillMaxSize()) {
                SearchLayer(
                    vm = vm,
                    state = stateHolder,
                    appearance = appearance,
                    notif = notifPrefs,
                    onSettings = effectivelyInSettings && !cover,
                    compact = cover,
                    onOpenChanged = { searchOpen = it },
                    hazeState = searchHazeState,
                )
            }
        }
    }
    }
    }
        // Biometric lock overlay, drawn over the blurred app; fades out on unlock.
        LockAlphaOverlay(locked = locked, vm = vm, opaqueBackdrop = !contentSettled)
    }
    }

}

// Owns the lock-blur animation in its own small recompose scope so animating
// it doesn't invalidate all of BlooApp (see BlooApp's call site comment).

// Owns the lock-overlay fade animation in its own small recompose scope, for
// the same reason as [LockBlurLayer].




/**
 * Caches the edge-trace ring's rounded-rect perimeter Path + PathMeasure
 * (and a reusable output Path) keyed on Canvas size, so the hold-to-refresh
 * gesture animation -- which redraws every frame -- doesn't reallocate 2
 * Path objects + a PathMeasure on every single frame. Only `measure.getSegment`
 * needs to re-run per frame; the perimeter only changes when size does.
 */
internal class EdgeTracePerimeterCache {
    var size: androidx.compose.ui.geometry.Size? = null
    val measure = androidx.compose.ui.graphics.PathMeasure()
    val traced = androidx.compose.ui.graphics.Path()
}



internal val FieldShape: androidx.compose.foundation.shape.RoundedCornerShape
    get() = com.bloo.uicommon.FieldShape




// --- Garage (main) --------------------------------------------------------

/** Minimum comfortable width for one car column before we add another. */
internal const val MIN_CARD_DP = 320

/**
 * Snackbar payload that carries its own severity, so the host colours each
 * message from ITS OWN type rather than from a shared variable that the next
 * queued message may already have overwritten. [type] matches
 * `UiState.messageType`: "success", "info", or anything else (treated as error).
 */
private class BlooSnackbarVisuals(
    override val message: String,
    val type: String,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val duration: SnackbarDuration = SnackbarDuration.Short
    override val withDismissAction: Boolean = false
}
