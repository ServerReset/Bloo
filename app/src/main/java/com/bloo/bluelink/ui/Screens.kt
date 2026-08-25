@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.runtime.State
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import com.bloo.bluelink.data.formatLockoutSeconds
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.CHARGE_LIMIT_RANGE
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
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.degValue
import com.bloo.bluelink.data.MapTiles
import com.bloo.bluelink.data.smartClimateTargetF
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.MorphButtonCore
import com.bloo.uicommon.connectedGroupShape
import com.bloo.uicommon.splitPillShapes
import com.bloo.uicommon.dropShadow
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.openLabels
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
import com.bloo.uicommon.rememberConfirmArm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
internal fun GarageScreen(state: State<UiState>, vm: AppViewModel) {
    val vehicles = state.value.vehicles
    if (vehicles.isEmpty()) return
    val appearance = LocalAppearance.current

    // Collected here rather than read off UiState: the pager's position is its
    // own flow now precisely so that finishing a swipe does not invalidate the
    // car pages. Reading it in THIS composable is fine and intended -- this is
    // one of the few places that genuinely needs it, and it is above the pages.
    val currentIndex by vm.currentIndex.collectAsState()
    val currentVehicle = vehicles.getOrNull(currentIndex.coerceIn(0, vehicles.lastIndex))
    val currentFetchedAt = currentVehicle?.let { state.value.fetchedAt(it) }
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
    LaunchedEffect(state.value.showSettingsHint) {
        if (state.value.showSettingsHint) {
            vm.reportInfo("Tip: fine-tune each car's seats, photo and pebble order in Settings")
            vm.dismissSettingsHint()
        }
    }

    // Settle haptic when a refresh lands.
    val haptics = LocalHaptics.current
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(state.value.refreshing) {
        if (wasRefreshing && !state.value.refreshing) haptics?.slotSettle()
        wasRefreshing = state.value.refreshing
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
        targetValue = if (state.value.refreshing || pulling) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "dotsFade",
    )
    // Live bounds of whichever car name is currently flying/docked on this
    // screen -- written from inside TitleFlightOverlay's onNameBoundsChanged
    // (both the hoisted single-car-per-page badge below, and each
    // ExpandedCar page's own badge), read by PagerDotsFor's own collision
    // dodge. A plain remembered State, never read here with `by` -- see
    // pullFractionState's identical doc just above for why a GarageScreen-
    // scope read of something that changes every animation frame would be
    // expensive; PagerDots reads `.value` itself, at draw time.
    val nameBoundsPxState = remember { mutableStateOf<Rect?>(null) }
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
    val overlayShiftTarget = if (state.value.refreshing) RefreshPullShift
        else (RefreshPullShift * pullFraction).coerceIn(0.dp, RefreshPullShift)
    val refreshShiftState = animateDpAsState(
        targetValue = overlayShiftTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (state.value.refreshing) Spring.StiffnessLow else Spring.StiffnessMedium,
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
        CompactGarage(state.value, vm, appearance)
        return
    }
    // How many full-height cards fit side by side; pages advance by this many.
    val perPage = (widthDp / MIN_CARD_DP).coerceIn(1, count)
    // Expanding to the dual-column view only makes sense on a wide screen.
    val canExpand = large && count > 1
    val singleLarge = large && count == 1
    // A car expanded by the user (multi-car), or the lone car on a big screen.
    val expandedByUser = state.value.expandedIndex?.takeIf { it in vehicles.indices && canExpand }
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
                                ExpandedCar(
                                    pv,
                                    state,
                                    vm,
                                    flipped = appearance.columnsFlipped,
                                    // Feeds this same PagerDotsFor's collision dodge
                                    // below. Wired unconditionally per page rather than
                                    // gated to "only the settled page" -- doing that
                                    // gate here would mean reading exPager.currentPage
                                    // in this scope, which is exactly the per-frame,
                                    // whole-pager-invalidating read this file's own
                                    // PagerDotsFor doc above warns against. Harmless
                                    // either way: only one page is ever actually
                                    // composed here (beyondViewportPageCount = 0), so
                                    // there's no simultaneous writer to race against.
                                    onNameBoundsChanged = { nameBoundsPxState.value = it },
                                )
                            }
                        }
                    }
                    StatusBarScrim()
                    if (count > 1 && !LocalReorderActive.current) {
                        PagerDotsFor(
                            pager = exPager,
                            real = { exWrap.real(it) },
                            count = count,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = HeaderCornerGap)
                                .graphicsLayer { alpha = dotsAlphaState.value },
                            onRefresh = { vm.refreshStatus(vehicles[exWrap.settledReal]) },
                            nameBoundsPx = nameBoundsPxState,
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
                // appearance.settingsAsPage || state.value.landOnSettingsPage, not
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
                val settingsAsPage = appearance.settingsAsPage || state.value.landOnSettingsPage
                val totalBlocks = if (settingsAsPage) pageCount + 1 else pageCount
                // Normally the car currentIndex was already parked on. The one
                // exception is state.value.landOnSettingsPage (see its own doc): Settings
                // itself just switched settingsAsPage on and asked to be followed,
                // so this fresh mount seeds straight onto the just-created Settings
                // slot instead of whichever car was selected before Settings was
                // ever opened -- otherwise the user would land on a car for one
                // frame before having to go find the page themselves.
                val initialBlock = if (state.value.landOnSettingsPage && settingsAsPage) {
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
                LaunchedEffect(state.value.landOnSettingsPage) {
                    if (state.value.landOnSettingsPage) {
                        if (settingsAsPage) wrap.snapToReal(pageCount)
                        vm.consumeLandOnSettingsPage()
                    }
                }
                // Keyed on totalBlocks too, not just pager/perPage: this effect's
                // own collect{} closes over realBlock/pageCount/settingsAsPage as
                // they were the moment it (re)started. Toggling Appearance.
                // settingsAsPage from a search result while GarageScreen stays
                // mounted the whole time (see that toggle's own comment -- it
                // deliberately doesn't require a fresh mount) changes totalBlocks
                // without touching pager's identity or perPage, so without this
                // key the running coroutine kept using a stale, pre-toggle
                // settingsAsPage (permanently false, so onSettingsPageSlot could
                // never become true and the search bubble never morphed to a
                // pill) AND a stale pageCount/realBlock pairing that no longer
                // matched the pager's own (live) virtual page count -- a
                // mismatched modulus that could resolve `block` to an unrelated
                // number and fire selectIndex with a bogus index. Restarting here
                // rebinds the closure to the current values the instant the slot
                // count changes.
                LaunchedEffect(pager, perPage, totalBlocks) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        // Guarded: the Settings slot isn't a car block, and
                        // selectIndex/currentIndex only ever mean "which car" --
                        // settling there should leave whatever car was last
                        // selected exactly as it was, so swiping back to a car
                        // lands where you left it instead of snapping to car 0.
                        val block = realBlock(page)
                        if (block < pageCount) vm.selectIndex((block * perPage).coerceIn(0, count - 1))
                        // See UiState.onSettingsPageSlot's own doc -- this is what
                        // lets SearchLayer's floating bubble/pill morph track the
                        // embedded Settings page the same way it already tracks
                        // the standalone route, instead of staying a garage
                        // "bubble" the whole time it's on screen.
                        vm.setOnSettingsPageSlot(settingsAsPage && block == pageCount)
                    }
                }
                // Resets the flag above the moment this pager itself leaves
                // composition (navigating away from the garage entirely) --
                // without it, closing Settings-as-embedded by navigating to some
                // OTHER screen (not a car, not standalone Settings) could leave
                // a stale `true` behind with nothing left to correct it, since
                // the collect{} above stops running once this composable is gone.
                DisposableEffect(Unit) { onDispose { vm.setOnSettingsPageSlot(false) } }
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
                    if (state.value.screen != Screen.Garage) return@LaunchedEffect
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
                    if (state.value.screen != Screen.Garage) return@LaunchedEffect
                    // Toggling settingsAsPage ON from the standalone route
                    // (SettingsScreen's own toggle) changes totalBlocks AND
                    // sets state.value.landOnSettingsPage in the very same
                    // transition -- this effect exists to snap back to
                    // currentIndex's own block when totalBlocks changes for
                    // its OWN reasons (see the doc above), but that's
                    // exactly wrong here: it raced the landOnSettingsPage
                    // effect above (both fire off the same totalBlocks
                    // change, in the same frame) and could snap to the
                    // CURRENT CAR right after that effect had already landed
                    // on the new Settings slot, silently undoing it --
                    // reported as toggling the switch not actually taking
                    // you to the embedded page. That effect's own
                    // consumeLandOnSettingsPage() call clears this flag once
                    // it's genuinely done, so deferring to it here is safe
                    // even if this effect happens to run first.
                    if (state.value.landOnSettingsPage) return@LaunchedEffect
                    val targetBlock = currentIndex.coerceIn(0, count - 1) / perPage
                    wrap.snapToReal(targetBlock)
                }
                // Hoisted identity badge state for the single-car-per-page
                // pager (perPage == 1) -- one shared TitleFlightOverlay, driven
                // by whichever page is currently SETTLED (car or the
                // embedded Settings slot), rather than each page keeping its
                // own. See HoistedIdentityFlight's own doc. The position is
                // deliberately NOT reset on page switches: a stale value
                // holds the badge steady for the single frame it takes the
                // newly settled page's own report to arrive (guaranteed
                // next layout pass -- onPositioned only ever fires from the
                // page currently holding the hoisted flight), and if the new
                // page's answer differs the badge just fades -- its only
                // move -- rather than flashing through a wrong state.value.
                val density = LocalDensity.current
                val hoistedTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val hoistedTopInsetPx = with(density) { hoistedTopInset.toPx() }
                val hoistedScrollToTop = remember { mutableStateOf<(suspend () -> Unit)?>(null) }
                val pillScope = rememberCoroutineScope()
                // remember(Unit), not remember(hoistedTopInsetPx): the old
                // keyed remember threw this whole object away -- accumulated
                // dock state, colour, titleScale, everything -- and rebuilt
                // it from scratch every time the status-bar inset itself
                // changed (rotation, fold/unfold, multi-window resize), even
                // though nothing about WHICH page is hoisted or what it's
                // doing changed at all. The freshly-built replacement then
                // had to re-earn its position from a genuine sentinel-free
                // "nothing reported yet" state (see HeroTitleFlight's own
                // doc) before the badge was visible again. topInsetPx is
                // pushed into the persistent object via a plain field write
                // below instead -- see HeroTitleFlight.topInsetPx's own doc.
                val hoistedFlight = remember {
                    HoistedIdentityFlight(
                        flight = HeroTitleFlight(hoistedTopInsetPx, with(density) { TitleDockHysteresis.toPx() }),
                        scrollToTop = hoistedScrollToTop,
                    )
                }
                SideEffect { hoistedFlight.flight.topInsetPx = hoistedTopInsetPx }
                // Per-page (keyed by pager page index, which every block --
                // car or the embedded Settings slot -- has exactly one of)
                // live "is THIS page's own title currently docked" flag,
                // reported up by whichever page is currently live (settled
                // or not -- see VehicleDetailContent/SettingsScreen's own
                // `onDockedChanged`). This used to be inferred purely from
                // `page == pager.settledPage`, which conflated two different
                // questions: "which page is settled" and "should the shared
                // corner badge take over from this page's own inline title".
                // A page can be settled for a long time while fully
                // undocked (the ordinary hero-card state) -- hoisting it
                // regardless meant its name was ALWAYS routed through the
                // shared floating overlay instead of ordinary page content,
                // even mid-drag, which is what let the badge visibly detach
                // from the card it names. Gating `hoisted` on this map
                // instead means only a page that has ACTUALLY scrolled its
                // title past the status bar ever claims the shared flight;
                // every other page (settled-but-undocked, or the
                // pre-composed neighbour) renders its own name as plain
                // page content that moves 1:1 with the pager's own drag.
                // Keyed by stable identity (a VIN, or "settings"), NOT by raw
                // page index -- an index's real-world meaning isn't stable:
                // deleting a car shifts every later one down a slot,
                // resizing a foldable/tablet window changes perPage and so
                // pageCount, and reordering cars (drag-to-reorder, reachable
                // from the embedded Settings page without ever leaving this
                // same composition) reassigns which car sits at which index
                // directly. An earlier, index-keyed version of this exact
                // idea (lastKnownDocked, since removed) had precisely this
                // bug: after a reorder, a page's dockedPages entry could
                // describe a DIFFERENT car than the one now sitting at that
                // index, hoisting the wrong page's badge or leaving the
                // right one stuck un-hoisted.
                val dockedPages = remember { mutableStateMapOf<Any, Boolean>() }
                // Cleared the instant `perPage` is observed to have actually
                // changed (grid <-> single-car, a live foldable/multi-window
                // resize) -- synchronously, during composition, NOT via a
                // LaunchedEffect(perPage): a coroutine-based clear only runs
                // after THIS recomposition (the one that first sees the new
                // perPage value, and that ALSO swaps in the freshly-built
                // single-car pager composables below) has already committed,
                // leaving those fresh composables' own first `hoisted`/
                // `hoistedVisible` reads (further down) still seeing whatever
                // stale `true` a car left behind before the resize -- one
                // whole recomposition too late to prevent hoisting a
                // genuinely-undocked, just-recomposed page for a frame.
                // dockedPages only ever gets written `if (perPage == 1)` (see
                // onDockedChanged's own gate below), so any entries left
                // over from a prior perPage==1 stint are unconditionally
                // stale once perPage has changed at all -- clearing the
                // whole map, not just one key, is correct here.
                var lastPerPage by remember { mutableStateOf(perPage) }
                if (lastPerPage != perPage) {
                    dockedPages.clear()
                    lastPerPage = perPage
                }
                fun dockedPageKey(page: Int): Any =
                    if (settingsAsPage && page == pageCount) "settings" else vehicles.getOrNull(page)?.vin ?: page
                // Whether the shared hoisted badge SHOULD be showing right
                // now, and which page it's showing/fading for -- computed
                // HERE (not beside the AnimatedVisibility call site that
                // actually renders it, further below) so the per-page pager
                // content below can also read them; see `isSettledAndDocked`
                // and `hoistedFullyGone`'s own docs for why both matter.
                val hoistedVisible = perPage == 1 && dockedPages[dockedPageKey(pager.settledPage)] == true
                // Frozen at the last page seen while `hoistedVisible` was
                // actually true. AnimatedVisibility (further below) keeps
                // its content composed for the duration of its own exit
                // fade, and `pager.settledPage` may have ALREADY moved on to
                // a DIFFERENT, never-docked page by the time that fade
                // starts (a fast swipe straight off a still-docked car) --
                // reading `pager.settledPage` straight, at either use site,
                // would relabel the still-fading badge with the new page's
                // identity, or (see `isSettledAndDocked` below) incorrectly
                // extend the new page's own hoisted grace period using the
                // OLD page's fade state.value. Written plainly here in
                // composition, not inside an effect/coroutine -- every
                // reader in this same pass sees the just-written value
                // immediately.
                var frozenBlock by remember { mutableStateOf(realBlock(pager.settledPage)) }
                if (hoistedVisible) frozenBlock = realBlock(pager.settledPage)
                // Backs the AnimatedVisibility call site further below
                // instead of a bare Boolean, so its own idle/target
                // bookkeeping can answer "has the exit fade actually
                // FINISHED", not just "has the dock flag flipped false" --
                // see `hoistedFullyGone`'s own doc for why the two are
                // different questions.
                val hoistedVisibleState = remember { MutableTransitionState(false) }
                // True only once the shared badge's own exit fade has
                // genuinely finished playing (isIdle) settled on "gone"
                // (!targetState) -- NOT the instant dockedPages flips false,
                // which only means the SPRING settled, one phase before the
                // 160ms crossfade even starts. Read synchronously here (a
                // plain property read on a Compose-owned object, not a
                // remembered duration or a coroutine delay), so using it
                // below to gate the undock hand-off can't race the fade the
                // way a `LaunchedEffect(...) { delay(160) }` guess could.
                val hoistedFullyGone = hoistedVisibleState.isIdle && !hoistedVisibleState.targetState
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
                        // remember(<that pebble's own catalogued fields>) { state.value }, so an
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
                        // Removes this page's own dockedPages entry the instant its
                        // key changes identity OR it leaves composition -- covers
                        // both real disposal (scrolled past beyondViewportPageCount,
                        // so a fresh instance later reusing this key starts from "not
                        // reported docked yet" instead of inheriting a stale `true`)
                        // and a reorder reassigning this pager slot to a different
                        // car mid-life (DisposableEffect re-keys on dockedPageKey(page)
                        // changing, cleaning up the OLD vin's entry as part of the
                        // same recomposition instead of leaving it orphaned). Nothing
                        // in this file previously cleared dockedPages at all, so a
                        // stale `true` could hoist a freshly-recomposed, genuinely
                        // undocked page for one or more frames -- exactly the class
                        // of "flash" this whole audit was looking for. perPage > 1
                        // never writes dockedPages (see onDockedChanged's own gate
                        // below), so this is a no-op there.
                        if (perPage == 1) {
                            val dpKey = dockedPageKey(page)
                            DisposableEffect(dpKey) { onDispose { dockedPages.remove(dpKey) } }
                        }
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
                        // Computed once per page body and reused below, instead of
                        // repeating the full expression (and its dockedPageKey()
                        // call + map lookup) at each call-site argument -- the
                        // "resolve once, don't re-derive per argument" rule this
                        // file already applies elsewhere (see TitleFlightOverlay's
                        // own textColorOverride doc).
                        //
                        // `|| (!hoistedFullyGone && page == frozenBlock)`, not
                        // just the raw dockedPages flag: dockedPages flips
                        // false the instant the shared badge's own SPRING
                        // settles back undocked, one phase before its 160ms
                        // crossfade (AnimatedVisibility, further above/below)
                        // even starts. Handing `hoisted` back to null the
                        // instant the flag flips used to switch this page's
                        // ambient LocalHeroTitleFlight back to its own local
                        // flight immediately -- cutting the shared flight off
                        // from any further live position reports while it was
                        // STILL VISIBLE, fading out for another 160ms. If the
                        // user was still actively scrolling during that window
                        // (a slow, deliberate scroll past the undock threshold,
                        // as opposed to a fling that's already stopped by the
                        // time the spring settles), the exiting badge kept
                        // animating toward a now-frozen stale target while the
                        // freshly-live local badge tracked real, still-moving
                        // coordinates -- the two visibly diverging, reading as
                        // the name flickering/partly vanishing rather than
                        // gliding. Keeping `hoisted` (and therefore the shared
                        // flight's own live position feed) alive for the FULL
                        // fade, not just the spring phase, closes that gap.
                        // Gated on `page == frozenBlock`, not just "any
                        // currently-settled page": without it, swiping straight
                        // from a still-docked car to a DIFFERENT, never-docked
                        // one would incorrectly extend the NEW page's own
                        // hoisted grace period off the OLD page's still-fading
                        // badge -- frozenBlock is specifically which page that
                        // badge belongs to.
                        val isSettledAndDocked = perPage == 1 && page == pager.settledPage &&
                            (dockedPages[dockedPageKey(page)] == true || (!hoistedFullyGone && page == frozenBlock))
                        // remember(page), not a fresh lambda literal per
                        // recomposition -- this whole per-page content block
                        // recomposes for reasons unrelated to docking (any
                        // UiState field this page's own descendants read), and
                        // `page` alone is enough to make this a stable function
                        // of "which page", the only thing the callback's own
                        // closure actually depends on.
                        val onPageDockedChanged: ((Boolean) -> Unit)? = remember(page) {
                            if (perPage == 1) ({ d: Boolean -> dockedPages[dockedPageKey(page)] = d }) else null
                        }
                        if (settingsAsPage && block == pageCount) {
                            // The extra slot: Settings itself, embedded rather than
                            // navigated to -- see SettingsScreen's own `embedded` doc.
                            // hoisted only for the SETTLED page, AND only once that
                            // page's own title has actually scrolled into the docked
                            // (pill) state -- see dockedPages' own doc above for why
                            // "settled" alone isn't the right gate any more.
                            SettingsScreen(
                                vm, embedded = true,
                                hoisted = if (isSettledAndDocked) hoistedFlight else null,
                                onDockedChanged = onPageDockedChanged,
                                // See VehicleDetailContent's identical `pageLabel`
                                // doc -- matches the shared hoisted badge's own
                                // label so the hand-off between the two instances
                                // has no width to pop.
                                pageLabel = if (perPage == 1 && totalBlocks > 1) "${block + 1} / $totalBlocks" else null,
                            )
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
                                            // Dynamic, not a flat "last car always leaves
                                            // room": the persistent gear button this is
                                            // dodging is itself hidden right here, in the
                                            // collapsed grid, whenever settingsAsPage is on
                                            // (see that button's own condition below --
                                            // expandedIdx is always null in this branch, so
                                            // its "|| expandedIdx != null" half never
                                            // applies). Reserving the gap for a button
                                            // that isn't there just left the last car's own
                                            // expand button sitting noticeably further from
                                            // the true corner than every other car's, for
                                            // no reason once nothing was actually competing
                                            // with it.
                                            reserveHeaderEnd = canExpand && i == end - 1 && !appearance.settingsAsPage,
                                            // Same condition PagerDotsFor itself uses to
                                            // decide whether it's showing at all -- see
                                            // reserveTopForDots's own doc.
                                            reserveTopForDots = totalBlocks > 1,
                                            // Only hide the per-car pull indicator in the
                                            // multi-car grid (perPage > 1) -- a prior fix
                                            // meant for the grid only ended up applying here
                                            // unconditionally, silently killing the single-
                                            // car view's refresh feedback too.
                                            hideIndicator = perPage > 1,
                                            // hoisted only for the SETTLED page in
                                            // single-car-per-page mode, AND only once
                                            // that page's own title has actually
                                            // scrolled into the docked (pill) state --
                                            // see dockedPages' own doc above. perPage >
                                            // 1 shows several cars at once, so there is
                                            // no single "the settled car" to hoist.
                                            hoisted = if (isSettledAndDocked) hoistedFlight else null,
                                            // Every page in the single-car-per-page
                                            // pager (settled or the pre-composed
                                            // neighbour alike) reports its own live
                                            // docked state up into dockedPages -- see
                                            // that map's own doc for why this can no
                                            // longer be conditioned on being settled.
                                            onDockedChanged = onPageDockedChanged,
                                            // Feeds PagerDotsFor's own collision
                                            // dodge -- was missing from this call
                                            // site entirely, which is why the dots
                                            // never actually dodged: this page's
                                            // own (non-hoisted) badge is the one
                                            // that's live and flying near the top
                                            // for the whole undocked/pre-dock
                                            // phase, in BOTH single-car and grid
                                            // mode, and nothing here was reporting
                                            // its bounds at all.
                                            onNameBoundsChanged = { nameBoundsPxState.value = it },
                                            // Only a grid column's container is
                                            // genuinely offset from the
                                            // composition root -- see
                                            // TitleFlightOverlay's own
                                            // `containerRelative` doc for why this
                                            // must stay scoped to exactly that
                                            // case.
                                            gridColumn = perPage > 1,
                                            // See VehicleDetailContent's own
                                            // `pageLabel` doc -- matches the
                                            // shared hoisted badge's own label so
                                            // the hand-off between the two
                                            // instances has no width to pop.
                                            // Only meaningful for the pager this
                                            // page's badge can actually hand off
                                            // into (perPage == 1); grid columns
                                            // never hoist at all.
                                            pageLabel = if (perPage == 1 && totalBlocks > 1) "${block + 1} / $totalBlocks" else null,
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
                    if (totalBlocks > 1 && !LocalReorderActive.current) {
                        PagerDotsFor(
                            pager = pager,
                            real = { realBlock(it) },
                            count = totalBlocks,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = HeaderCornerGap)
                                .graphicsLayer { alpha = dotsAlphaState.value },
                            // Guarded like every other currentIndex read in this
                            // function (currentVehicle above, etc.) -- currentIndex
                            // is its own StateFlow, independent of `vehicles`, so a
                            // resync/removal shrinking the list can leave it briefly
                            // out of range; an unguarded vehicles[currentIndex] here
                            // would crash the screen on a mistimed pull-to-refresh.
                            onRefresh = { vehicles.getOrNull(currentIndex)?.let { vm.refreshStatus(it) } },
                            nameBoundsPx = nameBoundsPxState,
                        )
                    }
                    // Grid mode (perPage > 1, wide/large screens) hides each
                    // card's own pull-to-refresh indicator above -- state.value.refreshing
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
                            visible = state.value.refreshing,
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(200)),
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = HeaderCornerGap),
                        ) {
                            LoadingIndicator()
                        }
                    }
                    // Hoisted identity badge -- one shared TitleFlightOverlay for
                    // single-car-per-page mode, following whichever page is
                    // currently SETTLED (car or the embedded Settings slot).
                    // See hoistedFlight's own doc above.
                    // Mounted ONLY once the settled page has actually reported
                    // itself docked (dockedPages, above) -- NOT unconditionally
                    // for every perPage==1 frame the way this used to read. Two
                    // reasons this can no longer stay unconditional now that
                    // `hoisted` itself is gated the same way (see the call
                    // sites' own doc): first, an undocked settled page renders
                    // its OWN plain title as ordinary content now (VehicleDetail
                    // Content/SettingsScreen's own `local` path), so an always-
                    // mounted copy here would draw a SECOND, stale copy of
                    // whatever name this shared flight last carried right on
                    // top of it. Second, nothing is writing fresh reports into
                    // hoistedFlight.flight while no page currently owns it, so
                    // that stale copy wouldn't even be showing the RIGHT car --
                    // exactly last fix's bug 2 (wrong name at a stale position),
                    // just relocated here instead of at the settle boundary.
                    // The one tradeoff: mounting/unmounting this composable
                    // resets TitleFlightOverlay's own internal dock/undock
                    // spring each time, instead of that spring free-running
                    // continuously the way a truly permanent instance would --
                    // acceptable because by the time this mounts, the page's
                    // own `local` flight has already been reporting the exact
                    // corner-adjacent position for a while (see
                    // VehicleDetailContent's `onDockedChanged`/hoisted hand-off
                    // doc), so there's no visible snap.
                    //
                    // Wrapped in AnimatedVisibility, not a plain `if`, so
                    // mounting/unmounting fades rather than pops -- a bare
                    // `if` used to tear this composable down (and stand it
                    // back up) INSTANTLY the moment the settled page's own
                    // docked state differs from the page swiped away from
                    // (e.g. settling on an undocked car right after a docked
                    // one), which read as the corner pill just vanishing/
                    // appearing with no transition at all.
                    //
                    // `hoistedVisible`/`frozenBlock` are computed once,
                    // higher up (right after `dockedPageKey`), not here --
                    // the per-page pager content above needs to read them
                    // too (see `isSettledAndDocked`'s own doc). Backed by
                    // `hoistedVisibleState`, a MutableTransitionState, not a
                    // bare `visible: Boolean` -- see `hoistedFullyGone`'s own
                    // doc for why knowing exactly when this fade FINISHES,
                    // not just when it starts, matters.
                    hoistedVisibleState.targetState = hoistedVisible
                    AnimatedVisibility(
                        visibleState = hoistedVisibleState,
                        enter = fadeIn(tween(160)),
                        exit = fadeOut(tween(160)),
                    ) {
                        // Settled, not current: matches every other "which page is
                        // this" read in this pager (the settle effect above), so
                        // the badge's own identity only updates mid-swipe once a
                        // page actually wins, not on every frame of the drag.
                        val settledBlock = frozenBlock
                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                        val onSettingsSlot = settingsAsPage && settledBlock == pageCount
                        val title = if (onSettingsSlot) "Settings" else vehicles.getOrNull(settledBlock)?.name ?: ""
                        // ONE persistent TitleFlightOverlay, bound directly to the
                        // single shared hoistedFlight.flight -- never torn down and
                        // rebuilt per page (an AnimatedContent-per-block design used
                        // to live here; see git history for the full saga of bugs
                        // that came from swapping the underlying flight object on
                        // every switch: stale-geometry windows, readiness races, a
                        // duplicate badge on the pre-composed neighbour, and a
                        // dock-state cache that could itself go stale). Since the
                        // object is never swapped, TitleFlightOverlay's OWN existing
                        // spring (mounted/LaunchedEffect(docked, flight), already
                        // used and proven for a real SCROLL-driven dock/undock
                        // crossing) is what carries a page switch that changes dock
                        // state too -- no separate "hop" machinery needed, because
                        // it is not a structurally different event to this function
                        // any more. inlinePos/dockedAnchor are also structurally the
                        // SAME position for every car (same corner offsets, same
                        // hero-card layout), so hero-hero and pill-pill switches
                        // don't visibly move at all -- only the TEXT changes, via
                        // the inner AnimatedContent in `content` below.
                        TitleFlightOverlay(
                            flight = hoistedFlight.flight,
                            cornerX = 16.dp,
                            cornerY = hoistedTopInset + HeaderCornerGap,
                            // Car slots clear the top-right gear/expand chrome, same
                            // as VehicleDetailContent's own badge (72dp). The embedded
                            // Settings slot instead needs to clear the always-visible
                            // 172dp Simple/Advanced toggle in the corner (192dp, same
                            // value SettingsScreen's own standalone route already
                            // reserves for it -- see SettingsHeaderRow) -- without
                            // this, a docked "Settings" pill could grow wide enough to
                            // run under that toggle, something only the standalone
                            // route was guarding against.
                            reserveEnd = if (onSettingsSlot) 192.dp else 72.dp,
                            maxWidth = screenWidth - 16.dp - (if (onSettingsSlot) 192.dp else 72.dp) - 32.dp,
                            // The Settings slot has no hero photo to morph its own
                            // colour against, so it's forced to plain onSurface;
                            // every car slot instead reads its own flight's live
                            // colour, resolved INSIDE TitleFlightOverlay (see
                            // textColorOverride's own doc for why reading it
                            // there instead of here as a call-site argument
                            // matters).
                            textColorOverride = if (onSettingsSlot) MaterialTheme.colorScheme.onSurface else null,
                            onClick = { pillScope.launch { hoistedScrollToTop.value?.invoke() } },
                            // Feeds PagerDotsFor's own collision dodge just
                            // above -- see nameBoundsPxState's declaration
                            // near dotsAlphaState for why this is a plain
                            // `.value =` write, not a `by` delegate.
                            onNameBoundsChanged = { nameBoundsPxState.value = it },
                            // Keeps dockedPages in sync with THIS shared
                            // badge's own resting state, in both directions
                            // -- see onSettledChanged's own doc for why
                            // undocking used to be reported off the raw
                            // scroll-threshold flag instead (from
                            // VehicleDetailContent/SettingsScreen), which cut
                            // this exact instance off from further position
                            // updates while its own exit spring was often
                            // still mid-flight, reading as a stutter back
                            // toward the pebble. Keyed off `frozenBlock`, not
                            // `pager.settledPage` -- this can still fire
                            // during the AnimatedVisibility exit fade, by
                            // which point the pager may have already settled
                            // onto a different page; `frozenBlock` is the
                            // page this instance was actually mounted for.
                            onSettledChanged = { atRest -> dockedPages[dockedPageKey(frozenBlock)] = atRest },
                            measureContent = {
                                Text(
                                    title,
                                    // headlineSmall, not titleLarge -- matches the
                                    // base PebbleShell actually scales its own
                                    // (invisible) title anchor from (see that
                                    // Text's own `titleStyle` comment). The flying
                                    // Text used to be styled a whole different type
                                    // step (titleLarge, 22sp default) than the base
                                    // its shared titleScale ratio was computed
                                    // against (titleMedium/headlineSmall, 16/24sp) --
                                    // so even when titleScale genuinely varied with
                                    // the hero photo pebble's own expand/collapse, the
                                    // rendered size never actually reached either of
                                    // the two type steps it was supposed to land on.
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            extraContent = {
                                // totalBlocks, not vehicles.size -- the Settings slot is
                                // one more page in the same sequence, so it counts too
                                // (see PagerDotsFor above, which already does the same
                                // swap).
                                if (totalBlocks > 1) {
                                    Text(
                                        "${settledBlock + 1} / $totalBlocks",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        ) {
                            // A LITERAL masked wipe, not an approximation: `content`
                            // is invoked from inside TitleFlightOverlay's own visible
                            // Text Box, which is already positioned exactly where the
                            // pill/hero-card sits -- so this AnimatedContent's local
                            // bounds genuinely ARE the text's on-screen bounds, and
                            // its default (clipping) SizeTransform genuinely clips to
                            // them, unlike the old outer-AnimatedContent attempt whose
                            // content was offset far outside its own measured box.
                            //
                            // docked read HERE, not hoisted out as a val above --
                            // same reasoning as flight.color's own doc: keeps the
                            // recompose scope this causes down to just this small
                            // inner composable, not the whole hoisted-badge block.
                            val docked by hoistedFlight.flight.docked
                            AnimatedContent(
                                targetState = title,
                                transitionSpec = {
                                    if (docked) {
                                        // A real, local wipe: the outgoing name
                                        // slides out one side while the incoming
                                        // one slides in from the other, both
                                        // clipped to their own (here, genuinely
                                        // local) bounds -- the "morph and change
                                        // the text with a wipe" this whole
                                        // redesign exists for.
                                        (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)))
                                            .togetherWith(
                                                slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180)),
                                            )
                                    } else {
                                        // Plain hero-card text -- already moving
                                        // with the pager's own drag underneath it;
                                        // no separate transition of its own.
                                        EnterTransition.None togetherWith ExitTransition.None
                                    }
                                },
                                label = "hoistedTitleWipe",
                            ) { t ->
                                Text(
                                    t,
                                    // headlineSmall -- see measureContent's
                                    // identical fix just above for why.
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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



// The cover reuses the phone's pebble CARDS: CompactCar's vertical tile pager
// renders SinglePebble(section) under LocalForceExpanded/PebbleFillHeight/
// CoverScrollState, so each pebble draws as an always-expanded, height-filling
// card. (The bespoke CoverTile toolkit + Cover*Tile faces were removed — they
// looked off-brand; the cover is back to the polished pebble-card design.)


// --- Full detail ----------------------------------------------------------


/**
 * Single-column car view (phones, and each column of the grid). Everything
 * scrolls together in one [Column] inside [Refreshable] (header row, then
 * the reorderable [PebbleList]).
 *
 * The car's name is drawn exactly ONCE, but not here -- the hero card's own
 * title slot is permanently invisible (space/position only); the one Text
 * that actually paints the name lives in [TitleFlightOverlay], which flies
 * it between that slot and the corner pill. See that function's own doc.
 */
@Composable
private fun VehicleDetailContent(
    v: Vehicle,
    /**
     * State SOURCE (vm.state.value.collectAsState()), consistent with PebbleList/SinglePebble:
     * per-use state.value reads keep this page and its pebble rows stable against
     * emissions that don't touch the values they actually read.
     */
    state: State<UiState>,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    reserveHeaderEnd: Boolean = false,
    hideIndicator: Boolean = false,
    // True whenever GarageScreen's own PagerDotsFor is showing (totalBlocks
    // > 1 there) -- that indicator floats fixed at TopCenter, independent of
    // this car's own scroll position, so it can sit directly over this
    // car's fact-chip row the instant the car is scrolled to its own top.
    // Reported from a real device: with exactly two cars the dots -- one
    // small circle, one elongated into a bar -- read as a toggle switch
    // sitting half behind the chips. Same idea as reserveHeaderEnd already
    // dodging the Settings gear; this reserves the analogous clearance at
    // the top instead of the end.
    reserveTopForDots: Boolean = false,
    // Non-null ONLY for GarageScreen's single-car-per-page pager's currently
    // SETTLED page, AND only once that page has reported itself DOCKED (see
    // `onDockedChanged` below and the call site's `dockedPages` doc) -- see
    // HoistedIdentityFlight's own doc. A settled-but-undocked page (the
    // ordinary hero-card state, and the common case for a plain hero-to-hero
    // swipe) now gets `hoisted == null` here just like the pre-composed
    // neighbour does, and renders its OWN name as ordinary page content
    // (`local`, below) instead -- see this param's git history for the two
    // bugs that came from routing that case through the shared flight
    // anyway: the badge visibly detaching from its card mid-drag (only the
    // ORIGIN page owned the shared flight for the whole gesture, since
    // `pager.settledPage` doesn't change until the drag fully settles), and
    // a one-frame flash of the new car's name at the old car's stale
    // position right at the settle boundary. When `hoisted` IS non-null,
    // this page's own scroll-to-top is reported into the CALLER's shared
    // flight, and this composable renders NO badge of its own at all -- the
    // caller renders ONE shared badge instead, covering every page
    // including this one.
    hoisted: HoistedIdentityFlight? = null,
    // Reports this page's own live docked state (with hysteresis -- see
    // HeroTitleFlight.docked's own doc) up to the caller on every change,
    // regardless of whether `hoisted` is currently null or not. Called for
    // EVERY page in the single-car-per-page pager -- settled or the
    // pre-composed neighbour alike -- because the caller needs to know the
    // instant a settled-but-undocked page BECOMES docked in order to start
    // passing `hoisted` for it; there's no other signal it could use. Null
    // for every page outside that pager (perPage > 1 grid mode), which has
    // no single "the settled car" for a caller-level flag to mean anything.
    onDockedChanged: ((Boolean) -> Unit)? = null,
    // Forwarded to this page's own (non-hoisted) TitleFlightOverlay call
    // below -- see that parameter's own doc (Screens.kt). Was missing
    // entirely from this call site until it was found to be the reason the
    // page-dot collision dodge never triggered: the hoisted badge and
    // ExpandedCar's badge both wired this, but the flying name most likely
    // to actually be near the dots (this composable's own per-page badge,
    // live for the whole undocked/pre-dock phase) never reported anything.
    onNameBoundsChanged: ((Rect?) -> Unit)? = null,
    // True ONLY when this is a perPage>1 grid column -- forwarded to this
    // page's own TitleFlightOverlay call as `containerRelative`. See that
    // parameter's own doc for why this must stay opt-in and default false.
    gridColumn: Boolean = false,
    // "N / M" page-count label, non-null under the exact same condition the
    // shared hoisted badge shows one (perPage == 1, more than one page) --
    // see this page's own TitleFlightOverlay call below for why passing it
    // here too, not just on the hoisted badge, is load-bearing rather than
    // decorative: without it, the local badge's chrome Row measures
    // narrower (name only) than the hoisted badge's chrome Row (name +
    // label) it gets swapped for the instant this page finishes docking,
    // and Modifier.size(dockedSize...) has no width animation of its own --
    // so the pill visibly popped wider the moment the hand-off happened.
    // Passing the identical label here means both instances measure to the
    // same width, so there's nothing left to jump.
    pageLabel: String? = null,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Built UNCONDITIONALLY now, for every page -- settled, pre-composed
    // neighbour, or a plain standalone grid column alike. This used to be
    // skipped entirely whenever `hoisted != null` or `hoistedPending`, on
    // the theory that a page in either state reports into (or will report
    // into) the caller's shared flight instead and so has no use for one of
    // its own. That left the pre-composed neighbour with NOTHING tracking
    // its own dock state or position until the moment it became settled --
    // exactly the gap that let a stale, previous-page position leak through
    // for a frame right at the settle boundary (see `hoisted`'s own doc).
    // Building this always means every page has a live, continuously
    // up-to-date local flight from the moment it exists, so there's always
    // something real to read from (`onDockedChanged`, below) and always
    // something real to hand off FROM the instant this page's own name
    // needs to take over the shared corner badge.
    val topInsetPx = with(density) { topInset.toPx() }
    // remember(Unit) + SideEffect, not remember(topInsetPx) -- see the
    // hoisted flight's identical construction in GarageScreen for why a
    // keyed remember here silently discarded all accumulated dock/
    // position state on every inset change instead of just picking up
    // the new inset value.
    val heroFlight = remember { HeroTitleFlight(topInsetPx, with(density) { TitleDockHysteresis.toPx() }) }
    SideEffect { heroFlight.topInsetPx = topInsetPx }
    // Same live-scroll-correction wiring as topInsetPx just above -- see
    // `HeroTitleFlight.inlinePos`'s own doc for why this closes the
    // "name feels a frame behind the card" gap. `scroll` is the exact
    // ScrollState the real hero card's own Column (below) places itself
    // against, so both read the identical, same-frame-fresh offset.
    SideEffect { heroFlight.scrollValuePx = { scroll.value.toFloat() } }
    val local = LocalNamePillState(flight = heroFlight)
    // Whichever flight is actually LIVE for this page right now: the
    // caller's shared one while genuinely hoisted, this page's own
    // otherwise. Both `docked` reporting and the ambient
    // LocalHeroTitleFlight below key off this SAME value, so there is never
    // a moment where the two disagree about which object HeroHeader should
    // be writing its real position/colour/scale into.
    val liveFlight = hoisted?.flight ?: local.flight
    // dockedPages is now driven ENTIRELY by whichever TitleFlightOverlay is
    // actually live's own `onSettledChanged` -- this page's own local badge
    // below while `hoisted == null`, or GarageScreen's shared hoisted badge
    // (its own call site) while `hoisted != null`. Used to also report the
    // undocking direction immediately off the raw `liveFlight.docked` flag
    // here -- reasoned at the time to have "no hand-off-timing hazard on the
    // way out", which was wrong: the instant that raw report flipped
    // `dockedPages` false, the shared hoisted badge got torn down as the
    // live one (this page's local flight took back over), which cut the
    // SHARED flight off from any further position updates while its own
    // exit spring was often still mid-flight -- it kept animating toward a
    // now-frozen stale target while the freshly-visible local badge tracked
    // live coordinates, the two visibly diverging for the crossfade window.
    // onSettledChanged (fired only once a spring genuinely finishes, see its
    // own doc) doesn't have that hazard in either direction.
    if (hoisted != null) {
        // Register this page as the one actually driving the hoisted badge.
        // Idempotent, so re-running it every recomposition while hoisted is
        // harmless -- the caller only ever passes non-null here for the
        // currently SETTLED, currently DOCKED page, so there's no risk of
        // two pages fighting over the same hoisted state.
        hoisted.scrollToTop.value = { scroll.animateScrollTo(0) }
    }
    Refreshable(v, state.value, vm, hideIndicator = hideIndicator) {
        CompositionLocalProvider(LocalHeroTitleFlight provides liveFlight) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Inset spacer (not padding) so content scrolls *behind* the bars --
                // topInset alone, no extra breathing room, so the name sits right at
                // the status bar's own edge instead of noticeably below it. Plus
                // PagerDotClearance when the dots are showing -- see
                // reserveTopForDots's own doc.
                Spacer(Modifier.height(topInset + if (reserveTopForDots) PagerDotClearance else 0.dp))
                CarHeaderRow(v, state.value, onExpand, reserveHeaderEnd, hideName = true)
                // summary (image+gauge) and controls are reorderable pebbles too. The full
                // pebble column always renders while swiping; smoothness comes from
                // PebbleList's own one-frame lazy-fill (filled/EAGER_PEBBLES) + the pager's
                // beyondViewportPageCount=1 pre-compose, not from an in-transit skeleton.
                PebbleList(v, state, vm)
                Spacer(Modifier.height(bottomInset + 16.dp))
            }
        }
        // Hoisted mode (this page is settled AND docked) renders NO badge of
        // its own here at all -- the caller (GarageScreen) renders ONE
        // shared badge covering every page, including this one. Every OTHER
        // state -- perPage > 1 grid mode, this pager's pre-composed
        // neighbour, or this same page before it's scrolled into the docked
        // state -- renders its own name here, as ordinary page content that
        // simply moves with the pager/scroll like everything else on the
        // page. See `hoisted`'s own doc.
        // AnimatedVisibility, not a bare `if`, with the SAME fade duration
        // GarageScreen's shared hoisted badge uses for its own enter/exit
        // (see that AnimatedVisibility's own doc) -- a real, reproducible
        // bug traced one side of this hand-off cutting out/in instantly
        // while the other ramped over 160ms, leaving a ~160ms window where
        // the name was dimmer than it should be (or, on the reverse
        // direction, two overlapping copies at mismatched alphas). Fading
        // both sides in lockstep removes that dip entirely.
        AnimatedVisibility(
            visible = hoisted == null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(160)),
        ) {
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            TitleFlightOverlay(
                flight = local.flight,
                cornerX = 16.dp,
                cornerY = topInset + HeaderCornerGap,
                // Clears the top-right FloatingIcon slot (the expand button
                // in grid columns, the gear on the standalone route) -- 12dp
                // outer padding + 48dp icon + a little air.
                reserveEnd = 72.dp,
                maxWidth = screenWidth - 16.dp - 72.dp - 32.dp,
                // Falls back to onSurface before HeroHeader has reported a real
                // colour yet (this composable's own first frame) -- Unspecified would
                // otherwise resolve through LocalContentColor's own default instead.
                // textColorOverride omitted: `flight` here IS local.flight, so
                // TitleFlightOverlay reads its live colour itself -- see that
                // parameter's own doc for why resolving it there instead of
                // here is load-bearing, not stylistic.
                onClick = { scope.launch { scroll.animateScrollTo(0) } },
                onNameBoundsChanged = onNameBoundsChanged,
                containerRelative = gridColumn,
                // See `liveFlight`'s own doc just above -- dockedPages is
                // driven entirely off this, in both directions, rather than
                // the raw scroll-threshold flag.
                onSettledChanged = { atRest -> onDockedChanged?.invoke(atRest) },
                // See `pageLabel`'s own doc -- matches the shared hoisted
                // badge's own extraContent (Screens.kt, GarageScreen) so
                // the two instances' chrome measures to the same width and
                // the hand-off between them has nothing left to pop.
                extraContent = pageLabel?.let { label ->
                    {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) {
                Text(
                    v.name,
                    // headlineSmall -- matches PebbleShell's own real title
                    // base; see the hoisted badge's identical fix for the
                    // full reasoning.
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
 *
 * A [TitleFlightOverlay] (built inline near the bottom, alongside the
 * flip-columns transition) fades in once CriticalContent's own HeroHeader --
 * the real hero photo card, shared with [VehicleDetailContent] -- has
 * scrolled out of view, same as every other surface. Tapping it scrolls
 * `controlsScroll` back to top: whichever column currently renders
 * `controls` (and therefore HeroHeader), regardless of which physical side
 * that is after a flip.
 */
@Composable
private fun ExpandedCar(
    v: Vehicle,
    /** See VehicleDetailContent's `state` doc -- same source plumbing. */
    state: State<UiState>,
    vm: AppViewModel,
    flipped: Boolean,
    // See the call site's own doc (GarageScreen's exPager block) -- feeds
    // the sibling PagerDotsFor's collision dodge. Null (the default) for
    // every OTHER caller of ExpandedCar, none of which pair it with a
    // page-dot indicator that needs to know.
    onNameBoundsChanged: ((Rect?) -> Unit)? = null,
) {
    val hotspot = state.value.hotspotFor(v.vin)
        ?.takeIf {
            it in state.value.sectionsFor(v) && state.value.isSectionAvailable(v, it)
        }
    val hotDrag = remember { HotSeatDrag() }
    // Hoisted (not recreated on flip) so each column keeps its own scroll
    // position when the columns swap sides. controlsScroll always belongs
    // to whichever COLUMN currently renders `controls` (and therefore
    // CriticalContent's own HeroHeader), regardless of which physical side
    // (left/right) that currently is: the leftScroll/rightScroll pairing
    // below always keeps this same ScrollState paired with the same content
    // across a flip -- which is what makes it the right thing for the
    // badge's own tap-to-scroll-to-top.
    val controlsScroll = rememberScrollState()
    val pebblesScroll = rememberScrollState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInsetPx = with(density) { topInset.toPx() }
    // remember(Unit) + SideEffect, not remember(topInsetPx) -- same
    // reasoning as VehicleDetailContent's and GarageScreen's identical
    // construction sites: an inset change alone shouldn't discard this
    // flight's accumulated dock/position state.
    val titleFlight = remember { HeroTitleFlight(topInsetPx, with(density) { TitleDockHysteresis.toPx() }) }
    SideEffect { titleFlight.topInsetPx = topInsetPx }
    // Same live-scroll-correction wiring as VehicleDetailContent's identical
    // construction site -- see HeroTitleFlight.inlinePos's own doc.
    // `controlsScroll`, not `pebblesScroll`: per this composable's own doc
    // just above, controlsScroll is always the ScrollState paired with
    // whichever column currently hosts CriticalContent's HeroHeader,
    // regardless of which physical side a flip has it on.
    SideEffect { titleFlight.scrollValuePx = { controlsScroll.value.toFloat() } }
    // CriticalContent's own HeroHeader is the real hero photo card here --
    // this view was NOT missing one the way the doc above used to claim;
    // CarHeaderRow's plain-text name and HeroHeader's own (on the photo)
    // were simply both visible at once, the exact duplicate-name bug fixed
    // everywhere else in the app. hideName = true here, matching
    // VehicleDetailContent's own CarHeaderRow call exactly: the floating
    // name is sourced from HeroHeader (via the ambient LocalHeroTitleFlight
    // below, which HeroHeader already knows how to use -- no changes needed
    // there), not from this plain header.
    val controls: @Composable ColumnScope.() -> Unit = {
        CarHeaderRow(v, state.value, onExpand = null, reserveEnd = false, hideName = true)
        CriticalContent(v, state.value, vm)
        HotspotSlot(v, hotspot, state.value, vm)
    }
    val pebbles: @Composable ColumnScope.() -> Unit = {
        PebbleList(v, state, vm, exclude = setOfNotNull("summary", "controls", hotspot))
    }
    CompositionLocalProvider(LocalHotSeatDrag provides hotDrag, LocalHeroTitleFlight provides titleFlight) {
    // Was hardcoded hideIndicator = true -- the same "grid-only" flag that
    // hid the pull-to-refresh spinner in the single-car view (fixed in
    // a944a91) also hid it here, in the expanded/wide dual-column detail
    // view, unconditionally. This is a single car's own detail screen, not
    // the multi-car grid the flag was meant for, so the real M3 Expressive
    // indicator should show here too.
    Refreshable(v, state.value, vm) {
        Box(Modifier.fillMaxSize()) {
        // Animate the swap when the columns are flipped. Same spring the
        // expand/collapse transition (GarageScreen) and the collapsed
        // pager's own settle both use -- this was the one transition left
        // running on AnimatedContent's plain default spec instead of the
        // app's own spring language, and read noticeably flatter/more
        // mechanical next to those two right beside it.
        AnimatedContent(
            targetState = flipped,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                val floatSpec = spring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                val offsetSpec = spring<IntOffset>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)
                (slideInHorizontally(offsetSpec) { w -> dir * w / 4 } + fadeIn(floatSpec)) togetherWith
                    (slideOutHorizontally(offsetSpec) { w -> -dir * w / 4 } + fadeOut(floatSpec))
            },
            label = "flipColumns",
        ) { isFlipped ->
            val leftCol = if (isFlipped) pebbles else controls
            val rightCol = if (isFlipped) controls else pebbles
            val leftScroll = if (isFlipped) pebblesScroll else controlsScroll
            val rightScroll = if (isFlipped) controlsScroll else pebblesScroll
            // Inset spacers (not padding) so content scrolls *behind* the bars;
            // the leading spacer also clears the floating overlay buttons --
            // HeaderCornerGap + HeaderButtonSize (their real combined
            // footprint, 60dp), not the bare 52.dp this used to hardcode,
            // which let content peek up 8dp under the buttons' own bottom
            // edge. HeaderContentClearance adds real buffer on top of that
            // bare footprint -- without it, the column's own 12dp
            // `spacedBy` was the only thing standing between the button's
            // ambient glow/shadow halo and the first pebble/control's own
            // card shadow, and the two could visibly touch (e.g. the AI
            // summary pebble sitting right under the gear/flip buttons).
            val lead: @Composable ColumnScope.() -> Unit = { Spacer(Modifier.height(topInset + HeaderCornerGap + HeaderButtonSize + HeaderContentClearance)) }
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
        // The floating name badge, once CriticalContent's own HeroHeader has
        // scrolled out of view (same hero-card source as VehicleDetailContent,
        // via the same ambient LocalHeroTitleFlight above).
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        TitleFlightOverlay(
            flight = titleFlight,
            // Clears GarageScreen's own back arrow (top-left, 12dp/48dp) --
            // it's always present whenever ExpandedCar is on screen.
            cornerX = 60.dp,
            cornerY = topInset + HeaderCornerGap,
            // Clears the flip-columns + gear buttons in the top-right.
            reserveEnd = 120.dp,
            maxWidth = screenWidth - 60.dp - 120.dp - 32.dp,
            // Same Unspecified-before-first-report fallback as VehicleDetailContent's own call site.
            // textColorOverride omitted -- same reasoning as VehicleDetailContent's
            // own call site: flight IS titleFlight, so TitleFlightOverlay reads
            // its live colour itself.
            onClick = { scope.launch { controlsScroll.animateScrollTo(0) } },
            onNameBoundsChanged = onNameBoundsChanged,
        ) {
            Text(
                v.name,
                // headlineSmall -- matches PebbleShell's own real title
                // base; see the hoisted badge's identical fix for the full
                // reasoning.
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        }
    }
    }
}


/**
 * A row of small fact chips (model/powertrain, "updated x ago"), with an
 * optional expand button -- [hideName] is true from every real caller now
 * ([VehicleDetailContent] and [ExpandedCar] both pass it, the car's name is
 * only ever drawn ONCE, live, on the hero photo card, so a second copy here
 * would be the same name twice on screen at once), which makes this row's
 * ENTIRE content the chips, not a name plus a caption underneath it.
 *
 * CenterVertically, not Top: with no name line above them any more, the
 * chips are the row's only content, sitting noticeably shorter than
 * [FloatingIcon]'s fixed 48dp -- top-aligning them against it left the icon
 * looming taller beside a strip of chips hugging the top edge, reading as
 * mismatched pieces rather than one row. Centering both against each other
 * is what makes it read as one consistent band, the same alignment this
 * exact icon-beside-content pairing uses everywhere else it isn't paired
 * with a taller title line of its own.
 *
 * [hideName] itself (and the name [Text] it would draw) stays as an escape
 * hatch rather than being deleted outright -- nothing currently calls it
 * false, but the option to draw a title-sized line above the chips again
 * (with its own top-aligned pairing) is cheap to keep and expensive to
 * reconstruct if a future caller needs it.
 */
@Composable
private fun CarHeaderRow(
    v: Vehicle,
    state: UiState,
    onExpand: (() -> Unit)?,
    reserveEnd: Boolean,
    hideName: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (reserveEnd) Modifier.padding(end = 52.dp) else Modifier),
        verticalAlignment = if (hideName) Alignment.CenterVertically else Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            if (!hideName) {
                Text(
                    v.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = if (hideName) 0.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetaChip("${v.model} · ${state.powertrainLabel(v)}")
                LastUpdatedLabel(v, state)
            }
        }
        if (onExpand != null) {
            // A proper floating chip (was a hard-to-see bare icon).
            FloatingIcon(Icons.Filled.Fullscreen, "Expand to full screen", onExpand)
        }
    }
}




// --- Trips (trip history) --------------------------------------------------



// --- Location -------------------------------------------------------------


// --- Photo crop -----------------------------------------------------------


// BlooDialog (the legacy second dialog shell) was removed here — every dialog now
// routes through the single GlassAlertDialog shell above. Its one caller
// (rename-device) was migrated in the same change.
