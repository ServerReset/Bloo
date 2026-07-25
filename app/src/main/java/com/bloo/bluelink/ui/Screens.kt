@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.app.StatusBarManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Bitmap
import android.os.Build
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.STALE_STATUS_MS
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.supportsConnectedStore
import com.bloo.bluelink.data.supportsHornLights
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.formatSpeed
import com.bloo.bluelink.data.formatTripDistance
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.serviceDue
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.smartClimateIsCooling
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.isPluggedIn
import com.bloo.uicommon.topFadeScrim
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan
import java.util.UUID
import androidx.compose.ui.graphics.toArgb

/** Which [ReorderColumn.introKey]s have already played their cold-start
 *  intro (see `staggerInOnColdStart`), so it plays once per key per process
 *  -- keyed per-vehicle (not a single global flag) so a prefetched/off-screen
 *  neighbour in the expanded car pager can't "use up" the intro before the
 *  page the user actually sees composes. */
private val coldStartIntroPlayed = mutableSetOf<Any>()

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
    // next message. The SnackbarHost reads this at render time — after the
    // reset — so reading state.messageType live would always paint red. Capture
    // the type WITH the message here and let the host read the captured value.
    var shownMessageType by remember { mutableStateOf("error") }
    LaunchedEffect(state.message) {
        state.message?.let {
            shownMessageType = state.messageType
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearMessage()
        }
    }

    CompositionLocalProvider(
        LocalHaptics provides haptics,
    ) {
    // Edge-to-edge: a soft full-bleed gradient paints behind the transparent
    // status/navigation bars; screen content draws on top of it.
    val scheme = MaterialTheme.colorScheme
    // Biometric lock overlay: blur the whole app behind it and fade the blur
    // away once unlocked.
    val lockBlur by animateDpAsState(
        targetValue = if (state.locked) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 450),
        label = "lockBlur",
    )
    val lockAlpha by animateFloatAsState(
        targetValue = if (state.locked) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "lockAlpha",
    )
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().blur(lockBlur)) {
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
                val snackColors = when (shownMessageType) {
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
                        IconButton(onClick = { clipboard.setText(AnnotatedString(data.visuals.message)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }
                        // Swipe-to-dismiss is a raw drag gesture with no
                        // TalkBack equivalent (a single-finger swipe here is
                        // captured by TalkBack's own navigation instead), so a
                        // screen-reader user previously had no way to dismiss
                        // early and had to wait out the auto-hide timeout.
                        IconButton(onClick = { data.dismiss() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        },
    ) { padding ->
        // Adding an account shows the login form even while already signed in.
        val target = if (state.addingAccount) Screen.Login else state.screen
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
                Screen.Login -> Box(Modifier.padding(padding)) {
                    LoginScreen(
                        loading = state.loading,
                        onLogin = vm::login,
                        onCancel = if (state.accounts.isNotEmpty()) ({ vm.cancelAddAccount() }) else null,
                    )
                    state.kiaOtp?.let { otp -> KiaOtpDialog(otp, loading = state.loading, vm = vm) }
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
                        if (appearance.auroraBackground) AuroraBackground(Modifier.matchParentSize(), appearance, refreshing = state.refreshing)
                        GarageScreen(state, vm)
                    }
                }
                Screen.Settings -> SettingsScreen(vm)
            }
        }
    }
    }
    }
        // Biometric lock overlay, drawn over the blurred app; fades out on unlock.
        if (lockAlpha > 0.01f) {
            Box(Modifier.fillMaxSize().alpha(lockAlpha)) {
                LockOverlay(vm)
            }
        }
    }
    }

}

// --- Onboarding wizard (first run + new-car detection) --------------------

private enum class WizardStepKind { POWERTRAIN, SEATS, STEERING }

private data class WizardPage(
    val kind: WizardStepKind,
    val vin: String? = null,
)

/**
 * Flattens the per-vehicle setup wizard into one linear list of pages: for
 * each vehicle, a POWERTRAIN page, then a SEATS page, then a STEERING page,
 * in that order. The resulting list drives a single [HorizontalPager] in
 * [CarSetupWizardScreen], so a multi-car setup becomes one continuous swipe
 * sequence instead of nested per-car flows.
 */
private fun buildSetupPages(vehicles: List<com.bloo.bluelink.data.Vehicle>): List<WizardPage> = buildList {
    vehicles.forEach { v ->
        add(WizardPage(WizardStepKind.POWERTRAIN, v.vin))
        add(WizardPage(WizardStepKind.SEATS, v.vin))
        add(WizardPage(WizardStepKind.STEERING, v.vin))
    }
}

private enum class OnboardingStepKind { INTRO, SETUP, CAR, CRASH_COURSE }

private data class OnboardingStep(val kind: OnboardingStepKind, val vin: String? = null)

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
private fun buildOnboardingSteps(
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
private fun OnboardingScreen(vm: AppViewModel) {
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
    LaunchedEffect(state.powertrains.keys, pageIndex) {
        if (pageIndex <= 1) preConfiguredVins = state.powertrains.keys.toSet()
    }
    val steps = remember(state.vehicles, preConfiguredVins) { buildOnboardingSteps(state.vehicles, preConfiguredVins) }
    LaunchedEffect(steps) { if (pageIndex > steps.lastIndex) pageIndex = steps.lastIndex }

    val lastIndex = steps.lastIndex
    val isLast = pageIndex == lastIndex

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
            val animatedProgress by animateFloatAsState(progress, tween(350), label = "onboardProgress")
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
                    (slideInHorizontally { it * dir } + fadeIn(tween(240))) togetherWith
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
                if (pageIndex > 0) {
                    OutlinedCard(
                        onClick = ::goBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Back", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                MorphButton(
                    onClick = ::goNext,
                    active = true,
                    modifier = Modifier.weight(if (pageIndex > 0) 2f else 1f),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    Icon(
                        if (isLast) Icons.Filled.CheckCircle else Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isLast -> "Enter Bloo"
                            pageIndex == 0 -> "Get started"
                            else -> "Next"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
private fun OnboardingIntroPage() {
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
private fun OnboardingSetupPage(vm: AppViewModel, state: UiState, context: android.content.Context, canBio: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Text(
        "Quick setup",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = scheme.onSurface,
    )
    Text(
        "All optional -- skip anything here and turn it on later in Settings.",
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
                Icon(
                    if (notifGranted) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (notifGranted) "Enabled" else "Enable notifications", fontWeight = FontWeight.SemiBold)
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
                Icon(
                    if (bioEnabled) Icons.Filled.CheckCircle else Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (bioEnabled) "Enabled" else "Enable fingerprint lock", fontWeight = FontWeight.SemiBold)
            }
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
        if (syncEnabled) {
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
                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Set up Drive sync", fontWeight = FontWeight.SemiBold)
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
@Composable
private fun OnboardingSetupCard(
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
@Composable
private fun OnboardingCarPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    sc: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    Text(
        "Set up",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Text(
        vehicle.name,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        color = scheme.onSurface,
    )
    Text(
        "Bloo cannot read powertrain or feature info from the API. Set them once here so the right controls appear.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Powertrain", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
        val currentPt = state.powertrainOf(vehicle)
        PowertrainPicker(current = currentPt) { pt -> vm.setPowertrain(vehicle, pt) }
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
        Surface(
            onClick = { vm.setSeatFlag(vehicle, "sw", !sc.steeringWheel) },
            shape = RoundedCornerShape(50),
            color = if (sc.steeringWheel) scheme.secondaryContainer else scheme.surfaceContainerHighest,
            contentColor = if (sc.steeringWheel) scheme.onSecondaryContainer else scheme.onSurface,
            border = if (sc.steeringWheel) null else BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (sc.steeringWheel) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                Text("Steering wheel heat", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Final step: a quick tip list covering the app's core gestures. */
@Composable
private fun OnboardingCrashCoursePage() {
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
}

/**
 * Standalone wizard shown when a new car is detected after first-run onboarding.
 * Mandatory — navigates to the garage only when every car in [vins] is configured.
 */
@Composable
private fun CarSetupWizardScreen(vm: AppViewModel, vins: List<String>) {
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
private fun CarFeatureWizard(
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
            val animatedProgress by animateFloatAsState(progress, tween(300), label = "wizProgress")
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
                    (slideInHorizontally { it * dir } + fadeIn(tween(220))) togetherWith
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
                if (pageIndex > 0) {
                    OutlinedCard(
                        onClick = ::goBack,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Back", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                MorphButton(
                    onClick = ::goNext,
                    active = true,
                    modifier = Modifier.weight(if (pageIndex > 0) 2f else 1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    val isLast = pageIndex == pages.lastIndex
                    val lastLabel = "Done"
                    Icon(
                        if (isLast) Icons.Filled.CheckCircle else Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isLast) lastLabel else "Next",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
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
private fun WizardPowertrainPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    Text(
        "Powertrain",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "What powers the ${vehicle.name}?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
    )
    Text(
        "Bloo uses this to show the right status tiles — battery percentage for EVs, " +
            "fuel level for gas, or both for plug-in hybrids.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
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
            Surface(
                onClick = { vm.setPowertrain(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(icon, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
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
private fun WizardSeatsPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    seats: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    Text(
        "Seat comfort",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "What does the ${vehicle.name} have?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
    )
    Text(
        "Bloo shows only the controls your car actually supports. Skip any seats you don't have.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
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
private data class SeatPosition(
    val label: String,
    val heatKey: String,
    val coolKey: String,
    val heat: (SeatConfig) -> Boolean,
    val cool: (SeatConfig) -> Boolean,
)

private val SeatPositions = listOf(
    SeatPosition("Driver", "dh", "dc", { it.driverHeat }, { it.driverCool }),
    SeatPosition("Front passenger", "ph", "pc", { it.passHeat }, { it.passCool }),
    SeatPosition("Rear left", "rlh", "rlc", { it.rearLeftHeat }, { it.rearLeftCool }),
    SeatPosition("Rear right", "rrh", "rrc", { it.rearRightHeat }, { it.rearRightCool }),
)

@Composable
private fun WizardSeatRow(
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
private fun WizardToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** One wizard page for the single "heated steering wheel" flag; same
 *  direct-to-view-model wiring as the other wizard pages, just one row. */
@Composable
private fun WizardSteeringPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    seats: com.bloo.bluelink.data.SeatConfig,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    Text(
        "Climate features",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "Any extras on the ${vehicle.name}?",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
    )
    Text(
        "Enable what the car actually has. These control which options appear in the climate command.",
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
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
private fun WizardFeatureToggle(
    title: String,
    body: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            // Same fix as ToggleRow: toggleable + Role.Switch on the row, with
            // the inner Switch's own semantics node cleared, so TalkBack sees
            // one correctly-announced toggle instead of two focus stops.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.clearAndSetSemantics {})
    }
}

private class Burst(val x: Float, val y: Float, val start: Float, val life: Float, val hue: Float, val count: Int, val maxR: Float)

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
private fun FireworksOverlay(modifier: Modifier = Modifier) {
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

// --- Login ----------------------------------------------------------------

private val FieldShape = RoundedCornerShape(18.dp)

/**
 * Sign-in form supporting all three brands from one screen. All fields
 * (email/password/pin/brand) are local `mutableStateOf` -- nothing is
 * persisted until [onLogin] fires, so switching brands mid-entry doesn't
 * lose the typed email/password. Selecting a brand via [MorphSegmented]
 * only changes copy/labels/validation shape shown here; brand-specific
 * strings (subtitle, email label, forgot-password URL, sign-in button
 * label) are recomputed from `brand` on every recomposition and each swap
 * cross-fades via [AnimatedContent] rather than snapping instantly.
 * The PIN field is only shown for brands that don't use OTP login
 * (`!brand.usesOtpLogin`); Kia instead gets a one-time-passcode dialog
 * elsewhere ([KiaOtpDialog]) after submitting. `formVisible` flips true one
 * frame after first composition purely to trigger the initial slide-up-and-
 * fade-in entrance animation.
 */
@Composable
private fun LoginScreen(
    loading: Boolean,
    onLogin: (String, String, String, Brand) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf(Brand.HYUNDAI) }
    val scheme = MaterialTheme.colorScheme
    val cfg = LocalConfiguration.current
    val shortScreen = cfg.screenHeightDp < 520
    val heroHeight = if (shortScreen) 96.dp else 160.dp
    val context = LocalContext.current

    // Brand-specific copy
    val brandSubtitle = when (brand) {
        Brand.HYUNDAI -> "A better Bluelink · US"
        Brand.GENESIS -> "A better Genesis · US"
        Brand.KIA     -> "A better Kia Connect · US"
    }
    val emailLabel = when (brand) {
        Brand.HYUNDAI -> "Bluelink email"
        Brand.GENESIS -> "Genesis account email"
        Brand.KIA     -> "Kia Connect email"
    }

    // Animate the form in from below on first composition.
    var formVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { formVisible = true }

    if (onCancel != null) BackHandler { onCancel() }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Wordmark hero — subtitle crossfades when the brand changes.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        "Bloo",
                        style = if (shortScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AnimatedContent(
                        targetState = brandSubtitle,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 3 })
                        },
                        label = "loginSubtitle",
                    ) { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Form slides up from below on first composition.
            AnimatedVisibility(
                visible = formVisible,
                enter = slideInVertically(tween(420, easing = LinearOutSlowInEasing)) { it / 3 } +
                    fadeIn(tween(380)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surface,
                        unfocusedContainerColor = scheme.surface,
                        disabledContainerColor = scheme.surface,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    )

                    Text(
                        "Sign in with",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    MorphSegmented(
                        options = Brand.entries.map { b -> SegmentOption(b.name, b.label, null) },
                        selectedKey = brand.name,
                        onSelect = { key -> brand = Brand.valueOf(key) },
                    )

                    // Email field — label and placeholder animate with brand.
                    AnimatedContent(
                        targetState = emailLabel,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "emailLabel",
                    ) { label ->
                        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text(emailLabel) },
                        singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Password", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        shape = FieldShape,
                        colors = fieldColors,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // PIN — only for Hyundai/Genesis (Kia uses OTP).
                    AnimatedVisibility(
                        visible = !brand.usesOtpLogin,
                        enter = expandVertically(tween(280)) + fadeIn(tween(280)),
                        exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("Service PIN", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                placeholder = { Text("Service PIN") },
                                singleLine = true,
                                shape = FieldShape,
                                colors = fieldColors,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    // Sign in CTA — label reflects the chosen brand.
                    MorphButton(
                        onClick = { onLogin(email, password, pin, brand) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !loading,
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ) {
                        if (loading) {
                            LoadingIndicator()
                        } else {
                            AnimatedContent(
                                targetState = brand.label,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                                label = "signInLabel",
                            ) { label ->
                                Text("Sign in to $label", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (onCancel != null) {
                        MorphButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                        ) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
                    }

                    // Forgot password — MorphTextButton that routes to the right brand portal.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MorphTextButton(
                            text = "Forgot password?",
                            onClick = {
                                val forgotUrl = when (brand) {
                                    Brand.HYUNDAI -> "https://owners.hyundaiusa.com/us/en/forgot-password"
                                    Brand.GENESIS -> "https://owners.genesis.com/us/en/forgot-password.html"
                                    Brand.KIA     -> "https://owners.kia.com/us/en/kia-owner-portal.html"
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(forgotUrl)))
                            },
                            contentColor = scheme.onSurfaceVariant,
                        )
                    }

                    AnimatedContent(
                        targetState = brand.label,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                        label = "privacyNote",
                    ) { label ->
                        Text(
                            "Credentials are sent directly to $label's telematics servers and " +
                                "stored encrypted on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kia sign-in verification: pick where the one-time code goes (email/text),
 * then enter it. Shown over the login form while a Kia OTP challenge is open.
 */
@Composable
private fun KiaOtpDialog(otp: KiaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.sentTo) { mutableStateOf("") }
    // Standardized on the shared GlassAlertDialog shell (frosted card, 28dp
    // corners, stacked full-width buttons) instead of a raw M3 AlertDialog.
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.kiaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = if (otp.sentTo == null) "Verify it's you" else "Enter your code",
        text = {
            if (otp.sentTo == null) {
                Text("Kia needs to verify this sign-in with a one-time code. Where should it go?")
                if (otp.challenge.hasEmail) {
                    MorphButton(
                        onClick = { vm.kiaSendOtp("EMAIL") },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Email" + (otp.challenge.email?.let { " · $it" } ?: "")) }
                }
                if (otp.challenge.hasSms) {
                    MorphButton(
                        onClick = { vm.kiaSendOtp("SMS") },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Text message" + (otp.challenge.sms?.let { " · $it" } ?: "")) }
                }
            } else {
                Text(
                    if (otp.sentTo == "SMS") "We texted you a one-time code."
                    else "We emailed you a one-time code.",
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code") },
                    singleLine = true,
                    shape = FieldShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        buttons = {
            // Verify shown only once a code's been sent; Cancel always. Stacked
            // full-width (primary on top) per the shell's convention.
            if (otp.sentTo != null) {
                MorphButton(
                    onClick = { vm.kiaVerifyOtp(code) },
                    enabled = !loading && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) LoadingIndicator() else Text("Verify", fontWeight = FontWeight.SemiBold)
                }
            }
            MorphTextButton("Cancel", vm::kiaCancelOtp, enabled = !loading, modifier = Modifier.fillMaxWidth())
        },
    )
}

// UpdatePromptDialog used to live here -- replaced by UpdateAvailableTile
// (see below Screens.kt), a standalone pebble pinned under the hero tile
// instead of an interrupting popup. See its doc comment for why.

/**
 * The app's shared "important pop-up" dialog shell. Update-available and
 * Drive-sync-setup both route through this now instead of two separately
 * hand-rolled AlertDialogs that merely happened to look similar. A single
 * elevated card -- icon in a tonal container, headline, supporting content,
 * stacked actions -- per the Material 3 "basic dialog" layout, rather than
 * routing through AlertDialog's own title/text slots: those render as two
 * independently-clipped boxes with a gap between them, which read as a
 * broken, disconnected stack of panels once each one lost the glass blur
 * that used to visually tie them together.
 */
@Composable
private fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
    // Optional leading icon: when non-null it renders in the 48dp primaryContainer
    // circle; when null the circle is skipped entirely (for dialogs like "Save
    // preset" / "Rename device" that have no natural glyph). Defaulted so existing
    // callers that pass an icon are unchanged.
    icon: ImageVector? = null,
    // Optional trailing action in the title row (e.g. PaletteEditorDialog's delete
    // button). Sits to the right of the title, vertically centered.
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismissRequest) {
        // Dialog() opens its own platform Window, which doesn't inherit the
        // app's forceDarkAllowed=false the way the main Activity window does
        // -- on API 29+ Android's automatic Force Dark heuristic was
        // re-inverting already-dark, explicitly-colored text drawn here
        // (this dialog's title and body rendered near-black on a near-black
        // card, while the identical text elsewhere in the app -- inside the
        // Activity's own window -- rendered correctly). Disabling it on this
        // window specifically stops Android from "helpfully" reprocessing
        // colors Compose already resolved correctly.
        val dialogView = LocalView.current
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val decorView = (dialogView.parent as? DialogWindowProvider)?.window?.decorView
                // Reflection, not a direct call: setForceDarkAllowed isn't
                // exposed as a resolvable View method against every compileSdk
                // stub this project has built against, even though it's a
                // real public API on-device at this API level.
                runCatching {
                    android.view.View::class.java
                        .getMethod("setForceDarkAllowed", Boolean::class.javaPrimitiveType)
                        .invoke(decorView, false)
                }
            }
        }
        // Near-opaque fill -- this card sits over the scrim, framed by the
        // app's frosted edge (appGlassRim).
        Surface(
            shape = shape,
            color = scheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha(0.97f)),
            modifier = Modifier
                .fillMaxWidth()
                .dropShadow(shape, blurRadius = 22.dp, offsetY = 8.dp)
                .appGlassRim(shape),
        ) {
            Column(Modifier.padding(24.dp)) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(scheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (titleTrailing != null) {
                        Spacer(Modifier.width(8.dp))
                        titleTrailing()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = text,
                )
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), content = buttons)
            }
        }
    }
}

/**
 * A softly-blurred, slowly-drifting "aurora" of colour blobs - the animated login
 * backdrop. Three blobs ease back and forth on different periods.
 */
/** Triangle wave in [0,1]: rises for [periodMs], falls for [periodMs], repeats. */
private fun triangleWave(elapsedMs: Long, periodMs: Long): Float {
    val phase = elapsedMs % (2 * periodMs)
    return if (phase < periodMs) phase.toFloat() / periodMs else 2f - phase.toFloat() / periodMs
}

/**
 * Draws the animated gradient-blob backdrop used behind the login screen,
 * onboarding, and (optionally) the garage. Colors, motion style, and the
 * pull-to-refresh "explosion" pulse are all independent concerns composed
 * together here:
 *  - [colorMode] picks how the blob hues are derived: "material" uses the
 *    theme's primary/secondary/tertiary directly, "custom" derives
 *    complementary/analogous hues from [appearance]'s stored hex color via
 *    HSV rotation, and the default ("complementary") derives a hue from the
 *    surface color rotated 180°.
 *  - [motionMode] picks whether the blobs drift on their own (`static`,
 *    driven by [triangleWave]-based ease loops further below) or track the
 *    phone's tilt via the accelerometer (`motion`). In Motion mode, a fast
 *    exponential-moving-average of the raw sensor reading is compared
 *    against a much slower moving average of the same signal; the
 *    difference isolates *deliberate* tilting from however the phone is
 *    generally being held, so the background doesn't sit permanently
 *    off-center just because the phone rests at an angle.
 *  - `refreshing` drives a one-shot grow/hold/shrink pulse (`explosion`, an
 *    [Animatable]) via [LaunchedEffect], keyed on `refreshing` itself so a
 *    pull-to-refresh that resolves near-instantly still visibly completes a
 *    full grow-then-shrink cycle instead of snapping back before the eye
 *    can register it.
 */
@Composable
private fun AuroraBackground(
    modifier: Modifier = Modifier,
    appearance: SettingsStore.Appearance? = null,
    refreshing: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val motionMode = appearance?.auroraMotion ?: "static"
    val colorMode = appearance?.auroraColorMode ?: "complementary"
    val customHex = appearance?.auroraCustomColor

    // "Motion" follows the phone's tilt (like a lock-screen wallpaper
    // parallax); "Static" ignores tilt entirely and instead gets its own
    // slow, small ambient drift (see p1/p2/p3 below) so it still reads as
    // alive when the phone is sitting still, rather than a literally frozen
    // frame. A low exponential-smoothing alpha is what keeps the tilt from
    // jittering on every tiny hand tremor: each sample only nudges the
    // running average a little, so the blobs drift toward wherever you've
    // tilted to over roughly a second, not instantly. The multiplier is
    // deliberately large enough to be unmistakable -- it previously read as
    // "not doing anything" because it was blended in alongside a much
    // bigger automatic drift that swamped it; Motion mode no longer runs
    // that automatic drift at all, so tilt is the only thing moving it.
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    val motionActive = motionMode == "motion"
    if (motionActive) {
        val ctx = LocalContext.current
        DisposableEffect(ctx) {
            val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            // Raw accelerometer values bake in however the phone is generally
            // being held, not just active tilting -- held upright to look at
            // (the overwhelmingly common case), gravity alone puts values[1]
            // near +-9.8, a huge constant offset next to the deliberate ~0.5
            // multiplier below. That pinned the blobs off in one direction
            // (reading as "not centered") and saturated well past where any
            // real hand tilt could move them further (reading as "motion does
            // nothing"). rawX/rawY track the sensor directly; baseX/baseY
            // track the same signal on a much slower average -- "how you're
            // generally holding it right now" -- and tilt is only the
            // difference between the two, so genuine movement still shows up
            // small and centred regardless of the phone's constant baseline
            // angle, and re-centres itself if you settle into holding it
            // differently for a while.
            var rawX = 0f; var rawY = 0f
            var baseX = 0f; var baseY = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val fastAlpha = 0.08f
                    val slowAlpha = 0.01f
                    val x = -event.values[0]
                    val y = event.values[1]
                    rawX = rawX * (1 - fastAlpha) + x * fastAlpha
                    rawY = rawY * (1 - fastAlpha) + y * fastAlpha
                    baseX = baseX * (1 - slowAlpha) + x * slowAlpha
                    baseY = baseY * (1 - slowAlpha) + y * slowAlpha
                    tiltX = (rawX - baseX) * 0.06f
                    tiltY = (rawY - baseY) * 0.06f
                }
                override fun onAccuracyChanged(s: Sensor, acc: Int) {}
            }
            if (sensor != null) mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { mgr.unregisterListener(listener) }
        }
    } else {
        // Not tracking tilt in Static mode -- reset so a mode switch away
        // from Motion doesn't leave the blobs stuck at a stale offset.
        LaunchedEffect(motionActive) { tiltX = 0f; tiltY = 0f }
    }

    val basePrimary = when (colorMode) {
        "material" -> scheme.primary
        "custom" -> customHex?.let { hx -> runCatching { Color(android.graphics.Color.parseColor(hx)) }.getOrNull() } ?: scheme.primary
        else -> {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(scheme.surface.toArgb(), hsv)
            hsv[0] = (hsv[0] + 180f) % 360f
            Color(android.graphics.Color.HSVToColor(hsv))
        }
    }
    val baseTertiary = when (colorMode) {
        "material" -> scheme.tertiary
        "custom" -> customHex?.let { hx -> runCatching {
            val c = android.graphics.Color.parseColor(hx)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c, hsv)
            hsv[0] = (hsv[0] + 180f) % 360f
            Color(android.graphics.Color.HSVToColor(hsv))
        }.getOrNull() } ?: scheme.tertiary
        else -> scheme.tertiary
    }
    val baseSecondary = when (colorMode) {
        "material" -> scheme.secondary
        "custom" -> customHex?.let { hx -> runCatching {
            val c = android.graphics.Color.parseColor(hx)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(c, hsv)
            hsv[0] = (hsv[0] + 90f) % 360f
            Color(android.graphics.Color.HSVToColor(hsv))
        }.getOrNull() } ?: scheme.secondary
        else -> scheme.secondary
    }

    // A guaranteed grow-then-shrink pulse rather than a value that just
    // chases the raw refreshing boolean: a quick refresh (cache hit, or a
    // refresh that resolves in well under a second) flipped refreshing back
    // to false before the spring had visibly moved, which read as the
    // background just snapping to its resting size instead of animating.
    // Holding briefly at the peak guarantees the "grow" half is actually
    // visible before the "shrink" half starts, regardless of how fast the
    // underlying refresh itself completes.
    val explosion = remember { Animatable(0f) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            explosion.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
            delay(220)
        }
        explosion.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    val explosionValue = explosion.value
    val explodeAlpha = 1f + explosionValue * 2.5f
    val explodeSize = 1f + explosionValue * 0.8f
    val explodeSpread = 1f + explosionValue * 0.3f
    // Both modes now run the same ambient drift below; Motion mode adds tilt
    // on top of it instead of replacing it entirely, so a phone that isn't
    // being actively tilted (sitting on a desk, in a stand, or just being
    // looked at) still reads as alive instead of a dead, frozen frame.
    // Hand-ticked at ~12fps instead of riding Compose's animation clock
    // (which recomposes on every display frame, up to 120x/sec). For a slow
    // multi-second drift sitting under a heavy 90dp blur, that clock was
    // forcing a full-screen blur redraw every single vsync for no visible
    // gain over a much coarser update rate -- a real, sustained source of
    // GPU load (and the phone heat it produced) any time this screen was on
    // screen, which is most of the time this background is enabled at all.
    var p1 by remember { mutableFloatStateOf(0.5f) }
    var p2 by remember { mutableFloatStateOf(0.5f) }
    var p3 by remember { mutableFloatStateOf(0.5f) }
    // Runs in BOTH modes now -- Motion previously froze this drift entirely
    // and relied only on tilt, so a phone sitting still (the common case:
    // on a desk, in a stand, or just being looked at without being moved)
    // showed a completely dead background. This is now a smaller ambient
    // drift added underneath tilt in Motion mode, and the sole driver in
    // Static mode -- widened and sped up from the previous ±0.08/9-14s
    // (correct in principle, but under a heavy 90dp blur it read as "not
    // animating" -- too subtle to actually perceive) to something
    // unambiguously visible at a glance.
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            p1 = 0.32f + (0.68f - 0.32f) * triangleWave(elapsed, 9_000L)
            p2 = 0.68f + (0.32f - 0.68f) * triangleWave(elapsed, 7_000L)
            p3 = 0.35f + (0.65f - 0.35f) * triangleWave(elapsed, 6_000L)
            delay(80)
        }
    }
    fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
    Box(
        modifier
            .fillMaxSize()
            // Lighter than before (was 120dp): that much blur smoothed three
            // drifting blobs into a wash that barely changed frame to frame,
            // reading as "not animating" even though the drift was running.
            .blur((90.dp * (1f + explosionValue * 0.5f)), edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .drawBehind {
                drawRect(scheme.surface)
                fun blob(c: Color, fx: Float, fy: Float, r: Float) =
                    drawCircle(c, radius = size.minDimension * r, center = Offset(size.width * fx, size.height * fy))
                blob(basePrimary.copy(alpha = (0.30f * explodeAlpha).coerceIn(0f, 1f)), (mix(0.26f, 0.74f, p1) + tiltX) * explodeSpread, (mix(0.30f, 0.65f, p2) + tiltY) * explodeSpread, 0.45f * explodeSize)
                blob(baseTertiary.copy(alpha = (0.25f * explodeAlpha).coerceIn(0f, 1f)), (mix(0.32f, 0.68f, p2) - tiltX) * explodeSpread, (mix(0.35f, 0.70f, p3) - tiltY) * explodeSpread, 0.40f * explodeSize)
                // fx range was 0.22-0.58 (centred at 0.40, visibly left of the
                // other two blobs' 0.50) -- the whole composite wash read as
                // biased toward one side even before any tilt was applied.
                blob(baseSecondary.copy(alpha = (0.20f * explodeAlpha).coerceIn(0f, 1f)), (mix(0.32f, 0.68f, p3) + tiltX) * explodeSpread, (mix(0.28f, 0.62f, p1) + tiltY) * explodeSpread, 0.38f * explodeSize)
            },
    )
}

// --- Lock -----------------------------------------------------------------

/**
 * The biometric lock, drawn as an overlay on top of the blurred app. High-contrast
 * white-on-scrim text reads over any wallpaper of cars behind it; a floating back
 * arrow returns to the login screen. Centered + width-capped so it sits well on
 * phones, flip-phone cover screens and tablets alike.
 */
@Composable
private fun LockOverlay(vm: AppViewModel) {
    val context = LocalContext.current
    val compact = isCompactCoverScreen()
    fun authenticate() {
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
    LaunchedEffect(Unit) { authenticate() }

    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            // Darken the blur for legibility, and swallow taps to the app behind.
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = noRipple, indication = null) {},
    ) {
        // Floating back arrow -> login.
        Surface(
            onClick = { vm.lockToLogin() },
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to login")
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                "Confirm it's you to reach your vehicles.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
            // White pill for maximum contrast over the dimmed blur.
            MorphButton(
                onClick = { authenticate() },
                modifier = Modifier.height(if (compact) 56.dp else ControlHeight),
                containerColor = Color.White,
                contentColor = Color.Black,
                contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Unlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --- Empty ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmptyScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val scheme = MaterialTheme.colorScheme

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

    Box(Modifier.fillMaxSize()) {
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
                            translationY = contentOffset.value
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
                        MorphButton(onClick = { vm.openSettings() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Settings", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        MorphButton(onClick = { vm.loadGarage() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (loadFailed) "Try again" else "Reload", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    MorphTextButton("Account Settings", onClick = { vm.openSettings() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// --- Garage (main) --------------------------------------------------------

/** Minimum comfortable width for one car column before we add another. */
private const val MIN_CARD_DP = 320

/** Screen height (dp) below which the phone gets the compact cover-screen
 *  layout -- a folding phone's small outer display (Galaxy Z Flip's ~260-280dp
 *  square cover, for instance), not a full unfolded/candybar phone screen.
 *  GarageScreen and LockOverlay used to each pick their own cutoff (570 vs
 *  440), so a screen sized between them got the compact UI on one but the
 *  full-size one on the other for the exact same physical device -- one
 *  shared threshold instead. Width is checked separately (see isCompactCoverScreen)
 *  so a wide-but-short screen (a tablet in landscape) doesn't false-positive. */
private const val COVER_SCREEN_HEIGHT_DP = 570
private const val COVER_SCREEN_WIDTH_DP = 600

/** True on a folding phone's compact cover screen; false on a full phone,
 *  foldable-unfolded, or tablet screen. See [COVER_SCREEN_HEIGHT_DP]. */
@Composable
private fun isCompactCoverScreen(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp < COVER_SCREEN_WIDTH_DP && cfg.screenHeightDp < COVER_SCREEN_HEIGHT_DP
}

/** Scales a "reference" spacing/padding value for the compact cover-screen
 *  layout so a tiny cover screen (a Z Flip's ~260dp-wide square) doesn't lose
 *  proportionally more room to fixed insets than a larger one (a Z Flip 6 or
 *  Razr+'s taller cover) does. [refWidthDp] is the width the base value was
 *  tuned against; clamped to +-40% so this nudges spacing rather than
 *  drastically re-laying things out at either extreme. */
@Composable
private fun coverScaled(base: Dp, refWidthDp: Float = 280f): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val factor = (widthDp / refWidthDp).coerceIn(0.6f, 1.4f)
    return base * factor
}

/** Which screen edge a camera cutout is flush against. Cover-screen code used
 *  to always assume "top" -- wrong for any device whose cover-display cutout
 *  coordinate space reports it against a different edge (bottom, left, or
 *  right, depending on how that device rotates its outer display). */
private enum class CameraEdge { TOP, BOTTOM, LEFT, RIGHT }

/** Figures out which edge of a [viewWidthPx] x [viewHeightPx] screen the
 *  cutout [rect] sits flush against, by comparing its margin to each edge --
 *  whichever margin is smallest is the edge it's cut into. Returns null for
 *  a null rect (no cutout at all). */
private fun cameraEdgeOf(rect: android.graphics.Rect?, viewWidthPx: Int, viewHeightPx: Int): CameraEdge? {
    if (rect == null) return null
    val margins = mapOf(
        CameraEdge.TOP to rect.top,
        CameraEdge.BOTTOM to (viewHeightPx - rect.bottom),
        CameraEdge.LEFT to rect.left,
        CameraEdge.RIGHT to (viewWidthPx - rect.right),
    )
    return margins.minByOrNull { it.value }?.key
}

/**
 * Top-level garage screen: picks between three fundamentally different
 * layouts based on screen size/shape and dispatches to the right one, then
 * (for the "normal phone" case) owns the pager(s) that let the user swipe
 * between cars.
 *
 * Layout selection:
 *  - `compact` (a folding phone's small cover screen, see
 *    [isCompactCoverScreen]) short-circuits straight to [CompactGarage] and
 *    returns early -- none of the pager/expand logic below applies there.
 *  - `large` (wide enough for [perPage] > 1 car side by side) enables the
 *    dual/multi-column view and "expand one car to fill the screen" gesture.
 *  - Otherwise, the default single-column swipe-between-cars view.
 *
 * State plumbing specific to this screen:
 *  - `pullFractionState`/`dotsAlpha`/`refreshShift` together drive how the
 *    floating page-indicator dots and other overlays react live as the user
 *    pulls to refresh -- fading/sliding out of the way during the pull and
 *    springing back once it resolves -- rather than only reacting once
 *    `state.refreshing` flips.
 *  - The expanded ([HorizontalPager] over `exPager`) and collapsed
 *    (multi-car-per-page `pager`) pagers both use the "start in the middle
 *    of a huge virtual page range, map back to a real index with modulo"
 *    trick to fake infinite wrap-around swiping in both directions.
 *  - A `LaunchedEffect(currentVehicle?.vin, currentFetchedAt)` watches for
 *    stale data and only warns the user if a fresh background refresh
 *    doesn't land within 25s (see the inline comment below for why the
 *    delay is cancellable).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GarageScreen(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    if (vehicles.isEmpty()) return
    val appearance by vm.appearance.collectAsState()

    val currentVehicle = vehicles.getOrNull(state.currentIndex.coerceIn(0, vehicles.lastIndex))
    val currentFetchedAt = currentVehicle?.let { state.fetchedAt(it) }
    val sessionStartMs = remember { System.currentTimeMillis() }
    LaunchedEffect(currentVehicle?.vin, currentFetchedAt) {
        if (currentFetchedAt != null &&
            currentFetchedAt < sessionStartMs &&
            System.currentTimeMillis() - currentFetchedAt > STALE_STATUS_MS) {
            // Give the automatic background fetch time to land. If it returns fresh
            // data, currentFetchedAt changes → this effect restarts → delay is
            // cancelled → user never sees a spurious "stale" toast.
            delay(25_000)
            vm.reportError("Data is over 15 min old. Pull down to refresh")
        }
    }

    // Gentle one-time nudge after onboarding, encouraging a Settings visit.
    LaunchedEffect(state.showSettingsHint) {
        if (state.showSettingsHint) {
            vm.reportInfo("Tip: fine-tune each car's seats, photo and pebble order in Settings")
            vm.dismissSettingsHint()
        }
    }

    // Settle haptic when a refresh lands.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.refreshing) {
        if (wasRefreshing && !state.refreshing) haptics?.slotSettle()
        wasRefreshing = state.refreshing
    }
    // Live pull distance reported by Refreshable, so the overlays react the moment
    // the user starts pulling - not only once a refresh is in flight.
    val pullFractionState = remember { mutableStateOf(0f) }
    val pullFraction by pullFractionState
    // Hide the page indicator as soon as the pull begins (and through the refresh),
    // so the squiggly indicator has the stage to itself; fade it back in when done.
    val dotsAlpha by animateFloatAsState(
        targetValue = if (state.refreshing || pullFraction > 0.01f) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "dotsFade",
    )
    // Slide the floating overlays (dots, settings, back/flip) down: in real time as
    // the user pulls, then settle/spring back up once the refresh completes.
    val overlayShiftTarget = if (state.refreshing) RefreshPullShift
        else (RefreshPullShift * pullFraction).coerceIn(0.dp, RefreshPullShift)
    val refreshShift by animateDpAsState(
        targetValue = overlayShiftTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (state.refreshing) Spring.StiffnessLow else Spring.StiffnessMedium,
        ),
        label = "refreshShift",
    )

    val count = vehicles.size
    val cfg = LocalConfiguration.current
    val widthDp = cfg.screenWidthDp
    val large = widthDp >= COVER_SCREEN_WIDTH_DP
    val compact = isCompactCoverScreen()
    // Only show cover-screen hints once per session.
    var coverHintShown by rememberSaveable { mutableStateOf(false) }
    // Detect a device that likely has a cover screen: look for a camera cutout
    // (punch-hole) on a short screen, indicating a flip/fold cover display.
    val view = LocalView.current
    val hasCameraCutout = remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            view.rootWindowInsets?.displayCutout?.boundingRects?.isNotEmpty() == true
        else false
    }
    LaunchedEffect(compact, hasCameraCutout) {
        if (!coverHintShown && hasCameraCutout) {
            coverHintShown = true
            if (compact && vehicles.isEmpty())
                vm.reportInfo("Open your phone for the full Bloo setup experience")
            else if (compact)
                vm.reportInfo("Open your phone for the full Bloo experience")
        }
    }
    if (compact) {
        CompactGarage(state, vm, appearance)
        return
    }
    // How many full-height cards fit side by side; pages advance by this many.
    val perPage = (widthDp / MIN_CARD_DP).coerceIn(1, count)
    // Expanding to the dual-column view only makes sense on a wide screen.
    val canExpand = large && count > 1
    val singleLarge = large && count == 1
    // A car expanded by the user (multi-car), or the lone car on a big screen.
    val expandedByUser = state.expandedIndex?.takeIf { it in vehicles.indices && canExpand }
    val expandedIdx = if (singleLarge) 0 else expandedByUser

    BackHandler(enabled = expandedByUser != null) { vm.collapse() }

    CompositionLocalProvider(LocalPullFraction provides pullFractionState) {
    BackdropHost {
        AnimatedContent(
            targetState = expandedIdx != null,
            transitionSpec = {
                val spec = spring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                (fadeIn(spec) + scaleIn(spec, initialScale = 0.94f)) togetherWith
                    (fadeOut(spec) + scaleOut(spec, targetScale = 0.94f))
            },
            label = "expand",
        ) { isExpanded ->
            if (isExpanded) {
                // Full-screen car; swipe left/right to switch cars. Infinite
                // wrap-around: start in the middle of a huge virtual range and
                // map each virtual page back onto a real car with modulo --
                // same technique the cover screen's tile pager already uses.
                val exLoop = count > 1
                val exVirtualCount = if (exLoop) count * 1000 else count
                val exStart = (if (exLoop) exVirtualCount / 2 else 0) + (expandedIdx ?: 0).coerceIn(0, count - 1)
                val exPager = rememberPagerState(initialPage = exStart) { exVirtualCount }
                fun exReal(virtualPage: Int) = ((virtualPage % count) + count) % count
                LaunchedEffect(exPager) {
                    snapshotFlow { exPager.settledPage }.collect { vm.expand(exReal(it)) }
                }
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = exPager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        pageSize = androidx.compose.foundation.pager.PageSize.Fill,
                    ) { page ->
                        // Read the continuous pager offset ONLY inside graphicsLayer{}
                        // below (draw-phase, never triggers recomposition) -- reading
                        // it as a plain val in this composable scope used to subscribe
                        // the WHOLE page composable (CarThemeOverride, VehicleDetailContent,
                        // every pebble in it) to recompose on literally every drag frame,
                        // the real remaining cause of swipe jank after the blur/tilt
                        // removal below. A secondary "snap bounce" spring driven off a
                        // discretized settled/unsettled boolean used to multiply into
                        // this too, on the theory that it'd add a subtle overshoot on
                        // release -- in practice it lagged the scale/alpha response
                        // behind the actual continuous drag position for the whole
                        // gesture (the spring has to visibly catch up to "unsettled"
                        // right as the drag starts), which is what made this pager's
                        // swipe read as less smooth than the cover screen's equivalent
                        // (CompactGarage), which never had that extra layer. Matching
                        // it here: the raw continuous offset drives the transform
                        // directly, no secondary spring in between.
                        // No blur, no rotationZ tilt -- tried both a position-driven
                        // and later a velocity-driven blur here, and the tilt on top
                        // of the fade/scale, and all of it together read as worse
                        // than the plain fade/scale alone. Just that now.
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            val off = ((page - exPager.currentPage).toFloat() + exPager.currentPageOffsetFraction).let { abs(it).coerceIn(0f, 1f) }
                            alpha = 1f - off * 0.2f
                            scaleX = 1f - off * 0.06f
                            scaleY = 1f - off * 0.06f
                        }) {
                            val pv = vehicles[exReal(page)]
                            CarThemeOverride(
                                paletteId = appearance.carCustomPaletteIds[pv.vin],
                                customPalettes = appearance.customPalettes,
                                themeMode = appearance.themeMode,
                                vibrancy = appearance.vibrancy,
                            ) {
                                ExpandedCar(pv, state, vm, flipped = appearance.columnsFlipped)
                            }
                        }
                    }
                    StatusBarScrim()
                    if (count > 1) {
                        PagerDots(
                            current = exReal(exPager.currentPage),
                            count = count,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).alpha(dotsAlpha),
                            onRefresh = { vm.refreshStatus(vehicles[exReal(exPager.settledPage)]) },
                        )
                    }
                }
            } else {
                val pageCount = (count + perPage - 1) / perPage
                // Infinite wrap-around, same technique as the expanded pager
                // and the cover screen's tile pager: start in the middle of a
                // huge virtual range and map each virtual page back onto a
                // real block of cars with modulo.
                val loopMulti = pageCount > 1
                val virtualPageCount = if (loopMulti) pageCount * 1000 else pageCount
                val initialBlock = (state.currentIndex.coerceIn(0, count - 1)) / perPage
                val pager = rememberPagerState(
                    initialPage = (if (loopMulti) virtualPageCount / 2 else 0) + initialBlock,
                ) { virtualPageCount }
                fun realBlock(virtualPage: Int) = ((virtualPage % pageCount) + pageCount) % pageCount
                LaunchedEffect(pager, perPage) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        vm.selectIndex((realBlock(page) * perPage).coerceIn(0, count - 1))
                    }
                }
                // The above only pushes the pager's own settles into
                // state.currentIndex, never the other direction -- so an
                // external change (a widget/shortcut tap selecting a specific
                // car while this pager was already composed on a different
                // one) updated currentIndex, and the floating name pill below
                // read it correctly, but the pager itself just sat there on
                // whatever car it last settled on. A widget tap always means
                // "look at this car now," so jump (no animated fly-through
                // across a potentially large virtual-page delta) the instant
                // currentIndex moves out from under the page actually shown.
                LaunchedEffect(state.currentIndex) {
                    val targetBlock = state.currentIndex.coerceIn(0, count - 1) / perPage
                    val delta = targetBlock - realBlock(pager.currentPage)
                    if (delta != 0) pager.scrollToPage(pager.currentPage + delta)
                }
                // Hoisted pill state for single-car-per-page (perPage == 1) mode.
                var carNameVisible by remember { mutableStateOf(false) }
                var scrollToTopFn by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
                val pillScope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        // Same fade/scale transition the expanded single-car pager
                        // above uses (see its own comment for why: the continuous
                        // offset is read only inside graphicsLayer{} below, draw-phase
                        // only, and the secondary "snap bounce" spring this used to
                        // multiply in is gone -- it lagged the visual response behind
                        // the actual drag for the whole gesture, which is what made
                        // this pager's swipe read as less smooth than the cover
                        // screen's equivalent). This, the default view most people
                        // see swiping between cars day to day, previously had no
                        // per-page transform at all, just a plain flat scroll.
                        val start = realBlock(page) * perPage
                        val end = minOf(start + perPage, count)
                        // No blur, no rotationZ tilt -- see the expanded pager above.
                        Row(
                            Modifier.fillMaxSize().graphicsLayer {
                                val off = ((page - pager.currentPage).toFloat() + pager.currentPageOffsetFraction).let { abs(it).coerceIn(0f, 1f) }
                                alpha = 1f - off * 0.2f
                                scaleX = 1f - off * 0.06f
                                scaleY = 1f - off * 0.06f
                            },
                        ) {
                            for (i in start until end) {
                                val gv = vehicles[i]
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    CarThemeOverride(
                                        paletteId = appearance.carCustomPaletteIds[gv.vin],
                                        customPalettes = appearance.customPalettes,
                                        themeMode = appearance.themeMode,
                                        vibrancy = appearance.vibrancy,
                                    ) {
                                        VehicleDetailContent(
                                            gv, state, vm,
                                            onExpand = if (canExpand) ({ vm.expand(i) }) else null,
                                            reserveHeaderEnd = canExpand && i == end - 1,
                                            onNameHiddenChanged = if (perPage == 1) { hidden, scrollFn ->
                                                carNameVisible = hidden
                                                scrollToTopFn = scrollFn
                                            } else null,
                                            // Only hide the per-car pull indicator in the
                                            // multi-car grid (perPage > 1) -- a prior fix
                                            // meant for the grid only ended up applying here
                                            // unconditionally, silently killing the single-
                                            // car view's refresh feedback too.
                                            hideIndicator = perPage > 1,
                                        )
                                    }
                                }
                            }
                            repeat(perPage - (end - start)) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    StatusBarScrim()
                    // Floating animated page indicator (no thin top bar).
                    if (pageCount > 1) {
                        PagerDots(
                            current = realBlock(pager.currentPage),
                            count = pageCount,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).alpha(dotsAlpha),
                            onRefresh = { vm.refreshStatus(vehicles[state.currentIndex]) },
                        )
                    }
                    // Grid mode (perPage > 1, wide/large screens) hides each
                    // card's own pull-to-refresh indicator above -- state.refreshing
                    // is one app-wide flag, not per-car, so leaving them unhidden
                    // would light up every visible card's spinner for a refresh
                    // that only touched one of them. But that left a real gap:
                    // pageCount == 1 (every car already fits on one page, common
                    // on tablets) meant PagerDots above never renders either, so
                    // pulling to refresh in the grid had *zero* visual feedback of
                    // any kind. One shared, real M3 Expressive indicator here
                    // covers every grid case, page dots or not.
                    if (perPage > 1) {
                        AnimatedVisibility(
                            visible = state.refreshing,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp),
                        ) {
                            LoadingIndicator()
                        }
                    }
                    // Hoisted car-name pill — centered at top, slides in/out vertically.
                    if (perPage == 1) {
                        AnimatedVisibility(
                            visible = carNameVisible,
                            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 },
                            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        ) {
                            // A percent-based RoundedCornerShape(50) (radius = half of
                            // min(width, height)) here -- unlike the other two name
                            // pills below, which never resize -- would make dropShadow/
                            // ambientRing's cached outline (see DropShadow.kt: only
                            // rebuilt when its own `size` read changes) chase this
                            // pill's own width while the Row's animateContentSize
                            // below is mid-transition, visibly lagging a beat behind
                            // the pill's own (always-correct, uncached) Surface clip --
                            // exactly the "square shadow that snaps right after a
                            // second" a user would see. A fixed 24dp radius (half the
                            // pill's own 48dp height) gives the identical resting pill
                            // shape without the corner radius depending on a value
                            // that's animating out from under it.
                            val pillShape = RoundedCornerShape(24.dp)
                            Surface(
                                onClick = { pillScope.launch { scrollToTopFn?.invoke() } },
                                shape = pillShape,
                                // Was a flat surfaceContainerHighest + shadowElevation --
                                // every other piece of floating chrome (FloatingIcon, the
                                // other two name pills) uses this same glass treatment;
                                // this one was quietly left on the old, pre-glass look.
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.ambientRing(pillShape).dropShadow(pillShape).frostedRim(pillShape),
                            ) {
                                Row(
                                    Modifier
                                        .heightIn(min = 48.dp)
                                        .animateContentSize(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AnimatedContent(
                                        targetState = state.currentIndex,
                                        transitionSpec = {
                                            val dir = if (targetState > initialState) 1 else -1
                                            (slideInHorizontally(tween(200)) { it * dir / 3 } +
                                                fadeIn(tween(200))) togetherWith
                                                fadeOut(tween(120))
                                        },
                                        label = "carNamePill",
                                    ) { idx ->
                                        Text(
                                            vehicles.getOrNull(idx)?.name ?: "",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                        )
                                    }
                                    if (vehicles.size > 1) {
                                        AnimatedContent(
                                            targetState = state.currentIndex,
                                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                                            label = "carNamePillCount",
                                        ) { idx ->
                                            Text(
                                                "${idx + 1} / ${vehicles.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Back/flip ride the refresh shift with the page content during a pull-
        // to-refresh; Settings stays put -- it's a persistent nav target, not
        // page-local chrome, so it shouldn't wander while pulling to refresh.
        if (expandedByUser != null) {
            FloatingIcon(
                icon = Icons.Filled.ArrowBack,
                description = "Back to all cars",
                onClick = { vm.collapse() },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().offset(y = refreshShift),
            )
        }
        if (expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.SwapHoriz,
                description = "Flip columns",
                onClick = { vm.setColumnsFlipped(!appearance.columnsFlipped) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 52.dp).offset(y = refreshShift),
            )
        }
        FloatingIcon(
            icon = Icons.Filled.Settings,
            description = "Settings",
            onClick = { vm.openSettings() },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
        )
    }
    }
}

/**
 * Cover-screen layout: swipe left/right for cars, up/down for section tiles.
 *
 * Owns one [HorizontalPager] (`pager`) for switching between cars, using the
 * same "virtual page count = real count * 1000, start in the middle, map
 * back with modulo" trick as the other car pagers in this file to fake
 * infinite wrap-around. Each car's page then hosts its own vertical tile
 * pager/scrubber further down (not shown in this snippet) for swiping
 * between that car's pebbles; `scrubbing` is shared mutable state that, when
 * true, disables `userScrollEnabled` on this horizontal pager so a
 * long-press-drag scrub of the vertical tile indicator can't accidentally
 * also trigger a car-switch swipe underneath it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactGarage(state: UiState, vm: AppViewModel, appearance: SettingsStore.Appearance) {
    val vehicles = state.vehicles
    val count = vehicles.size
    // count - 1 goes negative with zero cars, and coerceIn(0, -1) throws
    // (min > max) before the pager below ever gets a chance to handle an
    // empty list gracefully -- the caller's own LaunchedEffect already
    // anticipates this exact case (compact && vehicles.isEmpty()) with its
    // own message, so it's a real state to guard, not a hypothetical one.
    if (count == 0) {
        EmptyScreen(vm)
        return
    }
    // Infinite wrap-around, matching every other car-switching pager in the
    // app (the expanded pager, the default grid) and the cover screen's own
    // tile pager, which already looped.
    val loopCars = count > 1
    val virtualCarCount = if (loopCars) count * 1000 else count
    val pager = rememberPagerState(
        initialPage = (if (loopCars) virtualCarCount / 2 else 0) + state.currentIndex.coerceIn(0, count - 1),
    ) { virtualCarCount }
    fun realCar(virtualPage: Int) = ((virtualPage % count) + count) % count
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.selectIndex(realCar(it)) }
    }
    // Mirror of the default garage pager's own fix: react to currentIndex
    // changing out from under an already-composed pager (e.g. a widget tap
    // selecting a specific car while the cover screen was already showing a
    // different one) by snapping to it, instead of only ever pushing this
    // pager's own settles into currentIndex one-way.
    LaunchedEffect(state.currentIndex) {
        val target = state.currentIndex.coerceIn(0, count - 1)
        val delta = target - realCar(pager.currentPage)
        if (delta != 0) pager.scrollToPage(pager.currentPage + delta)
    }
    // True while the page scrubber is active; suspends car-switching swipes so a
    // scrub gesture can't be hijacked into flipping to the next car.
    val scrubbing = remember { mutableStateOf(false) }
    // Hide the page indicators while a refresh is in flight (pull-to-refresh /
    // manual refresh) so the loading indicator owns the screen. Shared by both
    // dot rows below (car-switch AND per-car tile) instead of each keeping its
    // own separate Animatable of the exact same value.
    val dotsAlpha by animateFloatAsState(
        targetValue = if (state.refreshing) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "coverDotsFade",
    )
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !scrubbing.value,
            beyondViewportPageCount = 1,
        ) { page ->
            val v = vehicles[realCar(page)]
            val pageOff by remember(page) {
                derivedStateOf {
                    val delta = ((page - pager.currentPage).toFloat() + pager.currentPageOffsetFraction)
                    abs(delta).coerceIn(0f, 1f)
                }
            }
            // No blur -- see the other two car pagers' history for why: a plain
            // Modifier.blur(x.dp) reconstructs and re-lays-out its own modifier
            // node on every drag frame (the jitter this exact pattern caused
            // elsewhere), and this cover-screen pager had never actually been
            // updated when that got fixed there. Just the cheap graphicsLayer
            // fade/scale transforms now, consistent with the other pagers.
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = 1f - pageOff * 0.2f
                scaleX = 1f - pageOff * 0.06f
                scaleY = 1f - pageOff * 0.06f
            }) {
                CarThemeOverride(
                    paletteId = appearance.carCustomPaletteIds[v.vin],
                    customPalettes = appearance.customPalettes,
                    themeMode = appearance.themeMode,
                    vibrancy = appearance.vibrancy,
                ) {
                    CompositionLocalProvider(LocalCoverScrubbing provides scrubbing) {
                        CompactCar(v, state, vm, dotsAlpha)
                    }
                }
            }
        }
        // Car-switching dots, hoisted out of CompactCar (a per-page composable)
        // and up to here -- a sibling of the whole pager, not inside any one
        // page's fade/scale graphicsLayer -- so it doesn't itself fade and
        // shrink along with the outgoing/incoming car during a swipe, exactly
        // like every other car pager's PagerDots already stays put outside
        // the per-page transform.
        if (count > 1) {
            PagerDots(
                current = realCar(pager.currentPage),
                count = count,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .alpha(dotsAlpha),
                // No hold-to-refresh here -- the cover screen's own edge-trace
                // gesture (drag down from the top edge) is already the refresh
                // affordance in this mode; the dots are display-only.
                onRefresh = null,
            )
        }
    }
}

/**
 * One car's page inside [CompactGarage]'s pager: a vertical stack of pebble
 * "tiles" (main summary, climate, charge, location, ...), one per screen,
 * navigated with the same infinite-wrap virtual-page trick as the car
 * pager itself. Also owns three independent, cover-screen-only concerns
 * layered into the same [Box]:
 *  - Camera-cutout avoidance: detects which edge ([cameraEdgeOf]) a punch-hole
 *    camera sits against and pads only that edge enough to clear it, then
 *    draws a decorative ring around the hole so it reads as intentional.
 *  - The edge-trace refresh gesture: a long-press-and-hold that fills an
 *    animated ring around the screen edge over 1.2s; completing the hold
 *    (without releasing or moving past touch slop) triggers a refresh. Its
 *    pointerInput lives on the outer parent [Box], deliberately relying on
 *    Compose's leaf-to-root gesture dispatch so [VerticalPager]'s own drag
 *    recognizer (a child, and therefore evaluated first) gets first claim on
 *    any real vertical drag before this handler ever sees it.
 *  - Per-tile scroll position (`tileScrollStates`), keyed by tile name so a
 *    tall tile's scroll offset survives being paged away from and back to,
 *    and survives the user reordering pebbles (unlike keying by index).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCar(v: Vehicle, state: UiState, vm: AppViewModel, dotsAlpha: Float) {
    val status = state.statusFor(v)
    val isGen5W = remember(v.brand, v.generation) { v.isGen5W }
    // Cover-screen tiles follow the same order the user arranged the pebbles in
    // (state.sectionsFor). "summary" maps to the always-present "main" tile;
    // "controls" has no cover tile so it falls away. If summary was somehow
    // dropped, "main" is prepended so the cover screen always has a home tile.
    val tiles = state.sectionsFor(v).mapNotNull { section ->
        when (section) {
            "summary" -> "main"
            else -> section.takeIf {
                it in CompactKnownTiles &&
                    (it != "charge" || state.hasBattery(v)) &&
                    (it != "ai" || state.aiEnabled) &&
                    // Trips: EV-only feed AND not served by Gen5W head units --
                    // gate on both so a gas car or a Gen5W car shows no empty tile.
                    (it != "trips" || (state.hasBattery(v) && !isGen5W)) &&
                    !state.isPebbleHidden(v.vin, it)
            }
        }
    }.let { ordered -> if ("main" in ordered) ordered else listOf("main") + ordered }
    // Infinite wrap-around: start in the middle of a huge virtual range and map
    // each virtual page back onto a real tile with modulo.
    val loop = tiles.size > 1
    val virtualCount = if (loop) tiles.size * 1000 else tiles.size
    val start = if (loop) virtualCount / 2 else 0
    val vPager = rememberPagerState(initialPage = start) { virtualCount }
    val current = ((vPager.currentPage % tiles.size) + tiles.size) % tiles.size
    // Per-tile scroll states, keyed by tile name so position persists across
    // pager recycling AND reordering. Tall tiles scroll their own content; the
    // VerticalPager then nested-scrolls to the next/previous tile once a tile is
    // scrolled to its edge.
    val tileScrollStates = remember { mutableMapOf<String, ScrollState>() }
    // Suspend native tile paging while the right-rail scrubber is driving the
    // pager, so a scrub drag can't also be read as a page swipe.
    val coverScrubbing = LocalCoverScrubbing.current

    // ---- Camera cutout detection ----
    // Read the front camera's bounding rect from the display cutout API.
    // boundingRects are in screen pixels (display coordinate system), which
    // aligns with the edge-to-edge Canvas coordinate space used below.
    val view = LocalView.current
    val density = LocalDensity.current
    val cameraHole: android.graphics.Rect? = remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()
        else null
    }
    // Was always treated as a top cutout regardless of where it actually sat
    // -- fine for the common case, wrong for any cover screen whose cutout
    // coordinate space reports it against a different edge. Figure out which
    // edge it's really flush against, then clear THAT edge by however much
    // room the cutout actually needs, leaving the other three at their
    // normal cover-screen insets instead of blindly padding the top.
    val cameraEdge = remember(cameraHole, view) { cameraEdgeOf(cameraHole, view.width, view.height) }
    // The gap from the screen edge, past the cutout, plus a comfortable
    // margin -- i.e. exactly how much dead space this side needs reserved
    // so content flows around the camera instead of under it.
    val cameraClearance: Dp? = cameraHole?.let { r ->
        with(density) {
            val clearancePx = when (cameraEdge) {
                CameraEdge.TOP -> r.bottom
                CameraEdge.BOTTOM -> view.height - r.top
                CameraEdge.LEFT -> r.right
                CameraEdge.RIGHT -> view.width - r.left
                null -> 0
            }
            clearancePx.toDp() + 12.dp
        }
    }
    // Decorative ring color — subtle outline that acknowledges the camera hole.
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    // ---- Edge-trace refresh gesture ----
    // Long-press anywhere on the cover screen to trace a line around the edge.
    // When the line completes its full circuit, trigger a refresh. This is a
    // cover-screen-only interaction (the normal phone layout doesn't use it).
    val edgeTraceProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var edgeTraceHolding by remember { mutableStateOf(false) }
    // The tile-scrubber dots (VerticalPagerDots) are a sibling inside this same
    // Box, so a press over them still reaches this pointerInput during the
    // normal ancestor dispatch -- without carving out their bounds, holding
    // the dots to scrub also started the edge-trace refresh ring underneath,
    // since edge-trace begins timing on raw down regardless of what else the
    // touch lands on. Populated by the dots' own onGloballyPositioned below.
    var dotsBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    LaunchedEffect(edgeTraceHolding) {
        if (edgeTraceHolding) {
            edgeTraceProgress.snapTo(0f)
            edgeTraceProgress.animateTo(
                1f,
                animationSpec = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
            if (edgeTraceHolding) {
                // Only refresh if the user is still holding (didn't release early).
                vm.refreshStatus(v)
            }
            edgeTraceHolding = false
        } else if (edgeTraceProgress.value > 0f) {
            // Released (or cancelled into a swipe) before completing the hold --
            // ease the partial ring back to nothing instead of leaving it frozen
            // at whatever progress it had reached.
            edgeTraceProgress.animateTo(0f, androidx.compose.animation.core.tween(200))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Edge-trace refresh gesture lives here, on the actual PARENT of
            // VerticalPager below, not on a separate sibling Box overlapping
            // it -- sibling dispatch order between two unrelated composables
            // is ambiguous and kept letting this steal the vertical swipe
            // despite two earlier attempts (never consuming; then watching on
            // the Final pass). Parent/child order is NOT ambiguous: the
            // default Main pass runs leaf-to-root, so VerticalPager's own
            // drag recognizer (the child) always gets first crack at a given
            // event, and by the time it bubbles up to this parent's handler,
            // change.isConsumed already reflects whether the pager claimed
            // it. This is the actual textbook nested-gesture-priority
            // pattern, not another guess at pass ordering between siblings.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A press starting inside the tile-scrubber dots' own hit
                    // area belongs entirely to their long-press-to-scrub
                    // gesture -- don't also start timing an edge-trace hold
                    // for it (see dotsBounds' declaration above).
                    if (dotsBounds?.contains(down.position) == true) return@awaitEachGesture
                    edgeTraceHolding = true
                    val slop = viewConfiguration.touchSlop
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed || change.isConsumed) break
                            val dx = abs(change.position.x - down.position.x)
                            val dy = abs(change.position.y - down.position.y)
                            if (dx > slop || dy > slop) break
                        }
                    } finally { if (edgeTraceHolding) edgeTraceHolding = false }
                }
            },
    ) {
        // Native vertical paging. The pager owns the swipe gesture and pages on
        // any vertical drag; tall tiles scroll their own content first and the
        // pager nested-scrolls to the next/previous tile once a tile is at its
        // edge. The car-switching HorizontalPager is orthogonal, so left/right
        // swipes go to it and up/down swipes go here without any custom gesture
        // arbitration. Paging is suspended while the right-rail scrubber is active.
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = coverScrubbing?.value != true,
        ) { page ->
            val i = ((page % tiles.size) + tiles.size) % tiles.size
            val tileScroll = tileScrollStates.getOrPut(tiles[i]) { ScrollState(0) }
            CompositionLocalProvider(
                LocalForceExpanded provides true,
                LocalPebbleFillHeight provides true,
                LocalCoverScrollState provides tileScroll,
            ) {
                // Baseline insets when that edge isn't the one the camera is
                // cut into; maxOf below only ever grows a side past its
                // baseline to clear the cutout, never shrinks it.
                val baseStart = coverScaled(10.dp)
                val baseTop = coverScaled(10.dp)
                // Was 24.dp -- on top of navigationBarsPadding() already
                // reserving the system nav bar's own inset below, and
                // PebbleShell's fillHeight body padding another 10.dp of its
                // own at the very bottom, that stacked into a noticeable band
                // of genuinely empty space at the bottom of every cover-screen
                // tile. 12.dp is still real breathing room above the nav bar
                // without compounding into dead space.
                val baseBottom = coverScaled(12.dp)
                val baseEnd = if (tiles.size > 1) coverScaled(22.dp) else coverScaled(10.dp)
                Box(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(
                            start = if (cameraEdge == CameraEdge.LEFT) maxOf(baseStart, cameraClearance ?: baseStart) else baseStart,
                            top = if (cameraEdge == CameraEdge.TOP) maxOf(baseTop, cameraClearance ?: baseTop) else baseTop,
                            bottom = if (cameraEdge == CameraEdge.BOTTOM) maxOf(baseBottom, cameraClearance ?: baseBottom) else baseBottom,
                            end = if (cameraEdge == CameraEdge.RIGHT) maxOf(baseEnd, cameraClearance ?: baseEnd) else baseEnd,
                        ),
                ) {
                    when (val tile = tiles[i]) {
                        "main" -> CompactMainTile(v, state, vm)
                        "climate" -> ClimatePebble(v, status, state.seatConfigFor(v), state, vm, Modifier)
                        "charge" -> ChargePebble(v, status, !state.loading, state, vm, Modifier)
                        "location" -> LocationPebble(v, state, vm, Modifier)
                        "weather" -> WeatherPebble(v, state, vm, Modifier)
                        "trips" -> TripsPebble(v, state, vm, Modifier)
                        "info" -> InfoPebble(v, status, state, vm, Modifier)
                        "diagnostics" -> DiagnosticsPebble(v, status, state, vm, Modifier)
                        "ai" -> AiPebble(v, state, vm, Modifier)
                    }
                }
            }
        }
        // Decorative camera ring — drawn over the tile content so it's always
        // visible regardless of which tile is showing. Only rendered when a
        // display cutout was detected (flip-phone cover screen with punch-hole).
        if (cameraHole != null) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = cameraHole.exactCenterX()
                val cy = cameraHole.exactCenterY()
                val holeRadius = cameraHole.width() / 2f
                // Inner circle: clear (transparent) punch matching the camera size.
                drawCircle(
                    color = ringColor,
                    radius = holeRadius + with(density) { 2.dp.toPx() },
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() }),
                )
                // Outer glow ring — slightly larger, very faint, for depth.
                drawCircle(
                    color = ringColor.copy(alpha = ringColor.alpha * 0.4f),
                    radius = holeRadius + with(density) { 5.dp.toPx() },
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = Stroke(width = with(density) { 1.dp.toPx() }),
                )
            }
        }
        // Edge-trace ring: when holding (gesture handler lives on the outer
        // Box now, see above), a line traces the screen edge clockwise from
        // the top-left. Full circuit = refresh. Purely decorative here --
        // this Box has no pointerInput of its own to conflict with anything.
        Box(Modifier.fillMaxSize()) {
            if (edgeTraceProgress.value > 0.001f) {
                val accent = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = with(density) { 3.dp.toPx() }
                    val inset = stroke / 2f
                    val rect = androidx.compose.ui.geometry.Rect(
                        inset, inset, size.width - stroke, size.height - stroke
                    )
                    // Full perimeter: 2*(w+h). Sweep starts at -90deg (12 o'clock)
                    // and goes clockwise; -90 to 270deg = 360deg.
                    drawArc(
                        color = accent.copy(alpha = edgeTraceProgress.value.coerceIn(0f, 1f) * 0.85f),
                        startAngle = -90f,
                        sweepAngle = 360f * edgeTraceProgress.value,
                        useCenter = false,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
            }
        }
        // Vertical page dots on the right edge - show which pebble tile is visible.
        // (Car-switching dots are hoisted up to CompactGarage -- see there.)
        if (tiles.size > 1) {
            VerticalPagerDots(
                current = current,
                count = tiles.size,
                tiles = tiles,
                onPageJump = { targetTile ->
                    val currPage = vPager.currentPage
                    val currTile = ((currPage % tiles.size) + tiles.size) % tiles.size
                    val delta = targetTile - currTile
                    vPager.scrollToPage((currPage + delta).coerceIn(0, virtualCount - 1))
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .alpha(dotsAlpha)
                    .onGloballyPositioned { dotsBounds = it.boundsInParent() },
            )
        }
    }
}

/** Vertical sibling of [PagerDots] for the cover-screen tile stack.
 *
 * Long-pressing the indicator expands it into a scrubber: slide finger up/down
 * to jump between pages quickly. Each 14 dp of drag moves one page.
 */
@Composable
private fun VerticalPagerDots(
    current: Int,
    count: Int,
    tiles: List<String>,
    onPageJump: suspend (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubStartPage by remember { mutableIntStateOf(0) }
    var scrubAccumY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val jumpScope = rememberCoroutineScope()
    // Shorter travel per page = a more sensitive scrub.
    val pxPerPage = with(density) { 14.dp.toPx() }
    // Shared flag so the parent HorizontalPager can lock car-switching swipes.
    val coverScrubbing = LocalCoverScrubbing.current
    val haptics = LocalHaptics.current

    // Drag down → higher page index (later tiles); drag up → lower index (earlier tiles).
    val scrubTargetPage by remember {
        derivedStateOf {
            (scrubStartPage + (scrubAccumY / pxPerPage).roundToInt()).coerceIn(0, count - 1)
        }
    }
    // Same tick-per-step convention as AnimatedSlider/MorphSegmented; the first
    // firing (right as scrubbing starts) doubles as a "scrub mode entered" tick,
    // matching ReorderColumn's onDragStart tick for the analogous pebble-drag gesture.
    LaunchedEffect(scrubTargetPage, scrubbing) {
        if (scrubbing) { haptics?.tick(); onPageJump(scrubTargetPage) }
    }

    fun tileName(t: String) = when (t) {
        "main" -> "Car"
        else -> t.replaceFirstChar { it.uppercase() }
    }

    val hPad by animateDpAsState(if (scrubbing) 18.dp else 6.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubHPad")
    val vPad by animateDpAsState(if (scrubbing) 18.dp else 10.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubVPad")
    val itemSpacing by animateDpAsState(if (scrubbing) 14.dp else 6.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubSpacing")
    val cornerRadius by animateDpAsState(if (scrubbing) 20.dp else 100.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubCorner")
    val surfaceAlpha by animateFloatAsState(if (scrubbing) 0.92f else 0.7f, label = "scrubAlpha")

    Box(
        // The resting pill is only as wide as its 7dp dot column plus 6dp
        // padding on each side (~19dp) -- a fifth of the app's own 48dp
        // minimum touch target (FloatingIcon, standard IconButtons), on this
        // screen's most cramped device widths, for the only way to enter the
        // scrub gesture. The gesture/semantics live on this wider invisible
        // Box; the Surface below stays visually as narrow as before.
        modifier = modifier
            .widthIn(min = 48.dp)
            .pointerInput(count) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    longPress.consume()
                    scrubbing = true
                    coverScrubbing?.value = true
                    scrubStartPage = current
                    scrubAccumY = 0f
                    try {
                        verticalDrag(longPress.id) { change ->
                            change.consume()
                            scrubAccumY += (change.position - change.previousPosition).y
                        }
                    } finally {
                        // Always clear, even if the gesture is cancelled, so the
                        // parent never gets stuck with car-switching disabled.
                        scrubbing = false
                        coverScrubbing?.value = false
                    }
                }
            }
            // Entirely gesture-driven (long-press-then-drag-to-scrub) with no
            // semantics at all -- with TalkBack's touch exploration
            // intercepting single-finger gestures, this was both unreachable
            // as its own focus stop and the scrub gesture itself couldn't be
            // performed. contentDescription announces which tile is showing;
            // customActions exposes a direct "go to this tile" action per
            // tile (onPageJump is the same suspend jump function the scrub
            // gesture already calls, so this is the exact same code path, not
            // a parallel one that could drift out of sync).
            .semantics {
                contentDescription = "Showing ${tileName(tiles.getOrElse(current) { "" })} tile, ${current + 1} of $count"
                customActions = tiles.mapIndexedNotNull { i, t ->
                    if (i == current) return@mapIndexedNotNull null
                    CustomAccessibilityAction("Go to ${tileName(t)}") {
                        jumpScope.launch { onPageJump(i) }
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                // Same gap as PagerDots below -- only ever had Material's own weak
                // tonal shadowElevation, no real shadow or rim, on a pill that
                // floats over the same unpredictable car-photo backgrounds.
                .ambientRing(RoundedCornerShape(cornerRadius))
                .dropShadow(RoundedCornerShape(cornerRadius))
                .frostedRim(RoundedCornerShape(cornerRadius)),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = surfaceAlpha),
        ) {
            Column(
                Modifier.padding(horizontal = hPad, vertical = vPad),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
                horizontalAlignment = Alignment.End,
            ) {
                repeat(count) { i ->
                    val selected = i == current
                    val scrubSelected = scrubbing && i == scrubTargetPage
                    val highlight = selected || scrubSelected
                    val dotH by animateDpAsState(
                        if (highlight) 28.dp else 7.dp,
                        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                        label = "vdotH",
                    )
                    val dotW by animateDpAsState(
                        if (scrubbing) 10.dp else 7.dp,
                        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                        label = "vdotW",
                    )
                    val color by androidx.compose.animation.animateColorAsState(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            scrubSelected -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        label = "vdotC",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (scrubbing) {
                            Text(
                                tileName(tiles[i]),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                                color = color,
                            )
                        }
                        Box(Modifier.width(dotW).height(dotH).clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

/** The dense main tile: faded car photo behind name, gauge and key controls. */
@Composable
private fun CompactMainTile(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val img = state.imageUrls[v.vin]
    val scheme = MaterialTheme.colorScheme

    // Entrance animation: slide up gently + fade in on first composition.
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(24f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(350)) }
        launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
    }

    // A themed Surface establishes the correct content colour for ALL text inside
    // (otherwise text on the cover screen falls back to the default black).
    Surface(
        // Lambda graphicsLayer: the non-lambda overload read alpha/offsetY in
        // composition, recomposing this full-screen Surface every frame of the
        // entrance animation; the lambda form re-reads them in the draw phase only.
        modifier = Modifier.fillMaxSize().graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
        },
        // Matches PebbleCornerExpanded so the "main" tile's corners agree with the
        // other (Pebble-wrapped) tiles it shares the same VerticalPager with.
        shape = RoundedCornerShape(PebbleCornerExpanded),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = rememberPhotoModel(img),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Was 0.22f -- on a cover screen's already-dark surfaceContainer
                    // this read as barely-there rather than a photo background. A
                    // top/bottom gradient scrim (below) keeps the title row and
                    // bottom actions legible now that the photo itself is much more
                    // visible, instead of dimming the whole tile uniformly to get there.
                    modifier = Modifier.fillMaxSize().alpha(0.5f),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to scheme.surfaceContainer.copy(alpha = 0.6f),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to scheme.surfaceContainer.copy(alpha = 0.6f),
                        ),
                    ),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().alpha(0.18f)
                        .background(carTonalBrush(scheme)),
                )
            }
            Column(Modifier.fillMaxSize().padding(coverScaled(14.dp)), verticalArrangement = Arrangement.spacedBy(coverScaled(10.dp))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The car-switching dots live at top-center of the screen
                    // (see CompactCar), not here.
                    Text(
                        v.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FloatingIcon(Icons.Filled.Refresh, "Refresh", { vm.refreshStatus(v) }, outerPadding = 2.dp)
                    FloatingIcon(Icons.Filled.Settings, "Settings", { vm.openSettings() }, outerPadding = 2.dp)
                }
                // Centre the live-status + lock group so the tile reads as one
                // balanced block instead of top-clustered with a big gap below.
                Spacer(Modifier.weight(1f))
                LastUpdatedLabel(v, state)
                ChargeFuelBar(status, state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v),
                    metric = vm.appearance.collectAsState().value.unitSystem == "metric")
                Spacer(Modifier.height(6.dp))
                // Flush with the 14 dp tile padding already on this Column, unlike
                // the dual-column/pebble callers' extra 26 dp start inset - cover
                // screens are narrow enough that the label text needs the width.
                PrimaryActions(v, state, vm, contentPadding = PaddingValues(0.dp))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A soft blurred scrim behind the status bar so scrolling content underneath
 * (a car photo, Aurora, dense text) doesn't fight the system clock/battery
 * icons drawn on top of it. Not the normal (non-cover-screen) layouts -- the
 * cover screen already reserves real space above its content instead of
 * drawing under the status bar at all, so it has nothing to scrim.
 */
@Composable
private fun StatusBarScrim() {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(topInset + 28.dp)
            .background(
                Brush.verticalGradient(
                    listOf(scheme.surface.copy(alpha = 0.55f), Color.Transparent),
                ),
            )
            .blur(18.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
    )
}

/** A small translucent circular icon button used as a floating overlay control.
 *  [outerPadding] is the breathing room around the 48dp circle - the default
 *  (12dp, a 72dp footprint) suits free-floating overlay corners; tight rows
 *  (the cover screen's title row, at 2dp) keep that footprint down to 52dp on
 *  a ~260dp-tall screen. */
@Composable
private fun FloatingIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: Dp = 12.dp,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "floatIconScale",
    )
    // Plain semi-transparent fill (see GlassChrome.kt) -- more transparent
    // than the original flat version per feedback that it read as too opaque.
    // The ambient halo/shadow frame it over car photos.
    Surface(
        onClick = { haptics?.click(); onClick() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = modifier
            .padding(outerPadding)
            .size(48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .ambientRing(CircleShape)
            .dropShadow(CircleShape)
            .appGlassRim(CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

/** Page indicator dots, optionally with long-press-to-refresh — holding the
 *  indicator for one second triggers [onRefresh] (mirrors the watch's
 *  CarNameOverlay pattern). Passing null drops the whole gesture (and its
 *  fill-ring) entirely instead of just disarming the action -- the cover
 *  screen's own edge-trace gesture already owns refresh there, and even a
 *  quick tap-through on the dots (e.g. brushing them mid-swipe) started that
 *  ring filling for a frame, which read as a spurious "refresh" flicker on
 *  every plain press. */
@Composable
private fun PagerDots(
    current: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val expandProgress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }

    if (onRefresh != null) {
        LaunchedEffect(holding) {
            if (holding) {
                expandProgress.snapTo(0f)
                expandProgress.animateTo(
                    1f,
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                )
                onRefresh.invoke()
                // Linger the full ring briefly, then ease it back to nothing --
                // this must happen BEFORE flipping `holding` back to false,
                // because that write re-keys (and thus cancels) this very
                // LaunchedEffect(holding) coroutine, which used to kill the
                // delay+collapse before it ever ran (the ring snapped away).
                delay(300)
                expandProgress.animateTo(0f, tween(200))
                holding = false
            } else if (expandProgress.value > 0f) {
                // Released (or the gesture was cancelled) before the hold
                // completed -- LaunchedEffect(holding) cancels the coroutine
                // above outright when holding flips back to false, which used to
                // leave the ring frozen at whatever fill it had reached instead
                // of easing back to nothing (matches the edge-trace gesture's
                // own release/cancel handling elsewhere on the cover screen).
                expandProgress.animateTo(0f, tween(200))
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Overlay ring that fills as the user holds
        if (onRefresh != null && expandProgress.value > 0.01f) {
            CircularProgressIndicator(
                progress = { expandProgress.value.coerceIn(0f, 1f) },
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Surface(
            modifier = Modifier
                .then(
                    if (onRefresh != null) {
                        Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                haptics?.tick()
                                holding = true
                                try { waitForUpOrCancellation() }
                                finally { holding = false }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                // This whole control is a raw pointerInput gesture (long-press
                // to refresh) with zero semantics -- with TalkBack's touch
                // exploration intercepting single-finger gestures, it was both
                // unreachable as its own focus stop and the long-press gesture
                // itself couldn't be triggered. contentDescription announces
                // which car is showing (the dots' only visual information);
                // onLongClick exposes the refresh gesture as a real
                // accessibility action instead of a gesture no assistive
                // technology can perform.
                .then(
                    if (onRefresh != null) {
                        Modifier.semantics {
                            contentDescription = "Car ${current + 1} of $count"
                            onLongClick("Refresh") { onRefresh(); true }
                        }
                    } else {
                        Modifier.semantics { contentDescription = "Car ${current + 1} of $count" }
                    },
                )
                // Was relying only on Material's own tonal shadowElevation (2dp) --
                // barely-there against a car photo, same gap as every other
                // piece of floating chrome the frostedRim/dropShadow pass
                // already covers (FloatingIcon, the name pill, the Settings
                // pill). This is one of the most visible floating pills in the
                // app (car-switcher dots at the top of the garage), so it
                // shouldn't have been the one left out.
                .ambientRing(CircleShape)
                .dropShadow(CircleShape)
                .frostedRim(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(count) { i ->
                    val selected = i == current
                    val w by animateDpAsState(if (selected) 20.dp else 7.dp, label = "dotW")
                    val color by androidx.compose.animation.animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        label = "dotC",
                    )
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(color))
                }
            }
        }
    }
}

// --- Hero header + charge/fuel bar ---------------------------------------

/**
 * The car's hero card: photo/visual on top, [ChargeFuelBar] below. Corner
 * radius eases between 24dp and 40dp (animateDpAsState) when `charging`
 * flips, as a subtle "something is happening" cue distinct from any text or
 * icon change. Fades and slides up 16dp on first composition
 * (`heroAlpha`/`heroOffset`, both [Animatable]s driven once in
 * `LaunchedEffect(Unit)`) so it enters in step with the rest of the
 * per-car stack rather than popping in instantly.
 */
@Composable
private fun HeroHeader(
    v: Vehicle,
    status: VehicleStatus?,
    imageUrl: String?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    vm: AppViewModel,
    drivingLabel: String? = null,
    dragHandle: Modifier = Modifier,
    height: Dp = 150.dp,
    metric: Boolean = false,
) {
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    val heroAlpha = remember { Animatable(0f) }
    val heroOffset = remember { Animatable(16f) }
    LaunchedEffect(Unit) {
        launch { heroAlpha.animateTo(1f, tween(400)) }
        launch { heroOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }
    val corner by animateDpAsState(
        targetValue = if (charging) 40.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = SoftDamping,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "heroCorner",
    )
    val heroShape = RoundedCornerShape(corner)
    val heroOutline by vm.appearance.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth().then(dragHandle).graphicsLayer {
            alpha = heroAlpha.value
            translationY = heroOffset.value
        }
            // Every other pebble gets this via the shared Pebble() wrapper --
            // the hero card rolls its own Card and was the one card in the
            // whole per-car stack with no shadow or rim at all. The rim half
            // respects the same off-by-default Pebble outline setting, and
            // uses the same bolder border Pebble() does (not frostedRim --
            // see its comment there for why that read as "not working").
            .dropShadow(heroShape, blurRadius = 12.dp, offsetY = 4.dp)
            .then(
                if (heroOutline.pebbleOutline) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), heroShape)
                } else Modifier,
            ),
        shape = heroShape,
    ) {
        Column(Modifier.padding(16.dp)) {
            HeroVisual(v, imageUrl, height)
            Spacer(Modifier.height(16.dp))
            ChargeFuelBar(status, hasBattery, hasFuel, drivingLabel, metric = metric)
        }
    }
}

/**
 * Bloo isn't on the Play Store, so this is its own update surface: a
 * standalone tile pinned directly below the hero tile whenever the checker
 * has found a newer build, animating in/out instead of interrupting with a
 * popup. Collapse/expand reuses the exact same [PebbleShell] every other
 * pebble is built on (this isn't tied to a car/section, hence PebbleShell
 * directly rather than the [Pebble] wrapper) -- collapsed, the header action
 * button doubles as the primary control and shows live download state
 * (Update / downloading % / Install); expanded, it adds install steps, this
 * build's release notes, and Remind-me/Not-now. Every push publishes a
 * rolling GitHub Release (see android.yml) with the raw phone/watch APKs
 * attached as plain public assets, so the primary action can download the
 * APK directly instead of opening a browser page.
 */
@Composable
private fun UpdateAvailableTile(state: UiState, vm: AppViewModel, dragHandle: Modifier = Modifier) {
    val info = state.updateAvailable
    AnimatedVisibility(
        visible = info != null && !state.updateTileDismissed,
        enter = fadeIn(tween(220)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(160)) + shrinkVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
    ) {
        if (info == null) return@AnimatedVisibility
        val context = LocalContext.current
        val hasDirectDownload = info.run.phoneApkUrl != null
        // Keyed on the build number so a genuinely different build (see
        // checkForUpdate's sameBuild check) starts collapsed again rather
        // than inheriting whatever expand state an earlier build was left in.
        var expanded by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
        PebbleShell(
            expanded = expanded,
            onToggle = { expanded = !expanded },
            dragHandle = dragHandle,
            icon = Icons.Filled.SystemUpdate,
            title = "Update available",
            vm = vm,
            summary = info.run.displayTitle?.takeIf { it.isNotBlank() } ?: "Build #${info.run.runNumber}",
            // No containerColor override -- PebbleShell's own default
            // (surfaceVariant) is what every ordinary pebble uses too
            // (Climate, Charge, Info, ...); this used primaryContainer,
            // which read as a special/different-looking tile instead of
            // fitting in with the rest of the per-car stack. AI's pebble is
            // the one deliberate exception (tertiaryContainer) -- this
            // wasn't meant to be another one.
            headerAction = PebbleHeaderAction(
                label = when {
                    state.updateDownloading -> state.updateDownloadProgress?.let { "${(it * 100).roundToInt()}%" } ?: "Downloading…"
                    state.updateApkReady -> "Install"
                    hasDirectDownload -> "Update"
                    else -> "Open"
                },
                icon = if (state.updateApkReady) Icons.Filled.SystemUpdate else Icons.Filled.Download,
                pending = state.updateDownloading,
                onClick = {
                    when {
                        state.updateApkReady -> vm.installDownloadedUpdate()
                        hasDirectDownload -> vm.downloadUpdateInBackground()
                        else -> {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl))) }
                            vm.dismissUpdate()
                        }
                    }
                },
            ),
        ) {
            val scheme = MaterialTheme.colorScheme
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurface,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("To install:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                    Text(
                        if (hasDirectDownload) "1. Tap \"Update\" above" else "1. Download the APK above",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (hasDirectDownload) "2. Tap \"Install\" once it's downloaded" else "2. Open the downloaded file",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Android's Play Protect flags any APK that didn't come
                    // from the Play Store, unsigned-by-Google or not --
                    // without this tip, "Blocked by Play Protect" reads
                    // like the install genuinely failed rather than one
                    // more tap.
                    Text(
                        "3. If you see \"Blocked by Play Protect\", tap \"More details\" → \"Install anyway\"",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            info.run.releaseNotes?.let { notes ->
                Text("What's new", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
                Text(
                    notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MorphTextButton("Remind me", onClick = vm::snoozeUpdate, enabled = !state.updateDownloading, modifier = Modifier.weight(1f))
                MorphTextButton("Not now", onClick = vm::dismissUpdate, enabled = !state.updateDownloading, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The tonal primary→tertiary→secondary gradient used as the fallback fill
 *  behind car photos across the garage/settings surfaces. Callers apply their
 *  own `.alpha(...)` where they want it dimmed -- this returns only the brush. */
private fun carTonalBrush(scheme: ColorScheme): Brush =
    Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))

/** The clipped square thumbnail used for a car: the set photo if there is one,
 *  else the [carTonalBrush] fallback with a centered car icon. [cornerRadius]
 *  and [iconSize] vary per caller (the settings card vs. the tiles header). */
@Composable
private fun CarThumb(img: String?, size: Dp, cornerRadius: Dp, iconSize: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (!img.isNullOrBlank()) {
            AsyncImage(
                model = rememberPhotoModel(img),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(carTonalBrush(scheme)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(iconSize))
            }
        }
    }
}

/** The Coil model for a stored car photo: a [java.io.File] for a locally-cropped
 *  absolute path, or the raw URL string for a pasted one. */
@Composable
private fun rememberPhotoModel(url: String): Any =
    remember(url) { if (url.startsWith("/")) java.io.File(url) else url }

/** Default = a clean brand gradient. If the user set a photo, show that instead. */
@Composable
private fun HeroVisual(v: Vehicle, imageUrl: String?, height: Dp) {
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(18.dp))
                .background(carTonalBrush(scheme)),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = rememberPhotoModel(imageUrl)
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends
        // seamlessly into the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        AsyncImage(
            model = model,
            contentDescription = v.model,
            contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (transparent) Modifier else Modifier.clip(RoundedCornerShape(18.dp))),
        )
    }
}

/**
 * The battery/fuel percentage readout: headline percent + range, a status
 * line beneath (charging details > driving/parked > plain "Battery"/"Fuel"
 * label, in that priority order), and a gradient progress bar. The bar's
 * fill animates via a spring (`animatedFrac`) rather than snapping to the
 * new percentage, and -- when plugged in -- a small dot marks the
 * charge-limit target percentage on the track so the user can see at a
 * glance how much further it'll charge.
 */
@Composable
private fun ChargeFuelBar(status: VehicleStatus?, hasBattery: Boolean, hasFuel: Boolean, drivingLabel: String? = null, metric: Boolean = false) {
    val fuelPct = status?.fuelLevel
    val pct = status?.percentFor(hasBattery)
    val frac = ((pct ?: 0).coerceIn(0, 100)) / 100f
    val range = status?.rangeMiFor(hasBattery)
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    // Charging time + type, shown in the badge slot (replacing parked/driving,
    // which is hidden while charging) so the pebble doesn't grow taller.
    val chargeMinutes = status?.evStatus?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
    val chargeType = when (status?.evStatus?.batteryPlugin) {
        1 -> "DC"
        2 -> "AC"
        else -> null
    }

    // The state line under the range: charging (with time/type) replaces it while
    // charging, then driving/parked, then a plain battery/fuel descriptor.
    val statusLine = when {
        charging -> buildString {
            append("Charging")
            chargeMinutes?.let { append(" · ${fmtMinutes(it)}") }
            chargeType?.let { append(" · $it") }
        }
        drivingLabel != null -> drivingLabel
        else -> if (hasBattery) "Battery" else "Fuel"
    }
    val statusColor = when {
        charging -> ChargeGreen
        drivingLabel == "Driving" || drivingLabel == "Running" -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
    }

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            // Roll the headline number when it changes.
            RollingNumber(
                text = pct?.let { "$it%" } ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                RollingNumber(
                    text = range?.let { formatDistance(it, metric) } ?: "--",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val animatedStatusColor by androidx.compose.animation.animateColorAsState(
                    statusColor, animationSpec = tween(300), label = "statusLineColor",
                )
                AnimatedContent(
                    targetState = statusLine,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically { -it / 2 })
                    },
                    label = "statusLine",
                ) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedStatusColor,
                        fontWeight = if (charging || drivingLabel == "Driving") FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        // Plug-in hybrid: surface the fuel tank too.
        if (hasBattery && hasFuel && fuelPct != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Fuel $fuelPct%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Expressive motion: the fill springs in with a gentle overshoot.
        val animatedFrac by animateFloatAsState(
            targetValue = frac,
            animationSpec = spring(
                dampingRatio = SoftDamping,
                stiffness = Spring.StiffnessLow,
            ),
            label = "chargeFill",
        )
        // Target-SOC marker: dot at the AC/DC limit, only when plugged in.
        val targetPct = status?.evStatus?.targetForCurrentPlug()
        BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
            // Track + gradient fill (darker green on the left → current green right).
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))
                    .background(ChargeGreen.copy(alpha = 0.18f)),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(maxWidth * animatedFrac.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(9.dp))
                    .background(Brush.horizontalGradient(listOf(ChargeGreenDark, ChargeGreen))),
            )
            if (targetPct != null) {
                val x = maxWidth * (targetPct.coerceIn(0, 100) / 100f)
                Box(
                    Modifier
                        .offset(x = x - 6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                        .align(Alignment.CenterStart),
                )
            }
        }
    }
}

// Was a phone-only re-declaration of the same hex values shared/BlooColors.kt
// already centralizes (bit-identical today, one edit away from silently
// diverging like chargerLabel's text had).
private val ChargeGreen = Color(com.bloo.bluelink.data.BlooColors.chargeGreen)
private val ChargeGreenDark = Color(com.bloo.bluelink.data.BlooColors.chargeGreenDark)

private val SoftDamping get() = com.bloo.uicommon.SoftDamping

/** Shared spring stiffness for the Simple/Advanced mode switch's card
 *  expand/collapse (the outer settings column, each card's own
 *  animateContentSize, and the advanced-only cards' enter/exit) -- slower
 *  than [Spring.StiffnessLow] for a slightly longer, calmer settle, paired
 *  with [SoftDamping] for minimal bounce. All of these must share one spec
 *  or the pieces visibly settle at different times/feels. */
private const val AdvancedModeStiffness = 130f

/**
 * The shared floating/card edge: the app's default frosted rim ([frostedRim]).
 * Call sites keep their normal [glassContainerAlpha] frosted fill. The [tint]
 * param is retained for call-site compatibility but is no longer used.
 */
@Composable
private fun Modifier.appGlassRim(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") tint: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier = this.frostedRim(shape)

/**
 * When true (cover-screen tiles), pebbles render permanently open with no
 * collapse chevron or drag handle - collapsing a full-screen tile makes no sense.
 */
private val LocalForceExpanded = staticCompositionLocalOf { false }

/**
 * When true (cover-screen tiles), a pebble stretches to fill the available height
 * and scrolls internally if its content is taller - so each tile fills the screen.
 */
private val LocalPebbleFillHeight = staticCompositionLocalOf { false }

/** Tile names that [CompactCar] can render — unknown sections are excluded. */
private val CompactKnownTiles = setOf(
    "climate", "charge", "location", "weather", "trips", "info", "diagnostics", "ai"
)

/**
 * When set, [Pebble] in fill-height cover-screen mode uses this scroll state
 * instead of creating a local one — lets the parent observe scroll position
 * to decide whether to switch pager pages or scroll tile content.
 */
private val LocalCoverScrollState = compositionLocalOf<ScrollState?> { null }

/**
 * Shared flag set true while the cover-screen page scrubber is active, so the
 * parent [CompactGarage] can suspend horizontal car-switching swipes during a
 * scrub. Provided around the HorizontalPager content.
 */
private val LocalCoverScrubbing = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/**
 * The live pull-to-refresh distance (0..1+), published by [Refreshable] so the
 * floating overlays in [GarageScreen] (page dots, settings/back/flip buttons)
 * can track the pull in real time instead of only animating once refresh starts.
 */
private val LocalPullFraction =
    staticCompositionLocalOf<androidx.compose.runtime.MutableState<Float>> { mutableStateOf(0f) }

/**
 * A headline number that rolls when it changes: it slides up when the value
 * grows and down when it shrinks (digits extracted from [text] decide the
 * direction), falling back to a cross-fade when there's no number to compare.
 */
@Composable
private fun RollingNumber(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    // Track the previous numeric value so we can roll in the right direction.
    val current = text.filter { it.isDigit() }.toIntOrNull()
    var previous by remember { mutableStateOf(current) }
    val goingUp = (current ?: 0) >= (previous ?: 0)
    LaunchedEffect(current) { previous = current }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val dir = if (goingUp) 1 else -1
            (fadeIn(tween(180)) + slideInVertically { dir * it / 2 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically { -dir * it / 2 })
        },
        label = "num",
    ) { t -> WiggleText(t, style = style, fontWeight = fontWeight, color = color) }
}

/** A coarse, self-ticking "x min ago" string for [millis] (null → null). */
@Composable
private fun rememberRelativeTime(millis: Long?): String? {
    if (millis == null) return null
    // Re-derives the bucket thresholds shared/relativeLabel() already owns
    // (and had already drifted from it -- "d ago" here vs "day ago" there).
    // `now` exists purely to force a recompute on a timer; relativeLabel()
    // reads the wall clock itself.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(millis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    return if (now >= 0) com.bloo.bluelink.data.relativeLabel(millis) else null
}

/** Small "Updated x ago" caption shown prominently under the car name. */
@Composable
private fun LastUpdatedLabel(v: Vehicle, state: UiState, modifier: Modifier = Modifier) {
    val rel = rememberRelativeTime(state.fetchedAt(v)) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Updated $rel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Drag-and-drop reordering --------------------------------------------

/**
 * Animates an item gliding to its new placement when siblings reorder around it,
 * instead of snapping. Used for the non-dragged pebbles so they slide out of the
 * way smoothly. The dragged item is offset manually and must not use this.
 */
private fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var anim by remember { mutableStateOf<Animatable<IntOffset, *>?>(null) }
    this
        .onPlaced { target = it.positionInParent().round() }
        .offset {
            val a = anim ?: Animatable(target, IntOffset.VectorConverter).also { anim = it }
            if (a.targetValue != target) {
                scope.launch { a.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
            a.value - target
        }
}


/**
 * A vertical list whose items can be reordered by long-pressing the supplied
 * [dragHandle] and dragging. Item heights are measured so variable-height rows
 * reorder correctly; the live order is committed via [onReorder] on drop.
 *
 * Designed to live inside an existing scroll container (it is a plain Column).
 *
 * Drag mechanism: `order` is local mutable state (re-synced from [items]
 * whenever nothing is being dragged). `draggingKey` identifies which item is
 * currently held; that item is excluded from [animatePlacement] and instead
 * manually translated by `offsetY`, a running total of vertical drag delta
 * (via [detectDragGesturesAfterLongPress]'s `onDrag`). On every drag tick,
 * `offsetY` is compared against the *next* or *previous* item's measured
 * height (tracked per-key in `heights`, populated by each row's own
 * `onSizeChanged`): once the drag has moved past half that neighbor's
 * height, the two items swap places in `order` and `offsetY` is reduced by
 * that neighbor's height, so the dragged item's on-screen position stays
 * continuous through the swap rather than jumping. Every other (non-dragged)
 * row uses [animatePlacement] to glide smoothly to its new slot when the
 * list order changes underneath it. [staggerInOnColdStart]/[introKey] are
 * unrelated to dragging -- they drive a one-time entrance stagger, see
 * [coldStartIntroPlayed].
 */
@Composable
private fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    // Optional cross-target drag hooks: [onDragMove] reports the live finger
    // position (window coords) of the dragged item; [onDragRelease] is called on
    // drop and, if it returns true, the drop was handled elsewhere (e.g. pinned
    // to the hot spot) so the normal reorder is skipped.
    onDragMove: ((key: Any, windowPointer: Offset) -> Unit)? = null,
    onDragRelease: ((key: Any) -> Boolean)? = null,
    // When true, each item fades/slides in top-to-bottom in quick lockstep the
    // first time this column appears after a fresh process start (see
    // [coldStartIntroPlayed]) -- e.g. the garage's pebble list, so opening the
    // app feels alive instead of the whole screen just popping in at once.
    staggerInOnColdStart: Boolean = false,
    // Identity for the "already played" check above -- distinct per logical
    // column (e.g. each car's VIN), so one column consuming the intro can't
    // rob another (possibly still off-screen/prefetched) column of its own.
    introKey: Any = Unit,
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var dropRipple by remember { mutableStateOf(0L) }
    // Consumed the instant this key is first read, so navigating back to the
    // garage (or a second car's column composing) later never replays it.
    val playIntro = remember(introKey) {
        staggerInOnColdStart && coldStartIntroPlayed.add(introKey)
    }

    // Sync with upstream changes only while not actively dragging.
    LaunchedEffect(items) { if (draggingKey == null) order = items }
    // Ripple animation when a tile is dropped (shows the "weight" of the move).
    val maxRippleScale = remember { Animatable(0f) }
    LaunchedEffect(dropRipple) {
        if (dropRipple != 0L) {
            maxRippleScale.snapTo(0f)
            maxRippleScale.animateTo(1f, tween(300))
            maxRippleScale.animateTo(0f, tween(200))
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEachIndexed { index, item ->
            val k = keyOf(item)
            // Identity key so Compose moves the existing node when the order
            // changes (instead of reusing nodes by slot, which looks janky).
            key(k) {
                val dragging = draggingKey == k
                val dragState = draggingKey != null
                val lift by animateFloatAsState(
                    targetValue = if (dragging) 1.08f else 1f,
                    animationSpec = if (dragging) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
                                   else spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow),
                    label = "lift"
                )
                // Quick top-to-bottom lockstep reveal, once, on a fresh launch.
                val intro = remember { Animatable(if (playIntro) 0f else 1f) }
                LaunchedEffect(Unit) {
                    if (playIntro) {
                        delay(index * 45L)
                        intro.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                    }
                }
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        // Non-dragged items glide to their new slot; the dragged
                        // one is positioned manually via graphicsLayer below.
                        .then(if (dragging) Modifier else Modifier.animatePlacement())
                        .graphicsLayer {
                            translationY = if (dragging) offsetY else (1f - intro.value) * 28.dp.toPx()
                            scaleX = lift
                            scaleY = lift
                            alpha = intro.value
                        }
                        .onSizeChanged { heights[k] = it.height },
                ) {
                    val handleCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val handle = Modifier
                        .onGloballyPositioned { handleCoords.value = it }
                        // The drag gesture below has no TalkBack equivalent at
                        // all -- reordering pebbles/presets/cars was completely
                        // unreachable for screen-reader users. Additive
                        // semantics-only "Move up"/"Move down" actions alongside
                        // the existing gesture (same pattern already used for
                        // MorphSegmented's drag track), reusing the same reorder
                        // + commit logic the drag path uses.
                        .semantics {
                            val cur = order.indexOfFirst { keyOf(it) == k }
                            customActions = listOfNotNull(
                                if (cur > 0) CustomAccessibilityAction("Move up") {
                                    order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                    onReorder(order)
                                    true
                                } else null,
                                if (cur in 0 until order.lastIndex) CustomAccessibilityAction("Move down") {
                                    order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                    onReorder(order)
                                    true
                                } else null,
                            )
                        }
                        .pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingKey = k; offsetY = 0f },
                        onDragEnd = {
                            val handled = onDragRelease?.invoke(k) ?: false
                            draggingKey = null; offsetY = 0f
                            if (!handled) onReorder(order)
                        },
                        onDragCancel = { onDragRelease?.invoke(k); draggingKey = null; offsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            handleCoords.value?.takeIf { it.isAttached }?.let {
                                onDragMove?.invoke(k, it.localToWindow(change.position))
                            }
                            val cur = order.indexOfFirst { keyOf(it) == k }
                            if (cur >= 0) {
                                if (offsetY > 0 && cur < order.lastIndex) {
                                    val nextH = heights[keyOf(order[cur + 1])] ?: 0
                                    if (nextH > 0 && offsetY > nextH / 2f) {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        offsetY -= nextH
                                    }
                                } else if (offsetY < 0 && cur > 0) {
                                    val prevH = heights[keyOf(order[cur - 1])] ?: 0
                                    if (prevH > 0 && -offsetY > prevH / 2f) {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        offsetY += prevH
                                    }
                                }
                            }
                        },
                    )
                    }
                    content(item, handle, dragging)
                }
            }
        }
    }
}

/**
 * A clean, fully custom slider: a rounded track with an accent fill, subtle step
 * ticks, and a circular thumb that springs to the nearest step. Drawn entirely on
 * a Canvas (no Material Slider) so its look is consistent and theme-driven.
 */
@Composable
private fun AnimatedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    accent: Color = MaterialTheme.colorScheme.primary,
    // Fired once, with the final value, when the drag/tap settles — for callers
    // whose real commit is expensive (see the Vibrancy/UI-scale sliders, which
    // otherwise call onValueChange on every drag tick and each one recomposes
    // the whole app since they feed BlooTheme's colorScheme/LocalDensity). Those
    // should update local/visual state cheaply in onValueChange and do the
    // actual expensive write here instead, matching "sync on commit" everywhere
    // else in the app.
    onValueSettled: ((Float) -> Unit)? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var latestValue by remember { mutableFloatStateOf(value) }
    com.bloo.uicommon.AnimatedSlider(
        value = value,
        onValueChange = { latestValue = it; onValueChange(it) },
        valueRange = valueRange,
        steps = steps,
        accent = accent,
        inactiveColor = scheme.surfaceContainerHighest,
        dotOnActive = scheme.onPrimary.copy(alpha = 0.7f),
        dotOnInactive = scheme.onSurfaceVariant.copy(alpha = 0.5f),
        reduceMotion = LocalReduceMotion.current,
        onStepTick = { haptics?.tick() },
        onSettle = { haptics?.click(); onValueSettled?.invoke(latestValue) },
    )
}

@Composable
private fun WiggleText(
    text: String,
    style: TextStyle,
    fontWeight: FontWeight,
    color: Color = Color.Unspecified,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    com.bloo.uicommon.WiggleText(
        text = text,
        style = style.copy(fontWeight = fontWeight, color = resolvedColor),
        reduceMotion = LocalReduceMotion.current,
    )
}

private fun snapToStep(v: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float =
    com.bloo.uicommon.snapToStep(v, range, steps)

/**
 * Softly fades the top/bottom [length] of a vertically scrolling area instead of
 * hard-clipping it at the bounds. The fade only appears on an edge that has more
 * content past it, and eases in as you scroll toward it.
 */
private fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val lenPx = length.toPx()
        val topAlpha = (scroll.value / lenPx).coerceIn(0f, 1f)
        val botAlpha = ((scroll.maxValue - scroll.value) / lenPx).coerceIn(0f, 1f)
        if (topAlpha > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = lenPx,
                ),
                blendMode = BlendMode.DstIn,
                alpha = topAlpha,
            )
        }
        if (botAlpha > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - lenPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
                alpha = botAlpha,
            )
        }
    }


/**
 * Shared state for dragging a pebble onto (or off) the dual-column hot spot. The
 * dragged section and the live finger position (window coords) are tracked here,
 * and the hot-spot slot publishes its window bounds so we can tell when a drag is
 * hovering it.
 */
private class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
    val overSlot: Boolean
        get() = section != null && slotSize.width > 0 &&
            pointer.x in slotTopLeft.x..(slotTopLeft.x + slotSize.width) &&
            pointer.y in slotTopLeft.y..(slotTopLeft.y + slotSize.height)
}

private val LocalHotSeatDrag = staticCompositionLocalOf<HotSeatDrag?> { null }

/** Trivial full-size [Box] wrapper; exists as a distinct composable purely so
 *  the hot-seat drag machinery has a single, stable, named host to reason
 *  about/hang [LocalHotSeatDrag] state around rather than an anonymous Box. */
@Composable
private fun BackdropHost(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}

// --- Full detail ----------------------------------------------------------

/**
 * Single-column car view (phones, and each column of the grid). Everything
 * scrolls together in one [Column] inside [Refreshable] (header row, then
 * the reorderable [PebbleList]). `nameHidden` is a [derivedStateOf] over the
 * scroll position -- once the user has scrolled the car name (roughly its
 * own height) out of view, a floating name pill fades in as a substitute, so
 * scrolling never leaves the screen not knowing which car it's looking at.
 * When [onNameHiddenChanged] is supplied (single-car-per-page mode in
 * [GarageScreen]), that pill is hoisted to the parent instead of rendered
 * inline here, so it can float independent of this column's own layout.
 */
@Composable
private fun VehicleDetailContent(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    reserveHeaderEnd: Boolean = false,
    onNameHiddenChanged: ((Boolean, suspend () -> Unit) -> Unit)? = null,
    hideIndicator: Boolean = false,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Show the floating name pill once the car name has scrolled out of view.
    val nameHidden by remember {
        derivedStateOf { scroll.value > with(density) { (topInset + 56.dp).toPx() } }
    }
    // Propagate nameHidden to the parent when a callback is supplied (hoisted pill).
    if (onNameHiddenChanged != null) {
        LaunchedEffect(nameHidden) {
            onNameHiddenChanged(nameHidden) { scroll.animateScrollTo(0) }
        }
    }
    Refreshable(v, state, vm, hideIndicator = hideIndicator) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Inset spacers (not padding) so content scrolls *behind* the bars.
            Spacer(Modifier.height(topInset + 8.dp))
            CarHeaderRow(v, state, onExpand, reserveHeaderEnd)
            // summary (image+gauge) and controls are reorderable pebbles too.
            PebbleList(v, state, vm)
            Spacer(Modifier.height(bottomInset + 16.dp))
        }
        // Only show the inline pill when no parent is managing it.
        if (onNameHiddenChanged == null) {
            androidx.compose.animation.AnimatedVisibility(
                visible = nameHidden,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            ) {
                Surface(
                    onClick = { scope.launch { scroll.animateScrollTo(0) } },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.ambientRing(RoundedCornerShape(50)).dropShadow(RoundedCornerShape(50)).frostedRim(RoundedCornerShape(50)),
                ) {
                    Box(Modifier.height(48.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(v.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Wide expanded view: critical info in one column, pebbles in the other.
 *
 * `controls` and `pebbles` are held as `@Composable` lambdas (not directly
 * inlined) so [flipped] can freely swap which one renders in the left vs.
 * right [Column] without re-creating either column's content -- each
 * column's own [rememberScrollState] (`controlsScroll`/`pebblesScroll`) is
 * hoisted here rather than created inside `controls`/`pebbles` themselves,
 * so a scroll position sticks with its *content* across a flip rather than
 * with whichever physical column (left/right) currently renders it.
 * [HotspotSlot] lets one pebble be pinned into the info column permanently
 * (excluded from the normal reorderable pebble list via `exclude` above);
 * [HotSeatDrag] (provided via [LocalHotSeatDrag]) is the cross-column drag
 * state that lets a pebble be dragged from the scrolling list directly onto
 * that slot to pin it.
 */
@Composable
private fun ExpandedCar(v: Vehicle, state: UiState, vm: AppViewModel, flipped: Boolean) {
    val hotspot = state.hotspotFor(v.vin)
        ?.takeIf {
            it in state.sectionsFor(v) && !state.isPebbleHidden(v.vin, it) &&
                (it != "trips" || (state.hasBattery(v) && !v.isGen5W)) &&
                (it != "update" || state.updateAvailable != null)
        }
    val hotDrag = remember { HotSeatDrag() }
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state, onExpand = null, reserveEnd = false)
        CriticalContent(v, state, vm)
        HotspotSlot(v, hotspot, state, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        PebbleList(v, state, vm, exclude = setOfNotNull("summary", "controls", hotspot))
    }
    // Hoisted (not recreated on flip) so each column keeps its own scroll position
    // when the columns swap sides, and so the floating name pill below can always
    // watch the column that currently holds CarHeaderRow, wherever it is.
    val controlsScroll = rememberScrollState()
    val pebblesScroll = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Mirrors the single-column view's floating name pill: appears once the header
    // has scrolled out of view, so a wide-screen user never loses track of which
    // car a long dual-column page belongs to.
    val nameHidden by remember {
        derivedStateOf { controlsScroll.value > with(density) { (topInset + 52.dp + 40.dp).toPx() } }
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag) {
    // Was hardcoded hideIndicator = true -- the same "grid-only" flag that
    // hid the pull-to-refresh spinner in the single-car view (fixed in
    // a944a91) also hid it here, in the expanded/wide dual-column detail
    // view, unconditionally. This is a single car's own detail screen, not
    // the multi-car grid the flag was meant for, so the real M3 Expressive
    // indicator should show here too.
    Refreshable(v, state, vm) {
        Box(Modifier.fillMaxSize()) {
        // Animate the swap when the columns are flipped.
        AnimatedContent(
            targetState = flipped,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally { w -> dir * w / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> -dir * w / 4 } + fadeOut())
            },
            label = "flipColumns",
        ) { isFlipped ->
            val leftCol = if (isFlipped) pebbles else controls
            val rightCol = if (isFlipped) controls else pebbles
            val leftScroll = if (isFlipped) pebblesScroll else controlsScroll
            val rightScroll = if (isFlipped) controlsScroll else pebblesScroll
            // Inset spacers (not padding) so content scrolls *behind* the bars;
            // the leading spacer also clears the floating overlay buttons.
            val lead: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(topInset + 52.dp)) }
            val trail: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(bottomInset + 16.dp)) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 960.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(leftScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { lead(); leftCol(); trail() }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().verticalScroll(rightScroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { lead(); rightCol(); trail() }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = nameHidden,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            Surface(
                onClick = { scope.launch { controlsScroll.animateScrollTo(0) } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.ambientRing(RoundedCornerShape(50)).dropShadow(RoundedCornerShape(50)).frostedRim(RoundedCornerShape(50)),
            ) {
              Box(Modifier.height(48.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                  Text(v.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
              }
            }
        }
        }
    }
    }
}

/** A friendly label for a pebble/section id. */
private fun sectionLabel(section: String): String = when (section) {
    "charge" -> "Charge / fuel"
    "climate" -> "Climate"
    "location" -> "Location"
    "weather" -> "Weather"
    "trips" -> "Trips"
    "info" -> "Car info"
    "diagnostics" -> "Diagnostics"
    "controls" -> "Lock / climate"
    else -> section.replaceFirstChar { it.uppercase() }
}

/**
 * The dual-column "hot spot": a fixed slot under the car-info column. When a
 * pebble is pinned here it renders non-collapsible (always open); otherwise it's
 * a chooser to pin one. Pinning moves the pebble out of the scrolling list.
 */
@Composable
private fun HotspotSlot(v: Vehicle, hotspot: String?, state: UiState, vm: AppViewModel) {
    if (hotspot != null) {
        val haptics = LocalHaptics.current
        // Drag the pinned pebble away (long-press, then drag past a threshold) to
        // unpin - the mirror of dragging a pebble onto the slot to pin. The Unpin
        // button does the same thing for discoverability.
        var lifted by remember(hotspot) { mutableStateOf(false) }
        var dragY by remember(hotspot) { mutableFloatStateOf(0f) }
        val lift by animateFloatAsState(if (lifted) 1.03f else 1f, label = "unpinLift")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (lifted) "Release to unpin" else "Pinned",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lifted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MorphTextButton("Unpin", onClick = { vm.setHotspot(v, null) })
            }
            CompositionLocalProvider(LocalForceExpanded provides true) {
                Box(
                    Modifier
                        .graphicsLayer { scaleX = lift; scaleY = lift }
                        .pointerInput(hotspot) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragY = 0f; lifted = true; haptics?.tick() },
                                onDrag = { change, amt -> change.consume(); dragY += abs(amt.x) + abs(amt.y) },
                                onDragEnd = {
                                    lifted = false
                                    if (dragY > 56f) { haptics?.heavy(); vm.setHotspot(v, null) }
                                },
                                onDragCancel = { lifted = false },
                            )
                        },
                ) {
                    SinglePebble(hotspot, v, state, vm, Modifier)
                }
            }
        }
    } else {
        var menu by remember { mutableStateOf(false) }
        val options = state.sectionsFor(v).filter {
            it !in setOf("summary", "controls") && !state.isPebbleHidden(v.vin, it) &&
                (it != "trips" || (state.hasBattery(v) && !v.isGen5W)) &&
                (it != "update" || state.updateAvailable != null)
        }
        val hotDrag = LocalHotSeatDrag.current
        val hovered = hotDrag?.overSlot == true
        // The empty slot is both a drop target (drag any pebble onto it to pin)
        // and a tap target (tap to pick one from a menu). It highlights while a
        // dragged pebble hovers over it.
        Box(
            Modifier.onGloballyPositioned {
                hotDrag?.let { d -> d.slotTopLeft = it.localToWindow(Offset.Zero); d.slotSize = it.size }
            },
        ) {
            MorphButton(
                onClick = { menu = true },
                modifier = Modifier.fillMaxWidth(),
                active = hovered,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                contentPadding = PaddingValues(16.dp),
            ) {
                Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (hovered) "Release to pin" else "Pin a pebble here", style = MaterialTheme.typography.bodyMedium)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                options.forEach { sec ->
                    DropdownMenuItem(
                        text = { Text(sectionLabel(sec)) },
                        onClick = { vm.setHotspot(v, sec); menu = false },
                    )
                }
            }
        }
    }
}

/** How far the floating overlays (dots, buttons) slide down during a refresh. */
private val RefreshPullShift = 96.dp

/**
 * Wraps content with the pull-to-refresh gesture with an overlay indicator.
 * Delegates the actual gesture recognition/animation state to Material 3's
 * [rememberPullToRefreshState] (`ptrState`); this composable's own job is
 * publishing that pull distance out to [LocalPullFraction] (so sibling
 * overlays elsewhere in [GarageScreen] can react to the live pull, not just
 * the boolean `state.refreshing`), and manually positioning the loading
 * indicator by hand rather than letting Material lay it out, so it can
 * slide fully off-screen above the content when idle and only ease into
 * view as the user pulls.
 */
@Composable
private fun Refreshable(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    hideIndicator: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val haptics = LocalHaptics.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Publish the pull distance so GarageScreen's overlays track the pull live.
    val pullFractionState = LocalPullFraction.current
    LaunchedEffect(ptrState) {
        snapshotFlow { ptrState.distanceFraction }.collect { pullFractionState.value = it }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = state.refreshing,
                state = ptrState,
                onRefresh = { haptics?.diceRoll(); vm.refreshStatus(v) },
            ),
    ) {
        // Content stays full-size and edge-to-edge; never shifted down.
        content()
        // Indicator floats above content as a z-elevated overlay. The whole
        // indicatorProgress/indicatorY calc used to live directly in this
        // composable's body, reading ptrState.distanceFraction on every frame
        // of the drag -- that's a *composition*-phase read, so it recomposed
        // this entire Box (and everything content() renders, the whole car
        // card) on every pixel of the pull gesture, not just re-laid-out the
        // small indicator. Moved into the offset{} lambda, which only runs in
        // the layout phase, so a live drag now costs one indicator relayout
        // per frame instead of a full recomposition of the car's content.
        if (!hideIndicator) {
            PullToRefreshDefaults.LoadingIndicator(
                state = ptrState,
                isRefreshing = state.refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val indicatorProgress = if (state.refreshing) 1f else ptrState.distanceFraction.coerceIn(0f, 1f)
                        val offScreenPx = -(topInset + 56.dp).roundToPx()
                        val onScreenPx = (topInset + 28.dp).roundToPx()
                        IntOffset(0, offScreenPx + ((onScreenPx - offScreenPx) * indicatorProgress).roundToInt())
                    },
            )
        }
    }
}

/** Car name/model + a Driving/Parked badge, with an optional expand button. */
@Composable
private fun CarHeaderRow(v: Vehicle, state: UiState, onExpand: (() -> Unit)?, reserveEnd: Boolean) {
    Row(
        Modifier.fillMaxWidth().then(if (reserveEnd) Modifier.padding(end = 52.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                v.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${v.model} · ${state.powertrainLabel(v)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LastUpdatedLabel(v, state, Modifier.padding(top = 2.dp))
        }
        if (onExpand != null) {
            // A proper floating chip (was a hard-to-see bare icon).
            FloatingIcon(Icons.Filled.Fullscreen, "Expand to full screen", onExpand)
        }
    }
}

/** Hero image + gauge, then the primary lock/charge controls (expanded view). */
@Composable
private fun CriticalContent(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val hMetric = vm.appearance.collectAsState().value.unitSystem == "metric"
    HeroHeader(v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v), vm, state.drivingLabel(v), metric = hMetric)
    // Update tile lives in the "pebbles" column's PebbleList as its own
    // reorderable/pinnable "update" section now, not hardcoded into this
    // fixed critical-info column -- see SinglePebble.
    // PrimaryActions is called bare here, unlike its other callers (ControlsPebble,
    // CompactMainTile) which always wrap it in a Surface that establishes a
    // readable contentColor. StateControl's status label falls back to
    // LocalContentColor when not highlighted/off-tinted, and Compose's own
    // default for that (when nothing upstream ever sets it - the dual-column
    // controls column isn't itself Surfaced) is opaque black, invisible against
    // this app's dark theme. That's what read as "no status text next to the
    // button" here even though the exact same StateControl shows it fine
    // everywhere else.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        PrimaryActions(v, state, vm)
    }
}

/**
 * The lock/unlock quick control. Deliberately *not* styled like the other
 * pebbles - it's just the morphing StateControl with its status on the left,
 * with no card, header or expand chevron. It can still be long-pressed and
 * dragged to reorder, like a pebble, even though it doesn't look like one.
 */
@Composable
private fun ControlsPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val shape = RoundedCornerShape(PebbleCornerCollapsed)
    // Was frostedRim unconditionally -- every other pebble instead gates a
    // bolder dedicated border on the pebbleOutline setting (see Pebble()),
    // frostedRim's alpha being tuned for chrome over a car photo and nearly
    // invisible against a flat pebble background either way. This pebble
    // rolls its own Surface instead of going through Pebble(), so it had been
    // missed -- the setting simply did nothing here.
    val pebbleOutline = vm.appearance.collectAsState().value.pebbleOutline
    Surface(
        modifier = Modifier.fillMaxWidth().height(ControlHeight).then(dragHandle)
            .dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp)
            .then(
                if (pebbleOutline) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
                } else Modifier,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            // PrimaryActions' own default start padding (26.dp) plus this
            // Box's 12.dp put the lock icon noticeably further right than
            // every other pebble's header icon (Charge, Climate, ...), which
            // only ever get Pebble's flat 16.dp row padding. 4.dp here lines
            // the two icons up (4 + 12 = 16, matching Pebble's inset).
            PrimaryActions(v, state, vm, contentPadding = PaddingValues(start = 4.dp, end = 8.dp))
        }
    }
}

/** The reorderable pebble stack for a car. */
@Composable
private fun PebbleList(v: Vehicle, state: UiState, vm: AppViewModel, exclude: Set<String> = emptySet()) {
    val allSections = state.sectionsFor(v)
    val sections = allSections.filter {
        it !in exclude &&
            !state.isPebbleHidden(v.vin, it) &&
            (it != "ai" || state.aiEnabled) &&
            // Trip history rides on the EV-only trip-details endpoint, so a
            // gas/PHEV/Kia car has nothing to show here -- gate it off battery.
            // Also gate off Gen5W: those head units don't serve the feed, so
            // TripsPebble renders nothing -- if it still entered the list it
            // would leave a phantom slot with a spacedBy gap on both sides
            // (the empty-space-between-pebbles bug).
            (it != "trips" || (state.hasBattery(v) && !v.isGen5W)) &&
            (it != "update" || state.updateAvailable != null)
    }
    val hotDrag = LocalHotSeatDrag.current
    ReorderColumn(
        items = sections,
        keyOf = { it },
        onReorder = { newVisible ->
            // Merge the reordered visible items back into the full section order so
            // excluded ones (the pinned hot-spot, summary, controls, hidden) keep
            // their slots instead of being dropped.
            val visibleSet = sections.toSet()
            val full = (allSections + com.bloo.bluelink.data.DEFAULT_SECTIONS).distinct()
            val queue = ArrayDeque(newVisible)
            val merged = full.map { s ->
                if (s in visibleSet && queue.isNotEmpty()) queue.removeFirst() else s
            }
            vm.setSectionOrder(v, merged)
        },
        // In the dual-column view, dragging a pebble onto the hot-spot slot pins it.
        onDragMove = hotDrag?.let { d ->
            { key, pointer -> d.section = key as String; d.pointer = pointer }
        },
        onDragRelease = hotDrag?.let { d ->
            { key ->
                val pin = d.overSlot
                d.section = null
                if (pin) { vm.setHotspot(v, key as String); true } else false
            }
        },
        staggerInOnColdStart = true,
        introKey = v.vin,
    ) { section, dragHandle, _ ->
        SinglePebble(section, v, state, vm, dragHandle)
    }
}

/** Renders one pebble by section name (used by the list and the hot spot). */
@Composable
private fun SinglePebble(section: String, v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val status = state.statusFor(v)
    val seats = state.seatConfigFor(v)
    val enabled = !state.loading
    val mSingle = vm.appearance.collectAsState().value.unitSystem == "metric"
    when (section) {
        "summary" -> HeroHeader(
            v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v), vm,
            state.drivingLabel(v), dragHandle = dragHandle, metric = mSingle,
        )
        // Its own reorderable/pinnable slot now, like every other pebble --
        // only actually present in the list while state.updateAvailable != null
        // (see PebbleList's filter and the two hotspot-eligibility checks).
        "update" -> UpdateAvailableTile(state, vm, dragHandle)
        "controls" -> ControlsPebble(v, state, vm, dragHandle)
        "climate" -> ClimatePebble(v, status, seats, state, vm, dragHandle)
        // The "charge" slot is the powertrain's energy pebble: charging for an
        // EV/PHEV, a fuel readout for a gas/hybrid car (no charge UI at all).
        "charge" -> if (state.hasBattery(v)) {
            ChargePebble(v, status, enabled, state, vm, dragHandle)
        } else {
            FuelPebble(v, status, state, vm, dragHandle)
        }
        "location" -> LocationPebble(v, state, vm, dragHandle)
        "weather" -> WeatherPebble(v, state, vm, dragHandle)
        // Trip history rides on the EV trip-details endpoint, so EVs only.
        "trips" -> TripsPebble(v, state, vm, dragHandle)
        "info" -> InfoPebble(v, status, state, vm, dragHandle)
        "diagnostics" -> DiagnosticsPebble(v, status, state, vm, dragHandle)
        "ai" -> AiPebble(v, state, vm, dragHandle)
        else -> Spacer(Modifier.fillMaxWidth())
    }
}

/** Optional on-device Gemini Nano summary of the car's last-refreshed status. */
@Composable
private fun AiPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val busy = v.vin in state.aiBusy
    val summary = state.aiSummaries[v.vin]
    Pebble(
        v, "ai", "AI summary", Icons.Filled.AutoAwesome, state, vm, dragHandle,
        summary = "On-device Gemini Nano",
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        headerAction = PebbleHeaderAction(
            label = "Summarize",
            icon = Icons.Filled.AutoAwesome,
            onClick = { vm.summarizeCar(v) },
            pending = busy,
        ),
    ) {
        if (summary != null) {
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                "Summarize this car's last-refreshed status, generated privately on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
            )
        }
        Text(
            "Reflects the last refresh. Tap Summarize to update.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
        )
    }
}

/**
 * The lock/unlock [StateControl] plus its brand-conditional grouped
 * Flash-lights/Horn-and-lights icon actions -- shared by every place a
 * car's primary quick-action needs to render (the dual-column critical
 * column, [ControlsPebble], and the cover screen's main tile), each
 * supplying its own [contentPadding] to line the icon up with that
 * particular container's own inset convention.
 */
@Composable
private fun PrimaryActions(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    contentPadding: PaddingValues = PaddingValues(start = 26.dp, end = 8.dp),
) {
    val status = state.statusFor(v)
    Column(Modifier.fillMaxWidth().padding(contentPadding)) {
        StateControl(
            name = "",
            isOn = status?.doorLock,
            stateOn = "Locked", stateOff = "Unlocked",
            turnOn = "Lock", turnOff = "Unlock",
            icon = Icons.Filled.Lock, deactivateIcon = Icons.Filled.LockOpen,
            pending = state.isPending(v.vin, "doors"),
            onActivate = { vm.lock(v) }, onDeactivate = { vm.unlock(v) },
            highlightWhenOff = true,
            offTextColor = MaterialTheme.colorScheme.error,
            // Kia's US API has no equivalent endpoint (see Vehicle.supportsHornLights),
            // so these only appear for Hyundai/Genesis, matching what those apps show.
            // A connected M3 button group with the Lock/Unlock button (see
            // StateControl/connectedGroupShape) -- icon-only, since a labelled
            // "Lights"/"Horn" pill this size squeezed the weighted name/state
            // column (the "Locked"/"Unlocked" label) down to nothing. contentDescription
            // keeps them labelled for TalkBack even with no visible text.
            groupActions = if (v.supportsHornLights) {
                val hlPending = state.isPending(v.vin, "hornLights")
                listOf(
                    GroupIconAction(Icons.Filled.FlashOn, "Flash lights", !hlPending) { vm.flashLights(v) },
                    GroupIconAction(Icons.Filled.Campaign, "Horn & lights", !hlPending) { vm.hornAndLights(v) },
                )
            } else emptyList(),
        )
    }
}

/**
 * A round colour swatch for the palette picker. Shows the palette's seed colour
 * and a ring + check when selected.
 */
@Composable
private fun PaletteSwatch(
    palette: ColorPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val ring by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "swatchRing",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outline)
                .padding(ring)
                .clip(CircleShape)
                .background(palette.swatch)
                // The colour name was only ever rendered as a sibling Text
                // below, outside this clickable's own semantics -- TalkBack
                // announced an unlabelled "double tap to activate" with no
                // colour name and no sense of which swatch is selected (the
                // ring/check are purely visual). RadioButton matches the
                // "pick exactly one" behaviour of this swatch row.
                .semantics {
                    contentDescription = palette.label
                    role = Role.RadioButton
                    this.selected = selected
                }
                .clickable { haptics?.click(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Like [PaletteSwatch] but for user-created [CustomPaletteData] entries. */
@Composable
private fun CustomPaletteSwatch(
    palette: CustomPaletteData,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val ring by animateDpAsState(
        if (selected) 3.dp else 0.dp,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "customSwatchRing",
    )
    val scale by animateFloatAsState(
        if (selected) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "customSwatchScale",
    )
    val swatchColor = Color(palette.primaryArgb.toLong() and 0xFFFFFFFFL)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Outer container is sized to accommodate the 1.12x scale without clipping.
        Box(
            modifier = Modifier
                .size(58.dp)
                .semantics {
                    contentDescription = palette.name
                    role = Role.RadioButton
                    this.selected = selected
                }
                .clickable { haptics?.click(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
                    .padding(ring)
                    .clip(CircleShape)
                    .background(swatchColor),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                palette.name,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // A bare 10dp clickable Icon (no IconButton) was well under the
            // minimum touch target guideline and had no button semantics --
            // TalkBack announced it with no "double tap to activate" cue and
            // it was genuinely hard to hit with a finger. IconButton gives
            // both a real (if still compact, given the tight swatch grid)
            // touch target and the Button role for free.
            IconButton(onClick = { haptics?.click(); onEdit() }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Edit palette",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Canvas-based colour picker: hue bar + saturation/value square.
 *
 * Internal state is plain HSV floats (`hue`/`sat`/`value`), seeded once from
 * the incoming [color] on first composition and never re-synced from it
 * afterward -- each drag on either Canvas computes a new HSV component
 * straight from the touch position (`awaitEachGesture` + a manual
 * down/while-pressed loop, since neither Canvas needs multi-touch or a
 * standard drag-gesture detector) and calls `update()`, which converts back
 * to RGB and reports it via [onColorChange]. `hexInput` is a separate text
 * mirror of the same colour: it's kept in sync from `picked` (but only
 * refreshed when the *canvas* changes the colour, not on every keystroke),
 * so a manually typed hex value only overwrites the canvas state once
 * `commitHex()` runs (on Done/focus-loss), not while the user is still
 * mid-edit.
 */
@Composable
private fun ColorPickerCanvas(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Internal HSV state initialised from the incoming colour once.
    var hue by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        mutableFloatStateOf(hsv[0])
    }
    var sat by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        mutableFloatStateOf(hsv[1].coerceAtLeast(0.05f))
    }
    var value by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        mutableFloatStateOf(hsv[2].coerceAtLeast(0.3f))
    }

    fun update() {
        onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
    }

    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val picked = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    val hueGradient = remember(Unit) {
        (0..12).map { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f))) }
    }
    fun hexOf(c: Color) = String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and c.toArgb())
    // A plain text field is the accessible alternative to the two drag-only
    // Canvases below, which have no TalkBack path at all -- a screen-reader
    // user can name and save a palette but never actually choose or perceive
    // its colour otherwise. Only re-synced when the CANVAS changes the colour
    // (picked), never on every keystroke, so it doesn't fight an in-progress
    // edit -- typing itself only updates `picked` once, on a successful commit.
    var hexInput by remember { mutableStateOf(hexOf(picked)) }
    var hexError by remember { mutableStateOf(false) }
    LaunchedEffect(picked) { if (hexInput != hexOf(picked)) { hexInput = hexOf(picked); hexError = false } }
    fun commitHex() {
        val parsed = runCatching {
            android.graphics.Color.parseColor(if (hexInput.startsWith("#")) hexInput else "#$hexInput")
        }.getOrNull()
        if (parsed == null) {
            hexError = true
            return
        }
        hexError = false
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(parsed, hsv)
        hue = hsv[0]
        sat = hsv[1].coerceAtLeast(0.05f)
        value = hsv[2].coerceAtLeast(0.3f)
        update()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Saturation × Value square
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = "Saturation and brightness picker. Use the hex colour field below for exact input."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        sat = (down.position.x / size.width).coerceIn(0.02f, 1f)
                        value = 1f - (down.position.y / size.height).coerceIn(0f, 0.98f)
                        update()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            sat = (ch.position.x / size.width).coerceIn(0.02f, 1f)
                            value = 1f - (ch.position.y / size.height).coerceIn(0f, 0.98f)
                            update()
                        }
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, 11.dp.toPx(), Offset(cx, cy))
            drawCircle(picked, 8.dp.toPx(), Offset(cx, cy))
        }

        // Hue bar
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = "Hue picker. Use the hex colour field below for exact input."
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        hue = ((down.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                        update()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            hue = ((ch.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                            update()
                        }
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(hueGradient))
            val tx = (hue / 360f) * size.width
            drawCircle(Color.White, 14.dp.toPx(), Offset(tx, size.height / 2f))
            drawCircle(pureHue, 11.dp.toPx(), Offset(tx, size.height / 2f))
        }

        // Preview swatch
        Box(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(picked)
        )

        OutlinedTextField(
            value = hexInput,
            onValueChange = { hexInput = it; hexError = false },
            label = { Text("Hex colour") },
            singleLine = true,
            isError = hexError,
            supportingText = if (hexError) { { Text("Not a valid colour") } } else null,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitHex() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) commitHex() },
        )
    }
}

/** Dialog to create or edit a [CustomPaletteData]. */
@Composable
private fun PaletteEditorDialog(
    editing: CustomPaletteData?,
    onSave: (CustomPaletteData) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val paletteId = remember(editing) { editing?.id ?: UUID.randomUUID().toString() }
    var name by remember(editing) { mutableStateOf(editing?.name ?: "Custom") }
    var primaryColor by remember(editing) {
        mutableStateOf(editing?.primaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF005AC1))
    }
    var useSecondary by remember(editing) { mutableStateOf(editing?.secondaryArgb != null) }
    var secondaryColor by remember(editing) {
        mutableStateOf(editing?.secondaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF7B4DFF))
    }
    var useTertiary by remember(editing) { mutableStateOf(editing?.tertiaryArgb != null) }
    var tertiaryColor by remember(editing) {
        mutableStateOf(editing?.tertiaryArgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF00696E))
    }
    // Was a single un-confirmed tap that permanently deleted a saved custom
    // palette -- same "tap again to confirm" + 4s auto-reset pattern as the
    // climate preset delete nub, so this destructive action isn't one
    // mis-tap away from losing work either.
    var confirmDelete by remember(editing) { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4000)
            confirmDelete = false
        }
    }
    // Standardized on the shared GlassAlertDialog shell. No leading icon (the
    // dialog is title-led); the delete affordance rides the shell's titleTrailing
    // slot; the shell already scrolls its body (max 360dp), so the inner
    // verticalScroll is dropped to avoid a nested-scroll conflict.
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = if (editing == null) "New palette" else "Edit \"${editing.name}\"",
        titleTrailing = if (editing != null) {
            {
                IconButton(onClick = {
                    if (confirmDelete) { onDelete(paletteId); onDismiss() } else { confirmDelete = true }
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (confirmDelete) "Confirm delete palette" else "Delete palette",
                        tint = if (confirmDelete) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
            }
        } else null,
        text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = FieldShape,
                )

                // Primary colour picker
                Text(
                    "Primary colour",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPickerCanvas(primaryColor, { primaryColor = it })

                // Secondary colour (optional)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Custom secondary", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useSecondary, onCheckedChange = { useSecondary = it })
                }
                AnimatedVisibility(useSecondary) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Secondary colour",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorPickerCanvas(secondaryColor, { secondaryColor = it })
                    }
                }

                // Tertiary colour (optional)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Custom tertiary", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useTertiary, onCheckedChange = { useTertiary = it })
                }
                AnimatedVisibility(useTertiary) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Tertiary colour",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ColorPickerCanvas(tertiaryColor, { tertiaryColor = it })
                    }
                }
        },
        buttons = {
            MorphButton(
                onClick = {
                    onSave(
                        CustomPaletteData(
                            id = paletteId,
                            name = name.ifBlank { "Custom" },
                            primaryArgb = primaryColor.toArgb(),
                            secondaryArgb = if (useSecondary) secondaryColor.toArgb() else null,
                            tertiaryArgb = if (useTertiary) tertiaryColor.toArgb() else null,
                        )
                    )
                    onDismiss()
                },
                active = true,
                modifier = Modifier.fillMaxWidth(),
            ) { MorphButtonLabel(Icons.Filled.Check, "Save", pending = false, iconSize = 18.dp) }
            MorphTextButton("Cancel", onDismiss, modifier = Modifier.fillMaxWidth())
        },
    )
}

/** One icon-only segment in a connected button group (see [connectedGroupShape]). */
private data class GroupIconAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * Shape for segment [index] of [count] in a Material 3 "connected" button
 * group (m3.material.io/components/button-groups): the group's outer corners
 * are fully round, every seam between two segments is a small square corner
 * instead, so the row reads as one continuous shape split into parts rather
 * than a row of separate pills sitting next to each other. Pair with a 2dp
 * gap between segments -- the spec's connected-group spacing at any size.
 */
// [cornerPercent] is the same 50 (pill) <-> 28 (pressed/active) morph every
// other MorphButton animates through -- passed in per-frame from the segment's
// own MorphButton so a segment still visibly squeezes on press instead of
// being frozen into a static silhouette just because it's part of a group.
private fun connectedGroupShape(index: Int, count: Int, cornerPercent: Int, smallCorner: Dp = 12.dp): RoundedCornerShape {
    val outer = CornerSize(percent = cornerPercent)
    val inner = CornerSize(smallCorner)
    val startCorner = if (index == 0) outer else inner
    val endCorner = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner)
}

/**
 * The one button style used across the whole app. It rests as a **pill** and
 * becomes a **rounded rectangle** only while [active] (an on/toggled state) - or
 * momentarily while pressed. When [active], it fills with [activeContainerColor].
 * Its width springs (with a little overshoot) whenever the content width changes,
 * e.g. the label flips Start -> Stop.
 */
@Composable
fun MorphButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    containerColor: Color = buttonContainer(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    activeContainerColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // An asymmetric shape to use instead of the plain pill<->square morph --
    // for a connected button-group segment (see StateControl), whose inner
    // (seam) corners stay small and square while the outer corner is the one
    // that morphs. Given the same animated corner percent [MorphButton] uses
    // internally, so a segment still visibly squeezes on press instead of
    // being frozen into a static silhouette just because it's part of a group.
    shapeForCorner: ((cornerPercent: Int) -> Shape)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHaptics.current
    // 50% = a true pill; a lower percent = a rounded rectangle.
    val pct by animateFloatAsState(
        targetValue = if (active || pressed) 28f else 50f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphCorner",
    )
    val bg by androidx.compose.animation.animateColorAsState(
        if (active) activeContainerColor else containerColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "morphBg",
    )
    val resolvedContent = if (active) activeContentColor else contentColor
    Button(
        onClick = { haptics?.click(); onClick() },
        modifier = modifier
            .animateContentSize(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
            // `active` is otherwise a colour-only change -- most call sites also
            // swap their label text (Lock/Unlock, Start/Stop), which is why this
            // mostly "worked" for TalkBack by accident, but that's caller
            // discipline, not something the shared button guarantees. Setting
            // `selected` here makes every MorphButton correct by construction:
            // the app's one button framework, so this is the single highest-
            // leverage place to fix it.
            .semantics { selected = active },
        enabled = enabled,
        shape = shapeForCorner?.invoke(pct.roundToInt()) ?: RoundedCornerShape(percent = pct.roundToInt()),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = resolvedContent,
            // Keep the button's full background when disabled (only the label
            // fades) instead of M3's default onSurface@12%, which is invisible
            // against light cards and made disabled buttons look backgroundless.
            disabledContainerColor = bg,
            disabledContentColor = resolvedContent.copy(alpha = 0.38f),
        ),
        border = if (active) null else border,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * A text-only [MorphButton] - the app's one button framework, used everywhere a
 * plain labelled button is needed (dialogs, settings, etc.) so they all share
 * the pill-morphs-to-rounded-square press feel.
 */
@Composable
fun MorphTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = buttonContainer(),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    MorphButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Standard leading slot for a [MorphButton]: shows the [icon], or a same-sized
 * spinner while [pending], so the button width never changes just from loading.
 */
@Composable
fun MorphButtonLabel(
    icon: ImageVector,
    label: String,
    pending: Boolean,
    iconSize: Dp = 18.dp,
    spinning: Boolean = false,
) {
    if (pending) {
        LoadingIndicator(Modifier.size(iconSize))
    } else {
        // Always-composed Animatable, but it only runs while spinning - so idle
        // buttons don't each hold a live infinite animation, and we avoid calling
        // remember conditionally.
        val angle = remember { Animatable(0f) }
        LaunchedEffect(spinning) {
            if (spinning) {
                // Ramp up: the first revolution accelerates from rest...
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutLinearInEasing),
                )
                // ...then hold a steady, fast linear spin.
                while (true) {
                    angle.animateTo(
                        targetValue = angle.value + 360f,
                        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                    )
                }
            } else if (angle.value != 0f) {
                // Ramp down: decelerate to the next full turn, then reset.
                val target = kotlin.math.ceil(angle.value / 360f) * 360f
                angle.animateTo(target, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
                angle.snapTo(0f)
            }
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize).rotate(angle.value),
        )
    }
    Spacer(Modifier.width(8.dp))
    Text(label, fontWeight = FontWeight.SemiBold)
}

/**
 * A unified selectable chip: a **pill** when unselected, morphing smoothly into a
 * filled **rounded box** when selected. Replaces ad-hoc FilterChips so selection
 * feels the same everywhere.
 */
@Composable
fun MorphChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (selected || pressed) 12.dp else 22.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "chipCorner",
    )
    val container by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else buttonContainer(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipBg",
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val chipSelected = selected
    Surface(
        onClick = { haptics?.tick(); onClick() },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        // Same gap MorphSegmented had: a selectable pill with no `selected`
        // semantics reaching TalkBack, which announced every chip identically
        // regardless of which one was actually active. Captured into a
        // differently-named local first -- inside semantics{}, `selected` on
        // its own resolves to the SemanticsPropertyReceiver's own property,
        // not this composable's `selected` parameter of the same name.
        modifier = modifier.semantics { this.selected = chipSelected },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/**
 * A chunky stateful control: shows the current state and a button offering the
 * *opposite* action. The button is always a clearly filled control that morphs
 * from a pill (calm) to a rounded square (highlighted).
 */
@Composable
private fun StateControl(
    name: String,
    isOn: Boolean?,
    stateOn: String,
    stateOff: String,
    turnOn: String,
    turnOff: String,
    icon: ImageVector,
    deactivateIcon: ImageVector? = null,
    pending: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    enabled: Boolean = true,
    disabledNote: String? = null,
    highlightWhenOff: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    highlightContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    offTextColor: Color? = null,
    groupActions: List<GroupIconAction> = emptyList(),
) {
    // Which state is the "highlighted" (on) one.
    val highlighted = enabled && (if (highlightWhenOff) isOn == false else isOn == true)
    Row(
        Modifier.fillMaxWidth().height(ControlHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Fill the button's height so the status reads as one tall control.
        Column(Modifier.weight(1f).fillMaxHeight().widthIn(min = 120.dp), verticalArrangement = Arrangement.Center) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            val stateText = when {
                !enabled && disabledNote != null -> disabledNote
                pending -> "Sending…"
                isOn == true -> stateOn
                isOn == false -> stateOff
                else -> "Unknown"
            }
            val stateColorTarget = when {
                !enabled -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
                isOn == false && offTextColor != null -> offTextColor
                highlighted -> highlightColor
                else -> LocalContentColor.current.copy(alpha = MutedContentAlpha)
            }
            val stateColor by androidx.compose.animation.animateColorAsState(
                stateColorTarget,
                animationSpec = tween(250),
                label = "stateColor",
            )
            when {
                // With no title, the lock state is the headline — icon AND word, side by side.
                name.isBlank() -> {
                    val stateIcon = when (isOn) {
                        true -> icon
                        false -> Icons.Filled.LockOpen
                        else -> icon
                    }
                    if (pending) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(Modifier.size(22.dp))
                            Text(
                                "Sending…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = stateColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = Pair(stateIcon, stateText),
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(200))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 1.1f, animationSpec = tween(150)))
                            },
                            // Default is TopStart: "Locked"/"Unlocked" render at
                            // slightly different intrinsic heights, so without
                            // this the old and new icon+label rows didn't align
                            // to the same vertical center during the crossfade,
                            // reading as the whole control nudging on toggle.
                            contentAlignment = Alignment.CenterStart,
                            label = "lockStateAnim",
                        ) { (ic, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // null, not `label`: the Text right after already
                                // carries the same words, so a non-null
                                // description here was a redundant swipe stop
                                // ("Locked" from the icon, then "Locked" again
                                // from the text) -- purely decorative now that
                                // the label is announced once.
                                Icon(ic, contentDescription = null, tint = stateColor, modifier = Modifier.size(22.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
                                    // If this column ever gets squeezed tight
                                    // again (groupActions content changes,
                                    // narrower screens), ellipsize instead of
                                    // wrapping mid-word ("Locke"/"d" on two
                                    // lines) -- a clipped label at least still
                                    // reads as one intact word.
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                else -> AnimatedContent(
                    targetState = stateText,
                    transitionSpec = {
                        fadeIn(tween(200)) + slideInVertically { -it / 3 } togetherWith
                        fadeOut(tween(150)) + slideOutVertically { it / 3 }
                    },
                    label = "stateTextAnim",
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        val haptics = LocalHaptics.current
        // Any extra icon actions (horn/lights) plus the lock/unlock button
        // form one Material 3 "connected" button group -- a single Row (one
        // child of the outer Row, so its own 2dp spacing isn't also getting
        // the outer Row's 12dp spacedBy piled on top) instead of a separate
        // icon cluster sitting next to an unrelated pill.
        val segmentCount = groupActions.size + 1
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            groupActions.forEachIndexed { i, action ->
                MorphButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    contentPadding = PaddingValues(0.dp),
                    shapeForCorner = { cp -> connectedGroupShape(i, segmentCount, cp) },
                    modifier = Modifier.size(50.dp),
                ) { Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(22.dp)) }
            }
            // Pill when off, rounded rectangle + highlight colour when on - same
            // as the climate/charge controls -- except when it's part of a
            // group, where the connected shape takes over (see MorphButton's
            // shape param doc): a connected group's silhouette is static, not
            // something one segment morphs independently of the others.
            MorphButton(
                onClick = { haptics?.heavy(); if (isOn == true) onDeactivate() else onActivate() },
                enabled = enabled && !pending,
                active = highlighted,
                activeContainerColor = highlightColor,
                activeContentColor = highlightContentColor,
                shapeForCorner = if (groupActions.isNotEmpty()) {
                    { cp -> connectedGroupShape(segmentCount - 1, segmentCount, cp) }
                } else {
                    null
                },
                // Same pill height as the pebble header actions (the row stays
                // ControlHeight tall, so the button is vertically centred in it).
                modifier = Modifier.heightIn(min = 50.dp),
            ) {
                val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = 22.dp)
            }
        }
    }
}

// --- Pebble (expandable, reorderable section) -----------------------------

/**
 * A collapsible "pebble" - a titled section that springs open/closed with a
 * playful bounce. Open/closed state lives in the ViewModel (per car + section),
 * and the section order is user-configurable in Settings.
 */
@Composable
private fun Pebble(
    v: Vehicle,
    section: String,
    title: String,
    icon: ImageVector,
    state: UiState,
    vm: AppViewModel,
    dragHandle: Modifier = Modifier,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val forceExpanded = LocalForceExpanded.current
    val expanded = forceExpanded || state.isPebbleExpanded(v.vin, section)
    PebbleShell(
        expanded = expanded,
        onToggle = { vm.togglePebble(v, section) },
        icon = icon,
        title = title,
        vm = vm,
        dragHandle = dragHandle,
        summary = summary,
        containerColor = containerColor,
        headerAction = headerAction,
        forceExpanded = forceExpanded,
        content = content,
    )
}

/**
 * The actual expand/collapse pebble shell -- [Pebble] derives [expanded]/
 * [onToggle] from a car+section key (state.isPebbleExpanded/vm.togglePebble);
 * this takes them directly so anything that isn't tied to a specific
 * vehicle/section (the update tile) can still get the exact same collapsible
 * card instead of a hand-rolled lookalike.
 */
@Composable
private fun PebbleShell(
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    title: String,
    vm: AppViewModel,
    dragHandle: Modifier = Modifier,
    summary: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    forceExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    // Collapsed = pill-soft corners; expanded morphs to a tighter rounded square.
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else PebbleCornerCollapsed,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "pebbleCorner",
    )
    val fillHeight = LocalPebbleFillHeight.current
    val pebbleShape = RoundedCornerShape(corner)
    // Off by default -- see Appearance.pebbleOutline's doc comment. Most
    // floating chrome always has a rim, but pebbles are the majority of
    // on-screen surface area, so a rim on every single one is a much bigger
    // visual commitment than one more floating button.
    val pebbleAppearance by vm.appearance.collectAsState()
    val pebbleOutline = pebbleAppearance.pebbleOutline
    Box(Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)) {
        Card(
            Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .dropShadow(pebbleShape, blurRadius = 12.dp, offsetY = 4.dp)
                // frostedRim's alpha (0.10-0.24) is tuned for chrome floating
                // over an unpredictable car photo, where it only has to beat
                // that photo's contrast -- against a flat dark pebble
                // background it was nearly imperceptible, reading as "this
                // setting does nothing" even though it was working. A
                // dedicated, considerably bolder border here instead, so
                // toggling this is actually visible.
                .then(
                    if (pebbleOutline) {
                        Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), pebbleShape)
                    } else Modifier,
                ),
            shape = pebbleShape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColorFor(containerColor),
            ),
        ) {
            // No animateContentSize here (cover-screen tiles fill instead) --
            // the body below is already wrapped in its own AnimatedVisibility
            // with expandVertically/shrinkVertically, which smoothly animates
            // that exact same height delta on its own. Wrapping this Column in
            // a SECOND, independently-sprung animateContentSize on top of that
            // made every collapse/expand visibly lag and rubber-band: each
            // frame of the inner animation is itself a "content size changed"
            // event the outer animateContentSize then re-animates towards,
            // compounding two springs where the collapse only needs one.
            Column(
                if (fillHeight) Modifier.fillMaxHeight() else Modifier,
            ) {
                if (fillHeight) {
                    // No header row on the cover screen -- the full-height icon
                    // + title + chevron row this tier used to share with every
                    // other pebble reserved a fixed ~76dp+padding before a
                    // single line of actual content, a big bite out of an
                    // already tiny screen, especially for a tile whose body
                    // needs to scroll. A small floating icon badge over the
                    // content's top-start corner (drawn in the Box below,
                    // outside any dedicated row of its own) identifies the
                    // tile instead -- the tile-scrubber dots on the right edge
                    // already announce its name too (see VerticalPagerDots),
                    // so this is a supplementary visual cue, not the only one.
                    if (expanded) {
                        val bodyScroll = LocalCoverScrollState.current ?: rememberScrollState()
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            // BoxWithConstraints captures the real available height
                            // (undisturbed by verticalScroll, which is applied one
                            // level in) so heightIn(min = ...) below can force the
                            // scrolling Column to at least that tall. A short tile's
                            // content then centers within that height via
                            // spacedBy(..., CenterVertically) instead of collapsing
                            // to the top with dead space underneath; a tall tile's
                            // content still exceeds it and scrolls exactly as before.
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val minHeight = maxHeight
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .fadingEdges(bodyScroll)
                                        .verticalScroll(bodyScroll)
                                        .heightIn(min = minHeight)
                                        // Extra top clearance (vs. the 4dp every other
                                        // pebble uses) so the first content row doesn't
                                        // sit directly under the floating badge.
                                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 34.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                                    content = content,
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                } else {
                    // Header: tap anywhere to toggle, long-press to drag-reorder. The
                    // action button and chevron handle their own clicks. Fixed min height
                    // so every collapsed pebble lines up.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (forceExpanded) Modifier
                                else Modifier.clickable {
                                    if (expanded) haptics?.tick() else haptics?.click()
                                    onToggle()
                                },
                            )
                            .then(dragHandle)
                            .heightIn(min = PebbleHeaderHeight)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            // Same heading fix as SettingsCard: with 8+ pebbles per
                            // car and no heading structure, TalkBack users could
                            // only reach a given section (Climate, Charge, ...) by
                            // swiping through every row of every pebble above it.
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            if (summary != null) {
                                AnimatedContent(
                                    targetState = summary,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                                        (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                                    },
                                    label = "pebbleSummary",
                                ) { s ->
                                    Text(
                                        s,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (!forceExpanded) {
                            if (headerAction != null) {
                                SplitExpandButton(
                                    action = headerAction,
                                    expanded = expanded,
                                    onToggle = onToggle,
                                )
                            } else {
                                MorphExpandButton(
                                    expanded = expanded,
                                    onToggle = onToggle,
                                )
                            }
                        }
                    }
                    // Normal pebbles: animate the body fading + sliding open/closed.
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(180)) + expandVertically(
                            spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                            expandFrom = Alignment.Top,
                        ),
                        exit = fadeOut(tween(130)) + shrinkVertically(
                            tween(160),
                            shrinkTowards = Alignment.Top,
                        ),
                    ) {
                        Column(
                            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/** The app's muted/secondary-text alpha, applied over LocalContentColor. */
private const val MutedContentAlpha = 0.7f

/** Shared control height: a collapsed pebble matches the lock/unlock button. */
private val ControlHeight = 76.dp

/** Uniform collapsed-header height so every pebble lines up at the same size. */
private val PebbleHeaderHeight = ControlHeight
private val PebbleCornerCollapsed = 38.dp
private val PebbleCornerExpanded = 20.dp

private class PebbleHeaderAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val pending: Boolean = false,
    val active: Boolean = false,
    val spinning: Boolean = false,
    val bounceIcon: Boolean = false,
    val activeContainer: Color? = null,
    val activeContent: Color? = null,
    val isWarning: Boolean = false,
    /** Explicit TalkBack label for icon-only actions (empty [label]) -- without
     *  it, an empty-label button inside a Surface (which doesn't merge
     *  descendant semantics) announces only "Button" with no indication of
     *  what it does. Only needed when [label] is blank. */
    val contentDescription: String? = null,
)

/**
 * Right-side expand control for pebbles that also have an action button.
 * Left half: the action (label + icon); right half: chevron nub. Together
 * they form a connected split pill, identical in style to [PresetPill].
 */
@Composable
private fun SplitExpandButton(
    action: PebbleHeaderAction,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitChevron",
    )
    val leftInteraction = remember { MutableInteractionSource() }
    val leftPressed by leftInteraction.collectIsPressedAsState()
    // Morph to rounded-rect when action is active, pressed, OR pebble is expanded.
    val morphed = action.active || leftPressed || expanded

    val outer by animateDpAsState(
        if (morphed) 16.dp else 50.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitOuter",
    )
    val inner = 6.dp

    val defaultContainer = buttonContainer()
    val leftBg by androidx.compose.animation.animateColorAsState(
        when {
            action.isWarning -> MaterialTheme.colorScheme.errorContainer
            action.active -> (action.activeContainer ?: MaterialTheme.colorScheme.primary)
            else -> defaultContainer
        },
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "splitLeftBg",
    )
    val leftFg = when {
        action.isWarning -> MaterialTheme.colorScheme.onErrorContainer
        action.active -> (action.activeContent ?: MaterialTheme.colorScheme.onPrimary)
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Bounce animation for the location button's icon.
    val bounceY = remember { Animatable(0f) }
    val bounceScope = rememberCoroutineScope()
    var bouncing by remember { mutableStateOf(false) }

    // Spinning animation for the climate button's icon.
    val spinAngle = remember { Animatable(0f) }
    LaunchedEffect(action.spinning) {
        if (action.spinning) {
            spinAngle.animateTo(
                targetValue = spinAngle.value + 360f,
                animationSpec = tween(durationMillis = 850, easing = FastOutLinearInEasing),
            )
            while (true) {
                spinAngle.animateTo(
                    targetValue = spinAngle.value + 360f,
                    animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                )
            }
        } else if (spinAngle.value != 0f) {
            val target = kotlin.math.ceil(spinAngle.value / 360f) * 360f
            spinAngle.animateTo(target, tween(durationMillis = 700, easing = LinearOutSlowInEasing))
            spinAngle.snapTo(0f)
        }
    }

    Row(
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left half — action button.
        Surface(
            onClick = {
                if (action.bounceIcon) bounceScope.launch {
                    bouncing = true
                    bounceY.animateTo(-9f, spring(stiffness = Spring.StiffnessHigh))
                    bounceY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    bouncing = false
                }
                haptics?.click()
                action.onClick()
            },
            enabled = action.enabled && !action.pending,
            interactionSource = leftInteraction,
            color = leftBg,
            contentColor = leftFg,
            shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
            modifier = Modifier.fillMaxHeight().then(
                if (action.label.isEmpty() && action.contentDescription != null) {
                    Modifier.semantics { contentDescription = action.contentDescription!! }
                } else Modifier,
            ),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .graphicsLayer { translationY = bounceY.value },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (action.pending && !bouncing) {
                    LoadingIndicator(Modifier.size(16.dp))
                } else {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        // graphicsLayer lambda, not rotate(): rotate() reads the
                        // Animatable in composition, and the spin runs for as long
                        // as climate is on - recomposing this button every frame
                        // indefinitely. The lambda defers the read to the draw phase.
                        modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = spinAngle.value },
                    )
                }
                if (action.label.isNotEmpty()) {
                    Text(action.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        // Right half — chevron nub.
        Surface(
            onClick = { if (expanded) haptics?.tick() else haptics?.click(); onToggle() },
            color = buttonContainer(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
            // The icon's own contentDescription below is the NEXT action
            // ("Expand"/"Collapse"); this is the CURRENT state -- without it
            // TalkBack only ever hears what tapping will do, never whether the
            // pebble is presently open, so distinguishing the two took a
            // double-tap-and-listen-again instead of being announced on focus.
            modifier = Modifier.fillMaxHeight().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        ) {
            Box(
                modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(rotation),
                )
            }
        }
    }
}

/**
 * Right-side expand control for pebbles with no action button — the whole
 * right handle is a pill that morphs to a rounded-square when the section is
 * open, giving a clear visual indicator of state.
 */
@Composable
private fun MorphExpandButton(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphChevron",
    )
    val corner by animateDpAsState(
        targetValue = if (expanded) 14.dp else 50.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "morphExpandCorner",
    )
    Surface(
        onClick = { if (expanded) haptics?.tick() else haptics?.click(); onToggle() },
        shape = RoundedCornerShape(corner),
        color = buttonContainer(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Same as SplitExpandButton's chevron: the icon's contentDescription is
        // the next action, this is the current state -- both together instead
        // of only announcing what tapping does.
        modifier = Modifier.size(50.dp).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }
    }
}

// --- Trips (trip history) --------------------------------------------------

/**
 * Recent drives from the Hyundai/Genesis US trip-details feed, with distance,
 * time, speeds and (for EVs) the energy/regen breakdown. Loaded lazily the
 * first time the pebble is composed, once per session. Shown for every car;
 * cars whose head unit doesn't report trips simply show an empty state.
 */
@Composable
private fun TripsPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    // The evTripDetails feed isn't served by Gen5W (generation 2) head units -
    // they report nothing, EV or not - so the pebble is hidden for them rather
    // than sitting permanently empty. Kia US doesn't report a generation, so it's
    // excluded from the check and keeps the pebble.
    val isGen5W = v.isGen5W
    if (isGen5W) return
    val trips = state.trips[v.vin]
    val loading = state.isPending(v.vin, "trips")
    LaunchedEffect(v.vin) { vm.loadTrips(v) }
    val summary = when {
        trips == null -> if (loading) "Loading…" else null
        trips.isEmpty() -> "No recent trips"
        else -> "${trips.size} recent"
    }
    Pebble(v, "trips", "Trips", Icons.Filled.Route, state, vm, dragHandle, summary = summary) {
        when {
            trips == null -> Text(if (loading) "Fetching trip history…" else "No trip data yet.")
            trips.isEmpty() -> Text("No recent trips reported by this car.")
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val tMetric = vm.appearance.collectAsState().value.unitSystem == "metric"
                trips.take(8).forEach { TripRow(it, metric = tMetric) }
            }
        }
    }
}

@Composable
private fun TripRow(trip: EvTrip, metric: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tripDate(trip.startdate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            trip.distance?.let {
                Text(formatTripDistance(it, metric), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val pace = remember(trip, metric) { buildList {
            trip.driveMinutes?.let { add("$it min") }
            trip.idleMinutes?.takeIf { it > 0 }?.let { add("$it min idle") }
            trip.avgspeed?.value?.let { add("avg ${formatSpeed(it.toDouble(), metric)}") }
            trip.maxspeed?.value?.let { add("max ${formatSpeed(it.toDouble(), metric)}") }
        } }
        if (pace.isNotEmpty()) {
            Text(pace.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val energy = remember(trip) { buildList {
            trip.usedKwh?.let { add("$it kWh used") }
            trip.regenKwh?.takeIf { it > 0 }?.let { add("$it kWh regen") }
        } }
        if (energy.isNotEmpty()) {
            Text(energy.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun tripDate(raw: String?): String = com.bloo.bluelink.data.tripDate(raw)

// --- Car info (status + service + links combined) -------------------------

@Composable
private fun InfoPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val appearance by vm.appearance.collectAsState()
    val inApp = appearance.linksInApp
    val metric = appearance.unitSystem == "metric"
    val location = state.locations[v.vin]
    val odoInt = parseOdometerMiles(v.odometer)
    val plate = state.licensePlates[v.vin]
    val lastSvc = state.lastServiceMiles[v.vin]
    val interval = state.serviceIntervalMiles[v.vin]
    val nextDue = if (lastSvc != null && interval != null) lastSvc + interval else null
    val remaining = serviceDue(odoInt, lastSvc, interval)

    val ev = status?.evStatus
    val plugged = ev?.isPluggedIn == true || ev?.batteryCharge == true

    val infoSummary = if (status?.doorLock == true) "Locked" else "Unlocked"
    Pebble(v, "info", "Car info", Icons.Filled.Info, state, vm, dragHandle, summary = infoSummary) {
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet.")
            else -> {
                SectionLabel("Status")
                status.engine?.let { StatusRow("Vehicle", if (it) "On" else "Off") }
                StatusRow("Doors", if (status.doorLock == true) "Locked" else "Unlocked")
                status.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Doors open", it.joinToString(", ")) }
                status.windowOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Windows open", it.joinToString(", ")) }
                if (status.trunkOpen == true) StatusRow("Trunk", "Open")
                if (status.hoodOpen == true) StatusRow("Hood", "Open")
                if (status.acc == true) StatusRow("Accessory power", "On")
                StatusRow("Climate", if (status.airCtrlOn == true) "On" else "Off")
                if (status.defrost == true) StatusRow("Defrost", "On")
                status.airTemp?.value?.let { StatusRow("Climate setpoint", degLabel(it, appearance.useFahrenheit)) }
                status.percentFor(state.hasBattery(v))?.let {
                    StatusRow(if (state.hasBattery(v)) "Charge" else "Fuel", "$it%")
                }
                status.rangeMiFor(state.hasBattery(v))?.let { StatusRow("Range", formatDistance(it, metric)) }
                status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                // Comfort heaters (read-only; mirror/rear-window heat track defrost).
                status.steerWheelHeat?.takeIf { it != 0 }?.let { StatusRow("Steering wheel heat", "On") }
                status.sideMirrorHeat?.takeIf { it != 0 }?.let { StatusRow("Mirror heat", "On") }
                status.sideBackWindowHeat?.takeIf { it != 0 }?.let { StatusRow("Rear defroster", "On") }
                location?.let { StatusRow("Coordinates", it.coordString()) }
                rememberRelativeTime(state.fetchedAt(v))?.let { StatusRow("Last refreshed", it) }

                if (plugged) {
                    SectionLabel("Charging")
                    ev?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
                        ?.let { StatusRow("Time to full", fmtMinutes(it)) }
                    chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
                    ev?.targetForCurrentPlug()?.let { StatusRow("Charge limit", "$it%") }
                }
            }
        }

        SectionLabel("Service & identity")
        SelectionContainer { StatusRow("VIN", v.vin) }
        if (!plate.isNullOrBlank()) StatusRow("License plate", plate)
        odoInt?.let { StatusRow("Odometer", formatDistance(it, metric)) }
        lastSvc?.let { StatusRow("Last service at", formatDistance(it, metric)) }
        nextDue?.let {
            val note = remaining?.let { r ->
                if (r >= 0) " · ${formatDistance(r, metric)} to go" else " · overdue ${formatDistance(-r, metric)}"
            } ?: ""
            StatusRow("Next service due", "${formatDistance(it, metric)}$note")
        }
        if (lastSvc == null || interval == null) {
            Text(
                "Set last-service mileage and a service interval in Settings to track service.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("${v.brand.label} owners")
        OwnerLinks(v, context, inApp)
    }
}

/**
 * Owner/assistance destinations as compact labelled buttons that flow 2+ per row
 * where they fit. Each says where it goes; the phone icon dials, others open
 * links. All destinations come from [BrandLinks] - the per-brand single source
 * of truth - so nothing here is defined twice.
 *
 * In-car payments (Hyundai Pay) and Plug & Charge are deliberately absent:
 * they live only inside the OEM app with no public web page or documented deep
 * link, so a button could only open an unrelated marketing page - better to
 * omit them than mislead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnerLinks(v: Vehicle, context: Context, inApp: Boolean) {
    val links = v.brand.links

    @Composable
    fun group(title: String, content: @Composable FlowRowScope.() -> Unit) {
        SectionLabel(title)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }

    val isSamsung = remember { Build.MANUFACTURER.lowercase() == "samsung" }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group("App & account") {
            LinkButton("${links.appName} app", Icons.Filled.OpenInNew) {
                openApp(context, listOf(links.appPackage), links.playStoreUrl, inApp)
            }
            LinkButton("Owners site", Icons.Filled.Person) { openUrl(context, links.ownersUrl, inApp) }
            // Features-on-Demand store (themes, lighting patterns…): ccNC-era
            // head units only - older Gen5W cars have nothing to buy.
            if (v.supportsConnectedStore) {
                LinkButton("Car store", Icons.Filled.Storefront) { openUrl(context, links.storeUrl, inApp) }
            }
        }
        group("Service") {
            LinkButton("Schedule service", Icons.Filled.Build) { openUrl(context, links.serviceScheduleUrl, inApp) }
            LinkButton(links.dealerLabel, Icons.Filled.Place) { openUrl(context, links.dealerUrl, inApp) }
            LinkButton("Manuals", Icons.Filled.MenuBook) { openUrl(context, links.manualsUrl, inApp) }
            LinkButton("Roadside", Icons.Filled.Call) { dial(context, links.roadsidePhone) }
        }
        // Digital Key: Gen5W head units use DK1 (BLE/NFC dedicated app).
        // Gen3+ and all Kia models use DK2 (UWB via wallet).
        // Kia has no gen field so isGen5W is always false for them.
        val isGen5W = v.isGen5W
        group("Digital Car Key") {
            if (isGen5W) {
                when (v.brand) {
                    Brand.HYUNDAI -> LinkButton("Digital Key", Icons.Filled.VpnKey) {
                        openApp(
                            context,
                            listOf("com.hyundaiusa.hyundai.digitalcarkey"),
                            "https://play.google.com/store/apps/details?id=com.hyundaiusa.hyundai.digitalcarkey",
                            inApp,
                        )
                    }
                    Brand.GENESIS -> LinkButton("Digital Key", Icons.Filled.VpnKey) {
                        openApp(
                            context,
                            listOf("com.genesisusa.genesis.digitalcarkey"),
                            "https://play.google.com/store/apps/details?id=com.genesisusa.genesis.digitalcarkey",
                            inApp,
                        )
                    }
                    Brand.KIA -> Unit
                }
            } else {
                if (isSamsung) {
                    LinkButton("Digital Key", Icons.Filled.CreditCard) {
                        openApp(context, listOf("com.samsung.android.spay"), "https://www.samsung.com/us/samsung-wallet/", inApp)
                    }
                } else {
                    LinkButton("Digital Key", Icons.Filled.AccountBalanceWallet) {
                        openApp(
                            context,
                            listOf("com.google.android.apps.walletnfcrel", "com.google.android.apps.wallet"),
                            "https://pay.google.com/",
                            inApp,
                        )
                    }
                }
            }
        }
    }
}

/** A compact owner-area destination button (sized to its label, not full width). */
@Composable
private fun LinkButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    // Same morphing pill framework as every other button, with a tonal fill that
    // reads clearly on the car-info pebble.
    MorphButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

// --- Diagnostics ----------------------------------------------------------

private data class DiagRow(val label: String, val value: String, val indent: Boolean = false)

@Composable
private fun DiagnosticsPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val a = vm.appearance.collectAsState().value
    val fahrenheit = a.useFahrenheit
    val metric = a.unitSystem == "metric"
    val rows = remember(status, fahrenheit, metric) { buildList {
        status?.tirePressureLamp?.let { tp ->
            val psiSuffix = status.tirePressure?.all?.takeIf { it > 0 }?.let { " · $it psi" } ?: ""
            add(DiagRow("Tire pressure", if (tp.hasWarning) "Warning$psiSuffix" else "OK$psiSuffix"))
            tp.frontLeft?.let { add(DiagRow("Front left", warn(it), indent = true)) }
            tp.frontRight?.let { add(DiagRow("Front right", warn(it), indent = true)) }
            tp.rearLeft?.let { add(DiagRow("Rear left", warn(it), indent = true)) }
            tp.rearRight?.let { add(DiagRow("Rear right", warn(it), indent = true)) }
        }
        status?.battery?.let { b ->
            b.batSoc?.let { soc ->
                add(DiagRow("12V battery", "$soc%"))
            }
        }
        status?.evStatus?.batteryStatus?.let { add(DiagRow("Drive battery", "$it%")) }
        status?.rangeMiFor(state.hasBattery(v))?.let { add(DiagRow("Range", formatDistance(it, metric))) }
        status?.airTemp?.value?.let { add(DiagRow("Climate setpoint", degLabel(it, fahrenheit))) }
        status?.fuelLevel?.let { add(DiagRow("Fuel level", "$it%")) }
        status?.lowFuelLight?.let { add(DiagRow("Low fuel", yesNo(it))) }
        status?.washerFluidStatus?.let { add(DiagRow("Washer fluid", if (it) "Low" else "OK")) }
        status?.breakOilStatus?.let { add(DiagRow("Brake fluid", if (it) "Check" else "OK")) }
        status?.smartKeyBatteryWarning?.let { add(DiagRow("Key fob battery", if (it) "Low" else "OK")) }
        status?.steerWheelHeat?.let { add(DiagRow("Steering wheel heat", onOff(it))) }
        status?.sideBackWindowHeat?.let { add(DiagRow("Rear defroster", onOff(it))) }
        status?.sideMirrorHeat?.let { add(DiagRow("Mirror heat", onOff(it))) }
        status?.seatHeaterVentState?.let { s ->
            val seats = listOfNotNull(
                s.flSeatHeatState?.takeIf { it != 0 }?.let { "Driver" },
                s.frSeatHeatState?.takeIf { it != 0 }?.let { "Passenger" },
                s.rlSeatHeatState?.takeIf { it != 0 }?.let { "Rear-left" },
                s.rrSeatHeatState?.takeIf { it != 0 }?.let { "Rear-right" },
            )
            if (seats.isNotEmpty()) add(DiagRow("Seat heat/vent active", seats.joinToString(", ")))
        }
        status?.evStatus?.pluggedInLabel?.let { add(DiagRow("Plug", it)) }
        status?.evStatus?.remainTime2?.atc?.value?.let { add(DiagRow("Time to full", "${it.toInt()} min")) }
        status?.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
            ?.let { add(DiagRow("Doors open", it.joinToString(", "))) }
        if (status?.trunkOpen == true) add(DiagRow("Trunk", "Open"))
        if (status?.hoodOpen == true) add(DiagRow("Hood", "Open"))
        if (status?.doorLock == false && status.engine != true) add(DiagRow("Lock", "Car is unlocked while parked"))
    } }
    // Surface a warning affordance if any diagnostic reports a problem.
    val hasWarning = remember(status) {
        (status?.tirePressureLamp?.hasWarning == true) ||
        status?.lowFuelLight == true || status?.washerFluidStatus == true ||
        status?.breakOilStatus == true || status?.smartKeyBatteryWarning == true
    }
    val diagSummary = remember(rows) { if (rows.isEmpty()) "No data" else "${rows.count { !it.indent }} checks" }
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, dragHandle,
        summary = diagSummary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        headerAction = if (hasWarning) PebbleHeaderAction(
            label = "",
            icon = Icons.Filled.Warning,
            onClick = { vm.togglePebble(v, "diagnostics") },
            isWarning = true,
            contentDescription = "Diagnostics warning",
        ) else null,
    ) {
        if (rows.isEmpty()) {
            Text(
                "No diagnostics yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { row ->
            if (row.indent) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        row.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(row.value, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                StatusRow(row.label, row.value)
            }
        }
    }
}

private fun warn(v: Int) = if (v == 0) "OK" else "Warning"
private fun yesNo(v: Boolean) = if (v) "Yes" else "No"
private fun onOff(v: Int) = if (v == 0) "Off" else "On"

// --- Climate --------------------------------------------------------------

/**
 * The climate control pebble -- by far the most stateful pebble in the app.
 * Local editable state (temp, duration, defrost, steering-wheel heat, and
 * all four seat levels) is `remember(v.vin)`-keyed so switching cars resets
 * to that car's own values rather than carrying over the previous car's.
 *
 * Three things keep this state in sync with the outside world:
 *  1. On first composition per car, `vm.loadSavedClimate` restores whatever
 *     was last saved for this car (`settingsLoaded` gates the debounced
 *     save below so it doesn't immediately re-save the values it just
 *     loaded).
 *  2. `remoteClimate` (from `state.climateSync`) mirrors whatever the watch
 *     app or another session set; a [LaunchedEffect] keyed on it snaps all
 *     the local state to match whenever it changes.
 *  3. A single debounced [LaunchedEffect] keyed on `(currentReq,
 *     activePresetId)` persists + publishes the current settings back out
 *     (to storage and to the watch) after they stop changing -- the actual
 *     400ms debounce lives in the ViewModel's own coroutine scope rather
 *     than in this effect, specifically so a car-switch or pebble collapse
 *     that removes this composable from the tree within that window can't
 *     silently cancel and drop the pending save.
 *
 * `activePresetId` tracks which saved preset (if any) matches the live
 * settings exactly; it's cleared automatically the moment any control
 * drifts away from that preset's exact values, so the "active" highlight
 * only ever marks a true match, never a stale one.
 *
 * The header's Start/Stop button is context-sensitive: while climate is
 * already on it stops it; while the pebble is expanded (sliders visible) it
 * starts with exactly what's shown; while collapsed in Simple mode it
 * computes a "smart" one-tap target temperature from the current weather
 * instead of making the user open the pebble first.
 */
@Composable
private fun ClimatePebble(
    v: Vehicle,
    status: VehicleStatus?,
    seats: SeatConfig,
    state: UiState,
    vm: AppViewModel,
    dragHandle: Modifier,
) {
    val pending = state.isPending(v.vin, "climate")
    val fahrenheit = vm.appearance.collectAsState().value.useFahrenheit
    var tempF by remember(v.vin) { mutableIntStateOf(DEFAULT_CLIMATE_TEMP_F) }
    var duration by remember(v.vin) { mutableIntStateOf(DEFAULT_CLIMATE_DURATION_MIN) }
    var defrost by remember(v.vin) { mutableStateOf(false) }
    var steeringHeat by remember(v.vin) { mutableStateOf(false) }
    var driver by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var passenger by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearLeft by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearRight by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var settingsLoaded by remember(v.vin) { mutableStateOf(false) }

    // Restore the car's last-used climate settings the first time the pebble shows.
    LaunchedEffect(v.vin) {
        vm.loadSavedClimate(v)?.let { r ->
            tempF = r.tempF
            duration = r.durationMinutes
            defrost = r.defrost
            steeringHeat = r.steeringWheelHeat
            driver = r.seatFrontLeft
            passenger = r.seatFrontRight
            rearLeft = r.seatRearLeft
            rearRight = r.seatRearRight
        }
        settingsLoaded = true
    }

    val currentReq = ClimateRequest(
        tempF = tempF,
        defrost = defrost,
        durationMinutes = duration,
        steeringWheelHeat = steeringHeat,
        seatFrontLeft = driver,
        seatFrontRight = passenger,
        seatRearLeft = rearLeft,
        seatRearRight = rearRight,
    )
    // Persist + watch-mirror is handled by ONE debounced call further down
    // (after activePresetId exists) - see the LaunchedEffect near the climate
    // sync block.

    val presets = state.climatePresets[v.vin].orEmpty()
    var showAddPreset by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    // Which preset (if any) is currently applied: set when you start one, and
    // cleared automatically once the live settings drift away from it (e.g. you
    // nudge a slider) so the highlight only marks a true match.
    var activePresetId by remember(v.vin) { mutableStateOf<String?>(null) }
    val applyPreset: (ClimateRequest) -> Unit = { r ->
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = r.steeringWheelHeat
        driver = r.seatFrontLeft
        passenger = r.seatFrontRight
        rearLeft = r.seatRearLeft
        rearRight = r.seatRearRight
    }
    LaunchedEffect(currentReq, activePresetId, presets) {
        val active = presets.firstOrNull { it.id == activePresetId }
        if (active != null && active.request != currentReq) activePresetId = null
    }

    // --- Two-way climate sync with the watch ----------------------------------
    // Reflect whatever the watch (or another session) set: sliders + active preset.
    val remoteClimate = state.climateSync[v.vin]
    LaunchedEffect(remoteClimate) {
        val r = remoteClimate ?: return@LaunchedEffect
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = r.steering
        driver = SeatLevel.fromApi(r.seatFrontLeft)
        passenger = SeatLevel.fromApi(r.seatFrontRight)
        rearLeft = SeatLevel.fromApi(r.seatRearLeft)
        rearRight = SeatLevel.fromApi(r.seatRearRight)
        activePresetId = r.activePresetId
    }
    // Persist + publish-to-watch once settings stop changing, not on every drag
    // tick: publishClimateState updates the shared ViewModel StateFlow the whole
    // screen collects, so per-tick commits recomposed far more than the slider
    // being dragged (read as "the sliders don't react until long after you
    // change them"). The 400ms debounce lives in the ViewModel (viewModelScope),
    // NOT here: an effect-side delay was cancelled whenever this pebble left
    // composition within 400ms of the last adjustment (cover-screen tile swipe,
    // car switch, collapse), silently reverting the user's change.
    LaunchedEffect(currentReq, activePresetId) {
        if (settingsLoaded) vm.saveClimateDebounced(v, currentReq, activePresetId)
    }

    val climateOn = status?.airCtrlOn == true
    // The car rejects remote climate commands while it's moving, so the whole
    // control goes read-only when driving - and if it's already on, we show
    // what it's currently set to at the car instead of editable inputs.
    val driving = state.isDriving(v)
    val startClimate = { vm.startClimate(v, currentReq) }
    val weather = state.carWeather[v.vin] ?: state.homeWeather
    val simpleMode = state.settingsMode != "advanced"
    // Whether the pebble's own body (the live sliders below) is actually on
    // screen right now -- mirrors Pebble()'s own expanded computation exactly
    // so this and the header's Start button agree on what "expanded" means.
    val expanded = LocalForceExpanded.current || state.isPebbleExpanded(v.vin, "climate")

    Pebble(
        v, "climate", "Climate", Icons.Filled.AcUnit, state, vm, dragHandle,
        summary = when {
            climateOn && driving -> "On · driving"
            climateOn -> "On"
            else -> "Off"
        },
        headerAction = PebbleHeaderAction(
            label = when {
                climateOn && driving -> "On"
                climateOn -> "Stop"
                else -> "Start"
            },
            icon = Icons.Filled.AcUnit,
            onClick = {
                if (climateOn) {
                    vm.stopClimate(v); activePresetId = null
                } else if (expanded) {
                    // The sliders are visible and live-editable right here --
                    // Start should do exactly what they're currently set to,
                    // not second-guess with the smart/preset logic meant for
                    // the collapsed one-tap case below.
                    startClimate()
                } else if (simpleMode && weather != null) {
                    val ambientF = ambientFahrenheit(weather.tempC)
                    val smartTarget = smartClimateTargetF(ambientF)
                    tempF = smartTarget; defrost = false; activePresetId = null
                    vm.startClimate(v, currentReq.copy(tempF = smartTarget, defrost = false))
                } else {
                    val defaultId = state.defaultClimatePresets[v.vin]
                    val matchingPreset = defaultId?.let { id -> presets.firstOrNull { it.id == id } }
                    if (matchingPreset != null) {
                        applyPreset(matchingPreset.request)
                        vm.startClimate(v, matchingPreset.request)
                        activePresetId = matchingPreset.id
                    } else if (weather != null) {
                        val ambientF = ambientFahrenheit(weather.tempC)
                        val smartTarget = smartClimateTargetF(ambientF)
                        tempF = smartTarget; defrost = false; activePresetId = null
                        vm.startClimate(v, currentReq.copy(tempF = smartTarget, defrost = false))
                    } else startClimate()
                }
            },
            enabled = !driving,
            pending = pending,
            active = climateOn,
            spinning = climateOn,
        ),
    ) {
        if (driving) {
            if (climateOn) {
                Text(
                    "Climate is on at the car. It ignores app commands while you're driving, so this is read-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                )
                status?.airTemp?.value?.let { StatusRow("Set to", degLabel(it, fahrenheit)) }
                status?.defrost?.let { StatusRow("Defrost", if (it) "On" else "Off") }
                status?.steerWheelHeat?.let { StatusRow("Steering wheel heat", onOff(it)) }
                status?.seatHeaterVentState?.let { s ->
                    s.flSeatHeatState?.takeIf { it != 0 }?.let { StatusRow("Driver seat", onOff(it)) }
                    s.frSeatHeatState?.takeIf { it != 0 }?.let { StatusRow("Passenger seat", onOff(it)) }
                }
            } else {
                Text(
                    "Climate can't be started while the car is driving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                )
            }
            return@Pebble
        }

        ClimatePresetSection(
            presets = presets,
            activeId = activePresetId,
            fahrenheit = fahrenheit,
            onStart = { preset ->
                // Tapping the running preset turns climate back off.
                if (activePresetId == preset.id && climateOn) {
                    vm.stopClimate(v)
                    activePresetId = null
                } else {
                    applyPreset(preset.request)
                    vm.startClimate(v, preset.request)
                    activePresetId = preset.id
                }
            },
            onDelete = { id ->
                if (activePresetId == id) activePresetId = null
                vm.deleteClimatePreset(v, id)
            },
            onReorder = { vm.reorderClimatePresets(v, it) },
        )

        // Smart climate: read the weather where the car is (falling back to home)
        // and pick a target -- see smartClimateTargetF, shared with the widget/QS
        // tile and the watch: ~10°F off ambient normally, or the car's most
        // aggressive setting on a genuinely extreme day, always within what the
        // car's own climate range actually accepts.
        if (weather != null) {
            val ambientF = ambientFahrenheit(weather.tempC)
            val smartTarget = smartClimateTargetF(ambientF)
            val targetLabel = degLabel(smartTarget.toString(), fahrenheit)
            val ambientLabel = degLabel(ambientF.toString(), fahrenheit)
            val smartLabel = if (smartClimateIsCooling(ambientF)) "Cool to $targetLabel" else "Heat to $targetLabel"
            SectionLabel("Smart climate")
            MorphButton(
                onClick = {
                    tempF = smartTarget
                    defrost = false
                    activePresetId = null
                    vm.startClimate(v, currentReq.copy(tempF = smartTarget, defrost = false))
                },
                enabled = !pending && !climateOn,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(smartLabel, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "It's $ambientLabel where your car is — Smart climate is targeting $targetLabel.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
            )
        }

        SectionLabel("Controls")

        // Show the set temperature when climate is running, with an animated entrance.
        AnimatedVisibility(
            visible = climateOn,
            enter = fadeIn(tween(300)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Set temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bloo.uicommon.AnimatedValue(degLabel(tempF.toString(), fahrenheit), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Was a hand-rolled version of the same blue->green->warm mapping
        // uicommon.tempColor() now centralizes (shared with the watch, which
        // had drifted to a different, unanimated palette).
        val tempRange = CLIMATE_TEMP_RANGE_F.first.toFloat()..CLIMATE_TEMP_RANGE_F.last.toFloat()
        val tempColor = com.bloo.uicommon.tempColor(tempF, tempRange.start, tempRange.endInclusive)
        if (fahrenheit) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bloo.uicommon.AnimatedValue(degLabel(tempF.toString(), true), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = tempColor))
            }
            AnimatedSlider(
                value = tempF.toFloat(),
                onValueChange = { tempF = it.roundToInt() },
                valueRange = tempRange,
                steps = 19,
                accent = tempColor,
            )
        } else {
            // Celsius: drive the slider in whole °C but keep tempF canonical for
            // the command, converting on each side.
            val tempC = ((tempF - 32) * 5 / 9f).roundToInt()
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bloo.uicommon.AnimatedValue(degLabel(tempF.toString(), false), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = tempColor))
            }
            AnimatedSlider(
                value = tempC.toFloat(),
                onValueChange = { tempF = (it * 9 / 5f + 32).roundToInt() },
                valueRange = 17f..28f,
                steps = 10,
                accent = tempColor,
            )
        }

        StepRow("Run time", "$duration min")
        AnimatedSlider(
            value = duration.toFloat(),
            onValueChange = { duration = it.roundToInt() },
            valueRange = CLIMATE_DURATION_RANGE.first.toFloat()..CLIMATE_DURATION_RANGE.last.toFloat(),
            steps = 8,
        )

        ToggleRow("Defrost", defrost) { defrost = it }
        if (seats.steeringWheel) {
            ToggleRow("Steering wheel heat", steeringHeat) { steeringHeat = it }
        }

        val isGen5W = v.isGen5W
        if (seats.any && !(isGen5W && v.isEv)) {
            SectionLabel("Seats")
            if (seats.driverHeat || seats.driverCool) {
                SeatControl("Driver seat", driver, seats.driverCool, seats.driverHeat) { driver = it }
            }
            if (seats.passHeat || seats.passCool) {
                SeatControl("Passenger seat", passenger, seats.passCool, seats.passHeat) { passenger = it }
            }
            if (seats.rearLeftHeat || seats.rearLeftCool) {
                SeatControl("Rear left seat", rearLeft, seats.rearLeftCool, seats.rearLeftHeat) { rearLeft = it }
            }
            if (seats.rearRightHeat || seats.rearRightCool) {
                SeatControl("Rear right seat", rearRight, seats.rearRightCool, seats.rearRightHeat) { rearRight = it }
            }
        }

        SectionLabel("Save")
        MorphTextButton(
            text = "Save as preset",
            onClick = { presetName = ""; showAddPreset = true },
            modifier = Modifier.fillMaxWidth(),
        )

        if (showAddPreset) {
            // Standardized on the shared GlassAlertDialog shell (stacked buttons).
            GlassAlertDialog(
                onDismissRequest = { showAddPreset = false },
                icon = Icons.Filled.Thermostat,
                title = "Save preset",
                text = {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        shape = FieldShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                buttons = {
                    MorphButton(
                        onClick = {
                            if (presetName.isNotBlank()) {
                                vm.saveClimatePreset(v, presetName.trim(), currentReq)
                                showAddPreset = false
                            }
                        },
                        enabled = presetName.isNotBlank(),
                        active = true,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                    MorphTextButton("Cancel", onClick = { showAddPreset = false }, modifier = Modifier.fillMaxWidth())
                },
            )
        }
    }
}

@Composable
private fun SeatControl(
    label: String,
    level: SeatLevel,
    canCool: Boolean,
    canHeat: Boolean,
    onChange: (SeatLevel) -> Unit,
) {
    val range = SeatLevel.rangeFor(canCool, canHeat)
    if (range.size <= 1) return
    val index = range.indexOf(level).let { if (it < 0) range.indexOf(SeatLevel.OFF) else it }
    val current = range[index]
    // Deeper colour the stronger the setting; smoothly cross-fades as you slide
    // through neutral between cooling (blues) and heating (reds).
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = seatTint(current),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "seatTint",
    )
    Column {
        // The level text (e.g. "High cool") wears the slider's colour, so OFF is
        // neutral, cooling reads blue and heating reads red - no caption needed.
        StepRow(label, current.label, valueColor = tint)
        AnimatedSlider(
            value = index.toFloat(),
            onValueChange = { onChange(range[it.roundToInt().coerceIn(0, range.lastIndex)]) },
            valueRange = 0f..range.lastIndex.toFloat(),
            steps = (range.size - 2).coerceAtLeast(0),
            accent = tint,
        )
    }
}

/** Seat colour by intensity: light->dark blue for cool, light->dark red for heat. */
@Composable
private fun seatTint(level: SeatLevel): Color = when {
    level.isCool -> androidx.compose.ui.graphics.lerp(
        Color(0xFF82B1FF), Color(0xFF1A45C0), ((level.apiValue - 3) / 2f).coerceIn(0f, 1f),
    )
    level.isHeat -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFF8A80), Color(0xFFC62828), ((level.apiValue - 6) / 2f).coerceIn(0f, 1f),
    )
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// --- Climate presets section ----------------------------------------------

@Composable
private fun ClimatePresetSection(
    presets: List<ClimatePreset>,
    activeId: String?,
    fahrenheit: Boolean,
    onStart: (ClimatePreset) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<ClimatePreset>) -> Unit,
) {
    SectionLabel("Presets")
    // Track IDs mid-exit so the item stays visible until its shrink animation ends.
    var deletingIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = presets.isNotEmpty(),
        enter = expandVertically(tween(280)) + fadeIn(tween(220)),
        exit = shrinkVertically(tween(240)) + fadeOut(tween(180)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(4.dp))
            // Full-width reorderable rows: drag handle to re-rank, tap to apply.
            ReorderColumn(
                items = presets,
                keyOf = { it.id },
                onReorder = onReorder,
                spacing = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { preset, dragHandle, _ ->
                AnimatedVisibility(
                    visible = preset.id !in deletingIds,
                    enter = scaleIn(tween(240, easing = LinearOutSlowInEasing), initialScale = 0.88f) +
                        expandVertically(tween(260)) + fadeIn(tween(200)),
                    exit = scaleOut(tween(180, easing = FastOutLinearInEasing), targetScale = 0.88f) +
                        shrinkVertically(tween(220)) + fadeOut(tween(160)),
                ) {
                    PresetPill(
                        name = preset.name,
                        detail = presetDetail(preset.request, fahrenheit),
                        active = preset.id == activeId,
                        onStart = { onStart(preset) },
                        onDelete = {
                            val id = preset.id
                            scope.launch {
                                deletingIds = deletingIds + id
                                delay(240)
                                onDelete(id)
                                deletingIds = deletingIds - id
                            }
                        },
                        dragHandle = dragHandle,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A compact "79° · Defrost · Heat" summary of what a preset will set. */
private fun presetDetail(req: ClimateRequest, fahrenheit: Boolean): String {
    val parts = mutableListOf<String>()
    parts += if (fahrenheit) "${req.tempF}°" else "${Math.round((req.tempF - 32) * 5 / 9.0)}°"
    if (req.defrost) parts += "Defrost"
    val seats = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
    if (seats.any { it.isHeat }) parts += "Heat"
    if (seats.any { it.isCool }) parts += "Cool"
    if (req.steeringWheelHeat) parts += "Wheel"
    return parts.joinToString(" · ")
}

/**
 * A two-segment split button for a saved preset, styled after M3 Expressive
 * connected-button group #5: a wider "start" half and a narrow "delete" half,
 * each a pill on its outer edge with a smaller radius on the inner edge. The two
 * are separated by a real gap (not a drawn line) so the pebble background shows
 * through and they read as distinct buttons.
 *
 * Tapping the start half loads the preset into the climate controls and fires it;
 * while it is the [active] (currently applied) preset, that half morphs from a
 * pill into a rounded rectangle and fills with the running-climate highlight,
 * exactly like the Start button when climate is on. The delete half removes it.
 */
@Composable
private fun PresetPill(
    name: String,
    detail: String,
    active: Boolean,
    onStart: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val leftInteraction = remember { MutableInteractionSource() }
    val leftPressed by leftInteraction.collectIsPressedAsState()
    val morphed = active || leftPressed
    // Outer corner: full pill when idle, rounded-rectangle when applied/pressed.
    val outer by animateDpAsState(
        if (morphed) 16.dp else 50.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "presetOuter",
    )
    // Inner corner (facing the gap): small nub when idle, matches outer when applied.
    val inner by animateDpAsState(
        if (morphed) 16.dp else 10.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "presetInner",
    )
    val leftBg by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else buttonContainer(),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "presetLeftBg",
    )
    val leftFg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    // Delete was a single un-confirmable tap right beside the much larger,
    // frequently-tapped Apply half -- a slightly mis-aimed tap silently and
    // irreversibly dropped a saved preset. Now requires a second tap, same
    // "tap again to confirm" pattern (with the same 4s auto-reset) used for
    // Sign out and the watch's own preset-delete confirm.
    val confirm = rememberConfirmArm()
    val deleteBg by androidx.compose.animation.animateColorAsState(
        if (confirm.armed) MaterialTheme.colorScheme.error else buttonContainer(),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "presetDeleteBg",
    )
    val deleteFg = if (confirm.armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface

    // The drag handle wraps the whole pill so long-press anywhere reorders.
    Row(
        modifier = dragHandle.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Apply half — snowflake icon plus the preset name.
        Surface(
            onClick = { haptics?.click(); onStart() },
            interactionSource = leftInteraction,
            color = leftBg,
            contentColor = leftFg,
            shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Icon(Icons.Filled.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Delete nub — inner (left) corners match the gap, outer (right) corners are pill-rounded.
        Surface(
            onClick = {
                if (confirm.armed) {
                    haptics?.tick()
                    onDelete()
                } else {
                    haptics?.tick()
                    confirm.arm()
                }
            },
            color = deleteBg,
            contentColor = deleteFg,
            shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
            modifier = Modifier.fillMaxHeight(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = if (confirm.armed) "Confirm delete $name" else "Delete $name",
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// --- Charge limits --------------------------------------------------------

/**
 * Two-segment split pill for the charge-limit control, styled like the climate
 * presets: wide left half shows the current value and hosts the inline slider;
 * narrow right half ("Set ⚡") sends the command. Morphs from pill to rounded
 * rectangle when pressed, identical motion to [PresetPill].
 */
@Composable
private fun ChargeLimitPill(
    label: String,
    limit: Int,
    pending: Boolean,
    enabled: Boolean,
    icon: ImageVector = Icons.Filled.Bolt,
    onValueChange: (Int) -> Unit,
    onApply: () -> Unit,
) {
    val haptics = LocalHaptics.current
    val leftInteraction = remember { MutableInteractionSource() }
    val leftPressed by leftInteraction.collectIsPressedAsState()

    val outer by animateDpAsState(
        if (leftPressed) 16.dp else 50.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "limitOuter",
    )
    val inner by animateDpAsState(
        if (leftPressed) 16.dp else 10.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "limitInner",
    )
    val rightBg by androidx.compose.animation.animateColorAsState(
        if (pending) MaterialTheme.colorScheme.primary else buttonContainer(),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "limitRightBg",
    )
    val rightFg = if (pending) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Left half — label. Tapping bumps the limit up by one step, wrapping
            // back to 50% after 100%, for quick keyboard-free adjustment.
            Surface(
                onClick = {
                    haptics?.tick()
                    onValueChange(if (limit >= 100) 50 else limit + 10)
                },
                interactionSource = leftInteraction,
                enabled = enabled,
                color = buttonContainer(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
                // Both the current value and what tapping actually does (bump
                // by 10%, wrapping at 100%) were purely visual -- TalkBack
                // announced only the label text with no indication this half
                // was itself a stepper, distinct from "Set" on the right.
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$label, $limit percent"
                        onClick(label = "Increase by 10 percent") {
                            onValueChange(if (limit >= 100) 50 else limit + 10)
                            true
                        }
                    },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    RollingNumber(
                        text = "$limit%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Right half — "Set" nub. Inner (left) corners match the gap; outer (right) are pill-rounded.
            Surface(
                onClick = { haptics?.heavy(); onApply() },
                enabled = enabled && !pending,
                color = rightBg,
                contentColor = rightFg,
                shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
                modifier = Modifier.fillMaxHeight(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
                ) {
                    if (pending) {
                        LoadingIndicator(Modifier.size(18.dp))
                    } else {
                        Text("Set", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AnimatedSlider(
            value = limit.toFloat(),
            onValueChange = { onValueChange((it / 10f).roundToInt() * 10) },
            valueRange = CHARGE_LIMIT_RANGE.first.toFloat()..CHARGE_LIMIT_RANGE.last.toFloat(),
            steps = 4,
        )
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * Charge pebble: collapsed shows just the charge start/stop control; expand to
 * set the charge limit and see charging info. Long-press to drag-reorder.
 */
@Composable
private fun ChargePebble(v: Vehicle, status: VehicleStatus?, enabled: Boolean, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val ev = status?.evStatus
    val charging = ev?.batteryCharge == true
    val plugged = ev?.isPluggedIn == true || charging
    val pending = state.isPending(v.vin, "charge")
    val limitPending = state.isPending(v.vin, "chargeLimit")
    val summary = when {
        charging -> "Charging"
        plugged -> "Plugged in · idle"
        else -> "Not plugged in"
    }

    // Separate AC (home / level-2) and DC (fast) charge-limit targets, each
    // seeded to a healthy default until the car's real targets load in --
    // 80% for AC (a daily ceiling), 90% for DC (fast-charging past that is
    // inefficient anyway; matches the default the watch/shared side already
    // uses -- see WearCommand's acLimit/dcLimit defaults in WearSync.kt --
    // this used to default BOTH to 80%, so tapping "Set" before the real DC
    // target loaded could silently push a DC target lower than intended).
    // Both pills' "Set" sends BOTH values together (setChargeLimits(v,
    // acLimit, dcLimit)), so leaving one un-seeded at a wrong default meant
    // tapping "Set" on just the AC pill silently reset a DC target that had
    // never actually been what it was seeded to -- and vice versa.
    var acLimit by remember(v.vin) { mutableIntStateOf(80) }
    var dcLimit by remember(v.vin) { mutableIntStateOf(90) }
    var limitsSeeded by remember(v.vin) { mutableStateOf(false) }
    LaunchedEffect(v.vin, ev?.reservChargeInfos) {
        if (limitsSeeded) return@LaunchedEffect
        val realAc = ev?.reservChargeInfos?.level(1)
        val realDc = ev?.reservChargeInfos?.level(0)
        if (realAc != null || realDc != null) {
            realAc?.let { acLimit = it }
            realDc?.let { dcLimit = it }
            limitsSeeded = true
        }
    }

    Pebble(
        v, "charge", "Charge", Icons.Filled.Bolt, state, vm, dragHandle,
        summary = summary,
        headerAction = PebbleHeaderAction(
            label = if (charging) "Stop" else "Start",
            icon = Icons.Filled.Bolt,
            onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
            enabled = plugged,
            pending = pending,
            active = charging,
            activeContainer = ChargeGreen,
            activeContent = Color.White,
        ),
    ) {
        if (plugged) {
            chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
        }
        ChargeLimitPill(
            label = "AC (home) limit",
            icon = Icons.Filled.Power,
            limit = acLimit,
            pending = limitPending,
            enabled = enabled,
            onValueChange = { acLimit = it },
            onApply = { vm.setChargeLimits(v, acLimit, dcLimit) },
        )
        ChargeLimitPill(
            label = "DC (fast) limit",
            icon = Icons.Filled.Bolt,
            limit = dcLimit,
            pending = limitPending,
            enabled = enabled,
            onValueChange = { dcLimit = it },
            onApply = { vm.setChargeLimits(v, acLimit, dcLimit) },
        )
        // The charge-port toggle lives in the controls pebble, next to lock/unlock.
    }
}

/**
 * The energy pebble for a gas/hybrid car: fuel level + range, no charge UI at
 * all. Occupies the same "charge" slot so order/collapse state carry over.
 */
@Composable
private fun FuelPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val metric = vm.appearance.collectAsState().value.unitSystem == "metric"
    val fuelPct = status?.fuelLevel
    val range = status?.dte?.value?.toInt()
    val summary = when {
        fuelPct != null && range != null -> "$fuelPct% · ${formatDistance(range, metric)}"
        fuelPct != null -> "$fuelPct%"
        range != null -> "${formatDistance(range, metric)}"
        else -> "--"
    }
    Pebble(
        v, "charge", "Fuel", Icons.Filled.LocalGasStation, state, vm, dragHandle,
        summary = summary,
    ) {
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet.")
            else -> {
                fuelPct?.let { StatusRow("Fuel level", "$it%") }
                range?.let { StatusRow("Range (distance to empty)", formatDistance(it, metric)) }
                if (fuelPct == null && range == null) Text("No fuel data reported.")
            }
        }
    }
}

private fun chargerLabel(plugin: Int?): String? = com.bloo.bluelink.data.chargerLabel(plugin)

private fun fmtMinutes(min: Int) = com.bloo.bluelink.data.fmtMinutes(min)

/**
 * A climate setpoint (the API reports it as a °F string) rendered in the user's
 * chosen unit. Non-numeric values pass through with a bare degree sign.
 */
private fun degLabel(valueF: String, fahrenheit: Boolean): String =
    com.bloo.bluelink.data.degLabel(valueF, fahrenheit)

// Five fixed stops instead of a free continuous 0-2 range with 20 snap
// points -- most of those were indistinguishable by eye, and "what number is
// this" isn't a useful question for a saturation slider. Each label names
// what you'd actually see, ending on "Best Buy TV" (a factor of 2.5 -- since
// saturate() coerces the HSV saturation channel to a hard 1.0 ceiling, this
// already pushes virtually every color to maximum saturation, the visual
// definition of "showroom TV wall" oversaturation).
private val VibrancySteps = floatArrayOf(0f, 0.5f, 1f, 1.6f, 2.5f)
private val VibrancyLabels = listOf("Monochrome", "A bit of color", "Normal", "Extra", "Best Buy TV")
private fun vibrancyIndexFor(v: Float): Int =
    VibrancySteps.indices.minByOrNull { kotlin.math.abs(VibrancySteps[it] - v) } ?: 2

/** Shared by the main Appearance card and the settings-search quick-jump
 *  preview so the 5-stop mapping lives in exactly one place. */
@Composable
private fun VibrancySlider(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    var indexDraft by remember(appearance.vibrancy) { mutableFloatStateOf(vibrancyIndexFor(appearance.vibrancy).toFloat()) }
    StepRow("Vibrancy", VibrancyLabels[indexDraft.roundToInt().coerceIn(0, 4)])
    AnimatedSlider(
        value = indexDraft,
        onValueChange = { indexDraft = it },
        valueRange = 0f..4f,
        steps = 3,
        onValueSettled = {
            val idx = it.roundToInt().coerceIn(0, 4)
            indexDraft = idx.toFloat()
            vm.setVibrancySoon(VibrancySteps[idx])
        },
    )
}

// --- Location -------------------------------------------------------------

@Composable
private fun LocationPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val fahrenheit = vm.appearance.collectAsState().value.useFahrenheit
    val location = state.locations[v.vin]
    val place = state.placeNames[v.vin]
    val locating = state.isPending(v.vin, "locate")
    // Show the place name (or a hint) in the header so it's visible even collapsed.
    val summary = place ?: if (location != null) "Located" else "Not located yet"
    Pebble(
        v, "location", "Location", Icons.Filled.LocationOn, state, vm, dragHandle, summary = summary,
        headerAction = PebbleHeaderAction(
            label = "Locate",
            icon = Icons.Filled.LocationOn,
            onClick = { vm.locate(v) },
            enabled = !locating,
            pending = locating,
            bounceIcon = true,
        ),
    ) {
        if (location == null) {
            Text("Tap Locate to query the car's current position.")
        }
        location?.let { loc ->
            CarMap(
                loc,
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            StatusRow("Coordinates", loc.coordString())
            // Weather where the car is parked. Fetched lazily once we have a fix.
            LaunchedEffect(loc.latitude, loc.longitude) { vm.loadCarWeather(v) }
            state.carWeather[v.vin]?.let { w ->
                WeatherStripe(w, fahrenheit, place ?: "At the car")
            }
            CommandButton("Open in maps", Icons.Filled.Map, Modifier.fillMaxWidth(), true) {
                val uri = Uri.parse(
                    "geo:${loc.latitude},${loc.longitude}" +
                        "?q=${loc.latitude},${loc.longitude}(My car)"
                )
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    )
                }
            }
        }
    }
}

// --- Weather --------------------------------------------------------------

/** The icon for a condition, picking a sun/moon variant by day vs night. */
private fun weatherIcon(code: WeatherCode, isDay: Boolean): ImageVector =
    com.bloo.uicommon.weatherIcon(code.toCode(), isDay)

@Composable
private fun weatherTint(code: WeatherCode, isDay: Boolean): Color =
    com.bloo.uicommon.weatherTint(code.toCode(), isDay, MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * A compact one-line weather readout: icon, temperature and condition, with a
 * small caption (place name) underneath. Used inside the Location pebble.
 */
@Composable
private fun WeatherStripe(weather: Weather, fahrenheit: Boolean, caption: String) {
    val tint = weatherTint(weather.condition, weather.isDay)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(weatherIcon(weather.condition, weather.isDay), contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text(weather.condition.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RollingNumber(
            text = weather.tempLabel(fahrenheit),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The Weather pebble: current conditions at the user's configured "home"
 * location, with a big temperature, condition icon and a few detail rows. Shown
 * identically on every car (it's a global readout). If no location is set it
 * nudges the user to Settings.
 */
@Composable
private fun WeatherPebble(v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val appearance by vm.appearance.collectAsState()
    val hasLocation = appearance.weatherLat != null && appearance.weatherLon != null
    val fahrenheit = appearance.useFahrenheit
    val w = state.homeWeather
    var weatherSpinning by remember { mutableStateOf(false) }
    var spinStartedAt by remember { mutableLongStateOf(0L) }
    // Refresh on first show (the VM throttles to a 15-minute TTL).
    LaunchedEffect(appearance.weatherLat, appearance.weatherLon) {
        if (hasLocation) vm.loadHomeWeather()
    }
    // Stop the spinner once new weather data arrives, but keep it visible for a
    // minimum duration so a cached/instant response still shows the animation.
    LaunchedEffect(state.homeWeather?.fetchedAt) {
        if (weatherSpinning) {
            val elapsed = System.currentTimeMillis() - spinStartedAt
            val minSpin = 900L
            if (elapsed < minSpin) delay(minSpin - elapsed)
            weatherSpinning = false
        }
    }
    val summary = when {
        !hasLocation -> "Set a location"
        w != null -> "${w.tempLabel(fahrenheit)} · ${w.condition.label}"
        else -> "Loading…"
    }
    Pebble(
        v, "weather", "Weather", Icons.Filled.WbSunny, state, vm, dragHandle, summary = summary,
        headerAction = PebbleHeaderAction(
            label = "Refresh",
            icon = Icons.Filled.Refresh,
            onClick = {
                weatherSpinning = true
                spinStartedAt = System.currentTimeMillis()
                vm.loadHomeWeather(force = true)
            },
            enabled = hasLocation,
            spinning = weatherSpinning,
        ),
    ) {
        when {
            !hasLocation -> Text(
                "Set your weather location in Settings → Weather to see local conditions here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            w == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Fetching current conditions…")
            }
            else -> {
                val tint = weatherTint(w.condition, w.isDay)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(
                        weatherIcon(w.condition, w.isDay),
                        contentDescription = w.condition.label,
                        tint = tint,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        RollingNumber(
                            text = w.tempLabel(fahrenheit),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(w.condition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        appearance.weatherLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                StatusRow("Feels like", w.feelsLikeLabel(fahrenheit))
                w.highLowLabel(fahrenheit)?.let { StatusRow("High / low", it) }
                w.humidity?.let { StatusRow("Humidity", "$it%") }
                StatusRow("Wind", formatSpeed(w.windKph, appearance.unitSystem == "metric"))
            }
        }
    }
}

/**
 * A small slippy map centred on the car, assembled from key-free OpenStreetMap
 * raw tiles (tile.openstreetmap.org). We compute the tiles needed to fill the box
 * with the car at the centre, draw each at its pixel offset, then drop a pin in
 * the middle. This avoids the flaky static-map render services that painted blank.
 */
@Composable
private fun CarMap(location: GeoLocation, modifier: Modifier = Modifier) {
    val zoom = 15
    val context = LocalContext.current
    val pinColor = MaterialTheme.colorScheme.error
    BoxWithConstraints(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val density = LocalDensity.current
        val tilePx = 256f
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val span = 1 shl zoom
        val latRad = Math.toRadians(location.latitude)
        val xTileF = (location.longitude + 180.0) / 360.0 * span
        val yTileF = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * span
        // World-pixel of the box's top-left so the car lands dead-centre.
        val originX = (xTileF * tilePx - wPx / 2f).toFloat()
        val originY = (yTileF * tilePx - hPx / 2f).toFloat()
        val firstX = floor(originX / tilePx).toInt()
        val firstY = floor(originY / tilePx).toInt()
        val lastX = floor((originX + wPx) / tilePx).toInt()
        val lastY = floor((originY + hPx) / tilePx).toInt()
        val tileDp = with(density) { tilePx.toDp() }
        for (tx in firstX..lastX) {
            for (ty in firstY..lastY) {
                if (ty < 0 || ty >= span) continue
                val wrappedX = ((tx % span) + span) % span
                val offX = tx * tilePx - originX
                val offY = ty * tilePx - originY
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data("https://tile.openstreetmap.org/$zoom/$wrappedX/$ty.png")
                        .setHeader("User-Agent", "Bloo Bluelink companion app")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(tileDp)
                        .offset(x = with(density) { offX.toDp() }, y = with(density) { offY.toDp() }),
                )
            }
        }
        // A pin whose tip points at the centred car position.
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = "Car location",
            tint = pinColor,
            modifier = Modifier.align(Alignment.Center).size(40.dp).offset(y = (-20).dp),
        )
    }
}

// --- Service & links ------------------------------------------------------


private fun openUrl(context: Context, url: String, inApp: Boolean) {
    val uri = Uri.parse(url)
    val external = { context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
    if (inApp) {
        runCatching { CustomTabsIntent.Builder().build().launchUrl(context, uri) }
            .onFailure { runCatching { external() } }
    } else {
        runCatching { external() }
    }
}

private fun openApp(context: Context, packages: List<String>, fallbackUrl: String, inApp: Boolean) {
    for (p in packages) {
        context.packageManager.getLaunchIntentForPackage(p)?.let {
            runCatching { context.startActivity(it) }.onSuccess { return }
        }
    }
    openUrl(context, fallbackUrl, inApp)
}

private fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

// --- Photo crop -----------------------------------------------------------

/**
 * Lightweight, crash-free crop: pinch-zoom + drag the picked image inside a 16:9
 * frame, then export the framed region to a file. Drawn via a Canvas + Matrix so
 * what you see is what gets saved.
 */
@Composable
private fun CropScreen(vin: String, uriString: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bmp by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uriString) { mutableStateOf(false) }
    var scale by remember(uriString) { mutableFloatStateOf(1f) }
    var offset by remember(uriString) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uriString) {
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return@runCatching null
                // Apply the photo's EXIF orientation (camera photos are often rotated).
                val orientation = context.contentResolver.openInputStream(uri)?.use {
                    androidx.exifinterface.media.ExifInterface(it).getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                val m = android.graphics.Matrix()
                when (orientation) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                }
                if (m.isIdentity) raw
                else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            }.getOrNull()
        }
        if (bmp == null) failed = true
    }

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = bmp
                when {
                    image != null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(18.dp))
                            .onSizeChanged { frame = it }
                            .pointerInput(image) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    offset += pan
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val wpx = size.width
                            val hpx = size.height
                            val cover = max(wpx / image.width, hpx / image.height)
                            val s = cover * scale
                            val maxX = ((image.width * s - wpx) / 2f).coerceAtLeast(0f)
                            val maxY = ((image.height * s - hpx) / 2f).coerceAtLeast(0f)
                            val cx = offset.x.coerceIn(-maxX, maxX)
                            val cy = offset.y.coerceIn(-maxY, maxY)
                            val m = android.graphics.Matrix().apply {
                                postTranslate(-image.width / 2f, -image.height / 2f)
                                postScale(s, s)
                                postTranslate(wpx / 2f + cx, hpx / 2f + cy)
                            }
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(image, m, null) }
                        }
                    }
                    failed -> Text("Couldn't load that image", color = Color.White)
                    else -> LoadingIndicator()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MorphTextButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                MorphButton(
                    onClick = {
                        val image = bmp ?: return@MorphButton
                        val f = frame
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                runCatching {
                                    val wpx = f.width.toFloat()
                                    val hpx = f.height.toFloat()
                                    val cover = max(wpx / image.width, hpx / image.height)
                                    val s = cover * scale
                                    val maxX = ((image.width * s - wpx) / 2f).coerceAtLeast(0f)
                                    val maxY = ((image.height * s - hpx) / 2f).coerceAtLeast(0f)
                                    val cx = offset.x.coerceIn(-maxX, maxX)
                                    val cy = offset.y.coerceIn(-maxY, maxY)
                                    val outScale = 1080f / wpx
                                    val out = Bitmap.createBitmap(1080, (hpx * outScale).toInt(), Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(out)
                                    val m = android.graphics.Matrix().apply {
                                        postTranslate(-image.width / 2f, -image.height / 2f)
                                        postScale(s, s)
                                        postTranslate(wpx / 2f + cx, hpx / 2f + cy)
                                        postScale(outScale, outScale)
                                    }
                                    canvas.drawBitmap(image, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                                    val dir = java.io.File(context.filesDir, "cars").apply { mkdirs() }
                                    // Preserve transparency: alpha sources are saved as PNG (so the
                                    // background stays see-through and renders seamlessly), others JPEG.
                                    val alpha = image.hasAlpha()
                                    val ext = if (alpha) "png" else "jpg"
                                    val file = java.io.File(dir, "car_${vin}_${System.currentTimeMillis()}.$ext")
                                    file.outputStream().use {
                                        if (alpha) out.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        else out.compress(Bitmap.CompressFormat.JPEG, 90, it)
                                    }
                                    file.absolutePath
                                }.getOrNull()
                            }
                            if (path != null) onSave(path) else onCancel()
                        }
                    },
                    enabled = bmp != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) { Text("Use photo", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// --- Settings -------------------------------------------------------------

/**
 * The whole Settings screen: one long scrolling [Column] of [SettingsCard]s
 * (Accounts, AI, App shortcuts, Cars, Backup & sync, Appearance, Quick
 * Settings tiles, and more further down), plus a floating search bar hoisted
 * outside the scroll so it can stay pinned to the bottom of the screen.
 *
 * Two things apply globally across the whole screen:
 *  - Simple vs. Advanced mode (`state.settingsMode`): several cards/sections
 *    are wrapped in `AnimatedVisibility(visible = advanced, ...)` using one
 *    shared `advancedEnter`/`advancedExit` transition spec, so toggling the
 *    mode reveals or hides every advanced-only section in visual lockstep
 *    rather than each one animating independently.
 *  - Settings search: `query` (live, updates every keystroke, purely for
 *    filtering the on-screen list of matching settings) is intentionally
 *    kept separate from `submittedQuery` (only set on an explicit
 *    submit/tap), since a mis-typed partial query must never itself trigger
 *    a real command or an AI request -- only a deliberate submission does.
 *
 * [BackHandler] is layered: while the search pill is expanded or has text,
 * back collapses/clears search first (matching how every other "expanded
 * surface" in the app treats back); only once search is already idle does
 * back return to the garage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val appearance by vm.appearance.collectAsState()
    val notif by vm.notifications.collectAsState()
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canBio = remember { vm.canUseBiometrics() }
    val settingsScroll = rememberScrollState()
    val settingsScope = rememberCoroutineScope()

    // System back returns to the garage, not out of the app.
    var pickTarget by remember { mutableStateOf<String?>(null) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    // System photo picker (crash-free), then our own Compose crop step.
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && pickTarget != null) cropUri = uri
    }

  val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  // Hoisted above the scrolling column: the search bar now floats fixed to
  // the bottom of the SCREEN (a sibling of the scrolling content, not part of
  // its scrolled flow), so both it and the column need this state.
  var query by remember { mutableStateOf("") }
  var searchFocused by remember { mutableStateOf(false) }
  // System back returns to the garage, not out of the app -- but while the
  // search pill is expanded, back should collapse it back to the small
  // button first (matching every other "expanded surface" in the app),
  // not skip straight past it to the previous screen.
  BackHandler {
      if (searchFocused || query.isNotEmpty()) {
          searchFocused = false
          query = ""
      } else {
          vm.closeSettings()
      }
  }
  // Separate from `query`, which updates on every keystroke purely to
  // live-filter the matching-settings list below (no side effects). Running
  // a vehicle command or an AI query is a real action -- it must only fire
  // once the user has actually submitted (Enter/search key, or tapping a
  // suggestion chip), never mid-typing off a debounce timer.
  var submittedQuery by remember { mutableStateOf("") }
  // Drop any stale AI answer once the search box is cleared.
  LaunchedEffect(query.isBlank()) { if (query.isBlank()) { vm.clearAiReply(); submittedQuery = "" } }
  BackdropHost {
        // On wide screens (tablets, landscape), cap width and centre so lines
        // don't stretch wall-to-wall.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .verticalScroll(settingsScroll)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Content scrolls behind the status bar; clear the floating pills.
            Spacer(Modifier.height(topInset + 56.dp))
            run {
                val advanced = state.settingsMode == "advanced"
            // Shared transition for every advanced-only card/section below, so
            // switching Simple/Advanced reveals or tucks them away smoothly
            // instead of an abrupt appear/disappear (the outer Column's own
            // animateContentSize only smooths the resulting height change
            // around them, not their own appearance).
            // A slower, near-critical settle (SoftDamping = 0.82, barely any
            // overshoot) instead of the previous MediumBouncy (0.2, very
            // bouncy) -- the mode switch read as too springy/fast; this reads
            // as a calmer, slightly longer settle instead. Shared with
            // CarSettingsCard/SettingsCard's own animateContentSize below so
            // all three settle at the same feel instead of visibly disagreeing.
            val advancedEnter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = AdvancedModeStiffness))
            val advancedExit = fadeOut(tween(150)) + shrinkVertically(spring(dampingRatio = SoftDamping, stiffness = AdvancedModeStiffness))
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.animateContentSize(spring(dampingRatio = SoftDamping, stiffness = AdvancedModeStiffness)),
            ) {
            // Accounts (one per brand; Hyundai + Genesis can both be signed in).
            SettingsCard("Accounts") {
                if (state.accounts.isEmpty()) {
                    Text(
                        "Not signed in",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.accounts.forEachIndexed { i, creds ->
                    if (i > 0) Spacer(Modifier.height(16.dp))
                    var pin by remember(creds.brand, creds.pin) { mutableStateOf(creds.pin) }
                    // Was a single un-confirmed tap that signed the account out
                    // immediately -- same "tap again to confirm" + 4s
                    // auto-reset pattern used for the climate preset/palette
                    // deletes above, so every destructive action in the app
                    // now asks for the same second tap instead of some firing
                    // instantly and others not.
                    var confirmSignOut by remember(creds.brand) { mutableStateOf(false) }
                    LaunchedEffect(confirmSignOut) {
                        if (confirmSignOut) {
                            delay(4000)
                            confirmSignOut = false
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(creds.brand.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusRow("Email", creds.email)
                        SecretRow("Password", creds.password)
                        // Kia US has no service PIN; commands are session-keyed.
                        if (!creds.brand.usesOtpLogin) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                label = { Text("Service PIN") },
                                singleLine = true,
                                shape = FieldShape,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!creds.brand.usesOtpLogin) {
                                MorphTextButton(
                                    "Update PIN",
                                    onClick = { vm.updatePin(creds.brand, pin) },
                                    enabled = pin.isNotBlank() && pin != creds.pin,
                                )
                            }
                            MorphTextButton(
                                if (confirmSignOut) "Tap again to confirm" else "Sign out",
                                onClick = {
                                    if (confirmSignOut) { vm.logout(creds.brand); confirmSignOut = false }
                                    else confirmSignOut = true
                                },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MorphTextButton("Add another account", onClick = { vm.beginAddAccount() }, modifier = Modifier.fillMaxWidth())
                Text(
                    "If commands fail with a locked PIN, fix the Service PIN above. Too " +
                        "many wrong-PIN attempts lock it for a few minutes server-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // On-device AI - only when the device supports Gemini Nano. Always
            // shown (not advanced-only): it's a headline feature, not a power-
            // user knob, and hiding it behind Advanced made it easy to miss.
            if (state.aiSupported) {
                SettingsCard("AI") {
                    ToggleRow("On-device AI (Gemini Nano)", state.aiEnabled) { vm.setAiEnabled(it) }
                    Text(
                        "Adds an AI summary pebble to each car and lets you ask the search " +
                            "box plain questions like \"what's the odometer\". Everything runs " +
                            "privately on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Advanced-only: a power-user nuance on top of the basic
                    // AI toggle above, not something a novice needs to see.
                    if (state.aiEnabled && advanced) {
                        ToggleRow("Summarize automatically", state.aiAuto) { vm.setAiAuto(it) }
                        Text(
                            if (state.aiAuto) {
                                "Summaries refresh on their own when you open a car, refresh its " +
                                    "status, or send a command. You can still tap Summarize anytime."
                            } else {
                                "Summaries only run when you tap Summarize on a car."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // App-icon shortcuts (long-press the launcher icon)
            AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
                var shortcutsExpanded by remember { mutableStateOf(false) }
                SettingsCard("App shortcuts") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Quick-access shortcuts from the launcher icon",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MorphExpandButton(expanded = shortcutsExpanded, onToggle = { shortcutsExpanded = !shortcutsExpanded })
                    }
                    AnimatedVisibility(
                        visible = shortcutsExpanded,
                        enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(160)),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            state.vehicles.forEach { v ->
                                Spacer(Modifier.height(4.dp))
                                Text(v.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                com.bloo.bluelink.Shortcuts.ACTIONS.forEach { cmd ->
                                    ToggleRow(
                                        com.bloo.bluelink.Shortcuts.actionLabel(cmd),
                                        state.isShortcutEnabled(v.vin, cmd),
                                    ) { vm.setShortcutEnabled(v.vin, cmd, it) }
                                }
                            }
                        }
                    }
                }
            }

            // Cars: drag to reorder, tap a car to expand its setup + photo. With a
            // single car there's nothing to order, so it's just shown expanded.
            // Always visible, in both Simple and Advanced -- this used to be
            // wrapped in the same advanced-only AnimatedVisibility as the
            // power-user cards below it, which hid the whole section (photo,
            // powertrain, seat/climate features, everything) from anyone in
            // Simple mode, the app's default. The two genuinely power-user
            // groups inside CarSettingsCard (default climate preset, palette
            // override) already have their own `state.settingsMode ==
            // "advanced"` checks, so gating the section as a whole here was
            // redundant with those AND too broad.
            if (state.vehicles.isNotEmpty()) {
                var expandedCar by remember { mutableStateOf<String?>(null) }
                val single = state.vehicles.size == 1
                val pick: (String) -> Unit = { vin ->
                    pickTarget = vin
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                SettingsCard(if (single) "Car" else "Cars") {
                    if (single) {
                        val v = state.vehicles[0]
                        CarSettingsCard(
                            v = v, state = state, vm = vm,
                            expanded = true, dragging = false, dragHandle = Modifier,
                            collapsible = false, showHandle = false,
                            onToggle = {}, onPickPhoto = { pick(v.vin) },
                        )
                    } else {
                        ReorderColumn(
                            items = state.vehicles,
                            keyOf = { it.vin },
                            onReorder = { vm.reorderVehicles(it) },
                            spacing = 8.dp,
                        ) { v, dragHandle, dragging ->
                            CarSettingsCard(
                                v = v, state = state, vm = vm,
                                expanded = expandedCar == v.vin, dragging = dragging, dragHandle = dragHandle,
                                onToggle = { expandedCar = if (expandedCar == v.vin) null else v.vin },
                                onPickPhoto = { pick(v.vin) },
                            )
                        }
                    }
                }
            }

            // Backup / Sync
            SettingsCard("Backup & sync") {
                var showDriveDialog by remember { mutableStateOf(false) }
                val settingsImportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri -> uri?.let { vm.importSettings(context, it) } }
                val driveSaveLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json"),
                ) { uri -> uri?.let { vm.setSyncUri(it) } }
                val driveOpenLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }

                // Icon + status caption up front, matching the icon-led header
                // every other multi-row card in Settings uses (Quick tiles, AI)
                // -- this card was the one still opening on two stacked lines
                // of plain text with no at-a-glance state.
                val driveConfigured = state.syncUri != null
                val driveIcon = when {
                    driveConfigured && state.syncError != null -> Icons.Filled.CloudOff
                    driveConfigured -> Icons.Filled.CloudDone
                    else -> Icons.Filled.CloudSync
                }
                val driveTint = when {
                    driveConfigured && state.syncError != null -> MaterialTheme.colorScheme.error
                    driveConfigured -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(driveIcon, contentDescription = null, tint = driveTint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Automatic Drive sync", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        val statusLabel = when {
                            !driveConfigured -> "Not set up"
                            state.syncError != null -> "Sync failed"
                            else -> com.bloo.bluelink.data.relativeLabel(state.lastSyncMs).takeIf { it.isNotBlank() }?.let { "Synced $it" } ?: "Set up"
                        }
                        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = driveTint)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keeps a Google Drive file continuously up to date, so every " +
                        "signed-in device converges on the same settings automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (showDriveDialog) {
                    DriveSyncSetupDialog(
                        onDismissRequest = { showDriveDialog = false },
                        onSaveToDrive = { showDriveDialog = false; driveSaveLauncher.launch("bloo_settings.json") },
                        onOpenFromDrive = { showDriveDialog = false; driveOpenLauncher.launch(arrayOf("application/json")) },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphTextButton(
                        if (state.syncUri != null) "Change Drive file" else "Set up auto-sync",
                        modifier = Modifier.weight(1f),
                        onClick = { showDriveDialog = true },
                    )
                    if (state.syncUri != null) {
                        MorphTextButton(
                            "Disable",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.clearSyncUri() },
                        )
                    }
                }
                if (state.syncUri != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Settings auto-sync to Drive in the background and on every " +
                            "refresh. Changes made on another device are merged automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    val lastSyncLabel = com.bloo.bluelink.data.relativeLabel(state.lastSyncMs)
                    if (lastSyncLabel.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Last synced $lastSyncLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // File-identity fingerprint: two phones that are truly on the
                    // SAME Drive file show the SAME code here. If they differ, the
                    // devices picked different files (Drive allows duplicate names) —
                    // the #1 reason settings/devices don't converge. This makes that
                    // instantly checkable across phones instead of a mystery.
                    state.syncFileFingerprint?.let { fp ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "File ID: $fp · this code must match on every device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.syncError != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Sync failed: ${state.syncError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // The synced-devices registry: one row per device on this file,
                    // with a ★ primary badge, a "This device" marker, and last-seen.
                    // Tapping a non-primary row makes it primary (source of truth);
                    // this device can be renamed. Empty until the first sync populates
                    // the registry.
                    SyncDevicesSection(state = state, vm = vm)
                    Spacer(Modifier.height(6.dp))
                    // Manual controls: "Sync now" force-pushes/pulls immediately
                    // (available any time, not just after a failure), and "Test
                    // sync" runs a non-destructive round-trip against the real
                    // Drive file so the user can confirm it actually works.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MorphTextButton(
                            "Sync now",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.syncNow() },
                        )
                        MorphTextButton(
                            "Test sync",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.testSync() },
                        )
                    }
                    // "Pull from primary now": force this device to adopt the primary's
                    // full settings. Shown only when a primary exists AND it isn't this
                    // device (pulling from yourself is a no-op).
                    if (state.syncPrimaryId != null && state.syncPrimaryId != state.thisDeviceId) {
                        Spacer(Modifier.height(4.dp))
                        MorphTextButton(
                            "Pull from primary now",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { vm.pullFromPrimary() },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("wifi", "Wi-Fi only", null),
                            SegmentOption("any", "Any network", null),
                        ),
                        selectedKey = if (state.syncWifiOnly) "wifi" else "any",
                        onSelect = { vm.setSyncWifiOnly(it == "wifi") },
                    )
                }

                // Advanced-only: a one-shot export/import file is a power-user
                // fallback (moving settings by hand, a local backup outside
                // Drive) next to the always-on automatic sync above, which is
                // what most people actually want and shouldn't be buried.
                AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
                  Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manual backup", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "A one-time snapshot of your theme, palettes, tile order and " +
                            "preferences as a file — share it anywhere, or restore it later. " +
                            "Sign-in credentials are never included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MorphTextButton(
                            "Export",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.exportSettings(context) },
                        )
                        MorphTextButton(
                            "Restore",
                            modifier = Modifier.weight(1f),
                            onClick = { settingsImportLauncher.launch("application/json") },
                        )
                    }
                  }
                }
            }

            // Display scale
            SettingsCard("Display") {
                // Advanced-only: a power-user knob, unlike the Units picker
                // below it which every user needs regardless of mode.
                if (advanced) {
                    var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
                    StepRow("Text & layout scale", "${(uiScaleDraft * 100).roundToInt()}%")
                    AnimatedSlider(
                        value = uiScaleDraft,
                        onValueChange = { uiScaleDraft = it },
                        valueRange = 0.8f..1.3f,
                        steps = 4,
                        onValueSettled = { uiScaleDraft = (it * 10).roundToInt() / 10f; vm.setUiScaleSoon(uiScaleDraft) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // Unit system: controls temperature, distance, and speed display.
                SettingsSegmentedRow(
                    label = "Units",
                    options = listOf(
                        SegmentOption("imperial", "Imperial", null),
                        SegmentOption("metric", "Metric", null),
                    ),
                    selectedKey = appearance.unitSystem,
                    onSelect = { vm.setUnitSystem(it) },
                )
            }

            // Font
            AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
            SettingsCard("Font") {
                val labels = mapOf(
                    FontChoice.SYSTEM to "System default",
                    FontChoice.ATKINSON to "Atkinson Hyperlegible",
                    FontChoice.GOOGLE_SANS to "Google Sans",
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoice.entries.forEach { choice ->
                        ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
                    }
                }
            }
            }

            // Links
            AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
            SettingsCard("Links") {
                SettingsSegmentedRow(
                    label = "Open links",
                    options = listOf(
                        SegmentOption("app", "In app", null),
                        SegmentOption("browser", "Browser", null),
                    ),
                    selectedKey = if (appearance.linksInApp) "app" else "browser",
                    onSelect = { vm.setLinksInApp(it == "app") },
                )
            }
            }

            // Logs
            AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
            SettingsCard("Logs") {
                var logsExpanded by remember { mutableStateOf(false) }
                val lineCount = logs.size
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Activity log  ·  $lineCount lines",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AnimatedVisibility(logsExpanded) {
                        Row {
                            MorphTextButton("Copy", onClick = {
                                clipboard.setText(AnnotatedString(logs.joinToString("\n")))
                            })
                            Spacer(Modifier.width(8.dp))
                            MorphTextButton("Clear", onClick = { vm.clearLogs() })
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    MorphExpandButton(expanded = logsExpanded, onToggle = { logsExpanded = !logsExpanded })
                }
                AnimatedVisibility(
                    visible = logsExpanded,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(150)) + shrinkVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(4.dp))
                        val logScroll = rememberScrollState()
                        SelectionContainer {
                            Text(
                                text = logs.joinToString("\n").ifBlank { "No activity yet." },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .fadingEdges(logScroll)
                                    .verticalScroll(logScroll),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (lineCount > 0) {
                            Text(
                                "Earliest entries at the top — the newest $lineCount lines are shown.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
            }

            // Notifications
            SettingsCard("Notifications") {
                ToggleRow("Service due alerts", notif.service) { vm.setNotifyService(it) }
                ToggleRow("Door-left-open alerts", notif.doorOpen) { vm.setNotifyDoor(it) }
                if (notif.doorOpen) {
                    var minutes by remember(notif.doorOpenMinutes) { mutableStateOf(notif.doorOpenMinutes.toString()) }
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = {
                            minutes = it.filter(Char::isDigit)
                            minutes.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(vm::setDoorOpenMinutes)
                        },
                        label = { Text("Door-open minutes") },
                        singleLine = true,
                        shape = FieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                ToggleRow("Car-running alerts", notif.running) { vm.setNotifyRunning(it) }
                if (notif.running) {
                    var runMin by remember(notif.runningMinutes) { mutableStateOf(notif.runningMinutes.toString()) }
                    OutlinedTextField(
                        value = runMin,
                        onValueChange = {
                            runMin = it.filter(Char::isDigit)
                            runMin.toIntOrNull()?.takeIf { m -> m in 1..120 }?.let(vm::setRunningMinutes)
                        },
                        label = { Text("Running minutes") },
                        singleLine = true,
                        shape = FieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                Text(
                    "Background checks run roughly every 30 minutes, so alerts may " +
                        "arrive a little after your set time. Door and running alerts " +
                        "include a one-tap action to lock or turn the car off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // The Updates card (CI build number, manual "Check now", auto-check
            // toggle) used to live here -- removed. Updates check automatically
            // now (cold start + every refresh, plus a periodic background
            // worker) and present themselves via the update tile pinned under
            // the hero tile; no settings/manual controls needed any more.

            // Quick Settings tiles -- per-tile config is power-user territory,
            // same tier as App shortcuts/Cars above.
            AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
            SettingsCard("Quick tiles") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Each car can have up to 12 tiles in your Quick Settings shade. " +
                            "Configure below, then tap \"Add to Quick Settings\" to place each one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On tap:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
                    Spacer(Modifier.width(8.dp))
                    MorphSegmented(
                        modifier = Modifier.weight(1f),
                        options = listOf(
                            SegmentOption("background", "Run in background", Icons.Filled.Bolt),
                            SegmentOption("open", "Open the app", Icons.Filled.OpenInNew),
                        ),
                        selectedKey = if (state.tileBackground) "background" else "open",
                        onSelect = { vm.setTileBackground(it == "background") },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.tileBackground) "Tiles fire the command directly and show a confirmation."
                    else "Tiles briefly open Bloo to send the command, then close.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Refresh:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
                    Spacer(Modifier.width(8.dp))
                    MorphSegmented(
                        modifier = Modifier.weight(1f),
                        options = listOf(
                            SegmentOption("off", "Off", null),
                            SegmentOption("on", "On", Icons.Filled.Refresh),
                        ),
                        selectedKey = if (state.tileLiveRefresh) "on" else "off",
                        onSelect = { vm.setTileLiveRefresh(it == "on") },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pulls the car's latest state when the tile appears (throttled to once a minute per car).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                QuickTilesManager(state, vm)
            }
            }

            // Security
            SettingsCard("Security") {
                if (canBio) {
                    SettingsSegmentedRow(
                        label = "Require fingerprint to open",
                        options = listOf(
                            SegmentOption("off", "Off", null),
                            SegmentOption("on", "On", null),
                        ),
                        selectedKey = if (appearance.biometricLock) "on" else "off",
                        onSelect = { key ->
                            if (key == "on") {
                                context.findFragmentActivity()?.let { activity ->
                                    showBiometricPrompt(
                                        activity = activity,
                                        title = "Enable fingerprint lock",
                                        subtitle = "Confirm to require it on launch",
                                        onSuccess = { vm.setBiometricLock(true) },
                                        onError = { },
                                    )
                                }
                            } else {
                                vm.setBiometricLock(false)
                            }
                        },
                    )
                    if (appearance.biometricLock) {
                        Spacer(Modifier.height(6.dp))
                        SettingsSegmentedRow(
                            label = "Lock the app",
                            options = LockTiming.entries.map { t -> SegmentOption(t.name, t.label, null) },
                            selectedKey = appearance.lockTiming.name,
                            onSelect = { key -> runCatching { vm.setLockTiming(LockTiming.valueOf(key)) } },
                        )
                    }
                } else {
                    Text(
                        "No fingerprint/biometric is enrolled on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Sounds & vibration
            SettingsCard("Sounds & vibration") {
                ToggleRow("Haptic feedback", appearance.hapticsEnabled) { vm.setHapticsEnabled(it) }
            }

            // Theme
            SettingsCard("Theme") {
                // Short segment labels; AMOLED is "pure black" for OLED screens.
                SettingsSegmentedRow(
                    label = "Appearance",
                    options = listOf(
                        SegmentOption(ThemeMode.SYSTEM.name, "System", null),
                        SegmentOption(ThemeMode.LIGHT.name, "Light", null),
                        SegmentOption(ThemeMode.DARK.name, "Dark", null),
                        SegmentOption(ThemeMode.AMOLED.name, "AMOLED", null),
                    ),
                    selectedKey = appearance.themeMode.name,
                    onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
                )
                // Advanced-only, same tier as the dynamic-color block below --
                // Aurora's motion/colour-mode/custom-hex sub-options are
                // power-user territory, not something a simple-mode user needs
                // (the built-in solid-surface background covers everyone else).
                AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
                  Column {
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Aurora background", appearance.auroraBackground) { vm.setAuroraBackground(it) }
                    Text(
                        "Show a gradient aurora behind the content instead of a solid surface.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (appearance.auroraBackground) {
                        Spacer(Modifier.height(8.dp))
                        Text("Motion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        MorphSegmented(
                            options = listOf(
                                SegmentOption("static", "Static", null),
                                SegmentOption("motion", "Motion", null),
                            ),
                            selectedKey = appearance.auroraMotion,
                            onSelect = { vm.setAuroraMotion(it) },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Colour", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        MorphSegmented(
                            options = listOf(
                                SegmentOption("complementary", "Complementary", null),
                                SegmentOption("material", "Material You", null),
                                SegmentOption("custom", "Custom", null),
                            ),
                            selectedKey = appearance.auroraColorMode,
                            onSelect = { vm.setAuroraColorMode(it) },
                        )
                        if (appearance.auroraColorMode == "custom") {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = appearance.auroraCustomColor ?: "",
                                onValueChange = { vm.setAuroraCustomColor(it.take(7).takeIf { it.matches(Regex("#[0-9A-Fa-f]{0,6}")) } ?: appearance.auroraCustomColor) },
                                label = { Text("Hex colour") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                  }
                }
                AnimatedVisibility(visible = advanced, enter = advancedEnter, exit = advancedExit) {
                  // AnimatedVisibility lays out a single child, not an implicit
                  // Column of its content lambda's composables -- without this
                  // wrapper the Spacer/Divider/Toggle/Slider siblings below would
                  // all stack on top of each other instead of flowing vertically.
                  Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Dynamic color (Material You)", appearance.dynamicColor) { vm.setDynamicColor(it) }
                    Text(
                        "Uses your wallpaper palette on Android 12+. Turn off to choose a built-in palette below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(visible = !appearance.dynamicColor) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            Text("Built-in palettes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorPalette.entries.forEach { palette ->
                                    PaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == null && appearance.colorPalette == palette,
                                        onClick = { vm.setColorPalette(palette); vm.setActiveCustomPaletteId(null) },
                                    )
                                }
                            }
                            // Custom palettes: the create/edit dialog and per-palette
                            // selection existed (SettingsStore + AppViewModel) but had
                            // no entry point anywhere in the UI after the old Color
                            // card was merged into this Theme card -- restore it here.
                            Spacer(Modifier.height(10.dp))
                            Text("Custom palettes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            var editingPalette by remember { mutableStateOf<CustomPaletteData?>(null) }
                            var showPaletteEditor by remember { mutableStateOf(false) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                appearance.customPalettes.forEach { palette ->
                                    CustomPaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == palette.id,
                                        onClick = { vm.setActiveCustomPaletteId(palette.id) },
                                        onEdit = { editingPalette = palette; showPaletteEditor = true },
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable { editingPalette = null; showPaletteEditor = true },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "New custom palette")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("New", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (showPaletteEditor) {
                                PaletteEditorDialog(
                                    editing = editingPalette,
                                    onSave = { vm.saveCustomPalette(it); vm.setActiveCustomPaletteId(it.id); showPaletteEditor = false },
                                    onDelete = { vm.deleteCustomPalette(it); showPaletteEditor = false },
                                    onDismiss = { showPaletteEditor = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    VibrancySlider(appearance, vm)
                    Spacer(Modifier.height(10.dp))
                    ToggleRow("Pebble outline", appearance.pebbleOutline) { vm.setPebbleOutline(it) }
                  }
                }
            }

            // Weather
            SettingsCard("Weather") {
                var weatherQuery by remember { mutableStateOf("") }
                val locationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    if (granted) vm.useDeviceLocationForWeather()
                    else vm.reportError("Location permission denied — type a place instead")
                }
                appearance.weatherLabel?.let { label ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        MorphTextButton("Clear", onClick = { vm.clearWeatherLocation() })
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = weatherQuery,
                    onValueChange = { weatherQuery = it },
                    label = { Text("City or place") },
                    singleLine = true,
                    shape = FieldShape,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphTextButton(
                        "Set place",
                        modifier = Modifier.weight(1f),
                        enabled = weatherQuery.isNotBlank(),
                        onClick = { vm.setWeatherPlace(weatherQuery); weatherQuery = "" },
                    )
                    MorphButton(
                        onClick = { locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.MyLocation, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("My location", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
          }
        }
          // The search bar itself now floats fixed to the screen's bottom
          // edge (see below, outside this scrolling column) -- reserve space
          // here so scrolled content never sits behind it.
          Spacer(Modifier.height(bottomInset + 132.dp))
        }
        // Bottom-anchored search: a fixed overlay, not part of the scrolling
        // list, so it's always reachable without scrolling. Expanding it
        // (tap, or once there's a query) raises the keyboard and reveals,
        // bottom-to-top: the search bar, an AI answer tile, then the
        // scrollable list of matching settings -- closest to the bar first.
        // Sits flush above whichever is taller, the keyboard or the nav bar
        // -- windowInsetsPadding with the UNION of the two insets tracks the
        // real system-reported IME height directly (smoothly animated by the
        // system as the keyboard slides up/down), instead of manually adding
        // a fixed nav-bar clearance on top of .imePadding() gated on our own
        // "is it focused" boolean. That manual version could only ever be
        // approximately right: it flipped the moment the app *requested*
        // focus, not when the keyboard had actually finished animating in,
        // which is what read as "jumps up too high, then snaps down."
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        ) {
            AnimatedVisibility(
                visible = searchFocused || query.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                // This panel floats directly over the scrolling settings list
                // behind it -- without its own opaque backdrop, "Try asking"
                // and the gaps between suggestion pills had nothing painted
                // under them at all, so whatever settings row happened to be
                // scrolled to that same spot showed straight through and
                // collided with the panel's own text. A real Surface (solid
                // fill, glass border, shadow) the same way the search bar
                // itself and its individual result cards already work.
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha(0.98f)),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().dropShadow(RoundedCornerShape(28.dp), blurRadius = 16.dp, offsetY = 6.dp),
                ) {
                    Box {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (query.isNotBlank()) {
                                SettingsSearchResults(query, submittedQuery, vm, state, appearance, notif)
                            } else {
                                // Focused but empty: nothing to search yet, so surface a
                                // few example queries -- otherwise there's no way to
                                // discover that search can answer data questions and
                                // run commands, not just find settings by name.
                                // Tapping one is itself the deliberate "go" action, so
                                // it submits immediately rather than just filling the
                                // box and waiting for a second Enter/tap.
                                SearchSuggestions(state) { picked -> query = picked; submittedQuery = picked }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlowySearchBar(
                query,
                searchFocused,
                onQueryChange = { query = it },
                onFocusChange = { searchFocused = it },
                onSubmit = { submittedQuery = query },
            )
        }
        } // Box (wide-screen centering)
        // Same blurred scrim GarageScreen uses behind the system clock/battery
        // icons -- this content scrolls behind the status bar too (see the
        // comment above the Column's top spacer). Skipped on a folding
        // phone's compact cover screen, matching GarageScreen/LockOverlay:
        // that tiny layout doesn't draw content under the status bar at all.
        if (!isCompactCoverScreen()) StatusBarScrim()
        // Floating back-arrow + "Settings" label + simple/advanced button.
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingIcon(Icons.Filled.ArrowBack, "Back to the app", { vm.closeSettings() })
            Surface(
                onClick = { settingsScope.launch { settingsScroll.animateScrollTo(0) } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.ambientRing(RoundedCornerShape(50)).dropShadow(RoundedCornerShape(50)).frostedRim(RoundedCornerShape(50)),
            ) {
                Box {
                    Text(
                        "Settings",
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // A real segmented control (not a single button that only ever
            // names the OTHER mode) so the CURRENT mode is always obvious at a
            // glance -- the old single-label button was easy to misread as "the
            // mode you're already in" and tap the wrong way.
            Box(
                Modifier
                    .width(172.dp)
                    // Was 20.dp -- MorphSegmented's own track corner is 16.dp,
                    // so the outline ring drawn here never actually matched
                    // the pill's real corners underneath it.
                    .ambientRing(RoundedCornerShape(16.dp))
                    .dropShadow(RoundedCornerShape(16.dp))
                    .frostedRim(RoundedCornerShape(16.dp)),
            ) {
                // Match the "Settings" title pill right next to it (same glass
                // treatment, same track height) instead of the ordinary
                // button-track color/size every other MorphSegmented uses --
                // they're both floating chrome in the same row. MorphSegmented
                // has no backdrop slot of its own, so the blur is drawn here,
                // behind it, at the same corner radius it clips its own
                // background to (20.dp).
                MorphSegmented(
                    options = listOf(
                        SegmentOption("simple", "Simple", null),
                        SegmentOption("advanced", "Advanced", null),
                    ),
                    selectedKey = state.settingsMode,
                    onSelect = { vm.setSettingsMode(it) },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
                    trackHeight = 44.dp,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        // First-run coach mark pointing at the back arrow.
        if (state.showSettingsCoach) {
            val coachAlpha = remember { Animatable(0f) }
            val coachOffset = remember { Animatable(-20f) }
            LaunchedEffect(Unit) {
                launch { coachAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
                launch { coachOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            }
            Surface(
                onClick = { vm.dismissSettingsCoach() },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 60.dp, end = 12.dp)
                    .graphicsLayer {
                        alpha = coachAlpha.value
                        translationY = coachOffset.value
                    }
                    .dropShadow(RoundedCornerShape(16.dp)),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "That arrow takes you into the app when you're done here. Tap to dismiss.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        cropUri?.let { uri ->
            val target = pickTarget
            if (target != null) {
                CropScreen(
                    vin = target,
                    uriString = uri.toString(),
                    onCancel = { cropUri = null; pickTarget = null },
                    onSave = { path -> vm.setVehicleImage(target, path); cropUri = null; pickTarget = null },
                )
            }
        }
  }
}

/** One reorderable car entry in Settings; tap to expand its setup + photo. */
@Composable
private fun CarSettingsCard(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    expanded: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onToggle: () -> Unit,
    onPickPhoto: () -> Unit,
    collapsible: Boolean = true,
    showHandle: Boolean = true,
) {
    val seats = state.seatConfigs[v.vin] ?: SeatConfig()
    val cardBg by androidx.compose.animation.animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        animationSpec = tween(200),
        label = "carCardBg",
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
    ) {
        // No animateContentSize on this Column -- the body below is already
        // wrapped in its own AnimatedVisibility with expandVertically/
        // shrinkVertically, which smoothly animates that same height delta on
        // its own. A second, independently-sprung animateContentSize here on
        // top of it fought that animation every frame (each step of the inner
        // spring is itself a "content size changed" event the outer one then
        // re-animates towards), which is what made this card's collapse/
        // expand read as janky/double-animated instead of one clean motion.
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .then(if (collapsible) Modifier.clickable { onToggle() } else Modifier)
                    .then(dragHandle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showHandle) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                // A thumbnail of whatever photo is set (or the same tonal
                // gradient/car-icon fallback CarTilesHeader uses elsewhere) --
                // this card used to be pure text with no visual trace of the
                // photo it lets you change, so a new photo never actually
                // showed up anywhere until you closed Settings and looked at
                // the garage screen.
                val thumbImg = state.imageUrls[v.vin]
                CarThumb(img = thumbImg, size = 44.dp, cornerRadius = 14.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${v.model} · ${state.powertrainLabel(v)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (collapsible) {
                    MorphExpandButton(expanded = expanded, onToggle = onToggle)
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(220)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(180)),
            ) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsGroup("Powertrain") {
                        PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
                    }

                    SettingsGroup("Climate features") {
                        Text(
                            "The remote climate command controls four seat positions. Enable " +
                                "heating and/or cooling for the seats your car actually has.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SeatPositions.forEach { pos ->
                            SeatConfigRow(pos.label, pos.heat(seats), pos.cool(seats),
                                { vm.setSeatFlag(v, pos.heatKey, it) }, { vm.setSeatFlag(v, pos.coolKey, it) })
                        }
                        ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }
                    }

                    if (state.settingsMode == "advanced") SettingsGroup("Default climate start") {
                            val carPresets = state.climatePresets[v.vin].orEmpty()
                            val currentDefault = state.defaultClimatePresets[v.vin] ?: "smart"
                            Text(
                                "When the climate Start button is tapped (collapsed view), " +
                                    "the app runs your chosen preset or smart climate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            MorphSegmented(
                                options = buildList {
                                    add(SegmentOption("smart", "Smart", null))
                                    carPresets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                                },
                                selectedKey = currentDefault,
                                onSelect = { key -> vm.setDefaultClimatePreset(v.vin, key.takeIf { it != "smart" }) },
                            )
                        }

                    // Per-car palette override: existed in SettingsStore/AppViewModel
                    // (setCarPaletteId) with no UI entry point anywhere -- only shown
                    // once there's at least one custom palette to actually choose.
                    val appearance by vm.appearance.collectAsState()
                    if (state.settingsMode == "advanced" && appearance.customPalettes.isNotEmpty()) {
                        SettingsGroup("Palette override") {
                            Text(
                                "Give this car its own colour palette instead of the app-wide theme.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                appearance.customPalettes.forEach { palette ->
                                    val selected = appearance.carCustomPaletteIds[v.vin] == palette.id
                                    CustomPaletteSwatch(
                                        palette = palette,
                                        selected = selected,
                                        onClick = { vm.setCarPaletteId(v.vin, if (selected) null else palette.id) },
                                        onEdit = {},
                                    )
                                }
                            }
                        }
                    }

                    SettingsGroup("Photo") {
                        val storedImage = state.imageUrls[v.vin]
                        // A live preview instead of just "Custom photo set" as plain
                        // text -- there was no way to actually see the effect of a
                        // photo change without leaving Settings and finding this car
                        // on the garage screen.
                        if (!storedImage.isNullOrBlank()) {
                            AsyncImage(
                                model = rememberPhotoModel(storedImage),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        }
                        if (storedImage != null && storedImage.startsWith("/")) {
                            Text(
                                "Custom photo set",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            OutlinedTextField(
                                value = storedImage ?: "",
                                onValueChange = { vm.setVehicleImage(v.vin, it) },
                                label = { Text("Image URL (blank = gradient)") },
                                singleLine = true,
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MorphTextButton("Choose photo", onClick = onPickPhoto)
                            if (state.imageUrls[v.vin] != null) {
                                MorphTextButton("Clear", onClick = { vm.setVehicleImage(v.vin, "") })
                            }
                        }
                    }

                    // Identity & service tracking and pebble visibility are both
                    // power-user record-keeping, not something a first-time or
                    // casual user needs to see every time they open a car's
                    // settings -- Simple mode now only shows what actually changes
                    // which controls appear (photo, powertrain, seat/climate
                    // features), matching Default climate start/Palette override
                    // above.
                    if (state.settingsMode == "advanced") {
                        SettingsGroup("Identity & service") {
                            SelectionContainer { StatusRow("VIN", v.vin) }
                            OutlinedTextField(
                                value = state.licensePlates[v.vin] ?: "",
                                onValueChange = { vm.setLicensePlate(v.vin, it) },
                                label = { Text("License plate") },
                                singleLine = true,
                                shape = FieldShape,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = state.lastServiceMiles[v.vin]?.toString() ?: "",
                                    onValueChange = { vm.setLastServiceMiles(v.vin, it.filter(Char::isDigit).toIntOrNull()) },
                                    label = { Text("Last service (mi)") },
                                    singleLine = true,
                                    shape = FieldShape,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = state.serviceIntervalMiles[v.vin]?.toString() ?: "",
                                    onValueChange = { vm.setServiceIntervalMiles(v.vin, it.filter(Char::isDigit).toIntOrNull()) },
                                    label = { Text("Interval (mi)") },
                                    singleLine = true,
                                    shape = FieldShape,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        SettingsGroup("Sections shown") {
                            val labels = mapOf(
                                "charge" to "Charge / fuel",
                                "climate" to "Climate",
                                "location" to "Location",
                                "weather" to "Weather",
                                "trips" to "Trips",
                                "info" to "Car info",
                                "diagnostics" to "Diagnostics",
                                "ai" to "AI summary",
                            )
                            com.bloo.bluelink.data.HIDEABLE_SECTIONS
                                // The AI toggle only matters when AI is enabled for this device.
                                .filter { it != "ai" || state.aiEnabled }
                                .forEach { sec ->
                                    ToggleRow(labels[sec] ?: sec, !state.isPebbleHidden(v.vin, sec)) { show ->
                                        vm.setSectionHidden(v, sec, !show)
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

/** A titled, boxed sub-group inside the per-car settings card, for hierarchy. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

private val SearchStopwords = setOf(
    "for", "the", "of", "show", "me", "what", "whats", "is", "a", "an", "to",
    "car", "cars", "my", "s", "setting", "settings", "get", "in",
)

private class SearchEntry(val title: String, val haystack: String, val content: @Composable () -> Unit)

/** A vehicle command recognised in a free-form search query. [cmd]/[climateTarget]
 *  map directly onto [com.bloo.bluelink.data.TileCommandRunner]'s own command
 *  vocabulary, so search runs commands through the exact same path the Quick
 *  Settings tiles use. */
private class ParsedVehicleCommand(val cmd: String, val climateTarget: String = "default", val label: String)

/** Recognises a small, deliberately-conservative set of command phrasings --
 *  lock/unlock, start/stop/smart climate, start/stop charging -- rather than
 *  attempting general natural-language command parsing. Order matters:
 *  "unlock" is checked before the bare "lock" pattern so "unlock" doesn't
 *  also match as "lock".
 *
 *  Direction is encoded IN the command itself, never left for the runner to
 *  re-derive from the last-known snapshot. When the phrasing says start / stop
 *  / turn on / turn off / begin, we emit the explicit directional token
 *  (`climate_on`/`climate_off`, `charge_on`/`charge_off`) so the runner forces
 *  that direction. Before this, both "start climate" and "stop climate"
 *  collapsed to the bare `"climate"` toggle and the runner flipped against the
 *  snapshot -- so "stop the climate" while climate was already off would
 *  *start* it on the real car. The bare toggle tokens ("climate"/"charge") are
 *  reserved for genuinely ambiguous phrasing (none currently produced here). */
private fun parseVehicleCommand(query: String): ParsedVehicleCommand? {
    val q = query.lowercase()
    return when {
        Regex("\\bunlock\\b").containsMatchIn(q) -> ParsedVehicleCommand("unlock", label = "Unlocking")
        Regex("\\block\\b").containsMatchIn(q) -> ParsedVehicleCommand("lock", label = "Locking")
        Regex("smart climate|smart (ac|a/c|heat)").containsMatchIn(q) -> ParsedVehicleCommand("climate_on", "smart", "Starting smart climate for")
        Regex("stop (the )?(climate|ac|a/c|heat)|turn off (the )?(climate|ac|a/c|heat)").containsMatchIn(q) -> ParsedVehicleCommand("climate_off", label = "Stopping climate for")
        Regex("(start|turn on|run) (the )?(climate|ac|a/c|heat)").containsMatchIn(q) -> ParsedVehicleCommand("climate_on", "default", "Starting climate for")
        Regex("stop (the )?charg|turn off (the )?charg").containsMatchIn(q) -> ParsedVehicleCommand("charge_off", label = "Stopping charge for")
        Regex("(start|begin|turn on) (the )?charg|charge (it|the car) now").containsMatchIn(q) -> ParsedVehicleCommand("charge_on", label = "Starting charge for")
        else -> null
    }
}

/**
 * The Settings search bar: ONE persistent pill-shaped element the whole time
 * -- a real button that morphs into the full search field, not two separate
 * composables cross-fading in and out. Only its width fraction (~40% ->
 * 100%) and inner content (a "Search" label -> a real text field) animate;
 * the Surface/glow/shape underneath never changes identity.
 */
@Composable
private fun GlowySearchBar(
    query: String,
    focused: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val expanded = focused || query.isNotEmpty()
    LaunchedEffect(focused) { if (focused) runCatching { focusRequester.requestFocus() } }
    // Hand-ticked at ~12fps rather than Compose's animation clock -- this
    // bar is a persistent fixture of the Settings screen, so an unthrottled
    // 60fps+ blur-halo redraw for as long as that screen is open was one
    // more sustained, always-there GPU cost worth trimming along with the
    // Aurora backgrounds above.
    var glowPulse by remember { mutableFloatStateOf(0.55f) }
    LaunchedEffect(expanded) {
        val periodMs = if (expanded) 1100L else 2200L
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - start
            glowPulse = 0.55f + (1f - 0.55f) * triangleWave(elapsed, periodMs)
            delay(80)
        }
    }
    val widthFraction by animateFloatAsState(
        targetValue = if (expanded) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "searchWidth",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "searchPress",
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth(widthFraction.coerceIn(0.05f, 1f))
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
        ) {
            // A real soft halo, sized to this element's own current bounds --
            // fill+clip to the pill shape FIRST, blur LAST with an unbounded
            // edge treatment so it fades outward past the shape's own bounds
            // instead of clipping flat at the edge (which looked like a
            // square cutout instead of a glow).
            Box(
                Modifier
                    .matchParentSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary.copy(alpha = (if (expanded) 0.5f else 0.26f) * glowPulse))
                    .blur(22.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            )
            // A second, tighter, brighter "hot core" layer right at the pill's
            // own edge -- one wide soft halo alone read as a flat wash; this
            // gives the glow actual depth (a bright core fading into the wider
            // bloom) the same way a real light source does.
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(50))
                    .background(scheme.primary.copy(alpha = (if (expanded) 0.34f else 0.18f) * glowPulse))
                    .blur(8.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            )
            Surface(
                onClick = { if (!expanded) onFocusChange(true) },
                shape = RoundedCornerShape(50),
                color = scheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha(0.80f)),
                contentColor = scheme.onSurface,
                tonalElevation = if (expanded) 10.dp else 6.dp,
                border = BorderStroke(
                    if (expanded) 1.5.dp else 1.dp,
                    Brush.verticalGradient(
                        listOf(
                            scheme.primary.copy(alpha = (if (expanded) 0.65f else 0.35f) * glowPulse),
                            scheme.primary.copy(alpha = 0.05f),
                        ),
                    ),
                ),
                interactionSource = interaction,
                // A real drop shadow (offset + soft blur), not just Surface's
                // own tonal shadowElevation, which reads as barely-there on
                // most backgrounds -- this is a plain Box behind the Surface
                // with its own background+blur, exactly like GlowySearchBar's
                // halo above, just darker and offset downward.
                modifier = Modifier
                    .fillMaxWidth()
                    .dropShadow(RoundedCornerShape(50))
                    .appGlassRim(RoundedCornerShape(50)),
            ) {
                Box {
                    // Cross-fades + scales between the collapsed and expanded
                    // content instead of an instant swap -- the pill's WIDTH
                    // already animates smoothly, but the content inside used to
                    // pop in/out the moment its branch flipped, landing well
                    // before the width settled and reading as a jarring cut
                    // partway through an otherwise fluid morph.
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 60)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 60))) togetherWith
                            (fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120)))
                        },
                        label = "searchContentMorph",
                    ) { isExpanded ->
                        if (isExpanded) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = query,
                                        onValueChange = onQueryChange,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                                        cursorBrush = SolidColor(scheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                                        // No auto-collapse-on-blur here: onFocusChanged
                                        // fires immediately with isFocused = false the
                                        // instant this field first composes (before the
                                        // LaunchedEffect-driven requestFocus() below has
                                        // actually landed) -- with an empty query that
                                        // false-positive blur collapsed the bar back down
                                        // in the same beat it opened, which looked like
                                        // tapping it did nothing at all. Closing is the
                                        // explicit trailing Close button's job now.
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester),
                                        decorationBox = { inner ->
                                            if (query.isEmpty()) {
                                                Text(
                                                    "Search settings & car data",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = scheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            inner()
                                        },
                                    )
                                }
                                IconButton(onClick = { if (query.isNotEmpty()) onQueryChange("") else onFocusChange(false) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = if (query.isNotEmpty()) "Clear" else "Close",
                                    )
                                }
                            }
                        } else {
                            // Collapsed: a centered icon+label cluster, not a
                            // fill-weighted text box with nothing balancing the
                            // other side -- that left everything reading as
                            // left-aligned instead of centered in the pill.
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Search",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Example queries shown while the search bar is focused but empty --
 * without these there's no way to discover that search answers data
 * questions ("what's my odometer") and runs commands ("lock my car"), not
 * just finds settings by name.
 */
@Composable
private fun SearchSuggestions(state: UiState, onPick: (String) -> Unit) {
    val carName = state.vehicles.firstOrNull()?.name
    val examples = buildList {
        add("odometer" + (carName?.let { " for $it" } ?: ""))
        add("battery level")
        add("lock" + (carName?.let { " my $it" } ?: " my car"))
        add("haptic feedback")
        if (state.vehicles.any { state.hasBattery(it) }) add("start smart climate")
    }
    Text(
        "Try asking",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        // Floating directly over the aurora/scrolling content behind it with
        // nothing opaque underneath -- onSurfaceVariant (a deliberately muted
        // secondary-text tone) read as low-contrast there. Full-strength
        // onSurface instead.
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        examples.forEach { example ->
            Surface(
                onClick = { onPick(example) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.dropShadow(RoundedCornerShape(50), blurRadius = 8.dp, offsetY = 3.dp),
            ) {
                Box {
                    Text(
                        example,
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Live search over both app settings and per-car data/fields. Tokenises the
 * query (dropping filler words like "for"/"the"), so "odometer for xyz" finds
 * the odometer of the car named xyz, and "plate" lists every car's plate.
 */
@Composable
private fun SettingsSearchResults(
    query: String,
    submittedQuery: String,
    vm: AppViewModel,
    state: UiState,
    appearance: SettingsStore.Appearance,
    notif: SettingsStore.NotificationPrefs,
) {
    val tokens = query.lowercase().split(Regex("[^a-z0-9%]+"))
        .filter { it.isNotBlank() && it !in SearchStopwords }

    val entries = ArrayList<SearchEntry>()
    fun add(title: String, keywords: String, content: @Composable () -> Unit) {
        entries.add(SearchEntry(title, "$title $keywords".lowercase(), content))
    }

    // --- App-wide settings ---
    add("Haptic feedback", "vibration vibrate buzz sound") {
        ToggleRow("Haptic feedback", appearance.hapticsEnabled) { vm.setHapticsEnabled(it) }
    }
    add("Text & layout scale", "display size zoom bigger") {
        var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
        StepRow("Scale", "${(uiScaleDraft * 100).roundToInt()}%")
        AnimatedSlider(
            value = uiScaleDraft,
            onValueChange = { uiScaleDraft = it },
            valueRange = 0.8f..1.3f,
            steps = 4,
            onValueSettled = { uiScaleDraft = (it * 10).roundToInt() / 10f; vm.setUiScaleSoon(uiScaleDraft) },
        )
    }
    add("Colour vibrancy", "color saturation vivid material you monochrome best buy tv") {
        // Deferred-commit, same as the main Appearance card's slider — see there.
        VibrancySlider(appearance, vm)
    }
    add("Open links in app", "browser tab links") {
        ToggleRow("Open links in app", appearance.linksInApp) { vm.setLinksInApp(it) }
    }
    add("Service due alerts", "notification reminder service") {
        ToggleRow("Service due alerts", notif.service) { vm.setNotifyService(it) }
    }
    add("Door-left-open alerts", "notification door open") {
        ToggleRow("Door-left-open alerts", notif.doorOpen) { vm.setNotifyDoor(it) }
    }
    add("Car-running alerts", "notification engine climate running left on") {
        ToggleRow("Car-running alerts", notif.running) { vm.setNotifyRunning(it) }
    }

    // --- Per-car ---
    state.vehicles.forEach { v ->
        val st = state.statusFor(v)
        val plate = state.licensePlates[v.vin] ?: ""
        add("License plate · ${v.name}", "plate licence registration ${v.name} $plate") {
            OutlinedTextField(
                value = plate,
                onValueChange = { vm.setLicensePlate(v.vin, it) },
                label = { Text("License plate") },
                singleLine = true, shape = FieldShape, modifier = Modifier.fillMaxWidth(),
            )
        }
        parseOdometerMiles(v.odometer)?.let { odoInt ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", formatDistance(odoInt, appearance.unitSystem == "metric")) }
        }
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        val battRange = st?.evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value
        ((if (state.hasBattery(v)) battRange else null) ?: st?.dte?.value)?.toInt()?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", formatDistance(r, appearance.unitSystem == "metric")) }
        }
        if (state.hasBattery(v)) {
            st?.evStatus?.batteryStatus?.let { b ->
                add("Battery · ${v.name}", "battery charge soc percent ${v.name}") { StatusRow("Battery", "$b%") }
            }
            // Current-plug target if plugged in, else the configured AC home limit.
            val limit = st?.evStatus?.targetForCurrentPlug() ?: st?.evStatus?.reservChargeInfos?.level(1)
            limit?.let { l -> add("Charge limit · ${v.name}", "charge limit target ${v.name}") { StatusRow("Charge limit", "$l%") } }
        } else {
            st?.fuelLevel?.let { f ->
                add("Fuel · ${v.name}", "fuel gas tank percent ${v.name}") { StatusRow("Fuel", "$f%") }
            }
        }
        rememberRelativeTime(state.fetchedAt(v))?.let { rel ->
            add("Last refreshed · ${v.name}", "updated refreshed time ${v.name}") { StatusRow("Last refreshed", rel) }
        }
        (state.placeNames[v.vin] ?: state.locations[v.vin]?.coordString(4))?.let { loc ->
            add("Location · ${v.name}", "location where place gps ${v.name}") { StatusRow("Location", loc) }
        }
        add("Powertrain · ${v.name}", "powertrain ev gas hybrid phev ${v.name}") {
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
        }
        add("Last service · ${v.name}", "service maintenance mileage ${v.name}") {
            OutlinedTextField(
                value = state.lastServiceMiles[v.vin]?.toString() ?: "",
                onValueChange = { vm.setLastServiceMiles(v.vin, it.filter(Char::isDigit).toIntOrNull()) },
                label = { Text("Last service (mi)") },
                singleLine = true, shape = FieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Matches render FIRST (top of this composable's output), the AI answer
    // LAST -- this composable is placed above the floating search bar, so the
    // resulting stack top-to-bottom is [suggested results] [AI tile]
    // [search bar], matching the requested reading order bottom-up.
    val results = if (tokens.isEmpty()) entries else entries.filter { e -> tokens.all { it in e.haystack } }
    // Floating above busy/aurora content needs real separation -- a plain
    // default Card blends into whatever's behind it. Elevated container +
    // actual shadow (not just tonal elevation) so results clearly pop.
    val resultCardShape = RoundedCornerShape(16.dp)
    val resultCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    // These float over busy/aurora content the same way the search bar and
    // "Try asking" panel above them do, but were left on plain tonal-
    // elevation Cards -- the one inconsistency in an otherwise unified
    // floating-chrome look within this exact panel.
    val resultCardModifier = Modifier.fillMaxWidth().dropShadow(resultCardShape, blurRadius = 10.dp, offsetY = 3.dp).frostedRim(resultCardShape)
    if (results.isEmpty()) {
        Card(resultCardModifier, shape = resultCardShape, colors = resultCardColors) {
            Text(
                "No matches for “$query”",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        results.forEach { e ->
            Card(resultCardModifier, shape = resultCardShape, colors = resultCardColors) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    e.content()
                }
            }
        }
    }

    // A recognised command ("lock my Ioniq", "start smart climate", "stop
    // charging") actually runs -- reuses TileCommandRunner, the same
    // execution path the Quick Settings tiles use, so this isn't a separate,
    // untested way of sending vehicle commands. If the query doesn't name a
    // specific car, this falls back to a single car (unambiguous) or asks
    // the user to be more specific (multiple cars, none named).
    //
    // Gated on submittedQuery, NOT the live query -- this actually sends a
    // command to the car, so it must only run once the user has deliberately
    // submitted (Enter/search key, or a suggestion tap), never mid-typing off
    // a debounce timer. Typing "lock my car" used to run the lock the moment
    // the debounce elapsed, whether or not that's what the user meant to do.
    val command = remember(submittedQuery) { if (submittedQuery.isBlank()) null else parseVehicleCommand(submittedQuery) }
    if (command != null) {
        val ctx = LocalContext.current
        // Whole-word, longest-match car resolution -- NOT a bare substring test.
        // A plain `name in query` lets "Ioniq" match inside "lock my Ioniq 5",
        // so a command meant for the "Ioniq 5" would be sent to the "Ioniq"
        // (list-order-first). Instead require the name to appear as a bounded
        // token sequence, and when several names match prefer the longest. If
        // several still match at that longest length the query is genuinely
        // ambiguous, so refuse to dispatch and ask which car (targetVehicle
        // stays null → the "Which car?" branch below).
        val q = submittedQuery.lowercase()
        val nameMatches = state.vehicles.filter { v ->
            v.name.isNotBlank() &&
                Regex("\\b" + Regex.escape(v.name.lowercase()) + "\\b").containsMatchIn(q)
        }
        val longestMatchLen = nameMatches.maxOfOrNull { it.name.length }
        val namedVehicle = nameMatches.filter { it.name.length == longestMatchLen }.singleOrNull()
        // Only fall back to "the one car" when NO name matched at all; if a name
        // matched but was ambiguous, do not silently pick a car.
        val targetVehicle = namedVehicle ?: if (nameMatches.isEmpty()) state.vehicles.singleOrNull() else null
        var actionResult by remember(submittedQuery) { mutableStateOf<String?>(null) }
        var actionRunning by remember(submittedQuery) { mutableStateOf(false) }
        LaunchedEffect(submittedQuery) {
            if (targetVehicle != null) {
                actionRunning = true
                val result = runCatching { TileCommandRunner.run(ctx, targetVehicle.vin, command.cmd, command.climateTarget) }.getOrNull()
                actionResult = result?.message ?: "Command failed"
                actionRunning = false
                vm.refreshStatus(targetVehicle)
            }
        }
        Card(
            resultCardModifier,
            shape = resultCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Action", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    when {
                        targetVehicle == null -> {
                            val example = state.vehicles.firstOrNull()?.name ?: "car"
                            "Which car? Mention its name, e.g. “${command.label} my $example”."
                        }
                        actionRunning -> "${command.label} ${targetVehicle.name}…"
                        actionResult != null -> actionResult!!
                        else -> "${command.label} ${targetVehicle.name}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    // On-device AI reply (when enabled): answer the question in natural
    // language -- a fallback/complement for questions with no structured
    // match above, or a plain-language gloss when there is one.
    //
    // Gated on submittedQuery, not the live query -- this fires a real AI
    // request (network/compute cost, and it used to visibly show "Thinking…"
    // while the user was still mid-word), so it must wait for a deliberate
    // submit rather than firing on every keystroke's debounce.
    if (state.aiEnabled) {
        LaunchedEffect(submittedQuery) {
            if (submittedQuery.isNotBlank()) {
                vm.askAi(submittedQuery)
            } else {
                vm.clearAiReply()
            }
        }
        val thinking = "search" in state.aiBusy
        val reply = state.aiSearchReply
        if (thinking || reply != null) {
            Card(
                resultCardModifier,
                shape = resultCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI answer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (reply != null) {
                        Text(reply, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val TileActions = listOf(
    Triple("doors", "Lock / unlock", Icons.Filled.Lock),
    Triple("climate", "Climate", Icons.Filled.Thermostat),
    Triple("charge", "Charge", Icons.Filled.Bolt),
    Triple("open", "Open", Icons.Filled.DirectionsCar),
)

/** Label for a tile action key (falls back to the key). */
private fun tileActionLabel(cmd: String): String =
    TileActions.firstOrNull { it.first == cmd }?.second ?: cmd

/** One option in a [MorphSegmented] control; re-exported from :uicommon. */
typealias SegmentOption = com.bloo.uicommon.SegmentOption

/**
 * A full-width segmented selector built from the app's button vocabulary: a
 * tonal track whose active segment fills with the primary accent and morphs to a
 * rounded-square, the rest staying pill-calm. Thin wrapper over the shared
 * :uicommon [com.bloo.uicommon.MorphSegmented], supplying the phone's Material 3
 * colours, label typography and haptics.
 */
@Composable
fun MorphSegmented(
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    trackHeight: Dp? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    com.bloo.uicommon.MorphSegmented(
        options = options,
        selectedKey = selectedKey,
        onSelect = onSelect,
        containerColor = containerColor ?: buttonContainer(),
        indicatorColor = scheme.primary,
        selectedTextColor = scheme.onPrimary,
        unselectedTextColor = scheme.onSurfaceVariant,
        textStyle = MaterialTheme.typography.labelLarge,
        onTick = { haptics?.tick() },
        modifier = modifier,
        trackHeight = trackHeight ?: (if (options.any { it.icon != null }) 48.dp else 44.dp),
        // Every other interactive surface (Pebble, floating pills, dialogs)
        // got a hairline rim once real glass blur stopped giving flat
        // surfaces a second depth cue; this control was the one left out.
        borderColor = scheme.outline.copy(alpha = 0.18f),
    )
}


/** A car's powertrain (Gas/Hybrid/PHEV/EV) is a fixed 4-way choice between
 *  equal alternatives — one shared MorphSegmented instead of the MorphChip
 *  row this was duplicated as in both CarSettingsCard and its settings-search
 *  mirror. */
@Composable
private fun PowertrainPicker(current: com.bloo.bluelink.data.Powertrain, onSelect: (com.bloo.bluelink.data.Powertrain) -> Unit) {
    // An icon per option (Gas/Hybrid/PHEV/EV) instead of text-only segments --
    // a quick visual "shape" for each choice, not just a label to read.
    MorphSegmented(
        options = listOf(
            SegmentOption(com.bloo.bluelink.data.Powertrain.GAS.name, "Gas", Icons.Filled.LocalGasStation),
            SegmentOption(com.bloo.bluelink.data.Powertrain.HYBRID.name, "Hybrid", Icons.Filled.Bolt),
            SegmentOption(com.bloo.bluelink.data.Powertrain.PHEV.name, "PHEV", Icons.Filled.Power),
            SegmentOption(com.bloo.bluelink.data.Powertrain.EV.name, "EV", Icons.Filled.FlashOn),
        ),
        selectedKey = current.name,
        onSelect = { key -> onSelect(com.bloo.bluelink.data.Powertrain.valueOf(key)) },
    )
}

/**
 * A labelled [MorphSegmented]: a small caption above a full-width segmented
 * control. The expressive replacement for a switch when the setting is really a
 * choice between two equal alternatives (°C/°F, in-app/browser) rather than on/off.
 */
@Composable
fun SettingsSegmentedRow(
    label: String,
    options: List<SegmentOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        MorphSegmented(options = options, selectedKey = selectedKey, onSelect = onSelect)
    }
}

/** Expressive per-car header: a tonal thumbnail/gradient bubble, name, and tile count. */
@Composable
private fun CarTilesHeader(name: String, img: String?, assignedCount: Int, totalTiles: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CarThumb(img = img, size = 44.dp, cornerRadius = 16.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (assignedCount == 0) "No tiles yet" else "$assignedCount of $totalTiles tiles used",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            // A slim capacity bar reads the per-car tile budget at a glance,
            // instead of just a count with no sense of how much room is left.
            Spacer(Modifier.height(6.dp))
            val fill by animateFloatAsState(
                targetValue = if (totalTiles > 0) assignedCount / totalTiles.toFloat() else 0f,
                animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                label = "tileCapacityFill",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(scheme.primary),
                )
            }
        }
    }
}

/** Shared muted hint line for the tile manager's empty/full states. */
@Composable
private fun TileEmptyHint(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Per-car Quick Settings tile manager with live previews. Each car gets its
 *  own tonal card (mirroring CarSettingsCard's per-car container elsewhere in
 *  Settings) so two cars' tile groups never read as one continuous list. */
@Composable
private fun QuickTilesManager(state: UiState, vm: AppViewModel) {
    if (state.vehicles.isEmpty()) {
        Text(
            "Add a car to set up quick tiles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val count = com.bloo.bluelink.data.TILE_COUNT
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.vehicles.forEach { car ->
            val assigned = (0 until count).filter { state.tileConfigs.getOrNull(it)?.first == car.vin }
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
            ) {
                Column(Modifier.padding(12.dp)) {
                    CarTilesHeader(
                        name = car.name,
                        img = state.imageUrls[car.vin],
                        assignedCount = assigned.size,
                        totalTiles = count,
                    )
                    Spacer(Modifier.height(10.dp))
                    assigned.forEach { idx ->
                        key(idx) { QuickTileCard(idx, car.vin, state, vm) }
                    }
                    val free = (0 until count).firstOrNull { state.tileConfigs.getOrNull(it) == null }
                    when {
                        free != null -> AddTilePill(
                            label = if (assigned.isEmpty()) "Add a quick tile" else "Add another",
                            onClick = { vm.setTileAssignment(free, car.vin, if (assigned.isEmpty()) "doors" else "climate") },
                        )
                        assigned.isEmpty() -> TileEmptyHint("All $count tiles are in use — remove one to add another.")
                    }
                }
            }
        }
    }
}

/**
 * Prompt the OS to add this configured tile straight to the Quick Settings shade.
 * The system dialog previews [label] + the action's icon before adding, so the
 * tile's name/properties are shown up front. On API < 33 (no add-tile API) we
 * guide the user to add it manually instead.
 */
private fun addTileToQuickSettings(context: Context, index: Int, cmd: String, label: String, unlocked: Boolean) {
    val iconRes = com.bloo.bluelink.tiles.BlooTileService.iconResFor(cmd, unlocked)
    val requested = com.bloo.bluelink.tiles.BlooTileService.requestAddToQuickSettings(
        context, index, label, iconRes,
    ) { result ->
        val msg = when (result) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "“$label” added to Quick Settings"
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "“$label” is already in Quick Settings"
            else -> null
        }
        msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    if (!requested) {
        Toast.makeText(
            context,
            "Open Quick Settings, tap edit, and add “$label” from the tile list.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun tileSummary(cmd: String, climateTarget: String, presetName: String?): String = when (cmd) {
    "doors" -> "Lock / unlock"
    "climate" -> when (climateTarget) {
        "smart" -> "Climate · Smart"
        "default" -> "Climate · Basic"
        else -> "Climate · ${presetName ?: "Preset"}"
    }
    "charge" -> "Start / stop charge"
    "open" -> "Opens the app"
    else -> cmd
}

/**
 * One configured tile, built on the exact same [PebbleShell] every car pebble
 * uses (see [UpdateAvailableTile] for the other non-car-scoped caller) instead
 * of a bespoke static-shape split row -- its collapsed header IS the live
 * preview (icon, name, current state), and its [PebbleHeaderAction] doubles as
 * the actual "Add" button so the common case (configure once, add it) never
 * needs to expand at all. Expanding is only for changing the action, custom
 * name, what climate runs, or removing the tile.
 */
@Composable
private fun QuickTileCard(index: Int, vin: String, state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    val cmd = state.tileConfigs.getOrNull(index)?.second ?: "doors"
    val customName = state.tileLabels.getOrNull(index)?.takeIf { it.isNotBlank() }
    val presets = state.climatePresets[vin].orEmpty()
    val target = state.tileClimateTargets.getOrNull(index) ?: "default"
    val presetName = presets.firstOrNull { it.id == target }?.name
    var expanded by remember { mutableStateOf(false) }

    // Live car state so the preview matches what the tile will actually show.
    val status = state.vehicles.firstOrNull { it.vin == vin }?.let { state.statusFor(it) }
    val active = when (cmd) {
        "doors" -> status?.doorLock == false
        "climate" -> status?.airCtrlOn == true
        "charge" -> status?.evStatus?.batteryCharge == true
        else -> false
    }
    val liveLabel = when (cmd) {
        "doors" -> status?.doorLock?.let { if (it) "Locked" else "Unlocked" }
        "climate" -> if (status?.airCtrlOn == true) "On" else null
        "charge" -> if (status?.evStatus?.batteryCharge == true) "Charging" else null
        else -> null
    }
    val headerIcon = when (cmd) {
        "doors" -> if (status?.doorLock == false) Icons.Filled.LockOpen else Icons.Filled.Lock
        "climate" -> Icons.Filled.Thermostat
        "charge" -> Icons.Filled.Bolt
        else -> Icons.Filled.DirectionsCar
    }
    val title = if (cmd == "open") "Open" else (customName ?: tileActionLabel(cmd))

    PebbleShell(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        icon = headerIcon,
        title = title,
        vm = vm,
        summary = liveLabel ?: tileSummary(cmd, target, presetName),
        headerAction = PebbleHeaderAction(
            label = "Add",
            icon = Icons.Filled.Add,
            active = active,
            onClick = { addTileToQuickSettings(context, index, cmd, title, unlocked = status?.doorLock == false) },
        ),
    ) {
        Text("Action", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        MorphSegmented(
            options = TileActions.map { (key, label, icon) ->
                SegmentOption(key, if (key == "doors") "Lock" else label, icon)
            },
            selectedKey = cmd,
            onSelect = { key -> vm.setTileAssignment(index, vin, key) },
        )

        if (cmd != "open") {
            Spacer(Modifier.height(10.dp))
            var name by remember(state.tileLabels.getOrNull(index)) {
                mutableStateOf(customName.orEmpty())
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; vm.setTileLabel(index, it) },
                label = { Text("Custom name (optional)") },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (cmd == "climate") {
            Spacer(Modifier.height(10.dp))
            Text("Runs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            MorphSegmented(
                options = buildList {
                    add(SegmentOption("default", "Basic", null))
                    add(SegmentOption("smart", "Smart", null))
                    presets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                },
                selectedKey = target,
                onSelect = { vm.setTileClimateTarget(index, it) },
            )
        }

        Spacer(Modifier.height(4.dp))
        MorphButton(
            onClick = { vm.setTileAssignment(index, null, null) },
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Remove tile", fontWeight = FontWeight.SemiBold)
        }
    }
}

/** An outlined "add" pill that morphs like the app's other buttons. */
@Composable
private fun AddTilePill(label: String, onClick: () -> Unit) {
    MorphButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .animateContentSize(spring(dampingRatio = SoftDamping, stiffness = AdvancedModeStiffness)),
        ) {
            // Role.Heading lets TalkBack's "headings" navigation control jump
            // section-to-section across Settings' ~15 SettingsCards instead of
            // linearly swiping through every row of every card to get anywhere
            // -- there was no heading structure anywhere in the phone app.
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SecretRow(label: String, value: String) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (show) value else "•".repeat(value.length.coerceIn(4, 10)),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        MorphTextButton(if (show) "Hide" else "Show", onClick = { show = !show })
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (selected || pressed) 14.dp else 24.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "choiceCorner",
    )
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else buttonContainer(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "choiceBg",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = { haptics?.click(); onSelect() },
        shape = RoundedCornerShape(corner),
        color = bg,
        contentColor = fg,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// --- Small reusable pieces ------------------------------------------------

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
        )
        // Was a hand-rolled AnimatedContent + WiggleText -- uicommon's shared
        // AnimatedValue already implements this (used elsewhere in this file
        // and now watch's ChargeRing); this is that same value-cell pattern
        // duplicated once per pebble-row composable instead of centralized.
        // Colour pinned to full-strength onSurface rather than inherited --
        // Pebble's Card sets its content color from containerColor (usually
        // surfaceVariant), so an uncoloured value here rendered at
        // onSurfaceVariant strength, barely distinguishable from the dimmed
        // label right next to it despite being the actually-important half
        // of the row.
        com.bloo.uicommon.AnimatedValue(
            value = value,
            style = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface),
        )
    }
}

/** A small bold group heading used inside the Car-info pebble. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = LocalContentColor.current.copy(alpha = 0.85f),
    )
}

@Composable
private fun StepRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        // Roll the value when it changes (e.g. dragging a slider).
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stepValue",
        ) { v -> Text(v, fontWeight = FontWeight.Medium, color = valueColor) }
    }
}

/**
 * The app's one toggle control for boolean settings. Ground-up redesign away
 * from a plain label next to a stock Material [Switch] -- a custom pill
 * track+thumb (spring-timed like [MorphButton]) stands in for the Switch so
 * this shares that pill-morph vocabulary too instead of being the one
 * default-Material holdout in an otherwise fully custom UI.
 *
 * An earlier version of this also washed the whole row toward the primary
 * color and rounded its corners when checked, matching how MorphButton fills
 * solid on activation -- dropped after feedback that stacked next to a
 * card's own background it read as a second nested box rather than a
 * highlight, especially over the AI toggle's already-boxed row.
 */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            // toggleable (not clickable) gives this its own Role.Switch + checked
            // semantics node -- the track below clears its own (identical) node
            // so TalkBack sees ONE correctly-announced toggle for the row
            // instead of two adjacent focus stops (a generic "double tap to
            // activate" for the row, then the real on/off announcement for the
            // track a swipe later).
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
            ) {
                val next = !checked
                if (next) haptics?.toggleOn() else haptics?.toggleOff()
                onChange(next)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
        )
        Spacer(Modifier.width(10.dp))
        MorphToggleTrack(checked)
    }
}

/**
 * A pill track + circular thumb, spring-timed like [MorphButton] instead of
 * the stock Material [Switch]. Purely visual -- [ToggleRow]'s own toggleable()
 * modifier owns the real click target and semantics, so this clears its own.
 */
@Composable
private fun MorphToggleTrack(checked: Boolean) {
    val trackColor by androidx.compose.animation.animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else buttonContainer(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleTrackBg",
    )
    val thumbColor by androidx.compose.animation.animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbFg",
    )
    val trackWidth = 44.dp
    val trackHeight = 26.dp
    val inset = 3.dp
    val thumbSize by animateDpAsState(
        if (checked) 20.dp else 16.dp,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbSize",
    )
    val thumbOffset by animateDpAsState(
        if (checked) trackWidth - thumbSize - inset else inset,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "toggleThumbOffset",
    )
    Box(
        Modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)), RoundedCornerShape(50))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/** One seat's heat + cool capability toggles, shown as two compact filter chips. */
@Composable
private fun SeatConfigRow(
    label: String,
    heat: Boolean,
    cool: Boolean,
    onHeat: (Boolean) -> Unit,
    onCool: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        MorphChip(selected = heat, onClick = { onHeat(!heat) }, label = "Heat")
        Spacer(Modifier.width(8.dp))
        MorphChip(selected = cool, onClick = { onCool(!cool) }, label = "Cool")
    }
}

@Composable
private fun CommandButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    MorphButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The synced-devices registry shown in the "Backup & sync" card: a
 * drag-to-reorder list (same gesture as the car-order list in Settings) where
 * the TOP device is the primary — the source of truth other devices adopt.
 * Dragging a device to the top makes it primary. Each row shows a drag handle, a
 * device icon (★ on the primary), its name (with a "This device" marker for
 * self + a rename affordance), model, and how long ago it last synced. Renders
 * nothing until the first sync populates the registry.
 */
@Composable
private fun SyncDevicesSection(state: UiState, vm: AppViewModel) {
    val devices = state.syncDevices
    if (devices.isEmpty()) return
    var renaming by remember { mutableStateOf(false) }

    // Order the list so the primary is on top (that's the invariant the drag
    // gesture maintains); everyone else falls in by most-recently-seen. Dragging
    // a device to the top sets it primary, after which this same sort keeps it
    // there — so the visual order and the "primary" concept stay in lockstep.
    val ordered = remember(devices, state.syncPrimaryId) {
        devices.sortedWith(
            compareByDescending<com.bloo.bluelink.data.SyncMerge.SyncDevice> { it.id == state.syncPrimaryId }
                .thenByDescending { it.lastSeenMs },
        )
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Synced devices",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        "Drag to reorder — the top device is primary, the source of truth the others follow.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    ReorderColumn(
        items = ordered,
        keyOf = { it.id },
        // Dropped in a new order → the new TOP device becomes primary. setPrimaryDevice
        // persists it + triggers a sync so every device converges on the choice.
        onReorder = { reordered -> reordered.firstOrNull()?.let { vm.setPrimaryDevice(it.id) } },
        spacing = 8.dp,
    ) { device, dragHandle, dragging ->
        SyncDeviceRow(
            device = device,
            isSelf = device.id == state.thisDeviceId,
            isPrimary = device.id == state.syncPrimaryId,
            dragging = dragging,
            dragHandle = dragHandle,
            onRename = { renaming = true },
        )
    }

    if (renaming) {
        var draft by remember { mutableStateOf(state.syncDeviceName) }
        // Standardized on the shared GlassAlertDialog shell (was the legacy
        // BlooDialog, now removed). Stacked full-width buttons, no leading icon.
        GlassAlertDialog(
            onDismissRequest = { renaming = false },
            icon = Icons.Filled.Smartphone,
            title = "Rename this device",
            text = {
                Text(
                    "Shown in the devices list on all your synced devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            buttons = {
                MorphButton(
                    onClick = {
                        if (draft.isNotBlank()) vm.renameThisDevice(draft)
                        renaming = false
                    },
                    active = true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                MorphTextButton("Cancel", onClick = { renaming = false }, modifier = Modifier.fillMaxWidth())
            },
        )
    }
}

/** One row in the drag-to-reorder [SyncDevicesSection]: a frosted card with a
 *  drag handle, a device icon (★ when primary), the device name (+ a "This
 *  device" chip and a rename button for self), model, and last-seen. Styled to
 *  match the card language of the rest of Settings; lifts slightly while dragged. */
@Composable
private fun SyncDeviceRow(
    device: com.bloo.bluelink.data.SyncMerge.SyncDevice,
    isSelf: Boolean,
    isPrimary: Boolean,
    dragging: Boolean,
    dragHandle: Modifier,
    onRename: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val container =
        if (isPrimary) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha(0.9f))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .then(if (dragging) Modifier.dropShadow(shape, blurRadius = 14.dp, offsetY = 4.dp) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle — the grab affordance, same idiom as the car-order list.
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = dragHandle.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Icon(
            if (isPrimary) Icons.Filled.Star else Icons.Filled.Smartphone,
            contentDescription = if (isPrimary) "Primary device" else null,
            tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name.ifBlank { "Unnamed device" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPrimary || isSelf) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSelf) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "This device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val seen = com.bloo.bluelink.data.relativeLabel(device.lastSeenMs)
            val sub = buildString {
                if (isPrimary) append("Primary")
                val model = device.model.takeIf { it.isNotBlank() }
                if (isPrimary && model != null) append(" · ")
                if (model != null) append(model)
                if (seen.isNotBlank()) { if (isNotEmpty()) append(" · "); append(seen) }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelf) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename this device", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * The Google Drive sync setup dialog, shared between onboarding and the
 * Settings "Backup & sync" card so both look and behave identically (they
 * used to be two separately hand-rolled dialogs -- one BlooDialog, one plain
 * AlertDialog with an awkward confirmButton/dismissButton split -- that had
 * drifted out of sync with each other). Two tappable choice cards instead of
 * three same-weight text buttons, so "start fresh" vs. "join an existing
 * sync" reads as an actual decision rather than an arbitrary button order.
 */
@Composable
private fun DriveSyncSetupDialog(
    onDismissRequest: () -> Unit,
    onSaveToDrive: () -> Unit,
    onOpenFromDrive: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Routed through the same GlassAlertDialog shell used elsewhere (icon+bold
    // title, informative content, stacked full-width buttons) rather than a
    // one-off hand-rolled AlertDialog.
    GlassAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = Icons.Filled.Cloud,
        title = "Google Drive sync",
        text = {
            Text(
                "Keep your settings in sync across devices with one file in Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            DriveSyncChoiceRow(
                icon = Icons.Filled.CreateNewFolder,
                title = "Save to Drive",
                subtitle = "Start fresh — create a new file with this device's settings.",
                onClick = onSaveToDrive,
            )
            DriveSyncChoiceRow(
                icon = Icons.Filled.FileOpen,
                title = "Open from Drive",
                subtitle = "Join an existing sync file set up on another device.",
                onClick = onOpenFromDrive,
            )
        },
        buttons = {
            MorphTextButton("Cancel", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
private fun DriveSyncChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    // The app's standard button component (MorphButton), not a bespoke
    // Surface row -- so this dialog's actions look and feel like every other
    // button in the app instead of a one-off.
    MorphButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// BlooDialog (the legacy second dialog shell) was removed here — every dialog now
// routes through the single GlassAlertDialog shell above. Its one caller
// (rename-device) was migrated in the same change.
