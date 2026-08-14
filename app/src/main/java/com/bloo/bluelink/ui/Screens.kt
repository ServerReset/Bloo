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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.snap
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
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
import androidx.compose.runtime.Stable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.DEFAULT_AC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.DEFAULT_DC_CHARGE_LIMIT_PCT
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
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.MapTiles
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
import com.bloo.bluelink.data.formatSpeedMph
import com.bloo.bluelink.data.formatTripDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.serviceDue
import com.bloo.bluelink.data.nextServiceMiles
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.bluelink.data.smartClimateIsCooling
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE
import com.bloo.bluelink.data.climateChunks
import com.bloo.bluelink.data.isPluggedOrCharging
import com.bloo.uicommon.topFadeScrim
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
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

    CompositionLocalProvider(
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
                        if (appearance.auroraBackground) AuroraBackground(Modifier.matchParentSize(), appearance, refreshing = state.refreshing)
                        GarageScreen(state, vm)
                    }
                }
                // The full phone Settings (search + keyboard, photo pickers, crop,
                // drag-reorder lists, sign-out) is unusable crammed onto a ~1-inch
                // flip cover — it used to render there verbatim. On the cover, show a
                // compact "manage on your phone" card instead; the real settings are
                // one unfold away. (The cover's gear button is also removed, so this
                // is a belt-and-suspenders fallback for the back-stack landing here.)
                Screen.Settings ->
                    if (isCompactCoverScreen()) CoverManageOnPhoneCard(vm)
                    else SettingsScreen(vm)
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
        // is always there -- that is how you find a setting.
        if (searchable && !state.locked && (appearance.showSearch || target == Screen.Settings)) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                SearchLayer(
                    vm = vm,
                    state = state,
                    appearance = appearance,
                    notif = notifPrefs,
                    onSettings = target == Screen.Settings && !cover,
                    compact = cover,
                )
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
                    MorphButton(
                        onClick = ::goBack,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        Text("Back", style = MaterialTheme.typography.titleMedium)
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
        // AnimatedContent, not a bare if/else -- this used to snap straight
        // from the "Set up Drive sync" button to the "enabled" row the instant
        // the dialog finished, the one un-animated content swap left in a step
        // whose sibling cards (notifications, fingerprint) at least keep the
        // same MorphButton in place and only recolor it.
        AnimatedContent(targetState = syncEnabled, label = "onboardingSyncDone") { enabled ->
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
                    Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Set up Drive sync", fontWeight = FontWeight.SemiBold)
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
/**
 * A single tinted "tip" card: a rounded [surfaceContainerHigh] surface holding a
 * primary-tinted icon beside a bold title and a muted one-line body. The onboarding
 * intro and crash-course pages each render a list of these; the card chrome was
 * copied verbatim between them, so it lives here and each page just maps its own
 * `Triple(icon, title, body)` list onto it.
 */
@Composable
private fun OnboardingTipCard(icon: ImageVector, title: String, body: String) {
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
private fun WizardPageHeader(eyebrow: String, title: String, body: String) {
    val scheme = MaterialTheme.colorScheme
    Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Bold)
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = scheme.onSurface)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
}

@Composable
private fun OnboardingCarPage(
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
        Surface(
            onClick = { extrasHaptics?.click(); vm.setSeatFlag(vehicle, "sw", !sc.steeringWheel) },
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
        OnboardingTipCard(icon, title, body)
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
                    MorphButton(
                        onClick = ::goBack,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        Text("Back", style = MaterialTheme.typography.titleMedium)
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
    WizardPageHeader(
        "Powertrain",
        "What powers the ${vehicle.name}?",
        "Bloo uses this to show the right status tiles: battery percentage for EVs, " +
            "fuel level for gas, or both for plug-in hybrids.",
    )
    val current = state.powertrainOf(vehicle)
    val haptics = LocalHaptics.current
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
                // This wizard's one selection row with no haptic feedback --
                // every sibling in the flow (Back/Next, the seat/steering
                // toggles, WizardToggleChip) has one now.
                onClick = { haptics?.click(); vm.setPowertrain(vehicle, pt) },
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
    val haptics = LocalHaptics.current
    Surface(
        onClick = { haptics?.click(); onClick() },
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
private fun WizardFeatureToggle(
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

internal val FieldShape = RoundedCornerShape(18.dp)

/**
 * The app's borderless, surface-filled text-field colours: a [scheme.surface] fill in
 * every state and transparent borders, so a field reads as a filled pill rather than an
 * outlined box. Used by the login form and the rename-device dialog, which sit ~9,000
 * lines apart and had each hand-built the identical `colors()` call. `disabledContainer`
 * is kept surface-coloured (neither caller ever disables its field, so it never renders,
 * but this keeps the login form byte-identical to before). */
@Composable
private fun borderlessFieldColors(): androidx.compose.material3.TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
    )
}

// A synced device not seen this long is flagged as possibly on a different Drive
// file (the two-files trap) in the sync settings. 2 days is well past any normal
// gap for a device in active use, so it doesn't false-alarm on a phone you simply
// didn't open yesterday.
private const val STALE_DEVICE_MS = 2L * 24 * 60 * 60 * 1000

/**
 * Sign-in form supporting every brand (US Hyundai/Genesis/Kia plus the three
 * Canada brands) from one screen. All fields
 * (email/password/pin/brand) are local `mutableStateOf` -- nothing is
 * persisted until [onLogin] fires, so switching brands mid-entry doesn't
 * lose the typed email/password. Selecting a brand via [MorphSegmented]
 * only changes copy/labels/validation shape shown here; brand-specific
 * strings (subtitle, email label, forgot-password URL, sign-in button
 * label) are recomputed from `brand` on every recomposition and each swap
 * cross-fades via [AnimatedContent] rather than snapping instantly.
 * The PIN field is only shown for brands that need one (`brand.requiresPin`
 * -- every brand except Kia US); Kia and Canada instead get a one-time-
 * passcode dialog elsewhere ([KiaOtpDialog]/[CanadaOtpDialog]) after
 * submitting -- Canada still shows the PIN field first since its commands
 * are PIN-gated even though sign-in itself goes through OTP. `formVisible`
 * flips true one
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
    // Region gates which 3 brands the segmented picker below offers, rather
    // than cramming all 6 US+Canada entries into one row -- Hyundai/Genesis/
    // Kia Canada run on a completely different backend (see CanadaApi) with
    // its own sign-in shape, so switching region also resets `brand` to that
    // region's first entry.
    var region by remember { mutableStateOf("US") }
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
        Brand.HYUNDAI_CA -> "A better Bluelink · Canada"
        Brand.GENESIS_CA -> "A better Genesis Connect · Canada"
        Brand.KIA_CA -> "A better Kia Connect · Canada"
        Brand.HYUNDAI_EU -> "A better Bluelink · Europe"
    }
    val emailLabel = when (brand) {
        Brand.HYUNDAI, Brand.HYUNDAI_CA, Brand.HYUNDAI_EU -> "Bluelink email"
        Brand.GENESIS, Brand.GENESIS_CA -> "Genesis account email"
        Brand.KIA, Brand.KIA_CA -> "Kia Connect email"
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
                    val fieldColors = borderlessFieldColors()

                    Text(
                        "Region",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    MorphSegmented(
                        options = listOf(
                            SegmentOption("US", "United States", null),
                            SegmentOption("CA", "Canada", null),
                            SegmentOption("EU", "Europe", null),
                        ),
                        selectedKey = region,
                        onSelect = { key ->
                            region = key
                            // Reset to the region's first (only, for EU) brand,
                            // since each region's backend/sign-in shape differs.
                            brand = Brand.brandsForRegion(key).first()
                        },
                    )

                    Text(
                        "Sign in with",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurface,
                    )
                    // Brand.brandsForRegion, shared with the watch's own picker --
                    // which was a hand-written copy of this list, and had silently
                    // stopped at the three US brands.
                    val brandOptions = Brand.brandsForRegion(region)
                    MorphSegmented(
                        options = brandOptions.map { b ->
                            SegmentOption(b.name, Brand.shortLabel(b), null)
                        },
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

                    // PIN — every brand except Kia US (Kia's own commands need
                    // no PIN at all; Canada still needs one for CanadaApi.pinAuth
                    // even though its sign-in also goes through OTP).
                    AnimatedVisibility(
                        visible = brand.requiresPin,
                        enter = collapseEnter(Alignment.Bottom),
                        exit = collapseExit(Alignment.Bottom),
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
                                    Brand.HYUNDAI_CA -> "https://www.hyundaicanada.com/en/owners-section"
                                    Brand.GENESIS_CA -> "https://www.genesis.com/ca/en/support/contact-us.html"
                                    Brand.KIA_CA -> "https://www.kia.ca/en/owners"
                                    Brand.HYUNDAI_EU -> "https://www.hyundai.com/eu/en/owners.html"
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
                OtpCodeField(code) { code = it }
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

/**
 * Canada sign-in verification: unlike [KiaOtpDialog] there's no destination to
 * pick (email only), and the code is already sent by the time this shows
 * (see AppViewModel.loginCanada), so it goes straight to code entry.
 */
@Composable
private fun CanadaOtpDialog(otp: CanadaOtpUi, loading: Boolean, vm: AppViewModel) {
    var code by remember(otp.challenge) { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = { if (!loading) vm.canadaCancelOtp() },
        icon = Icons.Filled.Lock,
        title = "Enter your code",
        text = {
            Text(
                "We emailed a one-time code" +
                    (otp.challenge.email?.let { " to $it" } ?: "") + " to verify this sign-in.",
            )
            OtpCodeField(code) { code = it }
        },
        buttons = {
            MorphButton(
                onClick = { vm.canadaVerifyOtp(code) },
                enabled = !loading && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) LoadingIndicator() else Text("Verify", fontWeight = FontWeight.SemiBold)
            }
            MorphTextButton("Cancel", vm::canadaCancelOtp, enabled = !loading, modifier = Modifier.fillMaxWidth())
        },
    )
}

/**
 * The one-time-code entry field shared by [KiaOtpDialog] and [CanadaOtpDialog]. Both
 * hoist their own `code` state (the Verify button reads it), so this takes the value
 * and its setter rather than owning the buffer -- everything else (the "Code" label,
 * single line, number keyboard, [FieldShape] and full width) is identical.
 */
@Composable
private fun OtpCodeField(code: String, onCodeChange: (String) -> Unit) {
    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        label = { Text("Code") },
        singleLine = true,
        shape = FieldShape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
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
internal fun GlassAlertDialog(
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
        // app's frosted edge (appGlassRim). Kept as its own override rather than
        // folded into the shared default the rest of the app's frosted chrome now
        // uses uniformly: a modal dialog is a different category from a pill or a
        // pebble card floating over live content -- it always sits over its own
        // dedicated scrim, never directly over an unpredictable photo, and its
        // job is paragraphs of body text and buttons a user has to read and act
        // on, not a glanceable control. The general "everything shares one
        // transparency" rule is about the floating chrome that DOES sit over
        // content; this is the one deliberate exception, not a leftover.
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

    // Remembered on the inputs the derivation actually reads, so the HSV round-trips and
    // parseColor calls don't re-run on every frame of the pull-to-refresh explosion animation
    // (this composable recomposes each of those frames because it reads explosion.value below;
    // the blob colours don't depend on the animation, so they shouldn't ride along with it).
    // Keys cover all three branches: material reads scheme.primary/tertiary/secondary, custom
    // reads customHex, complementary reads scheme.surface (+ tertiary/secondary passthrough).
    val (basePrimary, baseTertiary, baseSecondary) = remember(
        colorMode, customHex, scheme.primary, scheme.tertiary, scheme.secondary, scheme.surface,
    ) {
        val primary = when (colorMode) {
            "material" -> scheme.primary
            "custom" -> customHex?.let { hx -> runCatching { Color(android.graphics.Color.parseColor(hx)) }.getOrNull() } ?: scheme.primary
            else -> {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(scheme.surface.toArgb(), hsv)
                hsv[0] = (hsv[0] + 180f) % 360f
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
        val tertiary = when (colorMode) {
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
        val secondary = when (colorMode) {
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
        Triple(primary, tertiary, secondary)
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
internal fun LockOverlay(vm: AppViewModel) {
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
internal fun EmptyScreen(vm: AppViewModel) {
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

private const val WRAP_MULTIPLIER = 1000
/** Max per-page scale shrink at full off-screen offset (floor 0.94). */
private const val PAGER_SHRINK = 0.06f

/**
 * Wraps a [PagerState] whose page space is a big virtual range, exposing the
 * real (modulo) index and a delta-jump that moves to a real index without an
 * animated fly-through across the virtual range. [realCount] is the number of
 * real items the pages cycle through (cars, car-blocks, or tiles depending on
 * the site); when it is <= 1 there is no wrap and [real] is always 0.
 */
@Stable
private class WrapPagerState(val pager: PagerState, val realCount: Int) {
    fun real(page: Int): Int = if (realCount <= 1) 0 else ((page % realCount) + realCount) % realCount
    val currentReal: Int get() = real(pager.currentPage)
    val settledReal: Int get() = real(pager.settledPage)
    /** Jump so the currently-shown page maps to [target], picking the nearest
     *  virtual page in the current direction (no long fly-through). */
    suspend fun snapToReal(target: Int) {
        if (realCount <= 1) return
        val t = target.coerceIn(0, realCount - 1)
        val delta = t - currentReal
        if (delta != 0) pager.scrollToPage((pager.currentPage + delta).coerceIn(0, pager.pageCount - 1))
    }
}

/**
 * Creates a [WrapPagerState] seeded at the middle of the virtual range plus
 * [initialRealIndex], so the pager opens on that real item and can wrap in
 * both directions. Falls back to a plain single-page state when [realCount]
 * <= 1. The underlying [PagerState] survives recomposition; the wrapper is
 * re-created only when [realCount] changes (it holds no scroll state itself).
 */
@Composable
private fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0): WrapPagerState {
    val loop = realCount > 1
    val virtualCount = if (loop) realCount * WRAP_MULTIPLIER else realCount.coerceAtLeast(1)
    val start = (if (loop) virtualCount / 2 else 0) + initialRealIndex.coerceIn(0, (realCount - 1).coerceAtLeast(0))
    val pager = rememberPagerState(initialPage = start) { virtualCount }
    return remember(pager, realCount) { WrapPagerState(pager, realCount) }
}

/**
 * The shared per-page depth transform for the horizontal car pagers: a subtle
 * shrink proportional to how far this [page] is from the settled one, read ONLY
 * in the draw phase (via [graphicsLayer]) so a drag never triggers recomposition
 * of the page content. NOT applied to the vertical tile pager, which stays flat
 * by design.
 *
 * Scale only — no alpha, no translation. The matching fade this used to apply
 * was removed for a real
 * frame-rate reason, not a taste one. A graphicsLayer with alpha < 1 over content
 * that overlaps (a full car page: cards, their drop shadows, the aurora behind
 * them) makes Compose's default compositing strategy allocate a FULL-SCREEN
 * offscreen buffer and composite through it every frame. During a drag two pages
 * are live, so that's two full-screen buffers per frame purely to tint pages 20%
 * darker in transit. Transforms need no such buffer: scale is applied by the
 * RenderNode directly. Dropping the fade keeps the depth read and removes the
 * per-frame allocation entirely. (CompositingStrategy.ModulateAlpha would also
 * avoid the buffer, but it applies alpha per drawing op, so each pebble's own
 * drop shadow would show THROUGH the semi-transparent card above it — a grey
 * wash under every card mid-swipe. Not worth it for a 0.2 fade.)
 */
private fun Modifier.pagerDepth(pager: PagerState, page: Int): Modifier = graphicsLayer {
    // NO translationX. A parallax drift was tried here and reverted from a
    // device screenshot: a pager page is full-bleed and its neighbours are
    // composed (beyondViewportPageCount = 1), so ANY translation toward the
    // viewport pulls the next car's card into the edge of the screen and
    // leaves it there AT REST -- a sliver of another car down both sides,
    // which is also live to touch. Depth on a full-bleed pager can only come
    // from transforms that shrink or push AWAY, never pull in.
    //
    // Offset formula matches the Compose Pager docs' own sample --
    // (currentPage - page) + currentPageOffsetFraction. This file previously
    // had (page - currentPage) + offset, which negates the fraction's
    // contribution and made the shrink slightly asymmetric mid-drag: one
    // neighbour shrank a touch more than the other for the same finger
    // position.
    val off = abs((pager.currentPage - page).toFloat() + pager.currentPageOffsetFraction)
        .coerceIn(0f, 1f)
    scaleX = 1f - off * PAGER_SHRINK
    scaleY = 1f - off * PAGER_SHRINK
}

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
internal fun isCompactCoverScreen(): Boolean {
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
 *  - `pullFractionState`/`dotsAlphaState`/`refreshShift` together drive how the
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
internal fun GarageScreen(state: UiState, vm: AppViewModel) {
    val vehicles = state.vehicles
    if (vehicles.isEmpty()) return
    val appearance = LocalAppearance.current

    // Collected here rather than read off UiState: the pager's position is its
    // own flow now precisely so that finishing a swipe does not invalidate the
    // car pages. Reading it in THIS composable is fine and intended -- this is
    // one of the few places that genuinely needs it, and it is above the pages.
    val currentIndex by vm.currentIndex.collectAsState()
    val currentVehicle = vehicles.getOrNull(currentIndex.coerceIn(0, vehicles.lastIndex))
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
            vm.reportInfo("Data is over 15 min old. Pull down to refresh")
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
    // NOT read via `by` here: this is GarageScreen scope, the car pager's parent.
    // A composition-scope read meant all ~12 frames of this 200ms fade recomposed
    // GarageScreen and, through it, every live pager page. Held as State and read
    // inside graphicsLayer{} at the use sites instead, so the fade is draw-phase
    // only and never invalidates composition.
    // Narrowed to the boolean flip rather than reading the continuous fraction
    // directly: pullFractionState changes on every pixel of a pull gesture, and a
    // composition-scope read of it here would recompose GarageScreen (the car
    // pager's parent -- see PagerDotsFor's doc comment for why that's expensive)
    // on every one of those pixels, for a target value that's already saturated
    // the moment the pull passes 1%.
    val pulling by remember { derivedStateOf { pullFractionState.value > 0.01f } }
    val dotsAlphaState = animateFloatAsState(
        targetValue = if (state.refreshing || pulling) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "dotsFade",
    )
    // Slide the floating overlays (dots, settings, back/flip) down: in real time as
    // the user pulls, then settle/spring back up once the refresh completes.
    // overlayShiftTarget genuinely needs the continuous fraction (the shift is
    // proportional to how far the user has pulled, not just on/off), so this read
    // can't be narrowed the same way -- it recomposes GarageScreen during an
    // active pull, same as before. What CAN be (and is, below) fixed is the
    // spring's OWN settling frames: `refreshShift` used to be read via `by`,
    // which meant every one of the ~12 frames it takes to spring back up also
    // recomposed GarageScreen, for a value only ever consumed inside an
    // offset { } at its two use sites.
    val overlayShiftTarget = if (state.refreshing) RefreshPullShift
        else (RefreshPullShift * pullFraction).coerceIn(0.dp, RefreshPullShift)
    val refreshShiftState = animateDpAsState(
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
    // `compact` is part of the CONDITION, not just of the message choice. It used
    // to set coverHintShown before testing it, so the once-per-session latch was
    // spent by any device with a punch-hole -- which is essentially every modern
    // phone -- while unfolded, showing nothing. Fold/unfold is a configuration
    // change, and coverHintShown is rememberSaveable precisely to survive one, so
    // a user who opened the app unfolded and then closed the phone reached the
    // cover screen with the hint already marked shown and never saw it: the hint
    // was reliably consumed everywhere except the one screen it exists for.
    //
    // The `vehicles.isEmpty()` variant that used to pick a "setup experience"
    // wording is gone with it -- GarageScreen returns on the first line when
    // vehicles is empty, so that branch was unreachable and the string it chose
    // could never appear.
    LaunchedEffect(compact, hasCameraCutout) {
        if (compact && hasCameraCutout && !coverHintShown) {
            coverHintShown = true
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
                val exWrap = rememberWrapPager(count, (expandedIdx ?: 0).coerceIn(0, count - 1))
                val exPager = exWrap.pager
                LaunchedEffect(exPager) {
                    snapshotFlow { exPager.settledPage }.collect { vm.expand(exWrap.real(it)) }
                }
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = exPager,
                        modifier = Modifier.fillMaxSize(),
                        // Finger swipe between cars is disabled per user request (the
                        // page-to-page swipe felt bad). To view a different car
                        // full-screen the user collapses back to the grid (the "Back to
                        // all cars" button / system back) and expands another car, which
                        // re-seeds this pager on that car via rememberWrapPager above.
                        userScrollEnabled = false,
                        // Paired with userScrollEnabled=false above: the expanded pager
                        // has NO finger swipe, so its neighbour pages can never be shown
                        // or scrolled to — pre-warming them is pure dead weight. Worse,
                        // ExpandedCar is heavier than a collapsed page (dual column, two
                        // scrolls, force-expanded hotspot) and UiState is unstable, so
                        // every state emission (poll/refresh tick/command) recomposes
                        // EVERY in-composition page. beyondViewportPageCount=1 keeps 3
                        // ExpandedCars in composition (current + 2 unreachable neighbours);
                        // 0 keeps just the visible one → the per-emission recompose cost
                        // (and the expand-entry burst under the fade/scale) drops ~3x.
                        // Dots/settle read pure PagerState, so nothing visible changes.
                        // If finger-swipe is ever re-enabled here, restore this to 1 — a
                        // live swipe needs the neighbour pre-warmed (see collapsed pager
                        // below for why).
                        beyondViewportPageCount = 0,
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
                        // Flat, for the same reason as the garage pager below:
                        // these pages are the same shadow-heavy pebble columns.
                        Box(Modifier.fillMaxSize()) {
                            val pv = vehicles[exWrap.real(page)]
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
                        PagerDotsFor(
                            pager = exPager,
                            real = { exWrap.real(it) },
                            count = count,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
                                .graphicsLayer { alpha = dotsAlphaState.value },
                            onRefresh = { vm.refreshStatus(vehicles[exWrap.settledReal]) },
                        )
                    }
                }
            } else {
                val pageCount = (count + perPage - 1) / perPage
                // One extra real "block" tacked onto the end for Settings, when the
                // user has opted into reaching it by swiping instead of the gear
                // button (Appearance.settingsAsPage) -- WrapPagerState.realCount is
                // already just "how many real things this cycles through," so it
                // costs nothing else here to hand it one more than the car-block
                // count and treat that extra index specially below and in the page
                // renderer. block == pageCount (never a valid car block index, which
                // only ever run 0 until pageCount-1) is what marks it as the
                // Settings slot rather than a car.
                // appearance.settingsAsPage || state.landOnSettingsPage, not
                // appearance.settingsAsPage alone: the preference write behind that
                // flag goes through DataStore, and DataStore is genuinely async --
                // the frame right after closeSettings(landOnSettingsPage = true)
                // (itself a synchronous UiState update) can still read the OLD
                // value here for a beat, before the write finishes its round trip
                // back through the Flow. Without the OR, totalBlocks below would
                // stay at the OLD (no-Settings-slot) count on that first frame,
                // making initialBlock's `pageCount` seed a few lines down point at
                // a block that doesn't exist yet -- landOnSettingsPage is
                // unambiguous proof the slot is about to exist regardless of
                // which frame the DataStore write actually lands on.
                val settingsAsPage = appearance.settingsAsPage || state.landOnSettingsPage
                val totalBlocks = if (settingsAsPage) pageCount + 1 else pageCount
                // Normally the car currentIndex was already parked on. The one
                // exception is state.landOnSettingsPage (see its own doc): Settings
                // itself just switched settingsAsPage on and asked to be followed,
                // so this fresh mount seeds straight onto the just-created Settings
                // slot instead of whichever car was selected before Settings was
                // ever opened -- otherwise the user would land on a car for one
                // frame before having to go find the page themselves.
                val initialBlock = if (state.landOnSettingsPage && settingsAsPage) {
                    pageCount
                } else {
                    (currentIndex.coerceIn(0, count - 1)) / perPage
                }
                // Infinite wrap-around: WrapPagerState.realCount is the BLOCK count
                // here (ceil(count / perPage), plus the Settings slot if enabled), and
                // the real vehicle index for a page is realBlock(page) * perPage.
                val wrap = rememberWrapPager(totalBlocks, initialBlock)
                val pager = wrap.pager
                fun realBlock(virtualPage: Int) = wrap.real(virtualPage)
                // Authoritative, not just a fire-and-hope seed: initialBlock above
                // already gets this right on the fast path (a genuinely fresh mount,
                // which returning from Screen.Settings normally is), but that value
                // is only ever honoured the very FIRST time rememberWrapPager builds
                // its underlying pager for this composable instance -- if anything
                // about Compose's own state retention across the AnimatedContent
                // screen transition ever meant this pager wasn't quite as fresh as
                // assumed, a seed-only fix would silently do nothing and Settings
                // would look like it was never actually followed. This actively
                // MOVES the pager there instead of hoping the seed took, which costs
                // nothing extra in the common case (wrap.snapToReal no-ops when
                // already there) and is the actual fix in the uncommon one.
                LaunchedEffect(state.landOnSettingsPage) {
                    if (state.landOnSettingsPage) {
                        if (settingsAsPage) wrap.snapToReal(pageCount)
                        vm.consumeLandOnSettingsPage()
                    }
                }
                LaunchedEffect(pager, perPage) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        // Guarded: the Settings slot isn't a car block, and
                        // selectIndex/currentIndex only ever mean "which car" --
                        // settling there should leave whatever car was last
                        // selected exactly as it was, so swiping back to a car
                        // lands where you left it instead of snapping to car 0.
                        val block = realBlock(page)
                        if (block < pageCount) vm.selectIndex((block * perPage).coerceIn(0, count - 1))
                    }
                }
                // The above only pushes the pager's own settles into
                // currentIndex, never the other direction -- so an
                // external change (a widget/shortcut tap selecting a specific
                // car while this pager was already composed on a different
                // one) updated currentIndex, and the floating name pill below
                // read it correctly, but the pager itself just sat there on
                // whatever car it last settled on. A widget tap always means
                // "look at this car now," so jump (no animated fly-through
                // across a potentially large virtual-page delta) the instant
                // currentIndex moves out from under the page actually shown.
                //
                // Both this and the totalBlocks effect below skip their own very
                // first firing (each with its own remember'd flag -- two
                // independent flags rather than one shared one, so there is no
                // ordering to get right between separate LaunchedEffects racing to
                // set it). LaunchedEffect always runs its body once on first
                // composition regardless of whether its key "changed" from
                // anything, and initialBlock above has ALREADY seeded the correct
                // starting page for every case, landOnSettingsPage included -- so
                // an unguarded first firing here did not correct drift, it
                // OVERWROTE that seed, unconditionally snapping back to
                // currentIndex's own block the instant the pager mounted. That
                // silently defeated landOnSettingsPage every time (Settings looked
                // like it never actually got followed) and, worse, chained into
                // the settle-observer above calling selectIndex for that block --
                // which on a multi-car-per-page grid is not always literally
                // currentIndex when the two don't share a block boundary, so the
                // "correction" could self-report as a genuine, uninitiated car
                // change on the very frame the screen appeared.
                val skipFirstIndexSnap = remember { mutableStateOf(true) }
                LaunchedEffect(currentIndex) {
                    if (skipFirstIndexSnap.value) { skipFirstIndexSnap.value = false; return@LaunchedEffect }
                    // Not on Garage any more (mid exit-transition to another screen,
                    // Settings included): this composition's `state` param keeps
                    // updating live even while AnimatedContent slides its ALREADY-
                    // STALE content off screen, so without this guard a snap here
                    // still visibly moves the pager underneath its own exit
                    // animation -- see the totalBlocks effect below for the exact
                    // trigger (settingsAsPage flipping mid-exit) and why it read as
                    // jank rather than a clean transition.
                    if (state.screen != Screen.Garage) return@LaunchedEffect
                    val targetBlock = currentIndex.coerceIn(0, count - 1) / perPage
                    wrap.snapToReal(targetBlock)
                }
                // Toggling Appearance.settingsAsPage changes totalBlocks -- and
                // therefore `wrap`'s realCount, the modulo divisor real() uses --
                // out from under the pager's raw (unmoved) virtual position. That
                // divisor changing while the position doesn't is exactly what a
                // "seam" is: real(pager.currentPage) resolves to a DIFFERENT block
                // than the one on screen a moment ago, so flipping the switch
                // could silently reshuffle which car you land on, or -- toggling
                // off while parked on the Settings slot itself, which no longer
                // exists under the new count -- strand the pager on an arbitrary
                // block instead of the last real car you were actually on. Same
                // fix as the currentIndex effect above and for the same reason:
                // snap (not fly-through) back to the block currentIndex actually
                // means, which is exactly "stay on the same car" when a car was
                // showing, and "return to the last car you had" when Settings was.
                // Skips its own first firing too -- see the currentIndex effect's
                // comment just above for why. Also skips once this screen is on
                // its way out (same reason, same fix): toggling the switch OFF
                // from the embedded page calls vm.openSettings() immediately, which
                // starts the OUTER Garage -> Settings slide the instant it runs --
                // but appearance.settingsAsPage's own DataStore write can still
                // land a beat or two INTO that slide, while this now-exiting
                // composition is still live and still reacting to real state
                // changes. totalBlocks changing at that exact moment used to fire
                // this effect and snap the pager to a different page while its
                // (already stale, already animating off screen) content was
                // visibly sliding away -- the reported "janky" transition.
                val skipFirstBlocksSnap = remember { mutableStateOf(true) }
                LaunchedEffect(totalBlocks) {
                    if (skipFirstBlocksSnap.value) { skipFirstBlocksSnap.value = false; return@LaunchedEffect }
                    if (state.screen != Screen.Garage) return@LaunchedEffect
                    val targetBlock = currentIndex.coerceIn(0, count - 1) / perPage
                    wrap.snapToReal(targetBlock)
                }
                // Hoisted pill state for single-car-per-page (perPage == 1) mode.
                var carNameVisible by remember { mutableStateOf(false) }
                var scrollToTopFn by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
                val pillScope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        // Finger swipe between cars is ON. Every page renders its FULL
                        // pebble column (VehicleDetailContent → PebbleList) — there is no
                        // in-transit skeleton. Swipe smoothness comes from two places:
                        // PebbleList's own one-frame lazy-fill (only the first EAGER_PEBBLES
                        // sections compose their bodies immediately; the rest fill one frame
                        // later) and beyondViewportPageCount=1 pre-composing the neighbour
                        // while idle, off the drag critical path.
                        userScrollEnabled = true,
                        // beyondViewportPageCount = 1 (was unset → default 0): the
                        // default meant the (heavy) neighbour car page only started
                        // composing the instant it peeked in — i.e. on the FIRST frames
                        // of the drag — so swiping between cars hitched right as it
                        // began. Pre-composing one neighbour while idle moves that work
                        // off the drag critical path. This matches the expanded pager
                        // (which already sets 1 with the same VehicleDetailContent
                        // pages) and the cover-screen pager, so it's consistent with
                        // proven-safe siblings. (The remaining ceiling is that each
                        // page composes a whole car's pebble list; making that lazy is
                        // a bigger, reorder-model-sensitive change left for a device.)
                        //
                        // KEEP THIS AT 1 — do NOT raise it. 1→2 holds two more live
                        // compositions and widens any state emission that DOES change
                        // UiState from ~3 pages to ~5.
                        //
                        // This used to say "because UiState is unstable". It is not: it is
                        // @Immutable, as are Appearance/NotificationPrefs, AppViewModel is
                        // @Stable, and compose-stability.conf covers the `data` package.
                        //
                        // The REAL remaining cost is the opposite of instability. Because
                        // UiState is @Immutable it is diffed with its generated equals(),
                        // which compares every field -- so any one changed field makes the
                        // whole object unequal and every pebble taking it whole recomposes.
                        // Do NOT "fix" that by dropping @Immutable: an unstable object is
                        // compared by reference instead, which is strictly less permissive.
                        //
                        // Fixed: SinglePebble now wraps the `state` it hands each pebble in
                        // remember(<that pebble's own catalogued fields>) { state }, so an
                        // unrelated field changing (another car's weather, an AI/update
                        // probe, a status fetch for a page that isn't even visible) no
                        // longer forces every pebble on every in-composition page to
                        // recompose -- only the ones whose own dependencies actually
                        // changed. currentIndex already lives outside UiState, so a plain
                        // car-switch settle changes nothing any pebble reads at all, and now
                        // that holds for pebble recomposition too, not just for triggering a
                        // new UiState emission in the first place. Reported as real,
                        // measurable cold-start/car-switch lag on a real device; see
                        // SinglePebble's own doc for the full reasoning and the per-pebble
                        // dependency lists.
                        beyondViewportPageCount = 1,
                    ) { page ->
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
                        val block = realBlock(page)
                        val start = block * perPage
                        val end = minOf(start + perPage, count)
                        // The "is this the settled page" test used to live here, as
                        // `page == pager.settledPage`. Discrete, yes -- but it still
                        // subscribed this page's composition to settledPage, so every
                        // in-composition page (three, with beyondViewportPageCount=1)
                        // recomposed its ENTIRE pebble column the moment a swipe
                        // settled. That landed on the same frames as the settle
                        // animation's tail and as selectIndex's own state emission,
                        // which recomposes those same three pages again: two full
                        // rebuilds of three car pages, back to back, exactly at the
                        // end of the gesture. That is the switch-pages hitch.
                        //
                        // It gates one callback, so it moved INTO that callback --
                        // read at invoke time, off the composition path entirely.
                        // No blur, no rotationZ tilt -- see the expanded pager above.
                        // NO pagerDepth here. Reported from a real device: this
                        // swipe was smooth when it was a plain flat scroll, and
                        // went juttery once the shrink was added. A graphicsLayer
                        // scale is cheap on a simple layer, but this page is a
                        // full pebble column and every pebble draws an elevation
                        // shadow -- shadows are rasterized from the layer's
                        // resolved size, so a scale that changes every frame
                        // re-renders all of them every frame, on the drag's
                        // critical path. The cover-screen pager keeps its shrink
                        // because its pages are small and shadow-light.
                        //
                        // The transition this was meant to improve is not worth
                        // the gesture it happens during: a swipe that tracks the
                        // finger exactly IS the effect.
                        if (settingsAsPage && block == pageCount) {
                            // The extra slot: Settings itself, embedded rather than
                            // navigated to -- see SettingsScreen's own `embedded` doc.
                            SettingsScreen(vm, embedded = true)
                        } else {
                        Row(Modifier.fillMaxSize()) {
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
                                            // Only the SETTLED page drives the hoisted
                                            // name-pill state. Without the `settled` gate,
                                            // a beyondViewportPageCount=1 pre-composed
                                            // neighbor (fresh scroll → nameHidden=false)
                                            // would clobber carNameVisible/scrollToTopFn
                                            // and wrongly hide the current car's pill.
                                            onNameHiddenChanged = if (perPage == 1) { hidden, scrollFn ->
                                                // Settled test at CALLBACK time, not
                                                // composition time -- see above. The
                                                // multi-car grid shows every car at
                                                // once, so it is always "settled" and
                                                // never reaches this branch.
                                                if (page == pager.settledPage) {
                                                    carNameVisible = hidden
                                                    scrollToTopFn = scrollFn
                                                }
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
                    }
                    StatusBarScrim()
                    // Floating animated page indicator (no thin top bar). totalBlocks,
                    // not pageCount -- the dots include the Settings slot (one more,
                    // trailing dot) when settingsAsPage is on, same as any other page.
                    if (totalBlocks > 1) {
                        PagerDotsFor(
                            pager = pager,
                            real = { realBlock(it) },
                            count = totalBlocks,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
                                .graphicsLayer { alpha = dotsAlphaState.value },
                            onRefresh = { vm.refreshStatus(vehicles[currentIndex]) },
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
                        // onNameHiddenChanged only fires from a car page (see its own
                        // guard above); swiping onto the Settings slot never touches
                        // carNameVisible, so without this it would keep showing
                        // whichever car was last on screen, floating at the exact
                        // same top-start corner SettingsScreen's own "Settings"
                        // title/back-arrow lives in -- two headers stacked on top of
                        // each other. Settled, not current: matches every other
                        // "which page is this" read in this pager (see the settle
                        // effect above), so it only drops mid-swipe, not the instant
                        // Settings peeks in from the edge.
                        val onSettingsSlot = settingsAsPage && realBlock(pager.settledPage) == pageCount
                        AnimatedVisibility(
                            visible = carNameVisible && !onSettingsSlot,
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
                                        targetState = currentIndex,
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
                                            targetState = currentIndex,
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
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
                    .offset { IntOffset(0, refreshShiftState.value.roundToPx()) },
            )
        }
        if (expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.SwapHoriz,
                description = "Flip columns",
                onClick = { vm.setColumnsFlipped(!appearance.columnsFlipped) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 52.dp)
                    .offset { IntOffset(0, refreshShiftState.value.roundToPx()) },
            )
        }
        // Hidden when Settings is reached by swiping instead (Appearance.settingsAsPage)
        // -- the pager's own extra page is the discovery mechanism in that mode, so a
        // second, redundant entry point here would contradict the "either/or" the
        // setting itself offers. BUT kept while a car is expanded (expandedIdx != null):
        // that pager has finger-swipe disabled entirely (see its own comment above), so
        // there is no swipe alternative there at all -- hiding this unconditionally
        // would make Settings genuinely unreachable from the expanded view.
        if (!appearance.settingsAsPage || expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.Settings,
                description = "Settings",
                onClick = { vm.openSettings() },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding(),
            )
        }
    }
    }
}

/**
 * Measured, adaptive metrics for the cover-screen content region, provided by
 * [CoverScaffold] via [LocalCoverMetrics]. Tiles read this instead of guessing:
 * everything is derived from the REAL available space, so the cover adapts to any
 * cover size, aspect, camera-bump position, and font scale rather than cramming
 * against fixed assumptions.
 *
 * @property widthDp / heightDp measured size of the content region (post-inset).
 * @property isTiny true when the shorter usable side is below [COVER_TINY_DP] —
 *   tiles show fewer secondary rows / a tighter type step when tiny.
 * @property contentPadding the single merged inset (nav bar ∪ display cutout ∪
 *   camera-bump clearance ∪ base gutter), applied ONCE by the tile region.
 */
@androidx.compose.runtime.Immutable
data class CoverMetrics(
    val widthDp: Float,
    val heightDp: Float,
    val isTiny: Boolean,
    val contentPadding: PaddingValues,
)

private val LocalCoverMetrics = staticCompositionLocalOf<CoverMetrics?> { null }

/** Below this (shorter usable side, dp) the cover is "tiny" — trim to essentials. */
private const val COVER_TINY_DP = 300f

/**
 * Horizontal content inset for cover pebbles.
 *
 * The one real consumer of [CoverMetrics.isTiny] -- [LocalCoverMetrics] was provided by
 * [CoverScaffold] and documented at length ("everything is derived from the REAL
 * available space... rather than cramming against fixed assumptions"), but nothing
 * actually read `isTiny` anywhere; every cover dimension was a flat constant
 * regardless of how small the measured region came out. This trims the inset by 4dp
 * on a tiny cover, which is a real fraction of a screen whose shorter usable side is
 * already under 300dp -- a fixed 16dp on both sides was costing that tile
 * proportionally more room than the same inset costs a larger cover.
 */
@Composable
private fun coverContentInset(): Dp = if (LocalCoverMetrics.current?.isTiny == true) 12.dp else 16.dp

/** True inside a [CoverTile]'s body, i.e. below a title band that already
 *  shows the page's icon and name. [CoverHero] reads it to avoid drawing that
 *  same glyph a second time, a few dp lower and larger. */
private val LocalCoverTileTitled = staticCompositionLocalOf { false }

/** The one converged cover-hero icon size. Was drifting 30/48/64 across tiles; a single
 *  scale is what makes the cover read as one system. Device-verify the exact value
 *  (32–36 is the safe window at ~1.15x font scale); 34 is one nudge up from the old majority. */
private val CoverHeroIcon = 34.dp

/**
 * The one shared glance-hero every cover tile opens with: a shrink-to-fit
 * headline [value] (via [com.bloo.uicommon.FittedText], so it can never
 * clip/wrap), optionally a [trailing] value pushed to the row end (e.g. Climate setpoint)
 * and a [subline] below (e.g. AI status, Location coordinates). Left-aligned, full-width,
 * and — critically — emits NO trailing Spacer: the cover shell's `spacedBy(CenterVertically)`
 * owns the gap to the next child, so Climate/Info/Diagnostics/AI/Fuel/Trips/Location all
 * share the exact same rhythm. Color must be baked into the FittedText style (it ignores
 * LocalContentColor).
 */
@Composable
private fun CoverHero(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: String? = null,
    trailingColor: Color = MaterialTheme.colorScheme.onSurface,
    subline: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The [icon] is drawn ONLY when this hero isn't already inside a
            // CoverTile that named the page with the same glyph. It always is,
            // now that every cover page goes through the template -- so in
            // practice this draws nothing and the value gets the full width,
            // which on a one-inch screen is several characters of headline.
            // The parameter stays because the icon is what a caller reaches
            // for first, and silently ignoring one passed outside a titled
            // tile would be worse than honouring it.
            if (!LocalCoverTileTitled.current) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(CoverHeroIcon))
            }
            com.bloo.uicommon.FittedText(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = valueColor),
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                com.bloo.uicommon.FittedText(
                    text = trailing,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = trailingColor),
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        if (subline != null) {
            Text(
                subline,
                style = MaterialTheme.typography.bodyMedium,
                // Not MutedContentAlpha (0.7): CoverHero only ever renders inside an
                // already cover-gated branch (FuelPebble, LocationPebble,
                // AiSummaryPebble, ...), where the ambient content color is already
                // the dimmer onSurfaceVariant role (the pebble's default container is
                // surfaceVariant) -- 0.7 on top compounds into the same "overly gray"
                // pattern StatusRow's label had. This is the hero every cover page
                // actually opens with, so it's a bigger legibility cost than a list
                // row's label.
                color = LocalContentColor.current.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * THE cover-screen tile template. Every page on the flip cover is one of
 * these, so they all read as the same object with different contents rather
 * than as a stack of unrelated cards.
 *
 * Three bands, always in this order:
 *  1. TITLE -- a small icon and the tile's name at title size, with an
 *     optional state [subtitle] under it. Cover pebbles used to have no title
 *     at all: the header row is dropped in fill-height mode (it cost ~76dp
 *     before a single line of content) and all that was left was a 30dp icon
 *     badge floating over the body's top-start corner. That badge said which
 *     tile you were on only if you already knew the iconography, and it
 *     overlapped the content it sat on.
 *  2. BODY -- weighted, so it takes everything left over, and centred within
 *     that. Scrolls when it's taller than the space, using the caller's
 *     [scrollState] so the cover pager can tell "scroll the tile" from "page
 *     to the next tile".
 *  3. ACTIONS -- an optional bottom bar pinned outside the scroll area, so a
 *     tile's controls are reachable no matter where its body is scrolled to.
 *
 * The bands are the standard; what goes in them is per-tile. That is the
 * whole point: the home tile's four-button bar and a pebble's single pinned
 * action are the same band in the same place at the same height, so paging
 * between them moves the content and nothing else.
 */
@Composable
private fun CoverTile(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    scrollState: ScrollState? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    // Drawn BEHIND the title/body/actions, inside the card's own clip -- the same
    // slot PebbleShell's own `background` is for the phone hero, and for the same
    // reason: CoverMainTile uses this for a full-bleed car photo. Whatever's here
    // is responsible for its own legibility (see titleColor/iconTint below); null
    // for every other tile, so nothing else pays for the extra Box.
    background: (@Composable BoxScope.() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    body: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(PebbleCornerExpanded)
    val outline = LocalAppearance.current.pebbleOutline
    Card(
        modifier = modifier
            .fillMaxSize()
            .dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp)
            .then(
                if (outline) {
                    Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
                } else Modifier,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColorFor(containerColor),
        ),
    ) {
      Box(Modifier.fillMaxSize()) {
        background?.invoke(this)
        Column(Modifier.fillMaxSize().padding(horizontal = coverContentInset())) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
                com.bloo.uicommon.FittedText(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    // Not MutedContentAlpha (0.7) atop the Card's own contentColor: that
                    // color is contentColorFor(containerColor), and the default
                    // containerColor is surfaceVariant, whose paired content tone is
                    // onSurfaceVariant -- already a lower-contrast MD3 role before any
                    // alpha is applied. Muting it further compounds two dimming steps
                    // into text that reported as "overly gray" on several cover pages,
                    // where this subtitle is a full line right under the title (not a
                    // small list-row label, the case MutedContentAlpha was tuned for).
                    // 0.92 keeps it visually secondary to the title without reading as
                    // washed out on a small, quick-glance screen.
                    color = subtitleColor ?: LocalContentColor.current.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // BoxWithConstraints captures the real available height, undisturbed
            // by verticalScroll one level in, so heightIn(min = ...) can force
            // the scrolling Column to at least that tall -- which is what makes
            // a short body centre in the band instead of collapsing to its top
            // with dead space underneath, while a tall one still scrolls.
            val scroll = scrollState ?: rememberScrollState()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val minHeight = maxHeight
                CompositionLocalProvider(LocalCoverTileTitled provides true) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fadingEdges(scroll)
                            .verticalScroll(scroll)
                            .heightIn(min = minHeight)
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                        content = body,
                    )
                }
            }
            if (actions != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    content = actions,
                )
            } else {
                Spacer(Modifier.height(14.dp))
            }
        }
      }
    }
}

/**
 * The flip cover's home tile: the car, its charge, and its controls on ONE
 * screen.
 *
 * It replaces two separate cover pages that were each mostly empty. The old
 * home tile was the phone's HeroHeader reused verbatim -- a card built around
 * a photo -- but landed on a flat gradient here instead: HeroHeader never went
 * through PebbleShell/CoverTile's fill-height cover treatment, so its own
 * `cover` branch stopped being reachable once this tile replaced it as the
 * cover's actual home page, leaving that photo path built but orphaned. The
 * lock/horn controls then lived on their own second page with the same
 * emptiness under them. Neither page filled a screen; both together do, and
 * merging them means the thing you most want with the phone shut -- lock
 * state and the lock button -- is on the page it opens on rather than one
 * swipe away.
 *
 * The car's photo is now this tile's own background (via CoverTile's
 * `background` slot), full-bleed with the same scrim [HeroPhotoBackdrop]
 * already builds for the phone hero -- reusing that composable rather than a
 * second implementation, so the two can't drift. No photo set -> HeroVisual's
 * own brand-gradient fallback fills the same way, so the tile is never a
 * dead inset rectangle either way.
 *
 * The car's name leads, at headline size. It used to be a labelMedium line in
 * the shared top overlay, sharing that space with the page dots, which on this
 * screen made the single most identifying thing on it the smallest text on it.
 */
@Composable
private fun CoverMainTile(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val metric = LocalAppearance.current.unitSystem == "metric"
    val imageUrl = state.imageUrls[v.vin]
    val hasPhoto = !imageUrl.isNullOrBlank()
    // The car's own name is this tile's title -- the template's title band is
    // where every other page says what it is, so the home page says which car.
    // Lock leads the subtitle because it is the reason to look at a shut
    // phone; driving/charging state is left to ChargeFuelBar's own status
    // line, which is directly below it and already says both.
    val bits = listOfNotNull(
        status?.doorLock?.let { if (it) "Locked" else "Unlocked" },
        if (status?.airCtrlOn == true) "Climate on" else null,
    )
    // Same trade the phone hero makes over its own photo (HeroPhotoBackdrop's scrim
    // is built for HeroOnPhoto text): a fixed near-white reads correctly against
    // that scrim regardless of the photo's own brightness, where the theme's usual
    // onSurface/error tones would not. Lock's own attention colour (error, an
    // unlocked car) still needs to read as a WARNING over a photo, not just legible
    // -- swapped to a fixed warm red rather than the theme's errorContainer-tuned
    // MaterialTheme.colorScheme.error, which is calibrated against a flat surface.
    val titleColor = if (hasPhoto) HeroOnPhoto else MaterialTheme.colorScheme.onSurface
    val subtitleColor = when {
        status?.doorLock == false -> if (hasPhoto) Color(0xFFFF8A80) else MaterialTheme.colorScheme.error
        hasPhoto -> HeroOnPhoto.copy(alpha = MutedContentAlpha)
        else -> null
    }
    CoverTile(
        title = v.name,
        icon = Icons.Filled.DirectionsCar,
        subtitle = bits.joinToString(" · ").ifBlank { null },
        subtitleColor = subtitleColor,
        iconTint = if (hasPhoto) HeroOnPhoto else MaterialTheme.colorScheme.primary,
        titleColor = titleColor,
        background = {
            // height is inert when fill = true -- HeroVisual only reads it in the
            // non-fill, non-aspectRatio branch (see its own `sizeModifier` when) --
            // so there's no real value to pass; this Box has no BoxWithConstraints
            // scope to measure one from anyway.
            HeroPhotoBackdrop(v, imageUrl, height = 0.dp, corner = PebbleCornerExpanded, fill = true)
        },
        actions = { CoverActionBar(v, state, vm) },
    ) {
        CompositionLocalProvider(LocalContentColor provides titleColor) {
        ChargeFuelBar(
            status,
            state.hasBattery(v),
            state.hasFuel(v),
            state.drivingLabel(v),
            metric = metric,
        )
        }
    }
}

/**
 * The cover screen's bottom control bar: one tap each for the actions that
 * live in the pebble headers on the phone -- lock, climate, charge, horn.
 *
 * Those header actions are the whole point of every pebble; on the cover they
 * were reachable only by swiping to the matching page, and two of them (climate
 * and charge) not at all, because those pages open on a glance hero rather than
 * their header. A shut phone is the surface where "just lock it" matters most,
 * so they get a permanent, full-width, thumb-height row instead -- the
 * [CoverTile] actions band, which every cover page now has.
 *
 * Buttons are sized by weight rather than fixed width, so a car with no
 * horn/lights support or no battery gets three fat buttons rather than four
 * narrow ones with a hole where the fourth was.
 */
@Composable
private fun RowScope.CoverActionBar(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val ev = status?.evStatus
    val locked = status?.doorLock
    val charging = ev?.batteryCharge == true
    val plugged = ev.isPluggedOrCharging
    val climateOn = status?.airCtrlOn == true
    val enabled = !state.loading
    CoverActionButton(
        icon = if (locked == true) Icons.Filled.LockOpen else Icons.Filled.Lock,
        label = if (locked == true) "Unlock" else "Lock",
        // Attention, not confirmation: an unlocked car is the state worth
        // colouring, matching StateControl's own highlightWhenOff.
        attention = locked == false,
        pending = state.isPending(v.vin, "doors"),
        enabled = enabled,
        onClick = { if (locked == true) vm.unlock(v) else vm.lock(v) },
    )
    CoverActionButton(
        icon = Icons.Filled.Thermostat,
        label = if (climateOn) "Stop" else "Climate",
        active = climateOn,
        pending = state.isPending(v.vin, "climate"),
        enabled = enabled,
        onClick = { vm.toggleClimate(v) },
    )
    if (state.hasBattery(v)) {
        CoverActionButton(
            icon = Icons.Filled.Bolt,
            label = if (charging) "Stop" else "Charge",
            active = charging,
            pending = state.isPending(v.vin, "charge"),
            // The car can't start a charge it isn't plugged into, and the
            // Charge pebble's own header button is gated the same way.
            enabled = enabled && plugged,
            onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
        )
    }
    if (v.supportsHornLights) {
        // One button doing double duty rather than a fifth icon squeezed into an
        // already-tight row on a ~1-inch cover: tap for the combined "Horn &
        // lights" the main phone UI leads with, long-press for lights-only --
        // silent, useful for finding a car in a dark lot without honking. The
        // main phone screen offers both as separate buttons in a group
        // (PrimaryActions); flashLights had no cover-screen path at all before
        // this, reported as a real feature gap. Long-press is already an
        // established cover gesture (the tile-scrubber rail, the edge-trace
        // refresh), so this isn't a new interaction language for the surface.
        CoverActionButton(
            icon = Icons.Filled.Campaign,
            label = "Horn",
            // Both flashLights and hornAndLights run under the same "hornLights"
            // pending key (AppViewModel), so one check covers either.
            pending = state.isPending(v.vin, "hornLights"),
            enabled = enabled,
            onClick = { vm.hornAndLights(v) },
            onLongClick = { vm.flashLights(v) },
        )
    }
}

/** One button in [CoverActionBar]: icon over a short label, filling its share
 *  of the row. Colour carries state -- [active] for a running command's target
 *  state, [attention] for a state the user probably wants to change. */
@Composable
private fun RowScope.CoverActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    attention: Boolean = false,
    pending: Boolean = false,
    enabled: Boolean = true,
    // A second action on the same button, reached by holding rather than
    // tapping -- null for every caller but the horn/flash one. Kept optional
    // rather than every button growing a second gesture it has no use for.
    onLongClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val container by androidx.compose.animation.animateColorAsState(
        when {
            active -> scheme.primary
            attention -> scheme.errorContainer
            else -> scheme.surfaceContainerHighest
        },
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "coverActionFill",
    )
    val content by androidx.compose.animation.animateColorAsState(
        when {
            active -> scheme.onPrimary
            attention -> scheme.onErrorContainer
            else -> scheme.onSurface
        },
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "coverActionInk",
    )
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                enabled = enabled && !pending,
                onLongClick = onLongClick?.let { fn -> { haptics?.tick(); fn() } },
                onClick = { haptics?.click(); onClick() },
            ),
        shape = RoundedCornerShape(16.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(3.dp))
            com.bloo.uicommon.FittedText(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                ),
            )
        }
    }
}

/** Most of one dimension a display cutout may ever claim as clearance. A real
 *  notch or lens is a few percent; anything demanding more than this is a
 *  camera island being measured as though it were a punch-hole, and honouring
 *  it costs more screen than it protects. See cutoutClearanceDp. */
private const val MAX_CUTOUT_FRACTION = 0.22f

/**
 * Per-edge camera-bump clearance in dp, computed from the display cutout rects for
 * ANY bump position. Returns how much each edge must be reserved so content flows
 * AROUND the punch-hole/bump instead of under it: (start, top, end, bottom) in dp,
 * zeros pre-API-28 or with no cutout.
 *
 * Why this exists alongside the native WindowInsets.displayCutout padding: on
 * Samsung flip COVER displays the OS frequently reports the front camera via
 * displayCutout.boundingRects (which is why the decorative ring positions
 * correctly) but exposes ZERO safeInset/displayCutout WINDOW insets for it — so
 * windowInsetsPadding(displayCutout) alone reserves nothing and content sits under
 * the bump (observed on the user's device). This reads the rects directly (each
 * call, not a remember(view) snapshot, so it reflects insets once dispatched).
 *
 * CRITICAL for a CORNER bump: PaddingValues insets a WHOLE edge, so reserving both
 * edges a corner bump touches removes an L-shaped chunk from two full sides — for a
 * bottom-right bump that's a full-HEIGHT right strip ~45% of the width, which
 * crushed every tile's content into the left half (observed: values wrapping
 * "Locke/d"/"Runnin/g", range clipped to "26…"). A corner bump only occludes its
 * corner, so this reserves only the edge with the SMALLER intrusion — for a
 * bottom-right bump that's the bump's HEIGHT (small), pushing content up just
 * enough to clear it while reclaiming the full width. A true single-edge cutout
 * still pads that one edge. Only a bump within [edgeBandPx] of an edge counts.
 */
private data class EdgeDp(val start: Float, val top: Float, val end: Float, val bottom: Float)

@Composable
private fun cutoutClearanceDp(): EdgeDp {
    val view = LocalView.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return EdgeDp(0f, 0f, 0f, 0f)
    val cutout = view.rootWindowInsets?.displayCutout ?: return EdgeDp(0f, 0f, 0f, 0f)
    val vw = view.width
    val vh = view.height
    if (vw <= 0 || vh <= 0) return EdgeDp(0f, 0f, 0f, 0f)
    val edgeBandPx = with(density) { 24.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    for (r in cutout.boundingRects) {
        val hIntr: Float? = when {
            r.left <= edgeBandPx -> r.right + margin
            vw - r.right <= edgeBandPx -> (vw - r.left) + margin
            else -> null
        }
        val vIntr: Float? = when {
            r.top <= edgeBandPx -> r.bottom + margin
            vh - r.bottom <= edgeBandPx -> (vh - r.top) + margin
            else -> null
        }
        // Corner bump: reserve only the smaller intrusion so the opposite full
        // dimension is reclaimed. Edge notch: reserve that one edge.
        val hOnly = hIntr != null && (vIntr == null || hIntr <= vIntr)
        val vOnly = vIntr != null && (hIntr == null || vIntr < hIntr)
        if (hOnly && r.left <= edgeBandPx) left = maxOf(left, hIntr!!)
        if (hOnly && vw - r.right <= edgeBandPx) right = maxOf(right, hIntr!!)
        if (vOnly && r.top <= edgeBandPx) top = maxOf(top, vIntr!!)
        if (vOnly && vh - r.bottom <= edgeBandPx) bottom = maxOf(bottom, vIntr!!)
    }
    // Clamp each edge to a fraction of its own dimension.
    //
    // The arithmetic above assumes the cutout is a small punch-hole, so the
    // clearance is measured from the FAR side of the rect: `r.right + margin`,
    // or `(vw - r.left) + margin`. That's right for a lens and catastrophic
    // for a flip cover screen, which reports its whole camera ISLAND as one
    // bounding rect -- an island starting halfway across yields a clearance of
    // half the display, and the content gets squeezed into the strip that's
    // left with the rest sitting empty. Reported from a real device.
    //
    // Past this cap the rect isn't a notch to dodge, it's the panel's shape,
    // and the honest response is to use the space rather than surrender it:
    // anything the hardware genuinely occludes is already excluded from the
    // window the app was given.
    val maxH = vw * MAX_CUTOUT_FRACTION
    val maxV = vh * MAX_CUTOUT_FRACTION
    return with(density) {
        EdgeDp(
            left.coerceAtMost(maxH).toDp().value,
            top.coerceAtMost(maxV).toDp().value,
            right.coerceAtMost(maxH).toDp().value,
            bottom.coerceAtMost(maxV).toDp().value,
        )
    }
}


/**
 * The strip of screen BESIDE the camera island, when there is one worth using.
 *
 * A flip cover reports its whole camera island as one display-cutout rect
 * hugging an edge, and every layout here so far has responded by reserving
 * that entire edge -- the island's height across the full width. But the
 * island only occupies part of that band; the rest of it is real, lit,
 * unoccluded screen that nothing was allowed to use. On a screen this small
 * that is a meaningful fraction of it.
 *
 * Returns the larger of the two free segments (left of the island or right of
 * it) as an absolute rect in dp from the window's top-left, or null when there
 * is no cutout, when the cutout doesn't hug a horizontal edge, or when what's
 * beside it is too small to hold anything worth putting there. Null is the
 * normal answer on a phone; this is a cover-screen affordance.
 */
private data class CoverBand(val xDp: Float, val yDp: Float, val widthDp: Float, val heightDp: Float)

/** Below these a band is a sliver: too short for a legible line of text, or
 *  too narrow for a name plus a tap target. */
private const val COVER_BAND_MIN_W = 84f
private const val COVER_BAND_MIN_H = 26f

@Composable
private fun coverCutoutBand(): CoverBand? {
    val view = LocalView.current
    val density = LocalDensity.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val cutout = view.rootWindowInsets?.displayCutout ?: return null
    val vw = view.width
    val vh = view.height
    if (vw <= 0 || vh <= 0) return null
    val edgeBandPx = with(density) { 24.dp.toPx() }
    var best: CoverBand? = null
    for (r in cutout.boundingRects) {
        // Only a rect hugging the TOP or BOTTOM edge leaves a band beside it
        // that runs the other way. One hugging a side edge leaves a tall thin
        // column, which is not a place to put a name and a button.
        val hugsTop = r.top <= edgeBandPx
        val hugsBottom = vh - r.bottom <= edgeBandPx
        if (!hugsTop && !hugsBottom) continue
        val leftFree = r.left.toFloat()
        val rightFree = (vw - r.right).toFloat()
        val useLeft = leftFree >= rightFree
        val widthPx = if (useLeft) leftFree else rightFree
        val xPx = if (useLeft) 0f else r.right.toFloat()
        val band = with(density) {
            CoverBand(
                xDp = xPx.toDp().value,
                yDp = r.top.toFloat().toDp().value,
                widthDp = widthPx.toDp().value,
                heightDp = (r.bottom - r.top).toFloat().toDp().value,
            )
        }
        if (band.widthDp < COVER_BAND_MIN_W || band.heightDp < COVER_BAND_MIN_H) continue
        // Widest wins, on the theory that whatever we put there wants room.
        if (best == null || band.widthDp > best!!.widthDp) best = band
    }
    return best
}

/**
 * The adaptive cover-screen scaffold. Measures the REAL available space with
 * BoxWithConstraints and merges every inset source (nav bar, display cutout,
 * corner-safe camera-bump clearance, a small base gutter) into ONE contentPadding
 * per edge via max() — never additively — so a device that reports the bump both
 * as a window inset AND a boundingRect reserves it exactly once (this was the
 * "crammed into the left half" bug). Exposes [CoverMetrics] via [LocalCoverMetrics]
 * and clamps the subtree font scale so a huge system font can't overflow the tiny
 * face. The scaffold itself does NOT apply the padding — the tile region reads
 * metrics.contentPadding — so full-bleed siblings (rings, rail) stay full-bleed.
 */
@Composable
private fun CoverScaffold(
    reserveRailGutter: Boolean,
    content: @Composable BoxWithConstraintsScope.(CoverMetrics) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutDir = LocalLayoutDirection.current
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        // Gentle base gutter off the shorter side so a small cover doesn't lose a
        // fixed chunk; extra end gutter when the tile-scrubber rail is shown.
        val gutterScale = (minOf(wDp, hDp) / 300f).coerceIn(0.8f, 1.2f)
        val baseSide = 10f * gutterScale
        val baseEnd = (if (reserveRailGutter) 22f else 10f) * gutterScale
        val cut = cutoutClearanceDp()
        val sys = WindowInsets.navigationBars.union(WindowInsets.displayCutout).asPaddingValues()
        val sysStart = sys.calculateStartPadding(layoutDir).value
        val sysTop = sys.calculateTopPadding().value
        val sysEnd = sys.calculateEndPadding(layoutDir).value
        val sysBottom = sys.calculateBottomPadding().value
        // Single merged inset per edge — the whole point: max(), not sum.
        val padStart = maxOf(baseSide, cut.start, sysStart)
        val padTop = maxOf(baseSide, cut.top, sysTop)
        val padEnd = maxOf(baseEnd, cut.end, sysEnd)
        val padBottom = maxOf(12f * gutterScale, cut.bottom, sysBottom)
        val usableW = (wDp - padStart - padEnd).coerceAtLeast(0f)
        val usableH = (hDp - padTop - padBottom).coerceAtLeast(0f)
        val isTiny = minOf(usableW, usableH) < COVER_TINY_DP
        val metrics = CoverMetrics(
            widthDp = usableW,
            heightDp = usableH,
            isTiny = isTiny,
            contentPadding = PaddingValues(start = padStart.dp, top = padTop.dp, end = padEnd.dp, bottom = padBottom.dp),
        )
        // Coarse font-scale clamp for the whole cover subtree so a large system font
        // can't blow past the measured region (FittedText is the fine guard on top).
        val cappedFont = density.fontScale.coerceAtMost(if (isTiny) 1.15f else 1.3f)
        CompositionLocalProvider(
            LocalCoverMetrics provides metrics,
            LocalDensity provides Density(density.density, cappedFont),
        ) {
            content(metrics)
        }
    }
}

/**
 * Cover-screen stand-in for the full phone Settings screen. The real Settings
 * (search + keyboard, photo pickers/crop, drag-reorder lists, sign-out) is
 * unusable on a ~1-inch flip cover, so on the cover we route here instead (see
 * BlooApp) — a single centered card telling the user to unfold / open Bloo on the
 * phone, with one Back button.
 *
 * Routed through [CoverScaffold] for its corner-safe padding, not a hand-rolled
 * stack of its own -- this used to chain `.windowInsetsPadding(navigationBars +
 * displayCutout)` and `.padding(cameraBumpPadding())` as two separate, ADDITIVE
 * modifiers (plus a third gutter padding on top). That is exactly the
 * double-reservation CoverScaffold's own doc explains at length: on a device
 * where both the window-inset channel and the boundingRect-derived clearance
 * report the same camera bump, this card reserved it twice -- "the 'crammed
 * into the left half' bug", on the one screen every cover user reaches when
 * they try to open Settings.
 */
@Composable
private fun CoverManageOnPhoneCard(vm: AppViewModel) {
    CoverScaffold(reserveRailGutter = false) { metrics ->
    Box(
        Modifier
            .fillMaxSize()
            .padding(metrics.contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(PebbleCornerExpanded),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    "Open Bloo on your phone to change settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                MorphTextButton("Back", onClick = vm::closeSettings, modifier = Modifier.fillMaxWidth())
            }
        }
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
    // (min > max) before the pager below ever gets a chance to handle an empty
    // list gracefully. Kept as a crash guard rather than an expected state, and
    // labelled that way on purpose: it is currently UNREACHABLE from the one
    // caller -- GarageScreen returns on its first line when vehicles is empty,
    // and a zero-car app routes to Screen.Empty long before Screen.Garage. This
    // comment used to cite a `compact && vehicles.isEmpty()` branch in that caller
    // as proof the state was real; that branch was itself dead for the same
    // reason, and has been deleted. Two lines of guard against a throwing
    // coerceIn is still worth keeping; the claim that something reaches it wasn't.
    if (count == 0) {
        EmptyScreen(vm)
        return
    }
    // Infinite wrap-around, matching every other car-switching pager in the
    // app (the expanded pager, the default grid) and the cover screen's own
    // tile pager, which already looped.
    // Same as GarageScreen: the index is its own flow, collected here.
    val currentIndex by vm.currentIndex.collectAsState()
    val wrap = rememberWrapPager(count, currentIndex.coerceIn(0, count - 1))
    val pager = wrap.pager
    fun realCar(virtualPage: Int) = wrap.real(virtualPage)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.selectIndex(realCar(it)) }
    }
    // Mirror of the default garage pager's own fix: react to currentIndex
    // changing out from under an already-composed pager (e.g. a widget tap
    // selecting a specific car while the cover screen was already showing a
    // different one) by snapping to it, instead of only ever pushing this
    // pager's own settles into currentIndex one-way.
    LaunchedEffect(currentIndex) {
        wrap.snapToReal(currentIndex.coerceIn(0, count - 1))
    }
    // True while the page scrubber is active; suspends car-switching swipes so a
    // scrub gesture can't be hijacked into flipping to the next car.
    val scrubbing = remember { mutableStateOf(false) }
    // Hide the page indicators while a refresh is in flight (pull-to-refresh /
    // manual refresh) so the loading indicator owns the screen. Shared by both
    // dot rows below (car-switch AND per-car tile) instead of each keeping its
    // own separate Animatable of the exact same value.
    // Held as State, not read via `by` — see the same treatment in GarageScreen.
    // Read in composition scope this fade recomposed the whole cover pager (and,
    // as a plain Float parameter, every CompactCar page) once per animation frame.
    val dotsAlphaState = animateFloatAsState(
        targetValue = if (state.refreshing) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "coverDotsFade",
    )
    Box(Modifier.fillMaxSize()) {
        // Which cover tile is showing. The home tile titles ITSELF with the
        // car's name (see CoverMainTile), so the shared overlay saying it too
        // put the same words twice on a one-inch screen -- which is what the
        // overlay was added to fix in the first place, for the OTHER tiles,
        // whose titles name a section rather than a car.
        var visibleTile by remember { mutableStateOf("main") }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !scrubbing.value,
            beyondViewportPageCount = 1,
        ) { page ->
            val v = vehicles[realCar(page)]
            // No blur -- see the other two car pagers' history for why: a plain
            // Modifier.blur(x.dp) reconstructs and re-lays-out its own modifier
            // node on every drag frame (the jitter this exact pattern caused
            // elsewhere), and this cover-screen pager had never actually been
            // updated when that got fixed there. Just the cheap graphicsLayer
            // fade/scale transforms now, consistent with the other pagers.
            Box(Modifier.fillMaxSize().pagerDepth(pager, page)) {
                CarThemeOverride(
                    paletteId = appearance.carCustomPaletteIds[v.vin],
                    customPalettes = appearance.customPalettes,
                    themeMode = appearance.themeMode,
                    vibrancy = appearance.vibrancy,
                ) {
                    CompositionLocalProvider(LocalCoverScrubbing provides scrubbing) {
                        CompactCar(v, state, vm, dotsAlphaState, onTileChange = { visibleTile = it })
                    }
                }
            }
        }
        // Measured once and shared by both readers below (the top-overlay name
        // and the band itself), so they can never disagree about whether the
        // band exists and end up showing the name twice or not at all.
        val band = coverCutoutBand()
        // Car-switching dots, hoisted out of CompactCar (a per-page composable)
        // and up to here -- a sibling of the whole pager, not inside any one
        // page's fade/scale graphicsLayer -- so it doesn't itself fade and
        // shrink along with the outgoing/incoming car during a swipe, exactly
        // like every other car pager's PagerDots already stays put outside
        // the per-page transform.
        // Car name + switching dots, in one TopCenter overlay.
        //
        // The name is here rather than in the tiles because cover pebbles
        // render header-less by design, so nothing on the cover screen said
        // which car you were looking at -- fine on the main tile, genuinely
        // confusing on Charge or Climate after swiping between cars.
        // Reported from a real device.
        //
        // It rides the same overlay the dots already occupied, so it claims
        // no vertical space that wasn't already spoken for, and fades with
        // the same refresh alpha so the loading indicator still owns the
        // screen during a refresh.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                .graphicsLayer { alpha = dotsAlphaState.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Skipped when the camera band is showing the name instead -- see
            // below. Saying it twice on a screen this size is worse than the
            // problem the overlay was added to solve.
            vehicles.getOrNull(currentIndex.coerceIn(0, count - 1))
                ?.takeIf { band == null && visibleTile != "main" }
                ?.let { current ->
                Text(
                    current.name,
                    // Was labelMedium/onSurfaceVariant -- the smallest, dimmest
                    // text on a screen whose whole job is telling you which car
                    // you are looking at. The home tile now says it at headline
                    // size itself; this overlay is what the OTHER tiles have, so
                    // it reads as a title rather than a caption.
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            if (count > 1) {
                Spacer(Modifier.height(6.dp))
                PagerDotsFor(
                    pager = pager,
                    real = { realCar(it) },
                    count = count,
                    // No hold-to-refresh here -- the cover screen's own
                    // edge-trace gesture (drag down from the top edge) is
                    // already the refresh affordance in this mode; the dots
                    // are display-only.
                    onRefresh = null,
                )
            }
        }
        if (band != null) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = band.xDp.dp, y = band.yDp.dp)
                    .width(band.widthDp.dp)
                    .height(band.heightDp.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // The car's name, which every other cover page has to spend a
                // whole title band on. Here it costs nothing.
                //
                // Name only: search on the cover is the draggable bubble (see
                // SearchLayer), and a second, fixed copy of it here would be
                // the thing the bubble exists to avoid -- a search button
                // parked somewhere the user can't move it off whatever it is
                // covering.
                vehicles.getOrNull(currentIndex.coerceIn(0, count - 1))?.let { current ->
                    com.bloo.uicommon.FittedText(
                        text = current.name,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * One car's page inside [CompactGarage]'s pager: a vertical stack of pebble
 * "tiles" (main summary, climate, charge, location, ...), one per screen,
 * navigated with the same infinite-wrap virtual-page trick as the car
 * pager itself. Also owns three independent, cover-screen-only concerns
 * layered into the same [Box]:
 *  - Camera-cutout avoidance: content is padded via native
 *    WindowInsets.displayCutout (corner-safe, recomposition-aware) so it clears
 *    a punch-hole on whichever edge(s) it touches; a decorative ring is drawn
 *    around the hole so it reads as intentional.
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
private fun CompactCar(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    // State, not Float: a plain Float parameter changes on every frame of the
    // dots fade, which made this whole page recompose ~15 times per fade. As
    // State the value is read draw-phase only (see its graphicsLayer use below).
    dotsAlphaState: androidx.compose.runtime.State<Float>,
    /** Which tile is centred, reported up so the shared top overlay knows
     *  whether the page below it is already showing the car's name. */
    onTileChange: (String) -> Unit = {},
) {
    val status = state.statusFor(v)
    val isGen5W = remember(v.brand, v.generation) { v.isGen5W }
    // Cover-screen tiles follow the same order the user arranged the pebbles in
    // (state.sectionsFor). "summary" maps to the always-present "main" tile;
    // "controls" has no cover tile so it falls away. If summary was somehow
    // dropped, "main" is prepended so the cover screen always has a home tile.
    // Memoized on exactly the state slices the predicate reads, so this mapNotNull +
    // list concat doesn't re-run on every unrelated state emission (CompactCar takes
    // the whole UiState, so it recomposes on any change — refresh/pending/message/etc).
    val hasBattery = state.hasBattery(v)
    val tiles = remember(state.sectionOrders[v.vin], hasBattery, state.aiEnabled, isGen5W, state.hiddenPebbles) {
        state.sectionsFor(v).mapNotNull { section ->
            when (section) {
                "summary" -> "main"
                else -> section.takeIf {
                    it in CompactKnownTiles &&
                        // Cover-screen-only gate, and the reason isSectionAvailable
                        // does not carry it: everywhere else SinglePebble falls back to
                        // a FuelPebble for a car with no battery, so "charge" still has
                        // something to render. The cover has no such fallback tile.
                        (it != "charge" || hasBattery) &&
                        state.isSectionAvailable(v, it)
                }
            }
        }.let { ordered -> if ("main" in ordered) ordered else listOf("main") + ordered }
    }
    // Infinite wrap-around: start in the middle of a huge virtual range and map
    // each virtual page back onto a real tile with modulo. FLAT tiles -- unlike
    // the three horizontal car pagers this VerticalPager gets NO pagerDepth and
    // NO beyondViewportPageCount.
    val vWrap = rememberWrapPager(tiles.size)
    val vPager = vWrap.pager
    val current = vWrap.currentReal
    LaunchedEffect(current, tiles) { onTileChange(tiles.getOrElse(current) { "main" }) }
    // Per-tile scroll states, keyed by tile name so position persists across
    // pager recycling AND reordering. Tall tiles scroll their own content; the
    // VerticalPager then nested-scrolls to the next/previous tile once a tile is
    // scrolled to its edge.
    val tileScrollStates = remember { mutableMapOf<String, ScrollState>() }
    // Suspend native tile paging while the right-rail scrubber is driving the
    // pager, so a scrub drag can't also be read as a page swipe.
    val coverScrubbing = LocalCoverScrubbing.current

    val density = LocalDensity.current
    // NOTE: nothing here reads the display cutout's boundingRects any more, which
    // is what this note is actually about -- it used to say "nothing here reads the
    // display cutout", flatly, which is not true and sends anyone chasing a
    // cover-screen bump problem to the wrong place. The hand-rolled per-edge
    // CLEARANCE math went first, and the decorative ring that was the last
    // remaining rects reader has now gone too (see where it used to be drawn).
    // Cutout avoidance is still very much present, just native and declarative:
    // the tile Box below takes the scaffold's merged nav-bar-union-cutout inset,
    // and the scrubber rail takes WindowInsets.displayCutout on its End side only.
    // Both are corner-safe and recomposition-aware, which the rects math was not.

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
                    // Only arm the edge-trace when the press starts near a screen
                    // EDGE — that's the whole metaphor ("trace around the rim"). It
                    // used to arm on ANY press anywhere, so a slow/stationary press on
                    // a center control (the DC-limit slider, a climate button) both
                    // flickered the ring on and, if held >1.2s, fired an unintended
                    // vm.refreshStatus. Requiring an edge start makes it intentional
                    // and stops it stealing center interactions.
                    val edgeMarginPx = with(density) { 40.dp.toPx() }
                    val nearEdge = down.position.x <= edgeMarginPx ||
                        down.position.x >= size.width - edgeMarginPx ||
                        down.position.y <= edgeMarginPx ||
                        down.position.y >= size.height - edgeMarginPx
                    if (!nearEdge) return@awaitEachGesture
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
      CoverScaffold(reserveRailGutter = tiles.size > 1) { metrics ->
        VerticalPager(
            state = vPager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = coverScrubbing?.value != true,
        ) { page ->
            val i = vWrap.real(page)
            val tileScroll = tileScrollStates.getOrPut(tiles[i]) { ScrollState(0) }
            CompositionLocalProvider(
                LocalForceExpanded provides true,
                LocalPebbleFillHeight provides true,
                LocalCoverScrollState provides tileScroll,
            ) {
                // ONE merged inset from the scaffold (nav bar ∪ cutout ∪ corner-safe
                // camera-bump ∪ base gutter, max()'d per edge) — replaces the old
                // three-layer additive stack that double-reserved the bump and
                // crammed content into the left half.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(metrics.contentPadding),
                ) {
                    // The cover reuses the phone's pebble CARDS, rendered under the
                    // LocalForceExpanded/PebbleFillHeight/CoverScrollState providers so
                    // each pebble draws as an always-expanded, header-less, height-
                    // filling scrolling card (its cover glance-hero branch). The tile
                    // list renames "summary" -> "main", so map it back for SinglePebble.
                    // The home tile is the cover's own combined layout, not
                    // the phone's photo-first HeroHeader -- see CoverMainTile.
                    if (tiles[i] == "main") {
                        CoverMainTile(v, state, vm)
                    } else {
                        SinglePebble(tiles[i], v, state, vm, Modifier)
                    }
                }
            }
        }
      }
        // The decorative camera ring that used to be drawn here has been
        // removed. It assumed the display cutout was a small circular
        // punch-hole and derived its radius from `cutout.width() / 2`, but a
        // flip cover screen reports the whole camera ISLAND as one bounding
        // rect -- so instead of tracing a lens it swept an enormous faint
        // circle across the panel, well outside the cameras it was meant to
        // acknowledge. Reported from a real device.
        //
        // Not re-fitted to the island shape: the rect is a bounding box, not
        // the real outline, so anything drawn from it is a guess at hardware
        // geometry that varies per device. It was purely cosmetic and load-
        // bearing for nothing (content padding comes from the native
        // WindowInsets.displayCutout on the tile Box above), so the honest
        // fix is to stop drawing it rather than to keep guessing.
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
                        inset, inset, size.width - inset, size.height - inset
                    )
                    // Trace the actual RECTANGULAR (rounded) screen perimeter, not an
                    // ellipse. The old code called drawArc on this full-screen rect,
                    // which draws an arc of the ELLIPSE inscribed in it — a huge oval
                    // bulging far past the visible edges (the "giant blue circle" in
                    // the screenshots). Instead, build the rounded-rect perimeter as a
                    // Path and take the first `progress` fraction of its length via
                    // PathMeasure.getSegment, so a thin stroke grows clockwise hugging
                    // the real edge.
                    val corner = with(density) { 28.dp.toPx() }
                    val perimeter = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                rect,
                                androidx.compose.ui.geometry.CornerRadius(corner, corner),
                            )
                        )
                    }
                    val measure = androidx.compose.ui.graphics.PathMeasure().apply {
                        setPath(perimeter, false)
                    }
                    val traced = androidx.compose.ui.graphics.Path()
                    measure.getSegment(
                        0f,
                        measure.length * edgeTraceProgress.value.coerceIn(0f, 1f),
                        traced,
                        true,
                    )
                    drawPath(
                        path = traced,
                        color = accent.copy(alpha = edgeTraceProgress.value.coerceIn(0f, 1f) * 0.85f),
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
                    vWrap.snapToReal(targetTile)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Clear a right-edge / bottom-right-corner camera bump: the
                    // scrubber sits flush to the physical right edge, so on a device
                    // whose cutout intrudes from the right it used to sit under the
                    // bump. Native displayCutout (End side only) floats it inboard;
                    // it's a no-op when the cutout doesn't touch the right edge.
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                    .padding(end = 6.dp)
                    .graphicsLayer { alpha = dotsAlphaState.value }
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
    // `current` closed over by a long-lived gesture coroutine, not read fresh each
    // gesture: Modifier.pointerInput(count) below only cancels-and-relaunches its
    // block when `count` (tiles.size) changes, not when `current` does -- ordinary
    // tile swipes change neither, and this composable's own car page survives many
    // of them (the outer car HorizontalPager keeps a neighbour alive via
    // beyondViewportPageCount). So the coroutine launched once, captured whatever
    // `current` was at that moment, and every long-press afterward -- regardless
    // of which tile was actually showing by then -- started the scrub from that
    // one frozen value. Reported as "hold the rail and it resets you to [a fixed]
    // page". rememberUpdatedState is the standard fix for exactly this: the
    // coroutine still only restarts on `count` changing, but reads .value fresh
    // on every gesture instead of the parameter it closed over at launch.
    val currentState = rememberUpdatedState(current)
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

    // Resting paddings/spacing bumped up (was 6/10/6) so the rail is a more
    // comfortable thumb target on the cover — the previous ~19dp-wide sliver was a
    // fifth of the app's 48dp min target. The invisible gesture Box already spans
    // 48dp wide (below); this widens the VISIBLE rail so it reads as tappable too.
    val hPad by animateDpAsState(if (scrubbing) 18.dp else 9.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubHPad")
    val vPad by animateDpAsState(if (scrubbing) 18.dp else 12.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), "scrubVPad")
    val itemSpacing by animateDpAsState(if (scrubbing) 14.dp else 8.dp,
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
                    scrubStartPage = currentState.value
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
                        if (highlight) 28.dp else 9.dp,
                        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                        label = "vdotH",
                    )
                    val dotW by animateDpAsState(
                        if (scrubbing) 10.dp else 9.dp,
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

// The cover reuses the phone's pebble CARDS: CompactCar's vertical tile pager
// renders SinglePebble(section) under LocalForceExpanded/PebbleFillHeight/
// CoverScrollState, so each pebble draws as an always-expanded, height-filling
// card. (The bespoke CoverTile toolkit + Cover*Tile faces were removed — they
// looked off-brand; the cover is back to the polished pebble-card design.)

/**
 * A soft blurred scrim behind the status bar so scrolling content underneath
 * (a car photo, Aurora, dense text) doesn't fight the system clock/battery
 * icons drawn on top of it. Not the normal (non-cover-screen) layouts -- the
 * cover screen already reserves real space above its content instead of
 * drawing under the status bar at all, so it has nothing to scrim.
 */
@Composable
internal fun StatusBarScrim() {
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
internal fun FloatingIcon(
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
/**
 * Reads `pager.currentPage` INSIDE its own restartable composable scope.
 *
 * This exists for one reason, and it was the single worst frame-stall in the
 * app. Every call site put `PagerDots(current = real(pager.currentPage))` in a
 * `Box` — and `Box` is an INLINE composable, so it is not its own recomposition
 * scope. The nearest restartable scope was the one that also contains the
 * sibling `HorizontalPager` call. `currentPage` flips the instant a drag crosses
 * the halfway point — i.e. at peak finger velocity — so that flip invalidated
 * the whole scope, re-invoked HorizontalPager with a freshly allocated content
 * lambda, and recomposed EVERY live page: three cars' full pebble columns, ~30
 * pebbles, in one frame, in the middle of every single swipe.
 *
 * Reading it one level down confines the invalidation to the dots. Keep the read
 * in here — hoisting it back to the call site silently restores the stall.
 */
@Composable
private fun PagerDotsFor(
    pager: PagerState,
    count: Int,
    real: (Int) -> Int,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
) = PagerDots(current = real(pager.currentPage), count = count, modifier = modifier, onRefresh = onRefresh)

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
    /** Whether the photo box is showing. Passed IN rather than collected from the
     *  view model here: this composable already has `vm`, but subscribing to state
     *  inside it would recompose the whole hero on every unrelated state change, and
     *  both call sites already hold the UiState they would read it from. */
    photoExpanded: Boolean = true,
) {
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    // Play the fade/slide-up entrance only ONCE per car per session, gated on the
    // same coldStartIntroPlayed set the pebble stagger uses. Previously this was an
    // unconditional LaunchedEffect(Unit) that replayed on EVERY (re)composition of
    // this hero — including when a swiped-away page is disposed and later recomposed
    // (or, now that the car pager pre-composes a neighbour via
    // beyondViewportPageCount=1, when that neighbour composes off-screen). Replaying
    // the fade on each enter added animation frames on top of the page's compose
    // burst mid-swipe. Once-per-VIN means a page that re-enters snaps straight to
    // rest instead of re-animating.
    val playIntro = remember(v.vin) { coldStartIntroPlayed.add("hero:${v.vin}") }
    val heroAlpha = remember { Animatable(if (playIntro) 0f else 1f) }
    val heroOffset = remember { Animatable(if (playIntro) 16f else 0f) }
    LaunchedEffect(v.vin) {
        if (!playIntro) return@LaunchedEffect
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
    // On the PHONE, match the EXPANDED pebble corner (PebbleCornerExpanded = 20dp) —
    // the hero reads as an always-expanded card, so it should share the tighter
    // expanded radius, not the rounder collapsed one. The old hardcoded charging 40dp /
    // idle 24dp both mismatched. The COVER keeps its own animated corner (full-height tile).
    val heroShape = RoundedCornerShape(if (LocalForceExpanded.current) corner else PebbleCornerExpanded)
    val heroOutline = LocalAppearance.current
    // On the flip cover this hero is one full-screen tile. Unlike every other
    // pebble it rolls its own Card and never went through PebbleShell, so it never
    // got the cover's fill-height treatment — it wrapped its content and left a
    // dead gradient box (no photo) plus a black void below. When on the cover, fill
    // the tile height, centre the content, and drop the empty photo box entirely.
    val cover = LocalForceExpanded.current
    if (!cover) {
        // On the phone the hero IS a pebble now, built on the same PebbleShell as every
        // other one: header with icon, title, summary and the standard chevron, and a body
        // that collapses with the shared collapseEnter/collapseExit transition.
        //
        // This replaces a bespoke Card with a MorphExpandButton bolted beside the charge
        // bar. That version worked, but it was a card that looked like a pebble and
        // collapsed like a pebble while sharing none of the mechanism -- so it
        // re-implemented the shadow, outline, corner, drag-handle plumbing and toggle
        // placement, and would have drifted from the real pebbles the first time any of
        // those changed.
        //
        // The photo needs no collapse logic of its own any more either: PebbleShell hides
        // the whole body when collapsed, so "no image when collapsed" falls out of the
        // shared component instead of being a rule this function enforces.
        //
        // Derived ONCE, here, and handed to both densities. The collapsed line and the
        // expanded block used to work the percentage, the range and the charging state out
        // separately -- same inputs, two derivations, and therefore two things to keep in
        // step by hand.
        val readout = chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric)
        // 0 collapsed, 1 expanded. The ONE value the readout's morph runs on: type sizes,
        // gaps, paddings and the header's reservation all lerp on it, so they cannot get out
        // of step with each other the way separate transitions did.
        //
        // Critically damped and terminating on a real threshold, for the same reason the
        // discarded bounds spring needed it: this drives a SIZE, and the theme's spatial
        // spring is under-damped by design, so type would overshoot past its target size and
        // spring back. Text that overshoots reads as a wobble, not as liveliness.
        val heroT by animateFloatAsState(
            targetValue = if (photoExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
            label = "heroMorph",
        )

        // The status line's ("Parked"/"Charging...") own fade, on its OWN clock rather than
        // heroT: originally delayed a full 500ms (requested as "half a second longer
        // before it fades in" -- the line was reading as arriving too eagerly, at the
        // same moment the card itself starts opening) and then reported as too long a
        // wait once that shipped, so trimmed to 250ms -- still a real, deliberate beat
        // after the card starts opening rather than simultaneous with it, just not a
        // hang. Delayed only going IN (photoExpanded true); collapsing fades it out
        // immediately, so the card doesn't look like it's still finishing an entrance
        // while it closes. This is a real wall-clock delay (tween + delayMillis), not a
        // fraction of heroT, because heroT is spring-driven with no fixed duration to
        // carve a fraction out of. By the time it starts, the height reveal (still on
        // heroT) has long since finished even at this shorter delay, so there's no
        // repeat of the clip-vs-alpha mismatch fixed just before this -- the slot is
        // already fully sized and the text just fades into it cleanly.
        // durationMillis raised from 200 to 350: reported as not fading in at all after
        // the delay was trimmed, and 200ms is short enough on a real device's frame
        // pacing to read as a snap rather than a fade, especially right after a 250ms
        // wait primes the eye to expect a discrete change. 350ms is closer to what the
        // original 500ms-delay version's own fadeIn spec would have taken to settle,
        // just without the long wait in front of it.
        val statusAlpha by animateFloatAsState(
            targetValue = if (photoExpanded) 1f else 0f,
            animationSpec = tween(
                durationMillis = 350,
                delayMillis = if (photoExpanded) 250 else 0,
            ),
            label = "heroStatusFade",
        )

        // ---- The travelling numbers -------------------------------------------
        //
        // ONE instance of the percentage and range, drawn by the overlay below and
        // positioned by MEASUREMENT rather than arithmetic.
        //
        // The two ends are laid out by the things that already know where they go:
        // the title Row puts the collapsed numbers beside the car name, and the
        // readout puts the expanded ones at the card's lower-left. Both keep doing
        // exactly that -- they simply stop painting and report their position
        // instead. Six earlier attempts computed the collapsed position by hand (a
        // bottom anchor, a derived lift, the measured title width, a type-step
        // ratio) and each landed slightly off, the last of them printing the
        // numbers above the name. A Row places its children correctly by
        // construction; the trick is to read that placement rather than reproduce it.
        //
        // Both anchors report in the CARD's coordinate space, so the overlay's
        // offset is a plain lerp between two points in the same space.
        val cardCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
        // Position AND width: the overlay needs the width to know how far apart to
        // push the percentage and the range. Collapsed that width is the natural
        // content width, so nothing moves; expanded it is the readout's full span,
        // which is what puts the range on the right.
        val collapsedNumbers = remember { mutableStateOf<Rect?>(null) }
        val expandedNumbers = remember { mutableStateOf<Rect?>(null) }
        // Until BOTH ends have been measured there is nothing to interpolate
        // between, so the two anchors paint themselves and the card looks exactly
        // as it did before. That makes the first frame correct rather than blank,
        // and a measurement that never arrives degrade to the old crossfade instead
        // of losing the numbers entirely.
        val hoisted = cardCoords.value != null &&
            collapsedNumbers.value != null && expandedNumbers.value != null
        fun report(into: androidx.compose.runtime.MutableState<Rect?>) =
            { coords: LayoutCoordinates ->
                val card = cardCoords.value
                if (card != null && coords.isAttached) {
                    val origin = card.localPositionOf(coords, Offset.Zero)
                    into.value = Rect(
                        origin,
                        androidx.compose.ui.geometry.Size(
                            coords.size.width.toFloat(),
                            coords.size.height.toFloat(),
                        ),
                    )
                }
            }

        PebbleShell(
            expanded = photoExpanded,
            onToggle = { vm.togglePebble(v, com.bloo.bluelink.data.HERO_PHOTO_SECTION) },
            icon = Icons.Filled.DirectionsCar,
            title = v.name,
            vm = vm,
            dragHandle = dragHandle,
            // Follows the morph rather than switching: the photo fades in over the same
            // t, so the name has to travel from the surface's own colour to the light one
            // the scrim is built for. Snapping at a threshold would flash a white name
            // onto a still-white card for the frames before the photo arrives.
            titleColor = lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
            // The ONLY pebble that grows its title. Here the title is the car's NAME and the
            // card becomes a photo of that car, so the name scaling up reads as the card taking
            // over. On "Location" or "Diagnostics" it is a heading resizing for no reason.
            growTitleOnExpand = true,
            // No `summary` string. The bar below IS the summary now, and it is the real
            // one -- restating "82% - 241 mi" as header text beside a bar showing the same
            // thing is how the same numbers get rendered twice and then drift, which is a
            // bug I already had to fix on the widget's MEDIUM tiers.
            // The photo is the card's BACKGROUND now, not a body child, so it runs up
            // behind the header row and the title and chevron overlay its top. Collapsing
            // it is the same shared transition as before -- the only change is which layer
            // it lives on.
            background = {
                // The card's own coordinate space, captured once. Both anchors
                // convert into this, so the overlay's lerp is between two points
                // in one space rather than a mix of window and local offsets --
                // which is the way this goes wrong silently, by landing the
                // numbers off the card entirely.
                Spacer(
                    Modifier
                        .matchParentSize()
                        .onGloballyPositioned { cardCoords.value = it },
                )
                // Captured here (composable context) rather than inside the slide
                // transitions' offset lambdas below, which run outside composition.
                val heroPhotoDensity = LocalDensity.current
                AnimatedVisibility(
                    visible = photoExpanded,
                    // The shared collapse spec (fade + the container's own height reveal)
                    // PLUS a slide-and-settle for the photo itself. This used to be a
                    // scaleIn/Out from 92%/94% on the same non-bouncy spec the container's
                    // own height uses -- an 8% scale change finishing at the same rate as
                    // the reveal it rides inside reads as the photo simply FILLING IN as
                    // the card grows, not as an object arriving on its own. Reported as
                    // "pops in" from a real device.
                    //
                    // The entrance spring is deliberately UNDER-damped
                    // (Spring.DampingRatioLowBouncy < 1): it overshoots its target and
                    // settles back, which is what makes this a bounce and not just a
                    // faster ease. The exit stays on the non-bouncy default spec --
                    // a bounce reads as arrival, not departure; overshooting on the
                    // way OUT would look like the photo hesitating before it leaves.
                    // slideInVertically travels a real distance (HeroPhotoSlideDistance)
                    // rather than a subtle scale nudge, so the photo visibly arrives FROM
                    // somewhere instead of blooming in place. Scale rides the same spring
                    // as the slide on each side, so the two read as one physical motion
                    // rather than two differently-timed effects layered on top of each
                    // other.
                    //
                    // Only the hero does this. A pebble body sliding open is content
                    // appearing; a photo is an object, and objects arrive and settle.
                    //
                    // slideInVertically's offset lambda runs outside composition (it's
                    // called by the animation, not composed), so the px distance is
                    // converted with a plain captured Density rather than
                    // LocalDensity.current inside the lambda.
                    enter = collapseEnter() +
                        slideInVertically(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) { with(heroPhotoDensity) { -HeroPhotoSlideDistance.roundToPx() } } +
                        scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ),
                    exit = collapseExit() +
                        slideOutVertically(
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ) { with(heroPhotoDensity) { -HeroPhotoSlideDistance.roundToPx() } } +
                        scaleOut(
                            targetScale = 0.9f,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                ) {
                    HeroPhotoBackdrop(v, imageUrl, height, aspectRatio = 16f / 9f)
                }
                // The expanded readout, at the BOTTOM of the card.
                //
                // A SIBLING of the photo, not a child of it. As a child it inherited the
                // photo's scaleIn/scaleOut settle, so the numbers and the bar zoomed with the
                // image -- wrong for text, which should arrive rather than being flown in.
                // Split, the photo settles as an object and the readout just closes with it.
                //
                // Aligned within the card's own Box rather than placed in the pebble body:
                // the body is top-aligned in its Column, so a bar there sits under the
                // header, and pushing it down would need the Column to fillMaxHeight inside a
                // Box whose own height comes from a sibling -- which in a scrollable parent
                // (maxHeight = Infinity) is exactly how you get a bad measure. Aligning has
                // no such dependency.
                // THE readout. One instance, both states, morphing between them.
                //
                // Bottom-anchored and deliberately NOT wrapped in an AnimatedVisibility,
                // because there is nothing to show or hide any more -- this node exists in
                // both states. That also retires the footprint bug this slot used to have: a
                // fade-only AnimatedVisibility held its full ~142dp for the whole fade and
                // then dropped it in one frame, which was the "hangs at the wrong size, then
                // snaps". A node that never leaves cannot strand a footprint.
                //
                // The TRAVEL is free. The photo above is already animating the card's height,
                // so anchoring here rides that change from the header down to the base of the
                // photo with no bounds animation at all. `heroT` drives only the SIZE morph.
                // That is what three attempts with `sharedBounds` were doing the hard way --
                // see HeroMorphReadout.
                //
                // The paddings lerp, which is what widens the bar: collapsed it stops short
                // of the chevron, expanded it runs the card's full width.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        // These three insets are DERIVED from the header's own geometry, not
                        // picked. Collapsed, this node has to land exactly in the slot the
                        // header reserved for it, and my first numbers did not -- the
                        // percentage sat on top of the car icon and clipped the title's
                        // descenders, because the readout is positioned against the CARD while
                        // the reserve lives inside the header's TEXT COLUMN. Two coordinate
                        // systems, and I had not made them agree.
                        //
                        // The header (PebbleShell) is: padding(horizontal = 16, vertical = 6),
                        // Icon(20), Spacer(10), then the weighted text column. So:
                        //
                        //  start  16 + 20 + 10 = 46dp -- the text column's left edge, so the
                        //         percentage lines up under the car NAME instead of over the
                        //         icon. Expanded there is no icon to clear, so 16dp.
                        //  end    the chevron is ~48dp inside the row's own 16dp padding, so
                        //         76dp leaves it clear with a small optical gap. This was 64dp,
                        //         which is why the bar ran under the chevron.
                        //  bottom  Derived, not tuned. The readout is bottom-anchored in the
                        //          card's Box, and the header reserves
                        //          collapsedReadoutHeight + HeroReadoutBottomInset for it, so
                        //          the two line up by construction rather than by a pixel budget
                        //          that has to be re-checked whenever the type changes.
                        //
                        //          The comment removed from here did a hand arithmetic proof
                        //          ("title occupies y 6..30 and the reserve y 30..70, this node
                        //          is 40dp tall") against a 40dp reserve. The code beside it
                        //          reserved 4.dp + ChargeBarHeight = 22dp. Whichever was once
                        //          true, they had stopped agreeing, which is exactly the failure
                        //          a derived value removes.
                        .padding(
                            // Clears the car icon, and NOTHING more. Putting the name's width in
                            // here pushed the whole Column across -- including the BAR, which
                            // then started under the percentage instead of spanning the card.
                            // The name-clearing offset belongs to the numbers Row alone; it is
                            // passed to HeroMorphReadout as `numbersStart` below.
                            start = lerp(46.dp, 16.dp, heroT),
                            end = lerp(76.dp, 16.dp, heroT),
                            bottom = lerp(HeroReadoutBottomInset, 16.dp, heroT),
                        ),
                ) {
                    // Same travel as the title: this readout sits ON the photo once the
                    // card is open, and it reads LocalContentColor, so without this the
                    // percentage, range and state line were near-black on a dark image
                    // exactly as the name was. One provider covers all three.
                    CompositionLocalProvider(
                        LocalContentColor provides
                            lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
                    ) {
                        HeroMorphReadout(
                            readout,
                            heroT,
                            onNumbersPositioned = report(expandedNumbers),
                            numbersHoisted = hoisted,
                            statusAlpha = statusAlpha,
                            // Collapsed, the numbers start after the name; expanded, they own the
                            // left edge. Only this Row shifts -- the bar underneath does not.
                            // Zero: this copy only ever shows EXPANDED, where it owns the card's
                            // lower-left. The collapsed numbers are the header's, so nothing here has
                            // to be offset past the car name any more -- which also retires the
                            // measured-title-width plumbing that offset needed.
                            numbersStart = 0.dp,
                        )
                    }
                }
                // THE numbers. One instance, travelling between the two anchors --
                // and the travel is a plain lerp because both anchors are points in
                // this same Box's space.
                //
                // A two-phase easing (height drops first, width/x held then released on
                // a curve that overshoots past its target) was tried here and reverted:
                // width is what HeroNumbers uses to size its Row, but the ROW's own text
                // size scales with heroT directly, not with width's easing. Holding width
                // at the narrow collapsed value while heroT (and so the type size) kept
                // advancing meant the range text grew past what its still-small width
                // could fit for part of the transition -- "mi" briefly shrank to "m..."
                // before width caught up and it reflowed back. A plain lerp keeps width
                // and type size moving together in lockstep, which is what avoids that.
                val from = collapsedNumbers.value
                val to = expandedNumbers.value
                if (hoisted && from != null && to != null) {
                    val x = androidx.compose.ui.util.lerp(from.left, to.left, heroT)
                    val y = androidx.compose.ui.util.lerp(from.top, to.top, heroT)
                    val w = androidx.compose.ui.util.lerp(from.width, to.width, heroT)
                    Box(
                        Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides
                                lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT),
                        ) {
                            HeroNumbers(
                                readout, heroT,
                                width = with(LocalDensity.current) { w.toDp() },
                                statusAlpha = statusAlpha,
                            )
                        }
                    }
                }
            },
            // Collapsed: name, percentage and range on ONE row, with the bar directly under
            // it. Two rows reads as a status line with a gauge under it, which is what it is.
            //
            // Collapsed: name, percentage and range on ONE row, with the bar under it.
            //
            // NOT a shared element, and this time the reason is measured rather than
            // guessed. `sharedBounds` requires a `SharedTransitionLayout`, which is a
            // LookaheadScope, so the hero's subtree runs an extra lookahead measure/place
            // pass every time it is placed -- and a pager drag re-places every page on every
            // frame, on all three pages beyondViewportPageCount keeps live. (Verified in the
            // resolved artifact: SharedBoundsNode implements ApproachLayoutModifierNode.)
            //
            // That is exactly why the flip cover's swipe was smooth while the phone's was
            // not: PebbleShell returns through CoverTile BEFORE it ever creates the scope, so
            // the cover path never pays for this at all. A travelling charge bar is not worth
            // the one gesture the user makes most.
            //
            // The original ask -- the percentage and range rendered twice -- stays fixed, and
            // at the level that actually mattered: ONE [ChargeReadout] derivation feeds both
            // densities, so they cannot drift and only one is ever on screen.
            // Both collapsed slots stay NON-NULL and gate with AnimatedVisibility inside.
            // `if (photoExpanded) null else { … }` deletes the node on the frame the pebble
            // opens, so there is nothing left to play an exit -- which is why these two
            // popped in and out with no animation at all after the shared element came out.
            //
            // No shared element here, deliberately. The travel needed a
            // SharedTransitionLayout, which is a LookaheadScope, and that is what cost the
            // car-swipe frames (see 3cc327a). An animated collapse does not need one: these
            // are ordinary enter/exit transitions on the two nodes, which participate in
            // layout exactly once per frame like everything else.
            // ROW 1 of the collapsed card: the percentage and range, on the car name's own line.
            //
            // In the header's own Row rather than positioned by me. Six attempts to compute this
            // inset -- a bottom anchor, a derived lift, the measured title width, the type-step
            // ratio -- each landed slightly off, the last of them printing the numbers ABOVE the
            // name. A Row aligns its children by construction, which is the whole reason this slot
            // exists; its KDoc named the hero as the user while nothing used it.
            //
            // Yes, this means the NUMBERS have two instances (this one and the expanded copy in
            // [HeroMorphReadout]) -- the honest cost of layout-instead-of-arithmetic. The charge
            // BAR is still a single instance, which was the part worth protecting. And the
            // roughness the two-copy version originally had came from both being visible at
            // similar opacity: this one is gone by t = 0.35 and the expanded copy starts appearing
            // there, so they never overlap.
            // Kept alive past 0.35 once hoisted: it is the collapsed ANCHOR then, and
            // an anchor that is removed stops reporting, which would strand the
            // overlay at its last known point.
            titleTrailing = if (heroT > 0.35f && !hoisted) null else {
                {
                    HeroCollapsedNumbers(
                        readout, heroT,
                        onPositioned = report(collapsedNumbers),
                        hoisted = hoisted,
                    )
                }
            },
            summary = null,
            headerContent = {
                // A RESERVATION, not content. The bar itself lives in the one readout at the
                // bottom of the card; this only stops the header's text column from sitting on
                // top of it while the card is short.
                //
                // Derived from the same tokens the readout composes with, not picked: its
                // collapsed height is the pct line (titleMedium) plus the inter-row gap plus
                // the bar. Choosing a number here instead of deriving it is how this slot
                // produced a mismatch every time it was a constant -- the deleted
                // heroReadoutReserve() was exactly that, and the tombstone above says so.
                // TextUnit.toDp() THROWS on an Unspecified or Em value, so this depends on
                // titleMedium keeping an sp lineHeight. It does: expressiveTypography() builds
                // from Typography() and `.copy(fontFamily, fontWeight)` only, so the default
                // 24.sp survives. Checked rather than assumed, because the failure would be a
                // crash in the hero rather than a layout being a few dp out. If a future
                // typography ever sets lineHeight = TextUnit.Unspecified, guard this.
                // The BAR only -- deliberately NOT the numbers row above it.
                //
                // Reserving the readout's whole height pushed it clear of the title and the
                // collapsed pill became THREE rows: name / numbers / bar. It must be two: name
                // and numbers sharing one row, bar underneath. The numbers row is the same
                // height as the title (both titleMedium), so reserving only what sits BELOW it
                // lets the bottom-anchored readout land its numbers on the title's own row.
                val collapsedReadoutHeight = 2.dp + ChargeBarHeight
                // + the readout's own bottom inset. The readout occupies
                // collapsedReadoutHeight of CONTENT and then sits HeroReadoutBottomInset above
                // the card's edge, so reserving only the content left the reservation one gap
                // short and the readout's top edge crossed into the title's row.
                val h = lerp(collapsedReadoutHeight + HeroReadoutBottomInset, 0.dp, heroT)
                // No graphicsLayer: there is nothing here to fade any more. An alpha on an
                // empty Box is a layer allocation per frame for no pixels.
                Spacer(Modifier.fillMaxWidth().height(h))
            },
        ) {
            // Empty by design. Everything the expanded state adds -- the photo and the
            // readout over its lower edge -- is in `background`, because both need to be
            // positioned against the IMAGE rather than stacked under the header.
            Spacer(Modifier.height(0.dp))
        }
        return
    }
    // Unreachable from here down: `cover` (LocalForceExpanded) is true only on the
    // flip cover, and the cover's home page no longer calls HeroHeader at all --
    // CoverMainTile replaced it, including this branch's own photo-as-background
    // treatment (see CoverMainTile's own doc for where that logic lives now). Left
    // as a `return` above rather than restructuring this already-long function to
    // drop the `if`, so the diff that orphaned this branch stays easy to find in
    // history if that's ever in question.
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
internal fun UpdateAvailableTile(state: UiState, vm: AppViewModel, dragHandle: Modifier = Modifier) {
    val info = state.updateAvailable
    // Stays visible during the pending-dismiss (undo) window — only the committed
    // updateTileDismissed truly hides it.
    AnimatedVisibility(
        visible = info != null && !state.updateTileDismissed,
        enter = collapseEnter(Alignment.Bottom),
        exit = collapseExit(Alignment.Bottom),
    ) {
        if (info == null) return@AnimatedVisibility
        val context = LocalContext.current
        // Download progress is collected from its own StateFlow rather than read off UiState,
        // so a per-chunk tick invalidates only this tile's bar/percent, not every pebble on the
        // live pager pages. state.updateDownloading (the boolean that gates the display below)
        // stays on UiState -- it changes twice per download, not hundreds of times.
        val downloadProgress by vm.updateDownloadProgress.collectAsState()
        val hasDirectDownload = info.run.phoneApkUrl != null
        val current = vm.currentBuildNumber
        // Build delta: "build 812 → build 828" when we know the installed build,
        // else just the target. buildLabel is the one canonical version formatter.
        val newLabel = com.bloo.bluelink.data.buildLabel(info.run.runNumber)
        val deltaLabel = if (current > 0) {
            "${com.bloo.bluelink.data.buildLabel(current)} → $newLabel"
        } else {
            newLabel
        }
        val seamless = LocalAppearance.current.seamlessInstallShizuku && state.shizukuAvailable
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
            summary = info.run.displayTitle?.takeIf { it.isNotBlank() } ?: deltaLabel,
            // No containerColor override -- PebbleShell's own default
            // (surfaceVariant) is what every ordinary pebble uses too
            // (Climate, Charge, Info, ...); this used primaryContainer,
            // which read as a special/different-looking tile instead of
            // fitting in with the rest of the per-car stack. AI's pebble is
            // the one deliberate exception (tertiaryContainer) -- this
            // wasn't meant to be another one.
            headerAction = PebbleHeaderAction(
                label = when {
                    state.updateInstalling -> "Installing…"
                    state.updateDownloading -> downloadProgress?.let { "${(it * 100).roundToInt()}%" } ?: "Downloading…"
                    state.updateApkReady -> if (seamless) "Install now" else "Install"
                    hasDirectDownload -> "Update"
                    else -> "Open"
                },
                icon = if (state.updateApkReady) Icons.Filled.SystemUpdate else Icons.Filled.Download,
                pending = state.updateDownloading || state.updateInstalling,
                enabled = !state.updateInstalling,
                // Same ChargeGreen/white pairing ChargePebble's own headerAction
                // uses for its "charging" active state -- this button used to stay
                // the same neutral, low-contrast default container/text regardless
                // of state, so the one moment this tile has a real "tap this now"
                // call to action (the download finished, install is one tap away)
                // looked identical to every other, less urgent state.
                active = state.updateApkReady,
                activeContainer = ChargeGreen,
                activeContent = Color.White,
                onClick = {
                    when {
                        state.updateApkReady -> vm.installDownloadedUpdate()
                        hasDirectDownload -> vm.downloadUpdateInBackground()
                        else -> {
                            // Dismiss ONLY if the page really opened. Swallowing an
                            // ActivityNotFoundException and dismissing anyway meant a
                            // tap did visibly nothing AND cost the user the tile.
                            val opened = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl)))
                            }.isSuccess
                            if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                        }
                    }
                },
            ),
        ) {
            val scheme = MaterialTheme.colorScheme
            // ONE state-driven status line (icon + text), replacing the old duplicated
            // delta row + scattered downloading/seamless/installing rows. The build
            // delta already lives in the header summary; here we say what's happening
            // NOW. Ready uses ChargeGreen as a success tick; everything else stays
            // neutral (no charging-green Bolt cross-metaphor).
            //
            // statusKind, not the rendered string, is what drives the AnimatedContent below --
            // it stays "downloading" for the WHOLE download instead of becoming a new string
            // on every percentage tick, which is what used to make "Downloading 45%" slide/fade
            // out and "Downloading 46%" slide/fade in as if they were two different states:
            // the static word was animating right along with the number that actually changed.
            // Only the percent itself is a moving target now (rendered with its own
            // AnimatedValue below), and the sentence around it stays put.
            val (statusIcon, statusKind, statusTint) = when {
                state.updateInstalling -> Triple(Icons.Filled.SystemUpdate, "installing", scheme.onSurfaceVariant)
                state.updateDownloading -> Triple(Icons.Filled.Download, "downloading", scheme.onSurfaceVariant)
                state.updateApkReady && seamless -> Triple(Icons.Filled.CheckCircle, "ready_seamless", ChargeGreen)
                state.updateApkReady -> Triple(Icons.Filled.CheckCircle, "ready", ChargeGreen)
                seamless -> Triple(Icons.Filled.Bolt, "seamless", scheme.onSurfaceVariant)
                else -> Triple(Icons.Filled.SystemUpdate, "update", scheme.primary)
            }
            // Sprung, not a snap -- the tint is what carries "this got a step further along"
            // (neutral -> ChargeGreen once the APK is ready), so it gets the same treatment
            // the charge bar's own fill-colour spring does rather than cutting on one frame.
            val animatedStatusTint by androidx.compose.animation.animateColorAsState(
                targetValue = statusTint,
                animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
                label = "updateStatusTint",
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // A tonal badge behind the icon, not a bare glyph -- the same "icon gets its
                // own coloured circle" weight CoverHero gives every stat it leads with,
                // which this tile otherwise lacked next to every pebble that opens on one.
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(animatedStatusTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // AnimatedContent, not a bare Icon swap -- installing -> downloading ->
                    // ready is a real sequence of distinct states, and a plain `when` cut
                    // between their icons on one frame while everything else on this card is
                    // now springing and cascading into place.
                    AnimatedContent(
                        targetState = statusIcon,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
                        label = "updateStatusIcon",
                    ) { icon ->
                        Icon(icon, contentDescription = null, tint = animatedStatusTint, modifier = Modifier.size(20.dp))
                    }
                }
                AnimatedContent(
                    targetState = statusKind,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically { it / 3 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically { -it / 3 })
                    },
                    label = "updateStatusText",
                    modifier = Modifier.weight(1f),
                ) { kind ->
                    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    when (kind) {
                        "installing" -> Text("Installing silently via Shizuku…", style = textStyle)
                        "downloading" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Downloading", style = textStyle)
                            downloadProgress?.let { p ->
                                Text(" ", style = textStyle)
                                // Its own AnimatedValue, not part of this AnimatedContent's own
                                // string -- this is the one piece of the line that legitimately
                                // changes every tick, so it's the only piece that should move;
                                // "Downloading" itself stays a completely static Text next to it.
                                com.bloo.uicommon.AnimatedValue(
                                    "${(p * 100).roundToInt()}%",
                                    style = textStyle,
                                    reduceMotion = LocalReduceMotion.current,
                                )
                            } ?: Text("…", style = textStyle)
                        }
                        "ready_seamless" -> Text("Downloaded · installs silently via Shizuku", style = textStyle)
                        "ready" -> Text("Downloaded · tap Install", style = textStyle)
                        "seamless" -> Text("Installs silently via Shizuku, no prompts", style = textStyle)
                        else -> Text(deltaLabel, style = textStyle)
                    }
                }
            }
            // Live download progress bar. Own PopVisible rather than a bare `if` --
            // this bar arrives and leaves while the tile is already open (download
            // starts, download finishes), which is exactly the "pops in/out on its
            // own" case PopVisible exists for.
            PopVisible(visible = state.updateDownloading) {
                // A taller, fully-rounded bar in its own tonal track, with the live percent
                // riding alongside it -- the plain default-height LinearProgressIndicator
                // read as a stray system control dropped into a card that otherwise draws
                // every other number (build, delta) as its own styled readout.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val p = downloadProgress
                    // CircleShape on the Surface, not a strokeCap param on the indicator
                    // itself -- clipping the whole track to a pill gives both ends the same
                    // rounded read without depending on exactly which LinearProgressIndicator
                    // overloads this BOM happens to expose a strokeCap parameter on.
                    Surface(
                        modifier = Modifier.weight(1f).height(8.dp),
                        shape = CircleShape,
                        color = scheme.onSurface.copy(alpha = 0.12f),
                    ) {
                        if (p != null) {
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent)
                        }
                    }
                    if (p != null) {
                        com.bloo.uicommon.AnimatedValue(
                            "${(p * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            reduceMotion = LocalReduceMotion.current,
                        )
                    }
                }
            }
            // Release notes ("What's new"), capped, with a "Full notes" link to the
            // release page when there's more than we show.
            PopVisible(visible = info.run.releaseNotes != null) {
                val notes = info.run.releaseNotes.orEmpty()
                // Its own tonal card, matching the install-help disclosure just below it --
                // bare text here made the release notes read as an unstyled afterthought
                // next to that block's Surface treatment.
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surfaceContainerHighest,
                    contentColor = scheme.onSurface,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // "Full notes" rides in the section header rather than taking a
                        // whole row of its own below the excerpt — one less stacked block
                        // in a tile that already carries status, notes and two dismissals.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "What's new",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            MorphTextButton("Full notes", onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl))) }
                            })
                        }
                        Text(
                            notes.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Progressive install help: only in the tap-through (non-seamless) path, and
            // only as an opt-in disclosure — the Play-Protect steps are scaffolding, not
            // something to shout before the user has even tapped Update.
            if (!seamless) {
                var showHelp by rememberSaveable(info.run.runNumber) { mutableStateOf(false) }
                MorphTextButton(if (showHelp) "Hide install help" else "Trouble installing?", onClick = { showHelp = !showHelp })
                PopVisible(visible = showHelp) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceContainerHighest,
                        contentColor = scheme.onSurface,
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (hasDirectDownload) "1. Tap \"Update\", then \"Install\" once it downloads" else "1. Download the APK, then open it",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            // Play Protect flags any non-Play-Store APK; without this tip,
                            // "Blocked by Play Protect" reads like a real failure.
                            Text(
                                "2. If you see \"Blocked by Play Protect\", tap \"More details\" → \"Install anyway\"",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            // Dismiss / undo / remind — hierarchy: during the undo window "Keep it" is
            // the recoverable emphasis; otherwise "Remind me" (deferral) is emphasized
            // over the plainer "Not now".
            if (state.updatePendingDismiss) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dismissing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    MorphButton(
                        onClick = { vm.undoDismissUpdate() },
                        enabled = !state.updateDownloading,
                        active = true,
                    ) { Text("Keep it", fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphButton(
                        onClick = { vm.snoozeUpdate() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.updateDownloading,
                    ) { Text("Remind me") }
                    MorphTextButton("Not now", onClick = vm::dismissUpdate, enabled = !state.updateDownloading, modifier = Modifier.weight(1f))
                }
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
internal fun CarThumb(img: String?, size: Dp, cornerRadius: Dp, iconSize: Dp) {
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
internal fun rememberPhotoModel(url: String): Any =
    remember(url) { if (url.startsWith("/")) java.io.File(url) else url }

// collapseEnter / collapseExit -- the app's one collapse spec -- now live in UiTokens.kt,
// with the reasoning that goes with them. 14 call sites in this file still use them.

/**
 * The car photo plus the contrast scrim that makes text on top of it legible. ONE
 * definition, used by the phone hero's expanded background and by the flip cover's tile.
 *
 * Contrast, not decoration. Every element overlaid on the hero -- title, chevron, the whole
 * charge readout -- sits on an arbitrary car photo, and against a light car they all
 * disappear. The widget hit the same problem and solved it with a luminance check on the
 * resolved accent; a scrim is the cheap version and is what the hero does.
 *
 * The gradient covers the FULL height and never reaches transparent. An earlier version
 * scrimmed only the top strip and faded to clear by 45%, on the assumption that only the
 * header row was overlaid -- it is not, the readout is over the image too. Heaviest at the
 * top and bottom because those are the two bands that carry content (title and chevron up
 * top, the charge readout along the bottom); the middle can afford to be clear because
 * nothing sits there, which is what lets the photo still read as a photo.
 *
 * remember-ed: Brush.verticalGradient allocates a stop list, and this sits inside a card
 * that recomposes on every status change.
 *
 * [aspectRatio] null means size by [height] -- the flip cover, whose tile height is given.
 */
@Composable
private fun HeroPhotoBackdrop(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    aspectRatio: Float? = null,
    corner: Dp = PebbleCornerExpanded,
    /** See [HeroVisual.fill] -- the flip cover fills its tile. */
    fill: Boolean = false,
) {
    Box(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        HeroVisual(v, imageUrl, height, corner, aspectRatio = aspectRatio, fill = fill)
        val scrim = remember {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.30f to Color.Black.copy(alpha = 0.22f),
                0.62f to Color.Black.copy(alpha = 0.28f),
                1f to Color.Black.copy(alpha = 0.62f),
            )
        }
        Spacer(Modifier.matchParentSize().background(scrim))
    }
}

/** Default = a clean brand gradient. If the user set a photo, show that instead. */
@Composable
private fun HeroVisual(
    v: Vehicle,
    imageUrl: String?,
    height: Dp,
    corner: Dp = 18.dp,
    /** When set, size by aspect ratio instead of [height] -- 16:9 for the phone hero, so
     *  the image keeps its shape at any screen width instead of being letterboxed or
     *  cropped by a fixed dp height. */
    aspectRatio: Float? = null,
    /** Fill the parent in BOTH axes, ignoring [height] and [aspectRatio] -- the flip cover,
     *  whose tile height is the frame, so cropping to fill it is what a full-screen glance
     *  wants. Requires a bounded parent, which the cover tile is (its Card fills height). */
    fill: Boolean = false,
) {
    val sizeModifier = when {
        fill -> Modifier.fillMaxSize()
        aspectRatio != null -> Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        else -> Modifier.fillMaxWidth().height(height)
    }
    if (imageUrl.isNullOrBlank()) {
        val scheme = MaterialTheme.colorScheme
        Box(
            sizeModifier
                .clip(RoundedCornerShape(corner))
                .background(carTonalBrush(scheme)),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = rememberPhotoModel(imageUrl)
        // A transparent PNG renders edge-to-edge with no opaque box, so it blends
        // seamlessly into the pebble (fit, not crop, so the whole subject shows).
        val transparent = imageUrl.endsWith(".png", ignoreCase = true)
        // crossfade, so the car photo ARRIVES instead of popping. This is the one hero
        // element that had no animation of any kind: the pebble's collapse animates, the
        // readout's numbers roll, the bar's fill springs -- and then the photo itself
        // appeared between two frames. The map tiles below already did this; the hero,
        // the largest image in the app and the one the eye lands on first, did not.
        //
        // Coil skips the fade for memory-cache hits by design, which is exactly right
        // here: a first load fades in, but scrolling back to an already-decoded photo
        // does not re-fade, so this cannot turn into a flicker on the car pager.
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = v.model,
            contentScale = if (transparent) ContentScale.Fit else ContentScale.Crop,
            modifier = sizeModifier
                .then(if (transparent) Modifier else Modifier.clip(RoundedCornerShape(corner))),
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
private fun ChargeFuelBar(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String? = null,
    metric: Boolean = false,
) {
    // Now literally [HeroMorphReadout] held at its expanded end. There is ONE readout
    // implementation in the app, and every surface that shows this -- the hero on the phone,
    // the flip cover's tile, the EV Charge pebble -- renders that same one.
    //
    // This function had grown a near-duplicate of it: a ChargeStatsBlock with the same Row,
    // the same weighted spacer, the same two RollingNumbers at the same two type steps, then
    // the same fuel row and the same bar. Two implementations of one readout is how the
    // collapsed bar ended up silently dropping the charge-limit marker the expanded one drew,
    // and how the morph pass dropped the fuel icon this file had always had. `t = 1f` is a
    // constant, so nothing here animates -- the morph is inert at its endpoint.
    HeroMorphReadout(chargeReadoutOf(status, hasBattery, hasFuel, drivingLabel, metric), t = 1f)
}

/**
 * Everything the charge/fuel readout says, derived ONCE.
 *
 * The hero renders this readout at two densities — one line in the collapsed header,
 * the full block at the bottom of the expanded card — and until now those were two
 * independent derivations of the same numbers: two answers to "battery percentage or
 * fuel percentage", two copies of the charging > driving > plain priority order for
 * the state line, two charging-colour rules. That is this codebase's recurring class
 * of bug (a rule that exists in one place and is re-typed in another), and here it
 * had already produced a visible one — both copies on screen simultaneously,
 * disagreeing about whether to mention charging.
 *
 * Now both densities render from one of these, and only the LAYOUT differs.
 */
private class ChargeReadout(
    val pctText: String,
    val rangeText: String?,
    val statusLine: String,
    val statusColor: Color,
    val charging: Boolean,
    /** Whether the state line is worth bolding -- charging, or actually moving. Derived
     *  here rather than re-tested at the render site, which needed [drivingLabel] passed
     *  alongside a [ChargeReadout] that had already consumed it. */
    val emphasizeStatus: Boolean,
    /**
     * Target fill, 0..1. Deliberately the TARGET and not an already-animated value:
     * each site springs towards it through [animatedChargeFrac] with the same spec,
     * so the two agree at rest — and at rest is when the pebble gets toggled. Holding
     * an animating float in here instead would rebuild this object every frame.
     */
    val frac: Float,
    /** The AC/DC charge limit to mark, when plugged in and below full. */
    val limitPct: Int?,
    /**
     * The pack has reached (or passed) its own configured limit -- "topped up," not
     * "still filling." Independent of [charging]: a car reported as charged to its
     * limit stays blue on this reading even hours later, unplugged, until either the
     * percentage or the limit itself changes -- there is no live session to lose.
     */
    val stuckAtLimit: Boolean,
    /** Plug-in hybrid only: the fuel tank alongside the pack. Null when there is no
     *  tank to show, so callers need no second `hasBattery && hasFuel` test. */
    val fuelPct: Int?,
)

/** Derives the [ChargeReadout] — the single source for both densities. */
@Composable
private fun chargeReadoutOf(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String?,
    metric: Boolean,
): ChargeReadout {
    val pct = status?.percentFor(hasBattery)
    val range = status?.rangeMiFor(hasBattery)
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    // displayChargeLimit, not targetForCurrentPlug directly: the latter is null the
    // instant nothing is plugged in, which used to silently drop the whole bar back to
    // a plain unsplit track (and lose the blue "topped up" state) for every parked car
    // -- see that function's own doc. Reported from a real device.
    val limitPct = status?.evStatus?.displayChargeLimit()?.takeIf { it in 1..99 }
    // Charging time + type, shown in the badge slot (replacing parked/driving,
    // which is hidden while charging) so the pebble doesn't grow taller.
    val chargeMinutes = status?.evStatus?.minutesToFull
    val chargeType = when (status?.evStatus?.batteryPlugin) {
        1 -> "DC"
        2 -> "AC"
        else -> null
    }
    return ChargeReadout(
        pctText = pct?.let { "$it%" } ?: "--",
        rangeText = range?.let { formatDistance(it, metric) },
        // The state line under the range: charging (with time/type) replaces it while
        // charging, then driving/parked, then a plain battery/fuel descriptor.
        statusLine = when {
            charging -> buildString {
                append("Charging")
                chargeMinutes?.let { append(" · ${fmtMinutes(it)}") }
                chargeType?.let { append(" · $it") }
            }
            drivingLabel != null -> drivingLabel
            else -> if (hasBattery) "Battery" else "Fuel"
        },
        statusColor = when {
            charging -> ChargeGreen
            drivingLabel == "Driving" || drivingLabel == "Running" -> MaterialTheme.colorScheme.primary
            // MutedContentAlpha compounds with the cover's already-dim default
            // container content color (surfaceVariant -> onSurfaceVariant) the same
            // way StatusRow's label and CoverHero's subline did -- this is the idle
            // "Battery"/"Fuel" caption directly under the headline percentage on the
            // Charge/Fuel cover tile, high-visibility real estate for how washed out
            // it read.
            else -> LocalContentColor.current.copy(
                alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
            )
        },
        charging = charging,
        emphasizeStatus = charging || drivingLabel == "Driving",
        frac = ((pct ?: 0).coerceIn(0, 100)) / 100f,
        limitPct = limitPct,
        stuckAtLimit = pct != null && limitPct != null && pct >= limitPct,
        fuelPct = status?.fuelLevel?.takeIf { hasBattery && hasFuel },
    )
}

/**
 * The one spring the charge fill uses, wherever the bar is drawn.
 *
 * Expressive motion: the fill settles in with a gentle overshoot. Extracted so the
 * collapsed and expanded hero bars animate identically — two hand-copied
 * `animateFloatAsState` blocks with the same numbers is exactly the drift this
 * refactor exists to remove.
 */
@Composable
private fun animatedChargeFrac(target: Float): Float {
    val frac by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFill",
    )
    return frac
}

// ChargeStatsBlock was deleted here. It was the expanded-density readout, and it had become
// a near-duplicate of HeroMorphReadout's t = 1 end: the same Row, the same weighted spacer,
// the same two RollingNumbers at the same two type steps. ChargeFuelBar now calls
// HeroMorphReadout directly, so there is exactly ONE readout implementation serving the
// phone hero, the flip cover's tile and the EV Charge pebble.

// ChargeStatsLine was deleted here. It drew the collapsed one-line copy of the
// percentage and range for the hero's title row, and there is no collapsed copy any
// more -- HeroMorphReadout below is one set of components that morphs between both
// densities, so the second implementation has nothing left to render.

// heroReadoutReserve() was deleted here. It measured the height the collapsed header had to
// leave for an absolutely-positioned readout. The collapsed card is now two ordinary rows --
// the numbers beside the name in the header's own row, the bar under them in headerContent --
// so the header reserves the right space by CONTAINING the content instead of by computing a
// height that has to match it. That removes the class of bug this constant kept producing.

// HeroCollapsedStats() was deleted here. It rendered a SECOND copy of the percentage and range
// beside the car name, crossfading against HeroMorphReadout's copy on heroT. Two renderings of
// the same digits at similar weight, both half-visible mid-morph, is what actually read as
// rough -- and no amount of tuning the two alphas fixes a duplicate. There is now one readout,
// visible in both states, that moves and changes shape; see HeroMorphReadout.
//
// Its scale-not-lerp argument was still correct for what it was doing, and is preserved where
// it now applies: nothing depended on ITS size, whereas the surviving readout's Column height
// must grow, which is why that one lerps real type steps.


/**
 * The hero's readout as ONE set of components that morphs between the collapsed and expanded
 * states, rather than two sets trading places.
 *
 * [t] is 0 collapsed, 1 expanded, and everything here is a lerp on it: the percentage's and
 * range's type sizes, the state line's alpha, the gaps. There is exactly one [RollingNumber]
 * per number and one [ChargeSegmentBar] in the whole card, so nothing can be duplicated and
 * nothing can drift.
 *
 * NO `SharedTransitionLayout`, and that is the point. Three earlier attempts used
 * `sharedBounds`, which needs a `LookaheadScope` -- `SharedBoundsNode` implements
 * `ApproachLayoutModifierNode`, so it participates in layout, and the hero sits on every car
 * page. With `beyondViewportPageCount = 1` that meant three lookahead scopes measuring twice
 * at 60Hz during a pager drag, which is what made the car swipe judder. It could not be tuned
 * out either: `RemeasureToBounds` re-lays out text every frame, and `ScaleToBounds` draws the
 * entering node at the wrong scale.
 *
 * The travel is FREE, and that is the insight the first three attempts missed. The card's
 * height is ALREADY animating -- the photo grows and shrinks on its own transition. Anchor
 * this to the card's bottom and it rides that height change from the header down to the base
 * of the photo with no bounds animation at all. I was animating a position that something
 * else was already animating for me. Only the SIZE morph needs driving, which is what [t] does.
 *
 * Cost per frame, deliberately bounded: two `Text` measures (the two type sizes lerp) plus one
 * `Canvas`, in a single layout pass. The version that felt laggy was ~8 paragraph layouts
 * DOUBLED by a lookahead pass.
 */
/** How far above its resting position the hero photo starts (entrance) / travels to
 *  (exit) -- see the AnimatedVisibility wrapping [HeroPhotoBackdrop]. Real enough to
 *  read as arriving from somewhere, short enough that it doesn't fight the card's own
 *  height reveal for what the eye follows. */
private val HeroPhotoSlideDistance = 28.dp

/**
 * The COLLAPSED percentage and range, drawn as trailing content on the pebble's own title Row.
 *
 * This exists because six attempts to place these numbers next to the car name by arithmetic --
 * bottom-anchoring plus a derived lift, a measured title width, a scaled ratio -- all landed
 * slightly off, in one direction or the other. The title Row can lay them out beside the name
 * exactly, for free, because that is what a Row does. PebbleShell's `titleTrailing` slot was
 * built for precisely this and its KDoc still said so while nothing used it.
 *
 * The cost, stated plainly: the numbers now have TWO instances -- this one and the expanded one in
 * [HeroMorphReadout]. The charge BAR is still a single instance. Two text copies that are never
 * both visible is a better trade than one copy whose position has to be computed from four
 * unrelated paddings, and the earlier roughness came from the two copies overlapping at similar
 * opacity, which the disjoint alpha ranges here and in [HeroMorphReadout] prevent.
 */
@Composable
private fun HeroCollapsedNumbers(
    data: ChargeReadout,
    t: Float,
    /** Reports where this row landed, in the coordinate space the overlay uses.
     *  The title Row positions it beside the name for free -- that placement is
     *  the thing six arithmetic attempts could not reproduce -- so the way to
     *  get a single travelling copy is to keep letting the Row do the placing
     *  and then read the answer off it. */
    onPositioned: (LayoutCoordinates) -> Unit = {},
    /** True once the overlay has both anchors and is drawing the real numbers.
     *  This row then measures and positions exactly as before but paints
     *  nothing, so the title Row still reserves the right space and reports the
     *  right position. */
    hoisted: Boolean = false,
) {
    // Gone by t = 0.35, where the expanded copy starts appearing -- unless the
    // overlay has taken over, in which case this stays laid out for its whole
    // life as the collapsed ANCHOR and simply never paints.
    val fade = (1f - t / 0.35f).coerceIn(0f, 1f)
    if (fade <= 0f && !hoisted) return
    Row(
        // The leading gap off the car name. PebbleShell deliberately puts no Spacer
        // before `titleTrailing` -- a gap left behind an absent node would squeeze the
        // expanded title -- so the slot owns it, and this slot did not. The name ran
        // straight into the percentage: "SONATA N-Line40%". Reported from a real device.
        //
        // Inside the faded Row, so it leaves with the numbers rather than holding a
        // 10dp hole open in the title row after they have gone.
        Modifier
            .graphicsLayer { alpha = if (hoisted) 0f else fade }
            .padding(start = 10.dp)
            .onGloballyPositioned(onPositioned),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroNumbers(data, t = 0f)
    }
}

/**
 * The percentage and range, as ONE definition.
 *
 * Three call sites render this and only one of them is ever visible: the
 * collapsed anchor in the title Row, the expanded anchor in the readout, and
 * the real travelling instance the overlay draws between them. That is what
 * makes the single copy true rather than nominal -- the two anchors exist to be
 * MEASURED, not read, so there is one set of glyphs on screen and one place
 * that decides what they say.
 *
 * [t] drives only the type size, because position is the overlay's job.
 */
@Composable
private fun HeroNumbers(
    data: ChargeReadout,
    t: Float,
    width: Dp? = null,
    // Stretches the inner Row to whatever width its PARENT already resolved via its own
    // fillMaxWidth, instead of this function measuring/being handed one. Exists so a caller
    // that is already fillMaxWidth (HeroMorphReadout's un-hoisted anchor) doesn't need
    // BoxWithConstraints to hand a Dp down -- that was tried and reverted for the
    // subcomposition cost, see the call site. Ignored when [width] is set; the two are
    // mutually exclusive ways of getting the same SpaceBetween arrangement a real width.
    fillWidth: Boolean = false,
    // The status line's own fade -- see the top-level `statusAlpha` this defaults from for
    // why it isn't just `t`. Defaults to `t` so the collapsed anchor (which calls this with
    // t = 0f and never shows the line at all, see the `t > 0.01f` guard below) and the
    // CoverTile call site need no changes.
    statusAlpha: Float = t,
) {
    val type = MaterialTheme.typography
    val pctStyle = lerp(type.titleMedium, type.displayMedium, t)
    // Expanded, the range is a HEADLINE rather than a slightly-larger title. It is
    // the number a driver actually acts on -- "can I get there" -- and at titleLarge
    // it read as a caption beside the percentage instead of the second real figure
    // on the card.
    val rangeStyle = lerp(type.titleMedium, type.headlineMedium, t)
    Row(
        modifier = when {
            width != null -> Modifier.width(width)
            fillWidth -> Modifier.fillMaxWidth()
            else -> Modifier
        },
        verticalAlignment = Alignment.Bottom,
        // Given a width, the two ends push apart: percentage on the left, range on
        // the right. Collapsed the width IS the natural content width, so
        // SpaceBetween lays out exactly as a wrapped Row would and there is no jump
        // when the arrangement starts to matter -- the gap simply opens as the card
        // does.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // RollingNumber, not Text: this is now the ONLY instance of the percentage,
        // so it has to keep the digit roll the readout's copy used to own. Losing it
        // would have traded one animation for another rather than adding the travel.
        RollingNumber(
            data.pctText,
            pctStyle,
            FontWeight.Bold,
            // Charging shows in the COLOUR while collapsed: that row has no space for
            // the word, and the expanded readout spells it out in its state line, so
            // the cue fades back to the ordinary content colour as the card opens.
            if (data.charging) lerp(ChargeGreen, LocalContentColor.current, t)
            else LocalContentColor.current,
        )
        Spacer(Modifier.width(lerp(8.dp, 14.dp, t)))
        Column(horizontalAlignment = Alignment.End) {
            RollingNumber(data.rangeText ?: "--", rangeStyle, FontWeight.Bold)
            // The status line ("Parked", "Charging - 25 min - DC") travels WITH the
            // numbers, under the range, right-aligned to it.
            //
            // It has to live here rather than in the readout: hoisting the numbers
            // into a single travelling instance hid the readout's whole numbers row,
            // and the status line was inside it, so the expanded card simply stopped
            // saying what the car was doing. That is the regression this fixes.
            //
            // Height LERPED rather than the node being dropped, which is what made
            // the mileage "go to the top and then snap down": this Column is
            // bottom-aligned in the Row, so its bottom edge is the status line's
            // while the line exists and the RANGE's the instant it stops. Removing it
            // at the end of the collapse teleported the range down by a whole line in
            // one frame. clipToBounds because the Text keeps its intrinsic height as
            // the slot shrinks.
            //
            // Alpha uses [statusAlpha], not `t` directly -- see the top-level `val
            // statusAlpha` for why (a deliberate short delay before the line fades in,
            // requested after an earlier version tied alpha straight to `t` and it read
            // as arriving too eagerly). It's still safe against the clip-vs-alpha
            // mismatch that WAS here (alpha on an offset 0.2..1 window while height-reveal
            // ran on plain `t`, so a half-clipped glyph was also half-transparent and read
            // as stuttering): statusAlpha stays at exactly 0 -- not partway -- for the
            // whole delay, and by the time it starts moving, `t` (and so the height reveal)
            // has long since finished, so there is no partial-clip-plus-partial-opacity
            // combination left to produce.
            val statusSlot = with(LocalDensity.current) { type.labelLarge.lineHeight.toDp() }
            Box(
                Modifier
                    .height(lerp(0.dp, statusSlot, t))
                    .clipToBounds(),
            ) {
                if (t > 0.01f) {
                    val statusColor by androidx.compose.animation.animateColorAsState(
                        data.statusColor, animationSpec = tween(300), label = "statusLineColor",
                    )
                    Text(
                        data.statusLine,
                        style = type.labelLarge,
                        color = statusColor,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = statusAlpha.coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMorphReadout(
    data: ChargeReadout,
    t: Float,
    modifier: Modifier = Modifier,
    /** Start inset for the NUMBERS row only, so it can sit after the car name while the bar
     *  below still spans the card. Zero for [ChargeFuelBar], which has no name beside it. */
    numbersStart: Dp = 0.dp,
    /** Reports where the NUMBERS row landed, for the travelling overlay. See
     *  [HeroCollapsedNumbers.onPositioned] -- same idea at the other end. */
    onNumbersPositioned: (LayoutCoordinates) -> Unit = {},
    /** True once the overlay draws the real numbers. This row keeps measuring and
     *  positioning so it stays a valid anchor, and stops painting. */
    numbersHoisted: Boolean = false,
    /** The status line's own fade, on its own delayed clock -- see the caller's
     *  `statusAlpha` for why it isn't just `t`. Defaults to `t` so the CoverTile call
     *  site (a fixed t = 1f, nothing animating) keeps behaving exactly as before. */
    statusAlpha: Float = t,
) {
    val type = MaterialTheme.typography
    // Real type steps, lerped -- not a graphicsLayer scale -- and the reason is DEPENDENT
    // LAYOUT, not glyph quality. This Column's height must genuinely grow as the numbers do:
    // the state line below has to be pushed down and the card's content has to reserve the
    // space. `graphicsLayer` explicitly does not affect that -- it "does not change the
    // measured size or placement", so siblings would stay put and the scaled digits would
    // draw OVER them. [HeroCollapsedStats] (now deleted) could scale precisely because nothing depended on its
    // size; this cannot.
    //
    // So this pays a real cost, knowingly: a `Text` measures through the SINGLE-SLOT
    // ParagraphLayoutCache, so a per-frame font size misses it every frame. Bounded to two
    // Text nodes in one layout pass, with no lookahead pass doubling it.
    //
    // (The previous claim here -- that "a scaled 45sp glyph is soft at every intermediate
    // frame" -- was not a verified mechanism, and it contradicted the since-deleted
    // HeroCollapsedStats' comment
    // arguing the reverse. If this ever needs to become free, the move is Compose's own:
    // sharedBounds with scaleToBounds + skipToLookaheadSize, which scales a layout measured
    // once. That was tried and reverted for a different reason -- the lookahead cost on every
    // pager page -- documented in this function's KDoc above.)
    // The type scale for the numbers lives in [HeroNumbers] now, with the numbers
    // themselves. It was duplicated here, and a second copy of a lerped type scale
    // is exactly the drift this rework exists to remove -- the two would have had to
    // be kept in step by hand for the anchor to keep describing what the overlay
    // draws.
    Column(
        // NO alpha ramp. This node is present and fully visible in BOTH states, which is the
        // whole point: one bar and one pair of numbers that move and change shape, rather than
        // two copies crossfading. The `t * t` fade that was here existed only to hide this copy
        // while a second one was drawn in the header.
        modifier,
        verticalArrangement = Arrangement.spacedBy(lerp(2.dp, 6.dp, t)),
    ) {
        // Fades IN on the back half only. The collapsed numbers are drawn by the header's own
        // title Row (see HeroCollapsedNumbers), because that is the only way to guarantee they sit
        // on the name's line -- so this copy must be invisible until that one has gone, or both
        // are on screen at once and the morph reads as a double image.
        //
        // A plain Row, not BoxWithConstraints -- that was tried (to hand HeroNumbers its own
        // measured width so this anchor doesn't render left-packed for however many frames it
        // takes the travelling overlay to hoist) and reverted for the same reason ChargeBar's
        // own KDoc already warns about a few hundred lines down: BoxWithConstraints is
        // SUBCOMPOSITION, found there once already "while chasing dropped frames in the hero's
        // collapse". This Row is present and re-measured on every frame of the whole heroT
        // transition (only its alpha changes, never its existence), so a subcomposition here
        // paid that cost every frame the card was opening or closing, times however many pager
        // pages keep this composed at once -- reported as the animation "dropping frames" after
        // that change landed.
        //
        // fillMaxWidth achieves the same thing for free: this Row already stretches to the
        // readout's full available width, and HeroNumbers' own inner Row can be told to do the
        // same (fillWidth = true) rather than being handed a measured Dp -- both end up
        // constrained to the identical width, but the fillMaxWidth version costs one ordinary
        // layout pass instead of a second, nested composition pass.
        Row(
            Modifier
                .padding(start = numbersStart)
                // fillMaxWidth so this anchor reports the readout's real span rather
                // than its own wrapped content width. The overlay lerps to that
                // width, and it is what puts the range against the right edge; a
                // wrapped anchor would have left it packed beside the percentage.
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = if (numbersHoisted) 0f
                    else ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                }
                .onGloballyPositioned(onNumbersPositioned),
            verticalAlignment = Alignment.Bottom,
        ) {
            // ONE definition of the numbers, shared with the collapsed anchor and the
            // travelling overlay -- see [HeroNumbers]. This row's job is now only to
            // be MEASURED: it lays the numbers out where the expanded card wants them
            // and reports that, and the overlay draws the copy anyone actually sees.
            //
            // Rendering the same composable here rather than a hand-kept twin is what
            // makes the anchor trustworthy: if this drew a different size from the
            // overlay, the interpolation would be between two points that describe
            // different things, and the numbers would drift as the card opened.
            //
            // fillWidth = true, not a measured `width`: this Row is already fillMaxWidth,
            // so HeroNumbers' own inner SpaceBetween Row just needs to be told to match it
            // (see [HeroNumbers]'s own `fillWidth` param) rather than being handed the number
            // back through a subcomposition.
            HeroNumbers(data, t, fillWidth = true, statusAlpha = statusAlpha)
        }
        // Plug-in hybrid's fuel tank: expanded only, same reasoning as the state line. Fades
        // in over the back half of the morph so it does not compete with the numbers growing.
        //
        // The pump icon is here because dropping it was a second regression in my first pass
        // at this morph -- ChargeFuelBar has always drawn one, and "Fuel 40%" on its own reads
        // as another battery figure in a card that is otherwise all battery.
        data.fuelPct?.takeIf { t > 0.5f }?.let { fuelPct ->
            val fuelColor = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = ((t - 0.5f) * 2f).coerceIn(0f, 1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fuelColor,
                )
                Spacer(Modifier.width(6.dp))
                Text("Fuel $fuelPct%", style = type.bodyMedium, color = fuelColor, maxLines = 1)
            }
        }
        ChargeSegmentBar(
            frac = animatedChargeFrac(data.frac),
            limitPct = data.limitPct,
            stuckAtLimit = data.stuckAtLimit,
            charging = data.charging,
        )
    }
}

/**
 * The hero's charge bar: three separately-rounded segments -- filled up to the
 * current charge, a track segment up to the limit, and a darker-backdrop dim
 * segment past it -- or two when the charge is already at (or past) its limit,
 * since there's no "still charging toward the limit" zone left to show
 * separately. Each piece is its own fully-rounded pill with a real gap either
 * side of it, explicitly requested over an earlier flush, one-continuous-shape
 * version: "I want it to be three rounded segments instead of one continuous
 * bar."
 *
 * Earlier designs, in order, and why each was replaced:
 *  1. A seam where the fill ended, plus a small circular marker drawn on top at
 *     the limit -- charge sitting AT its limit (the common case) put both devices
 *     on the same pixel, "a 5dp hole under a 14dp dot."
 *  2. Three segments with a gap only at the limit split, the rest flush -- fixed
 *     (1)'s collision, but read as an uneven mix of one joined piece and one
 *     separate piece rather than a consistent shape.
 *  3. All three segments flush, no gap anywhere, legibility carried by a darker
 *     backdrop instead of any physical break -- this was mistakenly taken from
 *     a reference image showing a smooth SINGLE bar, but the actual request was
 *     for the "smooth rounded corners" style applied to each of three DISTINCT
 *     pieces, not one continuous shape. This version.
 *
 * Blue fill instead of green when the charge has reached its limit -- "topped
 * up," not "still filling" -- regardless of whether the car is actively
 * reporting a charging session, so the colour stays accurate hours after the
 * car finished charging to that limit, not just while plugged in.
 *
 * The limit split still animates: the fill springs to its target the same way it
 * always did, and the limit split slides to a new position rather than snapping
 * between two frames if the limit itself changes while charging.
 *
 * Two more animations, phone-only ("more motion on the phone card... keep others
 * static but visually matching" -- the widget and the notification are real
 * RemoteViews/Glance surfaces with no animation APIs to reach for, so this is the
 * one place any of this can live):
 *  - the fill's own colour springs between green and blue rather than snapping the
 *    instant [stuckAtLimit] flips, so reaching the limit reads as the bar arriving
 *    somewhere rather than a hard colour cut mid-frame;
 *  - while [charging] is true, a soft highlight sweeps once across the filled
 *    segment on a loop -- the one piece of genuinely ambient motion on this card,
 *    there specifically to read as "still happening" during the long stretches
 *    where the fill itself has already settled and isn't moving on its own.
 */
@Composable
private fun ChargeSegmentBar(
    frac: Float,
    limitPct: Int?,
    stuckAtLimit: Boolean,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val limit = limitPct?.takeIf { it in 1..99 }
    val trackColor = scheme.onSurface.copy(alpha = 0.16f)
    // The past-the-limit zone: a genuinely darker BACKDROP (fixed black, not a
    // theme colour -- this card sits on an arbitrary car photo as often as a flat
    // surface, and a black scrim is what already keeps text legible over that same
    // photo elsewhere on this exact card, so it reads as "darker" regardless of
    // what's underneath) painted first, with the ordinary dim tint layered on top of
    // it -- carries the "won't fill past here" distinction alongside its own
    // separately-rounded shape, not instead of it.
    val farBackdropColor = Color.Black.copy(alpha = 0.35f)
    val trackDimColor = scheme.onSurface.copy(alpha = 0.08f)
    // Sprung, not a plain `if`: this used to pick the two-item colour list outright,
    // so a car finishing its last percent to the limit cut from green to blue on
    // whatever single frame stuckAtLimit flipped. Springing both gradient stops gives
    // that moment an actual transition instead of a colour popping mid-draw.
    val fillDark by androidx.compose.animation.animateColorAsState(
        targetValue = if (stuckAtLimit) ChargeBlueDark else ChargeGreenDark,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFillDark",
    )
    val fillLight by androidx.compose.animation.animateColorAsState(
        targetValue = if (stuckAtLimit) ChargeBlue else ChargeGreen,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFillLight",
    )
    // Animatable, not animateFloatAsState, for the same reason the old marker's slide
    // was: snap to the first-ever value (no previous position to animate FROM when a
    // limit first appears), spring for every change after that.
    val limitAnim = remember { Animatable(0f) }
    var limitSeen by remember { mutableStateOf(false) }
    LaunchedEffect(limit) {
        val target = (limit ?: return@LaunchedEffect) / 100f
        if (limitSeen) {
            limitAnim.animateTo(target, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow))
        } else {
            limitSeen = true
            limitAnim.snapTo(target)
        }
    }
    // The charging shimmer's own travelling position, 0 at the fill's start and 1 at
    // its end -- built (not just gated) only while charging, so an idle/parked car
    // pays nothing for an InfiniteTransition it will never render: no ticket, no
    // per-frame invalidation, nothing running in the background of a page that's
    // sitting on a fully charged or unplugged car.
    val shimmerX = if (charging) {
        val shimmer = rememberInfiniteTransition(label = "chargeShimmer")
        val x by shimmer.animateFloat(
            initialValue = -0.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "chargeShimmerX",
        )
        x
    } else {
        null
    }
    // DRAWN, not composed -- see the git history here for why: this used to be a
    // BoxWithConstraints holding a Row of Boxes plus an offset child for the marker, and
    // BoxWithConstraints is SUBCOMPOSITION, which cost a re-measure on every frame of the
    // fill/marker animations. One Canvas pass costs nothing per frame that isn't already
    // being paid for the fill's own animateFloatAsState.
    Canvas(modifier.fillMaxWidth().height(ChargeBarHeight)) {
        val h = size.height
        val radius = CornerRadius(h / 2f)
        // The actual segment math lives in chargeBarLayout, a plain function with no
        // Compose/DrawScope dependency, specifically so it's unit-testable -- this
        // Canvas lambda cannot be. See ChargeSegmentBarTest, which sweeps a wide range
        // of width x percent x limit combinations asserting the three-segment case
        // (fill, track-to-limit, dim-track-past-it) genuinely produces three
        // positive-width, correctly-gapped segments, not just that the formula looks
        // right by eye.
        val layout = chargeBarLayout(
            totalWidth = size.width,
            barHeight = h,
            filledFrac = frac,
            limitFrac = limit?.let { limitAnim.value },
            stuckAtLimit = stuckAtLimit,
            gap = ChargeSegmentGap.toPx(),
        )
        if (layout.fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(fillDark, fillLight),
                    startX = 0f,
                    endX = layout.fillWidth,
                ),
                size = Size(layout.fillWidth, h),
                cornerRadius = radius,
            )
            // The shimmer band: transparent everywhere except a soft white peak that
            // travels with shimmerX. Drawn as a SECOND rounded rect the same size as
            // the fill (rather than a separate clip) -- drawRoundRect only lights up
            // the pixels its own shape covers, so this rides on top of the gradient
            // above without needing to clip anything itself. A linear (not radial)
            // brush with Transparent at both ends is safe to position anywhere,
            // including bandCenter values outside the fill's own bounds, because
            // Brush.linearGradient clamps to its end colour past start/end -- which
            // is Transparent here -- so there is no stop-ordering math to get wrong
            // as the band enters and leaves.
            if (shimmerX != null) {
                val bandWidth = layout.fillWidth * 0.35f
                val bandCenter = layout.fillWidth * shimmerX
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                        start = Offset(bandCenter - bandWidth, 0f),
                        end = Offset(bandCenter + bandWidth, 0f),
                    ),
                    size = Size(layout.fillWidth, h),
                    cornerRadius = radius,
                )
            }
        }
        if (layout.hasSingleTrack) {
            // No limit at all, or already at/past it: one remaining segment, the
            // ordinary track colour when there's no limit to speak of, the DARKER
            // backdrop + dim tint when the charge is stuck there -- the whole
            // remainder past the current charge means "won't fill further" in
            // that case, not "still on the way".
            if (layout.singleTrackWidth > 0f) {
                val at = Offset(layout.singleTrackStart, 0f)
                val sz = Size(layout.singleTrackWidth, h)
                if (layout.singleTrackDim) {
                    drawRoundRect(color = farBackdropColor, topLeft = at, size = sz, cornerRadius = radius)
                    drawRoundRect(color = trackDimColor, topLeft = at, size = sz, cornerRadius = radius)
                } else {
                    drawRoundRect(color = trackColor, topLeft = at, size = sz, cornerRadius = radius)
                }
            }
        } else {
            // Two remaining segments, each its own rounded piece: current -> limit
            // (still filling toward it) and limit -> 100% (won't fill past it).
            if (layout.midWidth > 0f) {
                drawRoundRect(
                    color = trackColor, topLeft = Offset(layout.midStart, 0f),
                    size = Size(layout.midWidth, h), cornerRadius = radius,
                )
            }
            if (layout.farWidth > 0f) {
                val at = Offset(layout.farStart, 0f)
                val sz = Size(layout.farWidth, h)
                drawRoundRect(color = farBackdropColor, topLeft = at, size = sz, cornerRadius = radius)
                drawRoundRect(color = trackDimColor, topLeft = at, size = sz, cornerRadius = radius)
            }
        }
    }
}

/**
 * Pure segment-boundary math for [ChargeSegmentBar], pulled out of its DrawScope
 * specifically so it can be unit-tested without a Compose runtime -- see
 * ChargeSegmentBarTest. [limitFrac]/[stuckAtLimit] mirror the composable's own params.
 * [gap] is the physical break reserved on BOTH sides of every internal boundary --
 * between the fill and whatever follows it, and (when there's a limit and it hasn't
 * been reached) between that and the far segment too -- so every piece comes out as
 * its own separately-rounded segment rather than any two of them reading as one
 * joined shape.
 */
internal data class ChargeBarLayout(
    val fillWidth: Float,
    /** True for the collapsed one-segment remainder (no limit at all, or already at/past
     *  it) -- [midWidth]/[farWidth] are both 0 in that case, and vice versa. */
    val hasSingleTrack: Boolean,
    val singleTrackStart: Float,
    val singleTrackWidth: Float,
    /** Dim track when stuck at the limit, ordinary track when there's no limit to speak
     *  of -- only meaningful when [hasSingleTrack]. */
    val singleTrackDim: Boolean,
    val midStart: Float,
    val midWidth: Float,
    val farStart: Float,
    val farWidth: Float,
)

internal fun chargeBarLayout(
    totalWidth: Float,
    barHeight: Float,
    filledFrac: Float,
    limitFrac: Float?,
    stuckAtLimit: Boolean,
    gap: Float,
): ChargeBarLayout {
    val clampedFrac = filledFrac.coerceIn(0f, 1f)
    // Floored at the bar's own height when there is ANY charge: below that the 50%
    // corner radius eats the whole shape, so 3% and 0% would otherwise draw the same
    // nothing. This is the CONCEPTUAL current-charge boundary -- the fill segment's
    // own width is derived from it below, shrunk by half the gap.
    val filledXRaw = if (clampedFrac <= 0f) 0f else minOf(totalWidth, maxOf(totalWidth * clampedFrac, barHeight))
    val halfGap = gap / 2f
    // Every segment's own bound is coerced against its neighbour's, the same pattern
    // repeated at each boundary: shrink towards the gap first, never past 0 width and
    // never past the far edge of the bar, so a transient animation frame (the fill
    // still catching up to a just-lowered limit, the limit sitting right next to the
    // fill, a charge near 0% or 100%) can only ever yield the gap or a zero-width
    // segment, never a negative one or an overflow.
    val fillWidth = (filledXRaw - halfGap).coerceAtLeast(0f)

    if (limitFrac == null || stuckAtLimit) {
        val trackStart = minOf(totalWidth, filledXRaw + halfGap)
        val trackWidth = (totalWidth - trackStart).coerceAtLeast(0f)
        return ChargeBarLayout(
            fillWidth = fillWidth,
            hasSingleTrack = true,
            singleTrackStart = trackStart,
            singleTrackWidth = trackWidth,
            singleTrackDim = limitFrac != null,
            midStart = 0f, midWidth = 0f, farStart = 0f, farWidth = 0f,
        )
    }
    val limitXRaw = (totalWidth * limitFrac).coerceIn(filledXRaw, totalWidth)
    val midStart = minOf(totalWidth, filledXRaw + halfGap)
    val midEnd = (limitXRaw - halfGap).coerceIn(midStart, totalWidth)
    val midWidth = (midEnd - midStart).coerceAtLeast(0f)
    val farStart = (limitXRaw + halfGap).coerceIn(limitXRaw, totalWidth)
    val farWidth = (totalWidth - farStart).coerceAtLeast(0f)
    return ChargeBarLayout(
        fillWidth = fillWidth,
        hasSingleTrack = false,
        singleTrackStart = 0f, singleTrackWidth = 0f, singleTrackDim = false,
        midStart = midStart, midWidth = midWidth,
        farStart = farStart, farWidth = farWidth,
    )
}

// Colours, sizes and motion specs shared across screens live in UiTokens.kt.

/**
 * The shared floating/card edge: the app's default frosted rim ([frostedRim]).
 * Call sites keep their normal [glassContainerAlpha] frosted fill. The [tint]
 * param is retained for call-site compatibility but is no longer used.
 */
@Composable
internal fun Modifier.appGlassRim(
    shape: Shape,
    @Suppress("UNUSED_PARAMETER") tint: Color = MaterialTheme.colorScheme.surfaceContainer,
): Modifier = this.frostedRim(shape)

/**
 * When true (cover-screen tiles), pebbles render permanently open with no
 * collapse chevron or drag handle - collapsing a full-screen tile makes no sense.
 */
/**
 * The current [SettingsStore.Appearance], provided once at the app root (see
 * BlooApp) so pebbles/tiles read it via LocalAppearance.current instead of each
 * opening its own vm.appearance.collectAsState() coroutine collector. ~20 hot
 * per-pebble/per-tile collectors collapse to one. Default is a fresh Appearance()
 * (all defaults) so a reader outside the provider degrades gracefully rather than
 * crashing — but every real screen is inside the provider.
 */
internal val LocalAppearance = staticCompositionLocalOf { SettingsStore.Appearance() }

private val LocalForceExpanded = staticCompositionLocalOf { false }

/**
 * When true (cover-screen tiles), a pebble stretches to fill the available height
 * and scrolls internally if its content is taller - so each tile fills the screen.
 */
private val LocalPebbleFillHeight = staticCompositionLocalOf { false }

/** Tile names that [CompactCar] can render — unknown sections are excluded. */
private val CompactKnownTiles = setOf(
    // No "controls" here, deliberately. It was added when the lock/horn
    // controls were unreachable on the cover, but as its own page it was one
    // short row of buttons above two thirds of an empty screen. Those same
    // controls now live in CoverMainTile's permanent action bar, on the page
    // the cover opens on -- so a separate page for them would be a second,
    // emptier copy of something already on screen.
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
internal fun RollingNumber(
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

/**
 * A coarse, self-ticking "x min ago" string for [millis] (null → null).
 *
 * Holds the LABEL in state rather than a clock, which is the whole efficiency of it. A
 * `mutableStateOf` write only invalidates readers when the value actually changes, so a tick
 * that recomputes "4h ago" and finds "4h ago" costs nothing at all. The previous version kept
 * `now` in state and returned a value derived from it, so every tick invalidated its caller
 * unconditionally -- for a car refreshed hours ago that was 120 recompositions an hour, each
 * producing a byte-identical string, at three or four call sites, times however many car pages
 * the pager holds live.
 *
 * The interval now matches the label's own resolution instead of being a flat 30s. Under a
 * minute the text really does change every few seconds, so tick at 10s; under an hour it can
 * only change once a minute; past that it cannot change more than every quarter of an hour.
 * Strictly more responsive at the fine end and ~30x less work at the coarse end.
 *
 * Also gone: `if (now >= 0)`, which was always true (it tested a wall-clock millis) and existed
 * only to make the composable read the state and thus subscribe to the timer. It worked, but a
 * condition that cannot be false is a trap for the next reader -- holding the label in state
 * makes the subscription honest and the guard unnecessary.
 *
 * The bucket thresholds themselves stay in shared/relativeLabel(), which owns them; this had
 * drifted from that once already ("d ago" here vs "day ago" there).
 */
@Composable
internal fun rememberRelativeTime(millis: Long?): String? {
    if (millis == null) return null
    var label by remember(millis) {
        mutableStateOf(com.bloo.bluelink.data.relativeLabel(millis))
    }
    LaunchedEffect(millis) {
        while (true) {
            val age = System.currentTimeMillis() - millis
            delay(
                when {
                    age < 60_000L -> 10_000L
                    age < 3_600_000L -> 60_000L
                    else -> 900_000L
                },
            )
            label = com.bloo.bluelink.data.relativeLabel(millis)
        }
    }
    return label
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
internal fun <T> ReorderColumn(
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
    // The four callback parameters, behind rememberUpdatedState so the per-item drag
    // Modifier below can be remembered without capturing a stale one. See `handle`.
    val keyOfNow by rememberUpdatedState(keyOf)
    val onReorderNow by rememberUpdatedState(onReorder)
    val onDragMoveNow by rememberUpdatedState(onDragMove)
    val onDragReleaseNow by rememberUpdatedState(onDragRelease)
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    // Consumed the instant this key is first read, so navigating back to the
    // garage (or a second car's column composing) later never replays it.
    val playIntro = remember(introKey) {
        staggerInOnColdStart && coldStartIntroPlayed.add(introKey)
    }

    // Sync with upstream changes only while not actively dragging.
    LaunchedEffect(items) { if (draggingKey == null) order = items }
    // The "drop ripple" animation that used to live here is gone. It was dead twice
    // over: `dropRipple` was declared and never assigned, so the effect's `!= 0L`
    // guard could not become true; and even if it had, nothing ever read
    // maxRippleScale, so no ripple would have been drawn. An Animatable and a
    // LaunchedEffect that could only ever do nothing, described by a comment
    // ("shows the 'weight' of the move") for an effect no user has seen.

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEachIndexed { index, item ->
            val k = keyOf(item)
            // Identity key so Compose moves the existing node when the order
            // changes (instead of reusing nodes by slot, which looks janky).
            key(k) {
                val dragging = draggingKey == k
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
                    // REMEMBERED, so this is ONE instance for the item's lifetime.
                    //
                    // Every pebble takes this as a `dragHandle: Modifier`. Built inline, the
                    // chain below is rebuilt on every recomposition, and a child can only skip
                    // if its arguments compare equal -- so a fresh chain means a changed
                    // argument. `Modifier` is a @Stable type, so it is compared with equals(),
                    // and each element's equals() compares its lambda by reference.
                    //
                    // ⚠ HONEST CAVEAT, because I first wrote this comment claiming more than
                    // it can. The reasoning I used -- "one unstable parameter makes the whole
                    // composable non-skippable" -- is PRE-strong-skipping framing and is
                    // outdated on this toolchain. Strong skipping has been the default since
                    // Kotlin 2.0.20 and this project is on 2.2.20: an unstable parameter no
                    // longer blocks skipping, it is just compared by reference instead of
                    // equals(). Worse for my claim, Kotlin 2.0.20+ also auto-remembers lambdas
                    // declared inside a composable, keyed on their captures -- so the three
                    // lambdas below may well have been memoized already, making this remember
                    // belt-and-braces rather than the unlock the commit said it was.
                    //
                    // Kept anyway: one remembered instance is strictly stronger than relying
                    // on per-lambda auto-remember plus every element's equals(), and it costs
                    // nothing. But do NOT treat this as the reason pebbles now skip. The
                    // measured lever is passing narrower parameters than the whole UiState.
                    //
                    // Safe to remember despite the captures: `order`, `offsetY`,
                    // `draggingKey` and `heights` are all delegated/remembered snapshot
                    // state, so the captured object is stable and the lambdas read and write
                    // the LIVE value when they run. The four caller-supplied callbacks are
                    // the ones that genuinely change identity per recomposition, and they go
                    // through rememberUpdatedState above rather than being captured directly.
                    val handle = remember(k) {
                        Modifier
                        .onGloballyPositioned { handleCoords.value = it }
                        // The drag gesture below has no TalkBack equivalent at
                        // all -- reordering pebbles/presets/cars was completely
                        // unreachable for screen-reader users. Additive
                        // semantics-only "Move up"/"Move down" actions alongside
                        // the existing gesture (same pattern already used for
                        // MorphSegmented's drag track), reusing the same reorder
                        // + commit logic the drag path uses.
                        .semantics {
                            val cur = order.indexOfFirst { keyOfNow(it) == k }
                            customActions = listOfNotNull(
                                if (cur > 0) CustomAccessibilityAction("Move up") {
                                    order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                    onReorderNow(order)
                                    true
                                } else null,
                                if (cur in 0 until order.lastIndex) CustomAccessibilityAction("Move down") {
                                    order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                    onReorderNow(order)
                                    true
                                } else null,
                            )
                        }
                        .pointerInput(k) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingKey = k; offsetY = 0f },
                        onDragEnd = {
                            val handled = onDragReleaseNow?.invoke(k) ?: false
                            draggingKey = null; offsetY = 0f
                            if (!handled) onReorderNow(order)
                        },
                        onDragCancel = { onDragReleaseNow?.invoke(k); draggingKey = null; offsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetY += dragAmount.y
                            handleCoords.value?.takeIf { it.isAttached }?.let {
                                onDragMoveNow?.invoke(k, it.localToWindow(change.position))
                            }
                            val cur = order.indexOfFirst { keyOfNow(it) == k }
                            if (cur >= 0) {
                                if (offsetY > 0 && cur < order.lastIndex) {
                                    val nextH = heights[keyOfNow(order[cur + 1])] ?: 0
                                    if (nextH > 0 && offsetY > nextH / 2f) {
                                        order = order.toMutableList().also { it.add(cur + 1, it.removeAt(cur)) }
                                        offsetY -= nextH
                                    }
                                } else if (offsetY < 0 && cur > 0) {
                                    val prevH = heights[keyOfNow(order[cur - 1])] ?: 0
                                    if (prevH > 0 && -offsetY > prevH / 2f) {
                                        order = order.toMutableList().also { it.add(cur - 1, it.removeAt(cur)) }
                                        offsetY += prevH
                                    }
                                }
                            }
                        },
                    )
                    }
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
internal fun AnimatedSlider(
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
internal fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = 28.dp): Modifier = this
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
internal fun BackdropHost(content: @Composable BoxScope.() -> Unit) {
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
            // summary (image+gauge) and controls are reorderable pebbles too. The full
            // pebble column always renders while swiping; smoothness comes from
            // PebbleList's own one-frame lazy-fill (filled/EAGER_PEBBLES) + the pager's
            // beyondViewportPageCount=1 pre-compose, not from an in-transit skeleton.
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
                CarNamePill(v.name) { scope.launch { scroll.animateScrollTo(0) } }
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
            it in state.sectionsFor(v) && state.isSectionAvailable(v, it)
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
            // ExpandedCar (this wide/dual-column layout) is only ever reached
            // through GarageScreen's expandedIdx != null branch, and that same
            // condition is what keeps GarageScreen's own floating gear AND
            // flip-columns buttons on screen unconditionally -- including with
            // Appearance.settingsAsPage on, which hides the gear button
            // everywhere else but explicitly not here (see that button's own
            // comment). So unlike VehicleDetailContent's single-column pill
            // (TopStart, nothing competing there), this one is GUARANTEED both
            // buttons are present the whole time it can be visible: gear's own
            // 12dp+48dp footprint plus flip-columns' 52dp+12dp+48dp footprint
            // put together span the rightmost 112dp. Was a flat 8dp, which put
            // this pill directly underneath both buttons the moment a wide
            // screen's expanded car got scrolled.
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 116.dp),
        ) {
            CarNamePill(v.name) { scope.launch { controlsScroll.animateScrollTo(0) } }
        }
        }
    }
    }
}

/**
 * The floating name pill shown at the top of a scrolled-past hero: a rounded glass
 * [Surface] carrying the car's name that scrolls the view back to the top when tapped.
 * The compact and wide hero layouts both reveal one (differing only in which corner it
 * aligns to and which scroll state resets), so the chrome -- the glass fill, ambient
 * ring, drop shadow and frosted rim, and the 48dp tap target -- lives here once.
 */
@Composable
private fun CarNamePill(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.ambientRing(RoundedCornerShape(50)).dropShadow(RoundedCornerShape(50)).frostedRim(RoundedCornerShape(50)),
    ) {
        Box(Modifier.height(48.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
        // Memoized on the exact slices the predicate reads, mirroring the sibling PebbleList
        // (which documents the same fix). HotspotSlot takes the whole UiState, so it recomposes
        // on every emission; without this it re-allocated the filtered list AND a fresh setOf()
        // literal on every refresh/command tick for the visible car. The two `!=` checks replace
        // the per-pass set allocation.
        val options = remember(
            state.sectionOrders[v.vin], state.hiddenPebbles, state.aiEnabled, state.hasBattery(v),
            v.isGen5W, state.updateAvailable, state.updateTileDismissed,
        ) {
            state.sectionsFor(v).filter {
                it != "summary" && it != "controls" && state.isSectionAvailable(v, it)
            }
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
    val hMetric = LocalAppearance.current.unitSystem == "metric"
    // Same fix as SinglePebble's "summary" branch, same reasoning: HeroHeader takes no
    // `state` itself, so what's memoized is the derived arguments built here.
    val heroState = remember(
        status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v),
        state.locations[v.vin], state.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
    ) { state }
    HeroHeader(
        v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
        heroState.drivingLabel(v), metric = hMetric,
        photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
    )
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
    val pebbleOutline = LocalAppearance.current.pebbleOutline
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
    // Memoized on the exact slices the predicate reads (the `eager` set below was
    // already remembered; this sibling filter was missed). PebbleList takes the whole
    // UiState so it recomposes on every emission — without this the filter re-allocated
    // the visible-section list on every refresh/command tick for the visible car.
    val hasBattery = state.hasBattery(v)
    // state.updateTileDismissed is in the key because isSectionAvailable now reads it: without
    // it this memo would keep the stale section list and the dismissed tile's phantom slot
    // would survive until some unrelated key changed. Every input the predicate reads has to be
    // a key, which is the contract this line already follows for the other six.
    val sections = remember(
        allSections, exclude, state.hiddenPebbles, state.aiEnabled, hasBattery, v.isGen5W,
        state.updateAvailable, state.updateTileDismissed,
    ) {
        allSections.filter {
            it !in exclude && state.isSectionAvailable(v, it)
        }
    }
    val hotDrag = LocalHotSeatDrag.current
    // PERF: each car-pager page composes this whole pebble stack. Composing all
    // 8-10 pebbles eagerly (incl. ClimatePebble/ChargePebble's top-level effects,
    // which run BEFORE their Pebble() call regardless of collapsed state) on the
    // fling-settle frame is the biggest remaining car-swipe cost. So: compose the
    // hero + first EAGER_PEBBLES sections immediately (they're the only ones
    // above the fold), stub the rest with a collapsed-height placeholder for ONE
    // frame, then fill them in once idle (`filled` flips after the first frame).
    // Keyed on VIN so a disposed→recomposed page re-defers cheaply; a page kept
    // warm by beyondViewportPageCount=1 fills before the user ever swipes to it.
    // CRITICAL: `items` stays the FULL section list, so ReorderColumn's per-item
    // Box/key/animatePlacement/onSizeChanged/drag/semantics all exist from frame
    // one — only the body inside the content lambda is deferred, so the reorder
    // model is 100% intact and the off-screen stub→real swap is never visible.
    var filled by remember(v.vin) { mutableStateOf(false) }
    LaunchedEffect(v.vin) { withFrameNanos { }; filled = true }
    val eager = remember(sections) { sections.take(EAGER_PEBBLES).toSet() }
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
        if (filled || section in eager) {
            SinglePebble(section, v, state, vm, dragHandle)
        } else {
            // One-frame off-screen placeholder: reserves ~collapsed pebble height so
            // the list doesn't visibly jump when the real body fills in, and carries
            // the dragHandle so ReorderColumn's item is fully formed. Below the fold,
            // so this transient state is never seen or interacted with.
            Box(Modifier.fillMaxWidth().height(PebbleHeaderHeight).then(dragHandle))
        }
    }
}

/** How many pebbles (from the top, incl. the hero summary) the per-car stack
 *  composes eagerly; the rest fill in one frame later, off the swipe. 3 comfortably
 *  covers everything above the fold on a phone so the visible region never stubs. */
private const val EAGER_PEBBLES = 3

/**
 * Renders one pebble by section name (used by the list and the hot spot).
 *
 * Every pebble function below takes the WHOLE [UiState], not just the fields it
 * reads -- `state` is a data class, so its equality (and therefore Compose's
 * recomposition-skip check) fails on ANY field changing anywhere in the app, not
 * just the fields a given pebble actually uses. A weather refresh for a car
 * that isn't even on screen, an AI probe finishing, another car's status
 * arriving -- every one of those forced every visible pebble on every visible
 * car page to recompose, which is a big part of why the whole app reads as
 * laggy for several seconds after cold start or a car switch: that's exactly
 * the window where the most independent state updates land in quick
 * succession (cached-status restore, per-car status fetches, AI/Shizuku/update
 * probes, weather).
 *
 * Each branch below wraps the `state` it hands its pebble in
 * `remember(<the exact fields that pebble reads>) { state }` -- when none of
 * those keys changed since last time, `remember` returns the SAME state
 * reference as before, so the pebble sees an unchanged parameter and Compose
 * skips recomposing it, even though a genuinely newer `state` exists one frame
 * up. The pebble's own body is untouched; only what gets handed to it here is
 * cached. Keys were catalogued by reading every pebble function's body in
 * full (including what its own helper calls like `statusFor`/`isPending`
 * transitively read) rather than guessed -- a missed key would be a real
 * stale-UI bug, so each list below is the pebble's complete, verified
 * dependency set, not a guess at "probably enough."
 */
@Composable
private fun SinglePebble(section: String, v: Vehicle, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val status = state.statusFor(v)
    val seats = state.seatConfigFor(v)
    val enabled = !state.loading
    val mSingle = LocalAppearance.current.unitSystem == "metric"
    when (section) {
        "summary" -> {
            // HeroHeader itself takes no `state` param -- its dependency is entirely
            // in the derived arguments built here, so THOSE are what's memoized.
            val heroState = remember(
                status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v),
                state.locations[v.vin], state.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
            ) { state }
            HeroHeader(
                v, status, heroState.imageUrls[v.vin], heroState.hasBattery(v), heroState.hasFuel(v), vm,
                heroState.drivingLabel(v), dragHandle = dragHandle, metric = mSingle,
                photoExpanded = heroState.isPebbleExpanded(v.vin, com.bloo.bluelink.data.HERO_PHOTO_SECTION),
            )
        }
        // Its own reorderable/pinnable slot now, like every other pebble --
        // only actually present in the list while state.updateAvailable != null
        // (see PebbleList's filter and the two hotspot-eligibility checks). Global,
        // not per-car fields, but still worth memoizing: this section is rendered
        // on every car page, so an unrelated per-car state change (another car's
        // status, weather, AI) would otherwise recompose it just as often as any
        // other pebble.
        "update" -> {
            val updateState = remember(
                state.updateAvailable, state.updateTileDismissed, state.shizukuAvailable,
                state.updateInstalling, state.updateDownloading, state.updateApkReady,
                state.updatePendingDismiss,
            ) { state }
            UpdateAvailableTile(updateState, vm, dragHandle)
        }
        "controls" -> {
            val controlsState = remember(status, state.isPending(v.vin, "doors"), state.isPending(v.vin, "hornLights")) { state }
            ControlsPebble(v, controlsState, vm, dragHandle)
        }
        "climate" -> {
            val climateState = remember(
                status, seats, state.isPending(v.vin, "climate"), state.climatePresets[v.vin],
                state.climateSync[v.vin], state.locations[v.vin], state.carWeather[v.vin],
                state.homeWeather, state.settingsMode, state.isPebbleExpanded(v.vin, "climate"),
                state.defaultClimatePresets[v.vin],
            ) { state }
            ClimatePebble(v, status, seats, climateState, vm, dragHandle)
        }
        // The "charge" slot is the powertrain's energy pebble: charging for an
        // EV/PHEV, a fuel readout for a gas/hybrid car (no charge UI at all).
        "charge" -> if (state.hasBattery(v)) {
            val chargeState = remember(
                status, enabled, state.isPending(v.vin, "charge"), state.isPending(v.vin, "chargeLimit"),
                state.hasBattery(v), state.hasFuel(v), state.locations[v.vin],
                state.isPebbleExpanded(v.vin, "charge"),
            ) { state }
            ChargePebble(v, status, enabled, chargeState, vm, dragHandle)
        } else {
            val fuelState = remember(status, state.refreshing, state.isPebbleExpanded(v.vin, "charge")) { state }
            FuelPebble(v, status, fuelState, vm, dragHandle)
        }
        "location" -> {
            val locationState = remember(
                state.locations[v.vin], state.placeNames[v.vin], state.isPending(v.vin, "locate"),
                state.carWeather[v.vin], state.isPebbleExpanded(v.vin, "location"),
            ) { state }
            LocationPebble(v, locationState, vm, dragHandle)
        }
        "weather" -> {
            val weatherState = remember(state.homeWeather, state.isPebbleExpanded(v.vin, "weather")) { state }
            WeatherPebble(v, weatherState, vm, dragHandle)
        }
        // Trip history rides on the EV trip-details endpoint, so EVs only.
        "trips" -> {
            val tripsState = remember(state.trips[v.vin], state.isPending(v.vin, "trips"), state.isPebbleExpanded(v.vin, "trips")) { state }
            TripsPebble(v, tripsState, vm, dragHandle)
        }
        "info" -> {
            val infoState = remember(
                status, state.locations[v.vin], state.licensePlates[v.vin], state.lastServiceMiles[v.vin],
                state.serviceIntervalMiles[v.vin], state.refreshing, state.hasBattery(v),
                state.placeNames[v.vin], state.fetchedAt(v), state.isPebbleExpanded(v.vin, "info"),
            ) { state }
            InfoPebble(v, status, infoState, vm, dragHandle)
        }
        "diagnostics" -> {
            val diagnosticsState = remember(status, state.hasBattery(v), state.isPebbleExpanded(v.vin, "diagnostics")) { state }
            DiagnosticsPebble(v, status, diagnosticsState, vm, dragHandle)
        }
        "ai" -> {
            val aiState = remember(v.vin in state.aiBusy, state.aiSummaries[v.vin], state.isPebbleExpanded(v.vin, "ai")) { state }
            AiPebble(v, aiState, vm, dragHandle)
        }
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
        // The one pebble whose subject is not a part of the car, so it is the one
        // that earns a different surface: a gradient marks "this was generated"
        // rather than measured, the same way the Summarize action is the only
        // header action that makes something rather than sending a command.
        //
        // Built from the scheme's own container roles rather than fixed hues, so it
        // follows the user's accent, their vibrancy setting and light/dark with no
        // second palette to maintain -- the mistake ChargeGreen's phone-side
        // re-declaration made, which is why colours live in tokens here.
        //
        // containerColor stays tertiaryContainer underneath. The gradient paints
        // over it, but it is what contentColorFor() reads to pick the text colour,
        // and all three stops are container-toned, so the contrast that colour was
        // chosen for holds across the whole sweep.
        background = {
            val scheme = MaterialTheme.colorScheme
            val brush = remember(scheme.tertiaryContainer, scheme.primaryContainer, scheme.secondaryContainer) {
                Brush.linearGradient(
                    // Diagonal rather than vertical: a pebble is much wider than it
                    // is tall when collapsed, so a vertical sweep would compress to
                    // a flat band and read as a slightly-off solid fill.
                    0f to scheme.tertiaryContainer,
                    0.55f to scheme.primaryContainer.copy(alpha = 0.55f),
                    1f to scheme.secondaryContainer.copy(alpha = 0.65f),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            }
            Spacer(Modifier.matchParentSize().background(brush))
        },
        headerAction = PebbleHeaderAction(
            label = "Summarize",
            icon = Icons.Filled.AutoAwesome,
            onClick = { vm.summarizeCar(v) },
            pending = busy,
        ),
    ) {
        // On the flip cover this tile fills the screen; two short text lines centred
        // in it read as a big empty purple void. Lead with a proper glance hero (big
        // icon + heading + status line) like the other cover tiles, then the copy.
        if (LocalForceExpanded.current) {
            // Shared CoverHero rhythm (converged 34dp icon + headline + status subline),
            // so the AI tile matches Climate/Info/Diagnostics/etc instead of its old
            // ad-hoc 48dp centered column.
            CoverHero(
                icon = Icons.Filled.AutoAwesome,
                value = "AI summary",
                subline = when {
                    busy -> "Summarizing on-device…"
                    summary != null -> "On-device Gemini Nano · updated"
                    else -> "On-device Gemini Nano"
                },
            )
        }
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
internal fun PaletteSwatch(
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
internal fun CustomPaletteSwatch(
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
            // Manual haptics?.click() dropped: MorphIconButton fires it. This was
            // the one bare IconButton in the file that remembered to.
            MorphIconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
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
internal fun PaletteEditorDialog(
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
                MorphIconButton(onClick = {
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
                // ToggleRow, not a hand-rolled label+Switch: identical layout and the
                // same bodyMedium label, but it brings the morph pill track, the
                // toggleOn/toggleOff haptics and the single-focus-stop TalkBack
                // semantics that every other boolean setting in the app has.
                ToggleRow("Custom secondary", useSecondary) { useSecondary = it }
                AnimatedVisibility(useSecondary, enter = collapseEnter(), exit = collapseExit()) {
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
                // ToggleRow, not a hand-rolled label+Switch: identical layout and the
                // same bodyMedium label, but it brings the morph pill track, the
                // toggleOn/toggleOff haptics and the single-focus-stop TalkBack
                // semantics that every other boolean setting in the app has.
                ToggleRow("Custom tertiary", useTertiary) { useTertiary = it }
                AnimatedVisibility(useTertiary, enter = collapseEnter(), exit = collapseExit()) {
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
        targetValue = if (active || pressed) MorphedCornerPercent else PillCornerPercent,
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
 * The morph family's icon-only member.
 *
 * [MorphButton] is the wrong tool for a bare icon affordance -- a snackbar
 * action, a text-field's clear button, a 28dp edit glyph in a swatch grid -- since
 * it would wrap each one in a filled pill and change the design rather than unify
 * it. So this keeps [IconButton]'s containerless chrome and 40dp target exactly,
 * and adds the two things every other member of the family provides and these
 * were missing:
 *
 *  - **The click haptic.** Of the six bare IconButtons in this file, exactly ONE
 *    remembered to call `haptics?.click()` itself. Every Morph* control fires one;
 *    a containerless icon is no less of a button to the finger.
 *  - **A press response.** With no container there is no corner to morph, so the
 *    equivalent is a scale dip, on the family's own [SoftDamping] spring.
 *
 * Same parameter shape as [IconButton] so converting a call site is mechanical.
 * If you are converting one that already called the haptic by hand, delete that
 * call -- it fires here now, and two in a row is a stutter, not emphasis.
 */
@Composable
fun MorphIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    val pressed by interactionSource.collectIsPressedAsState()
    // Already optimal, and worth a note so nobody "fixes" it: `by` here costs nothing, because
    // what decides the phase of a snapshot read is WHERE the getter runs, not whether the
    // property is delegated. `scale` is referenced only inside the graphicsLayer BLOCK below,
    // so the read happens when Compose invokes that block -- composition and layout are
    // skipped. Google's own guidance shows exactly this shape (`val color by animateColorBetween(...)`
    // read inside `drawBehind { }`).
    //
    // I briefly rewrote this to `val scale = animateFloatAsState(...)` plus `scale.value`,
    // believing the delegated form forced a composition read. It does not; the two are
    // identical here. Reverted, because a comment asserting a difference that does not exist
    // teaches the next reader a false rule.
    //
    // The real audit question for the ~61 `by animate*AsState` sites in this project is not
    // `by` vs `=`. It is whether the value is read in the composable BODY (recomposes every
    // frame -- e.g. passed to `Modifier.padding(...)`, a `TextStyle`, or a size) or inside a
    // lambda modifier like `graphicsLayer {}` / `offset {}` / `drawBehind {}` (already
    // deferred, nothing to do). This site is the second kind.
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMedium),
        label = "morphIconPress",
    )
    IconButton(
        onClick = { haptics?.click(); onClick() },
        // On the button, not the icon: scaling the icon alone shrinks the glyph
        // inside a target that stays put, which reads as a glitch rather than a
        // press.
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        interactionSource = interactionSource,
        content = content,
    )
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
        // Bigger, thumb-friendly hit targets on the cover screen (operated by a
        // thumb on a ~1-inch square) than on the phone (mouse-precise finger taps in
        // a full pebble). LocalForceExpanded is true only on the cover.
        val coverTargets = LocalForceExpanded.current
        val groupBtnSize = if (coverTargets) 58.dp else 50.dp
        val actionIconSize = if (coverTargets) 26.dp else 22.dp
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            groupActions.forEachIndexed { i, action ->
                MorphButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    contentPadding = PaddingValues(0.dp),
                    shapeForCorner = { cp -> connectedGroupShape(i, segmentCount, cp) },
                    modifier = Modifier.size(groupBtnSize),
                ) { Icon(action.icon, contentDescription = action.contentDescription, modifier = Modifier.size(actionIconSize)) }
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
                // ControlHeight tall, so the button is vertically centred in it);
                // taller on the cover for a thumb.
                modifier = Modifier.heightIn(min = groupBtnSize),
            ) {
                val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
                MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = actionIconSize)
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
internal fun Pebble(
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
    /** Drawn BEHIND the header and body, clipped to the pebble's own shape.
     *  [PebbleShell] has always had this -- the hero's car photo uses it -- but
     *  [Pebble] did not forward it, so a per-car pebble could only ever have a flat
     *  fill. Forwarded now, which is what lets the AI pebble carry a gradient
     *  without either of them growing a special case for it. */
    background: (@Composable BoxScope.() -> Unit)? = null,
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
        background = background,
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
internal fun PebbleShell(
    expanded: Boolean,
    onToggle: () -> Unit,
    icon: ImageVector,
    title: String,
    vm: AppViewModel,
    dragHandle: Modifier = Modifier,
    summary: String? = null,
    /**
     * Trailing content on the TITLE row -- a headline stat that would otherwise need a
     * third row of its own. Null for every other pebble.
     *
     * A composable slot rather than a string: the hero puts a styled, derived readout
     * here ([ChargeStatsLine]), not a caption. It owns its own leading gap -- there is no
     * [Spacer] before it here -- so a pebble with no trailing stat doesn't pay for one, and
     * the expanded hero's title isn't squeezed by a gap left behind an absent node.
     */
    titleTrailing: (@Composable () -> Unit)? = null,
    /**
     * Overrides the colour of [title] and [summary]. [Color.Unspecified] (the default)
     * inherits, which is what every pebble but the hero wants.
     *
     * The hero needs it because its `background` slot puts a PHOTO behind the header,
     * and the header is drawn over that with the surface's own content colour -- so an
     * expanded card rendered the car's name in near-black on a dark photo and it could
     * not be read. The photo already carries a scrim built for light text; nothing was
     * telling the text to be light. Reported from a real device.
     */
    titleColor: Color = Color.Unspecified,
    // onTitleWidth was deleted here. It reported the title's measured width so the hero could
    // offset its collapsed readout past the car name. That whole approach is gone: the numbers are
    // now trailing content ON this Row (see HeroCollapsedNumbers), so the Row positions them and
    // nothing needs to know how wide the name is.
    /**
     * Extra content in the header, under the title and [summary].
     *
     * A string is all `summary` can be, and the hero wants a graphical readout there when
     * collapsed: a mini charge bar plus its percentage. This is that slot and nothing more.
     * It renders inside the header's own text column, so it inherits the header's width,
     * padding and content colour, and sits above the chevron's row sibling rather than
     * competing with it for horizontal space.
     *
     * Null for every other pebble.
     */
    headerContent: (@Composable () -> Unit)? = null,
    /**
     * Whether the TITLE grows when this pebble expands.
     *
     * False for every pebble but the hero, and that is the point. The growth used to be
     * unconditional, so "Location", "Weather", "Diagnostics" and the rest all swelled from
     * titleMedium to headlineSmall on expand. On the hero it reads as the car's name taking
     * over the card it now fills; on a utility pebble it is just a heading changing size for
     * no reason, four of them doing it at once, and it fights the body content appearing
     * underneath.
     *
     * Also the only one where the cost is justified: the growth lerps a real font size, so
     * every frame misses the SINGLE-SLOT ParagraphLayoutCache and re-lays the text out. One
     * node doing that on one card is affordable; making it the default charged every pebble
     * for an effect only one of them wanted.
     */
    growTitleOnExpand: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    headerAction: PebbleHeaderAction? = null,
    forceExpanded: Boolean = false,
    /**
     * Drawn BEHIND the header and the collapsing body, inside the card's clip.
     *
     * A pebble is otherwise a plain vertical stack with no z-order, so nothing could sit
     * under the header. The hero needs that: its photo runs up behind the header row so
     * the title and the chevron overlay the top of the image.
     *
     * Whatever goes here is responsible for its own legibility. Header text lands on top
     * of it, and over an arbitrary car photo that text disappears -- the widget hit the
     * same thing and resolved it with a luminance check. A scrim under the text is the
     * cheap version and is what the hero does.
     *
     * Null for every other pebble, so nothing else gains a layer.
     */
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    // Collapsed = pill-soft corners; expanded morphs to a tighter rounded square. Direction
    // picks between the SAME two springs collapseEnter/collapseExit use for the height, rather
    // than one flat spec for both directions -- that used to run regardless of direction, so
    // the corners settled on their own schedule while the height was doing something else
    // (bouncing open, or -- when closing briefly bounced too -- overshooting shut in a way
    // that read as disconnected from the collapse itself). Matching each direction's spring
    // exactly is what keeps the corners and the height reading as one card in both directions,
    // even though open bounces and close (deliberately, now) doesn't -- see collapseExit's own
    // doc for why closing settled on a calm spring instead.
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else PebbleCornerCollapsed,
        animationSpec = if (expanded) {
            spring(dampingRatio = PebbleBounceDamping, stiffness = PebbleBounceStiffness)
        } else {
            spring(dampingRatio = PebbleCloseDamping, stiffness = PebbleBounceStiffness)
        },
        label = "pebbleCorner",
    )
    val fillHeight = LocalPebbleFillHeight.current
    // On the cover screen a pebble IS a cover tile -- same template as the
    // home tile and every other page (title band, centred body, actions
    // band). It used to be this same Card with the header row dropped and a
    // 30dp icon badge floating over the body's corner, which meant a pebble
    // page looked like a different kind of object from the home page and
    // named itself only to someone who already knew the iconography.
    // headerAction becomes the actions band, so the pebble's one control
    // lands in the same place, at the same size, as the home tile's four.
    if (fillHeight && expanded) {
        val act = headerAction?.takeIf { it.label.isNotEmpty() }
        CoverTile(
            title = title,
            icon = icon,
            subtitle = summary,
            containerColor = containerColor,
            scrollState = LocalCoverScrollState.current,
            actions = if (act == null) {
                null
            } else {
                {
                    CoverActionButton(
                        icon = act.icon,
                        label = act.label,
                        onClick = act.onClick,
                        active = act.active,
                        pending = act.pending,
                        enabled = act.enabled,
                    )
                }
            },
            body = content,
        )
        return
    }
    val pebbleShape = RoundedCornerShape(corner)
    // Off by default -- see Appearance.pebbleOutline's doc comment. Most
    // floating chrome always has a rim, but pebbles are the majority of
    // on-screen surface area, so a rim on every single one is a much bigger
    // visual commitment than one more floating button.
    val pebbleAppearance = LocalAppearance.current
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
            // Box, so `background` can draw BEHIND the header and body. A pebble is
            // otherwise a plain vertical stack with no z-order, which is why an image
            // could not sit under the header before this.
            Box(Modifier.fillMaxWidth()) {
                background?.invoke(this)
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
                    // Phone only. The cover screen never reaches here: PebbleShell
                    // returns above, through CoverTile, so a pebble on the cover is
                    // the same template as every other page there. What follows is
                    // the collapsible header + animated body card.
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
                            // The header grows and hardens as the pebble opens. Expanded, the
                            // hero's header sits over a photo, so bigger and higher-contrast
                            // is legibility rather than flourish -- and it makes opening feel
                            // like the card is coming forward instead of just getting taller.
                            //
                            // Interpolated on the theme's SPATIAL spec (type size is a spatial
                            // property) so it moves with the same physics as the expansion it
                            // belongs to, and lerped through real type steps rather than being
                            // scaled, so every frame is a genuine font size.
                            // A slow, lightly-bouncy spring, NOT the theme's default spatial
                            // spec. That default is tuned for a card's whole bounds, and driving
                            // a TYPE STEP with it read as rough: it is quick enough that a
                            // 16sp -> 24sp change lands in a handful of frames, and each of
                            // those frames is a genuine re-layout at a new font size, so what
                            // you see is a few discrete jumps rather than a glide.
                            //
                            // dampingRatio 0.62 gives a real overshoot -- the name grows a
                            // touch past its target and settles back -- and StiffnessVeryLow
                            // stretches it over enough frames for the intermediate sizes to
                            // read as motion instead of steps. Both halves matter: bounce with
                            // a fast spring is still steppy, and a slow spring without bounce
                            // is just a slower version of the same flat move.
                            // Only animates for the pebble that asked (the hero). For the
                            // rest the target is a constant 0, so the spring never leaves its
                            // resting value, titleStyle stays titleMedium, and the per-frame
                            // font-size relayout never happens at all.
                            val headerT by animateFloatAsState(
                                targetValue = if (expanded && growTitleOnExpand) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.62f,
                                    stiffness = Spring.StiffnessVeryLow,
                                ),
                                label = "pebbleHeaderGrow",
                            )
                            // Drawn at the LARGER size always and SCALED down, rather than
                            // lerping the font size. The lerp was the choppiness: a Text
                            // measures through ParagraphLayoutCache, which is single-slot, so a
                            // font size that changes every frame misses it every frame -- 100%
                            // invalidation, a full text relayout per frame, and the visible
                            // result is a few discrete steps rather than a glide. Scaling a
                            // layout measured ONCE is what Compose itself recommends for
                            // animated type, and it is draw-phase only.
                            //
                            // headlineSmall is the base and it scales DOWN, never up: text
                            // scaled down stays crisp, upscaling is what goes soft.
                            //
                            // transformOrigin pins the LEFT edge so the name grows out of its
                            // own start position instead of drifting sideways from the centre.
                            val titleStyle = MaterialTheme.typography.headlineSmall
                            // Ratio of the two real type steps, so the collapsed size still
                            // equals titleMedium exactly rather than a hand-picked number.
                            val collapsedTitleScale = with(LocalDensity.current) {
                                MaterialTheme.typography.titleMedium.fontSize.toPx() /
                                    MaterialTheme.typography.headlineSmall.fontSize.toPx()
                            }
                            // Plain arithmetic, not lerp(): this file imports the Color, TextStyle
                            // and Dp overloads of `lerp` but NOT the Float one from
                            // androidx.compose.ui.util, so a Float call does not resolve -- which
                            // is exactly how the first attempt at this broke the build. Spelling
                            // out the interpolation removes the dependency on which overload
                            // happens to be in scope.
                            val titleScale = if (!growTitleOnExpand) {
                                collapsedTitleScale
                            } else {
                                collapsedTitleScale + (1f - collapsedTitleScale) * headerT
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                title,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    // Reports the DRAWN size. graphicsLayer scales the drawing and
                                    // leaves the measured size alone, so without this the title's
                                    // box stayed headline-TALL while its glyphs were title-sized --
                                    // which made the row taller than the text in it and pushed
                                    // everything beside the name out of line.
                                    //
                                    // Measure once at headlineSmall, then report width and height
                                    // multiplied by the same scale the layer draws with, and place
                                    // the (still full-size) content centred on that smaller box so
                                    // scaling about its left-centre keeps the glyphs where the box
                                    // says they are. This is what lets `titleTrailing` sit against
                                    // the name's real edge rather than a headline-sized box.
                                    .layout { measurable, constraints ->
                                        val placeable = measurable.measure(constraints)
                                        val w = (placeable.width * titleScale).roundToInt()
                                        val h = (placeable.height * titleScale).roundToInt()
                                        layout(w, h) {
                                            placeable.place(0, (h - placeable.height) / 2)
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = titleScale
                                        scaleY = titleScale
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    },
                                style = titleStyle,
                                color = titleColor,
                                fontWeight = FontWeight.Bold,
                                // Cap at one line: at a large display/font size the
                                // header action button (SplitExpandButton, now width-
                                // bounded below) used to squeeze this weighted Column
                                // so a title like "Location"/"Weather"/"Diagnostics"
                                // wrapped and visually collided with the button. One
                                // line + ellipsis keeps the title on its own line.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Trailing content on the TITLE row, so a pebble that wants a
                            // headline stat does not need a third row for it. The hero puts
                            // its percentage and range here, which is what lets the collapsed
                            // card be name-and-numbers over a bar instead of three stacked
                            // lines with the bar stranded at the bottom.
                            //
                            // No Spacer before it any more, and no styling applied here:
                            // the slot owns both. The hero shows this only while collapsed,
                            // and a 10dp gap left behind when it goes would squeeze the
                            // expanded title for a node that is no longer in the row.
                            titleTrailing?.invoke()
                            }
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
                                        // Ellipsize a long summary ("Set a location")
                                        // instead of hard-clipping it to "Set a…".
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            headerContent?.invoke()
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
                    // Normal pebbles: animate the body sliding open/closed. fade = false on
                    // the exit -- StaggeredRevealColumn's rows own their own fade now (see
                    // collapseExit's own doc for why running a SECOND, block-level fade at the
                    // same time buried that per-row one and made closing look like it had no
                    // content animation at all).
                    AnimatedVisibility(
                        visible = expanded,
                        enter = collapseEnter(),
                        exit = collapseExit(fade = false),
                    ) {
                        // StaggeredRevealColumn, not a plain Column: every row pops in/out on
                        // its own as this cascades open/closed, instead of every row appearing
                        // together the instant the block-level AnimatedVisibility above reveals
                        // it. See that composable's own doc for why this is the ONE place that
                        // needed changing to give every pebble's rows this for free.
                        //
                        // `transition` here is `AnimatedVisibilityScope.transition` -- this
                        // lambda's implicit receiver, since it's the content of the
                        // AnimatedVisibility right above. Passing THAT (not a boolean) is what
                        // lets the row cascade register itself as part of the same Transition
                        // driving this card's own height/fade, so the card can't finish
                        // closing before the rows do -- see StaggeredRevealColumn's own doc.
                        StaggeredRevealColumn(
                            transition = transition,
                            // AnimatedVisibility only animates the whole block
                            // appearing and disappearing; content that changes
                            // WHILE expanded (an install step arriving, notes
                            // loading) still jumped the card's height. This
                            // animates those in place too.
                            modifier = Modifier.animateContentSize(
                                spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
                            ).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                            verticalGap = 8.dp,
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

internal class PebbleHeaderAction(
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
                    Text(
                        action.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        // Cap the label width so a long action ("Summarize",
                        // "Downloading…") at a large font size can't grow this button
                        // unbounded and squeeze the pebble title into wrapping/overlap.
                        // The label ellipsizes past the cap; the icon still identifies it.
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp),
                    )
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
internal fun MorphExpandButton(
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
    // Same reasoning one step further out: a Gen5W head unit reports nothing,
    // and neither does a backend with no trips endpoint. Kia US, Canada and
    // Europe all inherit the repository's empty default, so without this they
    // show the pebble and it never fills.
    if (!v.brand.supportsTrips) return
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
                val tMetric = LocalAppearance.current.unitSystem == "metric"
                // COVER SCREEN: a small "Recent trips" header + only the 3 most recent,
                // so the tile fits the small square without scrolling and you land at
                // the top. Phone keeps up to 8 with no header. Gated on forceExpanded.
                val coverGlance = LocalForceExpanded.current
                if (coverGlance) {
                    CoverHero(icon = Icons.Filled.Route, value = if (trips.size == 1) "1 trip" else "${trips.size} trips")
                }
                trips.take(if (coverGlance) 3 else 8).forEach { TripRow(it, metric = tMetric) }
            }
        }
    }
}

@Composable
private fun TripRow(trip: EvTrip, metric: Boolean = false) {
    // TripsPebble renders this list straight under the cover's CoverHero with no
    // color override of its own, so every Text below inherits whatever the
    // pebble's own container hands out -- surfaceVariant's onSurfaceVariant by
    // default. Pinning the primary date/distance line to full onSurface (it was
    // entirely unstyled before, not just muted) is the same "the important half
    // shouldn't be barely distinguishable from the caption below it" fix
    // StatusRow's own value already has.
    val primaryColor = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                tripDate(trip.startdate),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor,
            )
            trip.distance?.let {
                Text(formatTripDistance(it, metric), style = MaterialTheme.typography.bodyMedium, color = primaryColor)
            }
        }
        val pace = remember(trip, metric) { buildList {
            // Same fmtMinutes the watch's Trips screen uses for these two exact
            // fields -- without it, one trip read "95 min" here and "1h 35m" there.
            trip.driveMinutes?.let { add(fmtMinutes(it)) }
            trip.idleMinutes?.takeIf { it > 0 }?.let { add("${fmtMinutes(it)} idle") }
            // formatSpeedMph, not formatSpeed: these are mph (EvTrip's KDoc, corroborated
            // by its sibling `distance` being treated as miles on both surfaces), and
            // formatSpeed's input is km/h. 62 mph used to render as "38 mph" in imperial
            // and "62 km/h" in metric. `.value` is already Double, so no toDouble().
            trip.avgspeed?.value?.let { add("avg ${formatSpeedMph(it, metric)}") }
            trip.maxspeed?.value?.let { add("max ${formatSpeedMph(it, metric)}") }
        } }
        // Same color-role swap as DiagnosticsPebble's indented rows: onSurfaceVariant
        // is already full-alpha as a raw color, so its dimness is the ROLE, not
        // something an alpha bump alone would fix. Boosted on the cover, where this
        // whole list has no other contrast handling of its own.
        val captionColor = if (LocalForceExpanded.current) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        if (pace.isNotEmpty()) {
            Text(pace.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = captionColor)
        }
        val energy = remember(trip) { buildList {
            trip.usedKwh?.let { add("$it kWh used") }
            trip.regenKwh?.takeIf { it > 0 }?.let { add("$it kWh regen") }
        } }
        if (energy.isNotEmpty()) {
            Text(energy.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = captionColor)
        }
    }
}

private fun tripDate(raw: String?): String = com.bloo.bluelink.data.tripDate(raw)

/** "10 + 3 min" for a 13-minute request -- the per-command chunks
 *  [climateChunks] splits an auto-extended climate run into, shown on the
 *  Climate pebble's Run time slider so it's clear a request past the car's
 *  single-command cap becomes more than one command rather than one longer
 *  one. */
private fun climateChunksLabel(totalMinutes: Int): String =
    climateChunks(totalMinutes).joinToString(" + ") + " min"

// --- Car info (status + service + links combined) -------------------------

@Composable
private fun InfoPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val appearance = LocalAppearance.current
    val inApp = appearance.linksInApp
    val metric = appearance.unitSystem == "metric"
    val location = state.locations[v.vin]
    val odoInt = parseOdometerMiles(v.odometer)
    val plate = state.licensePlates[v.vin]
    val lastSvc = state.lastServiceMiles[v.vin]
    val interval = state.serviceIntervalMiles[v.vin]
    val nextDue = if (lastSvc != null && interval != null) nextServiceMiles(lastSvc, interval) else null
    val remaining = serviceDue(odoInt, lastSvc, interval)

    val ev = status?.evStatus
    val plugged = ev.isPluggedOrCharging

    // Tri-state: null (unknown -- no status yet, or the car hasn't reported lock state) must
    // NOT read as "Unlocked". A null summary is omitted by Pebble, so the header simply carries
    // no lock word until we actually know -- rather than asserting a state as fact in visible
    // text and to TalkBack. Matches CoverMainTile / StateControl, which already handle unknown.
    val infoSummary = status?.doorLock?.let { if (it) "Locked" else "Unlocked" }
    val coverGlance = LocalForceExpanded.current
    Pebble(v, "info", "Car info", Icons.Filled.Info, state, vm, dragHandle, summary = infoSummary) {
        // COVER SCREEN only: lead with a big lock-state hero. On the cover the info
        // tile drops its header (so the "Locked/Unlocked" summary is otherwise
        // buried as one row among ~15). A large icon + word makes it the glance
        // value. Phone is untouched (coverGlance = LocalForceExpanded, false there).
        if (coverGlance && status != null) {
            // Three-way: an unknown lock state (doorLock == null) shows a neutral "Unknown"
            // glance rather than a red "Unlocked", which would assert as fact a state the car
            // never reported. Locked/unlocked keep their existing icon + colour treatment.
            when (status.doorLock) {
                true -> CoverHero(
                    icon = Icons.Filled.Lock,
                    value = "Locked",
                    iconTint = MaterialTheme.colorScheme.primary,
                    valueColor = MaterialTheme.colorScheme.onSurface,
                )
                false -> CoverHero(
                    icon = Icons.Filled.LockOpen,
                    value = "Unlocked",
                    iconTint = MaterialTheme.colorScheme.error,
                    valueColor = MaterialTheme.colorScheme.error,
                )
                null -> CoverHero(
                    // Info (the pebble's own glyph, already imported) rather than a lock icon:
                    // showing either padlock would imply a state we don't have.
                    icon = Icons.Filled.Info,
                    value = "Lock state unknown",
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            status == null && state.refreshing -> Text("Fetching live status…")
            status == null -> Text("No status yet.")
            else -> {
                SectionLabel("Status")
                status.engine?.let { StatusRow("Vehicle", if (it) "On" else "Off") }
                // Absent when the lock state is unknown, matching the engine row above --
                // "Unlocked" was being shown for a car that simply hadn't reported it.
                status.doorLock?.let { StatusRow("Doors", if (it) "Locked" else "Unlocked") }
                status.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Doors open", it.joinToString(", ")) }
                status.windowOpen?.openLabels()?.takeIf { it.isNotEmpty() }
                    ?.let { StatusRow("Windows open", it.joinToString(", ")) }
                if (status.trunkOpen == true) StatusRow("Trunk", "Open")
                if (status.hoodOpen == true) StatusRow("Hood", "Open")
                if (status.acc == true) StatusRow("Accessory power", "On")
                // Absent when climate state is unknown (airCtrlOn null), like the engine/doorLock
                // rows above -- "Off" was being shown as fact for a car that never reported it.
                status.airCtrlOn?.let { StatusRow("Climate", if (it) "On" else "Off") }
                if (status.defrost == true) StatusRow("Defrost", "On")
                status.airTemp?.let { t ->
                    t.value?.let { StatusRow("Climate setpoint", degLabel(it, appearance.useFahrenheit, t.unit)) }
                }
                status.percentFor(state.hasBattery(v))?.let {
                    StatusRow(if (state.hasBattery(v)) "Charge" else "Fuel", "$it%")
                }
                status.rangeMiFor(state.hasBattery(v))?.let { StatusRow("Range", formatDistance(it, metric)) }
                status.battery?.batSoc?.let { StatusRow("12V battery", "$it%") }
                // Comfort heaters (read-only; mirror/rear-window heat track defrost).
                status.steerWheelHeat?.takeIf { it != 0 }?.let { StatusRow("Steering wheel heat", "On") }
                status.sideMirrorHeat?.takeIf { it != 0 }?.let { StatusRow("Mirror heat", "On") }
                status.sideBackWindowHeat?.takeIf { it != 0 }?.let { StatusRow("Rear defroster", "On") }
                // Resolved place name when geocoding's landed (same source the
                // Location pebble and the AI summary both use); raw coordinates
                // ONLY as the fallback until it does, never as the steady state --
                // this row used to show coordString() unconditionally, the one
                // place in the app that never even tried to resolve an address.
                location?.let { StatusRow("Location", state.placeNames[v.vin] ?: it.coordString()) }
                rememberRelativeTime(state.fetchedAt(v))?.let { StatusRow("Last refreshed", it) }

                if (plugged) {
                    SectionLabel("Charging")
                    ev?.minutesToFull
                        ?.let { StatusRow("Time to full", fmtMinutes(it)) }
                    chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
                    ev?.targetForCurrentPlug()?.let { StatusRow("Charge limit", "$it%") }
                }
            }
        }

        // "Service & identity" (VIN/plate/odometer/service) and the owner-links block
        // are lookup/management surfaces with no at-a-glance value on a ~1-inch cover
        // tile, and they're what overflows it into a long scroll. Show them only on
        // the phone (not coverGlance). Odometer stays visible on the cover as one
        // quick row since it's genuinely glanceable.
        if (coverGlance) {
            odoInt?.let { StatusRow("Odometer", formatDistance(it, metric)) }
        } else {
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
                    Brand.KIA, Brand.HYUNDAI_CA, Brand.GENESIS_CA, Brand.KIA_CA, Brand.HYUNDAI_EU -> Unit
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
    val a = LocalAppearance.current
    val fahrenheit = a.useFahrenheit
    val metric = a.unitSystem == "metric"
    val rows = remember(status, fahrenheit, metric) { buildList {
        status?.tirePressureLamp?.let { tp ->
            // No psi suffix. `TirePressure.all` was only ever populated FROM the warning lamp --
            // Kia read `tirePressure.all` (a 0/1 indicator) and Canada read
            // `tirePressureLamp.tirePressureLampAll` outright -- so this rendered "Warning · 1
            // psi" and "OK · 0 psi". No producer in this app has ever supplied a real
            // pressure, and both assignments are now deleted, so there is nothing to suffix.
            add(DiagRow("Tire pressure", if (tp.hasWarning) "Warning" else "OK"))
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
        status?.airTemp?.let { t ->
            t.value?.let { add(DiagRow("Climate setpoint", degLabel(it, fahrenheit, t.unit))) }
        }
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
        // fmtMinutes, not "$it min" -- the charge pebble's own "Time to full" row a
        // few hundred lines up already used it, so a 95-minute estimate read
        // "1h 35m" there and "95 min" here, in the same app on the same screen.
        status?.evStatus?.minutesToFull?.let { add(DiagRow("Time to full", fmtMinutes(it))) }
        status?.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
            ?.let { add(DiagRow("Doors open", it.joinToString(", "))) }
        if (status?.trunkOpen == true) add(DiagRow("Trunk", "Open"))
        if (status?.hoodOpen == true) add(DiagRow("Hood", "Open"))
        if (status?.doorLock == false && status.engine != true) add(DiagRow("Lock", "Car is unlocked while parked"))
    } }
    val diagSummary = remember(rows) { if (rows.isEmpty()) "No data" else "${rows.count { !it.indent }} checks" }
    // The count of actual problems, for the cover health-verdict hero below. diagSummary is a
    // *checks* count, not an issue count. The warning affordance is then just "any problem at
    // all" -- issueCount > 0 -- rather than a second hand-kept copy of these five predicates,
    // which is what this used to be (a parallel `hasWarning` ||-chain that had to stay in sync
    // with this list by hand). One source now; they can't drift.
    val issueCount = remember(status) {
        listOf(
            status?.tirePressureLamp?.hasWarning == true,
            status?.lowFuelLight == true,
            status?.washerFluidStatus == true,
            status?.breakOilStatus == true,
            status?.smartKeyBatteryWarning == true,
        ).count { it }
    }
    val hasWarning = issueCount > 0
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
        // COVER SCREEN only: a health-verdict hero — green check + "All systems OK",
        // or an error warning + "N issues" — so the tile reads at a glance instead of
        // as a flat list of ~12 rows. Gated on LocalForceExpanded (phone untouched).
        if (LocalForceExpanded.current && status != null) {
            CoverHero(
                icon = if (hasWarning) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                value = if (hasWarning) (if (issueCount == 1) "1 issue" else "$issueCount issues") else "All systems OK",
                iconTint = if (hasWarning) MaterialTheme.colorScheme.error else ChargeGreen,
                valueColor = if (hasWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (rows.isEmpty()) {
            Text(
                "No diagnostics yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { row ->
            if (row.indent) {
                // DiagnosticsPebble has no cover-vs-phone split of its own -- this whole
                // ~12-row list renders on the cover too, below the health-verdict hero.
                // The label stayed at the fixed dim onSurfaceVariant role there, same
                // class of issue StatusRow's label had; boosted on the cover the same
                // way. The value used to be entirely unstyled (inheriting the pebble's
                // own ambient onSurfaceVariant content color) rather than pinned to a
                // legible tone the way StatusRow's own value already is -- an indented
                // sub-row's VALUE is still the thing a user is actually checking.
                // onSurfaceVariant is already full-alpha as a raw theme color -- its
                // dimness is the ROLE itself (a lower-contrast RGB against the
                // surface), not an alpha multiply, so unlike StatusRow/CoverHero this
                // needed a color swap, not an alpha bump, to actually read stronger.
                val indentLabelColor = if (LocalForceExpanded.current) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
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
                        color = indentLabelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
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
    val fahrenheit = LocalAppearance.current.useFahrenheit
    var tempF by remember(v.vin) { mutableIntStateOf(DEFAULT_CLIMATE_TEMP_F) }
    var duration by remember(v.vin) { mutableIntStateOf(DEFAULT_CLIMATE_DURATION_MIN) }
    var defrost by remember(v.vin) { mutableStateOf(false) }
    var steeringHeat by remember(v.vin) { mutableStateOf(false) }
    var driver by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var passenger by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearLeft by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var rearRight by remember(v.vin) { mutableStateOf(SeatLevel.OFF) }
    var settingsLoaded by remember(v.vin) { mutableStateOf(false) }

    // Copy a ClimateRequest's nine fields into the sliders' state. Defined up here so the
    // restore effect just below and the preset-apply buttons further down share ONE copy of the
    // assignment -- it was written out twice, byte-for-byte, and "restore last-used" and "apply
    // preset" are the same operation (set the sliders from a request). Captures only the nine
    // `var` setters above it. NOT reused by the watch-sync effect below, which maps through
    // SeatLevel.fromApi and so is genuinely different.
    val applyRequest: (ClimateRequest) -> Unit = { r ->
        tempF = r.tempF
        duration = r.durationMinutes
        defrost = r.defrost
        steeringHeat = r.steeringWheelHeat
        driver = r.seatFrontLeft
        passenger = r.seatFrontRight
        rearLeft = r.seatRearLeft
        rearRight = r.seatRearRight
    }

    // Restore the car's last-used climate settings the first time the pebble shows.
    LaunchedEffect(v.vin) {
        vm.loadSavedClimate(v)?.let(applyRequest)
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
    // applyPreset was here; it was the same body as applyRequest (defined above, next to the
    // sliders' state). The preset buttons below call applyRequest directly now.
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
                        applyRequest(matchingPreset.request)
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
        // COVER SCREEN only: lead with a big on/off + setpoint hero so the climate
        // tile reads at a glance instead of opening on a wall of sliders. Gated on
        // LocalForceExpanded (phone untouched); shown even while driving.
        if (LocalForceExpanded.current) {
            CoverHero(
                icon = Icons.Filled.AcUnit,
                value = if (climateOn) "On" else "Off",
                iconTint = if (climateOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                valueColor = if (climateOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                trailing = status?.airTemp?.let { t -> t.value?.let { degLabel(it, fahrenheit, t.unit) } },
            )
        }
        if (driving) {
            if (climateOn) {
                Text(
                    "Climate is on at the car. It ignores app commands while you're driving, so this is read-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                )
                status?.airTemp?.let { t ->
                    t.value?.let { StatusRow("Set to", degLabel(it, fahrenheit, t.unit)) }
                }
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
                    applyRequest(preset.request)
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
        // Its own PopVisible: weather can arrive AFTER the pebble is already open (it's
        // a separate fetch), so this section pops in live rather than only ever being
        // present from the first frame.
        PopVisible(visible = weather != null) {
            val w = weather
            if (w != null) {
                val ambientF = ambientFahrenheit(w.tempC)
                val smartTarget = smartClimateTargetF(ambientF)
                val targetLabel = degLabel(smartTarget.toString(), fahrenheit)
                val ambientLabel = degLabel(ambientF.toString(), fahrenheit)
                val smartLabel = if (smartClimateIsCooling(ambientF)) "Cool to $targetLabel" else "Heat to $targetLabel"
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        "It's $ambientLabel where your car is. Smart climate is targeting $targetLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
                    )
                }
            }
        }

        SectionLabel("Controls")

        // Show the set temperature when climate is running, with an animated entrance.
        AnimatedVisibility(
            visible = climateOn,
            enter = collapseEnter(),
            exit = collapseExit(),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Set temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bloo.uicommon.AnimatedValue(
                    degLabel(tempF.toString(), fahrenheit),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    reduceMotion = LocalReduceMotion.current,
                )
            }
        }

        // Was a hand-rolled version of the same blue->green->warm mapping
        // uicommon.tempColor() now centralizes (shared with the watch, which
        // had drifted to a different, unanimated palette).
        val tempRange = CLIMATE_TEMP_RANGE_F.first.toFloat()..CLIMATE_TEMP_RANGE_F.last.toFloat()
        val tempColor = com.bloo.uicommon.tempColor(tempF, tempRange.start, tempRange.endInclusive)
        // The label + value readout is the same in either unit -- only degLabel's
        // suffix (°F/°C) and the slider below differ -- so it's hoisted out of the
        // branch. RollingNumber (used for the hero's %/range) rather than the plain
        // AnimatedValue this had: it rolls the DIRECTION the value actually moved (up
        // when dragged warmer, down when cooler) instead of always sliding one way.
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RollingNumber(
                text = degLabel(tempF.toString(), fahrenheit),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = tempColor,
            )
        }
        if (fahrenheit) {
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
            AnimatedSlider(
                value = tempC.toFloat(),
                onValueChange = { tempF = (it * 9 / 5f + 32).roundToInt() },
                valueRange = 17f..28f,
                steps = 10,
                accent = tempColor,
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                "Run time",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            // RollingNumber, not StepRow's built-in roll: StepRow's AnimatedContent
            // always slides the same direction regardless of which way the value
            // moved, which reads oddly on a slider you're actively dragging both
            // ways. RollingNumber rolls up when the minutes increase, down when
            // they decrease, matching every other draggable number in the app.
            RollingNumber(
                text = "$duration min",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        AnimatedSlider(
            value = duration.toFloat(),
            // Extended range: the car itself has no single command past
            // CLIMATE_DURATION_RANGE's 10-minute cap -- a request beyond that
            // is auto-chained into follow-up commands instead (see
            // AppViewModel.startClimate / ClimateExtendWorker), so the slider
            // can go further than any one command actually could.
            onValueChange = { duration = it.roundToInt() },
            valueRange = CLIMATE_EXTENDED_DURATION_RANGE.first.toFloat()..CLIMATE_EXTENDED_DURATION_RANGE.last.toFloat(),
            steps = CLIMATE_EXTENDED_DURATION_RANGE.last - CLIMATE_EXTENDED_DURATION_RANGE.first - 1,
        )
        AnimatedVisibility(
            visible = duration > CLIMATE_DURATION_RANGE.last,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            Text(
                "Sent as ${climateChunksLabel(duration)}, continued automatically",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
        enter = collapseEnter(Alignment.Bottom),
        exit = collapseExit(Alignment.Bottom),
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
    // Bare "°" rather than degLabel's "°F"/"°C": this is a compact one-line summary
    // where the unit is already established by everything around it. The CONVERSION
    // is shared now though -- this used to re-inline the °F-to-°C arithmetic, so the
    // rounding rule lived here as well as in degLabel and could drift from it.
    parts += "${degValue(req.tempF.toDouble(), fahrenheit)}°"
    if (req.defrost) parts += "Defrost"
    val seats = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
    if (seats.any { it.isHeat }) parts += "Heat"
    if (seats.any { it.isCool }) parts += "Cool"
    if (req.steeringWheelHeat) parts += "Wheel"
    return parts.joinToString(" · ")
}

/**
 * The animated corner shapes for a two-segment "split pill" -- one where the two
 * halves meet at a gap. Returns `(left, right)`: each half is pill-rounded on its
 * OUTER edge and takes a small nub radius on the INNER edge facing the gap, and both
 * morph to a shared rounded-rectangle radius when [morphed] (pressed/applied). The
 * preset pill and the charge-limit pill are the two split pills in the app and had
 * byte-identical corner plumbing; this owns the two `animateDpAsState`s and the
 * mirrored shapes so their motion can't drift.
 */
@Composable
private fun rememberSplitPillShapes(morphed: Boolean): Pair<Shape, Shape> {
    val outer by animateDpAsState(
        if (morphed) 16.dp else 50.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitPillOuter",
    )
    val inner by animateDpAsState(
        if (morphed) 16.dp else 10.dp,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "splitPillInner",
    )
    val left = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
    val right = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
    return left to right
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
    // Outer edge = full pill when idle, rounded-rectangle when applied/pressed;
    // inner edge (facing the gap) = small nub when idle, matching outer when applied.
    val (leftShape, rightShape) = rememberSplitPillShapes(morphed)
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
            shape = leftShape,
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
            shape = rightShape,
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

    val (leftShape, rightShape) = rememberSplitPillShapes(leftPressed)
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
                shape = leftShape,
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
                shape = rightShape,
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
    val plugged = ev.isPluggedOrCharging
    val pending = state.isPending(v.vin, "charge")
    val limitPending = state.isPending(v.vin, "chargeLimit")
    val summary = when {
        charging -> "Charging"
        plugged -> "Plugged in · idle"
        else -> "Not plugged in"
    }

    // Separate AC (home / level-2) and DC (fast) charge-limit targets, each
    // seeded to a healthy default until the car's real targets load in. The
    // seeds are the shared DEFAULT_*_CHARGE_LIMIT_PCT constants, so the phone
    // seed can't drift from the watch/wire defaults (this once defaulted BOTH
    // to 80%, so tapping "Set" before the real DC target loaded pushed it low).
    // Both pills' "Set" sends BOTH values together (setChargeLimits(v,
    // acLimit, dcLimit)), so leaving one un-seeded at a wrong default meant
    // tapping "Set" on just the AC pill silently reset a DC target that had
    // never actually been what it was seeded to -- and vice versa.
    var acLimit by remember(v.vin) { mutableIntStateOf(DEFAULT_AC_CHARGE_LIMIT_PCT) }
    var dcLimit by remember(v.vin) { mutableIntStateOf(DEFAULT_DC_CHARGE_LIMIT_PCT) }
    // Seeded INDEPENDENTLY, one latch each. A single `limitsSeeded` flag was set as soon as
    // EITHER limit arrived, and the effect then returned early forever -- so a car that reports
    // its AC target first and its DC target on a later poll (or not in the same payload) left
    // dcLimit pinned to the hardcoded 90 and could never pick the real one up.
    //
    // That is not a display bug. The note above records that both pills' "Set" sends BOTH values
    // together, because setChargeLimits writes them as a pair -- so tapping Set on the AC pill
    // pushed a DC limit of 90 to the CAR, a value the user never chose and the car may never have
    // had. Latching per limit shrinks that to the case where a limit has genuinely never been
    // reported, instead of the far commoner case where it merely arrived second.
    //
    // Canada is the extreme case of this: CanadaApi never populates reservChargeInfos at all,
    // so on those cars neither latch could ever close. That is exactly why the pills below are
    // hidden for Canada (see Brand.supportsChargeLimits) -- the seeding here would never fire,
    // Set would only ever send the 80/90 defaults, so the whole editable control is suppressed.
    var acSeeded by remember(v.vin) { mutableStateOf(false) }
    var dcSeeded by remember(v.vin) { mutableStateOf(false) }
    LaunchedEffect(v.vin, ev?.reservChargeInfos) {
        if (!acSeeded) ev?.reservChargeInfos?.level(1)?.let { acLimit = it; acSeeded = true }
        if (!dcSeeded) ev?.reservChargeInfos?.level(0)?.let { dcLimit = it; dcSeeded = true }
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
        // COVER SCREEN only: lead with the big charge %/range/charging-state hero
        // (the same ChargeFuelBar the cover "main" tile uses), so the charge tile
        // opens on the number that matters instead of just two limit sliders. On the
        // phone this pebble sits directly under the car's HeroHeader (which already
        // shows ChargeFuelBar), so we DON'T duplicate it there — gated on forceExpanded.
        if (LocalForceExpanded.current) {
            ChargeFuelBar(
                status,
                state.hasBattery(v),
                state.hasFuel(v),
                state.drivingLabel(v),
                metric = LocalAppearance.current.unitSystem == "metric",
            )
            // No trailing Spacer — the cover shell's spacedBy(10.dp) owns the gap, so
            // the hero-to-content rhythm matches every CoverHero tile (was 26dp here).
        }
        // Its own PopVisible: this row arrives/leaves live while the pebble is open --
        // plugging or unplugging the car doesn't require re-expanding to see it change.
        PopVisible(visible = plugged) {
            chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
        }
        // Charge-limit editing is shown only for brands that can actually report the
        // targets. Canada can't (reservChargeInfos is always null), so the sliders would
        // sit on the 80/90 display defaults and "Set" would push a value the user never
        // chose to the car -- so we hide them entirely there. Start/Stop and the charging
        // hero above stay; only the editable limits go. See Brand.supportsChargeLimits.
        if (v.brand.supportsChargeLimits) {
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
        }
    }
}

/**
 * The energy pebble for a gas/hybrid car: fuel level + range, no charge UI at
 * all. Occupies the same "charge" slot so order/collapse state carry over.
 */
@Composable
private fun FuelPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val metric = LocalAppearance.current.unitSystem == "metric"
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
        // COVER SCREEN only: lead with a big fuel-% hero so the gas tile gets the same
        // glance treatment the EV Charge tile gets from ChargeFuelBar (it previously
        // fell straight to two dim StatusRows). Gated on LocalForceExpanded → phone
        // untouched.
        if (LocalForceExpanded.current && status != null) {
            CoverHero(
                icon = Icons.Filled.LocalGasStation,
                value = fuelPct?.let { "$it%" } ?: "--",
                subline = range?.let { "${formatDistance(it, metric)} to empty" },
            )
        }
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
 * A climate setpoint rendered in the user's chosen unit. Non-numeric values pass
 * through with a bare degree sign.
 *
 * [sourceUnit] is the API's own unit code for this value -- 0 Celsius, 1
 * Fahrenheit. It has to be forwarded rather than dropped: this file-private
 * wrapper SHADOWS the shared function for every call site in this file, so a
 * two-argument version here silently pinned all of them to the old
 * assume-Fahrenheit behaviour no matter what the shared one learned to do.
 * That is exactly what happened -- the four setpoint call sites were updated
 * to pass the unit and failed to compile against this wrapper.
 */
private fun degLabel(valueF: String, fahrenheit: Boolean, sourceUnit: Int? = null): String =
    com.bloo.bluelink.data.degLabel(valueF, fahrenheit, sourceUnit)

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
internal fun VibrancySlider(appearance: SettingsStore.Appearance, vm: AppViewModel) {
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
    val fahrenheit = LocalAppearance.current.useFahrenheit
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
        val coverGlance = LocalForceExpanded.current
        AnimatedVisibility(
            visible = location == null,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            Text("Tap Locate to query the car's current position.")
        }
        // Mirror of the "not located yet" AnimatedVisibility above -- same
        // pebble, same boolean flip, only the empty side had the treatment.
        AnimatedVisibility(
            visible = location != null,
            enter = collapseEnter(Alignment.Bottom),
            exit = collapseExit(Alignment.Bottom),
        ) {
            val loc = location
            if (loc != null) {
                Column {
                // COVER SCREEN: lead with the place-name hero (the cover drops the header
                // where the place summary otherwise shows), and shrink the map so hero +
                // map + coords + weather + button fit without overflowing the ~1-inch tile.
                if (coverGlance) {
                    // The subline used to always be the raw coordinate string, even
                    // once `place` had resolved into the headline right next to it --
                    // showing an address and its own coordinates in the same glance.
                    // Only fall back to coordinates here while nothing better exists
                    // yet; once an address resolves, it's the only thing shown.
                    CoverHero(
                        icon = Icons.Filled.LocationOn,
                        value = place ?: "Located",
                        subline = if (place == null) "Resolving address…" else null,
                    )
                }
                CarMap(
                    loc,
                    Modifier
                        .fillMaxWidth()
                        .height(if (coverGlance) 130.dp else 220.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                // Same reasoning as the cover hero above: a resolved address is
                // already the pebble's header/summary, so a permanent raw-coordinate
                // row here was redundant with it every single time -- exactly what
                // "should be an address, not coordinates" was pointing at. Only shown
                // as a fallback while geocoding hasn't (yet, or ever) resolved a name.
                if (!coverGlance && place == null) StatusRow("Location", loc.coordString())
                // Weather where the car is parked. Fetched lazily once we have a fix.
                LaunchedEffect(loc.latitude, loc.longitude) { vm.loadCarWeather(v) }
                // Its own PopVisible: weather can arrive AFTER this pebble is already
                // open (it's a separate fetch triggered above), so this row pops in
                // live rather than only ever being present from the first frame --
                // same idiom the Climate pebble's smart-climate section uses.
                val weather = state.carWeather[v.vin]
                PopVisible(visible = weather != null) {
                    val w = weather
                    if (w != null) WeatherStripe(w, fahrenheit, place ?: "At the car")
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
    val appearance = LocalAppearance.current
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
                // COVER SCREEN: center the icon+temp and make the temp bigger so the
                // tile reads as a weather face; the phone keeps the left-aligned
                // icon+column layout. Gated on LocalForceExpanded.
                val coverGlance = LocalForceExpanded.current
                // Only up-size the temp when the user's font scale is modest — at a
                // large display/font size (the mom's setup) displayMedium + the fixed
                // 64dp icon can exceed the narrow cover width and ellipsize the temp
                // to "72…". Above ~1.15x, keep displaySmall so the value stays whole.
                val bigTemp = coverGlance && LocalDensity.current.fontScale <= 1.15f
                Row(
                    modifier = if (coverGlance) Modifier.fillMaxWidth() else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (coverGlance) Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                                            else Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        weatherIcon(w.condition, w.isDay),
                        contentDescription = w.condition.label,
                        tint = tint,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(if (coverGlance) Modifier else Modifier.weight(1f)) {
                        RollingNumber(
                            text = w.tempLabel(fahrenheit),
                            style = if (bigTemp) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(w.condition.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        appearance.weatherLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // No trailing Spacer — the cover shell's spacedBy owns the gap uniformly.
                StatusRow("Feels like", w.feelsLikeLabel(fahrenheit))
                w.highLowLabel(fahrenheit)?.let { StatusRow("High / low", it) }
                // Humidity + wind are secondary; hide them on the cover so it reads as
                // a clean weather face (feels-like + high/low stay).
                if (!coverGlance) {
                    w.humidity?.let { StatusRow("Humidity", "$it%") }
                    StatusRow("Wind", formatSpeed(w.windKph, appearance.unitSystem == "metric"))
                }
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
        val tilePx = MapTiles.TILE_PX.toFloat()
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val span = MapTiles.span(zoom)
        val xTileF = MapTiles.tileX(location.longitude, zoom)
        val yTileF = MapTiles.tileY(location.latitude, zoom)
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
                val wrappedX = MapTiles.wrapX(tx, zoom)
                val offX = tx * tilePx - originX
                val offY = ty * tilePx - originY
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(MapTiles.tileUrl(zoom, wrappedX, ty))
                        // OSM returns a "blocked" placeholder tile to clients whose
                        // User-Agent doesn't identify the app. This one used to read
                        // "Bloo Bluelink companion app" -- no version, no contact URL,
                        // i.e. still shaped like the string that gets blocked, while
                        // the widget and watch had already been fixed.
                        .setHeader("User-Agent", MapTiles.userAgent("Android"))
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
internal fun CropScreen(vin: String, uriString: String, onCancel: () -> Unit, onSave: (String) -> Unit) {
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
// Moved to SettingsScreen.kt (3,407 lines). See that file's header for why, and for why
// the two commits before it -- UiTokens.kt, then the private -> internal promotions --
// had to come first.


// --- Small reusable pieces ------------------------------------------------

@Composable
internal fun StatusRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        // Top-align so that if the value wraps to a 2nd line (a long value at a
        // large display/font size), the label stays anchored to the first line
        // rather than floating to the vertical center of a now-taller row.
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            // MutedContentAlpha (0.7) on the phone, where LocalContentColor is usually
            // full onSurface and 0.7 reads as a deliberately secondary label. On the
            // cover every pebble's default container is surfaceVariant, whose paired
            // content tone is ALREADY the dimmer onSurfaceVariant role -- StatusRow is
            // the single most-reused row in the app (Diagnostics, Trips, Charge,
            // Weather, ...), so this one compounding was the largest contributor to
            // "flip mode is a contrast nightmare". 0.92 on the cover, unchanged
            // elsewhere: same fix CoverTile's own subtitle already got.
            color = LocalContentColor.current.copy(
                alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
            ),
            // Without a cap, at a large display size the value cell (below) used to
            // take its full intrinsic width first, starving this weighted label into
            // a sliver — and a single-word label ("Coordinates", "Email", "VIN")
            // with no room to break at a space then wrapped CHARACTER-by-character
            // ("Coordin/ates"). One line + ellipsis keeps the label intact; giving
            // the value its own weight (below) stops it from crushing the label in
            // the first place. Mirrors the correctly-built SyncInfoRow.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        // Right-aligning Box that OWNS half the row (weight(1f), which fills its
        // allocation) with the value end-aligned inside it. This keeps the classic
        // label-left / value-right column: a SHORT value ("Off", "90%", "Locked")
        // sits flush to the row's right edge, while a LONG value (coordinates, VIN,
        // email) is bounded to this half and wraps to a 2nd line instead of crushing
        // the label into character-by-character wrapping. (An earlier version put
        // weight(1f, fill = false) directly on the value; because AnimatedValue's
        // leaf text hugs its content, fill=false made a short value measure to its
        // intrinsic width and pack just past row-center — floating in the middle
        // with dead space to its right, since textAlign=End has no room to act in a
        // content-width box. The filling Box gives End something to align against.)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // Was a hand-rolled AnimatedContent + WiggleText -- uicommon's shared
            // AnimatedValue already implements this (used elsewhere in this file
            // and now watch's ChargeRing). Colour pinned to full-strength onSurface
            // rather than inherited -- Pebble's Card sets its content color from
            // containerColor (usually surfaceVariant), so an uncoloured value here
            // rendered at onSurfaceVariant strength, barely distinguishable from the
            // dimmed label right next to it despite being the important half.
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = LocalTextStyle.current.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                ),
                maxLines = 2,
                reduceMotion = LocalReduceMotion.current,
            )
        }
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
internal fun StepRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // Cap at 2 lines so a long label ("Text & layout scale") wraps cleanly at
        // spaces instead of the value (short: "130%") crushing it mid-word at a
        // large font size.
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        // Roll the value when it changes (e.g. dragging a slider).
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stepValue",
        ) { v -> Text(v, fontWeight = FontWeight.Medium, color = valueColor, maxLines = 1) }
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
            // Cap at 2 lines: the toggle track is fixed-width so it can't be pushed
            // off, but a long setting label at a large font size should wrap at
            // spaces to two lines rather than growing the row indefinitely.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
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
internal fun SeatConfigRow(
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
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            // The two chips are fixed-width; cap the label so a long seat name at a
            // large font size wraps at spaces rather than being crushed mid-word.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
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

/** A label/value row inside the sync status block: muted label on the left,
 *  emphasised value on the right (monospaced for the File ID so it reads as a
 *  code to compare across devices). */
@Composable
internal fun SyncInfoRow(
    label: String,
    value: String,
    valueMono: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontFamily = if (valueMono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
internal fun SyncDevicesSection(state: UiState, vm: AppViewModel) {
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
        "Drag to reorder. The top device is primary, the source of truth the others follow.",
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

    // Advisory: if a peer hasn't checked in for a while but this device just
    // synced, it likely drifted onto a DIFFERENT Drive file (a device can't see
    // another's file directly — the File ID at the top is the real cross-check).
    val now = System.currentTimeMillis()
    val stalePeer = devices.any { it.id != state.thisDeviceId && it.lastSeenMs > 0 && now - it.lastSeenMs > STALE_DEVICE_MS }
    if (stalePeer) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "A device hasn't synced in a while. If it's still in use, check its File ID matches the one under Diagnostics. Otherwise it's on a different file. Reconnect it via Change Drive file → Open from Drive.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (renaming) {
        var draft by remember { mutableStateOf(state.syncDeviceName) }
        val scheme = MaterialTheme.colorScheme
        // Standardized on the shared GlassAlertDialog shell (was the legacy
        // BlooDialog, now removed). Stacked full-width buttons.
        GlassAlertDialog(
            onDismissRequest = { renaming = false },
            icon = Icons.Filled.Smartphone,
            title = "Rename this device",
            text = {
                Text(
                    "Shown in the devices list on all your synced devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                // Styled to match every other text field in the app (18dp FieldShape,
                // borderless surface fill) rather than a default outlined box, which
                // looked generic against the frosted dialog.
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Device name") },
                    placeholder = { Text(Build.MODEL ?: "This device") },
                    singleLine = true,
                    shape = FieldShape,
                    colors = borderlessFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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
                    enabled = draft.isNotBlank(),
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
        // Shared default, not its own 0.9 -- see glassContainerAlpha's own doc
        // for why every frosted surface takes the one value now.
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = glassContainerAlpha())
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
            MorphIconButton(onClick = onRename) {
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
internal fun DriveSyncSetupDialog(
    onDismissRequest: () -> Unit,
    onSaveToDrive: () -> Unit,
    onOpenFromDrive: () -> Unit,
    // True when this device has synced before / knows about other devices. In
    // that case "Save to Drive" would create a SEPARATE new file (Google Drive
    // allows duplicate names) — the exact trap that leaves two devices on two
    // files that never converge — so it's gated behind a warning + confirm, and
    // "Open from Drive" (join the existing file) is emphasized as the right path.
    hasExistingSync: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // Local warning step: first tap of "Save to Drive" while already synced flips
    // this on and swaps the row for a warning + explicit "Create anyway"; the
    // recommended action is to join the existing file instead.
    var warnNewFile by remember { mutableStateOf(false) }
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
            // Join first — it's the correct choice when another device already set
            // sync up, and making it the emphasized (active) card steers people away
            // from accidentally creating a second file.
            DriveSyncChoiceRow(
                icon = Icons.Filled.FileOpen,
                title = "Open from Drive",
                subtitle = "Join the file another device already set up, they'll share settings.",
                emphasized = hasExistingSync,
                onClick = onOpenFromDrive,
            )
            if (warnNewFile) {
                // The trap, spelled out, with the safe alternative one tap away.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.errorContainer.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "This creates a NEW, separate file: your devices would end up on different files and stop sharing settings. Only do this to start over.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer,
                    )
                    MorphButton(
                        onClick = onSaveToDrive,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) { Text("Create a new file anyway", color = scheme.error) }
                }
            } else {
                DriveSyncChoiceRow(
                    icon = Icons.Filled.CreateNewFolder,
                    title = "Save to Drive",
                    subtitle = "Start fresh: create a new file with this device's settings.",
                    onClick = { if (hasExistingSync) warnNewFile = true else onSaveToDrive() },
                )
            }
        },
        buttons = {
            MorphTextButton("Cancel", onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
        },
    )
}

@Composable
private fun DriveSyncChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    // Highlights this choice as the recommended one (filled/active MorphButton).
    emphasized: Boolean = false,
) {
    // The app's standard button component (MorphButton), not a bespoke
    // Surface row -- so this dialog's actions look and feel like every other
    // button in the app instead of a one-off.
    MorphButton(
        onClick = onClick,
        active = emphasized,
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
