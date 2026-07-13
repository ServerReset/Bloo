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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CreditCard
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
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
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherCode
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.supportsConnectedStore
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.targetForCurrentPlug
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

@Composable
fun BlooApp(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // One haptics engine for the whole app; its enabled flag tracks the setting.
    val haptics = remember { Haptics(context.applicationContext) }
    haptics.enabled = appearance.hapticsEnabled

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

    LaunchedEffect(state.message) {
        state.message?.let {
            scope.launch { snackbar.showSnackbar(it) }
            vm.clearMessage()
        }
    }

    CompositionLocalProvider(LocalHaptics provides haptics) {
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
    Box(
        Modifier
            .fillMaxSize()
            .blur(lockBlur)
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
                val snackColors = when (state.messageType) {
                    "success" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                    "info" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = snackColors.first,
                    contentColor = snackColors.second,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .padding(16.dp)
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
                    val appearance by vm.appearance.collectAsState()
                    Box(Modifier.fillMaxSize()) {
                        if (appearance.auroraBackground) AuroraBackground(Modifier.matchParentSize(), appearance)
                        GarageScreen(state, vm)
                    }
                }
                Screen.Settings -> SettingsScreen(vm)
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
        // App-wide, not tied to any one screen; skipped while locked so a
        // biometric prompt and an update prompt never compete for attention.
        if (!state.locked) {
            state.updateInfo?.let { info ->
                UpdatePromptDialog(info = info, vm = vm)
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

private fun buildSetupPages(vehicles: List<com.bloo.bluelink.data.Vehicle>): List<WizardPage> = buildList {
    vehicles.forEach { v ->
        add(WizardPage(WizardStepKind.POWERTRAIN, v.vin))
        add(WizardPage(WizardStepKind.SEATS, v.vin))
        add(WizardPage(WizardStepKind.STEERING, v.vin))
    }
}

/** First-run: one scrollable page with welcome, per-car config, and permissions. */
@Composable
private fun OnboardingScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val state by vm.state.collectAsState()
    val canBio = remember { vm.canUseBiometrics() }
    val scheme = MaterialTheme.colorScheme
    var countdown by remember { mutableIntStateOf(15) }

    LaunchedEffect(Unit) {
        Fireworks.playSound(context)
        haptics?.fireworks()
        while (countdown > 0) {
            delay(1_000)
            countdown--
        }
    }
    BackHandler {}

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Modifier.matchParentSize())
        FireworksOverlay(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            // --- Welcome header ---
            Spacer(Modifier.height(4.dp))
            StaggerFadeIn(delay = 0, offset = 20) {
                Text(
                    "Welcome to Bloo",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = scheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            StaggerFadeIn(delay = 120, offset = 16) {
                Text(
                    "Control your Hyundai, Genesis, or Kia from this app. Set up your car details below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                )
            }

            Spacer(Modifier.height(28.dp))

            // --- Per-car configuration ---
            if (state.vehicles.isNotEmpty()) {
                Text(
                    "Your car${if (state.vehicles.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                )
                Text(
                    "Bloo cannot read powertrain or feature info from the API. Set them once here so the right controls appear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                state.vehicles.forEach { vehicle ->
                    val sc = state.seatConfigs[vehicle.vin] ?: com.bloo.bluelink.data.SeatConfig()
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                vehicle.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onSurface,
                            )
                            // Powertrain
                            Text(
                                "Powertrain",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val currentPt = state.powertrainOf(vehicle)
                            PowertrainPicker(current = currentPt) { pt -> vm.setPowertrain(vehicle, pt) }
                            // Seats — individual row per position with heat / cool toggles
                            Text(
                                "Seats",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(scheme.surfaceContainerHighest)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                SeatPositions.forEachIndexed { i, pos ->
                                    if (i > 0) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.35f))
                                    WizardSeatRow(pos.label, pos.heat(sc), pos.cool(sc),
                                        { vm.setSeatFlag(vehicle, pos.heatKey, it) }, { vm.setSeatFlag(vehicle, pos.coolKey, it) })
                                }
                            }
                            // Steering wheel heat
                            Text(
                                "Extras",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
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
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Permissions ---
            Text(
                "Optional setup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var notifGranted by remember {
                    mutableStateOf(com.bloo.bluelink.data.Notifications.hasPermission(context))
                }
                val notifLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted -> notifGranted = granted }
                MorphButton(
                    onClick = { if (!notifGranted) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    active = notifGranted,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(
                        if (notifGranted) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (notifGranted) "Notifications enabled" else "Enable notifications",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (canBio) {
                var bioEnabled by remember { mutableStateOf(false) }
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
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(
                        if (bioEnabled) Icons.Filled.CheckCircle else Icons.Filled.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (bioEnabled) "Fingerprint lock enabled" else "Enable fingerprint lock",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(16.dp))

            // --- Enter Bloo CTA ---
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Refresh, contentDescription = null,
                            tint = scheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sync across devices",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Back up your settings to Google Drive for automatic sync.",
                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    var showDriveDialog by remember { mutableStateOf(false) }
                    val driveSaveLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/json"),
                    ) { uri -> uri?.let { vm.setSyncUri(it) } }
                    val driveOpenLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument(),
                    ) { uri -> uri?.let { vm.importSettingsAndSync(context, it) } }
                    if (showDriveDialog) {
                        AlertDialog(
                            onDismissRequest = { showDriveDialog = false },
                            title = { Text("Google Drive sync") },
                            text = {
                                Text(
                                    "Sync your settings across devices using Google Drive.\n\n" +
                                        "Save to Drive: pick a folder in Google Drive to " +
                                        "store your settings. Changes sync automatically.\n\n" +
                                        "Open from Drive: pick the settings file from " +
                                        "Google Drive on another device.",
                                )
                            },
                            confirmButton = {
                                MorphTextButton("Save to Drive", onClick = {
                                    showDriveDialog = false
                                    driveSaveLauncher.launch("bloo_settings.json")
                                })
                            },
                            dismissButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MorphTextButton("Open from Drive", onClick = {
                                        showDriveDialog = false
                                        driveOpenLauncher.launch(arrayOf("application/json"))
                                    })
                                    MorphTextButton("Cancel", onClick = { showDriveDialog = false })
                                }
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MorphTextButton("Set up Drive sync",
                            modifier = Modifier.weight(1f),
                            onClick = { showDriveDialog = true })
                        MorphTextButton("Skip",
                            modifier = Modifier.weight(1f),
                            onClick = { /* do nothing */ })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            MorphButton(
                onClick = { vm.finishOnboarding() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Enter Bloo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
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
            .clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private class Burst(val x: Float, val y: Float, val start: Float, val life: Float, val hue: Float, val count: Int, val maxR: Float)

/** A short, lightweight particle-burst fireworks animation drawn on a Canvas. */
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
    AlertDialog(
        onDismissRequest = { if (!loading) vm.kiaCancelOtp() },
        title = { Text(if (otp.sentTo == null) "Verify it's you" else "Enter your code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            if (otp.sentTo != null) {
                MorphButton(
                    onClick = { vm.kiaVerifyOtp(code) },
                    enabled = !loading && code.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    if (loading) LoadingIndicator() else Text("Verify", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            MorphTextButton("Cancel", vm::kiaCancelOtp, enabled = !loading)
        },
    )
}

/**
 * Bloo isn't on the Play Store, so this is its own update surface: shown once
 * per cold start when a newer GitHub Actions build than this one has landed
 * on the default branch. Actions artifacts need a GitHub-authenticated
 * browser session to download (the app itself has no token to fetch them
 * with), so the primary action opens the run's page rather than downloading
 * anything in-app — both the phone and watch APKs are attached there.
 */
@Composable
private fun UpdatePromptDialog(info: com.bloo.bluelink.update.UpdateInfo, vm: AppViewModel) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = vm::dismissUpdate,
        title = { Text("Update available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Build #${info.run.runNumber} is ready on GitHub Actions.")
                Text(
                    "Open the run page to download the phone and watch APKs. " +
                        "Android will warn that the app is from an unknown source — " +
                        "tap \"More details\", then \"Install without scanning\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MorphTextButton(
                    "Remind me in 3 days",
                    onClick = vm::snoozeUpdate,
                )
            }
        },
        confirmButton = {
            MorphButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.run.htmlUrl)))
                    }
                    vm.dismissUpdate()
                },
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("Open GitHub Actions", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            MorphTextButton("Not now", vm::dismissUpdate)
        },
    )
}

/**
 * A softly-blurred, slowly-drifting "aurora" of colour blobs - the animated login
 * backdrop. Three blobs ease back and forth on different periods.
 */
@Composable
private fun AuroraBackground(
    modifier: Modifier = Modifier,
    appearance: SettingsStore.Appearance? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motionMode = appearance?.auroraMotion ?: "static"
    val colorMode = appearance?.auroraColorMode ?: "complementary"
    val customHex = appearance?.auroraCustomColor

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    if (motionMode == "motion") {
        val ctx = LocalContext.current
        DisposableEffect(ctx) {
            val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val alpha = 0.03f
                    tiltX = tiltX * (1 - alpha) + (-event.values[0] * 0.025f) * alpha
                    tiltY = tiltY * (1 - alpha) + (event.values[1] * 0.025f) * alpha
                }
                override fun onAccuracyChanged(s: Sensor, acc: Int) {}
            }
            mgr.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { mgr.unregisterListener(listener) }
        }
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

    val t = rememberInfiniteTransition(label = "aurora")
    val p1 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "p1")
    val p2 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse), label = "p2")
    val p3 by t.animateFloat(0f, 1f, infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "p3")
    fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f
    Box(
        modifier
            .fillMaxSize()
            .blur(120.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .drawBehind {
                drawRect(scheme.surface)
                fun blob(c: Color, fx: Float, fy: Float, r: Float) =
                    drawCircle(c, radius = size.minDimension * r, center = Offset(size.width * fx, size.height * fy))
                blob(basePrimary.copy(alpha = 0.30f), mix(0.38f, 0.62f, p1) + tiltX, mix(0.40f, 0.55f, p2) + tiltY, 0.45f)
                blob(baseTertiary.copy(alpha = 0.25f), mix(0.42f, 0.58f, p2) - tiltX, mix(0.45f, 0.60f, p3) - tiltY, 0.40f)
                blob(baseSecondary.copy(alpha = 0.20f), mix(0.35f, 0.50f, p3) + tiltX, mix(0.38f, 0.52f, p1) + tiltY, 0.38f)
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
    val cfg = LocalConfiguration.current
    val compact = cfg.screenHeightDp < 440
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
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Bloo") },
            actions = {
                IconButton(onClick = { vm.openSettings() }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = { vm.loadGarage() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
            },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    if (state.accounts.isEmpty()) Icons.Filled.CloudOff else Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
                Text(
                    if (state.accounts.isEmpty()) "Not signed in" else "No vehicles found",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (state.accounts.isEmpty())
                        "Sign in to your Hyundai, Kia, or Genesis account in Settings to get started."
                    else
                        "No enrolled vehicles were found on this account.\n\nMake sure your car is registered in the BlueLink / UVO app, then tap Reload.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                if (state.accounts.isEmpty()) {
                    FilledTonalButton(onClick = { vm.openSettings() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Settings")
                    }
                } else {
                    FilledTonalButton(onClick = { vm.loadGarage() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reload")
                    }
                }
                OutlinedButton(onClick = { vm.openSettings() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Account Settings")
                }
            }
        }
    }
}

// --- Garage (main) --------------------------------------------------------

/** Minimum comfortable width for one car column before we add another. */
private const val MIN_CARD_DP = 320

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
            System.currentTimeMillis() - currentFetchedAt > 15 * 60 * 1000L) {
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
    val heightDp = cfg.screenHeightDp
    val large = widthDp >= 600
    val compact = !large && heightDp < 570
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
    val likelyCoverScreen = compact && hasCameraCutout
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
                // Full-screen car; swipe left/right to switch cars.
                val exPager = rememberPagerState(initialPage = (expandedIdx ?: 0).coerceIn(0, count - 1)) { count }
                LaunchedEffect(exPager) {
                    snapshotFlow { exPager.settledPage }.collect { vm.expand(it) }
                }
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = exPager,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        pageSize = androidx.compose.foundation.pager.PageSize.Fill,
                    ) { page ->
                        val pageOff by remember(page) {
                            derivedStateOf {
                                ((page - exPager.currentPage).toFloat() + exPager.currentPageOffsetFraction).let { abs(it).coerceIn(0f, 1f) }
                            }
                        }
                        // Spring-bouncy page transition: off-screen pages fade, shrink,
                        // and tilt slightly — snapping back with a subtle overshoot.
                        val snapBounce by animateFloatAsState(
                            targetValue = if (pageOff < 0.01f) 0f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "pageBounce",
                        )
                        val effectiveOff = pageOff * (1f - snapBounce * 0.3f)
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            alpha = 1f - effectiveOff * 0.2f
                            scaleX = 1f - effectiveOff * 0.06f
                            scaleY = 1f - effectiveOff * 0.06f
                            rotationZ = effectiveOff * if (page >= exPager.currentPage) 2f else -2f
                        }) {
                            val pv = vehicles[page]
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
                    if (count > 1) {
                        PagerDots(
                            current = exPager.currentPage,
                            count = count,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).alpha(dotsAlpha),
                            onRefresh = { vm.refreshStatus(vehicles[exPager.settledPage]) },
                        )
                    }
                }
            } else {
                val pageCount = (count + perPage - 1) / perPage
                val pager = rememberPagerState(
                    initialPage = (state.currentIndex.coerceIn(0, count - 1)) / perPage,
                ) { pageCount }
                LaunchedEffect(pager, perPage) {
                    snapshotFlow { pager.settledPage }.collect { page ->
                        vm.selectIndex((page * perPage).coerceIn(0, count - 1))
                    }
                }
                // Hoisted pill state for single-car-per-page (perPage == 1) mode.
                var carNameVisible by remember { mutableStateOf(false) }
                var scrollToTopFn by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
                val pillScope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        val start = page * perPage
                        val end = minOf(start + perPage, count)
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
                                            onNameHiddenChanged = if (perPage == 1) { hidden, scrollFn ->
                                                carNameVisible = hidden
                                                scrollToTopFn = scrollFn
                                            } else null,
                                        )
                                    }
                                }
                            }
                            repeat(perPage - (end - start)) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    // Floating animated page indicator (no thin top bar).
                    if (pageCount > 1) {
                        PagerDots(
                            current = pager.currentPage,
                            count = pageCount,
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).alpha(dotsAlpha),
                            onRefresh = { vm.refreshStatus(vehicles[state.currentIndex]) },
                        )
                    }
                    // Hoisted car-name pill — centered at top, slides in/out vertically.
                    if (perPage == 1) {
                        AnimatedVisibility(
                            visible = carNameVisible,
                            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it },
                            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 },
                            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        ) {
                            Surface(
                                onClick = { pillScope.launch { scrollToTopFn?.invoke() } },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shadowElevation = 2.dp,
                            ) {
                                Row(
                                    Modifier
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
        // Floating overlay controls. They ride the same refresh shift as the page
        // content so everything slides down together during a pull-to-refresh.
        if (expandedByUser != null) {
            FloatingIcon(
                icon = Icons.Filled.ArrowBack,
                description = "Back to all cars",
                onClick = { vm.collapse() },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding(),
            )
        }
        if (expandedIdx != null) {
            FloatingIcon(
                icon = Icons.Filled.SwapHoriz,
                description = "Flip columns",
                onClick = { vm.setColumnsFlipped(!appearance.columnsFlipped) },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 52.dp),
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

/** Cover-screen layout: swipe left/right for cars, up/down for section tiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactGarage(state: UiState, vm: AppViewModel, appearance: SettingsStore.Appearance) {
    val vehicles = state.vehicles
    val count = vehicles.size
    val pager = rememberPagerState(initialPage = state.currentIndex.coerceIn(0, count - 1)) { count }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { vm.selectIndex(it) }
    }
    // True while the page scrubber is active; suspends car-switching swipes so a
    // scrub gesture can't be hijacked into flipping to the next car.
    val scrubbing = remember { mutableStateOf(false) }
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !scrubbing.value,
        beyondViewportPageCount = 1,
    ) { page ->
        val v = vehicles[page]
        val pageOff by remember(page) {
            derivedStateOf {
                val delta = ((page - pager.currentPage).toFloat() + pager.currentPageOffsetFraction)
                abs(delta).coerceIn(0f, 1f)
            }
        }
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
                    CompactCar(v, state, vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCar(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val isGen5W = remember(v.brand, v.generation) {
        v.brand != Brand.KIA && (v.generation.trim().toIntOrNull() ?: 3) < 3
    }
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
                    (it != "trips" || !isGen5W) &&
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

    val carIndex = state.vehicles.indexOf(v).coerceAtLeast(0)
    val carCount = state.vehicles.size
    // Hide the page indicators while a refresh is in flight (pull-to-refresh /
    // manual refresh) so the loading indicator owns the screen.
    val dotsAlpha by animateFloatAsState(
        targetValue = if (state.refreshing) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "coverDotsFade",
    )

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
    // Top padding for tile content: push below the camera + comfortable gap.
    // Falls back to the standard 10 dp when there is no cutout.
    val tileTopPadding: Dp = cameraHole?.let { r ->
        with(density) { r.bottom.toDp() + 12.dp }
    } ?: 10.dp
    // Decorative ring color — subtle outline that acknowledges the camera hole.
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    // ---- Edge-trace refresh gesture ----
    // Long-press anywhere on the cover screen to trace a line around the edge.
    // When the line completes its full circuit, trigger a refresh. This is a
    // cover-screen-only interaction (the normal phone layout doesn't use it).
    val edgeTraceProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    var edgeTraceHolding by remember { mutableStateOf(false) }
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
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                LocalCameraHolePx provides cameraHole,
                LocalCoverScrollState provides tileScroll,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(
                            start = 10.dp, top = tileTopPadding, bottom = 24.dp,
                            end = if (tiles.size > 1) 22.dp else 10.dp,
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
        // Edge-trace overlay: when holding, a line traces the screen edge clockwise
        // from the top-left. Full circuit = refresh. Drawn on top of everything.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        edgeTraceHolding = true
                        down.consume()
                        try { waitForUpOrCancellation() }
                        finally { if (edgeTraceHolding) edgeTraceHolding = false }
                    }
                },
        ) {
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
        // Car-switching dots, always at top-center (every tile, including main).
        if (carCount > 1) {
            PagerDots(
                current = carIndex,
                count = carCount,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .alpha(dotsAlpha),
                onRefresh = { vm.refreshStatus(v) },
            )
        }
        // Vertical page dots on the right edge - show which pebble tile is visible.
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
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).alpha(dotsAlpha),
            )
        }
    }
}

/** Vertical sibling of [PagerDots] for the cover-screen tile stack.
 *
 * Long-pressing the indicator expands it into a scrubber: slide finger up/down
 * to jump between pages quickly. Each 40 dp of drag moves one page.
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

    Surface(
        modifier = modifier
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
            },
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp).copy(alpha = surfaceAlpha),
        shadowElevation = if (scrubbing) 8.dp else 2.dp,
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

/** The dense main tile: faded car photo behind name, gauge and key controls. */
@Composable
private fun CompactMainTile(v: Vehicle, state: UiState, vm: AppViewModel) {
    val status = state.statusFor(v)
    val img = state.imageUrls[v.vin]
    val scheme = MaterialTheme.colorScheme
    val carIndex = state.vehicles.indexOf(v).coerceAtLeast(0)
    val carCount = state.vehicles.size
    // If the camera is at the top-center, nudge the car name row to the right so
    // it doesn't try to render behind the hole area. The tile-level padding already
    // cleared the camera vertically; this avoids horizontal crowding on the title row.
    val cameraHole = LocalCameraHolePx.current

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
                    model = if (img.startsWith("/")) java.io.File(img) else img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.22f),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().alpha(0.18f)
                        .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))),
                )
            }
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The car-switching dots live at top-center of the screen
                    // (see CompactCar), not here.
                    Text(
                        v.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    FloatingIcon(Icons.Filled.Refresh, "Refresh", { vm.refreshStatus(v) }, outerPadding = 2.dp)
                    FloatingIcon(Icons.Filled.Settings, "Settings", { vm.openSettings() }, outerPadding = 2.dp)
                }
                // Centre the live-status + lock group so the tile reads as one
                // balanced block instead of top-clustered with a big gap below.
                Spacer(Modifier.weight(1f))
                LastUpdatedLabel(v, state)
                ChargeFuelBar(status, state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v))
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

/** A small translucent circular icon button used as a floating overlay control.
 *  [outerPadding] is the breathing room around the 44dp circle - the default
 *  suits free-floating overlay corners; tight rows (the cover screen's title
 *  row) pass less so the row doesn't inflate to 68dp on a ~260dp-tall screen. */
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
    Surface(
        onClick = { haptics?.click(); onClick() },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.padding(outerPadding).size(44.dp).graphicsLayer(scaleX = scale, scaleY = scale),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

/** Page indicator dots with long-press-to-refresh — holding the indicator for
 *  one second triggers [onRefresh] (mirrors the watch's CarNameOverlay pattern). */
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

    LaunchedEffect(holding) {
        if (holding) {
            expandProgress.snapTo(0f)
            expandProgress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            )
            onRefresh?.invoke()
            holding = false
            delay(300)
            expandProgress.animateTo(0f, tween(200))
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Overlay ring that fills as the user holds
        if (expandProgress.value > 0.01f) {
            CircularProgressIndicator(
                progress = { expandProgress.value.coerceIn(0f, 1f) },
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    haptics?.tick()
                    holding = true
                    try { waitForUpOrCancellation() }
                    finally { holding = false }
                }
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.7f),
            shadowElevation = 2.dp,
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

@Composable
private fun HeroHeader(
    v: Vehicle,
    status: VehicleStatus?,
    imageUrl: String?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String? = null,
    dragHandle: Modifier = Modifier,
    height: Dp = 150.dp,
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
    Card(modifier = Modifier.fillMaxWidth().then(dragHandle).graphicsLayer {
        alpha = heroAlpha.value
        translationY = heroOffset.value
    }, shape = RoundedCornerShape(corner)) {
        Column(Modifier.padding(16.dp)) {
            HeroVisual(v, imageUrl, height)
            Spacer(Modifier.height(16.dp))
            ChargeFuelBar(status, hasBattery, hasFuel, drivingLabel)
        }
    }
}

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
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary, scheme.tertiary, scheme.secondary),
                    )
                ),
        )
    } else {
        // A locally-cropped photo is an absolute path; a pasted one is a URL.
        val model: Any = if (imageUrl.startsWith("/")) java.io.File(imageUrl) else imageUrl
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

@Composable
private fun ChargeFuelBar(status: VehicleStatus?, hasBattery: Boolean, hasFuel: Boolean, drivingLabel: String? = null) {
    // Primary metric: battery if the car has one, else fuel. Plug-in hybrids show
    // both - battery as the headline and fuel as a secondary line.
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
                    text = range?.let { "$it mi" } ?: "--",
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

private val ChargeGreen = Color(0xFF2EBD59)
private val ChargeGreenDark = Color(0xFF1B8A41)

private val SoftDamping get() = com.bloo.uicommon.SoftDamping

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
 * The front-facing camera cutout rect for the current display, in raw screen
 * pixels (display coordinate system), or null when there is no cutout. Provided
 * by [CompactCar] so every cover-screen tile can react to the camera hole.
 */
private val LocalCameraHolePx = staticCompositionLocalOf<android.graphics.Rect?> { null }

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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(millis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val diff = (now - millis).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        else -> "${diff / 86_400_000L} d ago"
    }
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
    content: @Composable (item: T, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var dropRipple by remember { mutableStateOf(0L) }

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
        order.forEach { item ->
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
                Box(
                    Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        // Non-dragged items glide to their new slot; the dragged
                        // one is positioned manually via graphicsLayer below.
                        .then(if (dragging) Modifier else Modifier.animatePlacement())
                        .graphicsLayer {
                            translationY = if (dragging) offsetY else 0f
                            scaleX = lift
                            scaleY = lift
                        }
                        .onSizeChanged { heights[k] = it.height },
                ) {
                    val handleCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val handle = Modifier
                        .onGloballyPositioned { handleCoords.value = it }
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

@Composable
private fun BackdropHost(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}

// --- Full detail ----------------------------------------------------------

/** Single-column car view (phones, and each column of the grid). */
@Composable
private fun VehicleDetailContent(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    onExpand: (() -> Unit)? = null,
    reserveHeaderEnd: Boolean = false,
    onNameHiddenChanged: ((Boolean, suspend () -> Unit) -> Unit)? = null,
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
    Refreshable(v, state, vm) {
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
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(v.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Wide expanded view: critical info in one column, pebbles in the other. */
@Composable
private fun ExpandedCar(v: Vehicle, state: UiState, vm: AppViewModel, flipped: Boolean) {
    val hotspot = state.hotspotFor(v.vin)
        ?.takeIf { it in state.sectionsFor(v) && !state.isPebbleHidden(v.vin, it) }
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
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
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
            it !in setOf("summary", "controls") && !state.isPebbleHidden(v.vin, it)
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

/** Wraps content with the pull-to-refresh gesture with an overlay indicator. */
@Composable
private fun Refreshable(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    content: @Composable BoxScope.() -> Unit,
) {
    val ptrState = rememberPullToRefreshState()
    val density = LocalDensity.current
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
        // Indicator floats above content as a z-elevated overlay.
        val indicatorProgress = if (state.refreshing) 1f else ptrState.distanceFraction.coerceIn(0f, 1f)
        val offScreenPx = with(density) { -(topInset + 56.dp).roundToPx() }
        val onScreenPx = with(density) { (topInset + 28.dp).roundToPx() }
        val indicatorY = offScreenPx + ((onScreenPx - offScreenPx) * indicatorProgress).roundToInt()
        PullToRefreshDefaults.LoadingIndicator(
            state = ptrState,
            isRefreshing = state.refreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, indicatorY) },
        )
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
    HeroHeader(v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v), state.drivingLabel(v))
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
    Surface(
        modifier = Modifier.fillMaxWidth().height(ControlHeight).then(dragHandle),
        shape = RoundedCornerShape(PebbleCornerCollapsed),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            PrimaryActions(v, state, vm)
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
            (it != "ai" || state.aiEnabled)
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
    when (section) {
        "summary" -> HeroHeader(
            v, status, state.imageUrls[v.vin], state.hasBattery(v), state.hasFuel(v),
            state.drivingLabel(v), dragHandle = dragHandle,
        )
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
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Edit",
                modifier = Modifier.size(10.dp).clickable { haptics?.click(); onEdit() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Canvas-based colour picker: hue bar + saturation/value square. */
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

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Saturation × Value square
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (editing == null) "New palette" else "Edit \"${editing.name}\"",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (editing != null) {
                    IconButton(onClick = { onDelete(paletteId); onDismiss() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete palette")
                    }
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
            }
        },
        confirmButton = {
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
            ) { MorphButtonLabel(Icons.Filled.Check, "Save", pending = false, iconSize = 18.dp) }
        },
        dismissButton = {
            MorphTextButton("Cancel", onDismiss)
        },
    )
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
        modifier = modifier.animateContentSize(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        ),
        enabled = enabled,
        shape = RoundedCornerShape(percent = pct.roundToInt()),
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
    Surface(
        onClick = { haptics?.tick(); onClick() },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier,
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
    extraAction: (@Composable () -> Unit)? = null,
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
                            label = "lockStateAnim",
                        ) { (ic, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(ic, contentDescription = label, tint = stateColor, modifier = Modifier.size(22.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
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
        // An optional secondary control (e.g. the charge-port toggle) sits just
        // left of the primary action button.
        if (extraAction != null) extraAction()
        val haptics = LocalHaptics.current
        // Pill when off, rounded rectangle + highlight colour when on - same as
        // the climate/charge controls.
        MorphButton(
            onClick = { haptics?.heavy(); if (isOn == true) onDeactivate() else onActivate() },
            enabled = enabled && !pending,
            active = highlighted,
            activeContainerColor = highlightColor,
            activeContentColor = highlightContentColor,
            // Same pill height as the pebble header actions (the row stays
            // ControlHeight tall, so the button is vertically centred in it).
            modifier = Modifier.heightIn(min = 50.dp),
        ) {
            val buttonIcon = if (isOn == true) (deactivateIcon ?: icon) else icon
            MorphButtonLabel(buttonIcon, if (isOn == true) turnOff else turnOn, pending, iconSize = 22.dp)
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
    val haptics = LocalHaptics.current
    // Collapsed = pill-soft corners; expanded morphs to a tighter rounded square.
    val corner by animateDpAsState(
        targetValue = if (expanded) PebbleCornerExpanded else PebbleCornerCollapsed,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "pebbleCorner",
    )
    val fillHeight = LocalPebbleFillHeight.current
    Card(
        Modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        // animateContentSize gives a smooth, correctly-measured collapse (no
        // post-animation size jump). Cover-screen tiles fill instead.
        Column(
            if (fillHeight) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.animateContentSize(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow))
            },
        ) {
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
                            vm.togglePebble(v, section)
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
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                            onToggle = { vm.togglePebble(v, section) },
                        )
                    } else {
                        MorphExpandButton(
                            expanded = expanded,
                            onToggle = { vm.togglePebble(v, section) },
                        )
                    }
                }
            }
            if (fillHeight) {
                // Cover-screen tiles are always force-expanded and must fill the
                // remaining height, so the body is a direct weighted child of the
                // Column (no AnimatedVisibility, which would break weight()).
                if (expanded) {
                    val bodyScroll = LocalCoverScrollState.current ?: rememberScrollState()
                    Column(
                        Modifier.weight(1f).fadingEdges(bodyScroll).verticalScroll(bodyScroll)
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content,
                    )
                }
            } else {
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
            modifier = Modifier.fillMaxHeight(),
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
            modifier = Modifier.fillMaxHeight(),
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
        modifier = Modifier.size(50.dp),
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
    val isGen5W = v.brand != Brand.KIA && (v.generation.trim().toIntOrNull() ?: 3) < 3
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
                trips.take(8).forEach { TripRow(it) }
            }
        }
    }
}

@Composable
private fun TripRow(trip: EvTrip) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tripDate(trip.startdate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            trip.distance?.let {
                Text("%.1f mi".format(it), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val pace = remember(trip) { buildList {
            trip.driveMinutes?.let { add("$it min") }
            trip.idleMinutes?.takeIf { it > 0 }?.let { add("$it min idle") }
            trip.avgspeed?.value?.let { add("avg ${it.toInt()} mph") }
            trip.maxspeed?.value?.let { add("max ${it.toInt()} mph") }
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

/** "2026-06-01 18:22:31.0" -> "Mon Jun 1 · 6:22 PM" (falls back to the raw date). */
private fun tripDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "Trip"
    // Drop any fractional seconds - the feed's precision varies (".0" vs ".000000").
    val trimmed = raw.substringBefore('.').trim()
    return runCatching {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(trimmed)
        java.text.SimpleDateFormat("EEE MMM d · h:mm a", java.util.Locale.US).format(parsed!!)
    }.getOrElse { trimmed.take(16) }
}

// --- Car info (status + service + links combined) -------------------------

@Composable
private fun InfoPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val context = LocalContext.current
    val appearance by vm.appearance.collectAsState()
    val inApp = appearance.linksInApp
    val location = state.locations[v.vin]
    val odo = v.odometer?.trim()?.takeIf { it.isNotBlank() }
    val odoInt = odo?.replace(",", "")?.toDoubleOrNull()?.toInt()
    val plate = state.licensePlates[v.vin]
    val lastSvc = state.lastServiceMiles[v.vin]
    val interval = state.serviceIntervalMiles[v.vin]
    val nextDue = if (lastSvc != null && interval != null) lastSvc + interval else null
    val remaining = if (nextDue != null && odoInt != null) nextDue - odoInt else null

    val ev = status?.evStatus
    val plugged = (ev?.batteryPlugin ?: 0) != 0 || ev?.batteryCharge == true

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
                status.percentFor(v.isEv)?.let {
                    StatusRow(if (v.isEv) "Charge" else "Fuel", "$it%")
                }
                status.rangeMiFor(v.isEv)?.let { StatusRow("Range", "$it mi") }
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
        odo?.let { StatusRow("Odometer", "$it mi") }
        lastSvc?.let { StatusRow("Last service at", "$it mi") }
        nextDue?.let {
            val note = remaining?.let { r -> if (r >= 0) " · $r mi to go" else " · overdue ${-r} mi" } ?: ""
            StatusRow("Next service due", "$it mi$note")
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
        val isGen5W = v.brand != Brand.KIA && (v.generation.trim().toIntOrNull() ?: 3) < 3
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
    val fahrenheit = vm.appearance.collectAsState().value.useFahrenheit
    val rows = remember(status, fahrenheit) { buildList {
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
        status?.rangeMiFor(v.isEv)?.let { add(DiagRow("Range", "$it mi")) }
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
    var tempF by remember(v.vin) { mutableIntStateOf(72) }
    var duration by remember(v.vin) { mutableIntStateOf(10) }
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
                if (climateOn) { vm.stopClimate(v); activePresetId = null } else startClimate()
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
        // and pre-cool/pre-heat to ~10°F off ambient, then start.
        val weather = state.carWeather[v.vin] ?: state.homeWeather
        if (weather != null) {
            val ambientF = ((weather.tempC * 9.0 / 5.0) + 32).roundToInt()
            val smartTarget = if (ambientF >= 70) (ambientF - 10).coerceIn(60, 85)
                              else (ambientF + 10).coerceIn(60, 85)
            val targetLabel = degLabel(smartTarget.toString(), fahrenheit)
            val ambientLabel = degLabel(ambientF.toString(), fahrenheit)
            val smartLabel = if (ambientF >= 70) "Cool to $targetLabel" else "Heat to $targetLabel"
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
                "It's $ambientLabel where your car is — Smart climate runs 10° ${if (ambientF >= 70) "cooler" else "warmer"}.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = MutedContentAlpha),
            )
        }

        SectionLabel("Controls")

        // Color shifts from blue (cold) through neutral to orange-red (hot),
        // normalised to the slider range so it adapts if the range ever changes.
        val tempRange = 62f..82f
        val tempT = ((tempF - tempRange.start) / (tempRange.endInclusive - tempRange.start)).coerceIn(0f, 1f)
        val tempColor by androidx.compose.animation.animateColorAsState(
            targetValue = when {
                tempT < 0.5f -> androidx.compose.ui.graphics.lerp(Color(0xFF2979FF), Color(0xFF66BB6A), tempT * 2f)
                else -> androidx.compose.ui.graphics.lerp(Color(0xFF66BB6A), Color(0xFFFF5722), (tempT - 0.5f) * 2f)
            },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "tempColor",
        )
        if (fahrenheit) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temperature", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bloo.uicommon.AnimatedValue("$tempF°F", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = tempColor))
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
                com.bloo.uicommon.AnimatedValue("$tempC°C", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = tempColor))
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
            valueRange = 1f..10f,
            steps = 8,
        )

        ToggleRow("Defrost", defrost) { defrost = it }
        if (seats.steeringWheel) {
            ToggleRow("Steering wheel heat", steeringHeat) { steeringHeat = it }
        }

        val isGen5W = v.brand != Brand.KIA && (v.generation.trim().toIntOrNull() ?: 3) < 3
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
            AlertDialog(
                onDismissRequest = { showAddPreset = false },
                title = { Text("Save preset") },
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
                confirmButton = {
                    MorphTextButton(
                        "Save",
                        onClick = {
                            if (presetName.isNotBlank()) {
                                vm.saveClimatePreset(v, presetName.trim(), currentReq)
                                showAddPreset = false
                            }
                        },
                        enabled = presetName.isNotBlank(),
                    )
                },
                dismissButton = {
                    MorphTextButton("Cancel", onClick = { showAddPreset = false })
                },
            )
        }
    }
}

private val SeatCool = Color(0xFF2E78FF)
private val SeatHeat = Color(0xFFE5484D)

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
            onClick = { haptics?.tick(); onDelete() },
            color = buttonContainer(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
            modifier = Modifier.fillMaxHeight(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Delete $name", modifier = Modifier.size(15.dp))
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
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
            valueRange = 50f..100f,
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
    val plugged = (ev?.batteryPlugin != null && ev.batteryPlugin != 0) || charging
    val pending = state.isPending(v.vin, "charge")
    val limitPending = state.isPending(v.vin, "chargeLimit")
    val summary = when {
        charging -> "Charging"
        plugged -> "Plugged in · idle"
        else -> "Not plugged in"
    }

    // Separate AC (home / level-2) and DC (fast) charge-limit targets, each
    // defaulting to 80% (a healthy daily ceiling) until the user picks one.
    var acLimit by remember(v.vin) { mutableIntStateOf(80) }
    var dcLimit by remember(v.vin) { mutableIntStateOf(80) }

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
    val fuelPct = status?.fuelLevel
    val range = status?.dte?.value?.toInt()
    val summary = when {
        fuelPct != null && range != null -> "$fuelPct% · $range mi"
        fuelPct != null -> "$fuelPct%"
        range != null -> "$range mi"
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
                range?.let { StatusRow("Range (distance to empty)", "$it mi") }
                if (fuelPct == null && range == null) Text("No fuel data reported.")
            }
        }
    }
}

private fun chargerLabel(plugin: Int?): String? = when (plugin) {
    1 -> "DC fast"
    2 -> "AC (level 2)"
    else -> null
}

private fun fmtMinutes(min: Int) = com.bloo.bluelink.data.fmtMinutes(min)

/**
 * A climate setpoint (the API reports it as a °F string) rendered in the user's
 * chosen unit. Non-numeric values pass through with a bare degree sign.
 */
private fun degLabel(valueF: String, fahrenheit: Boolean): String =
    com.bloo.bluelink.data.degLabel(valueF, fahrenheit)

/** A descriptive name for a vibrancy multiplier (0 = greyscale, 1 = default, 2 = ultra). */
private fun vibrancyLabel(v: Float): String = when {
    v < 0.2f -> "Greyscale"
    v < 0.6f -> "Muted"
    v < 0.9f -> "Subdued"
    v < 1.15f -> "Default"
    v < 1.6f -> "Vivid"
    else -> "Ultra"
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
                StatusRow("Wind", "${w.windKph.toInt()} km/h")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val appearance by vm.appearance.collectAsState()
    val notif by vm.notifications.collectAsState()
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    val updateChecksEnabled by vm.updateChecksEnabled.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val canBio = remember { vm.canUseBiometrics() }
    val settingsScroll = rememberScrollState()
    val settingsScope = rememberCoroutineScope()

    // System back returns to the garage, not out of the app.
    BackHandler { vm.closeSettings() }

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
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search settings & car data") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                    }
                },
                singleLine = true,
                shape = FieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
          // Drop any stale AI answer once the search box is cleared.
          LaunchedEffect(query.isBlank()) { if (query.isBlank()) vm.clearAiReply() }
          if (query.isNotBlank()) {
            SettingsSearchResults(query, vm, state, appearance, notif)
          } else {
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
                                "Sign out",
                                onClick = { vm.logout(creds.brand) },
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

            // On-device AI - only when the device supports Gemini Nano.
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
                    if (state.aiEnabled) {
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
            SettingsCard("App shortcuts") {
                Text(
                    "Pick which long-press app-icon shortcuts appear. Launchers usually show " +
                        "only the first 4–5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            // Cars: drag to reorder, tap a car to expand its setup + photo. With a
            // single car there's nothing to order, so it's just shown expanded.
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
                Text(
                    "Export your settings to a file. Save it to Google Drive to " +
                        "keep your theme, palettes, tile order and preferences " +
                        "synced across devices. Sign-in credentials are never included.",
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
                Spacer(Modifier.height(8.dp))
                if (showDriveDialog) {
                    AlertDialog(
                        onDismissRequest = { showDriveDialog = false },
                        title = { Text("Google Drive sync") },
                        text = {
                            Text(
                                "Sync your settings across devices using Google Drive.\n\n" +
                                    "Save to Drive: pick a folder in Google Drive to " +
                                    "store your settings. Changes sync automatically.\n\n" +
                                    "Open from Drive: pick the settings file from " +
                                    "Google Drive on another device.",
                            )
                        },
                        confirmButton = {
                            MorphTextButton("Save to Drive", onClick = {
                                showDriveDialog = false
                                driveSaveLauncher.launch("bloo_settings.json")
                            })
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MorphTextButton("Open from Drive", onClick = {
                                    showDriveDialog = false
                                    driveOpenLauncher.launch(arrayOf("application/json"))
                                })
                                MorphTextButton("Cancel", onClick = { showDriveDialog = false })
                            }
                        },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MorphTextButton(
                        if (state.syncUri != null) "Auto-sync: on" else "Set up auto-sync",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (state.syncUri != null) {
                                // Already set up; show dialog to re-pick or open from another device
                                showDriveDialog = true
                            } else {
                                showDriveDialog = true
                            }
                        },
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
                        "Settings auto-sync to Drive on every refresh. Changes made on " +
                            "another device are merged automatically.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
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
            }

            // Color
            SettingsCard("Color") {
                // editingPalette: null = no dialog; non-null id but missing in list = new
                var editingPalette by remember { mutableStateOf<CustomPaletteData?>(null) }
                var showEditor by remember { mutableStateOf(false) }
                val haptics = LocalHaptics.current
                val paletteImportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri -> uri?.let { vm.importPalettes(context, it) } }

                ToggleRow("Dynamic color (Material You)", appearance.dynamicColor) { vm.setDynamicColor(it) }
                Text(
                    "Uses your wallpaper palette on Android 12+. Turn off to choose or design your own palette.",
                    style = MaterialTheme.typography.bodySmall,
                )
                AnimatedVisibility(visible = !appearance.dynamicColor) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Built-in palettes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ColorPalette.entries.forEach { palette ->
                                PaletteSwatch(
                                    palette = palette,
                                    selected = appearance.activeCustomPaletteId == null &&
                                        appearance.colorPalette == palette,
                                    onClick = {
                                        vm.setColorPalette(palette)
                                        vm.setActiveCustomPaletteId(null)
                                    },
                                )
                            }
                        }
                        if (appearance.customPalettes.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Custom palettes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                appearance.customPalettes.forEach { custom ->
                                    CustomPaletteSwatch(
                                        palette = custom,
                                        selected = appearance.activeCustomPaletteId == custom.id,
                                        onClick = { vm.setActiveCustomPaletteId(custom.id) },
                                        onEdit = { editingPalette = custom; showEditor = true },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        MorphTextButton(
                            "Add custom palette",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { editingPalette = null; showEditor = true },
                        )
                        // Export / import the user's custom palettes.
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MorphTextButton(
                                "Export",
                                modifier = Modifier.weight(1f),
                                enabled = appearance.customPalettes.isNotEmpty(),
                                onClick = { vm.exportPalettes(context) },
                            )
                            MorphTextButton(
                                "Import",
                                modifier = Modifier.weight(1f),
                                onClick = { paletteImportLauncher.launch("application/json") },
                            )
                        }
                        // Per-car theme overrides: each car can run its own custom palette.
                        if (appearance.customPalettes.isNotEmpty() && state.vehicles.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Per-car theme",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Override the global palette for individual cars.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            state.vehicles.forEach { car ->
                                val carPaletteId = appearance.carCustomPaletteIds[car.vin]
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        car.name,
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    MorphChip(
                                        selected = carPaletteId == null,
                                        onClick = { haptics?.tick(); vm.setCarPaletteId(car.vin, null) },
                                        label = "Global",
                                    )
                                    appearance.customPalettes.forEach { pal ->
                                        val selected = carPaletteId == pal.id
                                        val swatchColor = Color(pal.primaryArgb.toLong() and 0xFFFFFFFFL)
                                        val palScale by animateFloatAsState(
                                            if (selected) 1.18f else 1f,
                                            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                            label = "carPalScale",
                                        )
                                        // Outer box gives room for 1.18x scale so the circle never clips.
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clickable { haptics?.tick(); vm.setCarPaletteId(car.vin, pal.id) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier
                                                    .size(30.dp)
                                                    .graphicsLayer(scaleX = palScale, scaleY = palScale)
                                                    .clip(CircleShape)
                                                    .background(if (selected) MaterialTheme.colorScheme.outline else Color.Transparent)
                                                    .padding(if (selected) 2.dp else 0.dp)
                                                    .clip(CircleShape)
                                                    .background(swatchColor),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Vibrancy feeds BlooTheme's colorScheme, which wraps ~the whole app --
                // committing on every drag tick (vm.setVibrancy -> DataStore -> Flow ->
                // recompose everything under MaterialTheme) made every tick an app-wide
                // recompose, dropping frames badly enough that the settle bounce looked
                // like it cut short. Draft locally while dragging; only commit on release.
                var vibrancyDraft by remember(appearance.vibrancy) { mutableFloatStateOf(appearance.vibrancy) }
                StepRow("Vibrancy", vibrancyLabel(vibrancyDraft))
                AnimatedSlider(
                    value = vibrancyDraft,
                    onValueChange = { vibrancyDraft = (it * 10).roundToInt() / 10f },
                    valueRange = 0f..2f,
                    steps = 19,
                    // A beat after release, not synchronously in onSettle: firing the
                    // app-wide recompose in the same instant the settle-bounce spring
                    // starts made that recompose compete with the bounce for frame
                    // budget, which is what read as the bounce stalling/cutting short
                    // even with the commit already limited to once-per-release.
                    onValueSettled = { vm.setVibrancySoon(vibrancyDraft) },
                )

                if (showEditor) {
                    PaletteEditorDialog(
                        editing = editingPalette,
                        onSave = { palette ->
                            vm.saveCustomPalette(palette)
                            vm.setActiveCustomPaletteId(palette.id)
                        },
                        onDelete = { id -> vm.deleteCustomPalette(id) },
                        onDismiss = { showEditor = false; editingPalette = null },
                    )
                }
            }

            // Display scale
            SettingsCard("Display") {
                // Same deferred-commit pattern as Vibrancy above: uiScale feeds
                // BlooTheme's LocalDensity override for ~the whole app, so committing
                // every drag tick recomposed everything and dropped frames badly
                // enough to look like the settle bounce cut short.
                var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
                StepRow("Text & layout scale", "${(uiScaleDraft * 100).roundToInt()}%")
                AnimatedSlider(
                    value = uiScaleDraft,
                    onValueChange = { uiScaleDraft = (it * 20).roundToInt() / 20f },
                    valueRange = 0.85f..1.3f,
                    steps = 8,
                    onValueSettled = { vm.setUiScaleSoon(uiScaleDraft) },
                )
                Spacer(Modifier.height(12.dp))
                // Global temperature unit, applied everywhere temperatures show.
                SettingsSegmentedRow(
                    label = "Temperature unit",
                    options = listOf(
                        SegmentOption("c", "Celsius", null),
                        SegmentOption("f", "Fahrenheit", null),
                    ),
                    selectedKey = if (appearance.useFahrenheit) "f" else "c",
                    onSelect = { vm.setUseFahrenheit(it == "f") },
                )
            }

            // Font
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

            // Links
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

            // Logs
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

            // Updates
            SettingsCard("Updates") {
                val build = vm.currentBuildNumber
                StepRow("CI build", if (build > 0) "#$build" else "Local build")
                Spacer(Modifier.height(2.dp))
                // Star of David with spin animation on tap
                var starRot by remember { mutableStateOf(0f) }
                val starAngle by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = starRot,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.4f, stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                    ),
                    label = "starSpin",
                )
                Text(
                    "Built by Lord Reset with OpenCode \u2721",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { starRot += 360f },
                )
                Spacer(Modifier.height(8.dp))
                ToggleRow("Check automatically", updateChecksEnabled) { vm.setUpdateChecksEnabled(it) }
                Spacer(Modifier.height(8.dp))
                MorphTextButton(
                    if (state.updateChecking) "Checking…" else "Check now",
                    onClick = { vm.checkForUpdatesNow() },
                    enabled = !state.updateChecking && build > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Bloo isn't on the Play Store, so this checks GitHub Actions for newer " +
                        "builds instead. When one's found, a prompt lets you open it, snooze " +
                        "for a few days, or dismiss it for now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.updateCheckFailed?.let { err ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Check failed: $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Quick Settings tiles
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
                Spacer(Modifier.height(12.dp))
                Text("Units", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                MorphSegmented(
                    options = listOf(
                        SegmentOption("imperial", "Imperial", null),
                        SegmentOption("metric", "Metric", null),
                    ),
                    selectedKey = appearance.unitSystem,
                    onSelect = { vm.setUnitSystem(it) },
                )
            }
            Spacer(Modifier.height(bottomInset + 16.dp))
          }
        }
        } // Box (wide-screen centering)
        // Floating back-arrow + "Settings" label pills over the content.
        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingIcon(Icons.Filled.ArrowBack, "Back to the app", { vm.closeSettings() })
            Surface(
                onClick = { settingsScope.launch { settingsScroll.animateScrollTo(0) } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    "Settings",
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 60.dp, end = 12.dp)
                    .graphicsLayer {
                        alpha = coachAlpha.value
                        translationY = coachOffset.value
                    },
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
        Column(Modifier.padding(12.dp).animateContentSize(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow))) {
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

                    SettingsGroup("Photo") {
                        val storedImage = state.imageUrls[v.vin]
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
        )
        content()
    }
}

private val SearchStopwords = setOf(
    "for", "the", "of", "show", "me", "what", "whats", "is", "a", "an", "to",
    "car", "cars", "my", "s", "setting", "settings", "get", "in",
)

private class SearchEntry(val title: String, val haystack: String, val content: @Composable () -> Unit)

/**
 * Live search over both app settings and per-car data/fields. Tokenises the
 * query (dropping filler words like "for"/"the"), so "odometer for xyz" finds
 * the odometer of the car named xyz, and "plate" lists every car's plate.
 */
@Composable
private fun SettingsSearchResults(
    query: String,
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
        // Deferred-commit, same as the main Display card's slider: uiScale feeds
        // BlooTheme's LocalDensity for ~the whole app, so committing every drag
        // tick recomposed everything and dropped frames badly enough to look
        // like the settle bounce cut short.
        var uiScaleDraft by remember(appearance.uiScale) { mutableFloatStateOf(appearance.uiScale) }
        StepRow("Scale", "${(uiScaleDraft * 100).roundToInt()}%")
        AnimatedSlider(
            value = uiScaleDraft,
            onValueChange = { uiScaleDraft = (it * 20).roundToInt() / 20f },
            valueRange = 0.85f..1.3f,
            steps = 8,
            onValueSettled = { vm.setUiScaleSoon(uiScaleDraft) },
        )
    }
    add("Colour vibrancy", "color saturation vivid material you") {
        // Deferred-commit, same as the main Appearance card's slider — see there.
        var vibrancyDraft by remember(appearance.vibrancy) { mutableFloatStateOf(appearance.vibrancy) }
        StepRow("Vibrancy", vibrancyLabel(vibrancyDraft))
        AnimatedSlider(
            value = vibrancyDraft,
            onValueChange = { vibrancyDraft = (it * 10).roundToInt() / 10f },
            valueRange = 0f..2f,
            steps = 19,
            onValueSettled = { vm.setVibrancySoon(vibrancyDraft) },
        )
    }
    add("Open links in app", "browser tab links") {
        ToggleRow("Open links in app", appearance.linksInApp) { vm.setLinksInApp(it) }
    }
    add("Check for updates", "update github actions build version") {
        val updateChecksEnabled by vm.updateChecksEnabled.collectAsState()
        ToggleRow("Check for updates", updateChecksEnabled) { vm.setUpdateChecksEnabled(it) }
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
        v.odometer?.trim()?.takeIf { it.isNotBlank() }?.let { odo ->
            add("Odometer · ${v.name}", "odometer mileage miles ${v.name}") { StatusRow("Odometer", "$odo mi") }
        }
        add("VIN · ${v.name}", "vin identification ${v.name} ${v.vin}") {
            SelectionContainer { StatusRow("VIN", v.vin) }
        }
        val battRange = st?.evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value
        ((if (state.hasBattery(v)) battRange else null) ?: st?.dte?.value)?.toInt()?.let { r ->
            add("Range · ${v.name}", "range distance dte empty ${v.name}") { StatusRow("Range", "$r mi") }
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

    // On-device AI reply (when enabled): answer the question in natural language,
    // while the structured data below still surfaces the exact values.
    if (state.aiEnabled) {
        LaunchedEffect(query) {
            if (query.isNotBlank()) {
                delay(450)
                vm.askAi(query)
            } else {
                vm.clearAiReply()
            }
        }
        val thinking = "search" in state.aiBusy
        val reply = state.aiSearchReply
        if (thinking || reply != null) {
            Card(
                Modifier.fillMaxWidth(),
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

    val results = if (tokens.isEmpty()) entries else entries.filter { e -> tokens.all { it in e.haystack } }
    if (results.isEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Text(
                "No matches for “$query”",
                Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    results.forEach { e ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                e.content()
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
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    com.bloo.uicommon.MorphSegmented(
        options = options,
        selectedKey = selectedKey,
        onSelect = onSelect,
        containerColor = buttonContainer(),
        indicatorColor = scheme.primary,
        selectedTextColor = scheme.onPrimary,
        unselectedTextColor = scheme.onSurfaceVariant,
        textStyle = MaterialTheme.typography.labelLarge,
        onTick = { haptics?.tick() },
        modifier = modifier,
    )
}


/** A car's powertrain (Gas/Hybrid/PHEV/EV) is a fixed 4-way choice between
 *  equal alternatives — one shared MorphSegmented instead of the MorphChip
 *  row this was duplicated as in both CarSettingsCard and its settings-search
 *  mirror. */
@Composable
private fun PowertrainPicker(current: com.bloo.bluelink.data.Powertrain, onSelect: (com.bloo.bluelink.data.Powertrain) -> Unit) {
    MorphSegmented(
        options = listOf(
            SegmentOption(com.bloo.bluelink.data.Powertrain.GAS.name, "Gas", null),
            SegmentOption(com.bloo.bluelink.data.Powertrain.HYBRID.name, "Hybrid", null),
            SegmentOption(com.bloo.bluelink.data.Powertrain.PHEV.name, "PHEV", null),
            SegmentOption(com.bloo.bluelink.data.Powertrain.EV.name, "EV", null),
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
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = if (img.startsWith("/")) java.io.File(img) else img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize()
                        .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (assignedCount) {
                    0 -> "No tiles yet"
                    1 -> "1 quick tile"
                    else -> "$assignedCount quick tiles"
                },
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
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

/**
 * A miniature of the real Quick Settings shade tile: a rounded pill with a leading
 * icon bubble, the tile label, and a state line. Filled with the accent when
 * [active] (mirrors BlooTileService's STATE_ACTIVE rendering) so the preview is honest.
 */
@Composable
private fun TileShadePreview(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
) {
    val scheme = MaterialTheme.colorScheme
    val container by androidx.compose.animation.animateColorAsState(
        if (active) scheme.primary else scheme.surfaceVariant,
        spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "previewBg",
    )
    val onContainer = if (active) scheme.onPrimary else scheme.onSurface
    val bubble = if (active) scheme.onPrimary.copy(alpha = 0.20f) else scheme.surface
    val bubbleIcon = if (active) scheme.onPrimary else scheme.onSurfaceVariant
    Surface(shape = shape, color = container, contentColor = onContainer, modifier = modifier) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(bubble), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = bubbleIcon, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = onContainer.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
 * One configured tile, styled after the climate-preset cards: a tappable summary
 * row (icon + name + what it does) that expands into an inline editor with chip
 * pickers for the action, a custom name, and — for climate — what it runs.
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

    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow),
        label = "tileChevron",
    )
    Card(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .animateContentSize(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            // Split button: the preview is the wide "value" half, the chevron gets
            // its own holding shape as the narrow half — same pill<->rounded-rect
            // morph convention as PresetPill/ChargeLimitPill, so a row with a
            // dropdown reads as one deliberate two-part control instead of a bare
            // floating arrow icon.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                // A faithful preview of the shade tile itself, so users see exactly
                // what the QS tile will read (mirrors BlooTileService state rendering).
                TileShadePreview(
                    icon = headerIcon,
                    title = if (cmd == "open") "Open" else (customName ?: tileActionLabel(cmd)),
                    subtitle = liveLabel ?: tileSummary(cmd, target, presetName),
                    active = active,
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 12.dp, bottomEnd = 12.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { expanded = !expanded },
                )
                Surface(
                    onClick = { expanded = !expanded },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Edit tile",
                            modifier = Modifier.size(20.dp).rotate(chevron),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(180), shrinkTowards = Alignment.Top),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))
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

                    Spacer(Modifier.height(12.dp))
                    val addLabel = if (cmd == "open") "Open" else (customName ?: tileActionLabel(cmd))
                    MorphButton(
                        onClick = {
                            addTileToQuickSettings(
                                context, index, cmd, addLabel,
                                unlocked = status?.doorLock == false,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add to Quick Settings", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    MorphButton(
                        onClick = { vm.setTileAssignment(index, null, null) },
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
                .animateContentSize(spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessMediumLow)),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        )
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
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(160)) + slideInVertically { it / 2 }) togetherWith
                (fadeOut(tween(100)) + slideOutVertically { -it / 2 })
            },
            label = "statusVal",
        ) { v -> WiggleText(v, style = LocalTextStyle.current, fontWeight = FontWeight.Medium) }
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

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = LocalHaptics.current
    val scale by animateFloatAsState(
        if (checked) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toggleScale",
    )
    Row(
        Modifier
            .fillMaxWidth()
            // The checked-state scale bump below draws outside the Switch's own
            // layout bounds; without this trailing reserve it got clipped by the
            // screen/card edge.
            .padding(end = 3.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val next = !checked
                if (next) haptics?.toggleOn() else haptics?.toggleOff()
                onChange(next)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = { if (it) haptics?.toggleOn() else haptics?.toggleOff(); onChange(it) },
            modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
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

/** A staggered fade-in + slide-up entrance for onboarding sections. */
@Composable
private fun StaggerFadeIn(delay: Int, offset: Int = 16, content: @Composable () -> Unit) {
    val animAlpha = remember { Animatable(0f) }
    val animY = remember { Animatable(offset.toFloat()) }
    LaunchedEffect(Unit) {
        if (delay > 0) delay(delay.toLong())
        launch { animAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    }
    Box(Modifier.graphicsLayer { alpha = animAlpha.value; translationY = animY.value }) {
        content()
    }
}
