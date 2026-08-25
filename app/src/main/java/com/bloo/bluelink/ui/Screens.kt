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
@Composable
private fun LockBlurLayer(locked: Boolean, content: @Composable () -> Unit) {
    val lockBlur by animateDpAsState(
        targetValue = if (locked) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 450),
        label = "lockBlur",
    )
    Box(Modifier.fillMaxSize().blur(lockBlur)) {
        content()
    }
}

// Owns the lock-overlay fade animation in its own small recompose scope, for
// the same reason as [LockBlurLayer].
@Composable
private fun LockAlphaOverlay(locked: Boolean, vm: AppViewModel) {
    val lockAlpha by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "lockAlpha",
    )
    if (lockAlpha > 0.01f) {
        Box(Modifier.fillMaxSize().alpha(lockAlpha)) {
            LockOverlay(vm)
        }
    }
}

// --- Onboarding wizard (first run + new-car detection) --------------------

private enum class WizardStepKind { POWERTRAIN, PLATFORM, SEATS, STEERING }

private data class WizardPage(
    val kind: WizardStepKind,
    val vin: String? = null,
)

/**
 * Flattens the per-vehicle setup wizard into one linear list of pages: for
 * each vehicle, a POWERTRAIN page, a PLATFORM page (only for a vehicle where
 * [com.bloo.bluelink.data.platformOverridable] is true -- see that
 * property's own doc; there's nothing to confirm for the rest), then SEATS,
 * then STEERING, in that order. The resulting list drives a single
 * [HorizontalPager] in [CarSetupWizardScreen], so a multi-car setup becomes
 * one continuous swipe sequence instead of nested per-car flows.
 */
private fun buildSetupPages(vehicles: List<com.bloo.bluelink.data.Vehicle>): List<WizardPage> = buildList {
    vehicles.forEach { v ->
        add(WizardPage(WizardStepKind.POWERTRAIN, v.vin))
        if (v.platformOverridable) add(WizardPage(WizardStepKind.PLATFORM, v.vin))
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

    // Devices without biometrics MUST finish the PIN step before leaving it
    // -- without a PIN there is no lock mechanism for this device at all.
    // The CTA below is disabled (with a hint) until the PIN lands.
    val pinRequired = !canBio && steps.getOrNull(pageIndex)?.kind == OnboardingStepKind.SETUP && !state.appPinSet

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
            val animatedProgress by animateFloatAsState(progress, tween(WizardProgressDurationMs), label = "onboardProgress")
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
                    enabled = !pinRequired,
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
                if (pinRequired) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Set your PIN above to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
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
        if (canBio)
            "All optional -- skip anything here and turn it on later in Settings."
        else
            "Everything here is optional -- except one thing: this device has no fingerprint sensor, so a PIN is required to lock the app.",
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

    // --- App PIN ---
    // Required (this exact card, not a skipped option) on devices with no
    // biometrics: without either mechanism the app could never lock at all.
    // On biometric devices it's the optional backup PIN.
    if (!canBio || !state.appPinSet) {
        OnboardingSetupCard(
            icon = Icons.Filled.Lock,
            title = if (canBio) "Backup PIN" else "PIN lock",
            body = if (canBio)
                "Add a 4-8 digit PIN as a backup for days fingerprint sensors act up."
            else
                "This device can't read fingerprints, so Bloo needs a 4-8 digit PIN to lock itself with.",
            done = state.appPinSet,
        ) {
            OnboardingPinForm(
                existing = state.appPinSet,
                onSet = { pin -> vm.setAppPin(pin) },
            )
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
        AnimatedContent(
            targetState = syncEnabled,
            // Explicit, not the implicit default -- every other AnimatedContent
            // in this file specifies its own transitionSpec; this one didn't,
            // which meant a real height difference between the two states (the
            // MorphButton's Material3 minimum touch target vs. the plain
            // "enabled" row) snapped instantly under the fade instead of
            // animating, a small but visible pop right when Drive sync
            // finishes setting up.
            transitionSpec = {
                (fadeIn(tween(180)) togetherWith fadeOut(tween(180)))
                    .using(SizeTransform(clip = false))
            },
            label = "onboardingSyncDone",
        ) { enabled ->
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
/**
 * The create-a-PIN mini form used by onboarding (and, in a slimmer re-use,
 * the building block of the Settings set/change/remove dialogs): two
 * matching 4-8 digit fields, a haptic'd Save only once valid. [existing]
 * true just swaps the call to "Replace PIN" semantics -- the caller handles
 * what that means; this form only ever validates and reports a valid new
 * PIN.
 */
@Composable
internal fun OnboardingPinForm(
    existing: Boolean,
    onSet: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val valid = pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS &&
        pin == confirm
    val sanitize: (String) -> String = { it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = sanitize(it); attempted = false },
            placeholder = { Text("4–8 digit PIN") },
            singleLine = true,
            shape = FieldShape,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && pin.isNotEmpty() && pin.length < PinCrypto.PIN_MIN_DIGITS,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = sanitize(it); attempted = false },
            placeholder = { Text("Confirm PIN") },
            singleLine = true,
            shape = FieldShape,
            colors = borderlessFieldColors(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = attempted && confirm.isNotEmpty() && pin != confirm,
            modifier = Modifier.fillMaxWidth(),
        )
        if (attempted && (pin.length < PinCrypto.PIN_MIN_DIGITS || pin != confirm)) {
            Text(
                "PINs must be 4-8 digits and match.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.error,
            )
        }
        MorphButton(
            onClick = {
                if (valid) {
                    haptics?.click()
                    onSet(pin)
                    pin = ""
                    confirm = ""
                } else {
                    attempted = true
                    haptics?.tick()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
            enabled = pin.isNotEmpty() && confirm.isNotEmpty(),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (existing) "Replace PIN" else "Save PIN", fontWeight = FontWeight.SemiBold)
        }
    }
}

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

    // Only Hyundai/Genesis US vehicles have a real head-unit generation to
    // confirm -- see platformOverridable's own doc.
    if (vehicle.platformOverridable) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Head-unit generation", style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontWeight = FontWeight.SemiBold)
            PlatformPicker(current = state.platformOf(vehicle)) { pt -> vm.setPlatform(vehicle, pt) }
        }
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
        // Same MorphButton as the rest of the app: a filled pill that lights up
        // secondaryContainer while the feature is on (for cars with it).
        MorphButton(
            onClick = { vm.setSeatFlag(vehicle, "sw", !sc.steeringWheel) },
            active = sc.steeringWheel,
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            activeContainerColor = scheme.secondaryContainer,
            activeContentColor = scheme.onSecondaryContainer,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            minHeight = 0.dp,
        ) {
            if (sc.steeringWheel) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
            }
            Text("Steering wheel heat", style = MaterialTheme.typography.labelMedium)
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
            val animatedProgress by animateFloatAsState(progress, tween(WizardProgressDurationMs), label = "wizProgress")
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
                    (slideInHorizontally { it * dir } + fadeIn(tween(WizardStepFadeInDurationMs))) togetherWith
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
                            WizardStepKind.PLATFORM -> WizardPlatformPage(veh, state, vm)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.bloo.bluelink.data.Powertrain.entries.forEach { pt ->
            val selected = current == pt
            val (icon, label, desc) = when (pt) {
                com.bloo.bluelink.data.Powertrain.GAS -> Triple("⛽", "Gasoline", "Combustion engine only")
                com.bloo.bluelink.data.Powertrain.HYBRID -> Triple("🔋", "Hybrid", "Gas + small electric motor (no plug)")
                com.bloo.bluelink.data.Powertrain.PHEV -> Triple("🔌", "Plug-in Hybrid", "Gas + large battery you can charge")
                com.bloo.bluelink.data.Powertrain.EV -> Triple("", "Electric", "Battery-only, no fuel tank")
            }
            // Same MorphButton as every other selector: pill at rest, fills
            // primaryContainer as a rounded square once chosen.
            MorphButton(
                onClick = { vm.setPowertrain(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                active = selected,
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                activeContainerColor = scheme.primaryContainer,
                activeContentColor = scheme.onPrimaryContainer,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                contentPadding = PaddingValues(16.dp),
                minHeight = 0.dp,
            ) {
                Text(icon, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                }
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

/**
 * One wizard page: which head-unit generation this Hyundai/Genesis US car
 * has, Gen5W or ccNC -- only ever reached for a vehicle where
 * [platformOverridable] is true (see [buildSetupPages]), same two-option
 * shape as [WizardPowertrainPage] otherwise: a selectable [Surface] row per
 * option, driven straight off `state.platformOf(vehicle)`, no local
 * "pending" selection.
 */
@Composable
private fun WizardPlatformPage(
    vehicle: com.bloo.bluelink.data.Vehicle?,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    if (vehicle == null) return
    WizardPageHeader(
        "Head-unit generation",
        "Which generation is the ${vehicle.name}?",
        "Bloo can't always tell these apart from the API alone. Confirm it here " +
            "so features like Trips only show up when they're actually available.",
    )
    val current = state.platformOf(vehicle)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VehiclePlatform.entries.forEach { pt ->
            val selected = current == pt
            val (label, desc) = when (pt) {
                VehiclePlatform.GEN5W -> "Gen5W" to "Older head unit -- no Trips, no connected-car store"
                VehiclePlatform.CCNC -> "ccNC" to "Newer head unit -- Trips and the connected-car store, where the backend supports them"
            }
            // Same MorphButton as the powertrain page: pill at rest, fills
            // primaryContainer as a rounded square once chosen.
            MorphButton(
                onClick = { vm.setPlatform(vehicle, pt) },
                modifier = Modifier.fillMaxWidth(),
                active = selected,
                containerColor = scheme.surfaceContainerHigh,
                contentColor = scheme.onSurface,
                activeContainerColor = scheme.primaryContainer,
                activeContentColor = scheme.onPrimaryContainer,
                border = BorderStroke(1.dp, scheme.outlineVariant),
                contentPadding = PaddingValues(16.dp),
                minHeight = 0.dp,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.7f) else scheme.onSurfaceVariant)
                }
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(24.dp))
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
    // The same MorphButton as everywhere: filled pill, secondaryContainer when
    // selected, outline border only while unselected (the wrapper clears it on
    // active). No second chip implementation left.
    MorphButton(
        onClick = { onClick() },
        active = selected,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        minHeight = 0.dp,
    ) {
        Text(
            label,
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

internal val FieldShape: androidx.compose.foundation.shape.RoundedCornerShape
    get() = com.bloo.uicommon.FieldShape

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

/**
 * The Screen.Loading bootstrapping placeholder -- see that state's own doc
 * (AppViewModel.kt) for why it exists. Same AuroraBackground + "Bloo"
 * wordmark [LoginScreen] opens with, so if this resolves to Login next
 * there's nothing to visually reconcile: same backdrop, same brand mark,
 * already faded in. No form, no fields, nothing interactive -- this is a
 * "still deciding" placeholder, shown for however long the cold-start
 * auto-login coroutine takes to resolve, not a real destination on its own.
 *
 * The wordmark fades in on its own (not present from frame one) rather than
 * being static: a car-status app booting into a full-strength logo the
 * INSTANT the process starts reads as an abrupt, slightly jarring "already
 * finished loading" claim before anything has actually happened yet; easing
 * it in over a beat reads as the app settling into itself instead.
 */
@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "loadingWordmarkFade",
    )
    Box(modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        Text(
            "Bloo",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center).graphicsLayer { this.alpha = alpha },
        )
    }
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
    var showPassword by remember { mutableStateOf(false) }
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

                    // Email field — label and placeholder animate with brand. Same
                    // fadeIn/fadeOut durations (220/160) as the sign-in button's own
                    // label and the privacy note below -- all three are driven by the
                    // same brand-selection change, so they should settle together.
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
                        leadingIcon = { Icon(Icons.Filled.MailOutline, contentDescription = null, modifier = Modifier.size(20.dp)) },
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
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            MorphIconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
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
                                // Same duration as the email label's own crossfade just
                                // above -- both are driven by the same brand-selection
                                // change, so they should settle together instead of at
                                // three slightly different paces.
                                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
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
                        // Same duration as this form's other two brand-driven crossfades
                        // (the email label and the sign-in button label) -- see there.
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
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
    /** Freezes the ambient drift (and the tilt sensor) while true. The search
     *  panel pauses it so typing/keyboard frames don't contend with a
     *  full-screen blur redraw -- the background's own drift is 12fps of
     *  blur work, exactly the cost a small low-end screen can't afford on
     *  top of an IME animation. */
    paused: Boolean = false,
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
    // LIVE pause flag (the coroutines and the sensor callback start once and
    // must keep seeing the newest value, not the first composition's).
    val currentPaused by rememberUpdatedState(paused)
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
                    // No writes while paused: a tilt sample just stalls (the
                    // sensor keeps delivering; we simply stop turning those
                    // samples into invalidation).
                    if (currentPaused) return
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
            if (currentPaused) {
                delay(120)
                continue
            }
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
            // Blur cut from 90dp to 44dp: the old radius was tuned when the
            // blobs were denser, but a full-screen 90dp blur redraws at every
            // drift tick (~12fps) any time this is on screen -- the single
            // most expensive steady-state draw in the app. 44dp still reads
            // as a soft wash over three large circles and costs a fraction
            // (and the pause hook above means it isn't redrawing at all
            // while the search panel is up with the keyboard animating).
            .blur((44.dp * (1f + explosionValue * 0.5f)), edgeTreatment = BlurredEdgeTreatment.Unbounded)
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
 * The app lock, drawn as an overlay on top of the blurred app. High-contrast
 * white-on-scrim text reads over any wallpaper of cars behind it; a floating
 * back arrow returns to the login screen. Centered + width-capped so it sits
 * well on phones, flip-phone cover screens and tablets alike.
 *
 * Two mechanisms, one overlay:
 *  - **Fingerprint/biometric** when the device has biometrics enrolled AND
 *    the biometric lock is on (the classic prompt, plus a "Use PIN" link);
 *  - **PIN** when a device PIN is installed -- which is always the case on
 *    the device when it has no biometrics at all (the onboarding flow
 *    requires one there, since otherwise the app could never lock) -- or
 *    when the user picks the PIN route from the biometric prompt.
 *
 * All controls are the app's standard components (MorphButton /
 * MorphTextButton / the FieldShape outline field), so the lock reads as part
 * of the same app, not a leftover scaffold screen.
 */
@Composable
internal fun LockOverlay(vm: AppViewModel) {
    val context = LocalContext.current
    val compact = isCompactCoverScreen()
    val appState by vm.state.collectAsState()
    // The device-biometric gate is a binder call -- evaluate once per overlay
    // mount, not per recomposition of the (frequently updating) state below.
    val bioAvailable = remember { vm.canUseBiometrics() }
    val appearance by vm.appearance.collectAsState()
    // Start on the biometric prompt when there's one to show; the user can
    // switch to PIN; devices without biometrics land straight on PIN.
    var usePinMode by remember { mutableStateOf(!bioAvailable) }
    var pin by remember { mutableStateOf("") }
    // A wall-clock ticker that only runs while a rejection window is open --
    // the countdown line needs a fresh "seconds left" each second, and
    // nothing else here wants a 1s recomposition loop.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val lockout = appState.pinLockout
    val rejected = lockout.isLocked(nowTick)
    val remainingMs = lockout.remainingMs(nowTick)

    fun authenticateBiometric() {
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
    fun attemptPin() {
        if (pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS && !rejected) {
            vm.verifyAppPin(pin)
            pin = ""
        }
    }
    LaunchedEffect(Unit) {
        // Fresh overlay (re-lock, cold start) → clear any stale rejection.
        vm.acknowledgePinRejection()
        if (!usePinMode) authenticateBiometric()
        while (true) {
            delay(250)
            nowTick = System.currentTimeMillis()
        }
    }
    // Pattern for "pick the PIN route": tapping "Use PIN" once; a failed
    // biometric prompt stays on the biometric UI; PIN always returns here on
    // the next lock anyway (fresh overlay remounts at the default mode).
    val haptics = LocalHaptics.current
    val noRipple = remember { MutableInteractionSource() }
    val showBiometric = bioAvailable && !usePinMode
    Box(
        Modifier
            .fillMaxSize()
            // Darken the blur for legibility, and swallow taps to the app behind.
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = noRipple, indication = null) {},
    ) {
        // Floating back arrow -> login: the same FloatingIcon every other floating
        // circular button in the app uses, with the lock scrim's plain-white
        // override colours (this is what its old hand-rolled Surface now
        // passes in -- one circle button, one component).
        FloatingIcon(
            icon = Icons.Filled.ArrowBack,
            description = "Back to login",
            onClick = { haptics?.click(); vm.lockToLogin() },
            containerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding(),
        )

        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showBiometric) {
                // --- Classic biometric prompt ---------------------------------
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
                    if (appState.appPinSet) "Confirm it's you, or use your PIN." else "Confirm it's you to reach your vehicles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
                // White pill for maximum contrast over the dimmed blur.
                MorphButton(
                    onClick = { authenticateBiometric() },
                    modifier = Modifier.height(if (compact) 56.dp else ControlHeight),
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 18.dp),
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Unlock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (appState.appPinSet) {
                    Spacer(Modifier.height(12.dp))
                    MorphTextButton(
                        "Use PIN",
                        onClick = { haptics?.click(); usePinMode = true },
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor = Color.White,
                    )
                }
            } else if (appState.appPinSet) {
                // --- PIN prompt (device has no biometrics, or user chose PIN) --
                Surface(
                    shape = RoundedCornerShape(if (compact) 20.dp else 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha(0.97f)),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Column {
                                Text(
                                    "Enter your PIN",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    if (bioAvailable) "Your fingerprint or your PIN unlocks Bloo."
                                    else "This device has no fingerprint sensor, so a PIN is required.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it.take(PinCrypto.PIN_MAX_DIGITS).filter { ch -> ch.isDigit() } },
                            placeholder = { Text("4–8 digit PIN") },
                            singleLine = true,
                            shape = FieldShape,
                            colors = borderlessFieldColors(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { attemptPin() }),
                            supportingText = {
                                when {
                                    rejected -> Text(
                                        "Too many attempts — try again in ${formatLockoutSeconds(remainingMs)}",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> Text(
                                        lockout.attemptsRemainingInBatch(nowTick)?.let { left ->
                                            if (left <= 2) "Careful — $left ${if (left == 1) "attempt" else "attempts"} before a lockout"
                                            else "${PinLockout.STRIKES_PER_BATCH} wrong attempts lock the app for 30 seconds — the wait doubles each time"
                                        } ?: "",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        MorphButton(
                            onClick = { attemptPin() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = !rejected && pin.length in PinCrypto.PIN_MIN_DIGITS..PinCrypto.PIN_MAX_DIGITS,
                        ) {
                            Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock", fontWeight = FontWeight.SemiBold)
                        }
                        if (bioAvailable) {
                            Spacer(Modifier.height(8.dp))
                            MorphTextButton(
                                "Use fingerprint",
                                onClick = { haptics?.click(); usePinMode = false; authenticateBiometric() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                // No mechanism at all -- should not be reachable (the lock
                // gate refuses to engage without one); a calm fallback so the
                // overlay never dead-ends silently.
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(if (compact) 44.dp else 72.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Bloo is locked",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Please try opening Bloo again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
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
internal class WrapPagerState(val pager: PagerState, val realCount: Int) {
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
internal fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0): WrapPagerState {
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
internal fun Modifier.pagerDepth(pager: PagerState, page: Int): Modifier = graphicsLayer {
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

/** The one shared "gap below the status bar" every free-floating header
 *  element -- [FloatingIcon]'s own default [FloatingIcon.outerPadding], every
 *  [TitleFlightOverlay] call site's `cornerY`, the page-dot overlays -- lines
 *  up against, so they all sit on the same row instead of each surface
 *  reproducing its own close-but-not-quite value (this used to be `12.dp` in
 *  some places and `10.dp` in others, an inconsistency invisible on any one
 *  screen alone but obvious the moment two headers are compared side by
 *  side). */
internal val HeaderCornerGap = 12.dp

/** The one shared size every free-floating header BUTTON -- [FloatingIcon]'s
 *  circle, and anything meant to sit in the same row as one -- is drawn at,
 *  so two buttons on the same header always share a vertical centre. Used to
 *  be re-typed as a bare `48.dp` at each call site (and, in one place,
 *  [LockOverlay]'s own hand-rolled back button, mistyped as `46.dp` -- a
 *  silent 2dp size/alignment drift from every other header button in the
 *  app). */
internal val HeaderButtonSize = 48.dp

/** Extra breathing room reserved *below* a header button's own footprint
 *  (`HeaderCornerGap + HeaderButtonSize`) before real content is allowed to
 *  start, on top of whatever `Arrangement.spacedBy` a column already adds.
 *  Needed because a button's true on-screen silhouette is bigger than its
 *  logical box: [FloatingIcon] draws `ambientRing()`/`dropShadow()` glow
 *  outside its 48dp circle, and content below it (e.g. a [Pebble] row) has
 *  its own card shadow -- so reserving exactly the button's geometric
 *  footprint (as ExpandedCar's dual-column header used to) leaves only the
 *  column's incidental 12dp `spacedBy` gap as buffer, which those two halos
 *  can visibly eat into. Mirrors the same "bare inset isn't enough, add a
 *  named clearance" pattern [PagerDotClearance] already uses below. */
internal val HeaderContentClearance = 12.dp

/** A small translucent circular icon button used as a floating overlay control.
 *  [outerPadding] is the breathing room around the [HeaderButtonSize] circle -
 *  the default ([HeaderCornerGap], a 72dp footprint) suits free-floating
 *  overlay corners; tight rows (the cover screen's title row, at 2dp) keep
 *  that footprint down to 52dp on a ~260dp-tall screen. */
@Composable
internal fun FloatingIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: Dp = HeaderCornerGap,
    // Overrides for surfaces that float over something other than the app's
    // content: the lock overlay's back arrow sits on a dark scrim, not a
    // card, so it deliberately uses plain white instead of the glass fill
    // (see LockOverlay's own note -- the old hand-rolled Surface there was
    // this exact shape re-built by hand; it now passes these instead).
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
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
        color = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = glassContainerAlpha()),
        contentColor = contentColor,
        interactionSource = interaction,
        modifier = modifier
            .padding(outerPadding)
            .size(HeaderButtonSize)
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
internal fun HeroHeader(
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
        // ONE reveal curve for the whole expanded readout -- the travelling
        // numbers, the status line and the fuel row all fade in AROUND THE
        // SAME WINDOW (a single smoothstep on heroT, with a soft head start
        // so the card's own open bounce has begun before the content lands),
        // instead of three pieces each skipping in on their own threshold.
        // Same clock = no "one part pops in, the rest follows later" stagger
        // (reported: fade in gracefully, and in lockstep). Pieces that ride
        // the card's photo (numbers/status/fuel) share this alpha; the bar
        // itself stays persistent because it is the "what the card shows you"
        // element, not a detail of it.
        val statusAlpha = run {
            val t = ((heroT - 0.15f) / 0.5f).coerceIn(0f, 1f)
            t * t * (3f - 2f * t)
        }

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

        // Reports this title's own real, measured position (and colour) to a
        // VehicleDetailContent ancestor's TitleFlightOverlay, if one is
        // providing it (null everywhere else -- ExpandedCar's own pebble
        // list excludes "summary" entirely, so this only ever applies
        // here). This slot is drawn permanently invisible below -- see
        // TitleFlightOverlay's own doc for why: it's a position anchor only,
        // the actual visible Text lives entirely in that overlay now.
        val heroTitleFlight = LocalHeroTitleFlight.current
        // The ambient flight instance itself changes the moment this page
        // becomes the hoisted/settled one (GarageScreen switches which
        // HeroTitleFlight it hands down) -- but onGloballyPositioned below
        // only fires on an actual RELAYOUT of this node, not merely because
        // the target it reports to changed. If this page's title happened
        // not to move on screen at that exact moment (the ordinary case: it
        // was already laid out as the pre-composed pager neighbour), nothing
        // would ever re-trigger a report to the NEW flight, leaving its
        // reportGeneration stuck and its caller's "ready" gate unresolved --
        // not just for a frame, but indefinitely, until some unrelated
        // relayout (a scroll) happened to occur. Caching the last known
        // coordinates and re-pushing them the instant the flight identity
        // changes closes that gap: the new flight gets a real report in the
        // very same frame it becomes current, with no dependency on layout
        // happening to be dirty at that moment.
        val lastHeroCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
        // Runs SYNCHRONOUSLY, during composition -- NOT inside a
        // LaunchedEffect, which is what this was until a real bug traced it
        // here. A coroutine only starts running after this composition pass
        // COMMITS, which is strictly AFTER TitleFlightOverlay's own
        // synchronous `val docked by flight.docked` read (and its cold-mount
        // `progress.snapTo()` branch, and its `settled`/onSettledChanged
        // computation) have already consumed whatever STALE docked/position
        // state the newly-adopted flight was left holding by whichever page
        // or moment last drove it -- one whole recomposition too late to
        // prevent a visible pop-to-corner/pop-to-hero flash (and, since a
        // stale-true `docked` can make `settled` spuriously true on that
        // same first frame, potentially a spurious re-hoist/re-unhoist
        // oscillation right after). A remembered "last corrected identity"
        // guard -- mirroring TitleFlightOverlay's own `lastDocked` latch
        // just below -- fires this exactly once per real identity change,
        // synchronously, before any sibling composable in this SAME pass
        // (including TitleFlightOverlay) gets a chance to read the flight.
        var lastCorrectedFlight by remember { mutableStateOf<HeroTitleFlight?>(null) }
        if (lastCorrectedFlight !== heroTitleFlight) {
            // onSettled, not onPositioned -- this is the FIRST report the
            // (possibly newly-current) flight gets from this page becoming
            // settled, not a continuous scroll update. The shared hoisted
            // flight is reused across every page switch now (see its own
            // doc), so its hysteresis baseline can be left over from
            // whichever DIFFERENT page was settled before this one -- biasing
            // this page's own first read through the wrong branch of that
            // hysteresis and rendering it docked (or undocked) purely
            // because of where the previous, unrelated page happened to
            // leave the flag. onSettled bypasses that bias for this one
            // report; onPositioned (below) still carries it correctly for
            // this page's OWN later, continuous scroll updates.
            lastHeroCoords.value?.let { heroTitleFlight?.onSettled(it.positionInRoot(), it.size) }
            lastCorrectedFlight = heroTitleFlight
        }
        // Follows the morph rather than switching: the photo fades in over the same
        // t, so the name has to travel from the surface's own colour to the light one
        // the scrim is built for. Snapping at a threshold would flash a white name
        // onto a still-white card for the frames before the photo arrives.
        val heroTitleColorNow = lerp(MaterialTheme.colorScheme.onSurface, HeroOnPhoto, heroT)
        if (heroTitleFlight != null) heroTitleFlight.color = heroTitleColorNow
        // Mirrors PebbleShell's own hero title grow/shrink spring EXACTLY
        // (same damping/stiffness, same collapsed/expanded type-step ratio)
        // -- see that spring's own doc for why these particular numbers.
        // PebbleShell's copy of this spring only ever drives the invisible
        // anchor Text underneath; without a second copy here, the one Text
        // that's actually PAINTED (TitleFlightOverlay's) never grew or
        // shrank with the pebble at all.
        val headerT by animateFloatAsState(
            targetValue = if (photoExpanded) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessVeryLow),
            label = "heroTitleFlightScale",
        )
        val collapsedTitleScale = with(LocalDensity.current) {
            MaterialTheme.typography.titleMedium.fontSize.toPx() /
                MaterialTheme.typography.headlineSmall.fontSize.toPx()
        }
        if (heroTitleFlight != null) {
            heroTitleFlight.titleScale = collapsedTitleScale + (1f - collapsedTitleScale) * headerT
        }
        PebbleShell(
            expanded = photoExpanded,
            onToggle = { vm.togglePebble(v, com.bloo.bluelink.data.HERO_PHOTO_SECTION) },
            icon = Icons.Filled.DirectionsCar,
            title = v.name,
            vm = vm,
            dragHandle = dragHandle,
            titleColor = heroTitleColorNow,
            titleModifier = if (heroTitleFlight != null) {
                // Permanently invisible -- this slot exists to hold the real,
                // measured layout position for TitleFlightOverlay's flying
                // Text to land on; that Text is the only thing that actually
                // PAINTS the name any more. See TitleFlightOverlay's own doc.
                Modifier
                    .onGloballyPositioned {
                        lastHeroCoords.value = it
                        heroTitleFlight.onPositioned(it.positionInRoot(), it.size)
                    }
                    .alpha(0f)
                    // Position anchor only -- see TitleFlightOverlay's matching
                    // measuring-copy comment for why this can't stay in the
                    // accessibility tree: it would announce the car's name a
                    // second time, on top of the one real flying Text.
                    .clearAndSetSemantics {}
            } else {
                Modifier
            },
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
                        Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .graphicsLayer { alpha = statusAlpha },
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
        UpdateStatusLine(deltaLabel, seamless, state, vm)
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
            // A body-level action for the two moments the header button alone can
            // be missed -- the idle "Download" state (the header just says
            // "Update") and the ready "Install" state. Same state logic as the
            // header action, spelled out here so the body reads as one complete
            // flow whether or not the pill is spotted.
            if (!state.updateDownloading && !state.updateInstalling) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphButton(
                        onClick = {
                            when {
                                state.updateApkReady -> vm.installDownloadedUpdate()
                                hasDirectDownload -> vm.downloadUpdateInBackground()
                                else -> {
                                    val opened = runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl)))
                                    }.isSuccess
                                    if (opened) vm.dismissUpdate() else vm.reportError("Couldn't open the release page.")
                                }
                            }
                        },
                        active = state.updateApkReady,
                        activeContainerColor = ChargeGreen,
                        activeContentColor = Color.White,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            if (state.updateApkReady) Icons.Filled.CheckCircle else Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                state.updateApkReady -> if (seamless) "Install now" else "Install"
                                hasDirectDownload -> "Download now"
                                else -> "Open release page"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (state.updatePendingDismiss) {
                        MorphTextButton("Keep it", onClick = vm::undoDismissUpdate)
                    }
                }
                Spacer(Modifier.height(4.dp))
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
/**
 * The live status half of the update flow: the tonal icon badge, the
 * animated one-line status ("Downloading 46%", "Downloaded · tap Install",
 * "Installing silently via Shizuku…") and the download progress bar.
 *
 * Shared by the update pebble's body and the Settings Updates card, so the
 * two can never drift apart -- this is the same state machine rendered the
 * same way in both places, exactly the "one implementation" rule the
 * Settings card's remake is about.
 *
 * [deltaLabel] ("build 812 → 828") is what the idle state says; [seamless]
 * selects the Shizuku phrasing and the "installs silently" hint.
 */
@Composable
internal fun UpdateStatusLine(
    deltaLabel: String,
    seamless: Boolean,
    state: UiState,
    vm: AppViewModel,
) {
    val scheme = MaterialTheme.colorScheme
    val downloadProgress by vm.updateDownloadProgress.collectAsState()
    // ONE state-driven status line -- see the tile's own long comment (git
    // history) on why statusKind, not the rendered string, drives the
    // AnimatedContent: the static word must stay put while the percent moves.
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
        // own coloured circle" weight CoverHero gives every stat it leads with.
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
            // color resolved explicitly, not left Color.Unspecified -- the
            // "Downloading X%" AnimatedValue below renders through BasicText,
            // which (unlike Text) does NOT fall back to LocalContentColor for
            // an unspecified color; it fell back to Android's own paint
            // default (black) instead.
            val textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current,
            )
            when (kind) {
                "installing" -> Text("Installing silently via Shizuku…", style = textStyle)
                "downloading" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Downloading", style = textStyle)
                    downloadProgress?.let { p ->
                        Text(" ", style = textStyle)
                        // Its own AnimatedValue, not part of this AnimatedContent's own
                        // string -- this is the one piece of the line that legitimately
                        // changes every tick, so it's the only piece that should move.
                        // fontFeatureSettings = "tnum" enables tabular figures so only
                        // the changing digit animates up without the whole number
                        // shifting left/right.
                        com.bloo.uicommon.AnimatedValue(
                            "${(p * 100).roundToInt()}%",
                            style = textStyle.copy(fontFeatureSettings = "tnum"),
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
    // this bar arrives and leaves while the card is already open (download
    // starts, download finishes).
    PopVisible(visible = state.updateDownloading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val p = downloadProgress
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
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = LocalContentColor.current,
                        fontFeatureSettings = "tnum",
                    ),
                    reduceMotion = LocalReduceMotion.current,
                )
            }
        }
    }
}

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
internal fun HeroPhotoBackdrop(
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
        // Memoized like the map tiles: creating a fresh ImageRequest every recomposition
        // would trigger unnecessary reloads and cause visible flicker/jank.
        val context = LocalContext.current
        val imageRequest = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
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
internal fun ChargeFuelBar(
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

internal val LocalForceExpanded = staticCompositionLocalOf { false }

/**
 * When true (cover-screen tiles), a pebble stretches to fill the available height
 * and scrolls internally if its content is taller - so each tile fills the screen.
 */
internal val LocalPebbleFillHeight = staticCompositionLocalOf { false }

/** Tile names that [CompactCar] can render — unknown sections are excluded. */
internal val CompactKnownTiles = setOf(
    // No "controls" here, deliberately. It was added when the lock/horn
    // controls were unreachable on the cover, but as its own page it was one
    // short row of buttons above two thirds of an empty screen. Those same
    // controls now live in CoverMainTile's permanent action bar, on the page
    // the cover opens on -- so a separate page for them would be a second,
    // emptier copy of something already on screen.
    // "update" IS here: the update-available card is a first-class pebble on
    // every phone page, and it silently vanished from the cover (reported).
    // Rendered through the same SinglePebble routing as every other tile, so
    // the Install/Remind-me/Not-now card works on the flip screen exactly as
    // it does unfolded.
    "climate", "charge", "location", "weather", "trips", "info", "diagnostics", "ai", "update"
)

/**
 * When set, [Pebble] in fill-height cover-screen mode uses this scroll state
 * instead of creating a local one — lets the parent observe scroll position
 * to decide whether to switch pager pages or scroll tile content.
 */
internal val LocalCoverScrollState = compositionLocalOf<ScrollState?> { null }

/**
 * Shared flag set true while the cover-screen page scrubber is active, so the
 * parent [CompactGarage] can suspend horizontal car-switching swipes during a
 * scrub. Provided around the HorizontalPager content.
 */
internal val LocalCoverScrubbing = staticCompositionLocalOf<MutableState<Boolean>?> { null }

/**
 * The live pull-to-refresh distance (0..1+), published by [Refreshable] so the
 * floating overlays in [GarageScreen] (page dots, settings/back/flip buttons)
 * can track the pull in real time instead of only animating once refresh starts.
 */
internal val LocalPullFraction =
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
    // Split into the rolling digits and the STATIC suffix ("%"): only the
    // digits roll up/down, the unit glyph rides with them as one unmoved
    // companion -- rolling the whole string including the "%" read as the
    // entire readout lifting off, which is not what a digit roll is.
    val digits = text.takeWhile { it.isDigit() }
    val suffix = text.drop(digits.length)
    // Track the previous NUMERIC value so we can roll in the right direction.
    val current = digits.toIntOrNull()
    var previous by remember { mutableStateOf(current) }
    val goingUp = (current ?: 0) >= (previous ?: 0)
    LaunchedEffect(current) { previous = current }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.wrapContentWidth()) {
        AnimatedContent(
            targetState = digits,
            transitionSpec = {
                val dir = if (goingUp) 1 else -1
                (fadeIn(tween(180)) + slideInVertically { dir * it / 2 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically { -dir * it / 2 })
            },
            label = "num",
        ) { t -> WiggleText(t, style = style, fontWeight = fontWeight, color = color) }
        if (suffix.isNotEmpty()) {
            WiggleText(suffix, style = style, fontWeight = fontWeight, color = color)
        }
    }
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

/**
 * Small pill-shaped fact badge -- [CarHeaderRow]'s own model/powertrain and
 * "updated x ago" facts, which used to be two stacked plain caption lines
 * with no container of their own, reading as an afterthought next to the
 * rest of the app's chip/pill chrome. A muted [surfaceContainerHigh] fill,
 * not the floating pills' glass treatment -- this sits on the app's own
 * ordinary surface, not over an unpredictable photo, so it doesn't need
 * that treatment's guaranteed contrast, just enough of a container to read
 * as a distinct fact rather than body text bleeding into the row beside it.
 */
@Composable
private fun MetaChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Updated x ago" fact, as a [MetaChip]. Null (renders nothing) until a
 *  first fetch has actually landed for [v]. */
@Composable
private fun LastUpdatedLabel(v: Vehicle, state: UiState, modifier: Modifier = Modifier) {
    val rel = rememberRelativeTime(state.fetchedAt(v)) ?: return
    MetaChip("Updated $rel", modifier, icon = Icons.Filled.Refresh)
}

// --- Drag-and-drop reordering --------------------------------------------

/**
 * Animates an item gliding to its new placement when siblings reorder around it,
 * instead of snapping. Used for the non-dragged pebbles so they slide out of the
 * way smoothly. The dragged item is offset manually and must not use this.
 */
internal fun Modifier.animatePlacement(): Modifier = composed {
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
    // Memoize the style copy to avoid recreating it when color/fontWeight don't change.
    val resolvedStyle = remember(style, fontWeight, resolvedColor) {
        style.copy(fontWeight = fontWeight, color = resolvedColor)
    }
    com.bloo.uicommon.WiggleText(
        text = text,
        style = resolvedStyle,
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
internal class HotSeatDrag {
    var section by mutableStateOf<String?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var slotTopLeft by mutableStateOf(Offset.Zero)
    var slotSize by mutableStateOf(IntSize.Zero)
    val overSlot: Boolean
        get() = section != null && slotSize.width > 0 &&
            pointer.x in slotTopLeft.x..(slotTopLeft.x + slotSize.width) &&
            pointer.y in slotTopLeft.y..(slotTopLeft.y + slotSize.height)
}

internal val LocalHotSeatDrag = staticCompositionLocalOf<HotSeatDrag?> { null }

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
        // Standard gap between connected button elements (matches SplitExpandButton's
        // own 3dp gap for visual consistency across all grouped controls).
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
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
            // both a real touch target and the Button role for free.
            // Manual haptics?.click() dropped: MorphIconButton fires it. This was
            // the one bare IconButton in the file that remembered to.
            //
            // 32dp, not the full 48dp guideline: this sits in a caption row
            // (Text + this button) inside a 58dp-wide swatch column, itself one
            // of several in a tight grid -- 48dp here would overflow that
            // column or push its touch target into the neighbouring swatch's.
            // 32dp is a real improvement over the old 28dp that still fits.
            MorphIconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
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
    // Both brushes only depend on colours that change far less often than the
    // Canvas redraws while dragging (sat/value redraw on every pointer move):
    // satValueBrush only needs to change when the hue itself changes, and
    // hueBrush's gradient stops never change at all. Hoisting them out of the
    // draw scope avoids allocating a new List + Brush on every drag frame.
    val satValueBrush = remember(pureHue) { Brush.horizontalGradient(listOf(Color.White, pureHue)) }
    val hueBrush = remember(hueGradient) { Brush.horizontalGradient(hueGradient) }
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
            drawRect(satValueBrush)
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
            drawRect(hueBrush)
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
internal data class GroupIconAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)


/**
 * The one button style used across the whole app. It rests as a **pill** and
 * becomes a **rounded rectangle** only while [active] (an on/toggled state) - or
 * momentarily while pressed. When [active], it fills with [activeContainerColor].
 * Its width springs (with a little overshoot) whenever the content width changes,
 * e.g. the label flips Start -> Stop.
 *
 * This IS the shared [MorphButtonCore] from :uicommon -- the same machinery the
 * watch's MorphButton uses -- dressed in this module's Material theme colours,
 * haptics and M3 content padding, plus two phone-wide conventions:
 *
 *  - [minHeight] of 48dp (the M3 touch target the old `Button` enforced
 *    implicitly) unless a caller opts out to keep a shorter pill
 *    (split-button halves, preset pills).
 *  - `selected = [active]` semantics, so TalkBack hears the state, not just
 *    the label ("Unlock" says what happens, not what is).
 *
 * Every other button-looking control in this app -- the split action+chevron
 * pills, the standalone chevron, the preset pills, the cover action bar --
 * is this same component; the ones that look different simply pass different
 * shapes (shapeForCorner) and colours. There are no separate button types.
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
    /** Overrides the disabled content tone (default: resolved content at 38%
     *  alpha -- "only the label fades"). The cover action button passes its
     *  own full-alpha tone because it dims the WHOLE pill itself. */
    disabledContentColor: Color? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    // An asymmetric shape to use instead of the plain pill<->square morph --
    // for a connected button-group segment (see StateControl), or a split
    // button half, whose inner (seam) corners stay small while the outer
    // corner is the one that morphs. Receives the raw morph progress
    // (0 = pill, 1 = fully morphed) and the animated corner percent, so the
    // shape can derive any corner geometry from the button's own spring.
    shapeForCorner: ((morph: Float, cornerPercent: Int) -> Shape)? = null,
    /** Hold-to-act action (chevron easter egg, cover flash-lights). */
    onLongClick: (() -> Unit)? = null,
    /** Haptic for a plain click; null = the standard click() pulse. The lock
     *  button overrides with heavy(), the chevron with tick()/click() by
     *  direction. */
    onClickHaptic: (() -> Unit)? = null,
    /** The pill's corner-percent when idle (50 = perfect pill) and when
     *  [active]/pressed (default 28 = the app's standard rounded square).
     *  Overridable so a fixed-height square button (cover actions, chevron
     *  nub) can land on its own exact corner radius. */
    pillCornerPercent: Float = PillCornerPercent,
    morphedCornerPercent: Float = MorphedCornerPercent,
    /** 48dp is the minimum touch-target height M3 `Button` enforced implicitly;
     *  pass 0.dp to let a short pill keep its natural height. */
    minHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = LocalHaptics.current
    val clickHaptic = onClickHaptic ?: { haptics?.click() }
    // The content tone content lambdas inherit, provided the way M3's Button
    // provides it internally (the shared core is foundation-only and cannot
    // reach material3's LocalContentColor).
    val resolvedContent = if (active) activeContentColor else contentColor
    val providedContent = if (enabled) {
        resolvedContent
    } else {
        // Keep the button's full background when disabled (only the label
        // fades) instead of M3's default onSurface@12%, which is invisible
        // against light cards and made disabled buttons look backgroundless.
        disabledContentColor ?: resolvedContent.copy(alpha = 0.38f)
    }
    CompositionLocalProvider(LocalContentColor provides providedContent) {
        MorphButtonCore(
            onClick = { clickHaptic(); onClick() },
            modifier = modifier
                // `active` is otherwise a colour-only change -- most call sites also
                // swap their label text (Lock/Unlock, Start/Stop), which is why this
                // mostly "worked" for TalkBack by accident, but that's caller
                // discipline, not something the shared button guarantees. Setting
                // `selected` here makes every MorphButton correct by construction:
                // the app's one button framework, so this is the single highest-
                // leverage place to fix it.
                .semantics { selected = active }
                .animateContentSize(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
                .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier),
            enabled = enabled,
            active = active,
            containerColor = containerColor,
            contentColor = contentColor,
            activeContainerColor = activeContainerColor,
            activeContentColor = activeContentColor,
            contentPadding = contentPadding,
            border = if (active) null else border,
            interactionSource = interactionSource,
            onLongClick = onLongClick,
            pillCornerPercent = pillCornerPercent,
            morphedCornerPercent = morphedCornerPercent,
            shapeForCorner = shapeForCorner,
            content = content,
        )
    }
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
    val chipSelected = selected
    // The same MorphButton as everywhere: pill when idle, primary fill +
    // rounded box when selected, standard corner-percent animation. The chip's
    // historic 22dp/12dp corners on its ~40dp height are just under the
    // framework's 50/28 defaults, so it uses the shared defaults verbatim.
    MorphButton(
        onClick = { onClick() },
        onClickHaptic = { haptics?.tick() },
        active = selected,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        minHeight = 0.dp,
        // Same gap MorphSegmented had: a selectable pill with no `selected`
        // semantics reaching TalkBack, which announced every chip identically
        // regardless of which one was actually active. Captured into a
        // differently-named local first -- inside semantics{}, `selected` on
        // its own resolves to the SemanticsPropertyReceiver's own property,
        // not this composable's `selected` parameter of the same name.
        modifier = modifier.semantics { this.selected = chipSelected },
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chipSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}


// --- Pebble (expandable, reorderable section) -----------------------------


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

    // Easter egg: same hold as SplitExpandButton — long-press spins + vibrates
    var easterEggTriggered by remember { mutableStateOf(false) }
    val easterEggSpin by animateFloatAsState(
        targetValue = if (easterEggTriggered) 360f else 0f,
        animationSpec = if (easterEggTriggered) spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow) else snap(),
        label = "easterEggMorphSpin",
        finishedListener = { if (easterEggTriggered) easterEggTriggered = false },
    )
    // This button is a FIXED 50dp square, so a 50% corner is a true circle and
    // 10dp is exactly 20%. The default 28 (the app's standard rounded square)
    // is deliberately overridden to keep this control's 10dp corners, which
    // the shared percent model expresses cleanly for a fixed-size button.
    MorphButton(
        onClick = { onToggle() },
        onClickHaptic = { if (expanded) haptics?.tick() else haptics?.click() },
        onLongClick = {
            // Easter egg: hold the chevron to spin it + vibrate.
            if (!easterEggTriggered) {
                easterEggTriggered = true
                haptics?.heavy()
            }
        },
        // Expanded highlight = the SAME active state as lock/unlock: primary
        // fill, onPrimary content, straight from MorphButton's defaults.
        active = expanded,
        contentPadding = PaddingValues(0.dp),
        pillCornerPercent = 50f,
        morphedCornerPercent = 20f,
        minHeight = 0.dp,
        // Same as SplitExpandButton's chevron: the icon's contentDescription is
        // the next action, this is the current state -- both together instead
        // of only announcing what tapping does. Tap toggles; holding spins the
        // chevron (easter egg) without toggling.
        modifier = Modifier.size(50.dp).semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
    ) {
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            // Larger chevron icon (24dp to match action button icon size), with
            // easter egg spin animation when the chevron is held.
            modifier = Modifier.size(24.dp).rotate(rotation + easterEggSpin),
        )
    }
}

// --- Trips (trip history) --------------------------------------------------


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
            val baseStyle = LocalTextStyle.current
            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
            // Memoized to avoid recreating the TextStyle.copy() on every recomposition.
            val valueStyle = remember(baseStyle, onSurfaceColor) {
                baseStyle.copy(
                    fontWeight = FontWeight.Medium,
                    color = onSurfaceColor,
                    textAlign = TextAlign.End,
                )
            }
            com.bloo.uicommon.AnimatedValue(
                value = value,
                style = valueStyle,
                maxLines = 2,
                reduceMotion = LocalReduceMotion.current,
            )
        }
    }
}

/** A small bold group heading used inside the Car-info pebble. */
@Composable
internal fun SectionLabel(text: String) {
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
internal fun CommandButton(
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
