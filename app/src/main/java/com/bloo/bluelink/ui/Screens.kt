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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalClipboardManager
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
    val state by vm.state.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
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
    val busy = state.loading || state.pending.isNotEmpty()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
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
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val visuals = BlooSnackbarVisuals(msg, state.messageType)
            scope.launch { snackbar.showSnackbar(visuals) }
            vm.clearMessage()
        }
    }

    // One floating registry for the whole app: every floating element (the flying car name, the
    // page dots, corner chrome) publishes its bounds here and asks here who else is in the way,
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
        // collectAsState() collector — see LocalAppearance.
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
    LockBlurLayer(locked = state.locked) {
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
                val offsetX = remember(data) { Animatable(0f) }
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
                Surface(
                    shape = snackShape,
                    color = snackColors.first,
                    contentColor = snackColors.second,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
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
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .graphicsLayer {
                            alpha = (1f - abs(offsetX.value) / (dismissPx * 2.2f)).coerceIn(0f, 1f)
                        }
                        .pointerInput(data) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    swipeScope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                                },
                                onDragEnd = {
                                    if (abs(offsetX.value) > dismissPx) {
                                        swipeScope.launch {
                                            val target = if (offsetX.value > 0) dismissPx * 4 else -dismissPx * 4
                                            offsetX.animateTo(target)
                                            data.dismiss()
                                        }
                                    } else {
                                        swipeScope.launch { offsetX.animateTo(0f) }
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
                        // A group: two icon buttons hard against each other at the end of a
                        // snackbar is exactly the case where pressing one should take its width
                        // from the other instead of shoving it toward the edge.
                        ExpressiveButtonRow(spacing = 0.dp) {
                            MorphIconButton(onClick = { clipboard.setText(AnnotatedString(data.visuals.message)) }) {
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
            }
        },
    ) { padding ->
        // Adding an account shows the login form even while already signed in.
        val target = if (state.addingAccount) Screen.Login else state.screen
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
                // Settings slides in from the right; returning slides back left.
                val sign = if (targetState == Screen.Settings) 1 else -1
                (slideInHorizontally { w -> sign * w } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -sign * w } + fadeOut())
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
                Screen.Loading -> LoadingScreen(Modifier.padding(padding))
                Screen.Login -> Box(Modifier.padding(padding)) {
                    LoginScreen(
                        loading = state.loading,
                        onLogin = vm::login,
                        onCancel = if (state.accounts.isNotEmpty()) ({ vm.cancelAddAccount() }) else null,
                    )
                    state.kiaOtp?.let { otp -> KiaOtpDialog(otp, loading = state.loading, vm = vm) }
                    state.canadaOtp?.let { otp -> CanadaOtpDialog(otp, loading = state.loading, vm = vm) }
                }
                // Lock is an overlay (see LockOverlay), not a full screen.
                Screen.Empty -> Box(Modifier.padding(padding)) { EmptyScreen(vm) }
                Screen.Onboarding -> OnboardingScreen(vm)
                is Screen.CarSetup -> CarSetupWizardScreen(vm, screen.vins)
                Screen.Garage -> {
                    // Reuses the outer `appearance` (already collected once above
                    // for the CompositionLocalProvider) instead of re-subscribing
                    // to the same StateFlow a second time here.
                    Box(Modifier.fillMaxSize()) {
                        // `paused = searchOpen`: the search panel sits ABOVE this
                        // background, and while it's up (typing frames, panel
                        // scrolling) the ambient drift would otherwise keep
                        // redrawing the blurred backdrop underneath at ~12fps --
                        // real contention on exactly the frames search is using.
                        if (appearance.auroraBackground) AuroraBackground(Modifier.matchParentSize(), appearance, refreshing = state.refreshing, paused = searchOpen)
                        GarageScreen(rememberUpdatedState(state), vm)
                    }
                }
                // The full phone Settings (search + keyboard, photo pickers, crop,
                // drag-reorder lists, sign-out) is unusable crammed onto a ~1-inch
                // flip cover — it used to render there verbatim. On the cover, show a
                // compact "manage on your phone" card instead; the real settings are
                // one unfold away. (The cover's gear button is also removed, so this
                // is a belt-and-suspenders fallback for the back-stack landing here.)
                Screen.Settings ->
                    if (isCompactCoverScreen()) {
                        // The cover can scroll settings fine (the grid scrolls as
                        // it does on the phone), but it is a cramped view of a
                        // screen built for a tall display -- say so once, then let
                        // them use it anyway.
                        CoverSettingsGate(vm)
                    } else SettingsScreen(vm)
            }
        }
        // Search lives HERE, above the screen-switching AnimatedContent and
        // outside it, which is the whole point: one element that survives the
        // transition, so garage -> Settings genuinely morphs a corner bubble
        // into the bottom bar instead of cross-fading two different objects
        // that happen to look alike. Only on the two screens that have
        // anything to search; login, onboarding and the setup wizard don't.
        val searchable = target == Screen.Garage || target == Screen.Settings
        val cover = isCompactCoverScreen()
        val notifPrefs by vm.notifications.collectAsState()
        // On the garage (and the cover) it is the user's switch. In Settings it
        // is always there -- that is how you find a setting. `|| state.onSettingsPageSlot`
        // on both lines below extends that same rule to Settings-as-an-embedded-page
        // (Appearance.settingsAsPage): without it, reaching Settings by swiping instead of
        // the gear button fell back to the ordinary garage-screen showSearch preference
        // (search could disappear entirely there for anyone with that off) and the search
        // element itself stayed shaped like a garage "bubble" instead of morphing into the
        // settings "pill" the moment the standalone route wasn't what put you there --
        // exactly the kind of un-seamless style transition between the two ways of
        // reaching Settings this exists to prevent.
        val effectivelyInSettings = target == Screen.Settings || state.onSettingsPageSlot
        if (searchable && !state.locked && (appearance.showSearch || effectivelyInSettings)) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                SearchLayer(
                    vm = vm,
                    state = state,
                    appearance = appearance,
                    notif = notifPrefs,
                    onSettings = effectivelyInSettings && !cover,
                    compact = cover,
                    onOpenChanged = { searchOpen = it },
                )
            }
        }
    }
    }
    }
        // Biometric lock overlay, drawn over the blurred app; fades out on unlock.
        LockAlphaOverlay(locked = state.locked, vm = vm)
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
 * The four car/tile pagers in this file (expanded, collapsed grid, cover
 * car-switch, cover tile-switch) all use the same infinite-wrap scheme: a huge
 * virtual page range (realCount * [WRAP_MULTIPLIER]) started at its midpoint,
 * with each virtual page mapped back onto a real index by modulo. These
 * primitives factor that out so the wrap math lives in exactly one place.
 */
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


// (coverScaled removed: its one caller -- a .padding(coverScaled(16.dp)) on
// the compact cover layout -- was removed in a later pass and nothing else
// ever called it, leaving this pure dead weight: a real, working function
// with a full doc comment for a question nothing in the file asks any more.)

// (cameraBumpPadding removed: it was a thin PaddingValues-rewrap of
// cutoutClearanceDp()'s own EdgeDp, and its one remaining caller,
// CoverManageOnPhoneCard, has moved onto CoverScaffold -- which already calls
// cutoutClearanceDp() directly and merges it with every other inset source via
// max(), not additively. The "why does this exist at all" explanation that used
// to live on this wrapper (Samsung flip COVER displays reporting the front
// camera via displayCutout.boundingRects but exposing zero WINDOW insets for
// it, so windowInsetsPadding(displayCutout) alone reserves nothing and content
// sits under the bump) now lives on [cutoutClearanceDp] itself, the function
// that actually does the work.
//
// (CameraEdge / cameraEdgeOf removed: cover-screen cutout avoidance is now driven
// by native WindowInsets.displayCutout — corner-safe and recomposition-aware —
// rather than hand-picking a single edge from a boundingRect margin comparison.)




// The cover reuses the phone's pebble CARDS: CompactCar's vertical tile pager
// renders SinglePebble(section) under LocalForceExpanded/PebbleFillHeight/
// CoverScrollState, so each pebble draws as an always-expanded, height-filling
// card. (The bespoke CoverTile toolkit + Cover*Tile faces were removed — they
// looked off-brand; the cover is back to the polished pebble-card design.)






// --- Trips (trip history) --------------------------------------------------



// --- Location -------------------------------------------------------------


// --- Photo crop -----------------------------------------------------------


// BlooDialog (the legacy second dialog shell) was removed here — every dialog now
// routes through the single GlassAlertDialog shell above. Its one caller
// (rename-device) was migrated in the same change.
